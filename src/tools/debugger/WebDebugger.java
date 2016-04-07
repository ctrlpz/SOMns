package tools.debugger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.ExecutionEvent;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.LineLocation;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.utilities.JSONHelper;
import com.oracle.truffle.api.utilities.JSONHelper.JSONArrayBuilder;
import com.oracle.truffle.api.utilities.JSONHelper.JSONObjectBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import som.vm.NotYetImplementedException;
import tools.highlight.JsonWriter;
import tools.highlight.Tags;


/**
 * The WebDebugger connects the Truffle debugging facilities with a HTML5
 * application using WebSockets and JSON.
 */
@Registration(id = WebDebugger.ID)
public class WebDebugger extends TruffleInstrument {

  public static final String ID = "web-debugger";

  private HttpServer httpServer;
  private WebSocketHandler webSocketServer;
  private Future<WebSocket> clientConnected;

  private static final Map<Source, Map<SourceSection, Set<Class<? extends Tags>>>> loadedSources = new HashMap<>();

  private static int nextSourceId = 0;
  private static int nextSourceSectionId = 0;
  private static final Map<Source, String> sourcesId = new HashMap<>();
  private static final Map<String, Source> idSources = new HashMap<>();
  private static final Map<SourceSection, String> sourceSectionId = new HashMap<>();

  private static WebDebugger debugger;
  private static WebSocket client;
  private static Debugger truffleDebugger;

  public WebDebugger() {
    debugger = this;
  }

  public static void reportSyntaxElement(final Class<? extends Tags> type,
      final SourceSection source) {
    Map<SourceSection, Set<Class<? extends Tags>>> sections = loadedSources.computeIfAbsent(
        source.getSource(), s -> new HashMap<>());
    Set<Class<? extends Tags>> tags = sections.computeIfAbsent(source, s -> new HashSet<>(2));
    tags.add(type);

    sourcesId.computeIfAbsent(source.getSource(), src -> {
      int n = nextSourceId;
      nextSourceId += 1;
      String id = "s-" + n;
      idSources.put(id, src);
      return id;
    });

    sourceSectionId.computeIfAbsent(source, s -> {
      int n = nextSourceSectionId;
      nextSourceSectionId += 1;
      return "ss-" + n;
    });
  }

  private static final ArrayList<Source> notReady = new ArrayList<>();

  public static void reportLoadedSource(final Source source) {
    if (debugger == null || debugger.webSocketServer == null || client == null) {
      notReady.add(source);
      return;
    }

    ensureConnectionIsAvailable();

    if (!notReady.isEmpty()) {
      for (Source s : notReady) {
        String json = createSourceAndSectionMessage(s);
        client.send(json);
      }
      notReady.clear();
    }

    String json = createSourceAndSectionMessage(source);
    client.send(json);
  }

  private static void ensureConnectionIsAvailable() {
    assert debugger != null;
    assert debugger.webSocketServer != null;
    assert client != null;

    assert client.isOpen();
  }

  private static String createSourceAndSectionMessage(final Source source) {
    return JsonWriter.createJson("source", loadedSources.get(source), sourcesId, sourceSectionId);
  }

  public static void reportRootNodeAfterParsing(final RootNode rootNode) {

  }

  public static void reportExecutionEvent(final ExecutionEvent e) {
    truffleDebugger = e.getDebugger();
    // TODO: prepare step and continue???
  }


  private static int nextSuspendEventId = 0;
  private static final Map<String, SuspendedEvent> suspendEvents  = new HashMap<>();
  private static final Map<String, CompletableFuture<Object>> suspendFutures = new HashMap<>();


  private static String getNextSuspendEventId() {
    int id = nextSuspendEventId;
    nextSuspendEventId += 1;
    return "se-" + id;
  }

  public static void reportSuspendedEvent(final SuspendedEvent e) {
    // e.getNode().getSourceSection().toString()
    System.out.print(".");


    JSONObjectBuilder builder  = JSONHelper.object();
    builder.add("type", "suspendEvent");

    JSONArrayBuilder stackJson = JSONHelper.array();
    List<FrameInstance> stack = e.getStack();


    for (int stackIndex = 0; stackIndex < stack.size(); stackIndex++) {
      final Node callNode = stackIndex == 0 ? e.getNode() : stack.get(stackIndex).getCallNode();
      stackJson.add(createFrame(callNode, stack.get(stackIndex)));
    }
    builder.add("stack", stackJson);
    builder.add("topFrame", createTopFrameJson(e.getFrame(), e.getNode().getRootNode()));

    String id = getNextSuspendEventId();
    builder.add("id", id);

    CompletableFuture<Object> future = new CompletableFuture<>();
    suspendEvents.put(id, e);
    suspendFutures.put(id, future);

    ensureConnectionIsAvailable();

    client.send(builder.toString());
    System.out.println(builder.toString());

    try {
      future.get();
    } catch (InterruptedException | ExecutionException e1) {
      System.out.println("[DEBUGGER] Future failed:");
      e1.printStackTrace();
    }
  }

  private static JSONObjectBuilder createTopFrameJson(final MaterializedFrame frame, final RootNode root) {
    JSONArrayBuilder arguments = JSONHelper.array();
    for (Object o : frame.getArguments()) {
      arguments.add(o.toString());
    }

    JSONObjectBuilder slots = JSONHelper.object();
    for (FrameSlot slot : root.getFrameDescriptor().getSlots()) {
      Object value = frame.getValue(slot);
      slots.add(slot.getIdentifier().toString(),
          Objects.toString(value));
    }

    JSONObjectBuilder frameJson = JSONHelper.object();
    frameJson.add("arguments", arguments);
    frameJson.add("slots", slots);
    return frameJson;
  }


  private static JSONObjectBuilder createFrame(final Node node, final FrameInstance stackFrame) {
    JSONObjectBuilder frame = JSONHelper.object();
    if (node != null && node.getEncapsulatingSourceSection() != null) {
      frame.add("sourceSection", JsonWriter.sectionToJson(
          node.getEncapsulatingSourceSection(),
          sourceSectionId.get(node.getEncapsulatingSourceSection()),
          sourcesId, new HashSet<>())); // TODO: add tags
    }

    RootCallTarget rct = (RootCallTarget) stackFrame.getCallTarget();
    SourceSection rootSource = rct.getRootNode().getSourceSection();
    String methodName;
    if (rootSource != null) {
      methodName = rootSource.getIdentifier();
    } else {
      methodName = rct.toString();
    }
    frame.add("methodName", methodName);

    // TODO: stack frame content, or on demand?
    // stackFrame.getFrame(FrameAccess.READ_ONLY, true);
    return frame;
}

  @Override
  protected void onCreate(final Env env) {
    // Checkstyle: stop
    try {
      System.out.println("[DEBUGGER] Initialize HTTP and WebSocket Server for Debugger");
      int port = 8889;
      initializeWebSocket(8889);
      System.out.println("[DEBUGGER] Started WebSocket Server");

      port = 8888;
      initializeHttpServer(port);
      System.out.println("[DEBUGGER] Started HTTP Server");
      System.out.println("[DEBUGGER]   URL: http://localhost:" + port + "/index.html");
    } catch (IOException e) {
      e.printStackTrace();
      System.out.println("Failed starting WebSocket and/or HTTP Server");
    }

    assert clientConnected != null;
    System.out.println("[DEBUGGER] Waiting for debugger to connect.");
    try {
      client = clientConnected.get();
//      System.out.println("SLEEEP");
//      Thread.sleep(Long.MAX_VALUE);
    } catch (InterruptedException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (ExecutionException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    System.out.println("[DEBUGGER] Debugger connected.");
    // Checkstyle: resume
  }

  private void initializeHttpServer(final int port) throws IOException {
    if (httpServer == null) {
      InetSocketAddress address = new InetSocketAddress(port);
      httpServer = HttpServer.create(address, 0);
      httpServer.createContext("/", new WebHandler());
      httpServer.setExecutor(null);
      httpServer.start();
    }
  }

  private static class WebHandler implements HttpHandler {

    @Override
    public void handle(final HttpExchange exchange) throws IOException {
      System.out.println("[REQ] " + exchange.getRequestURI().toString());
      switch (exchange.getRequestURI().toString()) {
        case "/":
        case "/index.html":
          File f = new File("/Users/smarr/Projects/SOM/SOMns/tools/index.html");
          exchange.sendResponseHeaders(200, f.length());
          copy(f, exchange.getResponseBody());
          return;
        case "/source.js":
          File js = new File("/Users/smarr/Projects/SOM/SOMns/tools/source.js");
          exchange.sendResponseHeaders(200, js.length());
          copy(js, exchange.getResponseBody());
          return;
        case "/favicon.ico":
          exchange.sendResponseHeaders(404, 0);
          return;
      }

      System.out.println("[REQ] not yet implemented");
      throw new NotYetImplementedException();
    }

    private static void copy(final File f, final OutputStream out) throws IOException {
      byte[] buf = new byte[8192];

      InputStream in = new FileInputStream(f);

      int c = 0;
      while ((c = in.read(buf, 0, buf.length)) > 0) {
        out.write(buf, 0, c);
//        out.flush();
      }

      out.close();
      in.close();
    }
  }

  private void initializeWebSocket(final int port) {
    if (webSocketServer == null) {
      clientConnected = new CompletableFuture<WebSocket>();
      InetSocketAddress addess = new InetSocketAddress(port);
      webSocketServer = new WebSocketHandler(
          addess, (CompletableFuture<WebSocket>) clientConnected);
      webSocketServer.start();
    }
  }

  private static class WebSocketHandler extends WebSocketServer {
    private static final int NUM_THREADS = 1;

    private final CompletableFuture<WebSocket> clientConnected;

    WebSocketHandler(final InetSocketAddress address,
        final CompletableFuture<WebSocket> clientConnected) {
      super(address, NUM_THREADS);
      this.clientConnected = clientConnected;
    }

    @Override
    public void onOpen(final WebSocket conn, final ClientHandshake handshake) {
      clientConnected.complete(conn);
    }

    @Override
    public void onClose(final WebSocket conn, final int code, final String reason,
        final boolean remote) {
      System.out.println("onClose: code=" + code + " " + reason);
    }

    @Override
    public void onMessage(final WebSocket conn, final String message) {
      JsonObject msg = Json.parse(message).asObject();

      switch(msg.getString("action", null)) {
        case "updateBreakpoint":
          System.out.println("UPDATE BREAKPOINT");
          String sourceId   = msg.getString("sourceId", null);
          String sourceName = msg.getString("sourceName", null);
          int lineNumber    = msg.getInt("line", -1);
          boolean enabled   = msg.getBoolean("enabled", false);
          System.out.println(sourceId + ":" + lineNumber + " " + enabled);

          Source source = idSources.get(sourceId);
          LineLocation line = source.createLineLocation(lineNumber);

          assert truffleDebugger != null : "debugger has not be initialized yet";
          Breakpoint bp = truffleDebugger.getBreakpoint(line);

          if (enabled && bp == null) {
            try {
              System.out.println("SetLineBreakpoint line:" + line);
              truffleDebugger.setLineBreakpoint(0, line, false);
            } catch (IOException e) {
              e.printStackTrace();
            }
          } else if (bp != null) {
            bp.setEnabled(enabled);
          }
          return;
      }

      System.out.println("not supported: onMessage: " + message);
    }

    @Override
    public void onError(final WebSocket conn, final Exception ex) {
      System.out.println("error:");
      ex.printStackTrace();
    }
  }
}

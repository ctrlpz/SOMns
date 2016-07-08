package tools.debugger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.java_websocket.WebSocket;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.utilities.JSONHelper.JSONObjectBuilder;
import com.sun.net.httpserver.HttpServer;

import som.interpreter.actors.Actor;
import som.interpreter.actors.EventualMessage;
import som.interpreter.actors.SFarReference;
import tools.ObjectBuffer;
import tools.highlight.Tags;


/**
 * Connect the debugger to the UI front-end.
 */
public class FrontendConnector {

  private Instrumenter instrumenter;

  private final Breakpoints breakpoints;
  private final WebDebugger webDebugger;

  /**
   * Serves the static resources.
   */
  private final HttpServer contentServer;

  /**
   * Receives requests from the client.
   */
  private final WebSocketHandler receiver;

  /**
   * Sends requests to the client.
   */
  private WebSocket sender;

  /**
   * Future to await the client's connection.
   */
  private Future<WebSocket> clientConnected;

  private final ArrayList<Source> notReady = new ArrayList<>(); //TODO rename: toBeSend


  public FrontendConnector(final Breakpoints breakpoints,
      final Instrumenter instrumenter, final WebDebugger webDebugger) {
    this.instrumenter = instrumenter;
    this.breakpoints = breakpoints;
    this.webDebugger = webDebugger;

    clientConnected = new CompletableFuture<WebSocket>();

    try {
      log("[DEBUGGER] Initialize HTTP and WebSocket Server for Debugger");
      int port = 8889;
      receiver = initializeWebSocket(port, clientConnected, breakpoints);
      log("[DEBUGGER] Started WebSocket Server");

      port = 8888;
      contentServer = initializeHttpServer(port);
      log("[DEBUGGER] Started HTTP Server");
      log("[DEBUGGER]   URL: http://localhost:" + port + "/index.html");
    } catch (IOException e) {
      log("Failed starting WebSocket and/or HTTP Server");
      throw new RuntimeException(e);
    }
    // now we continue execution, but we wait for the future in the execution
    // event
  }

  private WebSocketHandler initializeWebSocket(final int port,
      final Future<WebSocket> clientConnected, final Breakpoints breakpoints) {
    InetSocketAddress addess = new InetSocketAddress(port);
    WebSocketHandler server = new WebSocketHandler(
        addess, (CompletableFuture<WebSocket>) clientConnected, this);
    server.start();
    return server;
  }

  private HttpServer initializeHttpServer(final int port) throws IOException {
    InetSocketAddress address = new InetSocketAddress(port);
    HttpServer httpServer = HttpServer.create(address, 0);
    httpServer.createContext("/", new WebResourceHandler());
    httpServer.setExecutor(null);
    httpServer.start();
    return httpServer;
  }

  private void ensureConnectionIsAvailable() {
    assert receiver != null;
    assert sender != null;
    assert sender.isOpen();
  }

  private void sendSource(final Source source,
      final Map<Source, Map<SourceSection, Set<Class<? extends Tags>>>> loadedSourcesTags) {
    String json = JsonSerializer.createSourceAndSectionMessage(source, loadedSourcesTags.get(source));
    sender.send(json);
  }

  private void sendBufferedSources(final Map<Source, Map<SourceSection, Set<Class<? extends Tags>>>> loadedSourcesTags) {
    if (!notReady.isEmpty()) {
      for (Source s : notReady) {
        sendSource(s, loadedSourcesTags);
      }
      notReady.clear();
    }
  }

  public void sendLoadedSource(final Source source,
      final Map<Source, Map<SourceSection, Set<Class<? extends Tags>>>> loadedSourcesTags) {
    if (receiver == null || sender == null) {
      notReady.add(source);
      return;
    }

    ensureConnectionIsAvailable();
    sendBufferedSources(loadedSourcesTags);
    sendSource(source, loadedSourcesTags);
  }

  public void awaitClient() {
    assert clientConnected != null;
    log("[DEBUGGER] Waiting for debugger to connect.");
    try {
      sender = clientConnected.get();
    } catch (InterruptedException | ExecutionException ex) {
      throw new RuntimeException(ex);
    }
    log("[DEBUGGER] Debugger connected.");
  }

  private static Map<SFarReference, String> createActorMap(
      final ObjectBuffer<ObjectBuffer<SFarReference>> actorsPerThread) {
    HashMap<SFarReference, String> map = new HashMap<>();
    int numActors = 0;

    for (ObjectBuffer<SFarReference> perThread : actorsPerThread) {
      for (SFarReference a : perThread) {
        assert !map.containsKey(a);
        map.put(a, "a-" + numActors);
        numActors += 1;
      }
    }
    return map;
  }

  public void sendSuspendedEvent(final SuspendedEvent e, final String id,
      final Map<Source, Map<SourceSection, Set<Class<? extends Tags>>>> loadedSourcesTags,
      final Map<Source, Set<RootNode>> rootNodes) {
    Node     suspendedNode = e.getNode();
    RootNode suspendedRoot = suspendedNode.getRootNode();
    Source suspendedSource;
    if (suspendedRoot.getSourceSection() != null) {
      suspendedSource = suspendedRoot.getSourceSection().getSource();
    } else {
      suspendedSource = suspendedNode.getSourceSection().getSource();
    }

    JSONObjectBuilder builder = JsonSerializer.createSuspendedEventJson(e,
        suspendedNode, suspendedRoot, suspendedSource, id,
        loadedSourcesTags, instrumenter, rootNodes);

    ensureConnectionIsAvailable();

    sender.send(builder.toString());
  }

  public void sendActorHistory() {
    ensureConnectionIsAvailable();

    log("[ACTORS] send message history");

    ObjectBuffer<ObjectBuffer<SFarReference>> actorsPerThread = Actor.getAllCreateActors();
    ObjectBuffer<ObjectBuffer<ObjectBuffer<EventualMessage>>> messagesPerThread = Actor.getAllProcessedMessages();

    Map<SFarReference, String> actorsToIds = createActorMap(actorsPerThread);
    Map<Actor, String> actorObjsToIds = new HashMap<>(actorsToIds.size());
    for (Entry<SFarReference, String> e : actorsToIds.entrySet()) {
      Actor a = e.getKey().getActor();
      assert !actorObjsToIds.containsKey(a);
      actorObjsToIds.put(a, e.getValue());
    }

    JSONObjectBuilder msg = JsonSerializer.createMessageHistoryJson(messagesPerThread,
        actorsToIds, actorObjsToIds);

    String m = msg.toString();
    log("[ACTORS] Message length: " + m.length());
    sender.send(m);
    log("[ACTORS] Message sent?");
    try {
      Thread.sleep(150000);
    } catch (InterruptedException e1) { }
    log("[ACTORS] Message sent waiting completed");

    sender.close();
  }

  public void requestBreakpoint(final boolean enabled, final URI sourceUri,
      final int startLine, final int startColumn, final int charLength) {
    try {
      Breakpoint bp = breakpoints.getBreakpoint(sourceUri, startLine, startColumn, charLength);
      bp.setEnabled(enabled);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void requestBreakpoint(final boolean enabled, final URI sourceUri,
      final int lineNumber) {
    try {
      Breakpoint bp = breakpoints.getBreakpoint(sourceUri, lineNumber);
      bp.setEnabled(enabled);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public SuspendedEvent getSuspendedEvent(final String id) {
    return webDebugger.getSuspendedEvent(id);
  }

  public void completeSuspendFuture(final String id, final Object value) {
    webDebugger.getSuspendFuture(id).complete(value);
  }

  static void log(final String str) {
    // Checkstyle: stop
    System.out.println(str);
    // Checkstyle: resume
  }
}

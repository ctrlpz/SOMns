package som.primitives;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.FindContextNode;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.source.SourceSection;

import som.VM;
import som.compiler.MixinDefinition;
import som.interop.ValueConversion.ToSomConversion;
import som.interop.ValueConversionFactory.ToSomConversionNodeGen;
import som.interpreter.Invokable;
import som.interpreter.SomLanguage;
import som.interpreter.nodes.nary.BinaryComplexOperation;
import som.interpreter.nodes.nary.UnaryBasicOperation;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vm.NotYetImplementedException;
import som.vm.constants.Classes;
import som.vm.constants.Nil;
import som.vmobjects.SArray;
import som.vmobjects.SArray.SImmutableArray;
import som.vmobjects.SObjectWithClass;
import som.vmobjects.SSymbol;


public final class SystemPrims {

  @CompilationFinal public static SObjectWithClass SystemModule;

  @GenerateNodeFactory
  @Primitive("systemModuleObject:")
  public abstract static class SystemModuleObjectPrim extends UnaryExpressionNode {
    public SystemModuleObjectPrim(final SourceSection source) { super(false, source); }

    @Specialization
    public final Object set(final SObjectWithClass system) {
      SystemModule = system;
      return system;
    }
  }

  public static Object loadModule(final String path) {
    MixinDefinition module;
    try {
      module = VM.loadModule(path);
      return module.instantiateModuleClass();
    } catch (IOException e) {
      // TODO convert to SOM exception when we support them
      e.printStackTrace();
    }
    return Nil.nilObject;
  }

  @GenerateNodeFactory
  @Primitive("load:")
  public abstract static class LoadPrim extends UnaryExpressionNode {
    public LoadPrim(final SourceSection source) { super(false, source); }

    @Specialization
    public final Object doSObject(final String moduleName) {
      return loadModule(moduleName);
    }
  }

  @GenerateNodeFactory
  @Primitive("load:nextTo:")
  public abstract static class LoadNextToPrim extends BinaryComplexOperation {
    protected LoadNextToPrim(final SourceSection source) { super(false, source); }

    @Specialization
    public final Object load(final String filename, final SObjectWithClass moduleObj) {
      String path = moduleObj.getSOMClass().getMixinDefinition().getSourceSection().getSource().getPath();
      File file = new File(path);
      return loadModule(file.getParent() + File.separator + filename);
    }
  }

  @GenerateNodeFactory
  @Primitive("exit:")
  public abstract static class ExitPrim extends UnaryExpressionNode {
    public ExitPrim(final SourceSection source) { super(false, source); }

    @Specialization
    public final Object doSObject(final long error) {
      VM.exit((int) error);
      return Nil.nilObject;
    }
  }

  @GenerateNodeFactory
  @Primitive("printString:")
  public abstract static class PrintStringPrim extends UnaryExpressionNode {
    public PrintStringPrim(final SourceSection source) { super(false, source); }

    @Specialization
    public final Object doSObject(final String argument) {
      VM.print(argument);
      return argument;
    }

    @Specialization
    public final Object doSObject(final SSymbol argument) {
      return doSObject(argument.getString());
    }
  }

  @GenerateNodeFactory
  @Primitive("printNewline:")
  public abstract static class PrintInclNewlinePrim extends UnaryExpressionNode {
    public PrintInclNewlinePrim(final SourceSection source) { super(false, source); }

    @Specialization
    public final Object doSObject(final String argument) {
      VM.println(argument);
      return argument;
    }
  }

  @GenerateNodeFactory
  @Primitive("printStackTrace:")
  public abstract static class PrintStackTracePrim extends UnaryExpressionNode {
    public PrintStackTracePrim(final SourceSection source) { super(false, source); }

    @Specialization
    public final Object doSObject(final Object receiver) {
      printStackTrace();
      return receiver;
    }

    public static void printStackTrace() {
      ArrayList<String> method   = new ArrayList<String>();
      ArrayList<String> location = new ArrayList<String>();
      int[] maxLengthMethod = {0};
      VM.println("Stack Trace");
      Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<Object>() {
        @Override
        public Object visitFrame(final FrameInstance frameInstance) {
          RootCallTarget ct = (RootCallTarget) frameInstance.getCallTarget();

          // TODO: do we need to handle other kinds of root nodes?
          if (!(ct.getRootNode() instanceof Invokable)) {
             return null;
          }

          Invokable m = (Invokable) ct.getRootNode();
          SourceSection ss = m.getSourceSection();
          if (ss != null) {
            String id = ss.getIdentifier();
            method.add(id);
            maxLengthMethod[0] = Math.max(maxLengthMethod[0], id.length());
            Node callNode = frameInstance.getCallNode();
            if (callNode != null) {
              SourceSection nodeSS = callNode.getEncapsulatingSourceSection();
              location.add(nodeSS.getSource().getName() + ":" + nodeSS.getStartLine() + ":" + nodeSS.getStartColumn());
            } else {
              location.add("");
            }
          } else {
            String id = m.toString();
            method.add(id);
            maxLengthMethod[0] = Math.max(maxLengthMethod[0], id.length());
            location.add("");
          }
          return null;
        }
      });

      StringBuilder sb = new StringBuilder();
      final int skipDnuFrames = 2;
      for (int i = method.size() - 1; i >= skipDnuFrames; i--) {
        sb.append(String.format("\t%1$-" + (maxLengthMethod[0] + 4) + "s",
          method.get(i)));
        sb.append(location.get(i));
        sb.append('\n');
      }

      VM.print(sb.toString());
    }
  }

  @GenerateNodeFactory
  @Primitive("vmArguments:")
  public abstract static class VMArgumentsPrim extends UnaryExpressionNode {
    public VMArgumentsPrim(final SourceSection source) { super(false, source); }

    @Specialization
    public final SImmutableArray getArguments(final Object receiver) {
      return new SImmutableArray(VM.getArguments(), Classes.valueArrayClass);
    }
  }

  @GenerateNodeFactory
  @Primitive("systemGC:")
  public abstract static class FullGCPrim extends UnaryExpressionNode {
    public FullGCPrim(final SourceSection source) { super(false, source); }

    @Specialization
    public final Object doSObject(final Object receiver) {
      System.gc();
      return true;
    }
  }

  @GenerateNodeFactory
  @Primitive("systemTime:")
  public abstract static class TimePrim extends UnaryBasicOperation {
    public TimePrim(final SourceSection source) { super(false, source); }

    @Specialization
    public final long doSObject(final Object receiver) {
      return System.currentTimeMillis() - startTime;
    }
  }

  @GenerateNodeFactory
  @Primitive("systemTicks:")
  public abstract static class TicksPrim extends UnaryBasicOperation {
    public TicksPrim(final SourceSection source) { super(false, source); }

    @Specialization
    public final long doSObject(final Object receiver) {
      return System.nanoTime() / 1000L - startMicroTime;
    }
  }

  @GenerateNodeFactory
  @Primitive("systemExport:as:")
  public abstract static class ExportAsPrim extends BinaryComplexOperation {
    @Child protected FindContextNode<VM> findContext;

    protected ExportAsPrim(final SourceSection source) {
      super(false, source);
      findContext = SomLanguage.INSTANCE.createNewFindContextNode();
    }

    @Specialization
    public final boolean doString(final Object obj, final String name) {
      VM vm = findContext.executeFindContext();
      vm.registerExport(name, obj);
      return true;
    }

    @Specialization
    public final boolean doSymbol(final Object obj, final SSymbol name) {
      return doString(obj, name.getString());
    }
  }

  @GenerateNodeFactory
  @Primitive("systemApply:with:")
  public abstract static class ApplyWithPrim extends BinaryComplexOperation {
    protected ApplyWithPrim(final SourceSection source) { super(false, source); }

    private final ValueProfile storageType  = ValueProfile.createClassProfile();

    @Child protected SizeAndLengthPrim size = SizeAndLengthPrimFactory.create(null, null);
    @Child protected ToSomConversion convert = ToSomConversionNodeGen.create(null);

    @Specialization
    public final Object doApply(final VirtualFrame frame,
        final TruffleObject fun, final SArray args) {
      Node execNode = Message.createExecute((int) size.executeEvaluated(args)).createNode();

      Object[] arguments;
      if (args.isLongType()) {
        long[] arr = args.getLongStorage(storageType);
        arguments = new Object[arr.length];
        for (int i = 0; i < arr.length; i++) {
          arguments[i] = arr[i];
        }
      } else if (args.isObjectType()) {
        arguments = args.getObjectStorage(storageType);
      } else {
        CompilerDirectives.transferToInterpreter();
        throw new NotYetImplementedException();
      }

      try {
        Object result = ForeignAccess.sendExecute(execNode, frame, fun, arguments);
        return convert.executeEvaluated(result);
      } catch (UnsupportedTypeException | ArityException
          | UnsupportedMessageException e) {
        CompilerDirectives.transferToInterpreter();
        throw new RuntimeException(e);
      }
    }
  }

  static {
    long current = System.nanoTime() / 1000L;
    startMicroTime = current;
    startTime = current / 1000L;
  }
  private static long startTime;
  private static long startMicroTime;
}

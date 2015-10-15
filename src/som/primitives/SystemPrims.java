package som.primitives;

import java.io.IOException;
import java.util.ArrayList;

import som.VM;
import som.compiler.MixinDefinition;
import som.interpreter.Invokable;
import som.interpreter.Method;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vm.Bootstrap;
import som.vm.constants.Nil;
import som.vmobjects.SArray.SImmutableArray;
import som.vmobjects.SObjectWithClass;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.source.SourceSection;


public final class SystemPrims {

  @CompilationFinal public static SObjectWithClass SystemModule;

  @GenerateNodeFactory
  @Primitive("systemModuleObject:")
  public abstract static class SystemModuleObjectPrim extends UnaryExpressionNode {
    @Specialization
    public final Object set(final SObjectWithClass system) {
      SystemModule = system;
      return system;
    }
  }

  @GenerateNodeFactory
  @Primitive("load:")
  public abstract static class LoadPrim extends UnaryExpressionNode {
    @Specialization
    public final Object doSObject(final String moduleName) {
      MixinDefinition module;
      try {
        module = Bootstrap.loadModule(moduleName);
        return module.instantiateModuleClass();
      } catch (IOException e) {
        // TODO convert to SOM exception when we support them
        e.printStackTrace();
      }
      return Nil.nilObject;
    }
  }

  @GenerateNodeFactory
  @Primitive("exit:")
  public abstract static class ExitPrim extends UnaryExpressionNode {
    @Specialization
    public final Object doSObject(final long error) {
      VM.exit((int) error);
      return Nil.nilObject;
    }
  }

  @GenerateNodeFactory
  @Primitive("printString:")
  public abstract static class PrintStringPrim extends UnaryExpressionNode {
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
    @Specialization
    public final Object doSObject(final String argument) {
      VM.println(argument);
      return argument;
    }
  }

  @GenerateNodeFactory
  @Primitive("printStackTrace:")
  public abstract static class PrintStackTracePrim extends UnaryExpressionNode {
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
      Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<Method>() {
        @Override
        public Method visitFrame(final FrameInstance frameInstance) {
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
            location.add(ss.getSource().getName() + ":" + ss.getStartLine());

          } else {
            String id = m.toString();
            method.add(id);
            maxLengthMethod[0] = Math.max(maxLengthMethod[0], id.length());
            location.add("");
          }
          return null;
        }
      });

      for (int i = method.size() - 1; i >= 0; i--) {
        VM.print(String.format("\t%1$-" + (maxLengthMethod[0] + 4) + "s",
          method.get(i)));
        VM.println(location.get(i));
      }
    }
  }

  @GenerateNodeFactory
  @Primitive("vmArguments:")
  public abstract static class VMArgumentsPrim extends UnaryExpressionNode {
    @Specialization
    public final SImmutableArray getArguments(final Object receiver) {
      return new SImmutableArray(VM.getArguments());
    }
  }

  @GenerateNodeFactory
  @Primitive("systemGC:")
  public abstract static class FullGCPrim extends UnaryExpressionNode {
    @Specialization
    public final Object doSObject(final Object receiver) {
      System.gc();
      return true;
    }
  }

  @GenerateNodeFactory
  @Primitive("systemTime:")
  public abstract static class TimePrim extends UnaryExpressionNode {
    @Specialization
    public final long doSObject(final Object receiver) {
      return System.currentTimeMillis() - startTime;
    }
  }

  @GenerateNodeFactory
  @Primitive("systemTicks:")
  public abstract static class TicksPrim extends UnaryExpressionNode {
    @Specialization
    public final long doSObject(final Object receiver) {
      return System.nanoTime() / 1000L - startMicroTime;
    }
  }

  {
    startMicroTime = System.nanoTime() / 1000L;
    startTime = startMicroTime / 1000L;
  }
  private static long startTime;
  private static long startMicroTime;
}

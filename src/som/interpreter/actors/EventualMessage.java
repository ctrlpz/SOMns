package som.interpreter.actors;

import java.util.Arrays;

import som.VM;
import som.compiler.AccessModifier;
import som.interpreter.Types;
import som.interpreter.actors.Actor.ActorProcessingThread;
import som.interpreter.actors.SPromise.SResolver;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.vm.Symbols;
import som.vmobjects.SBlock;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.IndirectCallNode;


public abstract class EventualMessage {
  private static final SSymbol VALUE_SELECTOR = Symbols.symbolFor("value:");

  protected final Object[]  args;
  protected final SResolver resolver;

  protected EventualMessage(final Object[] args, final SResolver resolver) {
    this.args     = args;
    this.resolver = resolver;
  }

  protected abstract Actor   getTarget();
  protected abstract SSymbol getSelector();

  /**
   * A message to a known receiver that is to be executed on the actor owning
   * the receiver.
   *
   * ARGUMENTS: are wrapped eagerly on message creation
   */
  public static final class DirectMessage extends EventualMessage {
    private final SSymbol selector;
    private final Actor   target;
    private final Actor   sender;

    public DirectMessage(final Actor target, final SSymbol selector,
        final Object[] arguments, final Actor sender, final SResolver resolver) {
      super(arguments, resolver);
      this.selector = selector;
      this.sender   = sender;
      this.target   = target;

      assert target != null;
      assert !(args[0] instanceof SFarReference) : "needs to be guaranted by call to this constructor";
      assert !(args[0] instanceof SPromise);
    }

    @Override
    protected Actor getTarget() {
      return target;
    }

    @Override
    protected SSymbol getSelector() {
      return selector;
    }

    @Override
    protected void executeMessage() {
      VM.thisMethodNeedsToBeOptimized("Not Optimized! But also not sure it can be part of compilation anyway");

      Object rcvrObj = args[0];
      assert rcvrObj != null;

      assert !(rcvrObj instanceof SFarReference);
      assert !(rcvrObj instanceof SPromise);

      Dispatchable disp = Types.getClassOf(rcvrObj).lookupMessage(selector, AccessModifier.PUBLIC);

      Object result = disp.invoke(args);
      resolve.resolution(resolver, result);
    }

    @Override
    public String toString() {
      String t = target.toString();
      return "DirectMsg(" + selector.toString() + ", "
        + Arrays.toString(args) +  ", " + t
        + ", sender: " + (sender == null ? "" : sender.toString()) + ")";
    }
  }

  protected static Actor determineTargetAndWrapArguments(final Object[] arguments,
      Actor target, final Actor currentSender, final Actor originalSender) {
    VM.thisMethodNeedsToBeOptimized("not optimized for compilation");

    // target: the owner of the promise that just got resolved
    // however, if a promise gets resolved to a far reference
    // we need to redirect the message to the owner of that far reference

    Object receiver = target.wrapForUse(arguments[0], currentSender);
    assert !(receiver instanceof SPromise) : "TODO: handle this case as well?? Is it possible? didn't think about it";

    if (receiver instanceof SFarReference) {
      // now we are about to send a message to a far reference, so, it
      // is better to just redirect the message back to the current actor
      target   = ((SFarReference) receiver).getActor();
      receiver = ((SFarReference) receiver).getValue();
    }

    arguments[0] = receiver;

    assert !(receiver instanceof SFarReference) : "this should not happen, because we need to redirect messages to the other actor, and normally we just unwrapped this";
    assert !(receiver instanceof SPromise);

    for (int i = 1; i < arguments.length; i++) {
      arguments[i] = target.wrapForUse(arguments[i], originalSender);
    }

    return target;
  }

  /** A message send after a promise got resolved. */
  public abstract static class PromiseMessage extends EventualMessage {
    public static final int PROMISE_RCVR_IDX  = 0;
    public static final int PROMISE_VALUE_IDX = 1;

    protected final Actor originalSender; // initial owner of the arguments

    public PromiseMessage(final Object[] arguments, final Actor originalSender,
        final SResolver resolver) {
      super(arguments, resolver);
      this.originalSender = originalSender;
    }

    public abstract void resolve(final Object rcvr, final Actor target, final Actor sendingActor);
  }

  /**
   * A message that was send with <-: to a promise, and will be delivered
   * after the promise is resolved.
   */
  public static final class PromiseSendMessage extends PromiseMessage {
    private final SSymbol selector;
    protected Actor target;
    protected Actor finalSender;

    protected PromiseSendMessage(final SSymbol selector,
        final Object[] arguments, final Actor originalSender,
        final SResolver resolver) {
      super(arguments, originalSender, resolver);
      this.selector = selector;
    }

    @Override
    public void resolve(final Object rcvr, final Actor target, final Actor sendingActor) {
      determineAndSetTarget(rcvr, target, sendingActor);
    }

    private void determineAndSetTarget(final Object rcvr, final Actor target, final Actor sendingActor) {
      VM.thisMethodNeedsToBeOptimized("not optimized for compilation");

      args[0] = rcvr;
      Actor finalTarget = determineTargetAndWrapArguments(args, target, sendingActor, originalSender);

      this.target      = finalTarget; // for sends to far references, we need to adjust the target
      this.finalSender = sendingActor;
    }

    @Override
    protected SSymbol getSelector() {
      return selector;
    }

    @Override
    protected Actor getTarget() {
      return target;
    }

    @Override
    public String toString() {
      String t;
      if (target == null) {
        t = "null";
      } else {
        t = target.toString();
      }
      return "PSendMsg(" + Arrays.toString(args) +  ", " + t
        + ", sender: " + (finalSender == null ? "" : finalSender.toString()) + ")";
    }

    @Override
    protected void executeMessage() {
      VM.thisMethodNeedsToBeOptimized("Not Optimized! But also not sure it can be part of compilation anyway");

      Object rcvrObj = args[0];
      assert rcvrObj != null;

      assert !(rcvrObj instanceof SFarReference);
      assert !(rcvrObj instanceof SPromise);

      Dispatchable disp = Types.getClassOf(rcvrObj).lookupMessage(selector, AccessModifier.PUBLIC);

      Object result = disp.invoke(args);
      resolve.resolution(resolver, result);
    }
  }

  /** The callback message to be send after a promise is resolved. */
  public static final class PromiseCallbackMessage extends PromiseMessage {

    public PromiseCallbackMessage(final Actor owner, final SBlock callback,
        final SResolver resolver) {
      super(new Object[] {callback, null}, owner, resolver);
    }

    @Override
    public void resolve(final Object rcvr, final Actor target, final Actor sendingActor) {
      setPromiseValue(rcvr, sendingActor);
    }

    /**
     * The value the promise was resolved to on which this callback is
     * registered on.
     *
     * @param resolvingActor - the owner of the value, the promise was resolved to.
     */
    private void setPromiseValue(final Object value, final Actor resolvingActor) {
      args[1] = originalSender.wrapForUse(value, resolvingActor);
    }

    @Override
    protected SSymbol getSelector() {
      return VALUE_SELECTOR;
    }

    @Override
    protected Actor getTarget() {
      return originalSender;
    }

    @Override
    public String toString() {
      return "PCallbackMsg(" + Arrays.toString(args) + ")";
    }

    @Override
    protected void executeMessage() {
      VM.thisMethodNeedsToBeOptimized("Not Optimized! But also not sure it can be part of compilation anyway");

      SBlock rcvrObj = (SBlock) args[0];
      assert rcvrObj != null;

      Object result = rcvrObj.getMethod().invoke(args);
      resolve.resolution(resolver, result);
    }
  }

  protected final void execute() {
    try {
      executeMessage();
    } catch (Throwable t) {
      t.printStackTrace();
      VM.errorExit("Some EventualMessage failed with Exception.");
    }
  }

  private static final ResolvePromiseNode resolve = ResolvePromiseNodeFactory.create(null, null);
  private static final IndirectCallNode indirectCall = Truffle.getRuntime().createIndirectCallNode();

  protected abstract void executeMessage();

  public static Actor getActorCurrentMessageIsExecutionOn() {
    Thread t = Thread.currentThread();
    if (t instanceof ActorProcessingThread) {
      return ((ActorProcessingThread) t).currentlyExecutingActor;
    }
    return mainActor;
  }

  private static void setCurrentActor(final Actor actor) {
    ActorProcessingThread t = (ActorProcessingThread) Thread.currentThread();
    t.currentlyExecutingActor = actor;
  }

  public static void setMainActor(final Actor actor) {
    mainActor = actor;
  }

  private static Actor mainActor;
}

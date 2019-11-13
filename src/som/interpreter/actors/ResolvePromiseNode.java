package som.interpreter.actors;

import bd.primitives.Primitive;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import som.interpreter.SArguments;
import som.interpreter.actors.SPromise.SResolver;
import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.vm.VmSettings;
import tools.asyncstacktraces.ShadowStackEntry;


@GenerateNodeFactory
@Primitive(primitive = "actorsResolve:with:")
public abstract class ResolvePromiseNode extends BinaryExpressionNode {
  @Child protected ResolveNode resolve;

  public ResolvePromiseNode() {
    resolve = ResolveNodeGen.create(null, null, null, null, null);
  }

  @Specialization
  public SResolver normalResolution(final VirtualFrame frame, final SResolver resolver,
                                    final Object result) {
    ShadowStackEntry entry = SArguments.getShadowStackEntry(frame);
    assert entry != null || !VmSettings.ACTOR_ASYNC_STACK_TRACE_STRUCTURE;
    return (SResolver) resolve.executeEvaluated(frame, resolver, result, entry, false, false);
  }
}

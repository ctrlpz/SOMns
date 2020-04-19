package som.interpreter.nodes.nary;

import bd.primitives.nodes.EagerPrimitive;
import bd.tools.nodes.Invocation;
import som.interpreter.nodes.ExpressionNode;
import som.vmobjects.SSymbol;


public abstract class EagerPrimitiveNode extends ExpressionNode
    implements EagerPrimitive, Invocation<SSymbol> {
  protected final SSymbol selector;

  protected EagerPrimitiveNode(final SSymbol selector) {
    this.selector = selector;
  }

  @Override
  public SSymbol getInvocationIdentifier() {
    return selector;
  }

  protected abstract EagerlySpecializableNode getPrimitive();

  public static ExpressionNode unwrapIfNecessary(final ExpressionNode node) {
    if (node instanceof EagerPrimitiveNode) {
      EagerPrimitiveNode n = (EagerPrimitiveNode) node;
      return n.getPrimitive();
    } else {
      return node;
    }
  }
}

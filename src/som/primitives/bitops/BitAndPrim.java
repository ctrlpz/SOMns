package som.primitives.bitops;

import java.math.BigInteger;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;

import som.primitives.Primitive;
import som.primitives.arithmetic.ArithmeticPrim;


@GenerateNodeFactory
@Primitive("int:bitAnd:")
public abstract class BitAndPrim extends ArithmeticPrim {
  protected BitAndPrim(final boolean eagWrap, final SourceSection source) { super(eagWrap, source); }
  protected BitAndPrim(final SourceSection source) { super(false, source); }

  @Specialization
  public final long doLong(final long left, final long right) {
    return left & right;
  }

  @Specialization
  public final Object doBigInteger(final BigInteger left, final BigInteger right) {
    return left.and(right);
  }

  @Specialization
  public final Object doLong(final long left, final BigInteger right) {
    return doBigInteger(BigInteger.valueOf(left), right);
  }

  @Specialization
  public final Object doBigInteger(final BigInteger left, final long right) {
    return doBigInteger(left, BigInteger.valueOf(right));
  }
}

package som.interpreter.actors;

import som.vmobjects.SAbstractObject;
import som.vmobjects.SClass;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;


public final class SFarReference extends SAbstractObject {
  @CompilationFinal private static SClass farReferenceClass;

  private final Actor  actor;
  private final Object value;

  public SFarReference(final Actor actor, final Object value) {
    this.actor = actor;
    this.value = value;
    assert !(value instanceof SFarReference);
  }

  public Actor getActor() {
    return actor;
  }

  /**
   * @return the object the far reference is pointing to
   */
  public Object getValue() {
    return value;
  }

  @Override
  public SClass getSOMClass() {
    return farReferenceClass;
  }

  @Override
  public String toString() {
    return "FarRef[" + value.toString() + ", " + actor.toString() + "]";
  }

  @Override
  public boolean isValue() {
    return true;
  }

  public static void setSOMClass(final SClass cls) {
    assert farReferenceClass == null || cls == null;
    farReferenceClass = cls;
  }
}

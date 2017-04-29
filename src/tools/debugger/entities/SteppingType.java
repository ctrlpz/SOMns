package tools.debugger.entities;

public enum SteppingType {
  STEP_INTO,
  STEP_OVER,
  STEP_RETURN,
  STEP_TO_RECEIVER_MESSAGE,
  STEP_TO_PROMISE_RESOLUTION,
  STEP_TO_NEXT_MESSAGE,
  STEP_RETURN_TO_PROMISE_RESOLUTION,
  STOP,
  RESUME
}

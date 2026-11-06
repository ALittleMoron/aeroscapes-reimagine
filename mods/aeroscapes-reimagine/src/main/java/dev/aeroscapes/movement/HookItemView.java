package dev.aeroscapes.movement;

/** Punchy's first-person arm uses a third-person item transform; context alone is ambiguous. */
public enum HookItemView {
  NONE,
  FIRST_PERSON,
  WORLD;

  public static HookItemView select(
      boolean worldEntities,
      boolean punchyCapture,
      boolean firstPersonContext,
      boolean thirdPersonContext,
      boolean firstPersonCamera) {
    if (worldEntities && !punchyCapture && thirdPersonContext) return WORLD;
    return firstPersonCamera && (firstPersonContext || punchyCapture) ? FIRST_PERSON : NONE;
  }
}

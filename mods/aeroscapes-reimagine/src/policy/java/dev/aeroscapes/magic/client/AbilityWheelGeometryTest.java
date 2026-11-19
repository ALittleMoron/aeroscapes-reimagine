package dev.aeroscapes.magic.client;

public final class AbilityWheelGeometryTest {
  private AbilityWheelGeometryTest() {}

  public static void main(String[] arguments) {
    require(AbilityWheelGeometry.sector(0, 0) == -1, "The center never changes selection");
    require(AbilityWheelGeometry.sector(17, 0) == -1, "Small cursor movement stays neutral");
    for (int slot = 0; slot < 8; slot++) {
      double angle = slot * Math.PI / 4;
      require(
          AbilityWheelGeometry.sector(Math.sin(angle) * 60, -Math.cos(angle) * 60) == slot,
          "Every icon lies in its own fixed sector: " + slot);
      require(
          AbilityWheelGeometry.sector(Math.sin(angle) * 600, -Math.cos(angle) * 600) == slot,
          "Cursor travel beyond the ring keeps the same selection: " + slot);
    }
    double boundary = Math.PI / 8;
    require(
        AbilityWheelGeometry.sector(
                Math.sin(boundary - 0.001) * 60, -Math.cos(boundary - 0.001) * 60)
            == 0,
        "The upper sector owns its side of the boundary");
    require(
        AbilityWheelGeometry.sector(
                Math.sin(boundary + 0.001) * 60, -Math.cos(boundary + 0.001) * 60)
            == 1,
        "The neighboring sector owns the other side of the boundary");
    System.out.println("Ability wheel: eight fixed sectors, neutral center and boundaries passed");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}

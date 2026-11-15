package dev.aeroscapes.preflight;

public final class PreflightReadingsTest {
  private PreflightReadingsTest() {}

  public static void main(String[] args) {
    verifyRequestWindow();
    require(PreflightReadings.supportMass(400, 0.5) == 200, "Altitude pressure changes support");
    require(PreflightReadings.supportMass(400, 0) == 0, "Vacuum supplies no balloon support");
    require(
        PreflightReadings.assessment(100, 150, 1, false, false, true)
            == PreflightReadings.Assessment.COVERS_MASS,
        "A positive margin describes measured balloon support only");
    require(
        PreflightReadings.assessment(100, 99, 1, false, false, true)
            == PreflightReadings.Assessment.BELOW_MASS,
        "Insufficient balloon support is visible");
    require(
        PreflightReadings.assessment(100, 1000, 1, true, false, true)
            == PreflightReadings.Assessment.LEAKING,
        "A positive present margin cannot hide an envelope leak");
    require(
        PreflightReadings.assessment(100, 1000, 1, false, true, true)
            == PreflightReadings.Assessment.INCOMPLETE,
        "Partial data never gives a support assessment");
    require(
        PreflightReadings.assessment(100, 1000, 1, false, false, false)
            == PreflightReadings.Assessment.INCOMPLETE,
        "Zero or differing gravity cannot give a hover assessment");
    require(
        PreflightReadings.assessment(100, 0, 0, false, false, true)
            == PreflightReadings.Assessment.NO_BALLOONS,
        "A propeller-only ship is not described as unable to fly");
    require(
        PreflightReadings.assessment(Double.NaN, 1000, 1, false, false, true)
            == PreflightReadings.Assessment.INCOMPLETE,
        "Invalid native readings cannot give a support assessment");
    System.out.println(
        "Preflight pressure, margin, leaks, incomplete readings, gravity and request coalescing passed");
  }

  private static void verifyRequestWindow() {
    var window = new PreflightRequestWindow();
    window.offer(1);
    require(window.take(100).orElseThrow() == 1, "The first request is answered immediately");
    window.offer(2);
    require(window.take(101).isEmpty(), "Rapid reopening does not cause another survey");
    window.offer(3);
    require(window.take(119).isEmpty(), "Repeated requests retain the survey rate limit");
    require(
        window.take(120).orElseThrow() == 3,
        "The latest reopened screen is answered after cooldown instead of silently timing out");
    require(window.take(121).isEmpty(), "A queued request is answered only once");
    window.offer(4);
    require(window.take(140).orElseThrow() == 4, "Later requests resume ordinary service");
    window.offer(5);
    require(window.take(0).orElseThrow() == 5, "A clock reset cannot strand a pending request");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}

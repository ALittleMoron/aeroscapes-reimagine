package dev.aeroscapes.worldgen;

import java.util.List;

public final class TemplateStructureSupportTest {
  private TemplateStructureSupportTest() {}

  public static void main(String[] arguments) {
    var lower = new TemplateStructureSupport.Interval(0, 0, 0, 10);
    var upper = new TemplateStructureSupport.Interval(0, 0, 12, 20);
    var intervals = List.of(lower, upper);
    require(
        TemplateStructureSupport.support(intervals, 13) == upper,
        "Containing upper body wins over nearby lower body");
    require(
        TemplateStructureSupport.support(intervals, 21) == upper,
        "Nearest supported body below floor");
    require(
        TemplateStructureSupport.support(intervals, 11) == lower,
        "Overhead body cannot support a floor");
    require(
        TemplateStructureSupport.support(intervals, 24) == null,
        "Larger unsupported gap is rejected");
    System.out.println("Template support: stacked-body selection and unsupported gaps passed");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}

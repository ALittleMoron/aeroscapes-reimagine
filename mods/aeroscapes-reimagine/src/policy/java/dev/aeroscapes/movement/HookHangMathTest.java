package dev.aeroscapes.movement;

import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.item.UseAnim;

public final class HookHangMathTest {
  private HookHangMathTest() {}

  public static void main(String[] arguments) {
    repeatedAnimation();
    for (UseAnim action :
        new UseAnim[] {
          UseAnim.BOW, UseAnim.CROSSBOW, UseAnim.SPEAR, UseAnim.SPYGLASS, UseAnim.CUSTOM
        }) require(HookHangMath.twoHandedUse(action), "Two-handed/custom native use owns the arms");
    require(
        !HookHangMath.twoHandedUse(UseAnim.EAT),
        "A free hand may eat while the hook supports the other");
    require(!HookHangMath.twoHandedUse(UseAnim.BLOCK), "A free hand may hold a shield");
    require(HookHangMath.suspended(8, 8, 7), "A taut overhead rope supports the player");
    require(
        HookHangMath.suspended(3, 8, 2),
        "Reach toward an overhead attachment before tension and while hovering in creative");
    require(!HookHangMath.suspended(8, 8, -7), "An anchor below the player cannot suspend them");
    require(!HookHangMath.suspended(Double.NaN, 8, 7), "Reject invalid anchor coordinates");
    require(!HookHangMath.suspended(8, 0, 7), "A zero-length/released rope is not supported");
    float weight = 0;
    for (int tick = 0; tick < 4; tick++) weight = HookHangMath.approach(weight, true, 1);
    require(weight == 1, "Enter over four ticks");
    require(
        HookHangMath.approach(weight, false, 0) == weight, "Multiple render passes do not tick");
    for (int tick = 0; tick < 4; tick++) weight = HookHangMath.approach(weight, false, 1);
    require(weight == 0, "Release restores native animation over four ticks");
    require(
        HookHangMath.approach(1, false, 100) == 0, "Unobserved entities cannot retain old poses");
    System.out.println("Hook hanging support and transition checks passed");
  }

  private static void repeatedAnimation() {
    ModelPart arm = new ModelPart(List.of(), Map.of());
    arm.setPos(5, 2, 0);
    arm.xRot = .2f;
    arm.xScale = .9f;
    HookModelPose nativePose = new HookModelPose(List.of(arm));
    float expected = .2f + .5f * (-2.65f - .2f);
    for (int phase = 0; phase < 4; phase++) {
      nativePose.restore();
      require(
          arm.xRot == .2f && arm.xScale == .9f && arm.x == 5,
          "Native animation starts with its original transforms and scale");
      nativePose = new HookModelPose(List.of(arm));
      arm.xRot += .5f * (-2.65f - arm.xRot);
      arm.xScale = 1.1f;
      arm.x = 6;
      require(
          Math.abs(arm.xRot - expected) < .00001f,
          "Repeated feature passes cannot accumulate a blended hanging pose");
    }
    nativePose.restore();
    require(
        arm.xRot == .2f && arm.xScale == .9f && arm.x == 5,
        "A shared model is restored for the next player");
  }

  private static void require(boolean passed, String message) {
    if (!passed) throw new AssertionError(message);
  }
}

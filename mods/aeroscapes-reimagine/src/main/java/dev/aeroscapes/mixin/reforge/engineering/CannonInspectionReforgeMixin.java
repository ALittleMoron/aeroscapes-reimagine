package dev.aeroscapes.mixin.reforge.engineering;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.equipment.ReforgeProfiles;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(
    targets = "rbasamoyai.createbigcannons.block_armor_properties.BlockArmorInspectionToolItem",
    remap = false)
abstract class CannonInspectionReforgeMixin {
  @Unique
  private static final ResourceLocation INSPECTOR =
      ResourceLocation.fromNamespaceAndPath("createbigcannons", "block_armor_inspection_tool");

  @ModifyExpressionValue(
      method = "addBlockArmorInfo",
      at = @At(value = "INVOKE", target = "Ljava/lang/Integer;toString()Ljava/lang/String;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static String aeroscapes$precision(String precision) {
    return aeroscapes$inspectionGrade() == 4 ? "4" : precision;
  }

  @Inject(method = "addBlockArmorInfo", at = @At("TAIL"))
  @SuppressWarnings({"PMD.UnusedPrivateMethod", "PMD.UnusedFormalParameter", "deprecation"})
  private static void aeroscapes$appendInspection(
      List<Component> tooltip,
      Level level,
      BlockPos position,
      BlockState block,
      CallbackInfo callback,
      @Local(ordinal = 0) double toughness,
      @Local(ordinal = 1) double hardness) {
    int grade = aeroscapes$inspectionGrade();
    if (grade == 0) return;
    tooltip.add(
        aeroscapes$line("target", block.getBlock().getName().withStyle(ChatFormatting.GOLD)));
    // CBC's fallback is the block's explosion resistance for toughness and 1.0 for hardness.
    if (grade >= 2)
      tooltip.add(
          aeroscapes$line(
              "toughness_difference",
              aeroscapes$delta(toughness - block.getBlock().getExplosionResistance(), grade)));
    if (grade >= 3)
      tooltip.add(aeroscapes$line("hardness_difference", aeroscapes$delta(hardness - 1.0, grade)));
  }

  @Unique
  private static Component aeroscapes$line(String key, Component value) {
    return Component.translatable("aeroscapes_reimagine.engineering.inspection." + key, value)
        .withStyle(ChatFormatting.GRAY);
  }

  @Unique
  private static Component aeroscapes$delta(double difference, int grade) {
    String format = grade == 4 ? "%+.4f" : "%+.2f";
    return Component.literal(
            String.format(Locale.getDefault(Locale.Category.FORMAT), format, difference))
        .withStyle(ChatFormatting.GOLD);
  }

  @Unique
  private static int aeroscapes$inspectionGrade() {
    var player = Minecraft.getInstance().player;
    if (player == null) return 0;
    var inspector = BuiltInRegistries.ITEM.get(INSPECTOR);
    var stack = player.getMainHandItem();
    if (!stack.is(inspector)) stack = player.getOffhandItem();
    if (!stack.is(inspector)) return 0;
    return switch (ReforgeProfiles.path(stack)) {
      case "cannon_inspection_fine" -> 1;
      case "cannon_inspection_exceptional" -> 2;
      case "cannon_inspection_epic" -> 3;
      case "cannon_inspection_legendary" -> 4;
      default -> 0;
    };
  }
}

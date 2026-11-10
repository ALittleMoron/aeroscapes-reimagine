package dev.aeroscapes.mixin.reforge.field;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.aeroscapes.equipment.ReforgeProfiles;
import dev.aeroscapes.equipment.field.FieldReforges;
import dev.ryanhcode.sable.Sable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
abstract class OpticsReforgeMixin {
  @Unique
  private static final ResourceLocation aeroscapes$ACTIVE =
      ResourceLocation.fromNamespaceAndPath("exposure", "camera_active");

  @Shadow @Final private Minecraft minecraft;

  @ModifyExpressionValue(
      method = "bobView",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;lerp(FFF)F"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private float aeroscapes$steadyFraming(float sway) {
    if (!(minecraft.getCameraEntity() instanceof Player player)
        || !minecraft.options.getCameraType().isFirstPerson()) return sway;
    String profile = aeroscapes$opticsProfile(player);
    float scale =
        switch (profile) {
          case "spyglass_poor_sway", "camera_poor_sway" -> 1.15F;
          case "spyglass_fine_steady", "camera_fine_steady" -> 0.75F;
          case "spyglass_exceptional_deck", "camera_exceptional_deck" ->
              aeroscapes$onDeck(player) ? 0.35F : 0.75F;
          case "spyglass_epic_frame", "camera_epic_frame" -> 0.45F;
          case "spyglass_legendary_deck", "camera_legendary_deck" ->
              player.isShiftKeyDown() && aeroscapes$onDeck(player) ? 0.0F : 0.45F;
          default -> 1.0F;
        };
    return sway * scale;
  }

  @Unique
  private static boolean aeroscapes$onDeck(Player player) {
    return ModList.get().isLoaded("sable")
        && Sable.HELPER.getTrackingOrVehicleSubLevel(player) != null;
  }

  @Unique
  private static String aeroscapes$opticsProfile(Player player) {
    if (player.isUsingItem() && player.getUseItem().getUseAnimation() == UseAnim.SPYGLASS) {
      return ReforgeProfiles.path(player.getUseItem());
    }
    for (ItemStack stack : player.getHandSlots()) {
      String profile = ReforgeProfiles.path(stack);
      if (profile.startsWith("camera_")
          && Boolean.TRUE.equals(FieldReforges.component(stack, aeroscapes$ACTIVE))) {
        return profile;
      }
    }
    return "";
  }
}

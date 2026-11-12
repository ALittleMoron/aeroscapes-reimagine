package dev.aeroscapes.mixin.atmosphere;

import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.atmosphere.HeldEquipmentRendering;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(
    targets = {
      "com.jesz.createdieselgenerators.content.tools.hammer.HammerItemRenderer",
      "com.jesz.createdieselgenerators.content.tools.wire_cutters.WireCuttersItemRenderer",
      "com.jesz.createdieselgenerators.content.tools.lighter.LighterItemRenderer"
    },
    remap = false)
public abstract class DieselHeldModelMixin {
  @ModifyArg(
      method = "render",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/simibubi/create/foundation/item/render/PartialItemModelRenderer;render(Lnet/minecraft/client/resources/model/BakedModel;I)V"),
      index = 0)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private BakedModel aeroscapes$heldPartial(
      BakedModel original, @Local(argsOnly = true) ItemDisplayContext context) {
    // The outer custom model already applied the hand pose; select only the inner mesh.
    return HeldEquipmentRendering.sandpaper(context, original);
  }
}

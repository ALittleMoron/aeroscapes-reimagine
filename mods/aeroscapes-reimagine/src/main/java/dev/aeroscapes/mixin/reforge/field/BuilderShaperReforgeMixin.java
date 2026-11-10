package dev.aeroscapes.mixin.reforge.field;

import dev.aeroscapes.equipment.ReforgeProfiles;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import team.creative.littletiles.client.render.overlay.PreviewRenderer;
import team.creative.littletiles.client.tool.shaper.ShapePosition;
import team.creative.littletiles.client.tool.shaper.ShapeSelection;

@Mixin(targets = "team.creative.littletiles.client.tool.shaper.LittleToolShaper", remap = false)
abstract class BuilderShaperReforgeMixin {
  @Shadow private boolean marked;
  @Shadow private ShapePosition last;
  @Shadow private List<ShapePosition> positions;
  @Shadow private ShapeSelection builtSelection;

  @Shadow
  public abstract void toggleMark();

  @Shadow
  protected abstract boolean hasPositionChanged();

  @Unique private long aeroscapes$nextShapeClick;

  @Inject(
      method = "interact",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lteam/creative/littletiles/client/tool/shaper/LittleToolShaper;getShapeResult()Lteam/creative/littletiles/client/render/overlay/PreviewRenderer$BoxRenderResult;"),
      cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$beforeNativeShape(
      PreviewRenderer renderer,
      ItemStack stack,
      Level level,
      BlockHitResult hit,
      boolean leftClick,
      CallbackInfoReturnable<Boolean> callback) {
    String rank = ReforgeProfiles.path(stack);
    if (rank.equals("builder_shape_poor_stiff")) {
      long now = level.getGameTime();
      if (now < aeroscapes$nextShapeClick) callback.setReturnValue(true);
      else aeroscapes$nextShapeClick = now + 8;
    } else if (rank.equals("builder_shape_legendary_pin") && !marked) {
      if (last != null && builtSelection != null && hit != null && !hasPositionChanged()) {
        toggleMark();
        renderer
            .player()
            .displayClientMessage(
                Component.translatable("aeroscapes_reimagine.equipment.builder.shape_pinned"),
                true);
      }
      callback.setReturnValue(true);
    }
  }

  @Inject(method = "interact", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$previewAlignment(
      PreviewRenderer renderer,
      ItemStack stack,
      Level level,
      BlockHitResult hit,
      boolean leftClick,
      CallbackInfoReturnable<Boolean> callback) {
    if (!callback.getReturnValue() || hit == null || marked) return;
    String rank = ReforgeProfiles.path(stack);
    String prefix = "aeroscapes_reimagine.equipment.builder.";
    var face = Component.translatable(prefix + "face." + hit.getDirection().getSerializedName());
    switch (rank) {
      case "builder_shape_fine_guide" ->
          renderer
              .player()
              .displayClientMessage(Component.translatable(prefix + "alignment", face), true);
      case "builder_shape_exceptional_layout" -> {
        var pos = hit.getBlockPos();
        renderer
            .player()
            .displayClientMessage(
                Component.translatable(prefix + "layout", face, pos.getX(), pos.getY(), pos.getZ()),
                true);
      }
      case "builder_shape_epic_layout", "builder_shape_legendary_pin" -> {
        var pos = hit.getBlockPos();
        renderer
            .player()
            .displayClientMessage(
                Component.translatable(
                    prefix + "shape_layout",
                    face,
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    positions.size()),
                true);
      }
      default -> {}
    }
  }
}

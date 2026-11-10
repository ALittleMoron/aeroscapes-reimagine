package dev.aeroscapes.equipment.tools;

import dev.aeroscapes.equipment.ReforgeProfiles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;

@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class ShovelGradePreview {
  private ShovelGradePreview() {}

  @SubscribeEvent
  public static void highlight(RenderHighlightEvent.Block event) {
    var player = Minecraft.getInstance().player;
    if (player == null
        || player.isShiftKeyDown()
        || !player.getAbilities().mayBuild
        || !ToolReforges.isSurvivalPlayer(player)
        || !ReforgeProfiles.has(player.getMainHandItem(), "shovel_legendary_grader")) return;

    var level = player.level();
    var center = event.getTarget().getBlockPos();
    var ground = level.getBlockState(center);
    if (!ToolReforges.isLooseGround(ground)
        || !level.getWorldBorder().isWithinBounds(center)
        || NaturalBlockProvenance.isOnShip(level, center)) return;

    var axis = player.getDirection().getClockWise();
    var camera = event.getCamera().getPosition();
    for (int side = -1; side <= 1; side += 2) {
      var neighbor = center.relative(axis, side);
      if (!AreaToolActions.canReapExtra(player, level, neighbor)
          || !level.getWorldBorder().isWithinBounds(neighbor)
          || NaturalBlockProvenance.isOnShip(level, neighbor)) continue;
      var state = level.getBlockState(neighbor);
      if (!state.is(ground.getBlock()) || state.hasBlockEntity()) continue;
      var outline = state.getShape(level, neighbor, CollisionContext.of(player));
      if (outline.isEmpty()) continue;
      var box =
          outline
              .bounds()
              .move(
                  neighbor.getX() - camera.x,
                  neighbor.getY() - camera.y,
                  neighbor.getZ() - camera.z);
      LevelRenderer.renderLineBox(
          event.getPoseStack(),
          event.getMultiBufferSource().getBuffer(RenderType.lines()),
          box,
          0.35F,
          0.80F,
          0.65F,
          0.70F);
    }
  }
}

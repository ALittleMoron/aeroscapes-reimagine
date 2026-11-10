package dev.aeroscapes.equipment.field;

import com.simibubi.create.content.equipment.zapper.ShootableGadgetItemMethods;
import dev.aeroscapes.equipment.ReforgeProfiles;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.sprocketgames.create_aeronautics_throwable_rope_connector.config.ModCommonConfig;
import net.sprocketgames.create_aeronautics_throwable_rope_connector.registry.ModItems;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class RopeLauncherAimPreview {
  private static @Nullable Aim aim;

  private RopeLauncherAimPreview() {}

  @SubscribeEvent
  public static void update(ClientTickEvent.Post event) {
    aim = null;
    var client = Minecraft.getInstance();
    if (client.level == null || client.player == null || !visible(client)) return;
    var player = client.player;
    var hand = aimHand(player);
    if (hand == null || player.isInWater() || player.isInLava()) return;
    boolean mainHand = hand == InteractionHand.MAIN_HAND;
    // These are the muzzle and direction offsets in the pinned launcher use method.
    Vec3 muzzle =
        ShootableGadgetItemMethods.getGunBarrelVec(player, mainHand, new Vec3(0.75, -0.15F, 1.5));
    Vec3 offset =
        ShootableGadgetItemMethods.getGunBarrelVec(player, mainHand, new Vec3(-0.05F, 0, 0))
            .subtract(player.position().add(0, player.getEyeHeight(), 0));
    double factor = RopeLauncherReforge.offsetFactor(player.getItemInHand(hand), true);
    Vec3 motion = player.getLookAngle().add(offset.scale(factor)).normalize().scale(3.0);
    var hit =
        firstSurface(client.level, muzzle, motion, ModCommonConfig.getClampedLauncherMaxDistance());
    if (hit != null) aim = new Aim(client.level, hand, hit);
  }

  private static @Nullable BlockHitResult firstSurface(
      ClientLevel level, Vec3 muzzle, Vec3 motion, double range) {
    Vec3 position = muzzle;
    for (int tick = 0; tick < 32; tick++) {
      Vec3 next = position.add(motion);
      var chunk = new net.minecraft.world.level.ChunkPos(BlockPos.containing(next));
      if (!level.getChunkSource().hasChunk(chunk.x, chunk.z)) return null;
      var hit =
          level.clip(
              new ClipContext(
                  position,
                  next,
                  ClipContext.Block.COLLIDER,
                  ClipContext.Fluid.ANY,
                  CollisionContext.empty()));
      if (hit.getType() == HitResult.Type.BLOCK) {
        Vec3 impact = worldLocation(level, hit, hit.getLocation(), null);
        return level.getFluidState(hit.getBlockPos()).isEmpty()
                && muzzle.distanceTo(impact) <= range
            ? hit
            : null;
      }
      if (muzzle.distanceTo(next) >= range) return null;
      position = next;
      motion = motion.scale(0.99F).add(0, -0.03, 0);
    }
    return null;
  }

  @SubscribeEvent
  public static void render(RenderLevelStageEvent event) {
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || aim == null) return;
    var client = Minecraft.getInstance();
    if (client.level != aim.level()
        || client.player == null
        || !visible(client)
        || aimHand(client.player) != aim.hand()) return;
    var hit = aim.hit();
    Vec3 marker = hit.getLocation().relative(hit.getDirection(), 0.075);
    Vec3 view =
        worldLocation(
                aim.level(), hit, marker, event.getPartialTick().getGameTimeDeltaPartialTick(false))
            .subtract(event.getCamera().getPosition());
    var buffers = client.renderBuffers().bufferSource();
    var lines = RenderType.lines();
    LevelRenderer.renderLineBox(
        event.getPoseStack(),
        buffers.getBuffer(lines),
        new AABB(view, view).inflate(0.065),
        1.0F,
        0.8F,
        0.28F,
        1.0F);
    buffers.endBatch(lines);
  }

  private static Vec3 worldLocation(
      ClientLevel level, BlockHitResult hit, Vec3 local, @Nullable Float partialTick) {
    if (ModList.get().isLoaded("sable")
        && Sable.HELPER.getContaining(level, hit.getBlockPos()) instanceof ClientSubLevel ship) {
      var pose = partialTick == null ? ship.logicalPose() : ship.renderPose(partialTick);
      var world = pose.transformPosition(new Vector3d(local.x, local.y, local.z));
      return new Vec3(world.x(), world.y(), world.z());
    }
    return local;
  }

  private static boolean visible(Minecraft client) {
    return client.screen == null
        && !client.options.hideGui
        && client.options.getCameraType().isFirstPerson()
        && client.getCameraEntity() == client.player
        && ModList.get().isLoaded("create_aeronautics_throwable_rope_connector");
  }

  private static @Nullable InteractionHand aimHand(LocalPlayer player) {
    if (!player.isShiftKeyDown() || player.isSpectator()) return null;
    for (var hand : InteractionHand.values()) {
      var launcher = player.getItemInHand(hand);
      var ammo =
          player.getItemInHand(
              hand == InteractionHand.MAIN_HAND
                  ? InteractionHand.OFF_HAND
                  : InteractionHand.MAIN_HAND);
      if (launcher.is(ModItems.ROPE_CONNECTOR_LAUNCHER.get())
          && ReforgeProfiles.has(launcher, RopeLauncherReforge.AIM_GUIDE)
          && !player.getCooldowns().isOnCooldown(launcher.getItem())
          && ammo.is(ModItems.THROWABLE_ROPE_CONNECTOR.get())) return hand;
    }
    return null;
  }

  private record Aim(ClientLevel level, InteractionHand hand, BlockHitResult hit) {}
}

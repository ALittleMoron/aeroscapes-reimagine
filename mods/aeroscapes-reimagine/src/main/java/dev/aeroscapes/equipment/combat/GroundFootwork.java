package dev.aeroscapes.equipment.combat;

import dev.aeroscapes.races.ClassProgression;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Limits ground-level footwork to a continuous supported path. */
@EventBusSubscriber(modid = "aeroscapes_reimagine")
public final class GroundFootwork {
  private static final double PROBE_SPACING = 0.25;
  private static final int[] SPEED = {2, 3, 4, 4, 3, 2, 1};
  public static final int DURATION = SPEED.length;

  private GroundFootwork() {}

  public static Vec3 motion(Vec3 travel, int tick) {
    return travel.scale(SPEED[tick] / 19.0);
  }

  public static boolean available(Player player) {
    return player.onGround() && canControlMotion(player);
  }

  static boolean canControlMotion(Player player) {
    return player.isAlive()
        && !player.isSpectator()
        && !player.isSleeping()
        && !player.isPassenger()
        && !player.isUsingItem()
        && !player.getAbilities().flying
        && !player.isFallFlying()
        && !player.onClimbable()
        && !player.isInWaterOrBubble()
        && !player.isInLava();
  }

  public static boolean learnedSidestep(Player player) {
    return ClassProgression.hasCombatTalent(player, "duelist_sidestep");
  }

  @SubscribeEvent
  public static void registerLateralInput(RegisterPayloadHandlersEvent event) {
    event
        .registrar("1")
        .playToServer(
            LateralInput.TYPE,
            LateralInput.CODEC,
            (message, context) -> {
              if (context.player() instanceof ServerPlayer player) {
                MartialTechniques.updateLateralInput(player, message.direction());
              }
            });
  }

  public record LateralInput(byte direction) implements CustomPacketPayload {
    public static final Type<LateralInput> TYPE =
        new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                "aeroscapes_reimagine", "footwork_lateral_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LateralInput> CODEC =
        StreamCodec.of(
            (buffer, input) -> buffer.writeByte(input.direction()),
            buffer -> new LateralInput(buffer.readByte()));

    @Override
    public Type<LateralInput> type() {
      return TYPE;
    }
  }

  public static Vec3 supportedTravel(Player player, Vec3 travel) {
    if (travel.lengthSqr() < 1.0E-10) return Vec3.ZERO;
    int probes = (int) Math.ceil(travel.length() / PROBE_SPACING);
    Vec3 stride = travel.scale(1.0 / probes);
    var level = player.level();
    AABB start = player.getBoundingBox();
    var entities = level.getEntityCollisions(player, start.expandTowards(travel));
    int supported = 0;
    while (supported < probes) {
      AABB current = start.move(stride.scale(supported));
      AABB next = current.move(stride);
      AABB ground =
          new AABB(next.minX, next.minY - 0.4, next.minZ, next.maxX, next.minY, next.maxZ);
      if (Entity.collideBoundingBox(player, stride, current, level, entities).distanceToSqr(stride)
              > 1.0E-10
          || !level.getBlockCollisions(player, ground).iterator().hasNext()) break;
      supported++;
    }
    return stride.scale(supported);
  }
}

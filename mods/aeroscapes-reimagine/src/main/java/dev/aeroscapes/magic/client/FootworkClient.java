package dev.aeroscapes.magic.client;

import dev.aeroscapes.equipment.combat.GroundFootwork;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class FootworkClient {
  private static final int INPUT_REFRESH_TICKS = 4;
  private static @Nullable Player subject;
  private static @Nullable Vec3 queued;
  private static Vec3 travel = Vec3.ZERO;
  private static int tick;
  private static int hurtTime;
  private static byte lastLateralInput;
  private static int lastInputTick;

  private FootworkClient() {}

  public static void start(Vec3 movement) {
    Player player = Minecraft.getInstance().player;
    if (player != null && subject == player && tick < GroundFootwork.DURATION) {
      queued = movement;
      return;
    }
    subject = player;
    begin(movement);
  }

  private static void begin(Vec3 movement) {
    queued = null;
    travel = movement;
    tick = 0;
    hurtTime = subject == null ? 0 : subject.hurtTime;
  }

  private static void reportLateralInput(MovementInputUpdateEvent event) {
    var player = event.getEntity();
    var input = event.getInput();
    byte direction =
        input.left == input.right || !GroundFootwork.learnedSidestep(player)
            ? 0
            : (byte) (input.left ? 1 : -1);
    int currentTick = player.tickCount;
    if (direction == lastLateralInput
        && (direction == 0
            || currentTick >= lastInputTick && currentTick - lastInputTick < INPUT_REFRESH_TICKS))
      return;
    PacketDistributor.sendToServer(new GroundFootwork.LateralInput(direction));
    lastLateralInput = direction;
    lastInputTick = currentTick;
  }

  @SubscribeEvent
  public static void movement(MovementInputUpdateEvent event) {
    reportLateralInput(event);
    if (event.getEntity() != subject) {
      subject = null;
      queued = null;
      return;
    }
    Player player = event.getEntity();
    if (!GroundFootwork.available(player) || player.hurtTime > hurtTime) {
      subject = null;
      queued = null;
      return;
    }
    if (tick == GroundFootwork.DURATION) {
      if (queued == null) {
        subject = null;
        player.setDeltaMovement(0, player.getDeltaMovement().y, 0);
        return;
      }
      begin(queued);
    }
    hurtTime = player.hurtTime;

    Vec3 intended = GroundFootwork.motion(travel, tick++);
    Vec3 advance = GroundFootwork.supportedTravel(player, intended);
    // The normal player movement packet reports each stride through vanilla collision checks.
    player.setDeltaMovement(advance.x, player.getDeltaMovement().y, advance.z);
    event.getInput().forwardImpulse = 0;
    event.getInput().leftImpulse = 0;
    event.getInput().jumping = false;
    if (advance.distanceToSqr(intended) > 1.0E-10) tick = GroundFootwork.DURATION;
  }
}

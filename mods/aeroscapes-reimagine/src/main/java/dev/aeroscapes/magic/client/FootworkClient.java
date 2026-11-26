package dev.aeroscapes.magic.client;

import dev.aeroscapes.equipment.combat.GroundFootwork;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import org.jetbrains.annotations.Nullable;

@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class FootworkClient {
  private static @Nullable Player subject;
  private static @Nullable Vec3 queued;
  private static Vec3 travel = Vec3.ZERO;
  private static int tick;
  private static int hurtTime;

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

  @SubscribeEvent
  public static void movement(MovementInputUpdateEvent event) {
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

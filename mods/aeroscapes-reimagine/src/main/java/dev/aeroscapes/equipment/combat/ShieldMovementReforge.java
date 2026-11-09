package dev.aeroscapes.equipment.combat;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class ShieldMovementReforge {
  private ShieldMovementReforge() {}

  @SubscribeEvent
  public static void movement(MovementInputUpdateEvent event) {
    if (ShieldReforges.nimble(event.getEntity())) {
      event.getInput().forwardImpulse *= 1.10F;
      event.getInput().leftImpulse *= 1.10F;
    }
  }
}

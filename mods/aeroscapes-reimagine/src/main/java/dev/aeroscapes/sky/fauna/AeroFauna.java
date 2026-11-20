package dev.aeroscapes.sky.fauna;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class AeroFauna {
  private static final String NAMESPACE = "aeroscapes_reimagine";
  private static final DeferredRegister<EntityType<?>> TYPES =
      DeferredRegister.create(Registries.ENTITY_TYPE, NAMESPACE);
  public static final DeferredHolder<EntityType<?>, EntityType<SkyRay>> SKY_RAY =
      TYPES.register(
          "sky_ray",
          () ->
              EntityType.Builder.of(SkyRay::new, MobCategory.MISC)
                  .sized(2.0F, 1.5F)
                  .clientTrackingRange(10)
                  .build(NAMESPACE + ":sky_ray"));
  public static final DeferredHolder<EntityType<?>, EntityType<CloudJelly>> CLOUD_JELLY =
      TYPES.register(
          "cloud_jelly",
          () ->
              EntityType.Builder.of(CloudJelly::new, MobCategory.MISC)
                  .sized(
                      1.0F,
                      CloudJellyAnatomy.of(CloudJellyAnatomy.Form.FRINGE).height()
                          / CloudJellyAnatomy.SIZE_UNITS)
                  .clientTrackingRange(10)
                  .build(NAMESPACE + ":cloud_jelly"));

  private AeroFauna() {}

  public static void register(IEventBus bus) {
    TYPES.register(bus);
    bus.addListener(AeroFauna::attributes);
    NeoForge.EVENT_BUS.addListener(FaunaEncounters::tick);
  }

  private static void attributes(EntityAttributeCreationEvent event) {
    event.put(SKY_RAY.get(), SkyRay.createAttributes().build());
    event.put(CLOUD_JELLY.get(), CloudJelly.createAttributes().build());
  }
}

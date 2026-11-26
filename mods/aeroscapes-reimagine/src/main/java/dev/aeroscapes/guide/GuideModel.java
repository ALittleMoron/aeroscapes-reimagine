package dev.aeroscapes.guide;

import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class GuideModel {
  private static final ModelResourceLocation COVER =
      ModelResourceLocation.standalone(
          ResourceLocation.parse("aeroscapes_reimagine:item/field_guide"));
  private static final ModelResourceLocation INVENTORY =
      new ModelResourceLocation(
          ResourceLocation.parse("aeroscapes_reimagine:field_guide"), "inventory");

  private GuideModel() {}

  @SubscribeEvent
  public static void register(ModelEvent.RegisterAdditional event) {
    event.register(COVER);
  }

  @SubscribeEvent
  public static void bake(ModelEvent.ModifyBakingResult event) {
    // Modonomicon requests this inventory key even though the cover is a standalone model.
    var cover = event.getModels().get(COVER);
    if (cover != null) event.getModels().put(INVENTORY, cover);
  }
}

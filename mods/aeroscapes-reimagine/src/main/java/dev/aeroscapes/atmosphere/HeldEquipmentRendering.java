package dev.aeroscapes.atmosphere;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Native custom renderers need their incoming context before they render inner models. */
@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class HeldEquipmentRendering {
  private static final ModelResourceLocation LUNCHBOX = model("nutritionalbalance", "lunchbox");
  private static final ModelResourceLocation ALTIMETER =
      model("supplementaries", "altimeter_template");
  private static final Map<BakedModel, BakedModel> ALTIMETERS = new IdentityHashMap<>();

  private HeldEquipmentRendering() {}

  public static boolean held(ItemDisplayContext context) {
    return switch (context) {
      case FIRST_PERSON_LEFT_HAND,
          FIRST_PERSON_RIGHT_HAND,
          THIRD_PERSON_LEFT_HAND,
          THIRD_PERSON_RIGHT_HAND ->
          true;
      default -> false;
    };
  }

  public static BakedModel lunchbox(ItemDisplayContext context, BakedModel original) {
    return held(context) ? Minecraft.getInstance().getModelManager().getModel(LUNCHBOX) : original;
  }

  public static BakedModel sandpaper(ItemDisplayContext context, BakedModel original) {
    if (!held(context)) return original;
    // Create renders the body with NONE after the outer hand transform already ran.
    return original.applyTransform(
        context,
        new PoseStack(),
        context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
            || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND);
  }

  public static BakedModel altimeter(ItemDisplayContext context, BakedModel original) {
    if (!held(context)) return original;
    return ALTIMETERS.computeIfAbsent(original, HeldEquipmentRendering::altimeter);
  }

  private static BakedModel altimeter(BakedModel original) {
    var manager = Minecraft.getInstance().getModelManager();
    var template =
        manager.getModel(
            ModelResourceLocation.standalone(
                ResourceLocation.fromNamespaceAndPath(
                    "supplementaries", "item/altimeter_template")));
    return altimeter(original, template, manager.getModel(ALTIMETER));
  }

  static BakedModel altimeter(BakedModel original, BakedModel template, BakedModel held) {
    var quads =
        new ArrayList<>(
            original.getQuads(null, null, RandomSource.create(0), ModelData.EMPTY, null));
    quads.removeAll(template.getQuads(null, null, RandomSource.create(0), ModelData.EMPTY, null));
    quads.addAll(held.getQuads(null, null, RandomSource.create(0), ModelData.EMPTY, null));
    return new BakedModelWrapper<>(original) {
      @Override
      public java.util.List<BakedQuad> getQuads(
          net.minecraft.world.level.block.state.BlockState state,
          net.minecraft.core.Direction side,
          RandomSource random) {
        return side == null ? quads : java.util.List.of();
      }

      @Override
      public java.util.List<BakedQuad> getQuads(
          net.minecraft.world.level.block.state.BlockState state,
          net.minecraft.core.Direction side,
          RandomSource random,
          ModelData data,
          net.minecraft.client.renderer.RenderType renderType) {
        return side == null ? quads : java.util.List.of();
      }

      @Override
      public java.util.List<BakedModel> getRenderPasses(
          net.minecraft.world.item.ItemStack stack, boolean fabulous) {
        return java.util.List.of(this);
      }

      @Override
      public BakedModel applyTransform(
          ItemDisplayContext context, PoseStack pose, boolean leftHand) {
        original.applyTransform(context, pose, leftHand);
        return this;
      }
    };
  }

  @SubscribeEvent
  public static void register(ModelEvent.RegisterAdditional event) {
    event.register(LUNCHBOX);
    event.register(ALTIMETER);
  }

  @SubscribeEvent
  public static void reload(ModelEvent.BakingCompleted event) {
    ALTIMETERS.clear();
  }

  private static ModelResourceLocation model(String namespace, String item) {
    return ModelResourceLocation.standalone(
        ResourceLocation.fromNamespaceAndPath(namespace, "item/equipment/held/" + item));
  }
}

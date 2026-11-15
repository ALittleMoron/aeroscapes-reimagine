package dev.aeroscapes.atmosphere;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.math.Transformation;
import com.yision.fluidlogistics.registry.AllDataComponents;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.QuadTransformers;
import net.neoforged.neoforge.client.model.SeparateTransformsModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import org.joml.Vector3f;

/** Keeps the native copper icon while drawing its actual fluid in the held vessel. */
@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class CopperBucketHeldRendering {
  private static final ModelResourceLocation BODY = model("copper_bucket");
  private static final ModelResourceLocation FILL = model("copper_bucket_fluid");
  private static final float FILL_BOTTOM = 1.8F / 16;
  private static final float GRIP_HEIGHT = 6.25F / 16;
  private static final float CAPACITY = 5000;

  private CopperBucketHeldRendering() {}

  public static BakedModel nativeIcon(BakedModel resolved) {
    // The provider rejects 3D bucket models before retexturing their flat icon.
    return resolved instanceof SeparateTransformsModel.Baked
        ? resolved.applyTransform(ItemDisplayContext.GUI, new PoseStack(), false)
        : resolved;
  }

  public static BakedModel resolve(BakedModel nativeModel, ItemStack stack) {
    var content =
        stack.getOrDefault(AllDataComponents.COPPER_BUCKET_CONTENT, SimpleFluidContent.EMPTY);
    return route(nativeModel, () -> held(nativeModel, content));
  }

  public static int luminance(ItemStack stack) {
    if (!BuiltInRegistries.ITEM
        .getKey(stack.getItem())
        .toString()
        .equals("fluidlogistics:copper_bucket")) return 0;
    var content =
        stack.getOrDefault(AllDataComponents.COPPER_BUCKET_CONTENT, SimpleFluidContent.EMPTY);
    if (content.isEmpty()) return 0;
    var fluid = content.copy();
    var bucket = BuiltInRegistries.ITEM.getKey(fluid.getFluid().getBucket());
    var profile = BucketContentRendering.profile(bucket);
    return Math.max(
        fluid.getFluidType().getLightLevel(fluid), profile == null ? 0 : profile.light());
  }

  static BakedModel route(BakedModel nativeModel, Supplier<BakedModel> heldModel) {
    return new BakedModelWrapper<>(nativeModel) {
      @Override
      public BakedModel applyTransform(
          ItemDisplayContext context, PoseStack pose, boolean leftHand) {
        return (HeldEquipmentRendering.held(context) ? heldModel.get() : originalModel)
            .applyTransform(context, pose, leftHand);
      }
    };
  }

  private static BakedModel held(BakedModel nativeModel, SimpleFluidContent content) {
    var minecraft = Minecraft.getInstance();
    var manager = minecraft.getModelManager();
    var body = manager.getModel(BODY);
    if (body == manager.getMissingModel()) return nativeModel;
    if (content.isEmpty()) return combine(body, null, false);
    var template = manager.getModel(FILL);
    if (template == manager.getMissingModel()) return nativeModel;
    var fluid = content.copy();
    var bucket = BuiltInRegistries.ITEM.getKey(fluid.getFluid().getBucket());
    return combine(
        body,
        BucketHeldRendering.contents(
            template, bucket, fluid, fluid.getAmount() / CAPACITY, nativeModel),
        fluid.getFluid().getFluidType().isLighterThanAir());
  }

  static BakedModel combine(BakedModel body, BakedModel fill, boolean gas) {
    return new BakedModelWrapper<>(body) {
      @Override
      public BakedModel applyTransform(
          ItemDisplayContext context, PoseStack pose, boolean leftHand) {
        var transformed = originalModel.applyTransform(context, pose, leftHand);
        if (gas) {
          // ItemRenderer centers the mesh next; keep the carrying grip fixed during inversion.
          pose.translate(0, GRIP_HEIGHT, 0);
          pose.mulPose(Axis.XP.rotationDegrees(-35));
          pose.mulPose(Axis.ZP.rotationDegrees(180));
          pose.translate(0, -GRIP_HEIGHT, 0);
        }
        return transformed == originalModel ? this : combine(transformed, fill, gas);
      }

      @Override
      public List<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous) {
        return fill == null ? List.of(this) : List.of(this, fill);
      }
    };
  }

  static BakedModel fill(
      BakedModel template, TextureAtlasSprite sprite, int amount, boolean emissive) {
    Map<Direction, List<BakedQuad>> sides = new EnumMap<>(Direction.class);
    for (var side : Direction.values())
      sides.put(side, fillQuads(template, side, sprite, amount, emissive));
    var unculled = fillQuads(template, null, sprite, amount, emissive);
    return new BakedModelWrapper<>(template) {
      @Override
      public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
        return side == null ? unculled : sides.get(side);
      }

      @Override
      public List<BakedQuad> getQuads(
          BlockState state,
          Direction side,
          RandomSource random,
          ModelData data,
          RenderType renderType) {
        return getQuads(state, side, random);
      }

      @Override
      public List<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous) {
        return List.of(this);
      }

      @Override
      public List<RenderType> getRenderTypes(ItemStack stack, boolean fabulous) {
        return List.of(
            emissive
                ? RenderType.entityTranslucentEmissive(InventoryMenu.BLOCK_ATLAS)
                : RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
      }
    };
  }

  private static List<BakedQuad> fillQuads(
      BakedModel template,
      Direction side,
      TextureAtlasSprite sprite,
      int amount,
      boolean emissive) {
    return template.getQuads(null, side, RandomSource.create(42), ModelData.EMPTY, null).stream()
        .map(quad -> fillQuad(quad, sprite, amount, emissive))
        .toList();
  }

  static BakedQuad fillQuad(
      BakedQuad template, TextureAtlasSprite sprite, int amount, boolean emissive) {
    var vertices = template.getVertices().clone();
    float fraction = Math.clamp(amount / CAPACITY, 0, 1);
    float width = (3.7F + (4.8F - 3.7F) * fraction) / 4.8F;
    var source = template.getSprite();
    for (int vertex = 0; vertex < 4; vertex++) {
      int start = vertex * IQuadTransformer.STRIDE;
      int horizontal = start + IQuadTransformer.UV0;
      int vertical = horizontal + 1;
      vertices[horizontal] =
          Float.floatToRawIntBits(
              sprite.getU(source.getUOffset(Float.intBitsToFloat(vertices[horizontal]))));
      vertices[vertical] =
          Float.floatToRawIntBits(
              sprite.getV(source.getVOffset(Float.intBitsToFloat(vertices[vertical]))));
    }
    var result =
        new BakedQuad(
            vertices,
            1,
            template.getDirection(),
            sprite,
            template.isShade(),
            template.hasAmbientOcclusion());
    var transform =
        new Transformation(
            new Vector3f(.5F * (1 - width), FILL_BOTTOM * (1 - fraction), .5F * (1 - width)),
            null,
            new Vector3f(width, fraction, width),
            null);
    QuadTransformers.applying(transform).processInPlace(result);
    if (emissive) QuadTransformers.settingMaxEmissivity().processInPlace(result);
    return result;
  }

  @SubscribeEvent
  public static void register(ModelEvent.RegisterAdditional event) {
    if (!ItemModelResources.enabled()) return;
    event.register(BODY);
    event.register(FILL);
  }

  private static ModelResourceLocation model(String name) {
    return ModelResourceLocation.standalone(
        ResourceLocation.fromNamespaceAndPath("fluidlogistics", "item/equipment/held/" + name));
  }
}

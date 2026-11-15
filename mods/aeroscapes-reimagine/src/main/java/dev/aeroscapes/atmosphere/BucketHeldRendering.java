package dev.aeroscapes.atmosphere;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import punchy.client.renderer.layer.ArmToolTuning;

/** Selects volume after provider state resolution, immediately before the one hand transform. */
@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class BucketHeldRendering {
  private static final ModelResourceLocation BODY = model("bucket_body");
  private static final ModelResourceLocation FILL = model("bucket_fill");
  private static final ModelResourceLocation VANILLA_BODY =
      ModelResourceLocation.standalone(
          ResourceLocation.withDefaultNamespace("item/equipment/held/bucket"));

  private BucketHeldRendering() {}

  static float surface(ItemStack stack) {
    return halfContents(stack) ? 5.4F / 16 : 9F / 16;
  }

  public static int luminance(ItemStack stack) {
    if (BuiltInRegistries.ITEM
        .getKey(stack.getItem())
        .toString()
        .equals("spawn:angler_fish_bucket"))
      return anglerLight(
          stack.getOrDefault(DataComponents.BUCKET_ENTITY_DATA, CustomData.EMPTY).copyTag());
    return CopperBucketHeldRendering.luminance(stack);
  }

  static int anglerLight(CompoundTag data) {
    // Spawn restores bucket health and variant, but not the live fish's air supply.
    boolean alive = !data.contains("Health", Tag.TAG_ANY_NUMERIC) || data.getFloat("Health") > 0;
    return alive ? 4 : 0;
  }

  private static boolean halfContents(ItemStack stack) {
    return switch (BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()) {
      case "naturalist:duck_bucket",
          "spawn:baby_seal_bucket",
          "spawn:coastal_crab_bucket",
          "spawn:captured_octopus",
          "quark:slime_in_a_bucket" ->
          true;
      default -> false;
    };
  }

  public static BakedModel select(
      BakedModel nativeModel, ItemStack stack, ItemDisplayContext context) {
    if (!HeldEquipmentRendering.held(context)) return nativeModel;
    var level = Minecraft.getInstance().level;
    return select(
        nativeModel,
        level == null
            ? BucketPresentation.physical(stack.getItem())
            : BucketPresentation.physical(stack, level.registryAccess()),
        authored(stack),
        context,
        () -> held(stack, nativeModel));
  }

  public static boolean needsVolume(ItemStack stack) {
    if (!ItemModelResources.enabled()) return false;
    var minecraft = Minecraft.getInstance();
    if (minecraft.level == null
        || !BucketPresentation.physical(stack, minecraft.level.registryAccess())) return false;
    return !authored(stack);
  }

  private static boolean authored(ItemStack stack) {
    return stack.is(Items.BUCKET)
        || BuiltInRegistries.ITEM
            .getKey(stack.getItem())
            .toString()
            .equals("fluidlogistics:copper_bucket");
  }

  public static ArmToolTuning.Profile armProfile(ArmToolTuning.Profile nativeProfile) {
    return new ArmToolTuning.Profile(
        nativeProfile.scale,
        nativeProfile.offX,
        nativeProfile.offY - .52F,
        nativeProfile.offZ,
        nativeProfile.rotX,
        nativeProfile.rotY,
        nativeProfile.rotZ);
  }

  static BakedModel select(
      BakedModel nativeModel,
      boolean physical,
      boolean authored,
      ItemDisplayContext context,
      Supplier<BakedModel> held) {
    return physical && !authored && HeldEquipmentRendering.held(context) ? held.get() : nativeModel;
  }

  private static BakedModel held(ItemStack stack, BakedModel nativeModel) {
    var minecraft = Minecraft.getInstance();
    var manager = minecraft.getModelManager();
    var body =
        manager.getModel(
            stack.is(Items.WATER_BUCKET) || stack.is(Items.LAVA_BUCKET) ? VANILLA_BODY : BODY);
    var template = manager.getModel(FILL);
    var fluid = FluidUtil.getFluidContained(stack).orElseGet(() -> fluid(stack));
    float fraction =
        halfContents(stack)
            ? .5F
            : stack.is(Items.MILK_BUCKET) ? milkAmount(nativeModel) / 5000F : 1;
    var contents =
        contents(
            template, BuiltInRegistries.ITEM.getKey(stack.getItem()), fluid, fraction, nativeModel);
    return new Vessel(
        CopperBucketHeldRendering.combine(
            body, contents, !fluid.isEmpty() && fluid.getFluidType().isLighterThanAir()));
  }

  static BakedModel contents(
      BakedModel template,
      ResourceLocation bucket,
      FluidStack fluid,
      float fraction,
      BakedModel icon) {
    if (fraction <= 0) return null;
    var minecraft = Minecraft.getInstance();
    var profile = BucketContentRendering.profile(bucket);
    boolean nativeSurface =
        bucket.toString().equals("aeronautics:levitite_blend_bucket")
            || bucket.toString().equals("create:chocolate_bucket")
            || bucket.toString().equals("minecraft:water_bucket")
            || bucket.toString().equals("minecraft:lava_bucket");
    if (nativeSurface) profile = null;
    ResourceLocation texture = profile == null ? null : profile.sprite();
    int tint = profile == null ? -1 : profile.tint();
    boolean emissive = profile != null && profile.light() > 0;
    if (!fluid.isEmpty()) {
      var extensions = IClientFluidTypeExtensions.of(fluid.getFluid());
      if (texture == null) {
        texture = extensions.getStillTexture(fluid);
        if (texture == null) texture = extensions.getStillTexture();
        tint = extensions.getTintColor(fluid);
      }
      emissive |= fluid.getFluidType().getLightLevel(fluid) > 0;
    }
    if (bucket.toString().equals("quark:slime_in_a_bucket")) {
      var nativeIcon = CopperBucketHeldRendering.nativeIcon(icon);
      for (var quad :
          nativeIcon.getQuads(null, null, RandomSource.create(42), ModelData.EMPTY, null)) {
        var name = quad.getSprite().contents().name();
        if (name.getNamespace().equals("quark") && name.getPath().contains("slime_in_a_bucket")) {
          texture = name;
          break;
        }
      }
    }
    if (texture == null) return null;
    var sprite = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(texture);
    if (nativeSurface)
      return tinted(
          CopperBucketHeldRendering.fill(template, sprite, Math.round(fraction * 5000), emissive),
          tint);
    double ticks =
        minecraft.level == null
            ? 0
            : minecraft.level.getGameTime()
                + minecraft.getTimer().getGameTimeDeltaPartialTick(false);
    return BucketContentRendering.create(
        template, sprite, profile, fraction, tint, emissive, ticks);
  }

  static int milkAmount(BakedModel nativeModel) {
    for (var quad :
        nativeModel.getQuads(null, null, RandomSource.create(42), ModelData.EMPTY, null)) {
      var sprite = quad.getSprite();
      if (sprite == null) continue;
      switch (sprite.contents().name().toString()) {
        case "eatinganimation:item/milk_bucket_drinking_0":
          return 2500;
        case "eatinganimation:item/milk_bucket_drinking_1":
          return 1250;
        case "eatinganimation:item/milk_bucket_drinking_2":
          return 0;
        default:
          break;
      }
    }
    return 5000;
  }

  static FluidStack fluid(ItemStack stack) {
    if (halfContents(stack)
        && !BuiltInRegistries.ITEM
            .getKey(stack.getItem())
            .toString()
            .equals("quark:slime_in_a_bucket")) return new FluidStack(Fluids.WATER, 1000);
    if (stack.getItem() instanceof BucketItem bucket)
      return bucket.content == Fluids.EMPTY
          ? FluidStack.EMPTY
          : new FluidStack(bucket.content, 1000);
    var level = Minecraft.getInstance().level;
    if (level != null
        && BucketPresentation.captureContainer(stack, level.registryAccess())
            .is(Items.WATER_BUCKET)) return new FluidStack(Fluids.WATER, 1000);
    return FluidStack.EMPTY;
  }

  static BakedModel tinted(BakedModel source, int color) {
    Map<Direction, List<BakedQuad>> sides = new EnumMap<>(Direction.class);
    for (var side : Direction.values()) sides.put(side, tinted(source, side, color));
    var unculled = tinted(source, null, color);
    return new BakedModelWrapper<>(source) {
      @Override
      public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
        return side == null ? unculled : sides.get(side);
      }

      @Override
      public List<BakedQuad> getQuads(
          BlockState state, Direction side, RandomSource random, ModelData data, RenderType type) {
        return getQuads(state, side, random);
      }

      @Override
      public List<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous) {
        return List.of(this);
      }
    };
  }

  private static List<BakedQuad> tinted(BakedModel source, Direction side, int color) {
    return source.getQuads(null, side, RandomSource.create(42), ModelData.EMPTY, null).stream()
        .map(
            quad -> {
              var vertices = quad.getVertices().clone();
              int packed = color & 0xFF00FF00 | (color >>> 16 & 255) | (color & 255) << 16;
              for (int vertex = 0; vertex < 4; vertex++)
                vertices[vertex * IQuadTransformer.STRIDE + IQuadTransformer.COLOR] = packed;
              return new BakedQuad(
                  vertices,
                  -1,
                  quad.getDirection(),
                  quad.getSprite(),
                  quad.isShade(),
                  quad.hasAmbientOcclusion());
            })
        .toList();
  }

  public static boolean vessel(BakedModel model) {
    return model instanceof Vessel;
  }

  @SubscribeEvent
  public static void register(ModelEvent.RegisterAdditional event) {
    if (!ItemModelResources.enabled()) return;
    event.register(BODY);
    event.register(FILL);
    event.register(VANILLA_BODY);
  }

  @SubscribeEvent
  public static void reloaded(ModelEvent.BakingCompleted event) {
    BucketContentRendering.clear();
  }

  private static ModelResourceLocation model(String name) {
    return ModelResourceLocation.standalone(
        ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", "item/" + name));
  }

  private static final class Vessel extends BakedModelWrapper<BakedModel> {
    private Vessel(BakedModel body) {
      super(body);
    }

    @Override
    public BakedModel applyTransform(ItemDisplayContext context, PoseStack pose, boolean leftHand) {
      originalModel.applyTransform(context, pose, leftHand);
      return this;
    }
  }
}

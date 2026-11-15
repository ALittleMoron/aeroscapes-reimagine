package dev.aeroscapes.atmosphere;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.ninni.teallib.api.common.entity.catchable.Catchable;
import com.ninni.teallib.api.common.entity.catchable.CustomInventoryRendering;
import com.ninni.teallib.api.common.item.CatchableMobBucketItem;
import dev.aeroscapes.mixin.atmosphere.BucketPreviewEntityAccess;
import dev.aeroscapes.mixin.atmosphere.MobBucketTypeAccess;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.model.pipeline.VertexConsumerWrapper;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.joml.Vector3f;

/** Displays isolated provider entities inside the held vessel without spawning or ticking them. */
@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class BucketCreatures {
  static final int SWORDFISH_REFERENCE_TICK = 1;
  private static final ResourceLocation SLIME_BUCKET = id("slime_in_a_bucket");
  private static final Map<ItemStack, Preview> PREVIEWS = new WeakHashMap<>();
  private static final Map<Entity, Placement> PLACEMENTS = new WeakHashMap<>();
  private static WeakReference<ClientLevel> cachedLevel = new WeakReference<>(null);

  private BucketCreatures() {}

  public static boolean containsCreature(ItemStack stack) {
    return !stack.isEmpty()
        && (stack.getItem() instanceof MobBucketItem
            || stack.getItem() instanceof CatchableMobBucketItem
            || slimeBucket(stack)
            || BuiltInRegistries.ITEM
                .getKey(stack.getItem())
                .toString()
                .equals("spawn:captured_octopus"));
  }

  public static void render(
      ItemStack stack,
      PoseStack pose,
      MultiBufferSource buffers,
      int packedLight,
      boolean leftHand) {
    var minecraft = Minecraft.getInstance();
    var level = minecraft.level;
    if (level != cachedLevel.get()) {
      PREVIEWS.clear();
      PLACEMENTS.clear();
      cachedLevel = new WeakReference<>(level);
    }
    if (level == null || !containsCreature(stack)) return;
    var entity = preview(stack, level);
    if (entity == null) return;
    float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
    entity.tickCount = (int) (level.getGameTime() & Integer.MAX_VALUE);
    if (minecraft.player != null) {
      entity.setPos(minecraft.player.position());
      entity.setOldPosAndRot();
    }
    var placement =
        PLACEMENTS.computeIfAbsent(
            entity,
            preview ->
                measure(
                    preview,
                    partialTick,
                    packedLight,
                    BucketHeldRendering.surface(stack),
                    !BucketHeldRendering.fluid(stack).isEmpty()));
    float age = entity.tickCount + partialTick;
    float hop = hop(slimeBucket(stack) && Boolean.TRUE.equals(component(stack, "excited")), age);
    pose.pushPose();
    try {
      var entityType = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
      pose.translate(.5F, placement.y() + hop, .5F + depth(entityType));
      pose.mulPose(
          Axis.YP.rotationDegrees(
              facing(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()), leftHand)));
      pose.translate(placement.x() - .5F, 0, placement.z() - .5F);
      pose.scale(placement.scale(), placement.scale(), placement.scale());
      pose.mulPose(Axis.ZP.rotationDegrees(placement.tilt()));
      pose.mulPose(Axis.XP.rotationDegrees(roll(entityType)));
      draw(entity, pose, buffers, packedLight, partialTick);
    } finally {
      pose.popPose();
    }
  }

  private static Entity preview(ItemStack stack, ClientLevel level) {
    var cached = PREVIEWS.get(stack);
    if (cached != null && ItemStack.isSameItemSameComponents(cached.snapshot(), stack)) {
      var entity = cached.entity().get();
      if (entity != null) return entity;
    }
    var type = entityType(stack);
    if (type == null) return null;
    var entity = type.create(level);
    if (entity == null) return null;
    restore(entity, stack);
    if (entity instanceof Catchable && entity instanceof CustomInventoryRendering inventory)
      inventory.setRenderedInTooltip(true);
    var fluid = BucketHeldRendering.fluid(stack);
    boolean water = fluid.is(Fluids.WATER);
    ((BucketPreviewEntityAccess) entity).aeroscapes$bucketWaterPose(water);
    ((BucketPreviewEntityAccess) entity).aeroscapes$bucketInitialized(false);
    if (!fluid.isEmpty())
      ((BucketPreviewEntityAccess) entity)
          .aeroscapes$bucketFluidHeight()
          .put(fluid.getFluidType(), 1);
    entity.setSilent(true);
    entity.setCustomNameVisible(false);
    entity.setYRot(90);
    entity.yRotO = 90;
    entity.setXRot(0);
    entity.xRotO = 0;
    if (entity instanceof LivingEntity living) {
      living.yBodyRot = 90;
      living.yBodyRotO = 90;
      living.yHeadRot = 90;
      living.yHeadRotO = 90;
    }
    // Weak values prevent entity -> level -> player inventory from retaining the weak stack key.
    PREVIEWS.put(stack, new Preview(stack.copyWithCount(1), new WeakReference<>(entity)));
    return entity;
  }

  private static EntityType<?> entityType(ItemStack stack) {
    if (BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals("spawn:captured_octopus"))
      return BuiltInRegistries.ENTITY_TYPE.get(
          ResourceLocation.fromNamespaceAndPath("spawn", "octopus"));
    if (stack.getItem() instanceof MobBucketItem bucket)
      return ((MobBucketTypeAccess) bucket).aeroscapes$bucketEntityType();
    if (stack.getItem() instanceof CatchableMobBucketItem bucket) {
      var saved = data(stack);
      if (saved.contains("id")) {
        var stored = EntityType.byString(saved.getString("id"));
        if (stored.isPresent()) return stored.get();
      }
      return bucket.entityTypeSupplier.get();
    }
    return slimeBucket(stack) ? EntityType.SLIME : null;
  }

  static void restore(Entity entity, ItemStack stack) {
    var name = stack.get(DataComponents.CUSTOM_NAME);
    if (name != null) entity.setCustomName(name);
    CompoundTag data = data(stack);
    if (slimeBucket(stack)) entity.load(data);
    else if (entity instanceof Bucketable bucketable) {
      bucketable.loadFromBucketTag(data);
      bucketable.setFromBucket(true);
    } else if (entity instanceof Mob mob && entity instanceof Catchable catchable) {
      catchable.loadDataFromTag(mob, data);
      catchable.setHasBeenCaught(true);
    }
    if (data.hasUUID("UUID")) entity.setUUID(data.getUUID("UUID"));
    else entity.setUUID(new UUID(1, 1));
  }

  private static CompoundTag data(ItemStack stack) {
    if (slimeBucket(stack)) {
      var value = component(stack, "slime_nbt");
      return value instanceof CustomData data ? data.copyTag() : new CompoundTag();
    }
    return stack.getOrDefault(DataComponents.BUCKET_ENTITY_DATA, CustomData.EMPTY).copyTag();
  }

  private static Object component(ItemStack stack, String name) {
    var type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(id(name));
    return type == null ? null : stack.get(type);
  }

  private static boolean slimeBucket(ItemStack stack) {
    return SLIME_BUCKET.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
  }

  private static Placement measure(
      Entity entity, float partialTick, int packedLight, float surface, boolean wet) {
    var entityType = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
    boolean swordfish = entityType.toString().equals("tide:blazing_swordfish");
    int originalAge = entity.tickCount;
    if (swordfish) {
      // Tide treats age zero as a bind-pose sentinel; fit the same active swim frame every time.
      entity.tickCount = SWORDFISH_REFERENCE_TICK;
      partialTick = 0;
    }
    try {
      var bounds = new Bounds();
      draw(entity, new PoseStack(), bounds.buffers(), packedLight, partialTick);
      if (!bounds.present) {
        bounds.addVertex(-entity.getBbWidth() / 2, 0, -entity.getBbWidth() / 2);
        bounds.addVertex(entity.getBbWidth() / 2, entity.getBbHeight(), entity.getBbWidth() / 2);
      }
      var type = entityType.toString();
      float tilt = lean(entityType);
      if (tilt == 0 && !wet && surface >= 9F / 16) tilt = -75;
      else if (tilt == 0
          && !(entity instanceof CustomInventoryRendering inventory
              && inventory.renderedInTooltip())
          && surface >= 9F / 16
          && bounds.maximum.y - bounds.minimum.y
              < Math.hypot(bounds.maximum.x - bounds.minimum.x, bounds.maximum.z - bounds.minimum.z)
                  * .5F) tilt = -30;
      if (tilt != 0) {
        var unposed = bounds;
        bounds = new Bounds();
        var pose = new PoseStack();
        pose.mulPose(Axis.ZP.rotationDegrees(tilt));
        pose.mulPose(Axis.XP.rotationDegrees(roll(entityType)));
        draw(entity, pose, bounds.buffers(), packedLight, partialTick);
        if (!bounds.present) {
          bounds = unposed;
          tilt = 0;
        }
      }
      var placement =
          type.equals("tide:blazing_swordfish")
              ? perchedPlacement(bounds, surface, -.22F)
              : surface < 9F / 16
                  ? floatingPlacement(
                      bounds,
                      surface,
                      type.equals("naturalist:duck")
                          ? -.05F
                          : type.equals("spawn:octopus") ? -.075F : 0)
                  : placement(bounds.minimum, bounds.maximum, surface, wet);
      return new Placement(placement.scale(), placement.x(), placement.y(), placement.z(), tilt);
    } finally {
      if (swordfish) entity.tickCount = originalAge;
    }
  }

  static float facing(ResourceLocation type, boolean leftHand) {
    return switch (type.toString()) {
      case "naturalist:duck", "minecraft:slime", "tide:blazing_swordfish" -> leftHand ? 180 : 0;
      case "spawn:coastal_crab" -> 90;
      case "spawn:seal" -> leftHand ? 135 : 0;
      case "spawn:octopus" -> leftHand ? 180 : 0;
      default -> 0;
    };
  }

  static float lean(ResourceLocation type) {
    return switch (type.toString()) {
      case "naturalist:duck" -> -20;
      case "spawn:seal" -> -30;
      case "tide:blazing_swordfish" -> -55;
      case "minecraft:slime" -> -45;
      default -> 0;
    };
  }

  static float roll(ResourceLocation type) {
    return type.toString().equals("tide:blazing_swordfish") ? 80 : 0;
  }

  static float depth(ResourceLocation type) {
    return type.toString().equals("tide:blazing_swordfish") ? .10F : 0;
  }

  static Placement floatingPlacement(Bounds bounds, float surface, float outward) {
    float vertical = Math.max(1F / 16, bounds.maximum.y - bounds.minimum.y);
    float low = 0;
    float high = Math.min(1.25F, .52F / vertical);
    for (int iteration = 0; iteration < 24; iteration++) {
      float scale = (low + high) / 2;
      var placement = floatingPlacement(bounds, surface, outward, scale);
      if (inside(bounds.vertices, placement)) low = scale;
      else high = scale;
    }
    return floatingPlacement(bounds, surface, outward, low);
  }

  static Placement perchedPlacement(Bounds bounds, float surface, float outward) {
    float vertical = Math.max(1F / 16, bounds.maximum.y - bounds.minimum.y);
    float low = 0;
    float high = Math.min(1.25F, .4F / vertical);
    var largest = perchedPlacement(bounds, surface, outward, high);
    if (insidePerch(bounds.vertices, largest)) return largest;
    // Above-rim head clearance is not monotonic in scale; locate the largest valid interval.
    float step = high / 64;
    for (int index = 1; index <= 64; index++) {
      float scale = high - step * index;
      if (insidePerch(bounds.vertices, perchedPlacement(bounds, surface, outward, scale))) {
        low = scale;
        high = scale + step;
        break;
      }
    }
    for (int iteration = 0; iteration < 24; iteration++) {
      float scale = (low + high) / 2;
      var placement = perchedPlacement(bounds, surface, outward, scale);
      if (insidePerch(bounds.vertices, placement)) low = scale;
      else high = scale;
    }
    return perchedPlacement(bounds, surface, outward, low);
  }

  private static Placement perchedPlacement(
      Bounds bounds, float surface, float outward, float scale) {
    var placement = floatingPlacement(bounds, surface, outward, scale);
    return new Placement(scale, placement.x(), placement.y() - 1.3F / 16, placement.z(), 0);
  }

  private static boolean insidePerch(List<Vector3f> vertices, Placement placement) {
    for (float depth : new float[] {-.10F, .10F})
      if (!insideBelowRim(
          vertices,
          new Placement(placement.scale(), placement.x(), placement.y(), placement.z() + depth, 0)))
        return false;
    return true;
  }

  private static boolean insideBelowRim(List<Vector3f> vertices, Placement placement) {
    var points =
        vertices.stream()
            .map(
                vertex ->
                    new Vector3f(vertex)
                        .mul(placement.scale())
                        .add(placement.x(), placement.y(), placement.z()))
            .toList();
    for (var point : points) if (!belowRimClearance(point.x, point.y, point.z)) return false;
    float rim = 9.8F / 16;
    for (int start = 0; start + 3 < points.size(); start += 4)
      for (int edge = 0; edge < 4; edge++) {
        var first = points.get(start + edge);
        var second = points.get(start + (edge + 1) % 4);
        if ((first.y > rim) == (second.y > rim)) continue;
        float fraction = (rim - first.y) / (second.y - first.y);
        if (!belowRimClearance(
            first.x + (second.x - first.x) * fraction,
            rim,
            first.z + (second.z - first.z) * fraction)) return false;
      }
    return true;
  }

  private static boolean belowRimClearance(float x, float y, float z) {
    // The fish's bill may protrude above the rim; submerged geometry must clear the walls.
    if (y > 9.8F / 16) return true;
    float radius = (3.7F + (y * 16 - 1.8F) / 8 * 1.242F - .04F) / 16;
    float horizontal = Math.abs(x - .5F);
    float depth = Math.abs(z - .5F);
    return Math.max(horizontal, depth) + Math.min(horizontal, depth) * .41421356F <= radius;
  }

  private static Placement floatingPlacement(
      Bounds bounds, float surface, float outward, float scale) {
    float height = (bounds.maximum.y - bounds.minimum.y) * scale;
    return new Placement(
        scale,
        .5F + outward - (bounds.minimum.x + bounds.maximum.x) * scale / 2,
        Math.max(1.8F / 16, surface - height * .15F) - bounds.minimum.y * scale,
        .5F - (bounds.minimum.z + bounds.maximum.z) * scale / 2,
        0);
  }

  private static boolean inside(List<Vector3f> vertices, Placement placement) {
    for (var vertex : vertices) {
      float x = Math.abs(vertex.x * placement.scale() + placement.x() - .5F);
      float z = Math.abs(vertex.z * placement.scale() + placement.z() - .5F);
      float y = Math.min(9.8F, (vertex.y * placement.scale() + placement.y()) * 16);
      // The actual octagonal cavity tapers from a 3.7px floor to a 4.942px mouth.
      float radius = (3.7F + (y - 1.8F) / 8 * 1.242F - .12F) / 16;
      if (Math.max(x, z) + Math.min(x, z) * .41421356F > radius) return false;
    }
    return true;
  }

  static Placement placement(Vector3f minimum, Vector3f maximum, float surface, boolean wet) {
    float horizontal =
        Math.max(1F / 16, (float) Math.hypot(maximum.x - minimum.x, maximum.z - minimum.z));
    float vertical = Math.max(1F / 16, maximum.y - minimum.y);
    boolean shallow = surface < 9F / 16;
    float scale =
        Math.min(
            shallow ? 1.25F : 1,
            Math.min(
                (shallow ? .55F : wet ? .5F : .4F) / horizontal,
                (shallow ? .52F : wet ? .4F : .8F) / vertical));
    float bottom =
        shallow || wet
            ? Math.max(1.8F / 16, surface - vertical * scale * (shallow ? .15F : .35F))
            : 1.8F / 16;
    return new Placement(
        scale,
        .5F - (minimum.x + maximum.x) * scale / 2,
        bottom - minimum.y * scale,
        .5F - (minimum.z + maximum.z) * scale / 2,
        0);
  }

  static float hop(boolean excited, float age) {
    return excited ? .035F * Math.abs((float) Math.sin(age * .6F)) : 0;
  }

  private static <E extends Entity> void draw(
      E entity, PoseStack pose, MultiBufferSource buffers, int packedLight, float partialTick) {
    var renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
    if (BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals("spawn:seal"))
      pose.mulPose(Axis.YP.rotationDegrees(90));
    renderer.render(
        entity,
        90,
        partialTick,
        pose,
        buffers,
        light(packedLight, renderer.getPackedLightCoords(entity, partialTick)));
  }

  static int light(int heldLight, int entityLight) {
    return LightTexture.pack(
        Math.max(LightTexture.block(heldLight), LightTexture.block(entityLight)),
        Math.max(LightTexture.sky(heldLight), LightTexture.sky(entityLight)));
  }

  @SubscribeEvent
  public static void unload(LevelEvent.Unload event) {
    if (event.getLevel() == cachedLevel.get()) {
      PREVIEWS.clear();
      PLACEMENTS.clear();
      cachedLevel.clear();
    }
  }

  private static ResourceLocation id(String name) {
    return ResourceLocation.fromNamespaceAndPath("quark", name);
  }

  record Placement(float scale, float x, float y, float z, float tilt) {}

  static final class Bounds implements VertexConsumer {
    final Vector3f minimum = new Vector3f(Float.POSITIVE_INFINITY);
    final Vector3f maximum = new Vector3f(Float.NEGATIVE_INFINITY);
    final List<Vector3f> vertices = new ArrayList<>();
    private boolean present;

    MultiBufferSource buffers() {
      // Native foil layers reject identical buffer delegates.
      return type -> new VertexConsumerWrapper(this) {};
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
      if (Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z)) {
        var point = new Vector3f(x, y, z);
        minimum.min(point);
        maximum.max(point);
        vertices.add(point);
        present = true;
      }
      return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
      return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
      return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
      return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
      return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
      return this;
    }
  }

  private record Preview(ItemStack snapshot, WeakReference<Entity> entity) {}
}

package dev.aeroscapes.sky.cargo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class LostCargoRenderer extends EntityRenderer<LostCargoBalloon> {
  private record Ring(float y, float radius) {}

  private static final int GORES = 12;
  private static final Ring[] PROFILE = {
    new Ring(1.08F, 0.26F),
    new Ring(1.26F, 0.49F),
    new Ring(1.57F, 0.76F),
    new Ring(1.94F, 0.88F),
    new Ring(2.25F, 0.78F),
    new Ring(2.50F, 0.53F),
    new Ring(2.65F, 0.26F)
  };
  private static final ResourceLocation IVORY = fabric("white");
  private static final ResourceLocation AMBER = fabric("orange");
  private static final ResourceLocation BRASS = ResourceLocation.parse("create:brass_casing");

  private final BlockRenderDispatcher blocks;

  private LostCargoRenderer(EntityRendererProvider.Context context) {
    super(context);
    blocks = context.getBlockRenderDispatcher();
  }

  @SubscribeEvent
  public static void register(EntityRenderersEvent.RegisterRenderers event) {
    event.registerEntityRenderer(LostCargoBalloon.TYPE.get(), LostCargoRenderer::new);
  }

  @Override
  public void render(
      LostCargoBalloon balloon,
      float yaw,
      float partialTick,
      PoseStack pose,
      MultiBufferSource buffers,
      int light) {
    envelope(
        pose.last(), buffers.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS)), light);
    rig(pose, buffers, light);
    super.render(balloon, yaw, partialTick, pose, buffers, light);
  }

  private static ResourceLocation fabric(String color) {
    return ResourceLocation.fromNamespaceAndPath(
        "aeronautics", "block/envelope_block/envelope_" + color);
  }

  private static void envelope(PoseStack.Pose pose, VertexConsumer vertices, int light) {
    var atlas = Minecraft.getInstance().getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS);
    var amber = atlas.getSprite(AMBER);
    var ivory = atlas.getSprite(IVORY);
    for (int gore = 0; gore < GORES; gore++) {
      var fabric = gore % 3 == 0 ? amber : ivory;
      for (int row = 1; row < PROFILE.length; row++) {
        Vector3f lower = point(PROFILE[row - 1], gore);
        Vector3f upper = point(PROFILE[row], gore);
        Vector3f nextUpper = point(PROFILE[row], gore + 1);
        Vector3f nextLower = point(PROFILE[row - 1], gore + 1);
        Vector3f normal = new Vector3f(upper).sub(lower).cross(new Vector3f(nextLower).sub(lower));
        normal.normalize();
        float lowerV = (2.68F - lower.y) / 1.60F;
        float upperV = (2.68F - upper.y) / 1.60F;
        vertex(pose, vertices, fabric, lower, normal, 0, lowerV, light);
        vertex(pose, vertices, fabric, upper, normal, 0, upperV, light);
        vertex(pose, vertices, fabric, nextUpper, normal, 1, upperV, light);
        vertex(pose, vertices, fabric, nextLower, normal, 1, lowerV, light);
      }
      Ring top = PROFILE[PROFILE.length - 1];
      Vector3f cap = new Vector3f(0, 2.68F, 0);
      Vector3f edge = point(top, gore);
      Vector3f next = point(top, gore + 1);
      Vector3f normal = new Vector3f(cap).sub(edge).cross(new Vector3f(next).sub(edge)).normalize();
      vertex(pose, vertices, fabric, edge, normal, 0, 0.25F, light);
      vertex(pose, vertices, fabric, cap, normal, 0.5F, 0, light);
      vertex(pose, vertices, fabric, next, normal, 1, 0.25F, light);
      vertex(pose, vertices, fabric, next, normal, 1, 0.25F, light);
    }
  }

  private static Vector3f point(Ring ring, int gore) {
    double angle = (gore % GORES) * Math.TAU / GORES;
    return new Vector3f(
        (float) Math.cos(angle) * ring.radius, ring.y, (float) Math.sin(angle) * ring.radius);
  }

  private static void vertex(
      PoseStack.Pose pose,
      VertexConsumer vertices,
      TextureAtlasSprite texture,
      Vector3f point,
      Vector3f normal,
      float u,
      float v,
      int light) {
    vertices
        .addVertex(pose, point.x, point.y, point.z)
        .setColor(-1)
        .setUv(texture.getU(u), texture.getV(v))
        .setOverlay(OverlayTexture.NO_OVERLAY)
        .setLight(light)
        .setNormal(pose, normal.x, normal.y, normal.z);
  }

  private void rig(PoseStack pose, MultiBufferSource buffers, int light) {
    var brass =
        BuiltInRegistries.BLOCK.getOptional(BRASS).orElse(Blocks.COPPER_BLOCK).defaultBlockState();
    var wood = Blocks.SPRUCE_PLANKS.defaultBlockState();
    var barrel = Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP);
    block(pose, buffers, light, barrel, -0.34F, 0.07F, -0.34F, 0.68F, 0.48F, 0.68F);
    block(pose, buffers, light, brass, -0.26F, 0.99F, -0.26F, 0.52F, 0.10F, 0.52F);
    for (float x : new float[] {-0.31F, 0.31F}) {
      block(pose, buffers, light, wood, x - 0.075F, 0.0F, -0.41F, 0.15F, 0.13F, 0.82F);
      block(pose, buffers, light, wood, x - 0.075F, 0.47F, -0.41F, 0.15F, 0.12F, 0.82F);
      for (float z : new float[] {-0.32F, 0.32F}) {
        block(pose, buffers, light, brass, x - 0.06F, 0.57F, z - 0.06F, 0.12F, 0.07F, 0.12F);
        Vector3f lower = new Vector3f(x, 0.62F, z);
        Vector3f direction = new Vector3f(x * 0.70F, 1.04F, z * 0.70F).sub(lower);
        float length = direction.length();
        pose.pushPose();
        pose.translate(lower.x, lower.y, lower.z);
        pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), direction.normalize()));
        block(
            pose,
            buffers,
            light,
            Blocks.CHAIN.defaultBlockState(),
            -0.13F,
            0,
            -0.13F,
            0.26F,
            length,
            0.26F);
        pose.popPose();
      }
    }
  }

  private void block(
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      BlockState block,
      float x,
      float y,
      float z,
      float width,
      float height,
      float depth) {
    pose.pushPose();
    pose.translate(x, y, z);
    pose.scale(width, height, depth);
    blocks.renderSingleBlock(
        block, pose, buffers, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
    pose.popPose();
  }

  @Override
  public ResourceLocation getTextureLocation(LostCargoBalloon balloon) {
    return InventoryMenu.BLOCK_ATLAS;
  }
}

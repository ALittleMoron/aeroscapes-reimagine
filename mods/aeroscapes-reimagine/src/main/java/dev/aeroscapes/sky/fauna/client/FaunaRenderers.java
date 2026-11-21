package dev.aeroscapes.sky.fauna.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.aeroscapes.sky.fauna.AeroFauna;
import dev.aeroscapes.sky.fauna.CloudJelly;
import dev.aeroscapes.sky.fauna.CloudJellyAnatomy.Form;
import dev.aeroscapes.sky.fauna.SkyRay;
import java.util.EnumMap;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LightLayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class FaunaRenderers {
  private static final ModelLayerLocation RAY =
      new ModelLayerLocation(
          ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", "sky_ray"), "main");
  private static final ModelLayerLocation JELLY =
      new ModelLayerLocation(
          ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", "cloud_jelly"), "main");

  private FaunaRenderers() {}

  @SubscribeEvent
  public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
    event.registerLayerDefinition(RAY, SkyRayModel::layer);
    event.registerLayerDefinition(JELLY, CloudJellyModel::layer);
  }

  @SubscribeEvent
  public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
    event.registerEntityRenderer(AeroFauna.SKY_RAY.get(), RayRenderer::new);
    event.registerEntityRenderer(AeroFauna.CLOUD_JELLY.get(), JellyRenderer::new);
  }

  private static ResourceLocation texture(String name) {
    return ResourceLocation.fromNamespaceAndPath(
        "aeroscapes_reimagine", "textures/entity/" + name + ".png");
  }

  private static final class RayRenderer extends MobRenderer<SkyRay, SkyRayModel> {
    private static final ResourceLocation SKIN = texture("sky_ray");

    private RayRenderer(EntityRendererProvider.Context context) {
      super(context, new SkyRayModel(context.bakeLayer(RAY)), 1.1F);
    }

    @Override
    public ResourceLocation getTextureLocation(SkyRay ray) {
      return SKIN;
    }
  }

  static ResourceLocation jellyTexture(Form form, float width, boolean pulse) {
    var skin = JellyRenderer.SKINS.get(form);
    if (width >= 4) return pulse ? skin.largePulse() : skin.largeIdle();
    return pulse ? skin.pulse() : skin.idle();
  }

  private static final class JellyRenderer extends MobRenderer<CloudJelly, CloudJellyModel> {
    private static final EnumMap<Form, MedusaSkin> SKINS = new EnumMap<>(Form.class);

    static {
      for (Form form : Form.values()) {
        String name = form == Form.FRINGE ? "cloud_jelly" : "cloud_jelly_" + form.id();
        SKINS.put(
            form,
            new MedusaSkin(
                texture(name),
                texture(name + "_pulse"),
                texture(name + "_large"),
                texture(name + "_large_pulse")));
      }
    }

    private record MedusaSkin(
        ResourceLocation idle,
        ResourceLocation pulse,
        ResourceLocation largeIdle,
        ResourceLocation largePulse) {}

    private JellyRenderer(EntityRendererProvider.Context context) {
      super(context, new CloudJellyModel(context.bakeLayer(JELLY)), 0.33F);
      addLayer(new CloudJellyGlow(this, entityRenderDispatcher));
    }

    @Override
    protected void setupRotations(
        CloudJelly jelly, PoseStack stack, float age, float yaw, float tick, float scale) {
      if (jelly.isAlive()) {
        // The shared swim frame rotates the crown and its strike surfaces about the same pivot.
        stack.mulPose(Axis.YP.rotationDegrees(180));
      } else {
        super.setupRotations(jelly, stack, age, 0, tick, scale);
      }
    }

    @Override
    protected void scale(CloudJelly jelly, PoseStack stack, float tick) {
      float diameter = jelly.bellDiameter();
      stack.scale(diameter, diameter, diameter);
    }

    @Override
    public ResourceLocation getTextureLocation(CloudJelly jelly) {
      return jellyTexture(jelly.form(), jelly.getBbWidth(), jelly.isPulsing());
    }

    @Override
    protected int getBlockLightLevel(CloudJelly jelly, BlockPos pos) {
      // LambDynamicLights adds a source's own luminance to EntityRenderer's return value.
      return jelly.isOnFire() ? 15 : jelly.level().getBrightness(LightLayer.BLOCK, pos);
    }
  }
}

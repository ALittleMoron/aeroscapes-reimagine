package dev.aeroscapes.atmosphere;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderCleanupEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.core.dependencyInjection.ModAccessorInjector;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IIrisAccessor;
import java.io.IOException;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class AstronomySkyDepth {
  private static ShaderInstance shader;
  private static int depthTexture;
  private static float farDepth;
  private static boolean observingDistantHorizons;

  private AstronomySkyDepth() {}

  @SubscribeEvent
  public static void registerShader(RegisterShadersEvent event) throws IOException {
    var mods = ModList.get();
    if (!mods.isLoaded("spyglass_astronomy")
        || !mods.isLoaded("distanthorizons")
        || !mods.isLoaded("iris")) return;
    event.registerShader(
        new ShaderInstance(
            event.getResourceProvider(),
            ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", "astronomy_sky_depth"),
            DefaultVertexFormat.POSITION_COLOR),
        loaded -> shader = loaded);
    if (!observingDistantHorizons) {
      DistantHorizonsDepthObserver.bind();
      observingDistantHorizons = true;
    }
  }

  @SubscribeEvent
  public static void beginFrame(RenderFrameEvent.Pre event) {
    depthTexture = 0;
  }

  @Nullable
  public static ShaderInstance prepare() {
    if (depthTexture == 0 || shader == null) return null;
    shader.setSampler("DhDepthSampler", depthTexture);
    shader.safeGetUniform("DhFarDepth").set(farDepth);
    return shader;
  }

  private static final class DistantHorizonsDepthObserver extends DhApiBeforeRenderCleanupEvent {
    static void bind() {
      DhApi.events.bind(DhApiBeforeRenderCleanupEvent.class, new DistantHorizonsDepthObserver());
    }

    @Override
    public void beforeCleanup(DhApiEventParam<DhApiRenderParam> event) {
      var iris = ModAccessorInjector.INSTANCE.get(IIrisAccessor.class);
      if (iris == null || !iris.isShaderPackInUse() || iris.isRenderingShadowPass()) return;
      var renderer = DhApi.Delayed.renderProxy;
      var texture = renderer.getDhDepthTextureGlId();
      if (texture.success && texture.payload > 0) {
        depthTexture = texture.payload;
        farDepth = renderer.getDepthDirection().farDepth;
      }
    }
  }
}

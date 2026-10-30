package dev.aeroscapes.atmosphere;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import java.util.EnumMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.projectile.FishingHook;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import punchy.client.render.FishingLineProjection;
import punchy.client.render.PunchyArmRenderer;

/** Bounded, opt-in observations of the provider and the final vanilla line draw. */
@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class RodTrace {
  private static final EnumMap<HumanoidArm, Sample> HANDS = new EnumMap<>(HumanoidArm.class);
  private static int deadline;
  private static int rows;
  private static long frame;
  private static String model = "none";

  private RodTrace() {}

  @SubscribeEvent
  public static void register(RegisterClientCommandsEvent event) {
    event
        .getDispatcher()
        .register(
            Commands.literal("aeroscapes_rod_trace")
                .executes(
                    context -> {
                      LocalPlayer player = Minecraft.getInstance().player;
                      if (player == null) return 0;
                      deadline = player.tickCount + 120;
                      rows = 120;
                      HANDS.clear();
                      model = "none";
                      LogUtils.getLogger()
                          .info("[Aeroscapes rod trace] start; max 120 rows / 120 client ticks");
                      context
                          .getSource()
                          .sendSuccess(
                              () ->
                                  Component.translatable("message.aeroscapes_reimagine.rod_trace"),
                              false);
                      return 1;
                    }));
  }

  public static boolean active() {
    LocalPlayer player = Minecraft.getInstance().player;
    if (rows == 0) return false;
    if (player == null || player.tickCount >= deadline) {
      finish();
      return false;
    }
    return true;
  }

  public static void beginWorld() {
    if (active()) frame++;
  }

  public static String beginModel(BakedModel baked) {
    String previous = model;
    model = "other:" + baked.getClass().getName();
    var manager = Minecraft.getInstance().getModelManager();
    for (String name :
        new String[] {
          "held_fishing_cast_fp",
          "held_fishing_cast_fp_left",
          "held_fishing_rod_fp",
          "held_fishing_rod_fp_left"
        }) {
      if (manager.getModel(
              ModelResourceLocation.standalone(
                  ResourceLocation.parse("minecraft:item/rods/" + name)))
          == baked) {
        model = name;
        break;
      }
    }
    row(
        "model="
            + model
            + " cast="
            + (Minecraft.getInstance().player.fishing != null)
            + " capture="
            + PunchyArmRenderer.isCapturingItemRenderer()
            + " gui3d="
            + baked.isGui3d()
            + " custom="
            + baked.isCustomRenderer());
    return previous;
  }

  public static void endModel(String previous) {
    model = previous;
  }

  public static void tip(LocalPlayer player, HumanoidArm arm, Matrix4f pose, Vector3f tip) {
    if (!active() || player != Minecraft.getInstance().player || player.fishing == null) return;
    if (pose == null || tip == null) {
      row("tip missing pose or geometry");
      return;
    }
    Vector3f ndc = project(pose, tip);
    if (ndc == null || !ndc.isFinite()) {
      row("tip has no finite projection");
      return;
    }
    HANDS.put(arm, new Sample(frame, player.tickCount, ndc));
    row("tip arm=" + arm + " model=" + model + " local=" + tip + " ndc=" + ndc);
  }

  public static void endpoint(FishingHook hook, float x, float y, float z, PoseStack.Pose pose) {
    if (!active() || Minecraft.getInstance().player.fishing != hook) return;
    Vector3f ndc = project(pose.pose(), new Vector3f(x, y + 0.25F, z));
    if (ndc == null || !ndc.isFinite()) {
      row("line has no finite projection");
      return;
    }
    LocalPlayer player = Minecraft.getInstance().player;
    for (var entry : HANDS.entrySet()) {
      Sample hand = entry.getValue();
      row(
          "line arm="
              + entry.getKey()
              + " camera="
              + Minecraft.getInstance().options.getCameraType()
              + " handAgeFrames="
              + (frame - hand.frame)
              + " handAgeTicks="
              + (player.tickCount - hand.tick)
              + " ndc="
              + ndc
              + " delta="
              + new Vector3f(ndc).sub(hand.ndc));
    }
    if (HANDS.isEmpty())
      row(
          "line without hand capture camera="
              + Minecraft.getInstance().options.getCameraType()
              + " ndc="
              + ndc);
  }

  private static Vector3f project(Matrix4f pose, Vector3f position) {
    return FishingLineProjection.projectTip(
        new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose),
        RenderSystem.getProjectionMatrix(),
        new Vector3f(position));
  }

  private static void row(String value) {
    if (rows == 0) return;
    LogUtils.getLogger().info("[Aeroscapes rod trace] frame={} {}", frame, value);
    if (--rows == 0) finish();
  }

  private static void finish() {
    rows = 0;
    HANDS.clear();
    LogUtils.getLogger().info("[Aeroscapes rod trace] end");
  }

  private record Sample(long frame, int tick, Vector3f ndc) {}
}

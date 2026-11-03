package dev.aeroscapes.movement;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yori3o.yo_hooks.common.entity.HookEntity;
import com.yori3o.yo_hooks.common.util.HookStackState;
import com.yori3o.yo_hooks.common.util.interfaces.PlayerWithHookData;
import java.util.EnumMap;
import java.util.UUID;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import punchy.client.render.FishingLineProjection;
import punchy.client.render.PunchyArmRenderer;

/** Match the rope's local endpoint to the handle's emitted first-person pose. */
public final class HookLineAttachment {
  private static final EnumMap<HumanoidArm, Attachment> ATTACHMENTS =
      new EnumMap<>(HumanoidArm.class);
  private static final Matrix4f WORLD_VIEW = new Matrix4f();
  private static final Matrix4f WORLD_PROJECTION = new Matrix4f();
  private static ClientLevel level;
  private static Vec3 cameraPosition;

  private HookLineAttachment() {}

  public static void reset() {
    ATTACHMENTS.clear();
    level = null;
    cameraPosition = null;
  }

  public static void beginWorld(Camera camera, Matrix4f view, Matrix4f projection) {
    var currentLevel = Minecraft.getInstance().level;
    if (currentLevel != level) {
      ATTACHMENTS.clear();
      level = currentLevel;
    }
    WORLD_VIEW.set(view);
    WORLD_PROJECTION.set(projection);
    cameraPosition = camera.getPosition();
  }

  public static void capture(
      ItemStack stack, ItemDisplayContext context, boolean leftHand, PoseStack pose) {
    var minecraft = Minecraft.getInstance();
    LocalPlayer player = minecraft.player;
    if (player == null
        || !minecraft.options.getCameraType().isFirstPerson()
        || !(context.firstPerson() || PunchyArmRenderer.isCapturingItemRenderer())
        || !HookStackState.isActive(stack, player)) return;
    var hook = ((PlayerWithHookData) player).yo_hooks$getHook();
    var hand = hook.getHoldingHand(player);
    HumanoidArm arm = leftHand ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
    if (hand == null || arm != arm(player, hand)) return;
    Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.last().pose());
    Vector3f clip = HookHandleProjection.project(modelView, RenderSystem.getProjectionMatrix());
    if (clip == null) ATTACHMENTS.remove(arm);
    else ATTACHMENTS.put(arm, new Attachment(player, hook.getUUID(), player.tickCount, clip));
  }

  public static Vec3 getWorldAnchor(HookEntity hook, Player owner) {
    var minecraft = Minecraft.getInstance();
    if (!(owner instanceof LocalPlayer player)
        || player != minecraft.player
        || !minecraft.options.getCameraType().isFirstPerson()
        || level != player.level()
        || cameraPosition == null
        || hook.isRemoved()
        || ((PlayerWithHookData) player).yo_hooks$getHook() != hook) return null;
    InteractionHand hand = hook.getHoldingHand(player);
    if (hand == null || !HookStackState.isActive(player.getItemInHand(hand), player)) return null;
    Attachment attachment = ATTACHMENTS.get(arm(player, hand));
    if (attachment == null
        || attachment.player() != player
        || !attachment.hookId().equals(hook.getUUID())
        || player.tickCount < attachment.tick()
        || player.tickCount - attachment.tick() > 2) return null;
    Vector3f point =
        FishingLineProjection.unprojectTip(WORLD_VIEW, WORLD_PROJECTION, attachment.clip());
    return point == null ? null : cameraPosition.add(point.x, point.y, point.z);
  }

  private static HumanoidArm arm(LocalPlayer player, InteractionHand hand) {
    return hand == InteractionHand.MAIN_HAND
        ? player.getMainArm()
        : player.getMainArm().getOpposite();
  }

  private record Attachment(LocalPlayer player, UUID hookId, int tick, Vector3f clip) {}
}

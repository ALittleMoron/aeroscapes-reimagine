package dev.aeroscapes.movement;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yori3o.yo_hooks.common.entity.HookEntity;
import com.yori3o.yo_hooks.common.util.HookStackState;
import com.yori3o.yo_hooks.common.util.interfaces.PlayerWithHookData;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import punchy.client.render.FishingLineProjection;
import punchy.client.render.PunchyArmRenderer;

/** Match rope endpoints to the final emitted item poses in both perspectives. */
public final class HookLineAttachment {
  private static final EnumMap<HumanoidArm, Attachment> ATTACHMENTS =
      new EnumMap<>(HumanoidArm.class);
  private static final Matrix4f WORLD_VIEW = new Matrix4f();
  private static final Matrix4f WORLD_PROJECTION = new Matrix4f();
  private static ClientLevel level;
  private static Vec3 cameraPosition;
  private static LivingEntity renderingOwner;
  private static boolean worldEntityPass;
  private static final Map<UUID, WorldAttachment> WORLD_ATTACHMENTS = new HashMap<>();

  private HookLineAttachment() {}

  public static void reset() {
    ATTACHMENTS.clear();
    level = null;
    cameraPosition = null;
    renderingOwner = null;
    WORLD_ATTACHMENTS.clear();
    worldEntityPass = false;
  }

  public static LivingEntity owner(LivingEntity owner) {
    LivingEntity previous = renderingOwner;
    renderingOwner = owner;
    return previous;
  }

  public static void worldEntities(boolean active) {
    worldEntityPass = active;
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
    WORLD_ATTACHMENTS.clear();
    worldEntityPass = false;
  }

  public static void capture(
      ItemStack stack, ItemDisplayContext context, boolean leftHand, PoseStack pose) {
    var minecraft = Minecraft.getInstance();
    HookItemView view =
        HookItemView.select(
            worldEntityPass,
            PunchyArmRenderer.isCapturingItemRenderer(),
            context.firstPerson(),
            context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
            minecraft.options.getCameraType().isFirstPerson());
    if (view == HookItemView.WORLD) {
      if (renderingOwner instanceof Player owner && HookStackState.isActive(stack, owner))
        captureWorld(owner, stack, leftHand, pose);
      return;
    }
    LocalPlayer player = minecraft.player;
    if (player == null
        || view != HookItemView.FIRST_PERSON
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

  private static void captureWorld(
      Player player, ItemStack stack, boolean leftHand, PoseStack pose) {
    if (!worldEntityPass || cameraPosition == null || player.level() != level) return;
    var hook = ((PlayerWithHookData) player).yo_hooks$getHook();
    var hand = hook.getHoldingHand(player);
    HumanoidArm arm = leftHand ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
    if (hand == null || arm != arm(player, hand) || !HookStackState.isActive(stack, player)) return;
    Matrix4f itemView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.last().pose());
    Vector3f point = HookHandleProjection.worldOffset(WORLD_VIEW, itemView);
    if (Float.isFinite(point.x) && Float.isFinite(point.y) && Float.isFinite(point.z))
      WORLD_ATTACHMENTS.put(
          hook.getUUID(),
          new WorldAttachment(player, cameraPosition.add(point.x, point.y, point.z)));
  }

  public static Vec3 getWorldAnchor(HookEntity hook, Player owner) {
    var minecraft = Minecraft.getInstance();
    if (owner != minecraft.player || !minecraft.options.getCameraType().isFirstPerson()) {
      WorldAttachment attachment = WORLD_ATTACHMENTS.get(hook.getUUID());
      return attachment != null
              && attachment.player() == owner
              && owner.level() == level
              && !hook.isRemoved()
              && ((PlayerWithHookData) owner).yo_hooks$getHook() == hook
          ? attachment.point()
          : null;
    }
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

  private static HumanoidArm arm(Player player, InteractionHand hand) {
    return hand == InteractionHand.MAIN_HAND
        ? player.getMainArm()
        : player.getMainArm().getOpposite();
  }

  private record Attachment(LocalPlayer player, UUID hookId, int tick, Vector3f clip) {}

  private record WorldAttachment(Player player, Vec3 point) {}
}

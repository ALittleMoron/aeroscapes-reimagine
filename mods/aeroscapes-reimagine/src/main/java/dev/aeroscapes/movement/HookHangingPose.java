package dev.aeroscapes.movement;

import com.yori3o.yo_hooks.common.compat.sable.SableCompat;
import com.yori3o.yo_hooks.common.entity.HookEntity;
import com.yori3o.yo_hooks.common.util.interfaces.PlayerWithHookData;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import tictim.paraglider.api.movement.Movement;

/** A temporary locomotion overlay; native action handlers and item state remain authoritative. */
public final class HookHangingPose {
  private static final Map<AbstractClientPlayer, State> STATES = new WeakHashMap<>();
  private static ClientLevel level;

  private HookHangingPose() {}

  public static void reset() {
    STATES.clear();
    level = null;
  }

  public static boolean hasPose(AbstractClientPlayer player, float partialTick) {
    State state = sample(player);
    return state != null && Mth.lerp(partialTick, state.previous, state.current) > 0;
  }

  public static void firstPerson(
      AbstractClientPlayer player, HumanoidArm arm, Matrix4f matrix, float partialTick) {
    State state = sample(player);
    float weight = weight(state, arm, partialTick);
    if (weight == 0) return;
    float side = arm == HumanoidArm.RIGHT ? 1 : -1;
    matrix.rotateX(-0.95f * weight).rotateZ(side * 0.12f * weight);
  }

  public static void thirdPerson(
      AbstractClientPlayer player, PlayerModel<?> model, float partialTick) {
    State state = sample(player);
    float weight = state == null ? 0 : Mth.lerp(partialTick, state.previous, state.current);
    if (weight == 0) return;
    ModelPart arm = state.arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
    float side = state.arm == HumanoidArm.RIGHT ? 1 : -1;
    float sway = Mth.sin((player.tickCount + partialTick) * 0.09f) * 0.025f;
    arm.xRot = Mth.lerp(weight, arm.xRot, -2.65f + sway);
    arm.yRot = Mth.lerp(weight, arm.yRot, state.reachYaw);
    arm.zRot = Mth.lerp(weight, arm.zRot, side * 0.13f);
    model.body.xRot = Mth.lerp(weight, model.body.xRot, 0.08f);
    model.leftLeg.xRot = Mth.lerp(weight, model.leftLeg.xRot, 0.13f - sway);
    model.rightLeg.xRot = Mth.lerp(weight, model.rightLeg.xRot, -0.08f + sway);
    model.leftSleeve.copyFrom(model.leftArm);
    model.rightSleeve.copyFrom(model.rightArm);
    model.leftPants.copyFrom(model.leftLeg);
    model.rightPants.copyFrom(model.rightLeg);
    model.jacket.copyFrom(model.body);
  }

  private static float weight(State state, HumanoidArm arm, float partialTick) {
    return state == null || state.arm != arm
        ? 0
        : Mth.lerp(partialTick, state.previous, state.current);
  }

  private static State sample(AbstractClientPlayer player) {
    ClientLevel currentLevel = Minecraft.getInstance().level;
    if (currentLevel != level) {
      STATES.clear();
      level = currentLevel;
    }
    if (currentLevel == null || player.level() != currentLevel) return null;
    HookEntity hook = ((PlayerWithHookData) player).yo_hooks$getHook();
    InteractionHand hand = hook == null ? null : hook.getHoldingHand(player);
    HumanoidArm arm =
        hand == InteractionHand.OFF_HAND ? player.getMainArm().getOpposite() : player.getMainArm();
    UUID hookId = hook == null ? null : hook.getUUID();
    State state = STATES.get(player);
    if (hook == null && (state == null || state.previous == 0 && state.current == 0)) {
      STATES.remove(player);
      return null;
    }
    boolean nativeAction = hook != null && hand != null && nativeAction(player, hand);
    if (nativeAction && state != null) {
      state.previous = 0;
      state.current = 0;
    }
    boolean changedHand =
        state != null && hand != null && (state.arm != arm || !hookId.equals(state.hookId));
    if (state != null && state.tick == player.tickCount && !changedHand) return state;
    if (state == null || changedHand || player.tickCount < state.tick) {
      state = new State();
      state.arm = arm;
      state.hookId = hookId;
      state.tick = player.tickCount - 1;
      STATES.put(player, state);
    }
    boolean active =
        hook != null
            && hand != null
            && hook.isControlledBy(player)
            && hook.isInBlock()
            && !player.onGround()
            && !player.isPassenger()
            && !player.isFallFlying()
            && !player.isInWaterOrBubble()
            && !player.isSwimming()
            && !player.isSleeping()
            && !player.isSpectator()
            && !nativeAction;
    if (active) {
      Vec3 anchor = SableCompat.getHookPos(hook.position(), hook);
      if (anchor == null) active = false;
      else {
        Vec3 offset = anchor.subtract(player.getEyePosition());
        active = HookHangMath.suspended(offset.length(), hook.getLength(), offset.y);
        state.reachYaw =
            Mth.clamp(
                Mth.wrapDegrees(
                        (float) Math.toDegrees(Math.atan2(-offset.x, offset.z)) - player.yBodyRot)
                    * Mth.DEG_TO_RAD,
                -0.45f,
                0.45f);
      }
    }
    state.previous = state.current;
    state.current = HookHangMath.approach(state.current, active, player.tickCount - state.tick);
    state.tick = player.tickCount;
    return state;
  }

  private static boolean nativeAction(AbstractClientPlayer player, InteractionHand hookHand) {
    if (Movement.get(player).state().paragliding()) return true;
    if (player.swinging && player.swingingArm == hookHand) return true;
    if (player.isUsingItem()
        && (player.getUsedItemHand() == hookHand
            || HookHangMath.twoHandedUse(player.getUseItem().getUseAnimation()))) return true;
    InteractionHand other =
        hookHand == InteractionHand.MAIN_HAND
            ? InteractionHand.OFF_HAND
            : InteractionHand.MAIN_HAND;
    var stack = player.getItemInHand(other);
    return stack.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(stack);
  }

  private static final class State {
    private UUID hookId;
    private HumanoidArm arm;
    private int tick;
    private float previous;
    private float current;
    private float reachYaw;
  }
}

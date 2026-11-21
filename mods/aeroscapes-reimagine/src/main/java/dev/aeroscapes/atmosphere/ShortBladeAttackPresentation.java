package dev.aeroscapes.atmosphere;

import dev.aeroscapes.atmosphere.ShortBladeAttack.Move;
import dev.aeroscapes.movement.HookHangingPose;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.api.layered.modifier.MirrorModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class ShortBladeAttackPresentation {
  private static final int CROSSFADE_TICKS = 2;
  private static final int RECOVERY_BLEND_TICKS = 3;
  private static final int MAX_PENDING_CUES = 2;
  private static boolean registered;

  private ShortBladeAttackPresentation() {}

  @SubscribeEvent
  public static void setup(FMLClientSetupEvent event) {
    if (!ShortBladeAttack.available()) return;
    event.enqueueWork(
        () -> {
          PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
              Layer.ID, 20, player -> new Layer());
          registered = true;
        });
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void attack(AttackEntityEvent event) {
    if (event.getEntity() instanceof LocalPlayer player) localSwing(player);
  }

  @SubscribeEvent
  public static void emptySwing(PlayerInteractEvent.LeftClickEmpty event) {
    if (event.getEntity() instanceof LocalPlayer player) localSwing(player);
  }

  @SubscribeEvent
  public static void afterPlayerTick(PlayerTickEvent.Post event) {
    if (!(event.getEntity() instanceof AbstractClientPlayer player)) return;
    Layer layer = layer(player);
    if (layer == null || (layer.lastMove == null && layer.pending.isEmpty())) return;
    if (!ShortBladeAttack.canAnimate(player) || actionInUse(player, 0)) layer.reset();
    else if (layer.canStart() && !layer.pending.isEmpty())
      layer.play(player, layer.pending.removeFirst());
  }

  private static void localSwing(LocalPlayer player) {
    Layer layer = layer(player);
    if (layer == null
        || !layer.canStart()
        || !ShortBladeAttack.canAnimate(player)
        || actionInUse(player, 0)) return;
    Move move =
        layer.lastMove != null
                && player.tickCount - layer.startTick < ShortBladeAttack.COMBO_RESET_TICKS
            ? layer.lastMove.next()
            : Move.JAB;
    if (layer.play(player, move)
        && player.connection.hasChannel(ShortBladeAttack.SwingRequest.TYPE))
      PacketDistributor.sendToServer(new ShortBladeAttack.SwingRequest(move.ordinal()));
  }

  static void remoteSwing(int entityId, int moveId) {
    var level = Minecraft.getInstance().level;
    Move move = Move.fromId(moveId);
    if (level != null
        && move != null
        && level.getEntity(entityId) instanceof AbstractClientPlayer player
        && !(player instanceof LocalPlayer)
        && ShortBladeAttack.canAnimate(player)
        && !actionInUse(player, 0)) {
      Layer layer = layer(player);
      if (layer == null) return;
      if (layer.canStart() && layer.pending.isEmpty()) layer.play(player, move);
      else if (layer.pending.size() < MAX_PENDING_CUES) layer.pending.addLast(move);
    }
  }

  public static float renderWeight(AbstractClientPlayer player, float partialTick) {
    Layer layer = layer(player);
    if (layer == null || !layer.isActive()) return 0;
    if (!ShortBladeAttack.canAnimate(player) || actionInUse(player, partialTick)) {
      layer.reset();
      return 0;
    }
    return layer.weight(partialTick);
  }

  static @Nullable Pose samplePose(AbstractClientPlayer player, float partialTick) {
    Layer layer = layer(player);
    if (layer == null || !layer.isActive()) return null;
    layer.setupAnim(partialTick);
    return new Pose(
        layer.pose("torso", partialTick, Vec3f.ZERO),
        layer.pose("rightArm", partialTick, new Vec3f(-5, 2, 0)),
        layer.pose("leftArm", partialTick, new Vec3f(5, 2, 0)));
  }

  private static boolean actionInUse(AbstractClientPlayer player, float partialTick) {
    if (ModList.get().isLoaded("yo_hooks") && HookHangingPose.hasPose(player, partialTick))
      return true;
    if (ModList.get().isLoaded("irons_spellbooks")) {
      var spell =
          PlayerAnimationAccess.getPlayerAssociatedData(player)
              .get(SpellAnimations.ANIMATION_RESOURCE);
      return spell != null && spell.isActive();
    }
    return false;
  }

  private static @Nullable Layer layer(AbstractClientPlayer player) {
    return registered
            && PlayerAnimationAccess.getPlayerAssociatedData(player).get(Layer.ID)
                instanceof Layer animation
        ? animation
        : null;
  }

  static Vec3f gripOffset(float pitch) {
    // The Refined knife and dagger hilts sit here relative to the equipped item origin.
    float gripY = -2;
    float gripZ = 1.5F;
    float cosine = Mth.cos(pitch);
    float sine = Mth.sin(pitch);
    return new Vec3f(0, gripY * (1 - cosine) + gripZ * sine, gripZ * (1 - cosine) - gripY * sine);
  }

  record Pose(PartPose torso, PartPose rightArm, PartPose leftArm) {}

  private static final class Layer extends ModifierLayer<KeyframeAnimationPlayer> {
    private static final ResourceLocation ID =
        ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", "short_blade_combo");
    private final MirrorModifier mirror = new MirrorModifier(false);
    private final Deque<Move> pending = new ArrayDeque<>();
    private Move lastMove;
    private int startTick;
    private float entryWeight;

    private Layer() {
      addModifierLast(mirror);
    }

    private boolean canStart() {
      var current = getAnimation();
      return current == null
          || !current.isActive()
          || current.getTick() >= lastMove.followThroughTick;
    }

    private boolean play(AbstractClientPlayer player, Move move) {
      if (!(PlayerAnimationRegistry.getAnimation(move.clip) instanceof KeyframeAnimation clip))
        return false;
      entryWeight = weight(0);
      mirror.setEnabled(player.getMainArm() == HumanoidArm.LEFT);
      replaceAnimationWithFade(
          AbstractFadeModifier.standardFadeIn(CROSSFADE_TICKS, Ease.INOUTSINE),
          new KeyframeAnimationPlayer(clip).setFirstPersonMode(FirstPersonMode.NONE),
          true);
      lastMove = move;
      startTick = player.tickCount;
      return true;
    }

    @Override
    public Vec3f get3DTransform(String part, TransformType type, float delta, Vec3f value) {
      Vec3f transformed = super.get3DTransform(part, type, delta, value);
      String item = mirror.isEnabled() ? "leftItem" : "rightItem";
      if (type == TransformType.POSITION && part.equals(item)) {
        // The held item uses the sampled rotation at every partial tick, so its hilt stays put.
        float pitch = super.get3DTransform(part, TransformType.ROTATION, delta, Vec3f.ZERO).getX();
        return transformed.add(gripOffset(pitch));
      }
      return transformed;
    }

    private PartPose pose(String part, float delta, Vec3f pivot) {
      Vec3f position = get3DTransform(part, TransformType.POSITION, delta, pivot);
      Vec3f rotation = get3DTransform(part, TransformType.ROTATION, delta, Vec3f.ZERO);
      return PartPose.offsetAndRotation(
          position.getX(),
          position.getY(),
          position.getZ(),
          rotation.getX(),
          rotation.getY(),
          rotation.getZ());
    }

    private float weight(float partialTick) {
      var current = getAnimation();
      if (current == null || !current.isActive()) return 0;
      float time = current.getTick() + partialTick;
      float enter = Mth.clamp(time / CROSSFADE_TICKS, 0, 1);
      float recover = Mth.clamp((current.getData().endTick - time) / RECOVERY_BLEND_TICKS, 0, 1);
      return Mth.lerp(enter, entryWeight, 1) * recover;
    }

    private void reset() {
      setAnimation(null);
      pending.clear();
      lastMove = null;
    }
  }
}

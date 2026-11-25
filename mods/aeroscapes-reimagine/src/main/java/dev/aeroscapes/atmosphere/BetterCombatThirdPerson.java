package dev.aeroscapes.atmosphere;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractModifier;
import dev.kosmx.playerAnim.core.util.Vec3f;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

public final class BetterCombatThirdPerson {
  private static final ResourceLocation ATTACK =
      ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", "better_combat_attack");

  private BetterCombatThirdPerson() {}

  public static void track(AbstractClientPlayer player, ModifierLayer<?> layer, String clip) {
    var data = PlayerAnimationAccess.getPlayerAssociatedData(player);
    if (data.get(ATTACK) instanceof Grip grip) {
      grip.shortBlade = isShortBlade(clip);
    } else {
      var grip = new Grip();
      grip.shortBlade = isShortBlade(clip);
      layer.addModifierBefore(grip);
      data.set(ATTACK, grip);
    }
  }

  public static boolean active(AbstractClientPlayer player, float partialTick) {
    if (!ModList.get().isLoaded("bettercombat") || FirstPersonMode.isFirstPersonPass())
      return false;
    var layer = PlayerAnimationAccess.getPlayerAssociatedData(player).get(ATTACK);
    return layer != null
        && layer.isActive()
        && player.isAlive()
        && !player.isSpectator()
        && !player.isSleeping()
        && !player.isUsingItem()
        && !ShortBladeAttackPresentation.actionInUse(player, partialTick);
  }

  public static boolean keepsViewAdjustment(String part, boolean firstPerson) {
    return firstPerson
        || switch (part) {
          case "body", "leftLeg", "rightLeg" -> false;
          default -> true;
        };
  }

  static boolean isShortBlade(String clip) {
    return switch (clip) {
      case "aeroscapes_reimagine:short_blade_jab",
          "aeroscapes_reimagine:short_blade_slash",
          "aeroscapes_reimagine:short_blade_overhead" ->
          true;
      default -> false;
    };
  }

  private static final class Grip extends AbstractModifier {
    private boolean shortBlade;

    @Override
    public Vec3f get3DTransform(String part, TransformType type, float delta, Vec3f value) {
      var transformed = super.get3DTransform(part, type, delta, value);
      if (!shortBlade
          || type != TransformType.POSITION
          || !(part.equals("leftItem") || part.equals("rightItem"))) return transformed;
      float pitch = super.get3DTransform(part, TransformType.ROTATION, delta, Vec3f.ZERO).getX();
      return transformed.add(ShortBladeAttackPresentation.gripOffset(pitch));
    }
  }
}

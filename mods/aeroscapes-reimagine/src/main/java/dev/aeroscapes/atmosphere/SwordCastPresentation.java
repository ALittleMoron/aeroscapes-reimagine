package dev.aeroscapes.atmosphere;

import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.render.animation.AnimationHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Local follow-through for confirmed casts of the three built-in sword spells. */
public final class SwordCastPresentation {
  private static final String ECHO = "irons_spellbooks:echoing_strikes";
  private static final ResourceLocation SELF =
      ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", "sword_spell_followthrough");
  private static final ResourceLocation COUNTER =
      ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", "counterspell_followthrough");

  private SwordCastPresentation() {}

  static ResourceLocation clip(String spell, CastSource source) {
    if (source != CastSource.SWORD) return null;
    return switch (spell) {
      case ECHO, "irons_spellbooks:frostbite" -> SELF;
      case "irons_spellbooks:counterspell" -> COUNTER;
      default -> null;
    };
  }

  public static void confirmed(String spell, CastSource source) {
    var animation = clip(spell, source);
    if (animation == null) return;
    var minecraft = Minecraft.getInstance();
    var player = minecraft.player;
    if (player == null) return;
    AnimationHelper.animatePlayerStart(player, animation);
    if (spell.equals(ECHO) && minecraft.options.getCameraType().isFirstPerson()) echo(player);
  }

  private static void echo(LocalPlayer player) {
    Vec3 forward = player.getViewVector(1);
    Vec3 side = Vec3.directionFromRotation(0, player.getYRot() + 90);
    Vec3 up = forward.cross(side).normalize();
    Vec3 center = player.getEyePosition().add(forward.scale(.85));
    for (int index = 0; index < 6; index++) {
      double sign = index % 2 == 0 ? 1 : -1;
      Vec3 point =
          center
              .add(side.scale(sign * (.25 + .07 * (index / 2))))
              .add(up.scale(.15 - .08 * (index / 2)));
      Vec3 drift = side.scale(sign * .055).add(forward.scale(.025));
      player
          .level()
          .addParticle(
              ParticleTypes.REVERSE_PORTAL, point.x, point.y, point.z, drift.x, drift.y, drift.z);
    }
  }
}

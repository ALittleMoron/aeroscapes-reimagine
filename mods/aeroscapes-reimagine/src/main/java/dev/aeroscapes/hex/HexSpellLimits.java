package dev.aeroscapes.hex;

import at.petrak.hexcasting.api.casting.RenderedSpell;
import at.petrak.hexcasting.api.casting.castables.SpellAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.iota.DoubleIota;
import at.petrak.hexcasting.api.casting.iota.EntityIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.Vec3Iota;
import at.petrak.hexcasting.api.casting.mishaps.MishapDisallowedSpell;
import at.petrak.hexcasting.common.casting.actions.spells.OpAddMotion;
import at.petrak.hexcasting.common.casting.actions.spells.OpBlink;
import at.petrak.hexcasting.common.casting.actions.spells.OpExplode;
import at.petrak.hexcasting.common.casting.actions.spells.OpPotionEffect;
import at.petrak.hexcasting.common.casting.actions.spells.great.OpAltiora;
import at.petrak.hexcasting.common.casting.actions.spells.great.OpTeleport;
import java.util.List;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;

public final class HexSpellLimits {
  private static final String READY_AT = "aeroscapes_reimagine_hex_travel_ready";
  private static final int TRAVEL_INTERVAL = 400;

  private HexSpellLimits() {}

  /** Hex has validated the arguments; its media debit and rendered effect are still pending. */
  public static SpellAction.Result check(
      SpellAction action, List<Iota> args, CastingEnvironment env, SpellAction.Result result)
      throws MishapDisallowedSpell {
    boolean lift =
        action instanceof OpPotionEffect potion && potion.getEffect().equals(MobEffects.LEVITATION);
    if (action instanceof OpPotionEffect potion && potion.getAllowPotency())
      limit(((DoubleIota) args.get(2)).getDouble(), 2, "effect");
    if (action instanceof OpExplode) limit(((DoubleIota) args.get(1)).getDouble(), 4, "blast");
    if (lift) limit(((DoubleIota) args.get(1)).getDouble(), 4, "lift");
    else if (action instanceof OpBlink)
      limit(Math.abs(((DoubleIota) args.get(1)).getDouble()), 16, "blink");
    else if (action instanceof OpTeleport)
      limit(((Vec3Iota) args.get(1)).getVec3().length(), 64, "teleport");
    else if (action instanceof OpAddMotion)
      limit(((Vec3Iota) args.get(1)).getVec3().length(), 1.5, "impulse");
    else if (!(action instanceof OpAltiora)) return result;

    Entity target = ((EntityIota) args.getFirst()).getEntity();
    long tick = env.getWorld().getServer().overworld().getGameTime();
    if (target.getPersistentData().getLong(READY_AT) > tick)
      throw new MishapDisallowedSpell("aeroscapes_travel_interval");
    RenderedSpell effect = new TimedMovement(result.getEffect(), target);
    return result.copy(effect, result.getCost(), result.getParticles(), result.getOpCount());
  }

  private static void limit(double value, double maximum, String reason)
      throws MishapDisallowedSpell {
    if (!Double.isFinite(value) || value > maximum)
      throw new MishapDisallowedSpell("aeroscapes_" + reason);
  }

  private record TimedMovement(RenderedSpell original, Entity target) implements RenderedSpell {
    @Override
    public void cast(CastingEnvironment env) {
      original.cast(env);
      remember(env);
    }

    @Override
    public CastingImage cast(CastingEnvironment env, CastingImage image) {
      var updated = original.cast(env, image);
      remember(env);
      return updated;
    }

    private void remember(CastingEnvironment env) {
      target
          .getPersistentData()
          .putLong(
              READY_AT, env.getWorld().getServer().overworld().getGameTime() + TRAVEL_INTERVAL);
    }
  }
}

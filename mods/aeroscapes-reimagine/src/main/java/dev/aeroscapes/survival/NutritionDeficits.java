package dev.aeroscapes.survival;

import com.dannyandson.nutritionalbalance.Config;
import com.dannyandson.nutritionalbalance.api.IPlayerNutrient;
import com.dannyandson.nutritionalbalance.setup.Registration;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Owns the presentation and additional tiers of Nutritional Balance's deficit effects. */
public final class NutritionDeficits {
  public static final int MOVEMENT_TIER = 3;
  public static final int MINING_TIER = 5;
  public static final int MOVEMENT_PERCENT = 20;
  public static final int MINING_PERCENT = 30;
  private static final String NAMESPACE = "aeroscapes_reimagine";
  private static final Set<String> REQUIRED =
      Set.of("carbs", "proteins", "fruits", "vegetables", "sugars");
  private static final ResourceLocation NATIVE_ATTACK_MODIFIER =
      ResourceLocation.fromNamespaceAndPath("nutritionalbalance", "malnourished_attack_damage");
  private static final DeferredRegister<MobEffect> EFFECTS =
      DeferredRegister.create(Registries.MOB_EFFECT, NAMESPACE);
  public static final DeferredHolder<MobEffect, MobEffect> MOVEMENT =
      registerEffect("nutrition_movement", Attributes.MOVEMENT_SPEED, MOVEMENT_PERCENT);
  public static final DeferredHolder<MobEffect, MobEffect> MINING =
      registerEffect("nutrition_mining", Attributes.BLOCK_BREAK_SPEED, MINING_PERCENT);

  private NutritionDeficits() {}

  public static void register(IEventBus bus) {
    EFFECTS.register(bus);
  }

  public static Holder<MobEffect> attackEffect() {
    return BuiltInRegistries.MOB_EFFECT.wrapAsHolder(Registration.MALNOURISHED_EFFECT.get());
  }

  public static MobEffectInstance permanentAttack(MobEffectInstance effect) {
    if (!effect.getEffect().equals(attackEffect())
        || (effect.isInfiniteDuration() && !effect.isAmbient())) return effect;
    var permanent =
        new MobEffectInstance(
            effect.getEffect(),
            MobEffectInstance.INFINITE_DURATION,
            effect.getAmplifier(),
            false,
            effect.isVisible(),
            effect.showIcon());
    permanent.getCures().clear();
    permanent.getCures().addAll(effect.getCures());
    return permanent;
  }

  public static int count(List<IPlayerNutrient> nutrients) {
    int deficient = 0;
    for (var nutrient : nutrients) {
      if (REQUIRED.contains(nutrient.getNutrientName())
          && nutrient.getStatus() == IPlayerNutrient.NutrientStatus.MALNOURISHED) deficient++;
    }
    return deficient;
  }

  public static boolean update(ServerPlayer player, List<IPlayerNutrient> nutrients) {
    return update(player, count(nutrients));
  }

  public static void afterCommand(ServerPlayer player, List<IPlayerNutrient> nutrients) {
    int deficient = count(nutrients);
    if (deficient > 0) {
      player.removeEffect(
          BuiltInRegistries.MOB_EFFECT.wrapAsHolder(Registration.NOURISHED_EFFECT.get()));
      player.removeEffect(
          BuiltInRegistries.MOB_EFFECT.wrapAsHolder(Registration.ENGORGED_EFFECT.get()));
    }
    apply(player, attackEffect(), deficient > 0);
    update(player, deficient);
  }

  private static boolean update(ServerPlayer player, int deficient) {
    var attack = player.getEffect(attackEffect());
    if (deficient > 0 && attack != null && (!attack.isInfiniteDuration() || attack.isAmbient())) {
      // A saved player can still carry the provider's previous finite effect instance.
      player.addEffect(permanentAttack(attack));
    }
    refreshNativeAttackModifier(player);
    boolean movementChanged = apply(player, MOVEMENT, deficient >= MOVEMENT_TIER);
    boolean miningChanged = apply(player, MINING, deficient >= MINING_TIER);
    return movementChanged || miningChanged;
  }

  private static DeferredHolder<MobEffect, MobEffect> registerEffect(
      String name, Holder<Attribute> attribute, int percent) {
    return EFFECTS.register(
        name,
        () ->
            new MobEffect(MobEffectCategory.HARMFUL, 0xad6845) {}.addAttributeModifier(
                attribute,
                ResourceLocation.fromNamespaceAndPath(NAMESPACE, name),
                -percent / 100.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
  }

  private static boolean apply(ServerPlayer player, Holder<MobEffect> effect, boolean enabled) {
    if (player.hasEffect(effect) == enabled) return false;
    if (!enabled) return player.removeEffect(effect);
    return player.addEffect(
        new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION, 0, false, false, true));
  }

  private static void refreshNativeAttackModifier(ServerPlayer player) {
    var active = player.getEffect(attackEffect());
    var damage = player.getAttribute(Attributes.ATTACK_DAMAGE);
    if (active == null || damage == null) return;
    // The provider retains active effects on reload, including the saved attack modifier.
    if (Config.MALNOURISHED_ATTACK_DAMAGE.get() == 0) {
      damage.removeModifier(NATIVE_ATTACK_MODIFIER);
      return;
    }
    active
        .getEffect()
        .value()
        .createModifiers(
            active.getAmplifier(),
            (attribute, modifier) -> {
              if (attribute.equals(Attributes.ATTACK_DAMAGE)
                  && !modifier.equals(damage.getModifier(modifier.id())))
                damage.addOrReplacePermanentModifier(modifier);
            });
  }
}

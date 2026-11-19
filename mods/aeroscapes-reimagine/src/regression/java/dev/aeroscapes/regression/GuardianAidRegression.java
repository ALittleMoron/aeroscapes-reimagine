package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import dev.aeroscapes.magic.MagicPractice;
import dev.aeroscapes.magic.PreparedAbilities;
import dev.aeroscapes.progression.SpellPractice;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.registries.MobEffectRegistry;
import java.util.UUID;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class GuardianAidRegression {
  private GuardianAidRegression() {}

  public static void verify(ServerLevel level) {
    Vec3 origin = Vec3.atBottomCenterOf(level.getSharedSpawnPos()).add(20, 48, 0);
    try (var session =
        TestShipPlayerSession.create(level, UUID.randomUUID(), "GuardianFixture", origin, null)) {
      var player = session.player;
      var ally = EntityType.VILLAGER.create(level);
      var wolf = EntityType.WOLF.create(level);
      var zombie = EntityType.ZOMBIE.create(level);
      require(ally != null && wolf != null && zombie != null, "Guardian actor types exist");
      ProgressionRegression.listPlayer(level, player, true);
      long previousTime = level.getGameTime();
      try {
        ally.setPos(origin.add(0, 0, 4));
        wolf.setPos(origin.add(4, 0, 0));
        wolf.tame(player);
        zombie.setPos(origin.add(3, 0, 4));
        require(
            level.addFreshEntity(ally)
                && level.addFreshEntity(wolf)
                && level.addFreshEntity(zombie),
            "Survival allies and a hostile attacker join the level");
        AbilitiesRegression.initialize(player);
        var fortify = SpellRegistry.FORTIFY_SPELL.get();
        var blessing = SpellRegistry.BLESSING_OF_LIFE_SPELL.get();
        AbilitiesRegression.unlockMastery(player, fortify, 4);
        AbilitiesRegression.unlock(player, "magic_restoration_guardian_aid");
        PreparedAbilities.edit(player, 0, fortify.getSpellId());
        PreparedAbilities.edit(player, 1, blessing.getSpellId());
        int cost = blessing.getManaCost(1);
        int reduced = cost - cost / 4;
        ward(player, ally, fortify);
        heal(player, ally, cost);
        ward(player, ally, fortify);
        ally.invulnerableTime = 0;
        ally.hurt(ally.damageSources().playerAttack(player), 1);
        heal(player, ally, cost);
        wound(ally);
        hit(ally, zombie);
        ally.setHealth(ally.getMaxHealth());
        heal(player, ally, cost);
        wound(wolf);
        heal(player, wolf, cost);
        wound(ally);
        float health = ally.getHealth();
        heal(player, ally, reduced);
        require(
            ally.getHealth() > health,
            "Blessing actually restores the protected villager's health");
        wound(ally);
        hit(ally, zombie);
        heal(player, ally, cost);
        ward(player, wolf, fortify);
        hit(wolf, zombie);
        var magic = MagicData.getPlayerMagicData(player);
        magic.getPlayerCooldowns().clearCooldowns();
        var scroll = AbilitiesRegression.scroll(blessing, 1, 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, scroll);
        aim(player, wolf);
        float mana = magic.getMana();
        require(
            blessing.attemptInitiateCast(
                scroll, 1, level, player, CastSource.SCROLL, true, "mainhand"),
            "A wounded tamed wolf can receive native Blessing from a scroll");
        AbilitiesRegression.complete(player);
        require(magic.getMana() == mana, "A free scroll cannot gain mana from Guardian Aid");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        wound(wolf);
        heal(player, wolf, reduced);
        ward(player, ally, fortify);
        hit(ally, zombie);
        level.getServer().getWorldData().overworldData().setGameTime(previousTime + 200);
        heal(player, ally, cost);
        LogUtils.getLogger()
            .info("Guardian Aid native ward and targeted-healing regression passed");
      } finally {
        level.getServer().getWorldData().overworldData().setGameTime(previousTime);
        zombie.discard();
        wolf.discard();
        ally.discard();
        ProgressionRegression.listPlayer(level, player, false);
      }
    }
  }

  private static void wound(LivingEntity ally) {
    ally.setHealth(ally.getMaxHealth() / 2);
  }

  private static void ward(ServerPlayer player, LivingEntity ally, AbstractSpell fortify) {
    wound(ally);
    ally.removeEffect(MobEffectRegistry.FORTIFY);
    ally.setAbsorptionAmount(0);
    cast(player, 0);
    require(
        ally.getAbsorptionAmount() > 0 && ally.hasEffect(MobEffectRegistry.FORTIFY),
        "Paid " + fortify.getSpellId() + " gives this wounded ally hearts");
  }

  private static void hit(LivingEntity ally, Zombie zombie) {
    float absorption = ally.getAbsorptionAmount();
    ally.invulnerableTime = 0;
    ally.hurt(ally.damageSources().mobAttack(zombie), 1);
    require(
        ally.getAbsorptionAmount() < absorption, "A hostile hit actually consumes Fortify hearts");
  }

  private static void aim(ServerPlayer player, LivingEntity ally) {
    player.lookAt(EntityAnchorArgument.Anchor.EYES, ally.getEyePosition());
  }

  private static void heal(ServerPlayer player, LivingEntity ally, int expected) {
    var magic = MagicData.getPlayerMagicData(player);
    float before = magic.getMana();
    long credit = MagicPractice.credit(player);
    aim(player, ally);
    cast(player, 1);
    require(
        before - magic.getMana() == expected,
        "Expected net Blessing cost "
            + expected
            + ", actually spent "
            + (before - magic.getMana()));
    require(
        MagicPractice.credit(player) - credit == SpellPractice.experience(expected),
        "Blessing awards practice exactly once on the actual net mana payment");
  }

  private static void cast(ServerPlayer player, int slot) {
    MagicData.getPlayerMagicData(player).getPlayerCooldowns().clearCooldowns();
    PreparedAbilities.select(player, slot);
    PreparedAbilities.cast(player);
    AbilitiesRegression.complete(player);
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}

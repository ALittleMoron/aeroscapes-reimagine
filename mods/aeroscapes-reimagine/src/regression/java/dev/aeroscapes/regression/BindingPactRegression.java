package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import dev.aeroscapes.magic.MagicPractice;
import dev.aeroscapes.magic.MagicPracticeProgress;
import dev.aeroscapes.magic.PreparedAbilities;
import dev.aeroscapes.magic.SpellKnowledge;
import dev.aeroscapes.progression.ProgressionBridge;
import io.redspace.ironsspellbooks.api.events.ChangeManaEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.capabilities.magic.RecastInstance;
import io.redspace.ironsspellbooks.capabilities.magic.RecastResult;
import io.redspace.ironsspellbooks.capabilities.magic.SummonManager;
import io.redspace.ironsspellbooks.entity.mobs.SummonedZombie;
import io.redspace.ironsspellbooks.entity.spells.root.RootEntity;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.puffish.skillsmod.SkillsMod;

public final class BindingPactRegression {
  private static final ResourceLocation MAGIC =
      ResourceLocation.parse(ProgressionBridge.MAGIC_CATEGORY);

  private BindingPactRegression() {}

  public static void verify(ServerLevel level) {
    Vec3 position = Vec3.atBottomCenterOf(level.getSharedSpawnPos()).add(0, 50, 12);
    try (var session =
            TestShipPlayerSession.create(level, UUID.randomUUID(), "BindingPact", position, null);
        var other =
            TestShipPlayerSession.create(
                level, UUID.randomUUID(), "OtherSummoner", position.add(8, 0, 0), null)) {
      ServerPlayer player = session.player;
      ProgressionRegression.listPlayer(level, player, true);
      try {
        exercise(level, player, other.player);
      } finally {
        ProgressionRegression.listPlayer(level, player, false);
      }
    }
    LogUtils.getLogger().info("Binding Pact native root and summon regression passed");
  }

  private static void exercise(ServerLevel level, ServerPlayer player, ServerPlayer otherSummoner) {
    var magic = MagicData.getPlayerMagicData(player);
    magic.setServerPlayer(player);
    magic.getSyncedData();
    Utils.setPlayerSpellbookStack(player, new ItemStack(ItemRegistry.WIMPY_SPELL_BOOK.get()));
    NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));
    MagicPractice.award(player, MagicPracticeProgress.minimumXp(45));
    for (String node :
        List.of(
            "magic_mastery_1",
            "magic_conjuration_mastery_2",
            "magic_conjuration_mastery_3",
            "magic_conjuration_mastery_4")) {
      SkillsMod.getInstance().unlockSkill(player, MAGIC, node);
    }
    AbstractSpell root = SpellKnowledge.spell("irons_spellbooks:root");
    PreparedAbilities.edit(player, 0, root.getSpellId());
    PreparedAbilities.select(player, 0);
    player.getAttribute(AttributeRegistry.MAX_MANA).setBaseValue(1000);
    player.getAttribute(AttributeRegistry.MANA_REGEN).setBaseValue(0);
    magic.setMana(500);

    LivingEntity target = EntityType.COW.create(level);
    require(target != null, "Registered target type");
    target.moveTo(player.position().add(0, 0.6, 4));
    target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
    target.setHealth(200);
    require(level.addFreshEntity(target), "Registered restraint target");
    var summon = new SummonedZombie(level, false);
    SummonManager.setOwner(summon, player);
    summon.moveTo(target.position().add(2, 0, 0));
    try {
      require(level.addFreshEntity(summon), "Registered Iron's melee summon");
      cast(player, target, root, CastSource.SPELLBOOK);
      float baseline = magic.getMana();
      hit(summon, target);
      close(magic.getMana(), baseline, "No talent earns no refund");
      release(target);

      SkillsMod.getInstance().unlockSkill(player, MAGIC, "magic_conjuration_binding_pact");
      float before = magic.getMana();
      Consumer<ChangeManaEvent> partialPayment =
          event -> {
            if (event.getEntity() == player && event.getNewMana() < event.getOldMana())
              event.setNewMana(event.getOldMana() - 7.5f);
          };
      NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, partialPayment);
      try {
        cast(player, target, root, CastSource.SPELLBOOK);
      } finally {
        NeoForge.EVENT_BUS.unregister(partialPayment);
      }
      float paid = before - magic.getMana();
      close(paid, 7.5f, "Payment reflects the provider's final mana-change event");
      baseline = magic.getMana();
      SummonManager.setOwner(summon, otherSummoner);
      hit(summon, target);
      close(magic.getMana(), baseline, "Foreign summon leaves the paid Root unspent");
      SummonManager.setOwner(summon, player);
      target.invulnerableTime = 0;
      require(target.hurt(player.damageSources().playerAttack(player), 1), "Caster connected");
      close(magic.getMana(), baseline, "Caster damage leaves the paid Root unspent");
      hit(summon, target);
      close(
          magic.getMana(),
          baseline + paid * 0.20f,
          "Own melee summon returns one fifth of the actual debit");
      hit(summon, target);
      close(magic.getMana(), baseline + paid * 0.20f, "Further hits cannot reuse payment");
      release(target);

      baseline = magic.getMana();
      cast(player, target, root, CastSource.SCROLL);
      hit(summon, target);
      close(magic.getMana(), baseline, "A consumed one-use Root has no mana to return");
      release(target);

      magic
          .getPlayerRecasts()
          .forceAddRecast(
              new RecastInstance(root.getSpellId(), 1, 2, 200, CastSource.SPELLBOOK, null));
      cast(player, target, root, CastSource.SPELLBOOK);
      hit(summon, target);
      close(magic.getMana(), baseline, "A free recast cannot inherit an earlier payment");
      magic.getPlayerRecasts().removeAll(RecastResult.USER_CANCEL);
      release(target);

      cast(player, target, root, CastSource.SPELLBOOK);
      baseline = magic.getMana();
      release(target);
      hit(summon, target);
      close(magic.getMana(), baseline, "Hits after the restraint ends cannot trigger it");
    } finally {
      release(target);
      SummonManager.removeSummon(summon);
      summon.discard();
      target.discard();
    }
  }

  private static void cast(
      ServerPlayer player, LivingEntity target, AbstractSpell root, CastSource source) {
    MagicData magic = MagicData.getPlayerMagicData(player);
    magic.getPlayerCooldowns().clearCooldowns();
    target.moveTo(player.position().add(0, 0.6, 4));
    if (source == CastSource.SCROLL) {
      var scroll = new ItemStack(ItemRegistry.SCROLL.get(), 2);
      ISpellContainer.createScrollContainer(root, 1, scroll);
      player.setItemInHand(InteractionHand.MAIN_HAND, scroll);
      scroll.use(player.level(), player, InteractionHand.MAIN_HAND);
    } else {
      PreparedAbilities.cast(player);
    }
    var manager = new MagicManager();
    for (int tick = 0; tick < 400 && magic.isCasting(); tick++) manager.tick(player.level());
    require(
        target.getVehicle() instanceof RootEntity rootEntity && rootEntity.getOwner() == player,
        "Native spell successfully rooted the intended target from " + source);
    if (source == CastSource.SCROLL)
      require(player.getMainHandItem().getCount() == 1, "Exactly one scroll consumed");
  }

  private static void release(LivingEntity target) {
    if (target.getVehicle() instanceof RootEntity root) root.removeRoot();
  }

  private static void hit(SummonedZombie summon, LivingEntity target) {
    target.invulnerableTime = 0;
    float health = target.getHealth();
    require(
        summon.doHurtTarget(target) && target.getHealth() < health,
        "Native summon dealt actual damage");
  }

  private static void close(float actual, float expected, String message) {
    require(Math.abs(actual - expected) < 0.0001f, message + ": " + actual + " != " + expected);
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}

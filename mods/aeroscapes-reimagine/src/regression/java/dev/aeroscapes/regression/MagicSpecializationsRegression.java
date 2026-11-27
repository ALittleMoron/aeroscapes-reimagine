package dev.aeroscapes.regression;

import dev.aeroscapes.magic.PreparedAbilities;
import dev.aeroscapes.magic.SpellKnowledge;
import dev.aeroscapes.progression.ProgressionBridge;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.SummonManager;
import io.redspace.ironsspellbooks.capabilities.magic.SummonedEntitiesCastData;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.entity.mobs.SummonedSkeleton;
import io.redspace.ironsspellbooks.entity.spells.blood_needle.BloodNeedle;
import io.redspace.ironsspellbooks.entity.spells.ender_chain.ArcaneShackleProjectile;
import io.redspace.ironsspellbooks.entity.spells.ender_chain.EnderChain;
import io.redspace.ironsspellbooks.registries.EntityRegistry;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import io.redspace.ironsspellbooks.registries.MobEffectRegistry;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.puffish.skillsmod.SkillsMod;

final class MagicSpecializationsRegression {
  private static final ResourceLocation MAGIC =
      ResourceLocation.parse(ProgressionBridge.MAGIC_CATEGORY);
  private static final String OAKSKIN_ALLY = "magic_oakskin_ally";
  private static final String FROSTWAVE_EXTINGUISH = "magic_frostwave_extinguish";
  private static final String BLOOD_NEEDLES_OVERHEAL = "magic_blood_needles_overheal";
  private static final String ARCANE_SHACKLE_MARKER = "magic_arcane_shackle_marker";

  private MagicSpecializationsRegression() {}

  static void verify(ServerLevel level) {
    var spawn = level.getSharedSpawnPos();
    var floor =
        new BlockPos(
            spawn.getX() + 100,
            Math.min(spawn.getY() + 50, level.getMaxBuildHeight() - 12),
            spawn.getZ() + 100);
    prepareTerrain(level, floor);
    Vec3 position = Vec3.atBottomCenterOf(floor);
    try (var casterSession =
            TestShipPlayerSession.create(
                level, UUID.randomUUID(), "MagicSpecialist", position, null);
        var allySession =
            TestShipPlayerSession.create(
                level, UUID.randomUUID(), "MagicAlly", position.add(0, 0, 4), null)) {
      var caster = casterSession.player;
      var ally = allySession.player;
      ProgressionRegression.listPlayer(level, caster, true);
      ProgressionRegression.listPlayer(level, ally, true);
      try {
        prepare(caster);
        verifyOakskin(level, caster, ally, floor);
        verifyFrostwave(level, caster, ally, floor);
        verifyBloodNeedles(level, caster, ally);
        verifyShackle(level, caster, floor);
      } finally {
        Utils.setPlayerSpellbookStack(caster, ItemStack.EMPTY);
        ProgressionRegression.listPlayer(level, ally, false);
        ProgressionRegression.listPlayer(level, caster, false);
      }
    }
  }

  private static void prepareTerrain(ServerLevel level, BlockPos origin) {
    for (BlockPos pos :
        BlockPos.betweenClosed(origin.offset(-10, -1, -6), origin.offset(10, 4, 12))) {
      level.setBlockAndUpdate(
          pos, (pos.getY() < origin.getY() ? Blocks.STONE : Blocks.AIR).defaultBlockState());
    }
  }

  private static void prepare(ServerPlayer caster) {
    MagicData.getPlayerMagicData(caster).setServerPlayer(caster);
    Utils.setPlayerSpellbookStack(caster, new ItemStack(ItemRegistry.WIMPY_SPELL_BOOK.get()));
    var oakskin = SpellRegistry.OAKSKIN_SPELL.get();
    var frostwave = SpellRegistry.FROSTWAVE_SPELL.get();
    SpellKnowledge.setStarterSpells(
        caster,
        List.of(
            oakskin.getSpellId(),
            frostwave.getSpellId(),
            SpellRegistry.BLOOD_NEEDLES_SPELL.get().getSpellId(),
            SpellRegistry.ARCANE_SHACKLE_SPELL.get().getSpellId()));
    unlock(caster, "magic_mastery_1");
    for (int tier = 2; tier <= 5; tier++) unlock(caster, "magic_destruction_mastery_" + tier);
    caster.setXRot(0);
    caster.setYRot(0);
  }

  private static void verifyOakskin(
      ServerLevel level, ServerPlayer caster, ServerPlayer ally, BlockPos floor) {
    var spell = SpellRegistry.OAKSKIN_SPELL.get();
    var effect = MobEffectRegistry.OAKSKIN;
    float ordinaryCost = cast(caster, spell);
    require(caster.hasEffect(effect) && !ally.hasEffect(effect), "Ordinary Oakskin protects self");
    unlock(caster, OAKSKIN_ALLY);
    var scoreboard = level.getScoreboard();
    var team = scoreboard.addPlayerTeam("oakskin_one");
    var opponents = scoreboard.addPlayerTeam("oakskin_two");
    try {
      requireOakskin(caster, ally, spell, ordinaryCost, !level.getServer().isPvpAllowed());
      scoreboard.addPlayerToTeam(caster.getScoreboardName(), team);
      requireOakskin(caster, ally, spell, ordinaryCost, false);
      scoreboard.addPlayerToTeam(ally.getScoreboardName(), opponents);
      requireOakskin(caster, ally, spell, ordinaryCost, false);
      scoreboard.addPlayerToTeam(ally.getScoreboardName(), team);
      requireOakskin(caster, ally, spell, ordinaryCost, true);
      level.setBlockAndUpdate(floor.south(2).above(), Blocks.STONE.defaultBlockState());
      try {
        requireOakskin(caster, ally, spell, ordinaryCost, false);
      } finally {
        level.removeBlock(floor.south(2).above(), false);
      }
      var previous = ally.position();
      ally.setPos(caster.position().add(0, 0, 10));
      requireOakskin(caster, ally, spell, ordinaryCost, false);
      ally.setPos(previous);
    } finally {
      scoreboard.removePlayerTeam(team);
      scoreboard.removePlayerTeam(opponents);
      caster.removeEffect(effect);
      ally.removeEffect(effect);
    }
  }

  private static void requireOakskin(
      ServerPlayer caster,
      ServerPlayer ally,
      AbstractSpell spell,
      float cost,
      boolean targetsAlly) {
    var effect = MobEffectRegistry.OAKSKIN;
    caster.removeEffect(effect);
    ally.removeEffect(effect);
    require(cast(caster, spell) == cost, "The specialization preserves Oakskin's actual mana cost");
    require(
        ally.hasEffect(effect) == targetsAlly && caster.hasEffect(effect) != targetsAlly,
        "Oakskin protects one eligible target or the caster");
  }

  private static void verifyFrostwave(
      ServerLevel level, ServerPlayer caster, ServerPlayer ally, BlockPos floor) {
    var spell = SpellRegistry.FROSTWAVE_SPELL.get();
    var fire = floor.offset(2, 0, 2);
    var campfire = floor.offset(-2, 0, 2);
    level.setBlockAndUpdate(fire, Blocks.FIRE.defaultBlockState());
    level.setBlockAndUpdate(campfire, Blocks.CAMPFIRE.defaultBlockState());
    caster.setRemainingFireTicks(100);
    ally.setRemainingFireTicks(100);
    float mana = cast(caster, spell);
    require(
        caster.isOnFire() && ally.isOnFire() && level.getBlockState(fire).is(Blocks.FIRE),
        "The ordinary spell does not extinguish players or ordinary fire");
    unlock(caster, FROSTWAVE_EXTINGUISH);
    var scoreboard = level.getScoreboard();
    var team = scoreboard.addPlayerTeam("frostwave_allies");
    try {
      scoreboard.addPlayerToTeam(caster.getScoreboardName(), team);
      require(
          cast(caster, spell) == mana && !caster.isOnFire() && ally.isOnFire(),
          "Frostwave clears self but leaves an unteamed player when the caster has a team");
      require(
          level.isEmptyBlock(fire) && level.getBlockState(campfire).is(Blocks.CAMPFIRE),
          "Frostwave douses exposed ordinary fire while leaving campfires in place");
      scoreboard.addPlayerToTeam(ally.getScoreboardName(), team);
      require(
          cast(caster, spell) == mana && !ally.isOnFire(),
          "The same paid cast extinguishes the visible teammate");
    } finally {
      scoreboard.removePlayerTeam(team);
    }
  }

  private static void verifyBloodNeedles(
      ServerLevel level, ServerPlayer caster, ServerPlayer ally) {
    var owned = new SummonedSkeleton(EntityRegistry.SUMMONED_SKELETON.get(), level);
    var other = new SummonedSkeleton(EntityRegistry.SUMMONED_SKELETON.get(), level);
    SummonManager.initSummon(caster, owned, 1_200, new SummonedEntitiesCastData());
    SummonManager.initSummon(ally, other, 1_200, new SummonedEntitiesCastData());
    owned.setPos(caster.position().add(3, 0, 1));
    other.setPos(caster.position().add(1, 0, 1));
    owned.setHealth(4);
    other.setHealth(4);
    require(level.addFreshEntity(owned) && level.addFreshEntity(other), "Spawn two native summons");
    try {
      var spell = SpellRegistry.BLOOD_NEEDLES_SPELL.get();
      caster.setHealth(caster.getMaxHealth());
      float baseMana = cast(caster, spell);
      needleHit(level, caster, spell);
      require(owned.getHealth() == 4, "Ordinary lifesteal does not redirect healing");
      unlock(caster, BLOOD_NEEDLES_OVERHEAL);
      require(cast(caster, spell) == baseMana, "The specialization preserves Blood Needles' cost");
      needleHit(level, caster, spell);
      require(
          owned.getHealth() > 4 && owned.getHealth() <= 8 && other.getHealth() == 4,
          "Only excess native lifesteal goes to one own undead summon");
      float previous = owned.getHealth();
      caster.setHealth(6);
      needleHit(level, caster, spell);
      require(
          caster.getHealth() > 6 && owned.getHealth() == previous,
          "Missing caster health takes priority over a wounded summon");
    } finally {
      owned.discard();
      other.discard();
      caster.setHealth(caster.getMaxHealth());
    }
  }

  private static void needleHit(ServerLevel level, ServerPlayer caster, AbstractSpell spell) {
    var victim = hostile(level, caster.position().add(-4, 0, -2));
    var projectile = new BloodNeedle(level, caster);
    try {
      DamageSources.applyDamage(victim, 16, spell.getDamageSource(projectile, caster));
      require(victim.getHealth() < victim.getMaxHealth(), "A real Blood Needle damage source hits");
    } finally {
      victim.discard();
    }
  }

  private static void verifyShackle(ServerLevel level, ServerPlayer caster, BlockPos floor) {
    var spell = SpellRegistry.ARCANE_SHACKLE_SPELL.get();
    var first = floor.offset(-5, -1, 6);
    var second = floor.offset(5, -1, 6);
    float cost = cast(caster, spell);
    impact(level, caster, first);
    var victim = hostile(level, Vec3.atBottomCenterOf(first.above()).add(0, 0, 1));
    try {
      tick(level);
      require(chains(level, caster).isEmpty(), "Ordinary Shackle leaves no delayed marker");
    } finally {
      victim.discard();
    }
    unlock(caster, ARCANE_SHACKLE_MARKER);
    require(cast(caster, spell) == cost, "The learned marker uses normal Arcane Shackle payment");
    impact(level, caster, first);
    impact(level, caster, second);
    var abandoned = hostile(level, Vec3.atBottomCenterOf(first.above()).add(0, 0, 1));
    var eligible = hostile(level, Vec3.atBottomCenterOf(second.above()).add(0, 0, 1));
    try {
      tick(level);
      var chains = chains(level, caster);
      require(
          chains.size() == 1 && chains.getFirst().getVictim() == eligible,
          "Only the newest marker tethers a nearby hostile through the native chain");
      chains.getFirst().discard();
    } finally {
      abandoned.discard();
      eligible.discard();
    }
    impact(level, caster, first);
    var clock = (ServerLevelData) level.getLevelData();
    long previous = clock.getGameTime();
    var late = hostile(level, Vec3.atBottomCenterOf(first.above()).add(0, 0, 1));
    try {
      clock.setGameTime(previous + 161);
      tick(level);
      require(chains(level, caster).isEmpty(), "An expired marker creates no chain");
    } finally {
      late.discard();
      clock.setGameTime(previous);
    }
  }

  private static Zombie hostile(ServerLevel level, Vec3 position) {
    var zombie = EntityType.ZOMBIE.create(level);
    require(zombie != null, "Construct the native hostile target");
    zombie.setNoAi(true);
    zombie.setPos(position);
    require(level.addFreshEntity(zombie), "Add the native hostile target");
    return zombie;
  }

  private static void tick(ServerLevel level) {
    NeoForge.EVENT_BUS.post(new LevelTickEvent.Post(() -> true, level));
  }

  private static List<EnderChain> chains(ServerLevel level, ServerPlayer caster) {
    return level.getEntitiesOfClass(
        EnderChain.class,
        caster.getBoundingBox().inflate(16),
        chain -> chain.getOwner() == caster && !chain.isRemoved());
  }

  private static void impact(ServerLevel level, ServerPlayer caster, BlockPos floor) {
    new ShackleImpact(level, caster).hitFloor(floor);
  }

  private static final class ShackleImpact extends ArcaneShackleProjectile {
    ShackleImpact(ServerLevel level, ServerPlayer caster) {
      super(level, caster);
      setChainHealth(8);
      setChainLifetime(100);
      setLashRadius(3);
      setRestraintStrength(0.8F);
    }

    void hitFloor(BlockPos floor) {
      Vec3 contact = Vec3.atBottomCenterOf(floor.above());
      setPos(contact);
      onHitBlock(new BlockHitResult(contact, Direction.UP, floor, false));
      setPos(contact.subtract(0, 0.5, 0));
    }
  }

  private static float cast(ServerPlayer caster, AbstractSpell spell) {
    var magic = MagicData.getPlayerMagicData(caster);
    magic.getPlayerCooldowns().clearCooldowns();
    magic.setMana(500);
    PreparedAbilities.edit(caster, 0, spell.getSpellId());
    PreparedAbilities.select(caster, 0);
    float before = magic.getMana();
    PreparedAbilities.cast(caster);
    AbilitiesRegression.complete(caster);
    float cost = before - magic.getMana();
    require(
        cost > 0 && PreparedAbilities.cooldownTicks(caster, spell) > 0,
        "A real native prepared cast pays mana and starts cooldown: " + spell.getSpellId());
    return cost;
  }

  private static void unlock(ServerPlayer player, String node) {
    SkillsMod.getInstance().tryUnlockSkill(player, MAGIC, node, true);
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}

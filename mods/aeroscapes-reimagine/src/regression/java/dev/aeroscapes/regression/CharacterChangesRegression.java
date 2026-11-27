package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import dev.aeroscapes.equipment.armor.ArmorProficiency;
import dev.aeroscapes.equipment.combat.BeastmasterCompanions;
import dev.aeroscapes.equipment.combat.MartialTechnique;
import dev.aeroscapes.magic.InnateAbility;
import dev.aeroscapes.magic.PreparedAbilities;
import dev.aeroscapes.magic.SpellKnowledge;
import dev.aeroscapes.magic.SpellProgression;
import dev.aeroscapes.progression.ProfessionDiscipline;
import dev.aeroscapes.progression.ProgressionBridge;
import dev.aeroscapes.progression.TalentRefunds;
import dev.aeroscapes.races.CharacterChanges;
import dev.aeroscapes.races.CharacterClass;
import dev.aeroscapes.races.CharacterSubclass;
import dev.aeroscapes.races.Profession;
import dev.aeroscapes.races.Race;
import dev.aeroscapes.races.RaceIdentity;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.puffish.skillsmod.SkillsMod;

final class CharacterChangesRegression {
  private static final ResourceLocation WARRIOR =
      ResourceLocation.parse(ProgressionBridge.COMBAT_CATEGORY);
  private static final ResourceLocation RANGER =
      ResourceLocation.parse(ProgressionBridge.RANGER_CATEGORY);
  private static final ResourceLocation MAGE =
      ResourceLocation.parse(ProgressionBridge.MAGIC_CATEGORY);
  private static final String MISSILE = "irons_spellbooks:magic_missile";
  private static final String MISSILE_NODE = "magic_spell_magic_missile";

  private CharacterChangesRegression() {}

  static void verify(ServerLevel level) {
    var spot = level.getSharedSpawnPos().atY(level.getMaxBuildHeight() - 16);
    try (var session =
        TestShipPlayerSession.create(
            level, UUID.randomUUID(), "IdentityChange", Vec3.atBottomCenterOf(spot), null)) {
      var player = session.player;
      ProgressionRegression.listPlayer(level, player, true);
      try {
        var magic = MagicData.getPlayerMagicData(player);
        magic.setServerPlayer(player);
        magic.getSyncedData();
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));
        require(
            RaceIdentity.choose(
                    player,
                    Race.ELF,
                    0,
                    CharacterClass.WARRIOR,
                    "quick_block",
                    Profession.BLACKSMITH,
                    "material_repair")
                && RaceIdentity.chooseSubclass(player, CharacterSubclass.DEFENDER),
            "Fixture has a free Defender starter and Elf trait");
        verifySubclass(player);
        verifyRace(player);
        verifyClassesAndKnowledge(player);
        verifyProfessionAndPendingGroup(level, player);
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
      } finally {
        ProgressionRegression.listPlayer(level, player, false);
      }
    }
    LogUtils.getLogger()
        .info("Identity seal reset, equipment, prepared slots and study regression passed");
  }

  private static void verifySubclass(ServerPlayer player) {
    var provider = SkillsMod.getInstance();
    provider.unlockSkill(player, WARRIOR, "wide_shield_shove");
    provider.unlockSkill(player, WARRIOR, "heavy_armor_handling");
    provider.unlockSkill(player, ProfessionDiscipline.MINING.category(), "mining_efficiency");
    player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
    require(
        !ArmorProficiency.undertrained(player, player.getItemBySlot(EquipmentSlot.HEAD)),
        "Fixture wears armor that is trained before the reset");
    PreparedAbilities.edit(player, 4, MartialTechnique.SHIELD_SHOVE.id());
    PreparedAbilities.edit(player, 3, InnateAbility.LIGHT.id);
    var loadout = PreparedAbilities.loadout(player);
    var selection = new CharacterChanges.Selection("subclass", "berserker", 0, "");
    var preview = CharacterChanges.preview(player, selection);
    require(
        preview != null
            && preview.disabled().contains(MartialTechnique.SHIELD_SHOVE.id())
            && preview.wornGear().contains("minecraft:iron_helmet")
            && preview
                .talents()
                .retiredGrants()
                .contains(new TalentRefunds.Node("combat", "shield_shove"))
            && preview
                .talents()
                .addedGrants()
                .contains(new TalentRefunds.Node("combat", "last_stand")),
        "Subclass reset forecasts the prepared attack, worn armor and changed grants");
    require(
        CharacterChanges.change(player, selection, preview)
            && RaceIdentity.subclass(player) == CharacterSubclass.BERSERKER
            && learned(player, WARRIOR).equals(Set.of("quick_block", "last_stand"))
            && learned(player, ProfessionDiscipline.MINING.category()).isEmpty()
            && PreparedAbilities.loadout(player).equals(loadout)
            && player.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET)
            && ArmorProficiency.undertrained(player, player.getItemBySlot(EquipmentSlot.HEAD)),
        "A subclass-only change still resets every bank and retains gear and slot assignments");
  }

  private static void verifyRace(ServerPlayer player) {
    var selection = new CharacterChanges.Selection("race", "dwarf", 0, "");
    var head = player.blockPosition().above();
    var level = player.serverLevel();
    var previous = level.getBlockState(head);
    try {
      level.setBlockAndUpdate(head, Blocks.STONE.defaultBlockState());
      require(
          CharacterChanges.preview(player, selection) == null
              && CharacterChanges.problem(player, selection).equals("space"),
          "The proposed race must fit the current world geometry");
    } finally {
      level.setBlockAndUpdate(head, previous);
    }
    player.setHealth(7);
    var preview = CharacterChanges.preview(player, selection);
    require(
        preview != null
            && preview.disabled().contains(InnateAbility.LIGHT.id)
            && preview.talents().retiredGrants().isEmpty(),
        "A race change previews the lost innate preparable action and keeps talent grants");
    require(
        CharacterChanges.change(player, selection, preview)
            && RaceIdentity.race(player) == Race.DWARF
            && PreparedAbilities.loadout(player).contains(InnateAbility.LIGHT.id)
            && player.getHealth() == 7
            && !CharacterChanges.change(player, selection, preview),
        "Changing race retires its innate action without clearing the slot or reusing the seal"
            + " quote");
  }

  private static void verifyClassesAndKnowledge(ServerPlayer player) {
    var mage = new CharacterChanges.Selection("class", "mage", 0, "first_mastery", "elementalist");
    var joinedMage = CharacterChanges.preview(player, mage);
    require(
        joinedMage != null
            && Set.copyOf(joinedMage.addedSpells())
                .equals(Set.of(MISSILE, "irons_spellbooks:heal", "irons_spellbooks:icicle"))
            && CharacterChanges.change(player, mage, joinedMage),
        "Changing to Elementalist previews the starter spells and applies the same grant set");
    require(
        RaceIdentity.characterClass(player) == CharacterClass.MAGE
            && RaceIdentity.subclass(player) == CharacterSubclass.ELEMENTALIST
            && SpellKnowledge.hasAccess(player)
            && !SpellKnowledge.hasFocus(player),
        "Mage access and school identity are restored without another creation journal");

    restoreCompletedStudy(player);
    var provider = SkillsMod.getInstance();
    provider.unlockSkill(player, MAGE, "magic_destruction_power");
    provider.unlockSkill(player, MAGE, "magic_destruction_mastery_2");
    var spell = SpellKnowledge.spell(MISSILE);
    var mageNodes = SpellKnowledge.unlocked(player);
    String mageReason = SpellKnowledge.knowledgeReason(player, spell, mageNodes);
    require(
        mageReason.isEmpty(),
        "Before leaving Mage, rank two must be castable. Reason "
            + mageReason
            + ", rank "
            + SpellKnowledge.rank(player, spell, mageNodes)
            + ", cap "
            + SpellKnowledge.cap(spell, mageNodes)
            + ", talents "
            + mageNodes);
    PreparedAbilities.edit(player, 5, MISSILE);
    require(
        PreparedAbilities.loadout(player).contains(MISSILE), "Prepare the mastered Magic Missile");
    var cooldowns = MagicData.getPlayerMagicData(player).getPlayerCooldowns();
    cooldowns.addCooldown(spell, 400);
    int time = PreparedAbilities.cooldownTicks(player, spell);
    var ranger = new CharacterChanges.Selection("class", "ranger", 0, "bow_stride", "beastmaster");
    var leftMage = CharacterChanges.preview(player, ranger);
    require(
        leftMage != null,
        "Changing Mage to Beastmaster must produce a quote. Header reason "
            + CharacterChanges.problem(player, ranger));
    require(
        leftMage.disabled().contains(MISSILE),
        "Quote the loss of prepared rank-two Magic Missile. Disabled " + leftMage.disabled());
    require(
        leftMage.retiredSpells().contains("irons_spellbooks:icicle"),
        "Retire the Elementalist starter. Retired spells " + leftMage.retiredSpells());
    require(
        leftMage
            .talents()
            .addedGrants()
            .contains(new TalentRefunds.Node("ranger", "beastmaster_fox")),
        "Quote the free Beastmaster fox. Added grants " + leftMage.talents().addedGrants());
    boolean changed = CharacterChanges.change(player, ranger, leftMage);
    require(
        changed,
        "Confirm the exact fresh Beastmaster quote. Still current "
            + leftMage.equals(CharacterChanges.preview(player, ranger))
            + ", learned Magic "
            + SpellKnowledge.unlocked(player)
            + ", learned Ranger "
            + learned(player, RANGER));
    var known = SpellKnowledge.unlocked(player);
    require(
        learned(player, RANGER).equals(Set.of("bow_stride", "beastmaster_fox"))
            && !SpellKnowledge.hasAccess(known)
            && known.equals(Set.of(MISSILE_NODE))
            && SpellProgression.studied(player, MISSILE)
            && SpellKnowledge.rank(player, spell, known) == 2
            && SpellKnowledge.knowledgeReason(player, spell, known).equals("magic_access")
            && PreparedAbilities.loadout(player).contains(MISSILE)
            && time > 0
            && PreparedAbilities.cooldownTicks(player, spell) == time,
        "Completed study, rank, preparation and cooldown survive loss of access");

    provider.unlockSkill(player, MAGE, "magic_mastery_1");
    require(
        SpellKnowledge.knowledgeReason(player, spell, SpellKnowledge.unlocked(player))
            .equals("mastery"),
        "The previous personal rank still requires its mastery");
    provider.unlockSkill(player, MAGE, "magic_destruction_power");
    provider.unlockSkill(player, MAGE, "magic_destruction_mastery_2");
    require(
        SpellKnowledge.knowledgeReason(player, spell, SpellKnowledge.unlocked(player)).isEmpty(),
        "Restoring the mastery enables the same rank without paying for study again");
  }

  private static void verifyProfessionAndPendingGroup(ServerLevel level, ServerPlayer player) {
    var before = player.position();
    var platform = new HashMap<BlockPos, BlockState>();
    for (var position :
        BlockPos.betweenClosed(
            player.blockPosition().offset(-3, -1, -3), player.blockPosition().offset(3, -1, 3))) {
      platform.put(position.immutable(), level.getBlockState(position));
      level.setBlockAndUpdate(position, Blocks.STONE.defaultBlockState());
    }
    var provider = SkillsMod.getInstance();
    String fox = "beastmaster_fox";
    int cooldown;
    try {
      provider.unlockSkill(player, RANGER, "beastmaster_persistence");
      require(
          BeastmasterCompanions.activate(player, fox)
              && BeastmasterCompanions.groupCount(player) == 1,
          "Only the free Beastmaster fox is summoned with paid persistence");
      player.setPos(before.add(0, 7, 0));
      BeastmasterCompanions.waystoneArrived(player);
      require(
          BeastmasterCompanions.pending(player),
          "Unsafe destination leaves a real saved group pending");
      cooldown = BeastmasterCompanions.cooldownTicks(player, fox);
      verifyProfession(player);
    } finally {
      player.setPos(before);
      platform.forEach(level::setBlockAndUpdate);
    }
    require(
        !BeastmasterCompanions.pending(player)
            && BeastmasterCompanions.groupCount(player) == 0
            && BeastmasterCompanions.cooldownTicks(player, fox) == cooldown
            && cooldown > 0,
        "The reset dismisses the pending group when it removes paid persistence, retaining"
            + " cooldown");
  }

  private static void verifyProfession(ServerPlayer player) {
    var profession = new CharacterChanges.Selection("profession", "farmer", 0, "extra_harvest");
    var preview = CharacterChanges.preview(player, profession);
    require(
        preview != null
            && preview
                .talents()
                .addedGrants()
                .contains(new TalentRefunds.Node("farming", "extra_harvest"))
            && preview
                .talents()
                .retiredGrants()
                .contains(new TalentRefunds.Node("smithing", "material_repair"))
            && CharacterChanges.change(player, profession, preview),
        "Changing profession swaps its free grant and also resets paid Magic mastery");
    require(
        RaceIdentity.profession(player) == Profession.FARMER
            && learned(player, ProfessionDiscipline.SMITHING.category()).isEmpty()
            && learned(player, ProfessionDiscipline.FARMING.category())
                .equals(Set.of("extra_harvest"))
            && learned(player, MAGE).equals(Set.of(MISSILE_NODE))
            && learned(player, RANGER).contains("beastmaster_fox")
            && !player.getInventory().contains(new ItemStack(Items.WHEAT_SEEDS)),
        "Only current profession, class and study grants remain and no creation kit is issued");
  }

  private static void restoreCompletedStudy(ServerPlayer player) {
    var data = player.getPersistentData().getCompound("aeroscapes_reimagine_abilities");
    var studied = new CompoundTag();
    studied.putBoolean(MISSILE, true);
    data.put("studied_spells", studied);
    var grants = new CompoundTag();
    grants.putBoolean(MISSILE_NODE, true);
    data.put("study_grants", grants);
    var ranks = new CompoundTag();
    ranks.putInt(MISSILE, 2);
    data.put("ranks", ranks);
    SpellProgression.restoreStudyGrants(player);
  }

  private static Set<String> learned(ServerPlayer player, ResourceLocation category) {
    return Set.copyOf(SkillsMod.getInstance().getUnlockedSkills(player, category).orElseThrow());
  }

  private static void require(boolean condition, String description) {
    if (!condition) throw new IllegalStateException(description);
  }
}

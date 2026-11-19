package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import dev.aeroscapes.magic.AbilityNetwork;
import dev.aeroscapes.magic.InnateAbility;
import dev.aeroscapes.magic.MagicPractice;
import dev.aeroscapes.magic.PreparedAbilities;
import dev.aeroscapes.magic.RacialAbilities;
import dev.aeroscapes.magic.SpellKnowledge;
import dev.aeroscapes.races.OriginOrbs;
import dev.aeroscapes.races.Race;
import dev.aeroscapes.races.RaceIdentity;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.util.Utils;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

final class RacialAbilitiesRegression {
  private RacialAbilitiesRegression() {}

  static void verify(ServerLevel level) {
    CreativeModeTabs.tryRebuildTabContents(level.enabledFeatures(), true, level.registryAccess());
    for (var tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
      for (var entries : List.of(tab.getDisplayItems(), tab.getSearchTabDisplayItems())) {
        require(
            entries.stream().noneMatch(stack -> stack.is(OriginOrbs.HIDDEN)),
            "Native reset orbs absent from creative tabs and search: " + tab.getDisplayName());
      }
    }
    var position = Vec3.atBottomCenterOf(level.getSharedSpawnPos()).add(0, 32, 0);
    verifyDwarf(level, position);
    verifyElf(level, position);
    AmuletStepRegression.verify(level);
    LogUtils.getLogger().info("Racial prepared abilities regression passed");
  }

  private static void verifyDwarf(ServerLevel level, Vec3 position) {
    var uuid = UUID.randomUUID();
    CompoundTag saved;
    var firebolt = SpellRegistry.FIREBOLT_SPELL.get();
    var sight = InnateAbility.NIGHT_VISION;
    try (var session = TestShipPlayerSession.create(level, uuid, "SightFixture", position, null)) {
      var player = session.player;
      AbilitiesRegression.initialize(player);
      AbilitiesRegression.unlock(player, "magic_mastery_1");
      PreparedAbilities.edit(player, 3, firebolt.getSpellId());
      PreparedAbilities.select(player, 3);
      var previous = PreparedAbilities.preparation(player);
      assertInnate(session, List.of());
      PreparedAbilities.edit(player, 3, sight.id);
      require(
          PreparedAbilities.preparation(player).equals(previous),
          "No racial grant before selection");
      require(RaceIdentity.choose(player, Race.DWARF, 0), "Choose dwarf");
      PreparedAbilities.sync(player, true);
      assertInnate(session, List.of(sight.id));
      require(
          PreparedAbilities.preparation(player).equals(previous),
          "Race selection preserves former slots");
      var magic = MagicData.getPlayerMagicData(player);
      magic.getPlayerCooldowns().addCooldown(firebolt, 400);
      PreparedAbilities.edit(player, 7, sight.id);
      PreparedAbilities.edit(player, 3, sight.id);
      require(
          PreparedAbilities.preparation(player).slots().get(7).equals(firebolt.getSpellId()),
          "Cross-provider swap");
      var focus = Utils.getPlayerSpellbookStack(player);
      var container = ISpellContainer.get(focus);
      require(
          container.getSpellAtIndex(3).getSpell() == SpellRegistry.none()
              && container.getSpellAtIndex(7).getSpell() == firebolt,
          "Book receives only the native spell at its actual mixed-slot index");
      PreparedAbilities.edit(player, 3, InnateAbility.LIGHT.id);
      require(
          PreparedAbilities.preparation(player).slots().get(3).equals(sight.id),
          "Forged other-race assignment rejected");
      Utils.setPlayerSpellbookStack(player, ItemStack.EMPTY);
      magic.setMana(0);
      long practice = MagicPractice.credit(player);
      player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 140));
      PreparedAbilities.cast(player);
      require(
          session.abilities().dwarfNightVision(),
          "First activation enables full vision with empty hands");
      PreparedAbilities.cast(player);
      require(
          !session.abilities().dwarfNightVision()
              && player.getEffect(MobEffects.NIGHT_VISION).getDuration() == 140,
          "Toggling off leaves the native potion untouched");
      PreparedAbilities.cast(player);
      PreparedAbilities.edit(player, 6, sight.id);
      require(session.abilities().dwarfNightVision(), "Moving its slot keeps the toggle");
      PreparedAbilities.edit(player, 6, "");
      require(!session.abilities().dwarfNightVision(), "Unpreparing turns the toggle off");
      PreparedAbilities.edit(player, 6, sight.id);
      PreparedAbilities.select(player, 6);
      PreparedAbilities.cast(player);
      require(
          MagicPractice.credit(player) == practice && magic.getMana() == 0 && !magic.isCasting(),
          "Racial input grants no magic practice and needs neither mana nor a cast");
      require(
          PreparedAbilities.cooldownTicks(player, firebolt) == 400,
          "Moving the spell preserves its cooldown");
      NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
      AbilitiesRegression.verifyDeathClone(player, RacialAbilitiesRegression::assertMixedDwarf);
      saved = player.saveWithoutId(new CompoundTag());
    }
    try (var session = TestShipPlayerSession.create(level, uuid, "SightFixture", position, saved)) {
      assertMixedDwarf(session.player);
      PreparedAbilities.sync(session.player, true);
      require(
          session.abilities().dwarfNightVision(), "Racial vision re-synchronizes after loading");
    }
  }

  private static void assertMixedDwarf(ServerPlayer player) {
    var prepared = PreparedAbilities.preparation(player);
    require(
        RaceIdentity.race(player) == Race.DWARF
            && RacialAbilities.nightVision(player)
            && prepared.selected() == 6
            && prepared.slots().get(6).equals(InnateAbility.NIGHT_VISION.id)
            && prepared.slots().get(7).equals(SpellRegistry.FIREBOLT_SPELL.get().getSpellId()),
        "Death and native save/load retain identity, toggle, spell, innate ability and selection");
  }

  private static void verifyElf(ServerLevel level, Vec3 position) {
    try (var session =
        TestShipPlayerSession.create(level, UUID.randomUUID(), "LightFixture", position, null)) {
      var player = session.player;
      AbilitiesRegression.initialize(player);
      require(RaceIdentity.choose(player, Race.ELF, 0), "Choose elf");
      Utils.setPlayerSpellbookStack(player, ItemStack.EMPTY);
      MagicData.getPlayerMagicData(player).setMana(0);
      var ability = InnateAbility.LIGHT;
      PreparedAbilities.edit(player, 0, ability.id);
      assertInnate(session, List.of(ability.id));
      long practice = MagicPractice.credit(player);
      int blockLight = level.getBrightness(LightLayer.BLOCK, player.blockPosition());
      var block = level.getBlockState(player.blockPosition());
      PreparedAbilities.cast(player);
      var light = player.getEffect(RacialAbilities.ELF_LIGHT);
      require(
          light != null
              && light.getDuration() == RacialAbilities.LIGHT_TICKS
              && !player.isOnFire()
              && level.getBrightness(LightLayer.BLOCK, player.blockPosition()) == blockLight
              && level.getBlockState(player.blockPosition()) == block,
          "Following light sets only a timed player effect, preserving block and spawn light");
      player.baseTick();
      require(
          light.getDuration() == RacialAbilities.LIGHT_TICKS - 1, "Temporary light counts down");
      PreparedAbilities.cast(player);
      require(
          light.getDuration() == RacialAbilities.LIGHT_TICKS
              && MagicPractice.credit(player) == practice
              && !SpellKnowledge.hasFocus(player),
          "A second activation renews the light without a focus or practice");
      AbilitiesRegression.verifyDeathClone(
          player,
          replacement ->
              require(
                  !replacement.hasEffect(RacialAbilities.ELF_LIGHT)
                      && PreparedAbilities.preparation(replacement)
                          .slots()
                          .getFirst()
                          .equals(ability.id),
                  "Death preserves the prepared action and ends its temporary light"));
      while (light.getDuration() > 1) light.tick(player, () -> {});
      player.baseTick();
      require(
          !player.hasEffect(RacialAbilities.ELF_LIGHT), "Native expiration extinguishes the light");
    }
  }

  private static void assertInnate(TestShipPlayerSession session, List<String> expected) {
    require(
        session.abilities().entries().stream()
            .filter(AbilityNetwork.Entry::innate)
            .map(AbilityNetwork.Entry::id)
            .toList()
            .equals(expected),
        "Catalogue exposes exactly the abilities of the chosen race");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}

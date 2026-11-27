package dev.aeroscapes.races;

import dev.aeroscapes.magic.SpellKnowledge;
import dev.aeroscapes.progression.ProgressionBridge;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.puffish.skillsmod.SkillsMod;

final class SubclassProgression {
  private static final String SUBCLASS = "subclass";
  private static final String FUNDED = "subclass_starter_funded";

  private SubclassProgression() {}

  @Nullable
  static String talentNode(@Nullable CharacterSubclass chosen) {
    return chosen == null || chosen.isSpellStarter() ? null : chosen.starter();
  }

  static boolean available(ServerPlayer player, CharacterSubclass chosen) {
    return ProgressionBridge.categoriesAvailable()
        && (chosen.isSpellStarter()
            ? SpellKnowledge.spell(chosen.starter()).isEnabled()
            : SkillsMod.getInstance()
                .getSkillState(
                    player, ClassProgression.category(chosen.mainClass()), chosen.starter())
                .isPresent());
  }

  static boolean assign(ServerPlayer player, @Nullable CharacterSubclass chosen) {
    var data = RaceIdentity.data(player);
    if (chosen == null) {
      data.remove(SUBCLASS);
      data.remove(FUNDED);
      ClassProgression.restoreSpells(player);
      return true;
    }
    var node = talentNode(chosen);
    if (node != null) {
      boolean funded = !ClassProgression.hasTalent(player, node);
      if (funded) {
        SkillsMod.getInstance()
            .unlockSkill(player, ClassProgression.category(chosen.mainClass()), node);
        if (!ClassProgression.hasTalent(player, node)) return false;
      }
      data.putBoolean(FUNDED, funded);
    } else data.remove(FUNDED);
    data.putString(SUBCLASS, chosen.id());
    ClassProgression.restoreSpells(player);
    return true;
  }

  static boolean restore(ServerPlayer player) {
    var subclass = RaceIdentity.subclass(player);
    var node = talentNode(subclass);
    if (node != null && !ClassProgression.hasTalent(player, node)) {
      SkillsMod.getInstance()
          .unlockSkill(player, ClassProgression.category(subclass.mainClass()), node);
      if (!ClassProgression.hasTalent(player, node)) return false;
    }
    ClassProgression.restoreSpells(player);
    return true;
  }

  static void markGranted(ServerPlayer player) {
    if (talentNode(RaceIdentity.subclass(player)) != null)
      RaceIdentity.data(player).putBoolean(FUNDED, true);
  }

  static boolean funded(ServerPlayer player) {
    return RaceIdentity.subclass(player) != null && RaceIdentity.data(player).getBoolean(FUNDED);
  }

  @Nullable
  static CharacterSubclass read(CompoundTag data, @Nullable CharacterClass mainClass) {
    var chosen = CharacterSubclass.byId(data.getString(SUBCLASS));
    return chosen != null && chosen.mainClass() == mainClass ? chosen : null;
  }
}

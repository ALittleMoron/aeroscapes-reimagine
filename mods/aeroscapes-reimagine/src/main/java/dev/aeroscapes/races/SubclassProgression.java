package dev.aeroscapes.races;

import dev.aeroscapes.progression.ProgressionBridge;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.puffish.skillsmod.SkillsMod;

final class SubclassProgression {
  private static final ResourceLocation COMBAT =
      ResourceLocation.parse(ProgressionBridge.COMBAT_CATEGORY);
  private static final String SUBCLASS = "subclass";
  private static final String FUNDED = "subclass_starter_funded";

  private SubclassProgression() {}

  static boolean available(ServerPlayer player, CharacterSubclass chosen) {
    return ProgressionBridge.categoriesAvailable()
        && SkillsMod.getInstance().getSkillState(player, COMBAT, chosen.starter()).isPresent();
  }

  static boolean assign(ServerPlayer player, @Nullable CharacterSubclass chosen) {
    var data = RaceIdentity.data(player);
    if (chosen == null) {
      data.remove(SUBCLASS);
      data.remove(FUNDED);
      return true;
    }
    var provider = SkillsMod.getInstance();
    boolean funded = !ClassProgression.hasCombatTalent(player, chosen.starter());
    if (funded) {
      provider.unlockSkill(player, COMBAT, chosen.starter());
      if (!ClassProgression.hasCombatTalent(player, chosen.starter())) return false;
    }
    data.putString(SUBCLASS, chosen.id());
    data.putBoolean(FUNDED, funded);
    return true;
  }

  static boolean restore(ServerPlayer player) {
    var subclass = RaceIdentity.subclass(player);
    if (subclass == null || ClassProgression.hasCombatTalent(player, subclass.starter()))
      return true;
    var provider = SkillsMod.getInstance();
    provider.unlockSkill(player, COMBAT, subclass.starter());
    return ClassProgression.hasCombatTalent(player, subclass.starter());
  }

  static boolean funded(ServerPlayer player) {
    return RaceIdentity.subclass(player) != null && RaceIdentity.data(player).getBoolean(FUNDED);
  }

  @Nullable
  static CharacterSubclass read(
      net.minecraft.nbt.CompoundTag data, @Nullable CharacterClass mainClass) {
    var chosen = CharacterSubclass.byId(data.getString(SUBCLASS));
    return chosen != null && chosen.mainClass() == mainClass ? chosen : null;
  }
}

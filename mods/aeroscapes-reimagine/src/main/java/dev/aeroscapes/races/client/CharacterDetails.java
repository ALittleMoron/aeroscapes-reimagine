package dev.aeroscapes.races.client;

import static dev.aeroscapes.races.client.RaceVisuals.text;

import dev.aeroscapes.races.CharacterClass;
import dev.aeroscapes.races.Race;
import dev.aeroscapes.races.RaceTraits;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

final class CharacterDetails {
  private CharacterDetails() {}

  static MutableComponent className(CharacterClass characterClass) {
    return text("class." + characterClass.id() + ".name");
  }

  static MutableComponent talent(String starter, String field) {
    String node = starter.equals("first_mastery") ? "magic_mastery_1" : starter;
    return Component.translatable(
        "puffish_skills.aeroscapes_reimagine.combat." + node + "." + field);
  }

  static MutableComponent race(Race race, int style, boolean expanded) {
    String key = race.id() + ".";
    var details = text("identity", text(key + "name"), text(key + "style." + style));
    section(details, text("racial_benefits"), ChatFormatting.GREEN, text(key + "benefits"));
    section(details, text("racial_drawbacks"), ChatFormatting.GOLD, text(key + "drawbacks"));
    if (expanded) {
      section(details, text("body"), ChatFormatting.AQUA, text(key + "body"));
      details.append("\n").append(text("vessels"));
    }
    return details;
  }

  static MutableComponent classProfile(
      CharacterClass characterClass, String starter, boolean expanded) {
    String key = "class." + characterClass.id() + ".";
    var details = className(characterClass).append("\n").append(text(key + "description"));
    section(details, text("class_benefits"), ChatFormatting.GREEN, text(key + "benefits"));
    section(details, text("class_drawbacks"), ChatFormatting.GOLD, text("no_class_penalty"));
    section(
        details,
        text("starter", talent(starter, "title")),
        ChatFormatting.AQUA,
        starter.equals("first_mastery") ? text("mage_start") : talent(starter, "description"));
    if (expanded) {
      if (characterClass == CharacterClass.MAGE)
        details.append("\n").append(talent(starter, "description"));
      if (characterClass == CharacterClass.ROGUE)
        details.append("\n").append(text("light_weapons"));
      details.append("\n").append(text("talent_access"));
    }
    return details;
  }

  static MutableComponent practice(
      Race race, @Nullable CharacterClass characterClass, boolean expanded) {
    var values =
        text(
            "practice_values",
            100 + RaceTraits.practiceBonusPercent(race, characterClass, "combat"),
            100 + RaceTraits.practiceBonusPercent(race, characterClass, "archery"),
            100 + RaceTraits.practiceBonusPercent(race, characterClass, "magic"));
    var result =
        text(characterClass == null ? "race_practice" : "combined_practice")
            .withStyle(ChatFormatting.AQUA);
    var paragraph = Component.empty().append(result).append("\n").append(values);
    if (expanded) paragraph.append("\n").append(text("practice_help"));
    return paragraph;
  }

  private static void section(
      MutableComponent doc, MutableComponent title, ChatFormatting color, Component body) {
    doc.append("\n\n").append(title.withStyle(color)).append("\n").append(body);
  }
}

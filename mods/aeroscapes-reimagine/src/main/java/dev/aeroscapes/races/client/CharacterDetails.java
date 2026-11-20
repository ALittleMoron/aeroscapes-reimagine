package dev.aeroscapes.races.client;

import static dev.aeroscapes.races.client.RaceVisuals.text;

import dev.aeroscapes.races.CharacterClass;
import dev.aeroscapes.races.ClassTraits;
import dev.aeroscapes.races.Profession;
import dev.aeroscapes.races.Race;
import dev.aeroscapes.races.RaceTraits;
import java.text.NumberFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

final class CharacterDetails {
  private CharacterDetails() {}

  static MutableComponent className(CharacterClass characterClass) {
    return text("class." + characterClass.id() + ".name");
  }

  static MutableComponent professionName(Profession profession) {
    return text("profession." + profession.id() + ".name");
  }

  static MutableComponent identity(
      Race race, CharacterClass characterClass, Profession profession) {
    return text(
        "sheet", text(race.id() + ".name"), className(characterClass), professionName(profession));
  }

  static MutableComponent professionTalent(String starter, String field) {
    return Component.translatable(
        "puffish_skills.aeroscapes_reimagine.profession." + starter + "." + field);
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

  record StartingAttributes(
      int hearts, int stamina, int walkPercent, int armor, int mana, double blockReach) {
    static StartingAttributes of(
        Race race, CharacterClass characterClass, String professionStarter) {
      int armor = characterClass == CharacterClass.WARRIOR ? ClassTraits.WARRIOR_ARMOR : 0;
      int mana = 100 + (characterClass == CharacterClass.MAGE ? ClassTraits.MAGE_MANA : 0);
      double reach = 4.5 + (professionStarter.equals("long_reach") ? 1 : 0);
      return switch (race) {
        case HUMAN -> new StartingAttributes(10, 1000, 100, armor, mana, reach);
        case DWARF -> new StartingAttributes(12, 1100, 95, armor, mana, reach);
        case GOBLIN -> new StartingAttributes(8, 850, 100, armor, mana, reach);
        case ELF -> new StartingAttributes(9, 900, 106, armor, mana, reach);
      };
    }
  }

  static MutableComponent attributes(
      Race race,
      CharacterClass characterClass,
      String professionStarter,
      boolean expanded,
      NumberFormat numbers) {
    var stats = StartingAttributes.of(race, characterClass, professionStarter);
    var result = text("starting_stats").withStyle(ChatFormatting.AQUA);
    var paragraph =
        Component.empty()
            .append(result)
            .append("\n")
            .append(text("reserves", stats.hearts(), stats.stamina(), stats.mana()))
            .append("\n")
            .append(
                text(
                    "movement",
                    stats.armor(),
                    stats.walkPercent(),
                    numbers.format(stats.blockReach())));
    if (characterClass == CharacterClass.ROGUE)
      paragraph
          .append("\n")
          .append(
              text(
                  "rogue_speed",
                  numbers.format(
                      stats.walkPercent() * (1 + ClassTraits.ROGUE_GROUND_SPEED_BONUS))));
    if (expanded) paragraph.append("\n").append(text("stat_sources"));
    return paragraph;
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

  static MutableComponent professionProfile(
      Profession profession, String starter, boolean expanded) {
    var details =
        professionName(profession)
            .append("\n")
            .append(text("profession." + profession.id() + ".description"));
    details
        .append("\n")
        .append(
            text(
                "profession_practice",
                Component.translatable("pmmo." + profession.practiceSkill()),
                RaceTraits.PRACTICE_AFFINITY_PERCENT));
    section(
        details,
        text("profession_starter", professionTalent(starter, "title")),
        ChatFormatting.AQUA,
        professionTalent(starter, "description"));
    var kit = Component.empty();
    for (var stack : profession.kit()) {
      if (!kit.getSiblings().isEmpty()) kit.append(", ");
      kit.append(text("kit_stack", stack.getHoverName(), stack.getCount()));
    }
    section(details, text("profession_kit"), ChatFormatting.GREEN, kit);
    details.append("\n").append(text("profession_drawbacks"));
    if (expanded) details.append("\n").append(text("profession_access"));
    return details;
  }

  static MutableComponent practice(
      Race race, CharacterClass characterClass, Profession profession, boolean expanded) {
    var values =
        text(
            "practice_values",
            100 + RaceTraits.practiceBonusPercent(race, characterClass, profession, "combat"),
            100 + RaceTraits.practiceBonusPercent(race, characterClass, profession, "archery"),
            100 + RaceTraits.practiceBonusPercent(race, characterClass, profession, "magic"));
    var result = text("combined_practice").withStyle(ChatFormatting.AQUA);
    var paragraph =
        Component.empty()
            .append(result)
            .append("\n")
            .append(values)
            .append("\n")
            .append(
                text(
                    "trade_practice",
                    Component.translatable("pmmo." + profession.practiceSkill()),
                    100
                        + RaceTraits.practiceBonusPercent(
                            race, characterClass, profession, profession.practiceSkill())));
    if (expanded) paragraph.append("\n").append(text("practice_help"));
    return paragraph;
  }

  private static void section(
      MutableComponent doc, MutableComponent title, ChatFormatting color, Component body) {
    doc.append("\n\n").append(title.withStyle(color)).append("\n").append(body);
  }
}

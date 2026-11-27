package dev.aeroscapes.races.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aeroscapes.races.CharacterClass;
import dev.aeroscapes.races.CharacterSubclass;
import dev.aeroscapes.races.Profession;
import dev.aeroscapes.races.Race;
import dev.aeroscapes.races.RaceTraits;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

public final class CharacterPreviewTest {
  private static final Path DATA =
      Path.of(
          "../../pack/global_packs/required_data/aeroscapes_reimagine_rules/data/aeroscapes_reimagine");
  private static final Path ASSETS = Path.of("src/main/resources/assets");

  private CharacterPreviewTest() {}

  public static void verify() throws IOException {
    checkReturningCharacters();
    var definitionsByProfession = new EnumMap<Profession, JsonObject>(Profession.class);
    for (var job : Profession.values()) {
      Path definitionFile =
          DATA.resolve("puffish_skills/categories")
              .resolve(job.practiceSkill())
              .resolve("definitions.json");
      definitionsByProfession.put(job, read(definitionFile));
    }
    int identities = 0;
    for (Race race : Race.values()) {
      var powers = new HashMap<String, Double>();
      for (var id : read(DATA.resolve("origins/" + race.id() + ".json")).getAsJsonArray("powers")) {
        var power =
            read(DATA.resolve("origins/powers/" + id.getAsString().split(":")[1] + ".json"));
        if (power.has("attribute"))
          powers.put(power.get("attribute").getAsString(), power.get("amount").getAsDouble());
      }
      for (var role : CharacterClass.selectable()) {
        for (var job : Profession.values()) {
          identities++;
          var definitions = definitionsByProfession.get(job);
          for (var starter : job.starters()) {
            var stats = CharacterDetails.StartingAttributes.of(race, role, starter);
            require(
                stats.hearts() == 10 + powers.getOrDefault("minecraft:generic.max_health", 0.0) / 2
                    && stats.stamina() == 1000 + powers.getOrDefault("paraglider:max_stamina", 0.0)
                    && stats.walkPercent()
                        == Math.round(
                            100
                                * (1
                                    + powers.getOrDefault(
                                        "minecraft:generic.movement_speed", 0.0))),
                "Racial totals diverged from " + race);
            double reach = 4.5;
            for (var reward : definitions.getAsJsonObject(starter).getAsJsonArray("rewards")) {
              var data = reward.getAsJsonObject().getAsJsonObject("data");
              if (data.get("attribute")
                  .getAsString()
                  .equals("minecraft:player.block_interaction_range"))
                reach += data.get("value").getAsDouble();
            }
            require(
                stats.blockReach() == reach
                    && stats.mana() == (role == CharacterClass.MAGE ? 120 : 100)
                    && stats.armor() == (role == CharacterClass.WARRIOR ? 2 : 0),
                "Class and talent totals for " + starter);
            require(
                RaceTraits.practiceBonusPercent(race, role, job, job.practiceSkill()) == 20,
                "Profile skill affinity for " + job);
          }
        }
      }
    }
    require(identities == 48, "Expected all 48 sets of starting attributes");
    for (var locale : List.of(Locale.US, Locale.forLanguageTag("ru-RU"))) checkTranslations(locale);
  }

  private static void checkTranslations(Locale locale) throws IOException {
    var keys = new HashSet<String>();
    for (String namespace :
        List.of(
            "aeroscapes_race_visuals",
            "aeroscapes_reimagine_techniques",
            "aeroscapes_reimagine_progression_translations",
            "aeroscapes_reimagine_magic_tree_translations")) {
      keys.addAll(
          read(ASSETS.resolve(
                  namespace + "/lang/" + locale.toString().toLowerCase(Locale.ROOT) + ".json"))
              .keySet());
    }
    String nativePath =
        "assets/irons_spellbooks/lang/" + locale.toString().toLowerCase(Locale.ROOT) + ".json";
    try (var nativeLanguage =
        CharacterPreviewTest.class.getClassLoader().getResourceAsStream(nativePath)) {
      require(nativeLanguage != null, "Missing pinned Iron's spell names for " + locale);
      keys.addAll(
          JsonParser.parseReader(new InputStreamReader(nativeLanguage, StandardCharsets.UTF_8))
              .getAsJsonObject()
              .keySet());
    }
    for (var role : CharacterClass.selectable())
      for (var subclass : CharacterSubclass.choices(role)) {
        translated(CharacterDetails.subclassProfile(subclass), keys);
        translated(CharacterDetails.subclassSummary(subclass), keys);
      }
    for (Race race : Race.values()) {
      for (int style = 0; style < race.styleCount(); style++)
        translated(CharacterDetails.race(race, style, true), keys);
      for (CharacterClass role : CharacterClass.selectable()) {
        for (String ability : role.starters())
          translated(CharacterDetails.classProfile(role, ability, true), keys);
        for (Profession job : Profession.values()) {
          translated(CharacterDetails.identity(race, role, job), keys);
          translated(CharacterDetails.practice(race, role, job, true), keys);
          for (String ability : job.starters()) {
            translated(CharacterDetails.professionTalent(ability, "title"), keys);
            translated(CharacterDetails.professionTalent(ability, "description"), keys);
            translated(
                CharacterDetails.attributes(
                    race, role, ability, true, NumberFormat.getInstance(locale)),
                keys);
          }
        }
      }
    }
  }

  private static void translated(Component component, Set<String> keys) {
    if (component.getContents() instanceof TranslatableContents contents) {
      var key = contents.getKey();
      if (!key.startsWith("pmmo."))
        require(keys.contains(key), "Missing character translation for " + key);
      for (Object argument : contents.getArgs())
        if (argument instanceof Component nested) translated(nested, keys);
    }
    for (Component sibling : component.getSiblings()) translated(sibling, keys);
  }

  private static void checkReturningCharacters() {
    require(
        CharacterClass.selectable()
                .equals(List.of(CharacterClass.WARRIOR, CharacterClass.RANGER, CharacterClass.MAGE))
            && CharacterClass.RANGER
                .starters()
                .equals(List.of("bow_stride", "steady_aim", "quick_recovery", "opening_strike")),
        "Creation offers three classes and all four Ranger passives");
    for (var role : CharacterClass.selectable())
      require(
          CharacterSubclass.choices(role).size() == 2
              && CharacterSubclass.validChoice(role, "")
              && CharacterSubclass.choices(role).stream()
                  .allMatch(
                      value ->
                          value.mainClass() == role
                              && CharacterSubclass.validChoice(role, value.id())),
          "Each class has two compatible subclass choices and allows Later");
    var fresh = new RaceClient.Profile(null, 0, Race.DEFAULT_TINT, null, "", null, "", false);
    require(
        RaceScreen.Page.remaining(fresh)
            .equals(
                List.of(
                    RaceScreen.Page.RACE,
                    RaceScreen.Page.CLASS,
                    RaceScreen.Page.SUBCLASS,
                    RaceScreen.Page.PROFESSION,
                    RaceScreen.Page.SUMMARY)),
        "New characters compare their subclasses after choosing a class");
    var previousRace =
        new RaceClient.Profile(Race.GOBLIN, 2, Race.DEFAULT_TINT, null, "", null, "", false);
    require(
        RaceScreen.Page.remaining(previousRace)
            .equals(
                List.of(
                    RaceScreen.Page.CLASS,
                    RaceScreen.Page.SUBCLASS,
                    RaceScreen.Page.PROFESSION,
                    RaceScreen.Page.SUMMARY)),
        "A partial character retains race and appearance");
    for (var role : CharacterClass.selectable()) {
      var returning =
          new RaceClient.Profile(
              Race.HUMAN,
              0,
              Race.DEFAULT_TINT,
              role,
              role.starters().getFirst(),
              Profession.FARMER,
              "careful_tilling",
              null,
              true,
              false);
      require(
          returning.needsSubclass()
              && returning.subclassDeferred()
              && RaceScreen.Page.remaining(returning).equals(List.of(RaceScreen.Page.SUMMARY)),
          "A deferred subclass can still be chosen from the character sheet");
    }
    for (boolean pending : List.of(false, true)) {
      var previousClass =
          new RaceClient.Profile(
              Race.ELF,
              1,
              Race.DEFAULT_TINT,
              CharacterClass.MAGE,
              "first_mastery",
              null,
              "",
              pending);
      require(
          !previousClass.complete()
              && RaceScreen.Page.remaining(previousClass)
                  .equals(
                      pending
                          ? List.of(RaceScreen.Page.PROFESSION, RaceScreen.Page.SUMMARY)
                          : List.of(
                              RaceScreen.Page.SUBCLASS,
                              RaceScreen.Page.PROFESSION,
                              RaceScreen.Page.SUMMARY)),
          "A partial character retains the class and its starter, including pending grants");
      var chosen =
          new RaceClient.Profile(
              Race.DWARF,
              2,
              Race.DEFAULT_TINT,
              CharacterClass.RANGER,
              "opening_strike",
              Profession.FARMER,
              "careful_tilling",
              pending);
      require(
          chosen.complete() != pending
              && RaceScreen.Page.remaining(chosen).equals(List.of(RaceScreen.Page.SUMMARY)),
          "A saved profession cannot be changed during a retry");
    }
  }

  private static JsonObject read(Path path) throws IOException {
    return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}

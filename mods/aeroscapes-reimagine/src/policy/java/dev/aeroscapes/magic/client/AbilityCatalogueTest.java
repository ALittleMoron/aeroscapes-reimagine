package dev.aeroscapes.magic.client;

import dev.aeroscapes.magic.AbilityNetwork;
import java.util.List;
import java.util.Map;

public final class AbilityCatalogueTest {
  private AbilityCatalogueTest() {}

  public static void main(String[] args) {
    var fire = entry("firebolt", "fire", 1, 3, "");
    var unknown = entry("fireball", "fire", 0, 3, "unlearned");
    var mana = entry("icicle", "ice", 1, 3, "mana");
    var cooldown = entry("heal", "holy", 1, 3, "cooldown");
    var focus = entry("shield", "evocation", 1, 3, "focus");
    var mastery = entry("meteor", "fire", 3, 2, "mastery");
    var discovery = entry("black_hole", "eldritch", 1, 3, "discovery");
    var disabled = entry("portal", "ender", 1, 3, "disabled");
    var entries = List.of(fire, unknown, mana, cooldown, focus, mastery, discovery, disabled);
    Map<String, String> names =
        Map.of(
            fire.id(), "Огненная стрела",
            unknown.id(), "Огненный шар",
            mana.id(), "Сосулька",
            cooldown.id(), "Исцеление",
            focus.id(), "Щит",
            mastery.id(), "Метеорит",
            discovery.id(), "Чёрная дыра",
            disabled.id(), "Портал");
    var learned = AbilityCatalogue.matching(entries, true, "", "", names::get);
    require(
        learned.size() == 7 && !learned.contains(unknown), "Only positive personal ranks appear");
    require(
        learned.containsAll(List.of(mana, cooldown, focus, mastery, discovery, disabled)),
        "Known spells remain inspectable regardless of cast or assignment restrictions");
    require(
        AbilityCatalogue.matching(entries, false, "", "", names::get).size() == entries.size(),
        "Disabling learned-only restores the complete catalogue");
    require(
        AbilityCatalogue.matching(entries, true, "fire", "", names::get)
            .equals(List.of(mastery, fire)),
        "The school filter combines with learned-only and localized name sorting");
    require(
        AbilityCatalogue.matching(entries, true, "", "  ОГНЕН  ", names::get).equals(List.of(fire)),
        "Localized search is case insensitive and trims spaces");
    require(
        AbilityCatalogue.matching(entries, false, "fire", "FIREBALL", names::get)
            .equals(List.of(unknown)),
        "Unlearned spells remain searchable by ID in the full catalogue");
    require(
        AbilityCatalogue.matching(entries, true, "fire", "FIREBALL", names::get).isEmpty(),
        "Search cannot bypass learned-only");
    require(
        AbilityCatalogue.matching(List.of(), true, "", "", names::get).isEmpty(),
        "Waiting and empty catalogues are safe");
    for (var assignable : List.of(fire, mana, cooldown, focus))
      require(
          AbilityCatalogue.assignmentReason(assignable).isEmpty(),
          "Transient casting resources do not prevent preparation");
    require(AbilityCatalogue.assignmentReason(unknown).equals("unlearned"), "Unlearned reason");
    require(AbilityCatalogue.assignmentReason(mastery).equals("mastery"), "Mastery reason");
    require(AbilityCatalogue.assignmentReason(discovery).equals("discovery"), "Discovery reason");
    require(AbilityCatalogue.assignmentReason(disabled).equals("disabled"), "Disabled reason");
    require(AbilityCatalogue.assignmentReason(null).equals("waiting"), "Stale selection reason");
    System.out.println(
        "Ability catalogue: learned-only, complete catalogue, localized search, school and preparation"
            + " restrictions passed");
  }

  private static AbilityNetwork.Entry entry(
      String id, String school, int rank, int cap, String reason) {
    return new AbilityNetwork.Entry(
        "irons_spellbooks:" + id, school, rank, cap, 10, reason.isEmpty(), reason, 0, 10);
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}

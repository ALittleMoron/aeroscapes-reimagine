package dev.aeroscapes.magic.client;

import dev.aeroscapes.magic.AbilityNetwork;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Catalogue visibility and preparation eligibility are independent of current casting resources.
 */
public final class AbilityCatalogue {
  private AbilityCatalogue() {}

  public static List<AbilityNetwork.Entry> matching(
      List<AbilityNetwork.Entry> entries,
      boolean learnedOnly,
      String school,
      String query,
      Function<String, String> name) {
    String normalized = query.toLowerCase(Locale.ROOT).trim();
    return entries.stream()
        .filter(entry -> !learnedOnly || entry.known())
        .filter(entry -> school.isEmpty() || entry.school().equals(school))
        .filter(
            entry ->
                name.apply(entry.id()).toLowerCase(Locale.ROOT).contains(normalized)
                    || entry.id().toLowerCase(Locale.ROOT).contains(normalized))
        .sorted(Comparator.comparing(entry -> name.apply(entry.id())))
        .toList();
  }

  public static String assignmentReason(AbilityNetwork.Entry entry) {
    if (entry == null) return "waiting";
    if (entry.technique()) return entry.known() ? "" : "technique_unlearned";
    if (entry.reason().equals("race")) return "race";
    if (entry.reason().equals("disabled")) return "disabled";
    if (entry.reason().equals("hex_missing")) return "hex_missing";
    if (entry.granted()) return "";
    if (entry.level() < 1) return "unlearned";
    if (entry.level() > entry.cap()) return "mastery";
    return entry.reason().equals("discovery") ? "discovery" : "";
  }
}

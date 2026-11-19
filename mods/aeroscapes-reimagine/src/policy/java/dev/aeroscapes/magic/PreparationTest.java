package dev.aeroscapes.magic;

import java.util.ArrayList;

public final class PreparationTest {
  private PreparationTest() {}

  public static void main(String[] arguments) {
    Preparation empty = Preparation.empty();
    require(
        empty.slots().size() == 8 && empty.slots().stream().allMatch(String::isEmpty),
        "Eight empty slots");
    Preparation first =
        empty.assign(0, "irons_spellbooks:firebolt").assign(1, "irons_spellbooks:heal");
    Preparation swapped = first.assign(1, "irons_spellbooks:firebolt");
    require(
        swapped.slots().get(0).equals("irons_spellbooks:heal")
            && swapped.slots().get(1).equals("irons_spellbooks:firebolt"),
        "Moving a spell swaps the occupied slots");
    require(
        first.slots().get(0).equals("irons_spellbooks:firebolt"),
        "Editing does not mutate the previous set");
    require(
        swapped.assign(1, "").slots().get(1).isEmpty(), "Clearing removes the assigned ability");
    require(
        first.assign(-1, "fake").equals(first) && first.assign(8, "fake").equals(first),
        "Invalid slot messages cannot modify preparation");
    require(
        first.select(7).selected() == 7 && first.select(8).equals(first),
        "Only actual slots can be selected");
    var source = new ArrayList<>(empty.slots());
    Preparation copied = new Preparation(source, 0);
    source.set(0, "changed");
    require(copied.slots().get(0).isEmpty(), "The preparation owns an immutable copy");
    require(
        SpellProgression.studyReason(1, 5, 3).equals("mastery"),
        "An over-level scroll is rejected, never downranked");
    require(
        SpellProgression.studyReason(3, 3, 6).equals("no_improvement"),
        "Duplicates do not consume a scroll");
    require(
        SpellProgression.studyReason(0, 1, 3).equals("unlearned"),
        "A scroll never buys a permanent spell node");
    require(
        SpellProgression.studyReason(1, 3, 3).isEmpty(),
        "An actual within-cap improvement is accepted");
    System.out.println("Preparation and spell progression policies passed");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}

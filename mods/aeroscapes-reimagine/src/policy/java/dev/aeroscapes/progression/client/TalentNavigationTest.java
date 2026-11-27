package dev.aeroscapes.progression.client;

import dev.aeroscapes.progression.ProfessionDiscipline;
import dev.aeroscapes.progression.client.TalentClient.ClassTree;
import dev.aeroscapes.progression.client.TalentScreen.Location;
import dev.aeroscapes.progression.client.TalentScreen.Page;
import dev.aeroscapes.progression.client.TalentScreen.SavedView;
import java.util.List;
import java.util.Set;
import net.puffish.skillsmod.client.data.ClientSkillScreenData;

public final class TalentNavigationTest {
  private TalentNavigationTest() {}

  public static void verify() {
    var current = new Location(Page.PROFESSION, ClassTree.RANGER, ProfessionDiscipline.BUILDING);
    try {
      TalentClient.lastView = new SavedView(current, List.of(), null, null);
      var reopened = new TalentScreen(new ClientSkillScreenData());
      require(
          reopened.location().equals(current),
          "A new atlas keeps the last section and both subsections");
      require(
          reopened.savedView().location().equals(current),
          "Closing the atlas before categories arrive keeps the saved location");
      var axe = ProfessionDiscipline.AXE_HANDLING.category();
      var warrior = ClassTree.WARRIOR.category();
      var mage = ClassTree.MAGE.category();
      var smithing = ProfessionDiscipline.SMITHING.category();
      require(
          reopened
              .location()
              .available(Set.of(axe, warrior)::contains)
              .equals(
                  new Location(Page.PROFESSION, ClassTree.WARRIOR, ProfessionDiscipline.BUILDING)),
          "A present tool branch keeps its owning subsection available");
      require(
          reopened
              .location()
              .available(Set.of(mage, smithing)::contains)
              .equals(new Location(Page.PROFESSION, ClassTree.MAGE, ProfessionDiscipline.SMITHING)),
          "A removed category falls back to an available one in the same section");
      require(
          reopened.location().available(Set.of(mage)::contains).category().equals(mage),
          "If a section has no categories, reopen the first available section");
      TalentScreen.logout(null);
      require(
          new TalentScreen(new ClientSkillScreenData()).location().category().equals(warrior),
          "A different connection starts on the default subsection");
    } finally {
      TalentClient.lastView = null;
    }

    var prior = new TreeViewport();
    prior.fit(-80, -60, 140, 100, 340, 160);
    prior.pan(26, -14);
    prior.zoom(3, 50, 13);
    var reopened = new TreeViewport();
    reopened.fit(-80, -60, 140, 100, 310, 150);
    reopened.restore(prior.position());
    require(
        reopened.position().equals(prior.position()),
        "Reopening preserves the camera pan and zoom");
  }

  private static void require(boolean condition, String reason) {
    if (!condition) throw new AssertionError(reason);
  }
}

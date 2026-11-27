package dev.aeroscapes.races;

import dev.aeroscapes.equipment.armor.ArmorProficiency;
import dev.aeroscapes.equipment.combat.BeastmasterCompanions;
import dev.aeroscapes.equipment.combat.MartialTechnique;
import dev.aeroscapes.equipment.combat.MartialTechniques;
import dev.aeroscapes.equipment.expedition.ExceptionalAccessory;
import dev.aeroscapes.magic.AbilityLoadout;
import dev.aeroscapes.magic.InnateAbility;
import dev.aeroscapes.magic.PreparedAbilities;
import dev.aeroscapes.magic.SpellKnowledge;
import dev.aeroscapes.magic.travel.AmuletStep;
import dev.aeroscapes.progression.ProfessionDiscipline;
import dev.aeroscapes.progression.TalentRefunds;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

public final class CharacterChanges {
  static final String STARTER_PERSONALLY_LEARNED = "class_starter_personally_learned";

  public record Selection(
      String kind, String target, int style, String starter, String subclassId) {
    public Selection(String kind, String target, int style, String starter) {
      this(kind, target, style, starter, "");
    }
  }

  public record Preview(
      TalentRefunds.Quote talents,
      List<String> disabled,
      List<String> wornGear,
      List<String> addedSpells,
      List<String> retiredSpells,
      @Nullable Selection selection,
      List<String> currentIdentity,
      AbilityLoadout loadout,
      List<String> equipment) {
    public Preview {
      disabled = List.copyOf(disabled);
      wornGear = List.copyOf(wornGear);
      addedSpells = List.copyOf(addedSpells);
      retiredSpells = List.copyOf(retiredSpells);
      currentIdentity = List.copyOf(currentIdentity);
      equipment = List.copyOf(equipment);
    }
  }

  private record Identity(
      Race race,
      int style,
      int tint,
      CharacterClass role,
      String starter,
      @Nullable CharacterSubclass subclass,
      Profession profession,
      String professionStarter) {

    private List<String> fingerprint() {
      return List.of(
          race.id(),
          Integer.toString(style),
          Integer.toString(tint),
          role.id(),
          starter,
          subclass == null ? "" : subclass.id(),
          profession.id(),
          professionStarter);
    }

    private Set<TalentRefunds.Node> grants() {
      String category = ClassProgression.category(role).getPath();
      var grants = new HashSet<TalentRefunds.Node>();
      grants.add(new TalentRefunds.Node(category, ClassProgression.node(role, starter)));
      String subclassTalent = SubclassProgression.talentNode(subclass);
      if (subclassTalent != null) grants.add(new TalentRefunds.Node(category, subclassTalent));
      grants.add(new TalentRefunds.Node(profession.practiceSkill(), professionStarter));
      return Set.copyOf(grants);
    }
  }

  private record Assessed(@Nullable Identity next, String problem) {}

  private static final Assessed INVALID = new Assessed(null, "invalid");
  private static final Assessed NO_SPACE = new Assessed(null, "space");

  private CharacterChanges() {}

  @Nullable
  public static Preview preview(ServerPlayer player, Selection selection) {
    var current = identity(player);
    if (current == null) return null;
    var next = assess(player, current, selection).next();
    return next == null ? null : quote(player, current, next, selection);
  }

  public static String problem(ServerPlayer player, Selection selection) {
    var current = identity(player);
    if (current == null) return INVALID.problem();
    var assessment = assess(player, current, selection);
    if (assessment.next() == null) return assessment.problem();
    return quote(player, current, assessment.next(), selection) == null ? INVALID.problem() : "";
  }

  @Nullable
  public static Preview previewReset(ServerPlayer player) {
    var current = identity(player);
    if (current == null) return null;
    var preview = quote(player, current, current, null);
    return preview != null && preview.talents().hasPaidNodes() ? preview : null;
  }

  public static boolean change(ServerPlayer player, Selection selection, Preview expected) {
    var current = identity(player);
    if (current == null) return false;
    var next = assess(player, current, selection).next();
    return next != null && apply(player, current, next, selection, expected);
  }

  public static boolean reset(ServerPlayer player, Preview expected) {
    var current = identity(player);
    return current != null
        && expected != null
        && expected.talents().hasPaidNodes()
        && apply(player, current, current, null, expected);
  }

  @Nullable
  private static Identity identity(ServerPlayer player) {
    if (!CharacterClass.creationAvailable()
        || !player.isAlive()
        || player.isSpectator()
        || RaceIdentity.grantPending(player)
        || RaceIdentity.awaitingChoice(player)) return null;
    var race = RaceIdentity.race(player);
    var role = RaceIdentity.characterClass(player);
    var profession = RaceIdentity.profession(player);
    if (race == null || role == null || profession == null) return null;
    return new Identity(
        race,
        RaceIdentity.style(player),
        RaceIdentity.tint(player),
        role,
        RaceIdentity.starter(player),
        RaceIdentity.subclass(player),
        profession,
        RaceIdentity.professionStarter(player));
  }

  private static Assessed assess(ServerPlayer player, Identity current, Selection selection) {
    if (selection.kind().equals("race")) return assessRace(player, current, selection);
    if (selection.style() != 0) return INVALID;
    return switch (selection.kind()) {
      case "class" -> assessClass(player, current, selection);
      case "subclass" -> assessSubclass(player, current, selection);
      case "profession" -> assessProfession(player, current, selection);
      default -> INVALID;
    };
  }

  private static Assessed assessRace(ServerPlayer player, Identity current, Selection selection) {
    var target = Race.byId(selection.target());
    if (target == null
        || target == current.race()
        || !target.validStyle(selection.style())
        || !selection.starter().isEmpty()
        || !selection.subclassId().isEmpty()) return INVALID;
    var body = player.getBoundingBox();
    double halfWidth =
        body.getXsize() * RaceTraits.width(target) / RaceTraits.width(current.race()) / 2;
    double height = body.getYsize() * RaceTraits.height(target) / RaceTraits.height(current.race());
    var nextBody =
        new AABB(
            player.getX() - halfWidth,
            body.minY,
            player.getZ() - halfWidth,
            player.getX() + halfWidth,
            body.minY + height,
            player.getZ() + halfWidth);
    if (!player.level().noCollision(player, nextBody.deflate(1.0e-7))) return NO_SPACE;
    return new Assessed(
        new Identity(
            target,
            selection.style(),
            current.tint(),
            current.role(),
            current.starter(),
            current.subclass(),
            current.profession(),
            current.professionStarter()),
        "");
  }

  private static Assessed assessClass(ServerPlayer player, Identity current, Selection selection) {
    var target = CharacterClass.byId(selection.target());
    if (target == null
        || target == current.role()
        || !target.id().equals(selection.target())
        || !ClassProgression.available(player, target, selection.starter())
        || !CharacterSubclass.validChoice(target, selection.subclassId())) return INVALID;
    var subclass = CharacterSubclass.byId(selection.subclassId());
    if (subclass != null && !SubclassProgression.available(player, subclass)) return INVALID;
    return new Assessed(
        new Identity(
            current.race(),
            current.style(),
            current.tint(),
            target,
            selection.starter(),
            subclass,
            current.profession(),
            current.professionStarter()),
        "");
  }

  private static Assessed assessSubclass(
      ServerPlayer player, Identity current, Selection selection) {
    var target = CharacterSubclass.byId(selection.target());
    if (!selection.starter().isEmpty()
        || !selection.subclassId().isEmpty()
        || !CharacterSubclass.validChoice(current.role(), selection.target())
        || target == current.subclass()
        || target != null && !SubclassProgression.available(player, target)) return INVALID;
    return new Assessed(
        new Identity(
            current.race(),
            current.style(),
            current.tint(),
            current.role(),
            current.starter(),
            target,
            current.profession(),
            current.professionStarter()),
        "");
  }

  private static Assessed assessProfession(
      ServerPlayer player, Identity current, Selection selection) {
    var target = Profession.byId(selection.target());
    if (target == null
        || target == current.profession()
        || !selection.subclassId().isEmpty()
        || !target.starters().contains(selection.starter())
        || !ProfessionProgression.available(player, selection.starter())) return INVALID;
    return new Assessed(
        new Identity(
            current.race(),
            current.style(),
            current.tint(),
            current.role(),
            current.starter(),
            current.subclass(),
            target,
            selection.starter()),
        "");
  }

  @Nullable
  private static Preview quote(
      ServerPlayer player, Identity current, Identity next, @Nullable Selection selection) {
    var nextGrants = selection == null ? RaceIdentity.identityGrants(player) : next.grants();
    var talents =
        TalentRefunds.preview(
                player,
                RaceIdentity.identityGrants(player),
                RaceIdentity.identityGrantOverlaps(player),
                nextGrants)
            .orElse(null);
    if (talents == null) return null;
    var currentSpells = ClassProgression.starterSpellIds(current.role(), current.subclass());
    var nextSpells = ClassProgression.starterSpellIds(next.role(), next.subclass());
    var loadout = PreparedAbilities.loadout(player);
    return new Preview(
        talents,
        disabled(player, current, next, talents, Set.copyOf(nextSpells), loadout),
        wornGear(player, talents),
        nextSpells.stream().filter(id -> !currentSpells.contains(id)).sorted().toList(),
        currentSpells.stream().filter(id -> !nextSpells.contains(id)).sorted().toList(),
        selection,
        current.fingerprint(),
        loadout,
        ArmorProficiency.SLOTS.stream()
            .map(
                slot -> player.getItemBySlot(slot).saveOptional(player.registryAccess()).toString())
            .toList());
  }

  private static List<String> disabled(
      ServerPlayer player,
      Identity current,
      Identity next,
      TalentRefunds.Quote talents,
      Set<String> nextStarterSpells,
      AbilityLoadout loadout) {
    var currentMagic = SpellKnowledge.unlocked(player);
    var nextMagic = talents.afterNodes("magic");
    var disabled = new ArrayList<String>();
    for (String id : loadout.assignedIds()) {
      var innate = InnateAbility.byId(id);
      var technique = MartialTechnique.byId(id);
      var accessory = ExceptionalAccessory.byId(id);
      var spell = SpellKnowledge.spell(id);
      if (innate != null && innate.race == current.race() && innate.race != next.race()
          || technique != null
              && MartialTechniques.learned(player, technique)
              && !hasTalentAfter(talents, ResourceLocation.parse(id).getPath())
          || id.equals(AnimalLure.ID)
              && AnimalLure.learned(player)
              && !talents.afterNodes(ProfessionDiscipline.FARMING.skill()).contains("animal_lure")
          || ((accessory != null
                      && accessory != ExceptionalAccessory.BREATH
                      && !accessory.equipped(player).isEmpty())
                  || id.equals(AmuletStep.ID) && AmuletStep.available(player))
              && SpellKnowledge.hasAccess(currentMagic)
              && !SpellKnowledge.hasAccess(nextMagic)
          || spell != SpellRegistry.none()
              && SpellKnowledge.knowledgeReason(player, spell, currentMagic).isEmpty()
              && !SpellKnowledge.knowledgeReason(player, spell, nextMagic, nextStarterSpells)
                  .isEmpty()) disabled.add(id);
    }
    return disabled;
  }

  private static List<String> wornGear(ServerPlayer player, TalentRefunds.Quote talents) {
    var affected = new ArrayList<String>();
    for (var slot : ArmorProficiency.SLOTS) {
      var stack = player.getItemBySlot(slot);
      String talent = ArmorProficiency.requiredTalent(stack);
      if (talent != null
          && !ArmorProficiency.undertrained(player, stack)
          && !hasTalentAfter(talents, talent))
        affected.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }
    return affected;
  }

  private static boolean hasTalentAfter(TalentRefunds.Quote talents, String node) {
    var pool = ClassProgression.categoryForTalent(node);
    return pool != null && talents.afterNodes(pool.getPath()).contains(node);
  }

  private static boolean apply(
      ServerPlayer player,
      Identity current,
      Identity next,
      @Nullable Selection selection,
      Preview expected) {
    if (expected == null || !expected.equals(quote(player, current, next, selection))) return false;
    var currentGrants = RaceIdentity.identityGrants(player);
    var nextGrants = selection == null ? currentGrants : next.grants();
    if (!TalentRefunds.apply(
        player,
        currentGrants,
        RaceIdentity.identityGrantOverlaps(player),
        nextGrants,
        expected.talents(),
        () ->
            RaceIdentity.finishReset(
                player,
                next.race(),
                next.style(),
                next.role(),
                next.starter(),
                next.subclass(),
                next.profession(),
                next.professionStarter()))) return false;
    BeastmasterCompanions.reconcile(player);
    PreparedAbilities.reconcileAfterTalentReset(player);
    return true;
  }
}

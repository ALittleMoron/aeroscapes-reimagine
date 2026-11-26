package dev.aeroscapes.progression;

import dev.aeroscapes.equipment.combat.MartialTechniques;
import dev.aeroscapes.equipment.combat.WeaponProficiency;
import dev.aeroscapes.equipment.workshop.WorkshopManufacture;
import dev.aeroscapes.magic.MagicPractice;
import dev.aeroscapes.races.CharacterSubclass;
import dev.aeroscapes.races.ProfessionEffects;
import dev.aeroscapes.races.RaceIdentity;
import dev.aeroscapes.races.RaceTraits;
import harmonised.pmmo.api.APIUtils;
import harmonised.pmmo.util.Reference;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.player.AnvilRepairEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEnchantItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Rates for personal actions whose native awards use incomplete or unbounded inputs. */
public final class PracticeActions {
  private PracticeActions() {}

  static void register() {
    NeoForge.EVENT_BUS.addListener(PracticeActions::craft);
    NeoForge.EVENT_BUS.addListener(PracticeActions::repair);
    NeoForge.EVENT_BUS.addListener(PracticeActions::enchant);
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, PracticeActions::breed);
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, PracticeActions::fish);
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, PracticeActions::shield);
    NeoForge.EVENT_BUS.addListener(PracticeActions::damage);
  }

  public static boolean personal(Entity actor) {
    return actor instanceof ServerPlayer player
        && !(player instanceof FakePlayer)
        && !player.isCreative()
        && !player.isSpectator();
  }

  private static void award(Entity actor, String skill, long amount) {
    if (personal(actor) && amount > 0) APIUtils.addXp(skill, (ServerPlayer) actor, amount);
  }

  private static void awardCombat(Entity actor, long amount, boolean shieldBlock) {
    if (!personal(actor) || amount <= 0) return;
    var player = (ServerPlayer) actor;
    var subclass = RaceIdentity.subclass(player);
    boolean affinity =
        shieldBlock
            ? subclass == CharacterSubclass.DEFENDER
            : subclass == CharacterSubclass.BERSERKER
                && MartialTechniques.isHeavyWeapon(WeaponProficiency.attackWeapon(player));
    if (affinity)
      RaceTraits.withSubclassPractice(player, () -> APIUtils.addXp("combat", player, amount));
    else APIUtils.addXp("combat", player, amount);
  }

  private static void craft(PlayerEvent.ItemCraftedEvent event) {
    ItemStack result = event.getCrafting();
    if (!personal(event.getEntity()) || result.isEmpty()) return;
    var grid = event.getInventory();
    // The forging anvil already credited the smith when it produced the output.
    if ("net.stirdrem.overgeared.screen.AbstractSmithingAnvilMenu$1"
        .equals(grid.getClass().getName())) return;
    int ingredients = 0;
    boolean homogeneous = true;
    boolean hasEquipmentInput = false;
    ItemStack first = ItemStack.EMPTY;
    for (int slot = 0; slot < grid.getContainerSize(); slot++) {
      ItemStack input = grid.getItem(slot);
      if (input.isEmpty()) continue;
      if (first.isEmpty()) first = input;
      ingredients++;
      homogeneous &= input.is(first.getItem());
      hasEquipmentInput |= input.is(result.getItem());
    }
    // Packing and unpacking a stack of the same material is a reversible inventory operation.
    if (ingredients == 0 || homogeneous && storageConversion(first, result, ingredients)) return;
    if (ModList.get().isLoaded("overgeared")
        && WorkshopManufacture.finishedEquipment(result)
        && !hasEquipmentInput) {
      awardWorkshop(event.getEntity());
      return;
    }
    boolean food = result.has(DataComponents.FOOD) || ProfessionEffects.isFeast(result);
    award(event.getEntity(), food ? "cooking" : "crafting", Math.min(ingredients, 9));
  }

  private static boolean storageConversion(ItemStack input, ItemStack result, int slots) {
    if (slots == 1 && result.getCount() > 1)
      return input.is(Tags.Items.STORAGE_BLOCKS)
          || input.is(Tags.Items.INGOTS) && result.is(Tags.Items.NUGGETS);
    return slots > 1
        && result.getCount() == 1
        && (result.is(Tags.Items.STORAGE_BLOCKS)
            || input.is(Tags.Items.NUGGETS) && result.is(Tags.Items.INGOTS));
  }

  public static void takeSmelting(Entity actor, ItemStack result, int taken) {
    if (!result.isEmpty())
      award(actor, result.has(DataComponents.FOOD) ? "cooking" : "smithing", 2L * taken);
  }

  public static void awardWorkshop(Entity actor) {
    award(actor, "smithing", 20);
  }

  private static void repair(AnvilRepairEvent event) {
    ItemStack before = event.getLeft();
    ItemStack after = event.getOutput();
    if (!event.getRight().isEmpty() && before.isDamageableItem() && after.is(before.getItem())) {
      int restored = before.getDamageValue() - after.getDamageValue();
      if (restored > 0)
        award(
            event.getEntity(), "smithing", Math.min(8, 1 + 8L * restored / before.getMaxDamage()));
    }
  }

  private static void enchant(PlayerEnchantItemEvent event) {
    if (!personal(event.getEntity())) return;
    int rank = 0;
    for (var enchantment : event.getEnchantments()) rank = Math.max(rank, enchantment.level);
    if (rank > 0) MagicPractice.award((ServerPlayer) event.getEntity(), Math.min(20, 4L * rank));
  }

  private static void breed(BabyEntitySpawnEvent event) {
    if (event.getChild() != null) award(event.getCausedByPlayer(), "farming", 4);
  }

  private static void fish(ItemFishedEvent event) {
    if (event.getDrops().stream().anyMatch(stack -> !stack.isEmpty()))
      award(event.getEntity(), "farming", 6);
  }

  private static void shield(LivingShieldBlockEvent event) {
    if (event.getBlocked()
        && event.getBlockedDamage() > 0
        && event.getDamageSource().getEntity() instanceof LivingEntity)
      awardCombat(event.getEntity(), Math.min(4, (long) Math.ceil(event.getBlockedDamage())), true);
  }

  public static void harvest(Entity actor) {
    award(actor, "farming", 3);
  }

  public static boolean crop(BlockState state) {
    return state.is(Reference.CROPS)
        || state.getBlock() instanceof CropBlock
        || state.is(Blocks.CAVE_VINES)
        || state.is(Blocks.CAVE_VINES_PLANT);
  }

  private static void damage(LivingDamageEvent.Post event) {
    var source = event.getSource();
    if (!personal(source.getEntity())
        || event.getNewDamage() <= 0
        || event.getEntity().getType().is(Reference.NO_XP_DAMAGE_DEALT)) return;
    long xp = Math.min(12, (long) Math.ceil(event.getNewDamage()));
    if (source.is(DamageTypes.PLAYER_ATTACK)) awardCombat(source.getEntity(), xp, false);
    else if (source.getDirectEntity() instanceof AbstractArrow || source.is(Reference.FROM_GUN))
      award(source.getEntity(), "archery", xp);
  }
}

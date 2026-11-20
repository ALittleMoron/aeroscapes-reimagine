package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import dev.aeroscapes.races.ChangeItems;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeEventHandler;
import net.neoforged.neoforge.common.loot.AddTableLootModifier;
import net.neoforged.neoforge.common.loot.LootModifierManager;

final class ChangeSuppliesRegression {
  private ChangeSuppliesRegression() {}

  static void run(ServerLevel level) {
    var recipeInput =
        CraftingInput.of(
            2,
            2,
            List.of(
                new ItemStack(Items.STRING),
                new ItemStack(Items.ENDER_PEARL),
                new ItemStack(Items.DIAMOND),
                new ItemStack(Items.STRING)));
    var recipe =
        level
            .getRecipeManager()
            .getRecipeFor(RecipeType.CRAFTING, recipeInput, level)
            .orElseThrow();
    var result = recipe.value().assemble(recipeInput, level.registryAccess());
    if (!result.is(ChangeItems.KNOTTED_THREAD.get())
        || result.getCount() != 1
        || recipe.value().getRemainingItems(recipeInput).stream()
            .anyMatch(stack -> !stack.isEmpty()))
      throw new IllegalStateException("Knotted thread crafting output or ingredients changed");
    var params =
        new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(level.getSharedSpawnPos()))
            .create(LootContextParamSets.CHEST);
    var random = RandomSource.create(2419783L);
    var modifier = registeredSealModifier();
    for (String table :
        List.of(
            "abandoned_mineshaft", "desert_pyramid", "shipwreck_treasure", "shipwreck_supply")) {
      var key =
          ResourceKey.create(
              Registries.LOOT_TABLE, ResourceLocation.withDefaultNamespace("chests/" + table));
      var loot = level.getServer().reloadableRegistries().getLootTable(key);
      if (loot == LootTable.EMPTY)
        throw new IllegalStateException("Missing table " + key.location());
      if (!key.location().equals(loot.getLootTableId()))
        throw new IllegalStateException("Incorrect queried table ID for " + key.location());
      int seals = 0;
      for (int attempt = 0; attempt < 512; attempt++) {
        // The mineshaft's other modifiers can generate a full terrain preview for a treasure map.
        var context =
            new LootContext.Builder(params)
                .withOptionalRandomSeed(random.nextLong())
                .withQueriedLootTableId(loot.getLootTableId())
                .create(Optional.empty());
        int count =
            modifier.apply(new ObjectArrayList<>(), context).stream()
                .filter(stack -> stack.is(ChangeItems.IDENTITY_SEAL.get()))
                .mapToInt(ItemStack::getCount)
                .sum();
        if (count > 1) throw new IllegalStateException("Duplicate seal roll in " + table);
        seals += count;
      }
      if (table.equals("shipwreck_supply") ? seals != 0 : seals < 6 || seals > 42)
        throw new IllegalStateException("Identity seals in 512 " + table + " chests: " + seals);
      LogUtils.getLogger().info("Identity seals in 512 {} modifier rolls: {}", table, seals);
      if (table.startsWith("shipwreck_"))
        verifyShipwreckChest(loot, params, table.equals("shipwreck_treasure"));
    }
  }

  private static AddTableLootModifier registeredSealModifier() {
    LootModifierManager manager;
    try {
      // NeoForge 21.1.244 exposes native registered modifiers but keeps their manager
      // package-private.
      var getter = NeoForgeEventHandler.class.getDeclaredMethod("getLootModifierManager");
      getter.setAccessible(true);
      manager = (LootModifierManager) getter.invoke(null);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException(
          "Cannot inspect NeoForge's registered loot modifiers", exception);
    }
    var target = ResourceLocation.parse("aeroscapes_reimagine:chests/identity_seal");
    var matches =
        manager.getAllLootMods().stream()
            .filter(AddTableLootModifier.class::isInstance)
            .map(AddTableLootModifier.class::cast)
            .filter(modifier -> modifier.table().location().equals(target))
            .toList();
    if (matches.size() != 1)
      throw new IllegalStateException(
          "Expected one registered seal modifier, found " + matches.size());
    return matches.getFirst();
  }

  private static void verifyShipwreckChest(LootTable loot, LootParams params, boolean eligible) {
    var random = RandomSource.create(125437L);
    for (int attempt = 0; attempt < (eligible ? 256 : 1); attempt++) {
      var chest = new SimpleContainer(27);
      long seed = random.nextLong();
      loot.fill(chest, params, seed);
      int count = chest.countItem(ChangeItems.IDENTITY_SEAL.get());
      if (count > 1 || (!eligible && count != 0))
        throw new IllegalStateException("Incorrect seal count in " + loot.getLootTableId());
      if (!eligible || count == 1) {
        LogUtils.getLogger()
            .info("Full chest {} seed {} yielded {} seal(s)", loot.getLootTableId(), seed, count);
        return;
      }
    }
    throw new IllegalStateException("No seal in 256 full shipwreck treasure chests");
  }
}

package dev.aeroscapes.regression;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.InsertableLinkedOpenCustomHashSet;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/** Actual loaded-data and event boundaries, including old stacks and both hands. */
final class RemovedHookRegression {
  private RemovedHookRegression() {}

  static void verify(ServerLevel level) {
    ResourceLocation id =
        ResourceLocation.fromNamespaceAndPath("crittersandcompanions", "grappling_hook");
    ItemStack removed = stack(id),
        kept = stack(ResourceLocation.parse("yo_hooks:iron_grappling_hook"));
    ItemStack silk = stack(ResourceLocation.parse("crittersandcompanions:silk"));
    require(
        level.getServer().getRecipeManager().byKey(id).isEmpty(),
        "Removed hook has no loaded crafting recipe");
    require(
        level
                .getServer()
                .getAdvancements()
                .get(ResourceLocation.parse("crittersandcompanions:recipes/tools/grappling_hook"))
            == null,
        "Removed recipe advancement has no dangling reward");
    require(
        removed.is(
            TagKey.create(Registries.ITEM, ResourceLocation.parse("c:hidden_from_recipe_viewers"))),
        "Removed hook is hidden from recipe viewers");
    var player =
        new FakePlayer(
            level,
            new GameProfile(
                UUID.fromString("6ef517fe-818a-4a74-bae0-bd5c46ff3ea5"), "RemovedHookProbe"));
    for (InteractionHand hand : InteractionHand.values()) {
      ItemStack copy = removed.copy();
      player.setItemInHand(hand, copy);
      require(
          CommonHooks.onItemRightClick(player, hand) == InteractionResult.FAIL,
          "Existing hook is blocked in " + hand);
      require(ItemStack.matches(copy, removed), "Blocking use preserves the stored stack");
      for (ItemStack allowed : List.of(kept, silk)) {
        player.setItemInHand(hand, allowed.copy());
        require(
            CommonHooks.onItemRightClick(player, hand) == null,
            "Only the selected companion hook is blocked");
      }
    }
    var parent = new InsertableLinkedOpenCustomHashSet<ItemStack>(ItemStackLinkedSet.TYPE_AND_TAG);
    var search = new InsertableLinkedOpenCustomHashSet<ItemStack>(ItemStackLinkedSet.TYPE_AND_TAG);
    for (ItemStack item : List.of(removed, kept, silk)) {
      parent.add(item.copy());
      search.add(item.copy());
    }
    var key = CreativeModeTabs.TOOLS_AND_UTILITIES;
    var parameters =
        new CreativeModeTab.ItemDisplayParameters(
            level.enabledFeatures(), true, level.registryAccess());
    var event =
        new BuildCreativeModeTabContentsEvent(
            BuiltInRegistries.CREATIVE_MODE_TAB.getOrThrow(key), key, parameters, parent, search);
    ModList.get()
        .getModContainerById("aeroscapes_reimagine")
        .orElseThrow()
        .getEventBus()
        .post(event);
    for (var entries : List.of(parent, search)) {
      require(!entries.contains(removed), "Removed hook is absent from creative parent and search");
      require(
          entries.contains(kept) && entries.contains(silk),
          "Other hooks and companion materials remain available");
    }
  }

  private static ItemStack stack(ResourceLocation id) {
    require(BuiltInRegistries.ITEM.containsKey(id), "Expected pinned item: " + id);
    return new ItemStack(BuiltInRegistries.ITEM.get(id));
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}

package dev.aeroscapes.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.aeroscapes.enchanting.LegacyEnchantingTables.LockedTable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(EnchantingTableBlock.class)
public abstract class EnchantingTableMenuLockMixin {
  @WrapMethod(method = "getMenuProvider")
  @SuppressWarnings("PMD.UnusedPrivateMethod") // MixinExtras wraps menu-provider lookup.
  private MenuProvider aeroscapes$protectLockedTable(
      BlockState state, Level level, BlockPos position, Operation<MenuProvider> original) {
    MenuProvider provider = original.call(state, level, position);
    if (provider == null || !(level.getBlockEntity(position) instanceof LockedTable table)) {
      return provider;
    }

    var lock = table.aeroscapes$lockCode();
    if (lock.key().isEmpty()) return provider;

    var title = provider.getDisplayName();
    return new SimpleMenuProvider(
        (id, inventory, player) ->
            BaseContainerBlockEntity.canUnlock(player, lock, title)
                ? provider.createMenu(id, inventory, player)
                : null,
        title);
  }
}

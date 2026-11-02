package dev.aeroscapes.travel;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.blay09.mods.waystones.api.WaystoneOrigin;
import net.blay09.mods.waystones.block.WaystoneBlock;
import net.blay09.mods.waystones.block.WaystoneBlockBase;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/** Applies to loot generation itself, including mechanical drills collecting drops directly. */
public final class NaturalWaystoneDrops extends LootModifier {
  public static final MapCodec<NaturalWaystoneDrops> CODEC =
      RecordCodecBuilder.mapCodec(
          instance -> codecStart(instance).apply(instance, NaturalWaystoneDrops::new));

  public NaturalWaystoneDrops(LootItemCondition[] conditions) {
    super(conditions);
  }

  @Override
  protected ObjectArrayList<ItemStack> doApply(
      ObjectArrayList<ItemStack> loot, LootContext context) {
    var state = context.getParamOrNull(LootContextParams.BLOCK_STATE);
    if (state != null
        && state.getBlock() instanceof WaystoneBlock
        && state.getValue(WaystoneBlockBase.ORIGIN) != WaystoneOrigin.PLAYER) {
      loot.removeIf(stack -> stack.is(state.getBlock().asItem()));
    }
    return loot;
  }

  @Override
  public MapCodec<? extends IGlobalLootModifier> codec() {
    return CODEC;
  }
}

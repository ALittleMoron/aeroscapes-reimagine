package dev.aeroscapes.weathering;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.ordana.immersive_weathering.blocks.snowy.SnowyBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The full Create scoria brick receives Immersive Weathering's native snow lifecycle. */
public final class SnowyScoriaBlock extends SnowyBlock {
  private static final String NAME = "snowy_cut_scoria_bricks";
  private static final ResourceLocation SCORIA = ResourceLocation.parse("create:cut_scoria_bricks");
  private static final DeferredRegister.Blocks BLOCKS =
      DeferredRegister.createBlocks("aeroscapes_reimagine");
  private static final DeferredRegister.Items ITEMS =
      DeferredRegister.createItems("aeroscapes_reimagine");
  private static final DeferredBlock<SnowyScoriaBlock> BLOCK =
      BLOCKS.registerBlock(
          NAME,
          SnowyScoriaBlock::new,
          // Create's pinned cut scoria bricks take their properties from brown blackstone.
          Properties.ofFullCopy(Blocks.BLACKSTONE).mapColor(MapColor.COLOR_BROWN).randomTicks());

  static {
    ITEMS.registerSimpleBlockItem(NAME, BLOCK);
  }

  private SnowyScoriaBlock(Properties properties) {
    super(properties);
  }

  public static void register(IEventBus bus) {
    BLOCKS.register(bus);
    ITEMS.register(bus);
  }

  public static BiMap<Block, Block> extendConversions(BiMap<Block, Block> original) {
    if (!BLOCK.isBound()) return original;
    return BuiltInRegistries.BLOCK
        .getOptional(SCORIA)
        .<BiMap<Block, Block>>map(
            scoria ->
                ImmutableBiMap.<Block, Block>builder()
                    .putAll(original)
                    .put(scoria, BLOCK.get())
                    .buildOrThrow())
        .orElse(original);
  }

  @Override
  protected ItemInteractionResult useItemOn(
      ItemStack stack,
      BlockState state,
      Level level,
      BlockPos position,
      Player player,
      InteractionHand hand,
      BlockHitResult hit) {
    return interactWithPlayer(state, level, position, player, hand, hit)
        ? ItemInteractionResult.sidedSuccess(level.isClientSide())
        : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
  }

  @Override
  protected InteractionResult useWithoutItem(
      BlockState state, Level level, BlockPos position, Player player, BlockHitResult hit) {
    // The upstream fallback reads getUsedItemHand(), which is not necessarily the clicked hand.
    return InteractionResult.PASS;
  }
}

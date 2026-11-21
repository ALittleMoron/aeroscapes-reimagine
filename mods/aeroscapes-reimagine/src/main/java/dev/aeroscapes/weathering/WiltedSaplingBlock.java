package dev.aeroscapes.weathering;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class WiltedSaplingBlock extends BushBlock implements EntityBlock {
  private static final MapCodec<WiltedSaplingBlock> CODEC = simpleCodec(WiltedSaplingBlock::new);
  private static final DeferredRegister.Blocks BLOCKS =
      DeferredRegister.createBlocks("aeroscapes_reimagine");
  private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
      DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "aeroscapes_reimagine");
  private static final DeferredBlock<WiltedSaplingBlock> BLOCK =
      BLOCKS.registerBlock(
          "wilted_sapling",
          WiltedSaplingBlock::new,
          Properties.of()
              .mapColor(MapColor.WOOD)
              .noCollission()
              .instabreak()
              .sound(SoundType.GRASS)
              .pushReaction(PushReaction.DESTROY)
              .noLootTable());
  private static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SaplingMemory>> MEMORY =
      BLOCK_ENTITIES.register(
          "wilted_sapling",
          () -> BlockEntityType.Builder.of(SaplingMemory::new, BLOCK.get()).build(null));

  private WiltedSaplingBlock(Properties properties) {
    super(properties);
  }

  public static void register(IEventBus bus) {
    BLOCKS.register(bus);
    BLOCK_ENTITIES.register(bus);
  }

  public static boolean wilt(Level level, BlockPos position, BlockState original) {
    if (original.hasBlockEntity()
        || !original.getFluidState().isEmpty()
        || level.getBlockState(position) != original
        || !level.setBlockAndUpdate(position, BLOCK.get().defaultBlockState())) return false;
    if (level.getBlockEntity(position) instanceof SaplingMemory memory) {
      memory.original = original;
      memory.setChanged();
      return true;
    }
    level.setBlockAndUpdate(position, original);
    return false;
  }

  @Override
  protected MapCodec<WiltedSaplingBlock> codec() {
    return CODEC;
  }

  @Override
  public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
    return new SaplingMemory(position, state);
  }

  @Override
  protected VoxelShape getShape(
      BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
    return Blocks.DEAD_BUSH.defaultBlockState().getShape(level, position, context);
  }

  @Override
  protected boolean canSurvive(BlockState state, LevelReader level, BlockPos position) {
    return !(level.getBlockEntity(position) instanceof SaplingMemory memory)
        || memory.original.isAir()
        || memory.original.canSurvive(level, position);
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
    if (!stack.is(Items.POTION)
        || !stack
            .getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY)
            .is(Potions.WATER)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    if (level.isClientSide()) return ItemInteractionResult.SUCCESS;
    if (!(level.getBlockEntity(position) instanceof SaplingMemory memory)
        || memory.original.isAir()
        || !memory.original.canSurvive(level, position)
        || !level.setBlockAndUpdate(position, memory.original)) return ItemInteractionResult.FAIL;
    if (!player.hasInfiniteMaterials()) {
      player.setItemInHand(
          hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.GLASS_BOTTLE)));
    }
    level.playSound(null, position, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1, 1);
    level.gameEvent(player, GameEvent.BLOCK_CHANGE, position);
    return ItemInteractionResult.CONSUME;
  }

  private static final class SaplingMemory extends BlockEntity {
    private BlockState original = Blocks.AIR.defaultBlockState();

    private SaplingMemory(BlockPos position, BlockState state) {
      super(MEMORY.get(), position, state);
    }

    @Override
    protected void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
      super.saveAdditional(data, registries);
      if (!original.isAir()) data.put("OriginalSapling", NbtUtils.writeBlockState(original));
    }

    @Override
    protected void loadAdditional(CompoundTag data, HolderLookup.Provider registries) {
      super.loadAdditional(data, registries);
      original =
          NbtUtils.readBlockState(
              registries.lookupOrThrow(Registries.BLOCK), data.getCompound("OriginalSapling"));
    }
  }
}

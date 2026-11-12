package dev.aeroscapes.engineering;

import com.jesz.createdieselgenerators.world.OilChunksSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/** A mineable, non-dropping target retains the stone replaced by world generation. */
public final class OilRockBlock extends Block {
  public static final EnumProperty<Host> HOST = EnumProperty.create("host", Host.class);

  public OilRockBlock(Properties properties) {
    super(properties);
    registerDefaultState(stateDefinition.any().setValue(HOST, Host.STONE));
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(HOST);
  }

  @Override
  protected float getDestroyProgress(
      BlockState state, Player player, BlockGetter level, BlockPos position) {
    return state.getValue(HOST).nativeState().getDestroyProgress(player, level, position);
  }

  @Override
  protected void onRemove(
      BlockState state, Level level, BlockPos position, BlockState replacement, boolean moving) {
    super.onRemove(state, level, position, replacement, moving);
    if (replacement.is(this)
        || !(level instanceof ServerLevel server)
        || server.dimension() != Level.OVERWORLD
        || !ModList.get().isLoaded("createdieselgenerators")) return;
    ChunkPos chunk = new ChunkPos(position);
    if (OilFields.findTarget(server, chunk) == null)
      OilChunksSavedData.setChunkOilAmount(server, chunk, 0);
  }

  public enum Host implements StringRepresentable {
    STONE("minecraft:stone"),
    GRANITE("minecraft:granite"),
    DIORITE("minecraft:diorite"),
    ANDESITE("minecraft:andesite"),
    DEEPSLATE("minecraft:deepslate", true),
    TUFF("minecraft:tuff"),
    BLACKSTONE("minecraft:blackstone", true),
    JASPER("quark:jasper"),
    LIMESTONE("quark:limestone"),
    SHALE("quark:shale"),
    TRAVERTINE("natures_spirit:travertine"),
    CHERT("natures_spirit:chert", true);

    private final ResourceLocation source;
    private final boolean distinctTop;

    Host(String source) {
      this(source, false);
    }

    Host(String source, boolean distinctTop) {
      this.source = ResourceLocation.parse(source);
      this.distinctTop = distinctTop;
    }

    @Override
    public String getSerializedName() {
      return source.getPath();
    }

    public boolean hasDistinctTop() {
      return distinctTop;
    }

    public ResourceLocation texture(boolean top) {
      return source.withPath("block/" + source.getPath() + (top && distinctTop ? "_top" : ""));
    }

    public BlockState nativeState() {
      return BuiltInRegistries.BLOCK.get(source).defaultBlockState();
    }

    public static @Nullable Host of(BlockState state) {
      ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
      for (Host host : values()) if (host.source.equals(key)) return host;
      return null;
    }
  }
}

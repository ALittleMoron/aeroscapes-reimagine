package dev.aeroscapes.equipment.field;

import dev.aeroscapes.equipment.ReforgeProfiles;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class ExtendoReforges {
  private static final String AXIS = "aeroscapes_reimagine:extendo_axis";
  private static final ResourceLocation EXTENDO =
      ResourceLocation.fromNamespaceAndPath("create", "extendo_grip");
  private static final Map<ServerPlayer, Placement> LAST_PLACEMENT = new WeakHashMap<>();

  private ExtendoReforges() {}

  public static int cost(ServerPlayer player, BlockState placed) {
    ItemStack grip = grip(player);
    String profile = ReforgeProfiles.path(grip);
    if (!profile.startsWith("extendo_")) return 1;
    long now = player.level().getGameTime();
    var prior = LAST_PLACEMENT.put(player, new Placement(new WeakReference<>(grip), now, placed));
    boolean repeated =
        prior != null
            && prior.grip().get() == grip
            && prior.tick() + 40 >= now
            && prior.state().is(placed.getBlock());
    if (keepsAxis(profile)
        && player.isShiftKeyDown()
        && placed.getBlock() instanceof RotatedPillarBlock) {
      CustomData.update(
          DataComponents.CUSTOM_DATA,
          grip,
          tag -> tag.putString(AXIS, placed.getValue(RotatedPillarBlock.AXIS).getName()));
    }
    int chance =
        switch (profile) {
          case "extendo_poor_repetition" -> repeated ? -25 : 0;
          case "extendo_fine_economy" -> 10;
          case "extendo_exceptional_axis" -> repeated ? 20 : 0;
          case "extendo_epic_axis", "extendo_legendary_axis" -> repeated ? 30 : 15;
          default -> 0;
        };
    if (chance == 0 || player.getRandom().nextInt(100) >= Math.abs(chance)) return 1;
    return chance > 0 ? 0 : 2;
  }

  public static BlockState placement(BlockPlaceContext context, BlockState state) {
    Player player = context.getPlayer();
    if (state == null
        || player == null
        || player.isShiftKeyDown()
        || !(state.getBlock() instanceof RotatedPillarBlock)
        || context
            .getItemInHand()
            .getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY)
            .properties()
            .containsKey("axis")) return state;
    ItemStack grip = grip(player);
    String profile = ReforgeProfiles.path(grip);
    if (!keepsAxis(profile)) return state;
    var axis =
        Direction.Axis.byName(
            grip.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag()
                .getString(AXIS));
    if (axis == null) return state;
    if (!"extendo_legendary_axis".equals(profile)
        && (!player.onGround() || context.getClickedFace().getAxis().isVertical())) return state;
    return state.setValue(RotatedPillarBlock.AXIS, axis);
  }

  private static boolean keepsAxis(String profile) {
    return switch (profile) {
      case "extendo_exceptional_axis", "extendo_epic_axis", "extendo_legendary_axis" -> true;
      default -> false;
    };
  }

  private static ItemStack grip(Player player) {
    ItemStack main = player.getMainHandItem();
    return EXTENDO.equals(BuiltInRegistries.ITEM.getKey(main.getItem()))
        ? main
        : player.getOffhandItem();
  }

  private record Placement(WeakReference<ItemStack> grip, long tick, BlockState state) {}
}

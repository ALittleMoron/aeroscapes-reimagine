package dev.aeroscapes.worldgen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.RedStoneOreBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Material variants for ore attempts whose actual natural target is blackstone. */
public final class BlackstoneOres {
  public static final List<String> MATERIALS =
      List.of(
          "coal",
          "copper",
          "iron",
          "gold",
          "lapis",
          "redstone",
          "diamond",
          "emerald",
          "zinc",
          "palladium");

  private static final DeferredRegister.Blocks BLOCKS =
      DeferredRegister.createBlocks("aeroscapes_reimagine");
  private static final DeferredRegister.Items ITEMS =
      DeferredRegister.createItems("aeroscapes_reimagine");
  private static final Map<ResourceLocation, Supplier<? extends Block>> VARIANTS = variants();

  private BlackstoneOres() {}

  public static void register(IEventBus bus) {
    BLOCKS.register(bus);
    ITEMS.register(bus);
  }

  public static BlockState adapt(BlockState target, BlockState ore) {
    if (!target.is(Blocks.BLACKSTONE)) return ore;
    Supplier<? extends Block> variant =
        VARIANTS.get(BuiltInRegistries.BLOCK.getKey(ore.getBlock()));
    if (variant == null) return ore;
    BlockState result = variant.get().defaultBlockState();
    return ore.hasProperty(RedStoneOreBlock.LIT)
        ? result.setValue(RedStoneOreBlock.LIT, ore.getValue(RedStoneOreBlock.LIT))
        : result;
  }

  private static Map<ResourceLocation, Supplier<? extends Block>> variants() {
    Map<ResourceLocation, Supplier<? extends Block>> result = new HashMap<>();
    for (String material : MATERIALS) {
      Block original =
          switch (material) {
            case "coal" -> Blocks.DEEPSLATE_COAL_ORE;
            case "copper" -> Blocks.DEEPSLATE_COPPER_ORE;
            case "iron", "zinc", "palladium" -> Blocks.DEEPSLATE_IRON_ORE;
            case "gold" -> Blocks.DEEPSLATE_GOLD_ORE;
            case "lapis" -> Blocks.DEEPSLATE_LAPIS_ORE;
            case "redstone" -> Blocks.DEEPSLATE_REDSTONE_ORE;
            case "diamond" -> Blocks.DEEPSLATE_DIAMOND_ORE;
            case "emerald" -> Blocks.DEEPSLATE_EMERALD_ORE;
            default -> throw new IllegalStateException("Unknown blackstone ore " + material);
          };
      int minimum =
          switch (material) {
            case "lapis" -> 2;
            case "diamond", "emerald" -> 3;
            default -> 0;
          };
      int maximum =
          switch (material) {
            case "coal" -> 2;
            case "lapis" -> 5;
            case "diamond", "emerald" -> 7;
            default -> 0;
          };
      String name = "blackstone_" + material + "_ore";
      var registered =
          BLOCKS.register(
              name,
              () -> {
                var properties =
                    material.equals("palladium")
                        ? BlockBehaviour.Properties.of()
                            .mapColor(MapColor.DEEPSLATE)
                            .strength(4.5F, 3.0F)
                            .sound(SoundType.DEEPSLATE)
                        : BlockBehaviour.Properties.ofFullCopy(original);
                return material.equals("redstone")
                    ? new RedStoneOreBlock(properties)
                    : new DropExperienceBlock(
                        maximum == 0 ? ConstantInt.of(0) : UniformInt.of(minimum, maximum),
                        properties);
              });
      ITEMS.registerSimpleBlockItem(name, registered);
      String namespace = sourceNamespace(material);
      for (String prefix : List.of("", "deepslate_"))
        result.put(
            ResourceLocation.fromNamespaceAndPath(namespace, prefix + material + "_ore"),
            registered);
    }
    return Map.copyOf(result);
  }

  public static String sourceNamespace(String material) {
    return switch (material) {
      case "zinc" -> "create";
      case "palladium" -> "galosphere";
      default -> "minecraft";
    };
  }
}

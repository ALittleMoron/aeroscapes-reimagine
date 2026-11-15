package dev.aeroscapes.regression;

import com.agricraft.agricraft.api.AgriApi;
import com.agricraft.agricraft.api.crop.AgriGrowthStage;
import com.agricraft.agricraft.api.genetic.AgriGenePair;
import com.agricraft.agricraft.api.genetic.AgriGenome;
import com.agricraft.agricraft.common.block.CropBlock;
import com.agricraft.agricraft.common.block.CropState;
import com.agricraft.agricraft.common.block.entity.CropBlockEntity;
import com.agricraft.agricraft.common.handler.VanillaSeedConversion;
import com.agricraft.agricraft.common.registry.ModBlocks;
import com.agricraft.agricraft.common.registry.ModItems;
import com.mojang.authlib.GameProfile;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.OrientedContraptionEntity;
import com.simibubi.create.content.contraptions.actors.harvester.HarvesterMovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.mounted.MountedContraption;
import dev.aeroscapes.equipment.ReforgeProfiles;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/** Exercises loaded crop data, native genetics, Create harvesting and safe helmet crafting. */
public final class AgricultureRegression {
  private AgricultureRegression() {}

  public static void verify(ServerLevel level) {
    var plants = level.registryAccess().registryOrThrow(AgriApi.AGRIPLANTS);
    require(
        plants.keySet().stream().noneMatch(id -> id.getNamespace().equals("agricraft")),
        "Resource-producing plants are not loaded");
    for (String plant :
        List.of(
            "minecraft:wheat",
            "minecraft:carrot",
            "minecraft:potato",
            "minecraft:allium",
            "farmersdelight:cabbage",
            "farmersdelight:onion",
            "farmersdelight:rice",
            "farmersdelight:tomato"))
      require(
          plants.containsKey(ResourceLocation.parse(plant)), "Available breeding plant: " + plant);
    verifyHelmetRecipe(level);
    for (var item :
        List.of(
            ModItems.CLIPPER.get(),
            ModItems.WOODEN_RAKE.get(),
            ModItems.IRON_RAKE.get(),
            ModItems.TROWEL.get()))
      require(
          ReforgeProfiles.paidGrades(item).isEmpty(),
          "Owner-selected native gardening tool has no reforge pool: " + item);
    verifyHarvest(level);
  }

  private static void verifyHelmetRecipe(ServerLevel level) {
    var holder =
        level
            .getRecipeManager()
            .byKey(ResourceLocation.parse("agricraft:magnifying_helmet"))
            .orElseThrow();
    require(holder.value() instanceof CraftingRecipe, "Native magnifying-helmet recipe exists");
    var recipe = (CraftingRecipe) holder.value();
    var helmet = new ItemStack(Items.IRON_HELMET);
    helmet.setDamageValue(7);
    helmet.set(DataComponents.CUSTOM_NAME, Component.literal("Retained helmet"));
    var ownerData = new CompoundTag();
    ownerData.putString("aeroscapes_regression", "retained");
    helmet.set(DataComponents.CUSTOM_DATA, CustomData.of(ownerData));
    var glass = new ItemStack(ModItems.MAGNIFYING_GLASS.get());
    var valid = CraftingInput.of(2, 1, List.of(helmet, glass));
    require(recipe.matches(valid, level), "Helmet and magnifier craft normally");
    var result = recipe.assemble(valid, level.registryAccess());
    require(
        result.is(Items.IRON_HELMET)
            && result.getDamageValue() == 7
            && result.getHoverName().getString().equals("Retained helmet"),
        "Magnification preserves helmet identity, damage and name");
    var data = result.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    require(
        data.getBoolean("magnifying") && data.getString("aeroscapes_regression").equals("retained"),
        "Magnification preserves existing custom data");
    require(
        !recipe.matches(
            CraftingInput.of(3, 1, List.of(helmet, glass, new ItemStack(Items.DIAMOND))), level),
        "Magnifying recipe refuses unrelated cargo instead of consuming it");
    require(
        !recipe.matches(CraftingInput.of(2, 1, List.of(result, glass)), level),
        "Already magnified helmets cannot consume another magnifier");
    require(
        !recipe.matches(CraftingInput.of(3, 1, List.of(helmet, glass, glass.copy())), level),
        "Magnifying recipe refuses duplicate magnifiers");
    require(
        !recipe.matches(CraftingInput.of(3, 1, List.of(helmet, helmet.copy(), glass)), level),
        "Magnifying recipe refuses duplicate ordinary helmets");
    require(
        !recipe.matches(CraftingInput.of(3, 1, List.of(helmet, glass, result)), level),
        "Magnifying recipe refuses an extra already-magnified helmet");
  }

  private static void verifyHarvest(ServerLevel level) {
    var spawn = level.getSharedSpawnPos();
    var pos =
        new BlockPos(
            spawn.getX(), Math.min(spawn.getY() + 40, level.getMaxBuildHeight() - 4), spawn.getZ());
    var previousSoil = level.getBlockState(pos.below());
    var previousCrop = level.getBlockState(pos);
    require(
        level.getBlockEntity(pos) == null && level.getBlockEntity(pos.below()) == null,
        "Agriculture fixture preserves existing block entities");
    var bounds = new AABB(pos).inflate(2);
    var previousDrops =
        level.getEntitiesOfClass(ItemEntity.class, bounds).stream()
            .map(ItemEntity::getUUID)
            .collect(Collectors.toSet());
    var harvesterInfo =
        new StructureBlockInfo(
            BlockPos.ZERO, AllBlocks.MECHANICAL_HARVESTER.getDefaultState(), new CompoundTag());
    var contraption = new MountedContraption();
    contraption.getBlocks().put(BlockPos.ZERO, harvesterInfo);
    contraption.anchor = pos;
    contraption.bounds = new AABB(BlockPos.ZERO);
    var contraptionEntity = OrientedContraptionEntity.create(level, contraption, Direction.NORTH);
    contraptionEntity.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    try {
      level.setBlockAndUpdate(
          pos.below(),
          Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7));
      verifyNativeFarming(level, pos);
      var context = new MovementContext(level, harvesterInfo, contraption);
      context.position = Vec3.atCenterOf(pos);
      context.motion = new Vec3(0, 0, 1);
      context.relativeMotion = context.motion;
      require(
          contraption.entity == contraptionEntity,
          "Native contraption creation supplies Sable's harvester context");
      var harvester = new RecordingHarvester();
      for (String plantId :
          List.of(
              "minecraft:wheat",
              "farmersdelight:cabbage",
              "farmersdelight:onion",
              "farmersdelight:rice",
              "farmersdelight:tomato")) {
        level.setBlockAndUpdate(
            pos,
            ModBlocks.CROP
                .get()
                .defaultBlockState()
                .setValue(CropBlock.CROP_STATE, CropState.SINGLE_STICKS));
        var crop = (CropBlockEntity) level.getBlockEntity(pos);
        require(crop != null, "Native crop block entity exists");
        var plant =
            AgriApi.getPlant(ResourceLocation.parse(plantId), level.registryAccess()).orElseThrow();
        var base = new AgriGenome(plant);
        var stats =
            base.getStatGenes().stream()
                .map(
                    pair -> {
                      var gene = pair.getGene();
                      return new AgriGenePair<>(gene, gene.getAllele(8), gene.getAllele(3));
                    })
                .toList();
        var genome = new AgriGenome(base.getSpeciesGene(), stats);
        crop.plantGenome(genome, null);
        crop.setGrowthStage(new AgriGrowthStage(2, plant.getInitialGrowthStage().total()));
        int immatureStage = crop.getGrowthStage().index();
        harvester.output.clear();
        harvester.visitNewPosition(context, pos);
        require(
            crop.getGrowthStage().index() == immatureStage && harvester.output.isEmpty(),
            "Create preserves immature growth: " + plantId);
        require(
            harvester.isValidOther(level, pos, level.getBlockState(pos)),
            "Create can pass through crop sticks: " + plantId);
        var savedGenome = new CompoundTag();
        genome.writeToNBT(savedGenome);
        for (int attempt = 0; attempt < 8 && harvester.output.isEmpty(); attempt++) {
          crop.setGrowthStage(new AgriGrowthStage(7, 8));
          harvester.visitNewPosition(context, pos);
        }
        require(!harvester.output.isEmpty(), "Mature crop produces native food: " + plantId);
        String produce =
            plantId.equals("farmersdelight:rice") ? "farmersdelight:rice_panicle" : plantId;
        var item =
            net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                ResourceLocation.parse(produce));
        require(
            harvester.output.stream().allMatch(stack -> stack.is(item)),
            "Create collects the native produce: " + plantId);
        require(
            crop.hasCropSticks() && crop.hasPlant() && !crop.isFullyGrown(),
            "Mature harvesting preserves the planting and regrows: " + plantId);
        var after = new CompoundTag();
        crop.getGenome().writeToNBT(after);
        require(
            after.equals(savedGenome),
            "Harvest preserves dominant and recessive genes: " + plantId);
        var saved = crop.saveWithFullMetadata(level.registryAccess());
        crop.loadWithComponents(saved, level.registryAccess());
        var reloaded = new CompoundTag();
        crop.getGenome().writeToNBT(reloaded);
        require(reloaded.equals(savedGenome), "Crop save/reload preserves genes: " + plantId);
      }
    } finally {
      contraptionEntity.discard();
      level.setBlockAndUpdate(pos, previousCrop);
      level.setBlockAndUpdate(pos.below(), previousSoil);
      for (var drop : level.getEntitiesOfClass(ItemEntity.class, bounds))
        if (!previousDrops.contains(drop.getUUID())) drop.discard();
    }
  }

  private static void verifyNativeFarming(ServerLevel level, BlockPos pos) {
    var player =
        new FakePlayer(
            level,
            new GameProfile(
                UUID.fromString("9e81c07b-dbc3-4d86-ae4c-655c6b373af0"), "AgriNativeFarm"));
    var seeds = new ItemStack(Items.WHEAT_SEEDS, 2);
    player.setItemInHand(InteractionHand.MAIN_HAND, seeds);
    var hit = new BlockHitResult(Vec3.atBottomCenterOf(pos), Direction.UP, pos.below(), false);
    require(
        VanillaSeedConversion.onRightClick(player, InteractionHand.MAIN_HAND, pos.below(), hit)
            == InteractionResult.PASS,
        "Ordinary farmland planting is not replaced by seed conversion");
    require(
        seeds.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction()
            && level.getBlockState(pos).is(Blocks.WHEAT)
            && seeds.getCount() == 1,
        "An ordinary seed plants and consumes exactly one native wheat seed");
    var state = Blocks.SHORT_GRASS.defaultBlockState();
    var params =
        new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
            .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
            .withParameter(LootContextParams.BLOCK_STATE, state)
            .create(LootContextParamSets.BLOCK);
    var loot =
        level.getServer().reloadableRegistries().getLootTable(Blocks.SHORT_GRASS.getLootTable());
    boolean ordinarySeeds = false;
    for (int sample = 0; sample < 128 && !ordinarySeeds; sample++) {
      ordinarySeeds =
          loot.getRandomItems(params, (sample + 1L) * 0x9e3779b97f4a7c15L).stream()
              .anyMatch(stack -> stack.is(Items.WHEAT_SEEDS));
    }
    require(ordinarySeeds, "AgriCraft grass loot preserves ordinary native seed acquisition");
  }

  private static final class RecordingHarvester extends HarvesterMovementBehaviour {
    private final List<ItemStack> output = new ArrayList<>();

    @Override
    public void collectOrDropItem(MovementContext context, ItemStack stack) {
      output.add(stack.copy());
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}

package dev.aeroscapes.regression;

import com.herobrot.tieredneo.api.ModifierUtils;
import com.herobrot.tieredneo.api.TieredNeoAPI;
import dev.aeroscapes.equipment.ReforgeProfiles;
import dev.aeroscapes.husbandry.CareClock;
import dev.aeroscapes.husbandry.ChickenGeneticAppearance;
import dev.aeroscapes.husbandry.GroomingReforges;
import dev.aeroscapes.husbandry.HusbandryLanguage;
import dev.aeroscapes.husbandry.TroughAutomation;
import dev.aeroscapes.husbandry.TroughWaterAccess;
import dev.architectury.networking.NetworkManager.PacketContext;
import dev.architectury.utils.Env;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import org.athisworkshop.animalhusbandry.Animalhusbandry;
import org.athisworkshop.animalhusbandry.block.FeedingTroughBlock;
import org.athisworkshop.animalhusbandry.block.FeedingTroughBlockEntity;
import org.athisworkshop.animalhusbandry.config.ModConfig;
import org.athisworkshop.animalhusbandry.network.HighlightAnimalPayload;
import org.athisworkshop.animalhusbandry.network.ModNetworking;
import org.athisworkshop.animalhusbandry.registry.ModAttachments;

public final class HusbandryRegression {
  private HusbandryRegression() {}

  public static void verify(ServerLevel level) {
    require(CareClock.elapsed(12000, 12000, 24000, 1, true) == 24000, "Loaded sleep advances care");
    require(
        CareClock.elapsed(12000, 12000, 480000, 468000, true) == 12000, "Unticked time is paused");
    require(CareClock.elapsed(12000, 12000, 24000, 1, false) == 12000, "Offline owners pause care");
    require(CareClock.elapsed(12000, 12000, 0, 1, true) == 12000, "Clock rollback keeps progress");
    require(GroomingReforges.multiplier("grooming_ordinary") == 1, "Ordinary brush keeps baseline");
    require(GroomingReforges.multiplier("grooming_fine") == 1.25F, "Fine brush improves happiness");
    require(GroomingReforges.multiplier("grooming_legendary") == 2.5F, "Grooming bonus is bounded");
    verifyTrough(level);
    verifyRecipesAndReforging(level);
    verifyAnimals(level);
    verifyLanguage();
    verifyChickenTextures(level);
    long cared = herdWork(level, false);
    long neglected = herdWork(level, true);
    System.out.println(
        "AEROSCAPES_HUSBANDRY_WORKLOAD direct_ai 12_cows_x40_steps cared_ns="
            + cared
            + " hungry_thirsty_without_trough_ns="
            + neglected);
  }

  private static void verifyLanguage() {
    Language previous = Language.getInstance();
    try {
      Language.inject(translations("en_us", previous));
      require(
          HusbandryLanguage.literal("§aAdded Ada_42 to the Ledger's co-owners.")
              .getString()
              .equals("§aAdded Ada_42 to the Ledger's co-owners."),
          "English dynamic message preserves the original name");
      Language.inject(translations("ru_ru", previous));
      require(
          HusbandryLanguage.literal("§aAdded Ada_42 to the Ledger's co-owners.")
              .getString()
              .equals("§aAda_42 добавлен в список помощников."),
          "Russian dynamic message translates surrounding text and preserves the name");
      require(
          HusbandryLanguage.text("Farm Roster").equals("Животные фермы"),
          "Ledger literals localize");
      require(
          HusbandryLanguage.text("Generation 42").equals("Поколение 42"),
          "Generation values remain dynamic");
      require(
          HusbandryLanguage.literal("§6Transfer Deed: Нора_100%")
              .getString()
              .equals("§6Передача животного: Нора_100%"),
          "Custom animal names retain Unicode and percent signs");
      require(
          Component.translatable("message.animalhusbandry.brushed_animal", 25)
              .getString()
              .equals("Вы расчесали животное! Счастье +25"),
          "Native numeric grooming feedback formats its actual gain");
      require(
          HusbandryLanguage.text("An owner's custom text").equals("An owner's custom text"),
          "Unknown text retains fallback");
    } finally {
      Language.inject(previous);
    }
  }

  private static Language translations(String language, Language fallback) {
    Map<String, String> entries = new HashMap<>();
    String path = "/assets/aeroscapes_husbandry_translations/lang/" + language + ".json";
    try (var stream = HusbandryLanguage.class.getResourceAsStream(path)) {
      require(stream != null, "Pack husbandry locale is available: " + language);
      Language.loadFromJson(stream, entries::put);
    } catch (IOException failure) {
      throw new IllegalStateException("Cannot read husbandry locale", failure);
    }
    return new Language() {
      @Override
      public String getOrDefault(String key, String defaultValue) {
        return entries.getOrDefault(key, fallback.getOrDefault(key, defaultValue));
      }

      @Override
      public boolean has(String key) {
        return entries.containsKey(key) || fallback.has(key);
      }

      @Override
      public boolean isDefaultRightToLeft() {
        return false;
      }

      @Override
      public FormattedCharSequence getVisualOrder(FormattedText text) {
        return fallback.getVisualOrder(text);
      }
    };
  }

  private static void verifyChickenTextures(ServerLevel level) {
    var chicken = EntityType.CHICKEN.create(level);
    require(chicken != null, "Chicken appearance fixture exists");
    var genetics = ModAttachments.getGenetics(chicken);
    boolean customTextures = ModConfig.INSTANCE.enableCustomTextures;
    try {
      ModConfig.INSTANCE.enableCustomTextures = true;
      var textures = new HashSet<ResourceLocation>();
      for (boolean baby : List.of(false, true)) {
        chicken.setBaby(baby);
        for (String color : List.of("white", "brown", "black", "golden")) {
          for (String pattern : List.of("solid", "speckled")) {
            genetics.primaryColor = color;
            genetics.pattern = pattern;
            ModAttachments.setGenetics(chicken, genetics);
            var texture = ChickenGeneticAppearance.texture(chicken);
            require(
                texture != null && textures.add(texture),
                "Every genetic appearance and age selects a distinct texture");
            String path = "/assets/" + texture.getNamespace() + "/" + texture.getPath();
            try (var stream = ChickenGeneticAppearance.class.getResourceAsStream(path)) {
              require(stream != null, "Selected appearance has an actual texture");
              byte[] bytes = stream.readAllBytes();
              require(
                  bytes.length >= 24
                      && java.nio.ByteBuffer.wrap(bytes, 16, 8).getInt() == 32
                      && java.nio.ByteBuffer.wrap(bytes, 20, 4).getInt() == 32,
                  "Selected appearance retains Fowl's32x32 UV dimensions");
            } catch (IOException failure) {
              throw new IllegalStateException("Cannot read inherited chicken texture", failure);
            }
          }
        }
      }
      genetics.pattern = "unknown";
      require(
          ChickenGeneticAppearance.texture(chicken) == null,
          "Unknown appearance keeps the provider renderer fallback");
      genetics.pattern = "solid";
      ModConfig.INSTANCE.enableCustomTextures = false;
      require(
          ChickenGeneticAppearance.texture(chicken) == null,
          "The owner's native texture preference remains effective");
    } finally {
      ModConfig.INSTANCE.enableCustomTextures = customTextures;
      chicken.discard();
    }
  }

  private static void verifyRecipesAndReforging(ServerLevel level) {
    int crafting = 0;
    int eggs = 0;
    for (var holder : level.getRecipeManager().getRecipes()) {
      if (holder.id().getNamespace().equals("animalhusbandry")
          && holder.value() instanceof CraftingRecipe recipe) {
        int width = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : 3;
        int height = recipe instanceof ShapedRecipe shaped ? shaped.getHeight() : 3;
        var ingredients =
            new ArrayList<>(
                recipe.getIngredients().stream()
                    .map(
                        ingredient ->
                            ingredient.isEmpty()
                                ? ItemStack.EMPTY
                                : ingredient.getItems()[0].copy())
                    .toList());
        while (ingredients.size() < width * height) ingredients.add(ItemStack.EMPTY);
        var input = CraftingInput.of(width, height, ingredients);
        require(recipe.matches(input, level), "Native animal-care recipe matches: " + holder.id());
        require(
            !recipe.assemble(input, level.registryAccess()).isEmpty(),
            "Native care recipe has output");
        require(
            recipe.getRemainingItems(input).stream().allMatch(ItemStack::isEmpty),
            "No duplicated care recipe remainder");
        crafting++;
      }
      if (holder.value() instanceof SmeltingRecipe recipe
          && holder.id().toString().equals("farmersdelight:fried_egg")) {
        var input = new SingleRecipeInput(new ItemStack(Items.EGG));
        require(
            recipe.matches(input, level), "Canonical cooked-egg recipe accepts actual egg input");
        require(
            recipe.assemble(input, level.registryAccess()).getCount() == 1,
            "Egg output remains one portion");
        eggs++;
      }
    }
    require(crafting == 14, "All14 AH native crafting recipes are usable");
    require(eggs == 1, "One canonical Farmer's Delight egg route remains registered");
    var sandwich =
        level
            .getRecipeManager()
            .byKey(ResourceLocation.parse("farmersdelight:egg_sandwich"))
            .orElseThrow()
            .value();
    if (!(sandwich instanceof CraftingRecipe recipe))
      throw new IllegalStateException("Native egg sandwich is a crafting recipe");
    var friedEgg = BuiltInRegistries.ITEM.get(ResourceLocation.parse("animalhusbandry:fried_egg"));
    var sandwichInput =
        CraftingInput.of(
            3,
            1,
            List.of(new ItemStack(Items.BREAD), new ItemStack(friedEgg), new ItemStack(friedEgg)));
    require(
        recipe.matches(sandwichInput, level)
            && recipe
                .assemble(sandwichInput, level.registryAccess())
                .is(
                    BuiltInRegistries.ITEM.get(
                        ResourceLocation.parse("farmersdelight:egg_sandwich"))),
        "AH furnace output remains usable in the native Farmer's Delight kitchen");
    var brush = BuiltInRegistries.ITEM.get(ResourceLocation.parse("animalhusbandry:brush"));
    var grades = ReforgeProfiles.paidGrades(brush);
    require(grades.size() == 6, "Paid grooming includes every quality from poor through legendary");
    for (var grade : grades)
      require(
          grade.outcomes().size() == 1
              && grade.outcomes().getFirst().id().getPath().equals("grooming_" + grade.key()),
          "Brush uses only its grooming outcome family");
    require(
        TieredNeoAPI.getReforgeBaseItems(brush).equals(List.of(Items.WHEAT)),
        "Brush reforge material follows its bristles");
    var yarn = BuiltInRegistries.ITEM.get(ResourceLocation.parse("animalhusbandry:angora_yarn"));
    for (String piece : List.of("hood", "sweater", "leg_warmers", "slippers")) {
      var armor =
          BuiltInRegistries.ITEM.get(ResourceLocation.parse("animalhusbandry:angora_" + piece));
      require(
          !ReforgeProfiles.paidGrades(armor).isEmpty(), "Angora piece joins existing armor family");
      require(
          TieredNeoAPI.getReforgeBaseItems(armor).equals(List.of(yarn)),
          "Angora uses native yarn for reforging");
    }
  }

  private static long herdWork(ServerLevel level, boolean hungry) {
    var herd = new ArrayList<Cow>();
    for (int index = 0; index < 12; index++) {
      Cow cow = EntityType.COW.create(level);
      require(cow != null, "Herd fixture cow exists");
      cow.moveTo(Vec3.atCenterOf(level.getSharedSpawnPos()).add(index % 4 * 3, 24, index / 4 * 3));
      cow.getRandom().setSeed(20261114L + index);
      var care = ModAttachments.getCareData(cow);
      care.isDomesticated = true;
      care.daysSinceFed = hungry ? 1 : 0;
      care.daysDehydrated = hungry ? 1 : 0;
      ModAttachments.setCareData(cow, care);
      require(level.addFreshEntity(cow), "Herd fixture is visible to native neighborhood queries");
      herd.add(cow);
    }
    try {
      long started = System.nanoTime();
      for (int tick = 0; tick < 40; tick++) for (Cow cow : herd) cow.aiStep();
      return System.nanoTime() - started;
    } finally {
      herd.forEach(Cow::discard);
    }
  }

  private static void verifyTrough(ServerLevel level) {
    BlockPos origin = level.getSharedSpawnPos().above(16).east(32);
    var previous = level.getBlockState(origin);
    var player = FakePlayerFactory.getMinecraft(level);
    ItemStack held = player.getMainHandItem().copy();
    try {
      var block =
          BuiltInRegistries.BLOCK.get(ResourceLocation.parse("animalhusbandry:feeding_trough"));
      level.setBlockAndUpdate(origin, block.defaultBlockState());
      var trough = (FeedingTroughBlockEntity) level.getBlockEntity(origin);
      require(trough != null, "Actual AH trough block entity exists");
      var items = level.getCapability(Capabilities.ItemHandler.BLOCK, origin, Direction.UP);
      var water = level.getCapability(Capabilities.FluidHandler.BLOCK, origin, Direction.NORTH);
      require(items != null && water != null, "Standard item/fluid capabilities are registered");
      require(
          items.insertItem(0, new ItemStack(Items.STONE), true).getCount() == 1, "Reject non-food");
      require(
          items.insertItem(0, new ItemStack(Items.WHEAT, 8), true).isEmpty(), "Simulate food fill");
      require(trough.isEmpty(), "Food simulation does not mutate the trough");
      require(
          trough.getBlockState().getValue(FeedingTroughBlock.CONTENT) == 0,
          "Food simulation keeps empty visual state");
      require(
          items.insertItem(0, new ItemStack(Items.WHEAT, 8), false).isEmpty(), "Execute food fill");
      require(
          trough.getBlockState().getValue(FeedingTroughBlock.CONTENT) == 1,
          "Food insertion updates native visual state");
      require(
          water.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE) == 0,
          "No water-food mix");
      require(items.extractItem(0, 8, false).getCount() == 8, "Food extraction preserves quantity");
      require(
          trough.getBlockState().getValue(FeedingTroughBlock.CONTENT) == 0,
          "Food extraction updates native visual state");
      require(
          water.fill(new FluidStack(Fluids.WATER, 250), FluidAction.SIMULATE) == 250,
          "Pipe fill simulation");
      require(TroughAutomation.waterUnits(trough) == 0, "Water simulation is read-only");
      require(
          water.fill(new FluidStack(Fluids.LAVA, 1000), FluidAction.EXECUTE) == 0, "Reject lava");
      for (int index = 0; index < 4; index++)
        require(
            water.fill(new FluidStack(Fluids.WATER, 250), FluidAction.EXECUTE) == 250,
            "Partial pipe fill");
      require(
          trough.waterLevel == 16 && TroughAutomation.waterUnits(trough) == 16000,
          "One bucket is16 drinks");
      require(
          items.insertItem(0, new ItemStack(Items.WHEAT), false).getCount() == 1,
          "No food in water trough");
      trough.waterLevel--;
      require(
          water.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE) == 62,
          "Half-millibucket preserved");
      require(
          ((TroughWaterAccess) trough).aeroscapes$waterRemainder() == 992,
          "Remainder stores sub-drink water");
      var saved = trough.saveWithFullMetadata(level.registryAccess());
      trough.loadWithComponents(saved, level.registryAccess());
      require(TroughAutomation.waterUnits(trough) == 15992, "Reload preserves fractional water");
      player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
      var hit = new BlockHitResult(Vec3.atCenterOf(origin), Direction.UP, origin, false);
      require(
          level
                  .getBlockState(origin)
                  .useItemOn(
                      player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit)
              == ItemInteractionResult.FAIL,
          "Partial trough cannot duplicate a full water bucket");
      require(
          player.getMainHandItem().is(Items.BUCKET) && TroughAutomation.waterUnits(trough) == 15992,
          "Rejected bucket keeps contents");
      trough.waterLevel--;
      require(
          water.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE) == 63,
          "Two drinks refill with125mB exactly");
      require(TroughAutomation.waterUnits(trough) == 16000, "Repeated fill does not create water");
      require(water.drain(1000, FluidAction.EXECUTE).isEmpty(), "Pipe endpoint is refill-only");
      trough.waterLevel = 0;
      ((TroughWaterAccess) trough).aeroscapes$waterRemainder(16);
      trough.updateVisualState();
      player.setShiftKeyDown(true);
      try {
        var spill =
            new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, origin, hit);
        NeoForge.EVENT_BUS.post(spill);
        require(
            spill.isCanceled() && spill.getCancellationResult().consumesAction(),
            "Sneak-use of an empty bucket spills a sub-drink remainder");
      } finally {
        player.setShiftKeyDown(false);
      }
      require(
          TroughAutomation.waterUnits(trough) == 0 && player.getMainHandItem().is(Items.BUCKET),
          "Spilling releases the trough without producing a bucket of water");
      require(
          items.insertItem(0, new ItemStack(Items.WHEAT), false).isEmpty(),
          "Food can enter after residual water is deliberately spilled");
      items.extractItem(0, 1, false);
      player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
      require(
          level
              .getBlockState(origin)
              .useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit)
              .consumesAction(),
          "A hand water bucket refills the recovered empty trough");
      require(
          TroughAutomation.waterUnits(trough) == 16000 && player.getMainHandItem().is(Items.BUCKET),
          "Hand refill consumes exactly one water bucket");
    } finally {
      player.setItemInHand(InteractionHand.MAIN_HAND, held);
      level.setBlockAndUpdate(origin, previous);
    }
  }

  private static void verifyAnimals(ServerLevel level) {
    var player = FakePlayerFactory.getMinecraft(level);
    ItemStack held = player.getMainHandItem().copy();
    long time = level.getDayTime();
    long gameTime = level.getGameTime();
    var levelData = (ServerLevelData) level.getLevelData();
    Cow cow = EntityType.COW.create(level);
    require(cow != null, "Actual vanilla cow exists");
    cow.moveTo(Vec3.atCenterOf(level.getSharedSpawnPos()).add(0, 16, 0));
    try {
      levelData.setGameTime((gameTime / 100 + 1) * 100);
      require(level.addFreshEntity(cow), "Cow is registered for actual receiver lookup");
      cow.aiStep();
      var care = ModAttachments.getCareData(cow);
      care.isDomesticated = true;
      care.ownerUUID = null;
      care.daysSinceFed = 1;
      care.daysSinceGroomed = 2;
      care.happiness = 30;
      care.lastTickTime = time - 12000;
      ModAttachments.setCareData(cow, care);
      var genetics = ModAttachments.getGenetics(cow);
      genetics.trait = "none";
      ModAttachments.setGenetics(cow, genetics);
      level.setDayTime(time + 240000);
      var saved = cow.saveWithoutId(new CompoundTag());
      require(
          saved.getLong("AeroscapesLoadedCareElapsed") == 12000,
          "Saving an unticked animal excludes elapsed world time but stores its earned care remainder");
      level.setDayTime(time + 480000);
      Cow reloaded = EntityType.COW.create(level);
      require(reloaded != null, "Reload target cow exists");
      reloaded.load(saved);
      reloaded.aiStep();
      var restored = ModAttachments.getCareData(reloaded);
      require(
          restored.daysSinceFed == 1 && restored.daysSinceGroomed == 2,
          "Returning after unload creates no neglect catch-up");
      require(level.getDayTime() - restored.lastTickTime == 12000, "Unload keeps earned half-day");
      level.setDayTime(level.getDayTime() + 24000);
      reloaded.aiStep();
      require(
          restored.daysSinceFed == 2 && restored.daysSinceGroomed == 3,
          "Actively loaded sleep advances the native daily block");
      require(
          ModAttachments.getGenetics(reloaded).animalId.equals(genetics.animalId),
          "Genetic identity survives save/reload");
      var brushItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse("animalhusbandry:brush"));
      var brush = new ItemStack(brushItem);
      restored.happiness = 30;
      ModAttachments.setCareData(reloaded, restored);
      brushItem.interactLivingEntity(brush, player, reloaded, InteractionHand.MAIN_HAND);
      require(
          restored.daysSinceGroomed == 0 && restored.happiness == 40,
          "Ordinary grooming keeps native cleanliness and happiness");
      require(brush.getDamageValue() == 1, "Successful grooming keeps native wear");
      for (String grade : List.of("poor", "fine", "exceptional", "epic", "legendary")) {
        var tier = ResourceLocation.parse("aeroscapes_reimagine:grooming_" + grade);
        var qualityBrush = new ItemStack(brushItem);
        ModifierUtils.applyTierToStack(qualityBrush, tier, TieredNeoAPI.getTierAttribute(tier));
        restored.daysSinceGroomed = 3;
        restored.happiness = 0;
        ModAttachments.setCareData(reloaded, restored);
        brushItem.interactLivingEntity(qualityBrush, player, reloaded, InteractionHand.MAIN_HAND);
        require(restored.daysSinceGroomed == 0, "Quality preserves full native cleaning");
        require(
            restored.happiness == Math.round(10 * GroomingReforges.multiplier("grooming_" + grade)),
            "Quality changes successful grooming happiness");
        require(qualityBrush.getDamageValue() == 1, "Quality preserves native brush wear");
      }
      PacketContext context = new ServerContext(level, player);
      invoke(
          ModNetworking.class,
          "lambda$init$9",
          new Class<?>[] {HighlightAnimalPayload.class, PacketContext.class},
          new HighlightAnimalPayload(cow.getId()),
          context);
      require(
          !cow.hasEffect(MobEffects.GLOWING),
          "Client highlight request cannot enable server radar");
      var deed = new ItemStack(Items.WRITTEN_BOOK);
      var deedTag = new CompoundTag();
      deedTag.putUUID("TransferAnimalUUID", cow.getUUID());
      deed.set(DataComponents.CUSTOM_DATA, CustomData.of(deedTag));
      player.setItemInHand(InteractionHand.MAIN_HAND, deed);
      player.tickCount = 0;
      invoke(Animalhusbandry.class, "lambda$init$2", new Class<?>[] {Player.class}, player);
      require(!cow.hasEffect(MobEffects.GLOWING), "Transfer deed does not reveal animal locations");
    } finally {
      level.setDayTime(time);
      levelData.setGameTime(gameTime);
      player.setItemInHand(InteractionHand.MAIN_HAND, held);
      cow.discard();
    }
  }

  private static void invoke(
      Class<?> type, String name, Class<?>[] parameters, Object... arguments) {
    try {
      var method = type.getDeclaredMethod(name, parameters);
      method.setAccessible(true);
      method.invoke(null, arguments);
    } catch (ReflectiveOperationException failure) {
      Throwable cause =
          failure instanceof InvocationTargetException invocation ? invocation.getCause() : failure;
      throw new IllegalStateException("Pinned Animal Husbandry receiver fixture failed", cause);
    }
  }

  private record ServerContext(ServerLevel level, Player player) implements PacketContext {
    @Override
    public Player getPlayer() {
      return player;
    }

    @Override
    public void queue(Runnable action) {
      action.run();
    }

    @Override
    public Env getEnvironment() {
      return Env.SERVER;
    }

    @Override
    public RegistryAccess registryAccess() {
      return level.registryAccess();
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}

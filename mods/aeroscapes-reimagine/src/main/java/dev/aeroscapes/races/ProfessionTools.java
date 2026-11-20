package dev.aeroscapes.races;

import dev.aeroscapes.equipment.tools.ToolReforges;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ProfessionTools {
  private static final DeferredRegister<Attribute> ATTRIBUTES =
      DeferredRegister.create(Registries.ATTRIBUTE, "aeroscapes_reimagine");
  private static final DeferredHolder<Attribute, Attribute> DISMANTLING_SPEED =
      ATTRIBUTES.register(
          "dismantling_speed",
          () ->
              new RangedAttribute("attribute.aeroscapes_reimagine.dismantling_speed", 0, 0, 1)
                  .setSyncable(true));
  private static final TagKey<Block> CONSTRUCTION =
      TagKey.create(
          Registries.BLOCK, ResourceLocation.parse("aeroscapes_reimagine:construction_blocks"));

  private ProfessionTools() {}

  public static void register(IEventBus bus) {
    ATTRIBUTES.register(bus);
    bus.addListener(ProfessionTools::addAttributes);
    NeoForge.EVENT_BUS.addListener(ProfessionTools::dismantle);
  }

  private static void addAttributes(EntityAttributeModificationEvent event) {
    event.add(EntityType.PLAYER, DISMANTLING_SPEED);
  }

  private static void dismantle(PlayerEvent.BreakSpeed event) {
    var player = event.getEntity();
    var state = event.getState();
    if (!state.is(CONSTRUCTION) || !ToolReforges.isSurvivalPlayer(player)) return;
    var tool = player.getMainHandItem();
    if (tool.getDestroySpeed(state) > 1
        && (!state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state))) {
      float bonus = (float) player.getAttributeValue(DISMANTLING_SPEED);
      event.setNewSpeed(event.getNewSpeed() * (1 + bonus));
    }
  }

  public static int tillingWear(LivingEntity user, int amount) {
    if (amount > 0
        && user instanceof ServerPlayer player
        && ModList.get().isLoaded("puffish_skills")
        && ToolReforges.isSurvivalPlayer(player)
        && ProfessionProgression.learned(player, "careful_tilling")
        && player.getRandom().nextInt(4) == 0) return 0;
    return amount;
  }
}

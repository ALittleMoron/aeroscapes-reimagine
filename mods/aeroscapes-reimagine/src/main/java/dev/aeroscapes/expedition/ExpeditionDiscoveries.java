package dev.aeroscapes.expedition;

import dev.aeroscapes.magic.PreparedAbilities;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ExpeditionDiscoveries {
  private static final String MOD_ID = "aeroscapes_reimagine";
  private static final String DISCOVERIES = MOD_ID + ":expedition_discoveries";
  private static final String DEEP_BREATH = "deep_breath";
  private static final String DISTRACTION = "distraction_throw";
  private static final String NEXT_DISTRACTION = "next_distraction";
  private static final int DISTRACTION_COOLDOWN = 100;
  private static final AttributeModifier BREATH =
      new AttributeModifier(
          ResourceLocation.fromNamespaceAndPath(MOD_ID, DEEP_BREATH),
          0.25,
          AttributeModifier.Operation.ADD_VALUE);
  private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
  static final DeferredItem<Item> BREATH_SCROLL =
      ITEMS.register("deep_breath_scroll", () -> new StudyScroll(DEEP_BREATH));
  static final DeferredItem<Item> DISTRACTION_SCROLL =
      ITEMS.register("distraction_throw_scroll", () -> new StudyScroll(DISTRACTION));

  private ExpeditionDiscoveries() {}

  public static void register(IEventBus bus) {
    ITEMS.register(bus);
    bus.addListener(ExpeditionDiscoveries::creativeContents);
    NeoForge.EVENT_BUS.addListener(ExpeditionDiscoveries::clonePlayer);
    NeoForge.EVENT_BUS.addListener(ExpeditionDiscoveries::login);
    NeoForge.EVENT_BUS.addListener(ExpeditionDiscoveries::respawn);
    ExpeditionStudyLoot.register();
    DistractionGoal.register();
  }

  public static boolean knowsDistraction(ServerPlayer player) {
    return discoveries(player).getBoolean(DISTRACTION);
  }

  public static int distractionCooldownTicks(ServerPlayer player) {
    long remaining =
        discoveries(player).getLong(NEXT_DISTRACTION) - player.server.overworld().getGameTime();
    return (int) Math.clamp(remaining, 0L, DISTRACTION_COOLDOWN);
  }

  public static void throwDistraction(ServerPlayer player) {
    if (!player.isAlive() || player.isSpectator() || !knowsDistraction(player)) return;
    if (distractionCooldownTicks(player) > 0) {
      message(player, "cooldown");
      return;
    }
    ItemStack ammunition = player.getOffhandItem();
    if (!ammunition.is(Items.SNOWBALL)) {
      ammunition =
          player.getInventory().items.stream()
              .filter(stack -> stack.is(Items.SNOWBALL))
              .findFirst()
              .orElse(ItemStack.EMPTY);
    }
    if (ammunition.isEmpty() && !player.getAbilities().instabuild) {
      message(player, "no_snowball");
      return;
    }

    var level = player.serverLevel();
    var snowball = new Snowball(level, player);
    if (!ammunition.isEmpty()) snowball.setItem(ammunition);
    snowball.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, 1.5F, 0.5F);
    DistractionGoal.mark(snowball);
    if (!level.addFreshEntity(snowball)) return;
    if (!player.getAbilities().instabuild) {
      ammunition.shrink(1);
      player.getInventory().setChanged();
    }
    var data = discoveries(player);
    data.putLong(NEXT_DISTRACTION, player.server.overworld().getGameTime() + DISTRACTION_COOLDOWN);
    player.getPersistentData().put(DISCOVERIES, data);
    level.playSound(null, player, SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.5F, 1.0F);
  }

  private static CompoundTag discoveries(Player player) {
    return player.getPersistentData().getCompound(DISCOVERIES);
  }

  private static void creativeContents(BuildCreativeModeTabContentsEvent event) {
    if (event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) {
      event.accept(BREATH_SCROLL);
      event.accept(DISTRACTION_SCROLL);
    }
  }

  private static void clonePlayer(PlayerEvent.Clone event) {
    var original = discoveries(event.getOriginal());
    if (!original.isEmpty())
      event.getEntity().getPersistentData().put(DISCOVERIES, original.copy());
  }

  private static void login(PlayerEvent.PlayerLoggedInEvent event) {
    restoreBreath(event.getEntity());
  }

  private static void respawn(PlayerEvent.PlayerRespawnEvent event) {
    restoreBreath(event.getEntity());
  }

  private static void restoreBreath(Player player) {
    if (player instanceof ServerPlayer && discoveries(player).getBoolean(DEEP_BREATH)) {
      var attribute = player.getAttribute(Attributes.OXYGEN_BONUS);
      if (attribute != null && !attribute.hasModifier(BREATH.id()))
        attribute.addTransientModifier(BREATH);
    }
  }

  private static void message(ServerPlayer player, String key) {
    player.displayClientMessage(Component.translatable(MOD_ID + ".expedition." + key), true);
  }

  private static final class StudyScroll extends Item {
    private final String discovery;

    private StudyScroll(String discovery) {
      super(new Properties().rarity(Rarity.RARE));
      this.discovery = discovery;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
        Level level, Player player, InteractionHand hand) {
      ItemStack stack = player.getItemInHand(hand);
      if (player instanceof ServerPlayer serverPlayer) {
        var data = discoveries(player);
        if (data.getBoolean(discovery)) {
          message(serverPlayer, "already_studied");
          return InteractionResultHolder.fail(stack);
        }
        data.putBoolean(discovery, true);
        player.getPersistentData().put(DISCOVERIES, data);
        restoreBreath(player);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        if (DISTRACTION.equals(discovery)
            && ModList.get().isLoaded("irons_spellbooks")
            && ModList.get().isLoaded("puffish_skills"))
          PreparedAbilities.sync(serverPlayer, false);
        message(serverPlayer, "learned." + discovery);
      }
      return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(
        ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
      tooltip.add(Component.translatable(getDescriptionId() + ".use"));
      tooltip.add(Component.translatable(MOD_ID + ".expedition.study_hint"));
    }
  }
}

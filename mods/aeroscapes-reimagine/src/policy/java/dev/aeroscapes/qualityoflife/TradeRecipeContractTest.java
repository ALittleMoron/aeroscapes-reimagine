package dev.aeroscapes.qualityoflife;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.registry.EmiStackList.ComparisonHashStrategy;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import moe.prwk.emitrades.util.EmiIngredients;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Exercises item lookup and payment stability without starting Minecraft. */
public final class TradeRecipeContractTest {
  private TradeRecipeContractTest() {}

  public static void main(String[] arguments) throws IOException, ReflectiveOperationException {
    LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
    SharedConstants.tryDetectVersion();
    Bootstrap.bootStrap();
    outputLookup();
    paymentSnapshots();
    System.out.println("EMI trade item lookup and payment contracts passed");
  }

  private static void outputLookup() {
    ItemStack item = new ItemStack(Items.FILLED_MAP, 3);
    item.set(DataComponents.CUSTOM_NAME, Component.literal("Trade contract fixture"));
    EmiStack original = EmiStack.of(item);
    EmiIngredient appended = EmiIngredients.appendTooltips(original);
    EmiStack broken = EmiIngredients.asStack(appended);
    require(
        !broken.isEqual(original), "Provider reproduction: wrapped map loses its item identity");

    EmiStack repaired = new TradeTooltipStack(appended);
    require(repaired.isEqual(original), "Repaired output matches the registered map item");
    require(repaired.getKey() == Items.FILLED_MAP, "Registered item key is retained");
    require(repaired.getId().equals(original.getId()), "Registered item ID is retained");
    require(repaired.getAmount() == 3, "Output count is retained");
    require(
        repaired.getComponentChanges().equals(original.getComponentChanges()),
        "Output components are retained");
    require(
        ItemStack.isSameItemSameComponents(repaired.getItemStack(), item),
        "Real item stack is retained");
    require(repaired.getName().equals(original.getName()), "Output display name is retained");

    Map<EmiStack, List<String>> outputs =
        new Object2ObjectOpenCustomHashMap<>(new ComparisonHashStrategy());
    outputs.computeIfAbsent(broken.copy(), ignored -> List.of("broken"));
    require(!outputs.containsKey(original), "EMI output index reproduces the lost map lookup");
    outputs.clear();
    outputs.computeIfAbsent(repaired.copy(), ignored -> List.of("map trade"));
    require(
        outputs.get(original).equals(List.of("map trade")),
        "EMI output index finds the repaired map trade");

    EmiStack copy = repaired.copy();
    copy.setAmount(7);
    require(
        copy.getAmount() == 7 && repaired.getAmount() == 3,
        "Copies have independent output counts");

    ClientTooltipComponent marker =
        new ClientTooltipComponent() {
          @Override
          public int getHeight() {
            return 0;
          }

          @Override
          public int getWidth(Font font) {
            return 0;
          }
        };
    EmiStack decorated =
        new TradeTooltipStack(new TooltipFixture(original.copy(), List.of(marker)));
    require(decorated.getTooltip().equals(List.of(marker)), "Decorated tooltip is retained");
    EmiStack decoratedCopy = decorated.copy();
    require(
        decoratedCopy.getTooltip().equals(List.of(marker)),
        "Decorated tooltip is retained after copying");
    decoratedCopy.setChance(0.25F);
    require(
        decoratedCopy.getChance() == 0.25F && decorated.getChance() == 1.0F,
        "Copies have independent chance metadata");

    EmiStack leather = EmiStack.of(Items.LEATHER_HELMET);
    EmiStack decoratedLeather = new TradeTooltipStack(new TooltipFixture(leather, List.of(marker)));
    outputs.clear();
    outputs.put(decoratedLeather.copy(), List.of("dye trade"));
    require(
        outputs.get(leather).equals(List.of("dye trade")),
        "EMI output index finds the repaired dyed-equipment trade");
  }

  private static void paymentSnapshots() throws IOException, ReflectiveOperationException {
    Constructor<?> constructor = providerConstructor();
    List<EmiIngredient> mutable = new ArrayList<>();
    mutable.add(EmiStack.of(Items.OAK_BOAT));
    mutable.add(EmiStack.EMPTY);
    EmiRecipe original = recipe(constructor, mutable, 0);
    mutable.add(0, EmiStack.of(Items.SPRUCE_BOAT));
    mutable.add(1, EmiStack.EMPTY);
    require(original.getInputs().size() == 4, "Provider reproduction: payment list is shared");
    require(
        original.getInputs().getFirst().getEmiStacks().getFirst().getKey() == Items.SPRUCE_BOAT,
        "Provider reproduction: earlier boat trade changes to the next boat");

    Method hook =
        Class.forName("dev.aeroscapes.mixin.atmosphere.EmiTradesPaymentMixin")
            .getDeclaredMethod("aeroscapes$preservePaymentSlots", List.class);
    hook.setAccessible(true);
    Object snapshot = hook.invoke(null, mutable);
    require(snapshot instanceof List<?>, "Payment hook returns a list");
    EmiRecipe repaired = recipe(constructor, snapshot, 1);
    mutable.add(0, EmiStack.of(Items.BIRCH_BOAT));
    mutable.add(1, EmiStack.EMPTY);
    require(repaired.getInputs().size() == 2, "Repaired trade exposes two payment slots");
    require(
        repaired.getInputs().getFirst().getEmiStacks().getFirst().getKey() == Items.SPRUCE_BOAT,
        "Repaired earlier trade retains its own boat");
    require(
        repaired.getInputs().get(1).isEmpty(),
        "Repaired single-payment trade retains its empty second slot");
  }

  private static EmiRecipe recipe(Constructor<?> constructor, Object inputs, int id)
      throws ReflectiveOperationException {
    return (EmiRecipe)
        constructor.newInstance(
            inputs,
            List.of(EmiStack.of(Items.EMERALD)),
            null,
            VillagerProfession.FISHERMAN,
            4,
            Optional.of(30),
            Optional.of(12),
            id);
  }

  private static Constructor<?> providerConstructor() throws IOException, NoSuchMethodException {
    String name = "moe/prwk/emitrades/recipes/TradeEntry";
    ClassNode node = new ClassNode();
    try (var input = TradeRecipeContractTest.class.getResourceAsStream("/" + name + ".class")) {
      if (input == null) throw new IOException("Missing exact EMI Trades provider class");
      new ClassReader(input).accept(node, 0);
    }
    int removed = 0;
    for (var method : node.methods) {
      if (!method.name.equals("<init>")) continue;
      for (AbstractInsnNode instruction : method.instructions.toArray()) {
        if (instruction instanceof MethodInsnNode call
            && call.owner.equals("net/minecraft/client/Minecraft")
            && call.name.equals("getInstance")
            && call.getNext() instanceof FieldInsnNode field
            && field.owner.equals("net/minecraft/client/Minecraft")
            && field.name.equals("font")) {
          // The tested payment constructor does not use its unrelated display-font field.
          method.instructions.insertBefore(call, new InsnNode(Opcodes.ACONST_NULL));
          method.instructions.remove(field);
          method.instructions.remove(call);
          removed++;
        }
      }
    }
    require(removed == 1, "Fixture replaces only the unrelated client-font initialization");
    ClassWriter writer = new ClassWriter(0);
    node.accept(writer);
    Class<?> type = new FixtureLoader().define(name.replace('/', '.'), writer.toByteArray());
    return type.getConstructor(
        List.class,
        List.class,
        AbstractVillager.class,
        VillagerProfession.class,
        int.class,
        Optional.class,
        Optional.class,
        int.class);
  }

  private static void require(boolean value, String description) {
    if (!value) throw new AssertionError(description);
  }

  private record TooltipFixture(EmiStack stack, List<ClientTooltipComponent> tooltips)
      implements EmiIngredient {
    @Override
    public List<EmiStack> getEmiStacks() {
      return List.of(stack);
    }

    @Override
    public EmiIngredient copy() {
      return new TooltipFixture(stack.copy(), tooltips);
    }

    @Override
    public long getAmount() {
      return stack.getAmount();
    }

    @Override
    public EmiIngredient setAmount(long amount) {
      stack.setAmount(amount);
      return this;
    }

    @Override
    public float getChance() {
      return stack.getChance();
    }

    @Override
    public EmiIngredient setChance(float chance) {
      stack.setChance(chance);
      return this;
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, float delta, int flags) {
      stack.render(graphics, x, y, delta, flags);
    }

    @Override
    public List<ClientTooltipComponent> getTooltip() {
      return new ArrayList<>(tooltips);
    }
  }

  private static final class FixtureLoader extends ClassLoader {
    private Class<?> define(String name, byte[] bytes) {
      return defineClass(name, bytes, 0, bytes.length);
    }
  }
}

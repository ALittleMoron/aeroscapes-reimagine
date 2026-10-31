package dev.aeroscapes.qualityoflife;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Keeps EMI item identity when the provider appends map or dye tooltips. */
public final class TradeTooltipStack extends EmiStack {
  private final EmiIngredient ingredient;
  private final EmiStack stack;

  public TradeTooltipStack(EmiIngredient ingredient) {
    this.ingredient = ingredient;
    List<EmiStack> stacks = ingredient.getEmiStacks();
    if (stacks.size() != 1) {
      throw new IllegalArgumentException("A trade output must contain exactly one stack");
    }
    stack = stacks.getFirst();
  }

  @Override
  public EmiStack copy() {
    TradeTooltipStack copy = new TradeTooltipStack(ingredient.copy());
    copy.comparison = comparison;
    copy.setRemainder(getRemainder());
    return copy;
  }

  @Override
  public boolean isEmpty() {
    return ingredient.isEmpty();
  }

  @Override
  public Object getKey() {
    return stack.getKey();
  }

  @Override
  public ResourceLocation getId() {
    return stack.getId();
  }

  @Override
  public DataComponentPatch getComponentChanges() {
    return stack.getComponentChanges();
  }

  @Override
  public ItemStack getItemStack() {
    return stack.getItemStack();
  }

  @Override
  public Component getName() {
    return stack.getName();
  }

  @Override
  public List<Component> getTooltipText() {
    return stack.getTooltipText();
  }

  @Override
  public List<ClientTooltipComponent> getTooltip() {
    return ingredient.getTooltip();
  }

  @Override
  public long getAmount() {
    return ingredient.getAmount();
  }

  @Override
  public EmiStack setAmount(long amount) {
    ingredient.setAmount(amount);
    return this;
  }

  @Override
  public float getChance() {
    return ingredient.getChance();
  }

  @Override
  public EmiStack setChance(float chance) {
    ingredient.setChance(chance);
    return this;
  }

  @Override
  public void render(GuiGraphics graphics, int x, int y, float delta, int flags) {
    ingredient.render(graphics, x, y, delta, flags);
  }
}

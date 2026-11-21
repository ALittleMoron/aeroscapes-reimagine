package dev.aeroscapes.progression;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.puffish.skillsmod.SkillsMod;

/** Keeps the practice-earned source intact and applies a separate, recoverable deduction. */
public final class RefundPenalty {
  private static final String DATA = "aeroscapes_reimagine_talent_refunds";
  private static final ResourceLocation SOURCE =
      ResourceLocation.parse("aeroscapes_reimagine:redistribution");
  private static final int PRACTICE_PER_POINT = 3000;

  private RefundPenalty() {}

  static int tenths(ServerPlayer player, String pool) {
    return player.getPersistentData().getCompound(DATA).getCompound(pool).getInt("tenths");
  }

  static int untilNext(ServerPlayer player, String pool) {
    int remaining =
        player.getPersistentData().getCompound(DATA).getCompound(pool).getInt("practice_due");
    return remaining > 0 ? 1 + (remaining - 1) % PRACTICE_PER_POINT : 0;
  }

  static void charge(ServerPlayer player, TalentRefunds.Quote quote) {
    var data = player.getPersistentData().getCompound(DATA).copy();
    var pool = data.getCompound(quote.pool());
    pool.putInt("tenths", quote.remainder());
    int due = pool.getInt("practice_due") + quote.loss() * PRACTICE_PER_POINT;
    setDeduction(player, quote.pool(), due);
    pool.putInt("practice_due", due);
    data.put(quote.pool(), pool);
    player.getPersistentData().put(DATA, data);
  }

  public static void practice(ServerPlayer player, String skill, long earned) {
    if (earned <= 0 || !player.getPersistentData().contains(DATA, Tag.TAG_COMPOUND)) return;
    String pool;
    if ("magic".equals(skill)) pool = "magic";
    else if (ProgressionBridge.COMBAT_SKILLS.contains(skill)) pool = "combat";
    else if (ProgressionBridge.PROFESSION_SKILLS.contains(skill)) pool = "profession";
    else return;
    CompoundTag state = player.getPersistentData().getCompound(DATA).getCompound(pool);
    int due = state.getInt("practice_due");
    if (due > 0) {
      int remaining = (int) Math.max(0L, due - earned);
      state.putInt("practice_due", remaining);
      if (Math.ceilDiv(remaining, PRACTICE_PER_POINT) < Math.ceilDiv(due, PRACTICE_PER_POINT))
        setDeduction(player, pool, remaining);
    }
  }

  static void sync(ServerPlayer player, String pool) {
    int due = player.getPersistentData().getCompound(DATA).getCompound(pool).getInt("practice_due");
    setDeduction(player, pool, due);
  }

  private static void setDeduction(ServerPlayer player, String pool, int due) {
    var category = ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", pool);
    int points = -Math.ceilDiv(due, PRACTICE_PER_POINT);
    var provider = SkillsMod.getInstance();
    if (provider.getPoints(player, category, SOURCE).orElse(0) != points)
      provider.setPoints(player, category, SOURCE, points, true);
  }

  static void clonePlayer(PlayerEvent.Clone event) {
    var original = event.getOriginal().getPersistentData();
    if (original.contains(DATA, Tag.TAG_COMPOUND))
      event.getEntity().getPersistentData().put(DATA, original.getCompound(DATA).copy());
  }
}

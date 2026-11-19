package dev.aeroscapes.magic;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.capabilities.magic.TargetEntityCastData;
import io.redspace.ironsspellbooks.entity.mobs.IMagicSummon;
import io.redspace.ironsspellbooks.entity.spells.root.RootEntity;
import io.redspace.ironsspellbooks.network.SyncManaPacket;
import io.redspace.ironsspellbooks.spells.nature.RootSpell;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class BindingPact {
  private static final String PAYMENT = "aeroscapes_reimagine_binding_pact";

  private BindingPact() {}

  public static void register() {
    NeoForge.EVENT_BUS.addListener(BindingPact::summonHit);
  }

  public record PaidRoot(LivingEntity target, Entity previousVehicle, float refund) {
    public void bind(ServerPlayer owner) {
      if (target.getVehicle() instanceof RootEntity root
          && root != previousVehicle
          && root.isAddedToLevel()
          && root.getOwner() == owner
          && root.getTarget() == target) {
        var payment = new CompoundTag();
        payment.putUUID("target", target.getUUID());
        payment.putFloat("refund", refund);
        root.getPersistentData().put(PAYMENT, payment);
      }
    }
  }

  public static PaidRoot paidRoot(
      AbstractSpell spell, ServerPlayer owner, MagicData magic, float paid) {
    if (!(spell instanceof RootSpell)
        || !(paid > 0)
        || !(magic.getAdditionalCastData() instanceof TargetEntityCastData cast)) return null;
    LivingEntity target = cast.getTarget(owner.serverLevel());
    if (target == null) return null;
    double share =
        MagicTalents.bonus(SpellKnowledge.unlocked(owner), "conjuration", "binding_refund");
    return share > 0 ? new PaidRoot(target, target.getVehicle(), (float) (paid * share)) : null;
  }

  private static void summonHit(LivingDamageEvent.Post event) {
    LivingEntity target = event.getEntity();
    if (event.getNewDamage() <= 0
        || !(target.getVehicle() instanceof RootEntity root)
        || !root.isAlive()
        || !root.getPersistentData().contains(PAYMENT, Tag.TAG_COMPOUND)
        || !(root.getOwner() instanceof ServerPlayer owner)) return;
    CompoundTag payment = root.getPersistentData().getCompound(PAYMENT);
    if (!payment.hasUUID("target")
        || !target.getUUID().equals(payment.getUUID("target"))
        || summonOwner(event.getSource()) != owner) return;
    root.getPersistentData().remove(PAYMENT);
    var magic = MagicData.getPlayerMagicData(owner);
    magic.addMana(payment.getFloat("refund"));
    PacketDistributor.sendToPlayer(owner, new SyncManaPacket(magic));
  }

  private static Entity summonOwner(DamageSource source) {
    if (source.getDirectEntity() instanceof IMagicSummon summon) return summon.getSummoner();
    if (source.getDirectEntity() instanceof Projectile projectile
        && projectile.getOwner() instanceof IMagicSummon summon) return summon.getSummoner();
    return source.getEntity() instanceof IMagicSummon summon ? summon.getSummoner() : null;
  }
}

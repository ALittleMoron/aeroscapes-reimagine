package dev.aeroscapes.sky.fauna;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.entity.PartEntity;

class AerialHitPart extends PartEntity<FlyingMob> {
  private boolean active = true;

  AerialHitPart(FlyingMob parent) {
    super(parent);
  }

  void place(AABB box) {
    active = true;
    setOldPosAndRot();
    setPos(box.getBottomCenter());
    setBoundingBox(box);
  }

  void hide() {
    if (!active) return;
    active = false;
    var feet = getParent().position();
    setPos(feet);
    setBoundingBox(new AABB(feet, feet));
  }

  @Override
  public boolean hurt(DamageSource source, float amount) {
    return isPickable() && getParent().hurt(source, amount);
  }

  @Override
  public boolean isPickable() {
    return active && getParent().isAlive();
  }

  @Override
  public boolean is(Entity entity) {
    return this == entity || getParent() == entity;
  }

  @Override
  public boolean shouldBeSaved() {
    return false;
  }

  @Override
  protected void defineSynchedData(SynchedEntityData.Builder builder) {}

  @Override
  protected void readAdditionalSaveData(CompoundTag tag) {}

  @Override
  protected void addAdditionalSaveData(CompoundTag tag) {}
}

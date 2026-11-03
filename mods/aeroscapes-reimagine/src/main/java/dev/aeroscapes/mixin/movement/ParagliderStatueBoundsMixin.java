package dev.aeroscapes.mixin.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tictim.paraglider.contents.worldgen.BaseHornedStatuePiece;

@Mixin(value = BaseHornedStatuePiece.class, remap = false)
public abstract class ParagliderStatueBoundsMixin extends TemplateStructurePiece {
  protected ParagliderStatueBoundsMixin(
      StructurePieceType type,
      int depth,
      StructureTemplateManager templates,
      ResourceLocation location,
      String name,
      StructurePlaceSettings settings,
      BlockPos position) {
    super(type, depth, templates, location, name, settings, position);
  }

  @Inject(
      method =
          "rot(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Rotation;Z)Ltictim/paraglider/contents/worldgen/BaseHornedStatuePiece;",
      at = @At("RETURN"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Native placement otherwise updates these after prefit.
  private void aeroscapes$updateRotatedBounds(
      BlockPos pivot,
      Rotation rotation,
      boolean savePivot,
      CallbackInfoReturnable<BaseHornedStatuePiece> callback) {
    boundingBox = template.getBoundingBox(placeSettings, templatePosition);
  }
}

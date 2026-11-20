package dev.aeroscapes.mixin.progression;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.puffish.skillsmod.SkillsMod;
import net.puffish.skillsmod.config.CategoryConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = SkillsMod.class, remap = false)
public interface TalentCategoryAccess {
  @Invoker("getCategory")
  Optional<CategoryConfig> aeroscapes$category(ResourceLocation id);
}

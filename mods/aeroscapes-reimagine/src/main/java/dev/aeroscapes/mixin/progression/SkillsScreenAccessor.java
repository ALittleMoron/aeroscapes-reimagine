package dev.aeroscapes.mixin.progression;

import net.puffish.skillsmod.client.data.ClientSkillScreenData;
import net.puffish.skillsmod.client.gui.SkillsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = SkillsScreen.class, remap = false)
public interface SkillsScreenAccessor {
  @Accessor("data")
  ClientSkillScreenData aeroscapes$screenData();
}

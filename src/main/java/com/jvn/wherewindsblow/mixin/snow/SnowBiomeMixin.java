package com.jvn.wherewindsblow.mixin.snow;

import com.jvn.wherewindsblow.block.SnowLogging;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Biome.class)
abstract class SnowBiomeMixin {
    @Inject(method = "shouldSnow", at = @At("RETURN"), cancellable = true, require = 1)
    private void snowThroughFoliage(LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() && !((Biome) (Object) this).warmEnoughToRain(pos) && SnowLogging.shouldSnow(level, pos)) {
            cir.setReturnValue(true);
        }
    }
}

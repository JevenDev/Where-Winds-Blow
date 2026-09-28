package com.jvn.wherewindsblow.mixin.snow;

import com.jvn.wherewindsblow.block.SnowLogging;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SnowyDirtBlock.class)
abstract class SnowyDirtBlockMixin {
    @Inject(method = "isSnowySetting", at = @At("RETURN"), cancellable = true, require = 1)
    private static void snowyFoliageSoil(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (SnowLogging.layers(state) > 0) {
            cir.setReturnValue(true);
        }
    }
}

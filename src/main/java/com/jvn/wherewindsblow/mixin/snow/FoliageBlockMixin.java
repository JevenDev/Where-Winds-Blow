package com.jvn.wherewindsblow.mixin.snow;

import com.jvn.wherewindsblow.block.SnowLogging;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
abstract class FoliageBlockMixin {
    @Inject(method = "hasDynamicShape", at = @At("RETURN"), cancellable = true, require = 1)
    private void snowCollisionProperties(CallbackInfoReturnable<Boolean> cir) {
        if (SnowLogging.supports((Block) (Object) this)) {
            cir.setReturnValue(true);
        }
    }
}

package com.jvn.wherewindsblow.mixin.snow;

import com.jvn.wherewindsblow.block.SnowLogging;
import java.util.Map;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StateDefinition.Builder.class)
abstract class FoliageStateDefinitionMixin {
    @Shadow @Final private Object owner;
    @Shadow @Final private Map<String, Property<?>> properties;
    @Shadow public abstract StateDefinition.Builder<?, ?> add(Property<?>... properties);

    @Inject(method = "create", at = @At("HEAD"), require = 1)
    private void addSnowLayers(CallbackInfoReturnable<StateDefinition<?, ?>> cir) {
        if (owner instanceof Block block && SnowLogging.supports(block) && !properties.containsKey(SnowLogging.LAYERS.getName())) {
            add(SnowLogging.LAYERS);
        }
    }
}

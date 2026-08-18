package com.jvn.wherewindsblow.mixin.client;

import com.jvn.wherewindsblow.client.lantern.SwingingLanternAssemblyRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Inject(method = "sendBlockUpdated", at = @At("HEAD"))
    private void wherewindsblow$invalidateLanternChunk(
            BlockPos pos,
            BlockState oldState,
            BlockState newState,
            int flags,
            CallbackInfo ci
    ) {
        SwingingLanternAssemblyRenderer.invalidateChunk(pos);
    }

    @Inject(method = "unload", at = @At("HEAD"))
    private void wherewindsblow$discardLanternChunk(LevelChunk chunk, CallbackInfo ci) {
        SwingingLanternAssemblyRenderer.invalidateChunk(chunk);
    }
}

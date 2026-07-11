package com.jvn.wherewindsblow.mixin.client;

import com.jvn.wherewindsblow.client.foliage.ResponsiveFoliagePhysics;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleExplosion", at = @At("TAIL"))
    private void wherewindsblow$addExplosionPressureWave(ClientboundExplodePacket packet, CallbackInfo ci) {
        ResponsiveFoliagePhysics.addExplosion(packet.getX(), packet.getY(), packet.getZ(), packet.getPower());
    }
}

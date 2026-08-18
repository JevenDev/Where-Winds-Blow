package com.jvn.wherewindsblow.mixin.client;

import com.jvn.wherewindsblow.client.foliage.ResponsiveFoliagePhysics;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Unique
    private static boolean wherewindsblow$warnedAboutInvalidEntityData;

    @Inject(method = "handleExplosion", at = @At("TAIL"))
    private void wherewindsblow$addExplosionPressureWave(ClientboundExplodePacket packet, CallbackInfo ci) {
        ResponsiveFoliagePhysics.addExplosion(packet.getX(), packet.getY(), packet.getZ(), packet.getPower());
    }

    @Redirect(
            method = "handleSetEntityData",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/syncher/SynchedEntityData;assignValues(Ljava/util/List;)V"
            )
    )
    private void wherewindsblow$applyCompatibleEntityData(
            SynchedEntityData entityData,
            List<SynchedEntityData.DataValue<?>> values
    ) {
        SynchedEntityData.DataItem<?>[] items =
                ((SynchedEntityDataAccessorMixin) (Object) entityData).wherewindsblow$getItemsById();
        List<SynchedEntityData.DataValue<?>> compatibleValues = new ArrayList<>(values.size());

        for (SynchedEntityData.DataValue<?> value : values) {
            int id = value.id();
            SynchedEntityData.DataItem<?> item = id >= 0 && id < items.length ? items[id] : null;
            if (item != null && item.getAccessor().serializer().equals(value.serializer())) {
                compatibleValues.add(value);
            } else {
                if (!wherewindsblow$warnedAboutInvalidEntityData) {
                    wherewindsblow$warnedAboutInvalidEntityData = true;
                    com.jvn.wherewindsblow.WhereWindsBlow.LOGGER.warn(
                            "Ignored incompatible entity metadata field {} from the server; "
                                    + "this is usually caused by a proxy or server protocol translation mismatch. "
                                    + "Further warnings will be suppressed for this game session.",
                            id
                    );
                }
            }
        }

        if (!compatibleValues.isEmpty()) {
            entityData.assignValues(compatibleValues);
        }
    }
}

package com.jvn.wherewindsblow.mixin.client;

import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SynchedEntityData.class)
public interface SynchedEntityDataAccessorMixin {
    @Accessor("itemsById")
    SynchedEntityData.DataItem<?>[] wherewindsblow$getItemsById();
}

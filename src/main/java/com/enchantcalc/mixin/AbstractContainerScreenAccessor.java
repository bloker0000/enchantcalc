package com.enchantcalc.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Window position and size of a container screen. These fields have the same names in every supported version. */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("leftPos")
    int enchantcalc$getLeftPos();

    @Accessor("leftPos")
    void enchantcalc$setLeftPos(int leftPos);

    @Accessor("topPos")
    int enchantcalc$getTopPos();

    @Accessor("imageWidth")
    int enchantcalc$getImageWidth();

    @Accessor("imageHeight")
    int enchantcalc$getImageHeight();
}

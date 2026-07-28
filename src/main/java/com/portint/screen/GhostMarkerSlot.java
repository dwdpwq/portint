package com.portint.screen;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** AE2-style ghost slot. Accepts any item, max 1. Used as filter marker slots. */
public class GhostMarkerSlot extends Slot {
    public GhostMarkerSlot(Container inv, int idx, int x, int y) {
        super(inv, idx, x, y);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return true;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }
}

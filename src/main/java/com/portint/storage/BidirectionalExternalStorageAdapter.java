package com.portint.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;

/**
 * Bidirectional MEStorage adapter that allows AE2 to both read from and write to
 * an external IItemHandler (e.g. a chest, tank, or machine inventory).
 * Unlike ExternalStorageAdapter, this does not enforce direction — both insert
 * and extract are always available, making the bound inventory fully visible
 * and operable from the AE network.
 */
public class BidirectionalExternalStorageAdapter implements MEStorage {

    private final IItemHandler handler;
    private final Component description;

    public BidirectionalExternalStorageAdapter(IItemHandler handler, Component description) {
        this.handler = handler;
        this.description = description;
    }

    @Override
    public long insert(@Nonnull AEKey what, long amount, @Nonnull Actionable mode,
                       @Nonnull IActionSource source) {
        if (!(what instanceof AEItemKey itemKey)) return 0;
        if (amount <= 0) return 0;

        int maxStack = itemKey.toStack(1).getMaxStackSize();
        long inserted = 0;
        long remaining = amount;

        while (remaining > 0) {
            int batch = (int) Math.min(remaining, maxStack);
            ItemStack leftover = ItemHandlerHelper.insertItem(handler,
                    itemKey.toStack(batch), mode == Actionable.SIMULATE);
            int pushed = batch - (leftover.isEmpty() ? 0 : leftover.getCount());
            if (pushed == 0) break;
            inserted += pushed;
            remaining -= pushed;
        }
        return inserted;
    }

    @Override
    public long extract(@Nonnull AEKey what, long amount, @Nonnull Actionable mode,
                        @Nonnull IActionSource source) {
        if (!(what instanceof AEItemKey itemKey)) return 0;
        if (amount <= 0) return 0;

        long extracted = 0;
        long remaining = amount;

        for (int slot = 0; slot < handler.getSlots() && remaining > 0; slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            AEItemKey slotKey = AEItemKey.of(stack);
            if (slotKey == null || !slotKey.equals(itemKey)) continue;

            int batch = (int) Math.min(remaining, Math.min(stack.getCount(), stack.getMaxStackSize()));
            ItemStack pulled = handler.extractItem(slot, batch, mode == Actionable.SIMULATE);
            if (pulled.isEmpty()) continue;
            extracted += pulled.getCount();
            remaining -= pulled.getCount();
        }
        return extracted;
    }

    @Override
    public void getAvailableStacks(@Nonnull KeyCounter out) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            AEItemKey key = AEItemKey.of(stack);
            if (key != null) {
                out.add(key, stack.getCount());
            }
        }
    }

    @Override
    public boolean isPreferredStorageFor(@Nonnull AEKey what, @Nonnull IActionSource source) {
        return true; // Always available as storage target
    }

    @Override
    @Nonnull
    public Component getDescription() {
        return description;
    }
}

package com.portint;

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
import java.util.Set;

/**
 * Adapts an external IItemHandler as AE2 MEStorage.
 * Respects per-column direction: input mode → extract only, output mode → insert only.
 */
public class ExternalStorageAdapter implements MEStorage {

    private final IItemHandler handler;
    private final boolean isOutput;   // true = output(↑): AE2→target, insert only
    private final Set<AEItemKey> markers;
    private final boolean isWhitelist;

    public ExternalStorageAdapter(IItemHandler handler, boolean isOutput,
                                   Set<AEItemKey> markers, boolean isWhitelist) {
        this.handler = handler;
        this.isOutput = isOutput;
        this.markers = markers;
        this.isWhitelist = isWhitelist;
    }

    private boolean passesFilter(AEItemKey key) {
        if (markers.isEmpty()) return true;
        boolean found = markers.contains(key);
        return isWhitelist ? found : !found;
    }

    @Override
    public long insert(@Nonnull AEKey what, long amount, @Nonnull Actionable mode,
                       @Nonnull IActionSource source) {
        com.portint.PortableInterface.LOGGER.info("INSERT: key={} amount={} mode={} isOutput={}",
            what, amount, mode, isOutput);
        if (!isOutput) return 0;
        if (!(what instanceof AEItemKey itemKey)) return 0;
        if (!passesFilter(itemKey)) return 0;
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
        com.portint.PortableInterface.LOGGER.info("EXTRACT: key={} amount={} mode={} isOutput={}",
            what, amount, mode, isOutput);
        if (isOutput) return 0;
        if (!(what instanceof AEItemKey itemKey)) return 0;
        if (!passesFilter(itemKey)) return 0;
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
        int total = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            AEItemKey key = AEItemKey.of(stack);
            if (key != null && passesFilter(key)) {
                out.add(key, stack.getCount());
                total += stack.getCount();
            }
        }
        com.portint.PortableInterface.LOGGER.info("STACKS: isOutput={} items={} total={}", isOutput, handler.getSlots(), total);
    }

    @Override
    public boolean isPreferredStorageFor(@Nonnull AEKey what, @Nonnull IActionSource source) {
        com.portint.PortableInterface.LOGGER.info("PREFER: key={} isOutput={}", what, isOutput);
        if (!(what instanceof AEItemKey itemKey)) return false;
        if (!passesFilter(itemKey)) return false;
        return isOutput;
    }

    @Override
    @Nonnull
    public Component getDescription() {
        return Component.literal("External Storage @ " + handler.getSlots() + " slots");
    }
}

package com.portint.storage;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

/**
 * 将多个 IItemHandler 合并为一个整体容器（如大箱子的两个半箱 → 54 格）。
 * 对外表现为单一连续槽位空间，槽位索引按 handler 顺序偏移映射。
 */
public class CombinedItemHandler implements IItemHandler {

    private final IItemHandler[] handlers;
    private final int totalSlots;

    public CombinedItemHandler(List<IItemHandler> handlers) {
        this.handlers = handlers.toArray(new IItemHandler[0]);
        int sum = 0;
        for (IItemHandler h : handlers) {
            sum += h.getSlots();
        }
        this.totalSlots = sum;
    }

    public CombinedItemHandler(IItemHandler... handlers) {
        this.handlers = handlers;
        int sum = 0;
        for (IItemHandler h : handlers) {
            sum += h.getSlots();
        }
        this.totalSlots = sum;
    }

    @Override
    public int getSlots() {
        return totalSlots;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        SlotRef ref = resolve(slot);
        return ref == null ? ItemStack.EMPTY : ref.handler.getStackInSlot(ref.index);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        SlotRef ref = resolve(slot);
        return ref == null ? stack : ref.handler.insertItem(ref.index, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        SlotRef ref = resolve(slot);
        return ref == null ? ItemStack.EMPTY : ref.handler.extractItem(ref.index, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        SlotRef ref = resolve(slot);
        return ref == null ? 0 : ref.handler.getSlotLimit(ref.index);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        SlotRef ref = resolve(slot);
        return ref != null && ref.handler.isItemValid(ref.index, stack);
    }

    private SlotRef resolve(int slot) {
        if (slot < 0 || slot >= totalSlots) {
            return null;
        }
        int offset = 0;
        for (IItemHandler h : handlers) {
            int n = h.getSlots();
            if (slot < offset + n) {
                return new SlotRef(h, slot - offset);
            }
            offset += n;
        }
        return null;
    }

    private record SlotRef(IItemHandler handler, int index) {}
}

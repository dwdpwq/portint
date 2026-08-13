package com.portint.screen;

import com.portint.block.ExternalStorageBlockEntity;
import com.portint.item.ModItems;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class ExternalStorageMenu extends AbstractContainerMenu {

    static final int UPGRADE_START = 0;   // 0-1
    static final int BINDING_START = 2;   // 2-10
    static final int PLAYER_START  = 11;  // 11-46

    static final int IMAGE_WIDTH  = 176;
    static final int IMAGE_HEIGHT = 166;

    final ExternalStorageBlockEntity tile;

    public ExternalStorageMenu(int windowId, Inventory playerInv, ExternalStorageBlockEntity tile) {
        super(ModMenuTypes.EXTERNAL_STORAGE_MENU.get(), windowId);
        this.tile = tile;

        var upgradeInv = tile.getUpgradeInventory();
        var bindingInv = tile.getBindingInventory();

        // Upgrade slots (capacity_card only)
        this.addSlot(new CapacityCardSlot(upgradeInv, 0, 134, 18));
        this.addSlot(new CapacityCardSlot(upgradeInv, 1, 134, 40));

        // 9 binding card slots
        for (int i = 0; i < 9; i++) {
            this.addSlot(new BindingCardSlot(bindingInv, i, 8 + i * 18, 35));
        }

        // Player inventory
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        // Hotbar
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
        }
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (clickType == ClickType.QUICK_MOVE) {
            Slot slot = slotId >= 0 && slotId < this.slots.size() ? this.slots.get(slotId) : null;
            if (slot != null && slot.hasItem() && slotId >= PLAYER_START) {
                ItemStack stack = slot.getItem().copy();

                // Route binding cards to binding slots
                if (stack.is(ModItems.BINDING_CARD.get())) {
                    if (this.moveItemStackTo(stack, BINDING_START, PLAYER_START, false)) {
                        slot.set(stack);
                    }
                    return;
                }

                // Route capacity cards to upgrade slots
                if (stack.is(ModItems.CAPACITY_CARD.get())) {
                    Slot up0 = this.slots.get(UPGRADE_START);
                    Slot up1 = this.slots.get(UPGRADE_START + 1);
                    if (!up0.hasItem()) {
                        up0.set(stack.split(1));
                    } else if (!up1.hasItem()) {
                        up1.set(stack.split(1));
                    }
                    slot.set(stack);
                    slot.setChanged();
                    return;
                }

                // Fallback: hotbar ↔ main inventory
                if (slotId >= PLAYER_START + 27) {
                    this.moveItemStackTo(stack, PLAYER_START, PLAYER_START + 27, false);
                } else {
                    this.moveItemStackTo(stack, PLAYER_START + 27, PLAYER_START + 36, false);
                }
                slot.set(stack);
                return;
            }
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return result;

        ItemStack stack = slot.getItem();
        result = stack.copy();

        if (index >= BINDING_START && index < PLAYER_START) {
            if (!this.moveItemStackTo(stack, PLAYER_START, PLAYER_START + 36, true))
                return ItemStack.EMPTY;
        } else if (index >= UPGRADE_START && index < BINDING_START) {
            if (!this.moveItemStackTo(stack, PLAYER_START, PLAYER_START + 36, true))
                return ItemStack.EMPTY;
        } else {
            if (!this.moveItemStackTo(stack, UPGRADE_START, BINDING_START, false)
                && !this.moveItemStackTo(stack, BINDING_START, PLAYER_START, false))
                return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        if (stack.getCount() == result.getCount())
            return ItemStack.EMPTY;

        slot.onTake(player, stack);
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return tile.getBindingInventory().stillValid(player);
    }

    // ──── custom slots ────

    static class BindingCardSlot extends Slot {
        public BindingCardSlot(net.minecraft.world.Container inv, int idx, int x, int y) {
            super(inv, idx, x, y);
        }
        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(ModItems.BINDING_CARD.get());
        }
        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    static class CapacityCardSlot extends Slot {
        public CapacityCardSlot(net.minecraft.world.Container inv, int idx, int x, int y) {
            super(inv, idx, x, y);
        }
        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(ModItems.CAPACITY_CARD.get());
        }
        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}

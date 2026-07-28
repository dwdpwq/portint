package com.portint.screen;

import com.portint.block.InterfaceBlockEntity;
import com.portint.item.ModItems;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public class InterfaceMenu extends AbstractContainerMenu {

    // Slot indices
    static final int UPGRADE_START  = 0;   // 0-1
    static final int BINDING_START  = 2;   // 2-10
    static final int MARKER_START   = 11;  // 11-253 (243 ghost markers)
    static final int PLAYER_START   = 254; // 254-289

    static final int IMAGE_WIDTH  = 256;
    static final int IMAGE_HEIGHT = 207;

    final InterfaceBlockEntity tile;
    final ContainerData data;

    public InterfaceMenu(int windowId, Inventory playerInv, InterfaceBlockEntity tile) {
        super(ModMenuTypes.INTERFACE_MENU.get(), windowId);
        this.tile = tile;
        this.data = tile.dataAccess;
        addDataSlots(data);

        Container upgradeInv = tile.getUpgradeInventory();
        Container bindingInv = tile.getBindingInventory();
        Container markerInv  = tile.getMarkerInventory();

        // Upgrade slots — positioned like AE2 vanilla ME Interface (right-aligned, vertical)
        this.addSlot(new UpgradeSlot(upgradeInv, 0, 175, 6));  // accepts both cards
        this.addSlot(new UpgradeSlot(upgradeInv, 1, 175, 22)); // accepts both cards

        // 9 binding card slots
        this.addSlot(new BindingCardSlot(bindingInv, 0, 8, 53));
        this.addSlot(new BindingCardSlot(bindingInv, 1, 26, 53));
        this.addSlot(new BindingCardSlot(bindingInv, 2, 44, 53));
        this.addSlot(new BindingCardSlot(bindingInv, 3, 62, 53));
        this.addSlot(new BindingCardSlot(bindingInv, 4, 80, 53));
        this.addSlot(new BindingCardSlot(bindingInv, 5, 98, 53));
        this.addSlot(new BindingCardSlot(bindingInv, 6, 116, 53));
        this.addSlot(new BindingCardSlot(bindingInv, 7, 134, 53));
        this.addSlot(new BindingCardSlot(bindingInv, 8, 152, 53));

        // 243 ghost marker slots (hidden at -2000, rendered in FilterScreen)
        for (int col = 0; col < 9; col++) {
            for (int i = 0; i < 27; i++) {
                this.addSlot(new GhostMarkerSlot(markerInv, col * 27 + i, -2000, -2000));
            }
        }

        // Player inventory 3x9
        this.addSlot(new Slot(playerInv, 9,  8,  123));
        this.addSlot(new Slot(playerInv, 10, 26, 123));
        this.addSlot(new Slot(playerInv, 11, 44, 123));
        this.addSlot(new Slot(playerInv, 12, 62, 123));
        this.addSlot(new Slot(playerInv, 13, 80, 123));
        this.addSlot(new Slot(playerInv, 14, 98, 123));
        this.addSlot(new Slot(playerInv, 15, 116, 123));
        this.addSlot(new Slot(playerInv, 16, 134, 123));
        this.addSlot(new Slot(playerInv, 17, 152, 123));
        this.addSlot(new Slot(playerInv, 18, 8,  141));
        this.addSlot(new Slot(playerInv, 19, 26, 141));
        this.addSlot(new Slot(playerInv, 20, 44, 141));
        this.addSlot(new Slot(playerInv, 21, 62, 141));
        this.addSlot(new Slot(playerInv, 22, 80, 141));
        this.addSlot(new Slot(playerInv, 23, 98, 141));
        this.addSlot(new Slot(playerInv, 24, 116, 141));
        this.addSlot(new Slot(playerInv, 25, 134, 141));
        this.addSlot(new Slot(playerInv, 26, 152, 141));
        this.addSlot(new Slot(playerInv, 27, 8,  159));
        this.addSlot(new Slot(playerInv, 28, 26, 159));
        this.addSlot(new Slot(playerInv, 29, 44, 159));
        this.addSlot(new Slot(playerInv, 30, 62, 159));
        this.addSlot(new Slot(playerInv, 31, 80, 159));
        this.addSlot(new Slot(playerInv, 32, 98, 159));
        this.addSlot(new Slot(playerInv, 33, 116, 159));
        this.addSlot(new Slot(playerInv, 34, 134, 159));
        this.addSlot(new Slot(playerInv, 35, 152, 159));
        // Hotbar
        this.addSlot(new Slot(playerInv, 0, 8,  181));
        this.addSlot(new Slot(playerInv, 1, 26, 181));
        this.addSlot(new Slot(playerInv, 2, 44, 181));
        this.addSlot(new Slot(playerInv, 3, 62, 181));
        this.addSlot(new Slot(playerInv, 4, 80, 181));
        this.addSlot(new Slot(playerInv, 5, 98, 181));
        this.addSlot(new Slot(playerInv, 6, 116, 181));
        this.addSlot(new Slot(playerInv, 7, 134, 181));
        this.addSlot(new Slot(playerInv, 8, 152, 181));
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (clickType == ClickType.QUICK_MOVE) {
            Slot slot = slotId >= 0 && slotId < this.slots.size() ? this.slots.get(slotId) : null;
            if (slot != null && slot.hasItem() && slotId >= PLAYER_START) {
                ItemStack stack = slot.getItem().copy();
                // ── Route special cards to their correct container slots ──
                if (stack.is(ModItems.BINDING_CARD.get())) {
                    if (this.moveItemStackTo(stack, BINDING_START, MARKER_START, false)) {
                        slot.set(stack);
                    }
                    return;
                }
                if (stack.is(ModItems.RANGE_CARD.get())) {
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
                if (stack.is(ModItems.LONG_CARD.get())) {
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
                if (stack.is(ModItems.DIMENSION_CARD.get())) {
                    Slot up1 = this.slots.get(UPGRADE_START + 1);
                    Slot up0 = this.slots.get(UPGRADE_START);
                    if (!up1.hasItem()) {
                        up1.set(stack.split(1));
                    } else if (!up0.hasItem()) {
                        up0.set(stack.split(1));
                    }
                    slot.set(stack);
                    slot.setChanged();
                    return;
                }
                // ── Fallback: hotbar ↔ main inventory for other items ──
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
    public boolean clickMenuButton(Player player, int id) {
        if (id >= 0 && id < 9) {
            tile.toggleOutputMode(id);
            return true;
        }
        if (id >= 10 && id < 19) {
            tile.toggleWhitelist(id - 10);
            return true;
        }
        if (id >= 20 && id < 29) {
            tile.openFilterMenu(player, id - 20);
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return result;

        ItemStack stack = slot.getItem();
        result = stack.copy();

        if (index >= BINDING_START && index < MARKER_START) {
            if (!this.moveItemStackTo(stack, PLAYER_START, PLAYER_START + 36, true))
                return ItemStack.EMPTY;
        } else if (index >= MARKER_START && index < PLAYER_START) {
            if (!this.moveItemStackTo(stack, PLAYER_START, PLAYER_START + 36, true))
                return ItemStack.EMPTY;
        } else if (index >= UPGRADE_START && index < BINDING_START) {
            if (!this.moveItemStackTo(stack, PLAYER_START, PLAYER_START + 36, true))
                return ItemStack.EMPTY;
        } else {
            // From player inventory — only upgrade/binding slots, never hidden ghost markers
            if (!this.moveItemStackTo(stack, UPGRADE_START, BINDING_START, false)
                && !this.moveItemStackTo(stack, BINDING_START, MARKER_START, false))
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

    @Override
    public void removed(Player player) {
        super.removed(player);
        // Validate bindings on GUI close — stale targets get cleared
        tile.validateBindings();
    }

    public boolean isOutputMode(int col) {
        boolean[] m = tile.getOutputModes();
        return col >= 0 && col < 9 && m[col];
    }

    public boolean isWhitelist(int col) {
        boolean[] m = tile.getWhitelistModes();
        return col >= 0 && col < 9 && m[col];
    }

    // ──── custom slots ────

    static class BindingCardSlot extends Slot {
        public BindingCardSlot(Container inv, int idx, int x, int y) {
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

    static class UpgradeSlot extends Slot {
        private static final java.util.Set<net.minecraft.world.item.Item> ACCEPTED =
                java.util.Set.of(ModItems.RANGE_CARD.get(), ModItems.DIMENSION_CARD.get(), ModItems.LONG_CARD.get());

        public UpgradeSlot(Container inv, int idx, int x, int y) {
            super(inv, idx, x, y);
        }
        @Override
        public boolean mayPlace(ItemStack stack) {
            return ACCEPTED.contains(stack.getItem());
        }
        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}

package com.portint.screen;

import com.portint.block.InterfaceBlockEntity;
import com.portint.packet.SetGhostItemPayload;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class FilterMenu extends AbstractContainerMenu {

    static final int MARKER_START = 0;   // 0-26 (27 ghost markers)
    static final int PLAYER_START = 27;  // 27-62 (36 player slots)

    final InterfaceBlockEntity tile;
    final int column;
    private final DataSlot whitelistSlot;

    public FilterMenu(int windowId, Inventory playerInv, InterfaceBlockEntity tile, int column) {
        super(ModMenuTypes.FILTER_MENU.get(), windowId);
        this.tile = tile;
        this.column = column;

        // Use vanilla DataSlot for reliable client sync
        this.whitelistSlot = new DataSlot() {
            private int value = (tile.getWhitelistModes()[column]) ? 1 : 0;
            @Override public int get() { return value; }
            @Override public void set(int v) { value = v; }
        };
        this.addDataSlot(whitelistSlot);

        Container markerInv = tile.getMarkerInventory();

        // 27 ghost marker slots in 3×9 layout
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int markerIdx = column * 27 + row * 9 + col;
                int slotX = 8 + col * 18;
                int slotY = 23 + row * 18;
                this.addSlot(new GhostMarkerSlot(markerInv, markerIdx, slotX, slotY));
            }
        }

        // Player inventory (3×9) — original Y
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 123 + row * 18));
            }
        }

        // Hotbar — original Y
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 181));
        }
    }

    // ── Ghost slot click handling ──────────────────────────────────

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        Slot slot = slotId >= 0 && slotId < this.slots.size() ? this.slots.get(slotId) : null;

        // Ghost marker slots: copy 1 or clear
        if (slot instanceof GhostMarkerSlot) {
            ItemStack carried = getCarried();
            if (!carried.isEmpty()) {
                slot.setByPlayer(carried.copyWithCount(1));
            } else {
                slot.setByPlayer(ItemStack.EMPTY);
            }
            // Client side: sync to server (slotId = within-column index 0-26)
            if (player.level().isClientSide) {
                PacketDistributor.sendToServer(new SetGhostItemPayload(column, slotId, slot.getItem()));
            }
            return;
        }

        // Shift-click on player inventory: mark into first empty ghost slot
        // without consuming the original item (AE2-style ghost marking)
        if (clickType == ClickType.QUICK_MOVE && slot != null && slot.hasItem()
                && slotId >= PLAYER_START) {
            ItemStack stack = slot.getItem();
            for (int i = MARKER_START; i < MARKER_START + 27; i++) {
                Slot gs = this.slots.get(i);
                if (!gs.hasItem()) {
                    ItemStack copy = stack.copyWithCount(1);
                    gs.setByPlayer(copy);
                    if (player.level().isClientSide) {
                        PacketDistributor.sendToServer(new SetGhostItemPayload(column, i, copy));
                    }
                    break;
                }
            }
            return;
        }

        super.clicked(slotId, button, clickType, player);
    }

    // ── Button handling ────────────────────────────────────────────

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == 0) {
            // 输入模式强制白名单，不允许切黑名单
            tile.toggleWhitelist(column);
            whitelistSlot.set(tile.getWhitelistModes()[column] ? 1 : 0);
            return true;
        }
        if (id == 255) {
            // Close filter and reopen interface screen
            tile.openCustomMenu(player);
            return true;
        }
        return false;
    }

    // ── Quick move ─────────────────────────────────────────────────

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return result;

        ItemStack stack = slot.getItem();
        result = stack.copy();

        if (index >= MARKER_START && index < PLAYER_START) {
            // Move from marker slots to player inventory
            if (!this.moveItemStackTo(stack, PLAYER_START, PLAYER_START + 36, true))
                return ItemStack.EMPTY;
        } else {
            // Move from player inventory to marker slots
            if (!this.moveItemStackTo(stack, MARKER_START, MARKER_START + 27, false))
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
    }

    // ── Accessors ──────────────────────────────────────────────────

    public int getColumn() {
        return column;
    }

    public boolean isWhitelist() {
        return whitelistSlot.get() == 1;
    }

    // ── Ghost slot sync ────────────────────────────────────────────

    /**
     * Called from client code (JEI drag, shift-click, etc.) to sync a ghost slot
     * change to the server-side markerInv.
     */
    public void syncGhostSlot(int slotIndex, ItemStack stack) {
        // Called on client; send packet to server
        PacketDistributor.sendToServer(new SetGhostItemPayload(column, slotIndex, stack));
    }

    /**
     * Server-side handler: update markerInv from client sync packet.
     */
    public void onSetGhostItem(SetGhostItemPayload payload) {
        Container markerInv = tile.getMarkerInventory();
        int idx = payload.column() * 27 + payload.slotIndex();
        if (idx >= 0 && idx < markerInv.getContainerSize()) {
            markerInv.setItem(idx, payload.stack());
        }
    }
}

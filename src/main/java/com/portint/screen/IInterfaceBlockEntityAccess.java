package com.portint.screen;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;

/**
 * Common interface for block entities that use InterfaceMenu/InterfaceScreen.
 */
public interface IInterfaceBlockEntityAccess {
    Container getUpgradeInventory();
    Container getBindingInventory();
    Container getMarkerInventory();
    ContainerData getDataAccess();
    boolean isOutputMode(int col);
    boolean isWhitelist(int col);
    void toggleOutputMode(int col);
    void toggleWhitelist(int col);
    void openFilterMenu(Player player, int col);
    void validateBindings();
    /** Whether this block supports per-column direction/markers UI. External storage = false. */
    boolean hasColumnConfig();
    /** Per-slot priority for storage mounting. Default 0 for all slots. */
    default int getSlotPriority(int slot) { return 0; }
    /** Adjust per-slot priority by delta. Default no-op. */
    default void adjustSlotPriority(int slot, int delta) { }
}

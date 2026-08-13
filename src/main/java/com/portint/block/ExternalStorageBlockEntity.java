package com.portint.block;

import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.storage.IStorageService;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.parts.automation.ForgeExternalStorageStrategy;
import com.portint.BoundTarget;
import com.portint.ModDataComponents;
import com.portint.item.BindingCardItem;
import com.portint.item.ModItems;
import com.portint.storage.BidirectionalExternalStorageAdapter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class ExternalStorageBlockEntity extends AENetworkedBlockEntity
        implements IStorageProvider, com.portint.screen.IInterfaceBlockEntityAccess {

    static final int BINDING_COUNT = 9;
    static final int UPGRADE_COUNT = 2;

    private static final IGridNodeListener<ExternalStorageBlockEntity> NODE_LISTENER =
            new IGridNodeListener<>() {
                @Override
                public void onSaveChanges(ExternalStorageBlockEntity nodeOwner, IGridNode node) {
                    nodeOwner.setChanged();
                }
            };

    private final SimpleContainer bindingInv = new SimpleContainer(BINDING_COUNT);
    private final SimpleContainer upgradeInv = new SimpleContainer(UPGRADE_COUNT);
    private final SimpleContainer markerInv = new SimpleContainer(9 * 27);
    private final ContainerData dataAccess = new ContainerData() {
        @Override public int get(int i) { return 0; }
        @Override public void set(int i, int v) { }
        @Override public int getCount() { return 2; }
    };

    private final Set<Long> forcedChunks = new HashSet<>();
    private boolean wasActive = false;

    private int tickCounter;
    private int[] bindingPriorities = new int[BINDING_COUNT];

    public ExternalStorageBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.EXTERNAL_STORAGE_BLOCK_ENTITY.get(), pos, state);
        this.bindingInv.addListener(inv -> {
            setChanged();
            // Sync priority and filter mode from cards to block entity arrays
            for (int i = 0; i < BINDING_COUNT; i++) {
                ItemStack card = bindingInv.getItem(i);
                if (!card.isEmpty()) {
                    int cardPriority = BindingCardItem.getPriority(card);
                    if (cardPriority != bindingPriorities[i]) {
                        bindingPriorities[i] = cardPriority;
                    }
                } else {
                    // Reset priority to 0 when binding card is removed
                    if (bindingPriorities[i] != 0) {
                        bindingPriorities[i] = 0;
                    }
                }
            }
            if (level instanceof ServerLevel) {
                validateBindings();
                // Notify AE2 to re-mount storage since bindings changed
                IGridNode node = getMainNode().getNode();
                if (node != null && node.isActive()) {
                    node.getGrid().getStorageService().refreshNodeStorageProvider(node);
                }
            }
        });
        this.upgradeInv.addListener(inv -> setChanged());
    }

    // ═════════════════════════════════════════════════════════════════
    //  AE2 Grid Node
    // ═════════════════════════════════════════════════════════════════

    @Override
    protected IManagedGridNode createMainNode() {
        return super.createMainNode()
                .setVisualRepresentation(ModBlocks.EXTERNAL_STORAGE_BLOCK.get())
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setIdlePowerUsage(0.3)
                .addService(IStorageProvider.class, this);
    }

    @Override
    public AECableType getCableConnectionType(net.minecraft.core.Direction dir) {
        return AECableType.SMART;
    }

    // ═════════════════════════════════════════════════════════════════
    //  Tick
    // ═════════════════════════════════════════════════════════════════

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                   ExternalStorageBlockEntity tile) {
        if (tile.getMainNode().getNode() == null) return;

        tile.tickCounter++;

        // Active state detection + model swap every 20 ticks
        if (tile.tickCounter % 20 == 0) {
            boolean nowActive = tile.getMainNode().isActive();
            if (nowActive != tile.wasActive) {
                tile.wasActive = nowActive;
                level.setBlock(pos, state.setValue(ExternalStorageBlock.ACTIVE, nowActive), 3);
            }
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state,
                                   ExternalStorageBlockEntity tile) {
        // No client-side tick logic needed
    }

    // ═════════════════════════════════════════════════════════════════
    //  IStorageProvider
    // ═════════════════════════════════════════════════════════════════

    @Override
    public void mountInventories(IStorageMounts mounts) {
        for (int col = 0; col < BINDING_COUNT; col++) {
            ItemStack card = bindingInv.getItem(col);
            if (card.isEmpty()) continue;

            var opt = card.get(ModDataComponents.BOUND_TARGET.get());
            if (opt == null || opt.isEmpty()) continue;

            BoundTarget bt = opt.get();

            // Find target BlockEntity
            if (!(level instanceof ServerLevel serverLevel)) continue;
            ServerLevel targetLevel = serverLevel.getServer().getLevel(bt.dimension());
            if (targetLevel == null || !targetLevel.isLoaded(bt.pos())) continue;

            BlockEntity be = targetLevel.getBlockEntity(bt.pos());
            if (be == null) continue;

            IItemHandler handler = targetLevel.getCapability(
                    Capabilities.ItemHandler.BLOCK, bt.pos(), bt.side());

            // Create bidirectional adapter so AE network can see and operate bound inventory
            Component desc = Component.translatable("gui.portint.external_storage_bound",
                    bt.pos().getX(), bt.pos().getY(), bt.pos().getZ(),
                    bt.dimension().location().toString());

            // Priority: base 10 + 5 per capacity card + per-slot offset
            int priority = 10 + countCapacityCards() * 5 + bindingPriorities[col];

            if (handler != null) {
                MEStorage adapter = new BidirectionalExternalStorageAdapter(handler, desc);
                mounts.mount(adapter, priority);
            }

            // Mount fluid handler as native AE2 external fluid storage
            IFluidHandler fluidHandler = targetLevel.getCapability(
                    Capabilities.FluidHandler.BLOCK, bt.pos(), bt.side());
            if (fluidHandler != null) {
                ExternalStorageStrategy strategy =
                        ForgeExternalStorageStrategy.createFluid(targetLevel, bt.pos(), bt.side());
                MEStorage fluidAdapter = strategy.createWrapper(false, this::onFluidStorageChanged);
                if (fluidAdapter != null) {
                    mounts.mount(fluidAdapter, priority);
                }
            }
        }

        setChanged();
    }

    /** Called when a mounted external fluid handler changes, refresh the network storage provider. */
    private void onFluidStorageChanged() {
        if (!(level instanceof ServerLevel)) return;
        IGridNode node = getMainNode().getNode();
        if (node != null && node.isActive()) {
            node.getGrid().getStorageService().refreshNodeStorageProvider(node);
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Capacity cards
    // ═════════════════════════════════════════════════════════════════

    public int countCapacityCards() {
        int count = 0;
        if (upgradeInv.getItem(0).is(ModItems.CAPACITY_CARD.get())) count++;
        if (upgradeInv.getItem(1).is(ModItems.CAPACITY_CARD.get())) count++;
        return count;
    }

    // ═════════════════════════════════════════════════════════════════
    //  Bindings
    // ═════════════════════════════════════════════════════════════════

    public void validateBindings() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        for (int col = 0; col < BINDING_COUNT; col++) {
            ItemStack card = bindingInv.getItem(col);
            if (card.isEmpty()) continue;
            var opt = card.get(ModDataComponents.BOUND_TARGET.get());
            if (opt == null || opt.isEmpty()) continue;
            BoundTarget bt = opt.get();
            ServerLevel targetLevel = serverLevel.getServer().getLevel(bt.dimension());
            if (targetLevel == null || !targetLevel.isLoaded(bt.pos())) continue;
            BlockEntity be = targetLevel.getBlockEntity(bt.pos());
            if (be == null) {
                card.remove(ModDataComponents.BOUND_TARGET.get());
                card.remove(ModDataComponents.BOUND_BLOCK_NAME.get());
                setChanged();
                continue;
            }
            // Check for item or fluid handler capability
            IItemHandler itemHandler = targetLevel.getCapability(
                    Capabilities.ItemHandler.BLOCK, bt.pos(), bt.side());
            IFluidHandler fluidHandler = targetLevel.getCapability(
                    Capabilities.FluidHandler.BLOCK, bt.pos(), bt.side());
            if (itemHandler == null && fluidHandler == null) {
                card.remove(ModDataComponents.BOUND_TARGET.get());
                card.remove(ModDataComponents.BOUND_BLOCK_NAME.get());
                setChanged();
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Accessors
    // ═════════════════════════════════════════════════════════════════

    public SimpleContainer getBindingInventory() { return bindingInv; }
    public SimpleContainer getUpgradeInventory() { return upgradeInv; }
    public Container getMarkerInventory() { return markerInv; }
    public ContainerData getDataAccess() { return dataAccess; }

    public boolean isOutputMode(int col) { return false; }
    public boolean isWhitelist(int col) { return false; }
    public void toggleOutputMode(int col) { }
    public void toggleWhitelist(int col) { }
    public void openFilterMenu(Player player, int col) { }
    public boolean hasColumnConfig() { return false; }

    @Override
    public int getSlotPriority(int slot) {
        return slot >= 0 && slot < BINDING_COUNT ? bindingPriorities[slot] : 0;
    }

    @Override
    public void adjustSlotPriority(int slot, int delta) {
        if (slot < 0 || slot >= BINDING_COUNT) return;
        bindingPriorities[slot] += delta;
        // Write back to card so the card carries its priority
        ItemStack card = bindingInv.getItem(slot);
        if (!card.isEmpty()) {
            card.set(ModDataComponents.PRIORITY.get(), bindingPriorities[slot]);
        }
        setChanged();
        // Notify AE2 to re-mount storage with updated priorities
        if (level instanceof ServerLevel) {
            IGridNode node = getMainNode().getNode();
            if (node != null && node.isActive()) {
                node.getGrid().getStorageService().refreshNodeStorageProvider(node);
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Menu
    // ═════════════════════════════════════════════════════════════════

    public void openMenu(Player player) {
        validateBindings();
        player.openMenu(new net.minecraft.world.MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatable("block.portint.external_storage_block");
            }
            @Override
            public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
                    int id, net.minecraft.world.entity.player.Inventory inv, Player p) {
                return new com.portint.screen.ExternalStorageMenu(id, inv,
                        ExternalStorageBlockEntity.this);
            }
        }, this.worldPosition);
    }

    /**
     * Open the InterfaceMenu GUI for this external storage block.
     */
    public void openCustomMenu(Player player) {
        validateBindings();
        player.openMenu(new net.minecraft.world.MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatable("block.portint.external_storage_block");
            }
            @Override
            public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
                    int id, net.minecraft.world.entity.player.Inventory inv, Player p) {
                return new com.portint.screen.InterfaceMenu(id, inv,
                        ExternalStorageBlockEntity.this);
            }
        }, this.worldPosition);
    }

    // ═════════════════════════════════════════════════════════════════
    //  Drop
    // ═════════════════════════════════════════════════════════════════

    public void dropContents() {
        releaseAllForcedChunks();
        if (level != null) {
            Containers.dropContents(level, worldPosition, bindingInv);
            Containers.dropContents(level, worldPosition, upgradeInv);
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Forced chunks
    // ═════════════════════════════════════════════════════════════════

    private void releaseAllForcedChunks() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        for (long key : forcedChunks) {
            ChunkPos cp = new ChunkPos(key);
            serverLevel.setChunkForced(cp.x, cp.z, false);
        }
        forcedChunks.clear();
    }

    // ═════════════════════════════════════════════════════════════════
    //  Save / Load
    // ═════════════════════════════════════════════════════════════════

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        tag.put("BindingInv",
                ContainerHelper.saveAllItems(new CompoundTag(), bindingInv.getItems(), reg));
        tag.put("UpgradeInv",
                ContainerHelper.saveAllItems(new CompoundTag(), upgradeInv.getItems(), reg));
        tag.putIntArray("BindingPriorities", bindingPriorities);
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadTag(tag, reg);
        if (tag.contains("BindingInv"))
            ContainerHelper.loadAllItems(tag.getCompound("BindingInv"),
                    bindingInv.getItems(), reg);
        if (tag.contains("UpgradeInv"))
            ContainerHelper.loadAllItems(tag.getCompound("UpgradeInv"),
                    upgradeInv.getItems(), reg);
        if (tag.contains("BindingPriorities")) {
            int[] loaded = tag.getIntArray("BindingPriorities");
            if (loaded.length == BINDING_COUNT) {
                bindingPriorities = loaded;
            }
        }
    }
}

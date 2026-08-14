package com.portint.block;

import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.helpers.InterfaceLogic;
import appeng.helpers.InterfaceLogicHost;
import appeng.util.ConfigInventory;
import com.portint.BoundTarget;
import com.portint.ModDataComponents;
import com.portint.PortableInterface;
import com.portint.item.BindingCardItem;
import com.portint.item.ModItems;
import com.portint.service.IMyCustomGridService;
import com.portint.storage.CombinedItemHandler;
import com.portint.storage.PortableInterfaceStorageAdapter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.*;

public class InterfaceBlockEntity extends AENetworkedBlockEntity
        implements InterfaceLogicHost, IStorageProvider, com.portint.screen.IInterfaceBlockEntityAccess {

    // ── Grid node listener ──────────────────────────────────────────
    private static final IGridNodeListener<InterfaceBlockEntity> NODE_LISTENER =
            new IGridNodeListener<>() {
                @Override
                public void onSaveChanges(InterfaceBlockEntity nodeOwner, IGridNode node) {
                    nodeOwner.setChanged();
                }
            };

    // ── Constants ───────────────────────────────────────────────────
    static final int UPGRADE_COUNT = 2;
    static final int BINDING_COUNT = 9;
    static final int MARKER_COUNT = 243;     // 27 per column (3 rows x 9 cols)
    static final int TRANSFER_RATE = 262145;  // 2^18 + 1

    // ── AE2 InterfaceLogic ──────────────────────────────────────────
    private final InterfaceLogic logic;

    // ── Portint custom inventories ──────────────────────────────────
    private final SimpleContainer upgradeInv = new SimpleContainer(UPGRADE_COUNT);
    private final SimpleContainer bindingInv = new SimpleContainer(BINDING_COUNT);
    private final SimpleContainer markerInv = new SimpleContainer(MARKER_COUNT) {
        @Override public int getMaxStackSize() { return 1; }
    };

    // ── Per-column modes ────────────────────────────────────────────
    private final boolean[] outputModes = new boolean[9];    // false=输入(↓), true=输出(↑)
    private final boolean[] whitelistModes = new boolean[9];  // false=黑名单, true=白名单
    private final int[] bindingPriorities = new int[9];       // per-column storage priority

    // ── Tick ────────────────────────────────────────────────────────
    private int tickCounter;
    private int activeStabilityCounter;
    private static final int STABILITY_THRESHOLD = 3; // 3 check cycles = 3s stability

    // ── Transfer cooldown (smart sleep) ────────────────────────────
    /** Cooldown in ticks per column. 0 = ready to process. Exponential backoff on idle. */
    private final int[] cooldowns = new int[9];
    private static final int MAX_COOLDOWN = 100;

    // ── Hot slot tracking per column ───────────────────────────────
    private final int[] lastExtractSlots = new int[9];
    private final int[] lastInsertSlots = new int[9];

    // ── Force-loaded chunks (for dim card) ──────────────────────────
    private final Set<Long> forcedChunks = new HashSet<>();

    // ── Network state tracking for model swap ──────────────────────
    private boolean wasActive = false;

    // ── Long card energy multiplier tracking ───────────────────────
    private static final double BASE_IDLE_POWER = 9000.0;
    private static final double LONG_CARD_POWER_MULTIPLIER = 64.0;
    /** Per-tick item transfer cap per long card (items/tick), read from config. */
    public static int longCardItemRate() {
        return com.portint.PortConfig.LONG_CARD_ITEM_RATE.get();
    }
    private boolean hadLongCard = false;

    // ═════════════════════════════════════════════════════════════════
    //  Constructor
    // ═════════════════════════════════════════════════════════════════

    public InterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INTERFACE_BLOCK_ENTITY.get(), pos, state);
        this.logic = new InterfaceLogic(getMainNode(), this,
                ModBlocks.INTERFACE_BLOCK_ITEM.get());
        // Trigger validation when binding cards change
        this.bindingInv.addListener(inv -> {
            setChanged();
            // Sync filter mode and priority from cards to block entity arrays
            for (int i = 0; i < BINDING_COUNT; i++) {
                ItemStack card = bindingInv.getItem(i);
                if (!card.isEmpty()) {
                    boolean cardWhitelist = BindingCardItem.getFilterMode(card) == 1;
                    // 输出模式强制白名单，不受卡片过滤模式覆盖
                    if (outputModes[i]) {
                        if (!whitelistModes[i]) whitelistModes[i] = true;
                    } else if (cardWhitelist != whitelistModes[i]) {
                        whitelistModes[i] = cardWhitelist;
                    }
                    int cardPriority = BindingCardItem.getPriority(card);
                    if (cardPriority != bindingPriorities[i]) {
                        bindingPriorities[i] = cardPriority;
                    }
                } else {
                    if (bindingPriorities[i] != 0) {
                        bindingPriorities[i] = 0;
                    }
                }
            }
            if (level instanceof ServerLevel) {
                validateBindings();
            }
        });
        // Mark dirty when upgrade cards change
        this.upgradeInv.addListener(inv -> setChanged());
    }

    // ═════════════════════════════════════════════════════════════════
    //  AE2 Grid Node
    // ═════════════════════════════════════════════════════════════════

    @Override
    protected IManagedGridNode createMainNode() {
        return super.createMainNode()
                .setVisualRepresentation(ModBlocks.INTERFACE_BLOCK.get())
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setIdlePowerUsage(0.5)
                .addService(IStorageProvider.class, this);
    }

    /**
     * All 6 sides carry SMART cable so ME cables can connect from any direction.
     */
    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.SMART;
    }

    /**
     * Get the custom grid service from this node's grid, if available.
     * This ensures the block registers itself as a recognized device on the AE2 network.
     */
    public IMyCustomGridService getGridService() {
        IGrid grid = getMainNode().getGrid();
        if (grid != null) {
            return grid.getService(IMyCustomGridService.class);
        }
        return null;
    }

    /**
     * Re-create the grid node when the chunk is loaded.
     * AE2's AENetworkedBlockEntity relies on clearRemoved → scheduleInit → onReady,
     * but chunk reloads from disk may not always fire clearRemoved in all MC versions.
     * Explicit create() here is idempotent and ensures the cable link is always restored.
     */
    // ═════════════════════════════════════════════════════════════════
    //  IStorageProvider
    // ═════════════════════════════════════════════════════════════════

    @Override
    public void mountInventories(IStorageMounts mounts) {
        MEStorage adapter = new PortableInterfaceStorageAdapter(this);
        mounts.mount(adapter, 10);
    }

    // ═════════════════════════════════════════════════════════════════
    //  InterfaceLogicHost implementation
    // ═════════════════════════════════════════════════════════════════

    @Override
    public InterfaceLogic getInterfaceLogic() {
        return logic;
    }

    @Override
    public BlockEntity getBlockEntity() {
        return this;
    }

    @Override
    public void saveChanges() {
        setChanged();
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return ModBlocks.INTERFACE_BLOCK_ITEM.get().getDefaultInstance();
    }

    // ═════════════════════════════════════════════════════════════════
    //  Accessors
    // ═════════════════════════════════════════════════════════════════

    public Container getUpgradeInventory() { return upgradeInv; }
    public Container getBindingInventory()  { return bindingInv; }
    public Container getMarkerInventory()   { return markerInv; }
    public net.minecraft.world.inventory.ContainerData getDataAccess()    { return dataAccess; }
    public boolean[] getOutputModes()       { return outputModes; }
    public boolean[] getWhitelistModes()    { return whitelistModes; }
    public boolean isOutputMode(int col)    { return col >= 0 && col < 9 && outputModes[col]; }
    public boolean isWhitelist(int col)     { return col >= 0 && col < 9 && whitelistModes[col]; }
    public boolean hasColumnConfig()        { return true; }

    @Override
    public int getSlotPriority(int slot) {
        return slot >= 0 && slot < BINDING_COUNT ? bindingPriorities[slot] : 0;
    }

    @Override
    public void adjustSlotPriority(int slot, int delta) {
        if (slot < 0 || slot >= BINDING_COUNT) return;
        bindingPriorities[slot] += delta;
        ItemStack card = bindingInv.getItem(slot);
        if (!card.isEmpty()) {
            card.set(ModDataComponents.PRIORITY.get(), bindingPriorities[slot]);
        }
        setChanged();
    }

    public boolean hasRangeCard() {
        return upgradeInv.getItem(0).is(ModItems.RANGE_CARD.get())
            || upgradeInv.getItem(1).is(ModItems.RANGE_CARD.get());
    }
    public boolean hasDimCard() {
        return upgradeInv.getItem(0).is(ModItems.DIMENSION_CARD.get())
            || upgradeInv.getItem(1).is(ModItems.DIMENSION_CARD.get());
    }
    public boolean hasLongCard() {
        return upgradeInv.getItem(0).is(ModItems.LONG_CARD.get())
            || upgradeInv.getItem(1).is(ModItems.LONG_CARD.get());
    }
    /** Count long cards in upgrade slots (0, 1, or 2). */
    public int countLongCards() {
        int count = 0;
        if (upgradeInv.getItem(0).is(ModItems.LONG_CARD.get())) count++;
        if (upgradeInv.getItem(1).is(ModItems.LONG_CARD.get())) count++;
        return count;
    }
    public int getEffectiveTransferRate() {
        int cards = countLongCards();
        return cards > 0
                ? com.portint.PortConfig.LONG_CARD_ITEM_RATE.get() * cards
                : com.portint.PortConfig.BASE_ITEM_RATE.get();
    }

    public void toggleOutputMode(int col) {
        if (col >= 0 && col < 9) {
            outputModes[col] = !outputModes[col];
            // 输出到绑定方块模式强制白名单
            if (outputModes[col]) {
                whitelistModes[col] = true;
            } else {
                // 切回输入模式时，恢复卡片上的过滤模式
                ItemStack card = bindingInv.getItem(col);
                if (!card.isEmpty()) {
                    whitelistModes[col] = BindingCardItem.getFilterMode(card) == 1;
                }
            }
            setChanged();
        }
    }

    public void toggleWhitelist(int col) {
        // 输出模式下白名单锁定为true，不允许切黑名单
        if (col >= 0 && col < 9 && !outputModes[col]) {
            whitelistModes[col] = !whitelistModes[col];
            // Write back to card so the card carries its filter mode
            ItemStack card = bindingInv.getItem(col);
            if (!card.isEmpty()) {
                card.set(ModDataComponents.FILTER_MODE.get(),
                    whitelistModes[col] ? (byte) 1 : (byte) 0);
            }
            setChanged();
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  ContainerData (synced to client)
    // ═════════════════════════════════════════════════════════════════

    public final net.minecraft.world.inventory.ContainerData dataAccess =
            new net.minecraft.world.inventory.ContainerData() {
                @Override public int get(int i) {
                    if (i == 0) return packBooleans(outputModes);
                    if (i == 1) return packBooleans(whitelistModes);
                    return 0;
                }
                @Override public void set(int i, int v) {
                    if (i == 0) unpackBooleans(outputModes, v);
                    else if (i == 1) unpackBooleans(whitelistModes, v);
                }
                @Override public int getCount() { return 2; }
            };

    // ═════════════════════════════════════════════════════════════════
    //  Menu
    // ═════════════════════════════════════════════════════════════════

    /**
     * Validate all binding card targets. If a bound block no longer exists,
     * clear the binding to stop that column from wasting ticks.
     */
    public void validateBindings() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        for (int col = 0; col < 9; col++) {
            ItemStack card = bindingInv.getItem(col);
            if (card.isEmpty()) continue;
            var opt = card.get(ModDataComponents.BOUND_TARGET.get());
            if (opt == null || opt.isEmpty()) continue;
            BoundTarget bt = opt.get();
            ServerLevel targetLevel = serverLevel.getServer().getLevel(bt.dimension());
            if (targetLevel == null || !targetLevel.isLoaded(bt.pos())) continue;
            BlockEntity be = targetLevel.getBlockEntity(bt.pos());
            if (be == null) {
                // Block gone — clear binding
                card.remove(ModDataComponents.BOUND_TARGET.get());
                card.remove(ModDataComponents.BOUND_BLOCK_NAME.get());
                setChanged();
            }
        }
    }

    /**
     * Periodic check: detect if the container at a bound position was replaced
     * and update the BOUND_BLOCK_NAME on the binding card accordingly.
     * Called once every 20 ticks, rotating through all 9 columns.
     */
    private void checkBindingUpdate(int col) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        ItemStack card = bindingInv.getItem(col);
        if (card.isEmpty()) return;
        var opt = card.get(ModDataComponents.BOUND_TARGET.get());
        if (opt == null || opt.isEmpty()) return;
        BoundTarget bt = opt.get();

        ServerLevel targetLevel = serverLevel.getServer().getLevel(bt.dimension());
        if (targetLevel == null || !targetLevel.isLoaded(bt.pos())) return;

        BlockEntity be = targetLevel.getBlockEntity(bt.pos());
        String currentName;
        if (be == null) {
            // No BlockEntity at position — block was removed, keep binding
            // but mark name as empty so next validateBindings will clear it
            currentName = "";
        } else {
            currentName = be.getBlockState().getBlock().getName().getString();
        }

        String storedName = card.getOrDefault(ModDataComponents.BOUND_BLOCK_NAME.get(),
                java.util.Optional.<String>empty()).orElse("");

        if (!currentName.equals(storedName)) {
            if (currentName.isEmpty()) {
                card.remove(ModDataComponents.BOUND_BLOCK_NAME.get());
            } else {
                card.set(ModDataComponents.BOUND_BLOCK_NAME.get(),
                        java.util.Optional.of(currentName));
            }
            setChanged();
        }
    }

    /**
     * Open portint's custom menu with binding cards, markers, etc.
     */
    public void openCustomMenu(Player player) {
        validateBindings();
        player.openMenu(new net.minecraft.world.MenuProvider() {
            @Override
            public net.minecraft.network.chat.Component getDisplayName() {
                return net.minecraft.network.chat.Component.translatable("block.portint.interface_block");
            }
            @Override
            public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
                    int id, net.minecraft.world.entity.player.Inventory inv, Player p) {
                return new com.portint.screen.InterfaceMenu(id, inv,
                        InterfaceBlockEntity.this);
            }
        }, this.worldPosition);
    }

    /** Open the filter configuration screen for a specific column. */
    public void openFilterMenu(Player player, int column) {
        player.openMenu(new net.minecraft.world.MenuProvider() {
            @Override
            public net.minecraft.network.chat.Component getDisplayName() {
                return net.minecraft.network.chat.Component.translatable(
                        "gui.portint.filter_title", column + 1);
            }
            @Override
            public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
                    int id, net.minecraft.world.entity.player.Inventory inv, Player p) {
                return new com.portint.screen.FilterMenu(id, inv,
                        InterfaceBlockEntity.this, column);
            }
        }, buf -> {
            buf.writeBlockPos(this.worldPosition);
            buf.writeByte(column);
        });
    }

    // ═════════════════════════════════════════════════════════════════
    //  Tick

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                   InterfaceBlockEntity tile) {
        // Wait until grid node is ready
        if (tile.getMainNode().getNode() == null) return;

        tile.tickCounter++;

        // Force initial state sync on first tick
        if (tile.tickCounter == 1) {
            BlockState current = level.getBlockState(pos);
            if (current.hasProperty(InterfaceBlock.ACTIVE)
                    && current.getValue(InterfaceBlock.ACTIVE) != tile.wasActive) {
                level.setBlock(pos, current.setValue(InterfaceBlock.ACTIVE, tile.wasActive), 3);
            }
            // Sync long card power state on load
            tile.hadLongCard = tile.hasLongCard();
            tile.getMainNode().setIdlePowerUsage(
                tile.hadLongCard ? BASE_IDLE_POWER * LONG_CARD_POWER_MULTIPLIER : BASE_IDLE_POWER);
        }

        // Check network active state every 20 ticks (1 second)
        // Use stability counter to prevent model flickering on transient network changes
        if (tile.tickCounter % 20 == 0) {
            boolean nowActive = tile.getMainNode().isActive();
            if (nowActive) {
                if (tile.activeStabilityCounter < STABILITY_THRESHOLD)
                    tile.activeStabilityCounter++;
            } else {
                if (tile.activeStabilityCounter > -STABILITY_THRESHOLD)
                    tile.activeStabilityCounter--;
            }
            boolean displayActive = tile.activeStabilityCounter > 0;
            if (displayActive != tile.wasActive) {
                tile.wasActive = displayActive;
                BlockState newState = state.setValue(InterfaceBlock.ACTIVE, displayActive);
                level.setBlock(pos, newState, 3);
            }

            // Long card energy multiplier: ×64 idle power when long card inserted
            boolean nowHasLongCard = tile.hasLongCard();
            if (nowHasLongCard != tile.hadLongCard) {
                tile.hadLongCard = nowHasLongCard;
                tile.getMainNode().setIdlePowerUsage(
                    nowHasLongCard ? BASE_IDLE_POWER * LONG_CARD_POWER_MULTIPLIER : BASE_IDLE_POWER);
            }
        }

        // Active transfer: process all 9 columns sorted by priority (highest first), with cooldown backoff
        Integer[] order = new Integer[9];
        for (int i = 0; i < 9; i++) order[i] = i;
        java.util.Arrays.sort(order,
                java.util.Comparator.comparingInt((Integer c) -> tile.bindingPriorities[c]).reversed());
        for (int idx = 0; idx < 9; idx++) {
            int col = order[idx];
            if (tile.cooldowns[col] > 0) {
                tile.cooldowns[col]--;
                continue;
            }
            boolean moved = tile.processTransfer(col);
            if (!moved) {
                // No items moved — exponential backoff（最小值 1 tick 确保有机会恢复）
                tile.cooldowns[col] = Math.min(tile.MAX_COOLDOWN, Math.max(1, tile.cooldowns[col] * 2 + 1));
            } else {
                tile.cooldowns[col] = 0;
            }
        }

        // Binding auto-update: check one column every 20 ticks (rotating)
        if (tile.tickCounter % 20 == 0) {
            int checkCol = (tile.tickCounter / 20) % 9;
            tile.checkBindingUpdate(checkCol);
        }

        // Manage force load when dim card installed/removed
        if (tile.tickCounter % 100 == 0) {
            tile.syncForceLoads();
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state,
                                   InterfaceBlockEntity tile) {}

    // ── Active transfer ─────────────────────────────────────────────

    /** @return true if any item, fluid or chemical was transferred */
    private boolean processTransfer(int col) {
        if (!getMainNode().isActive()) return false;
        ItemStack card = bindingInv.getItem(col);
        if (card.isEmpty()) return false;
        var opt = card.get(ModDataComponents.BOUND_TARGET.get());
        if (opt == null || opt.isEmpty()) return false;
        BoundTarget bt = opt.get();
        if (!(level instanceof ServerLevel serverLevel)) return false;

        ServerLevel targetLevel = serverLevel.getServer().getLevel(bt.dimension());
        if (targetLevel == null || !targetLevel.isLoaded(bt.pos())) return false;

        boolean dimCard = hasDimCard();
        boolean rangeCard = hasRangeCard();
        if (!dimCard && !rangeCard) {
            int wirelessRange = com.portint.PortConfig.getEffectiveWirelessRange();
            if (wirelessRange >= 0
                    && Math.sqrt(bt.pos().distSqr(worldPosition)) > wirelessRange) {
                return false;
            }
        }

        IItemHandler handler = targetLevel.getCapability(
                Capabilities.ItemHandler.BLOCK, bt.pos(), bt.side());
        if (handler == null) return false;
        // 大箱子合并：绑定单一半箱时，合并整个大箱子（54 格）
        handler = mergeDoubleChest(targetLevel, bt.pos(), bt.side(), handler);

        boolean isOutput = outputModes[col];
        boolean isWhitelist = whitelistModes[col];
        Set<AEItemKey> markers = collectMarkerKeys(col);

        var grid = getMainNode().getGrid();
        if (grid == null) return false;
        var netStorage = grid.getStorageService().getInventory();

        boolean moved = false;

        if (isOutput) {
            moved = doOutput(netStorage, handler, markers, isWhitelist, col);
        } else {
            moved = doInput(netStorage, handler, markers, isWhitelist, col);
        }

        // Fluid handling (with fluid-specific markers from ghost slots)
        IFluidHandler fluidHandler = targetLevel.getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, bt.pos(), bt.side());
        if (fluidHandler != null) {
            Set<AEFluidKey> fluidMarkers = collectFluidMarkerKeys(col);
            if (isOutput) {
                moved |= doOutputFluid(netStorage, fluidHandler, fluidMarkers, isWhitelist);
            } else {
                moved |= doInputFluid(netStorage, fluidHandler, fluidMarkers, isWhitelist);
            }
        }

        // Chemical (Mekanism gas/slurry/etc) handling
        moved |= transferChemicals(netStorage, targetLevel, bt, isOutput, col, markers, isWhitelist);

        return moved;
    }

    /**
     * 检测原版大箱子（ChestBlock 的 LEFT/RIGHT），若当前 handler 未合并（不足 54 格），
     * 则找到配对箱子构建复合 handler 覆盖整个大箱子；已合并则原样返回。
     */
    private IItemHandler mergeDoubleChest(ServerLevel level, BlockPos pos, Direction side, IItemHandler handler) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock)) return handler;
        ChestType type = state.getValue(ChestBlock.TYPE);
        if (type == ChestType.SINGLE) return handler;
        ChestType target = type == ChestType.LEFT ? ChestType.RIGHT : ChestType.LEFT;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos p = pos.relative(dir);
            BlockState ps = level.getBlockState(p);
            if (ps.getBlock() instanceof ChestBlock && ps.getValue(ChestBlock.TYPE) == target) {
                IItemHandler pairHandler = level.getCapability(
                        Capabilities.ItemHandler.BLOCK, p, side);
                if (pairHandler != null && handler.getSlots() < 54) {
                    return new CombinedItemHandler(handler, pairHandler);
                }
                break;
            }
        }
        return handler;
    }

    private boolean doInput(appeng.api.storage.MEStorage netStorage, IItemHandler handler,
                         Set<AEItemKey> markers, boolean isWhitelist, int col) {
        long budget = getEffectiveTransferRate();
        long totalMoved = 0;
        var source = new appeng.me.helpers.BaseActionSource();
        int slotCount = handler.getSlots();
        if (slotCount == 0) return false;

        // Hot slot: start from last successful extract slot, wraparound
        int start = lastExtractSlots[col] % slotCount;

        for (int i = 0; i < slotCount && budget > 0; i++) {
            int t = (start + i) % slotCount;
            ItemStack stack = handler.getStackInSlot(t);
            if (stack.isEmpty()) continue;
            if (!passesFilter(stack, markers, isWhitelist)) continue;

            AEItemKey key = AEItemKey.of(stack);
            int toExtract = (int) Math.min(budget, stack.getCount());
            // 部分容器（如 Functional Storage 抽屉）单次 extractItem 会钳制到
            // maxStackSize，导致每 tick 只抽 64；此处循环累加至 toExtract 吃满预算
            int extracted = 0;
            while (extracted < toExtract) {
                ItemStack pulled = handler.extractItem(t, toExtract - extracted, false);
                if (pulled.isEmpty()) break;
                int got = pulled.getCount();
                if (got <= 0) break;
                extracted += got;
            }
            if (extracted == 0) continue;

            long inserted = netStorage.insert(key, extracted,
                    Actionable.MODULATE, source);
            if (inserted < extracted) {
                // Network full for this type — return leftover to closest valid slot
                ItemStack leftover = key.toStack((int)(extracted - inserted));
                for (int i2 = 0; i2 < slotCount && !leftover.isEmpty(); i2++) {
                    int t2 = (t + i2) % slotCount;
                    leftover = handler.insertItem(t2, leftover, false);
                }
                totalMoved += inserted;
                lastExtractSlots[col] = t;
                break;
            }
            budget -= inserted;
            totalMoved += inserted;
            lastExtractSlots[col] = t;
        }
        if (totalMoved > 0) setChanged();
        return totalMoved > 0;
    }

    private boolean doOutput(appeng.api.storage.MEStorage netStorage, IItemHandler handler,
                          Set<AEItemKey> markers, boolean isWhitelist, int col) {
        long budget = getEffectiveTransferRate();
        long totalMoved = 0;
        var source = new appeng.me.helpers.BaseActionSource();
        int slotCount = handler.getSlots();

        // InventoryCounts 预计算：一次性遍历 handler 建 ItemKey → 存量映射，
        // 供 Count 约束 O(1) 取存量，避免后续重复 getStackInSlot 计数。
        // Simulate-then-Commit：同时判断目标容器是否完全满，满则跳过整列。
        java.util.Map<AEItemKey, Long> invCounts = new java.util.HashMap<>();
        boolean anySpace = false;
        for (int t = 0; t < slotCount; t++) {
            ItemStack s = handler.getStackInSlot(t);
            if (!s.isEmpty()) {
                invCounts.merge(AEItemKey.of(s), (long) s.getCount(), Long::sum);
            }
            if (s.getCount() < handler.getSlotLimit(t)) anySpace = true;
        }
        if (!anySpace) return false;

        var available = netStorage.getAvailableStacks();
        for (var entry : available) {
            if (budget <= 0) break;
            if (!(entry.getKey() instanceof AEItemKey itemKey)) continue;
            if (!passesAeFilter(itemKey, markers, isWhitelist)) continue;

            long toExtract = Math.min(budget, entry.getLongValue());

            long extracted = netStorage.extract(itemKey, toExtract,
                    Actionable.MODULATE, source);
            if (extracted <= 0) continue;

            // Hot slot: start insertion from last successful insert slot
            ItemStack reference = itemKey.toStack(1);
            long remaining = extracted;
            int start = lastInsertSlots[col] % slotCount;
            for (int i = 0; i < slotCount && remaining > 0 && budget > 0; i++) {
                int t = (start + i) % slotCount;
                ItemStack existing = handler.getStackInSlot(t);
                int slotLimit = handler.getSlotLimit(t);
                if (!existing.isEmpty()) {
                    if (!ItemStack.isSameItemSameComponents(existing, reference)) continue;
                    if (existing.getCount() >= slotLimit) continue;
                }
                int space = slotLimit - (existing.isEmpty() ? 0 : existing.getCount());
                if (space <= 0) continue;
                int toInsert = (int) Math.min(remaining, space);
                ItemStack toPush = itemKey.toStack(toInsert);
                ItemStack leftover = handler.insertItem(t, toPush, false);
                long pushed = toInsert - leftover.getCount();
                if (pushed > 0) {
                    budget -= pushed;
                    totalMoved += pushed;
                    remaining -= pushed;
                    lastInsertSlots[col] = t;
                    invCounts.merge(itemKey, pushed, Long::sum);  // 同步更新预计算存量
                }
            }
            if (remaining > 0) {
                // Target full for this item — refund rest to network
                netStorage.insert(itemKey, remaining, Actionable.MODULATE, source);
            }
        }
        if (totalMoved > 0) setChanged();
        return totalMoved > 0;
    }

    // ── Fluid transfer ──────────────────────────────────────────────

    private boolean doInputFluid(appeng.api.storage.MEStorage netStorage, IFluidHandler handler,
                               Set<AEFluidKey> markers, boolean isWhitelist) {
        long budget = com.portint.PortConfig.getFluidBudget();
        long totalMoved = 0;
        var source = new appeng.me.helpers.BaseActionSource();
        int tankCount = handler.getTanks();
        if (tankCount == 0) return false;

        for (int i = 0; i < tankCount && budget > 0; i++) {
            int t = i;
            FluidStack stack = handler.getFluidInTank(t);
            if (stack.isEmpty()) continue;
            AEFluidKey key = AEFluidKey.of(stack.getFluid());
            if (key == null) continue;
            if (!passesFluidFilter(key, markers, isWhitelist)) continue;

            long toExtract = Math.min(budget, stack.getAmount());
            FluidStack drained = handler.drain(new FluidStack(stack.getFluid(), (int) toExtract),
                    IFluidHandler.FluidAction.EXECUTE);
            if (drained.isEmpty()) continue;

            long inserted = netStorage.insert(key, drained.getAmount(),
                    Actionable.MODULATE, source);
            if (inserted < drained.getAmount()) {
                FluidStack leftover = drained.copy();
                leftover.setAmount((int)(drained.getAmount() - inserted));
                handler.fill(leftover, IFluidHandler.FluidAction.EXECUTE);
                totalMoved += inserted;
                break;
            }
            budget -= inserted;
            totalMoved += inserted;
        }
        if (totalMoved > 0) setChanged();
        return totalMoved > 0;
    }

    private boolean doOutputFluid(appeng.api.storage.MEStorage netStorage, IFluidHandler handler,
                                Set<AEFluidKey> markers, boolean isWhitelist) {
        long budget = Integer.MAX_VALUE;
        long totalMoved = 0;
        var source = new appeng.me.helpers.BaseActionSource();
        var available = netStorage.getAvailableStacks();

        for (var entry : available) {
            if (budget <= 0) break;
            if (!(entry.getKey() instanceof AEFluidKey fluidKey)) continue;
            if (!passesFluidFilter(fluidKey, markers, isWhitelist)) continue;

            long toExtract = Math.min(budget, entry.getLongValue());
            long extracted = netStorage.extract(fluidKey, toExtract,
                    Actionable.MODULATE, source);
            if (extracted <= 0) continue;

            FluidStack toFill = fluidKey.toStack((int) extracted);
            int filled = handler.fill(toFill, IFluidHandler.FluidAction.EXECUTE);
            if (filled < extracted) {
                FluidStack leftover = toFill.copy();
                leftover.setAmount((int)(extracted - filled));
                netStorage.insert(fluidKey, leftover.getAmount(),
                        Actionable.MODULATE, source);
            }
            budget -= filled;
            totalMoved += filled;
            if (filled > 0) setChanged();
        }
        if (totalMoved > 0) setChanged();
        return totalMoved > 0;
    }

    /** ResourceLocations of Mekanism chemical tank items (all tiers). */
    private static final java.util.Set<net.minecraft.resources.ResourceLocation> MEK_TANK_IDS = java.util.Set.of(
        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("mekanism", "basic_chemical_tank"),
        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("mekanism", "advanced_chemical_tank"),
        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("mekanism", "elite_chemical_tank"),
        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("mekanism", "ultimate_chemical_tank")
    );

    /** @return true if any chemical was transferred */
    private boolean transferChemicals(appeng.api.storage.MEStorage netStorage, ServerLevel targetLevel,
                                     BoundTarget bt, boolean isOutput, int col, Set<AEItemKey> markers, boolean isWhitelist) {
        boolean moved = false;
        long remainingBudget = com.portint.PortConfig.getFluidBudget();
        try {
            // Collect chemical markers from ghost slots
            Set<Object> chemMarkers = collectChemicalMarkers(col);

            // ── Mekanism API reflection ──
            Class<?> chemCapClass = Class.forName("mekanism.common.capabilities.Capabilities");
            var chemCapField = chemCapClass.getField("CHEMICAL");
            var chemMultiCap = chemCapField.get(null);

            // Block chemical handler
            var blockMethod = chemMultiCap.getClass().getMethod("block");
            var blockCap = blockMethod.invoke(chemMultiCap);
            @SuppressWarnings("unchecked")
            var chemBlockCap = (net.neoforged.neoforge.capabilities.BlockCapability<?, net.minecraft.core.Direction>) blockCap;
            var handler = targetLevel.getCapability(chemBlockCap, bt.pos(), bt.side());
            if (handler == null) return false;

            // Item chemical handler (for tank items)
            var itemMethod = chemMultiCap.getClass().getMethod("item");
            @SuppressWarnings("unchecked")
            var chemItemCap = (net.neoforged.neoforge.capabilities.ItemCapability<?, Void>) itemMethod.invoke(chemMultiCap);

            // Core Mekanism types
            Class<?> actionClass = Class.forName("mekanism.api.Action");
            var actionExec = actionClass.getField("EXECUTE").get(null);
            var actionSimulate = actionClass.getField("SIMULATE").get(null);
            Class<?> chemStackClass = Class.forName("mekanism.api.chemical.ChemicalStack");
            Class<?> chemicalClass = Class.forName("mekanism.api.chemical.Chemical");
            var emptyMethod = chemStackClass.getMethod("isEmpty");
            var amountMethod = chemStackClass.getMethod("getAmount");
            var getChemicalMethod = chemStackClass.getMethod("getChemical");
            var getChemicalHolderMethod = chemStackClass.getMethod("getChemicalHolder");
            var stackCtor = chemStackClass.getConstructor(chemicalClass, long.class);

            // Handler methods
            var getChemTanks = handler.getClass().getMethod("getChemicalTanks");
            var getChemInTank = handler.getClass().getMethod("getChemicalInTank", int.class);
            var getChemTankCapacity = handler.getClass().getMethod("getChemicalTankCapacity", int.class);
            var extractChemical = handler.getClass().getMethod("extractChemical", int.class, long.class, actionClass);
            var insertChemical = handler.getClass().getMethod("insertChemical", int.class, chemStackClass, actionClass);

            // ── AppMek MekanismKey (optional) ──
            Class<?> mekKeyClass = null;
            java.lang.reflect.Method mekKeyOfMethod = null;
            java.lang.reflect.Method mekKeyGetStackMethod = null;
            try {
                mekKeyClass = Class.forName("me.ramidzkh.mekae2.ae2.MekanismKey");
                mekKeyOfMethod = mekKeyClass.getMethod("of", chemStackClass);
                mekKeyGetStackMethod = mekKeyClass.getMethod("getStack");
            } catch (ClassNotFoundException ignored) {}

            var source = new appeng.me.helpers.BaseActionSource();
            int tanks = (int) getChemTanks.invoke(handler);

            // ── Basic Chemical Tank item (fallback carrier) ──
            var basicTankItem = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("mekanism", "basic_chemical_tank"));

            if (isOutput) {
                // ═══ Output: ME storage → target chemical handler ═══
                // Priority: MekanismKey (AppMek) > AEFluidKey (gas) > chemical tank items
                var available = netStorage.getAvailableStacks();
                for (var entry : available) {
                    AEKey storageKey = entry.getKey();
                    Object chemObj = null;
                    long availableAmount;

                    if (mekKeyClass != null && mekKeyClass.isInstance(storageKey)) {
                        // AppMek native chemical storage
                        var storedStack = mekKeyGetStackMethod.invoke(storageKey);
                        chemObj = getChemicalMethod.invoke(storedStack);
                        if (chemObj == null) continue;
                        availableAmount = entry.getLongValue();
                        // Check chemical filter (use Holder for proper equals)
                        if (chemMarkers.isEmpty()) {
                            if (isWhitelist) continue;
                        } else {
                            var chemHolder = getChemicalHolderMethod.invoke(storedStack);
                            boolean chemFound = chemMarkers.contains(chemHolder);
                            if (isWhitelist ? !chemFound : chemFound) continue;
                        }
                    } else if (storageKey instanceof AEFluidKey fluidKey) {
                        // Legacy gas-as-fluid storage
                        chemObj = findChemicalForFluid(fluidKey);
                        if (chemObj == null) continue;
                        availableAmount = entry.getLongValue();
                        // Check chemical filter (use Holder for proper equals)
                        if (chemMarkers.isEmpty()) {
                            if (isWhitelist) continue;
                        } else {
                            var chemHolder = getAsHolderFromChemical(chemObj);
                            boolean chemFound = chemMarkers.contains(chemHolder);
                            if (isWhitelist ? !chemFound : chemFound) continue;
                        }
                    } else if (storageKey instanceof AEItemKey itemKey) {
                        // Mekanism chemical tank items in item storage
                        var rl = itemKey.getItem().builtInRegistryHolder().key().location();
                        if (!MEK_TANK_IDS.contains(rl)) continue;

                        // Extract one tank to check its contents
                        long extracted = netStorage.extract(storageKey, 1,
                                Actionable.MODULATE, source);
                        if (extracted <= 0) continue;
                        var tankStack = itemKey.toStack();

                        // Get chemical from the tank's item capability
                        var tankHandler = getTankCapability(tankStack, chemItemCap);
                        if (tankHandler == null) {
                            netStorage.insert(storageKey, 1, Actionable.MODULATE, source);
                            continue;
                        }
                        int tankTanks = (int) tankHandler.getClass()
                                .getMethod("getChemicalTanks").invoke(tankHandler);
                        if (tankTanks == 0) {
                            netStorage.insert(storageKey, 1, Actionable.MODULATE, source);
                            continue;
                        }
                        var tankChem = tankHandler.getClass()
                                .getMethod("getChemicalInTank", int.class).invoke(tankHandler, 0);
                        if (tankChem == null || (boolean) emptyMethod.invoke(tankChem)) {
                            netStorage.insert(storageKey, 1, Actionable.MODULATE, source);
                            continue;
                        }
                        chemObj = getChemicalMethod.invoke(tankChem);

                        // Check chemical filter (use Holder for proper equals)
                        if (chemMarkers.isEmpty()) {
                            if (isWhitelist) {
                                netStorage.insert(storageKey, 1, Actionable.MODULATE, source);
                                continue;
                            }
                        } else {
                            var chemHolder = getChemicalHolderMethod.invoke(tankChem);
                            boolean chemFound = chemMarkers.contains(chemHolder);
                            if (isWhitelist ? !chemFound : chemFound) {
                                netStorage.insert(storageKey, 1, Actionable.MODULATE, source);
                                continue;
                            }
                        }

                        // Extract the rest of the stack
                        long remainingCount = entry.getLongValue();
                        if (remainingCount > 0) {
                            netStorage.extract(storageKey, remainingCount,
                                    Actionable.MODULATE, source);
                        }

                        // Drain all chemical from all extracted tanks, then store empties back
                        long totalDrained = (long) amountMethod.invoke(tankChem);
                        for (int i = 1; i <= remainingCount; i++) {
                            var extraStack = itemKey.toStack();
                            var extraHandler = getTankCapability(extraStack, chemItemCap);
                            if (extraHandler != null) {
                                var extraChem = extraHandler.getClass()
                                        .getMethod("getChemicalInTank", int.class).invoke(extraHandler, 0);
                                if (extraChem != null && !(boolean) emptyMethod.invoke(extraChem)) {
                                    totalDrained += (long) amountMethod.invoke(extraChem);
                                } else {
                                    // Empty tank — store back
                                    netStorage.insert(AEItemKey.of(extraStack), 1,
                                            Actionable.MODULATE, source);
                                }
                            }
                        }

                        // Insert all chemical into target handler, tank by tank
                        var toInsert = stackCtor.newInstance(chemObj, totalDrained);
                        long inserted = 0;
                        for (int ti = 0; ti < tanks; ti++) {
                            long currentAmt = (long) amountMethod.invoke(toInsert);
                            if (currentAmt <= 0) break;
                            var leftover = insertChemical.invoke(handler, ti, toInsert, actionExec);
                            if ((boolean) emptyMethod.invoke(leftover)) {
                                inserted += currentAmt;
                                break;
                            }
                            long leftoverAmt = (long) amountMethod.invoke(leftover);
                            inserted += (currentAmt - leftoverAmt);
                            toInsert = leftover;
                        }
                        if (inserted > 0) { setChanged(); moved = true; }

                        // Refund any uninserted chemical as filled tank items
                        if (inserted < totalDrained) {
                            var refundChem = stackCtor.newInstance(chemObj, totalDrained - inserted);
                            storeChemicalAsTank(refundChem, basicTankItem,
                                    chemStackClass, actionExec, chemItemCap,
                                    netStorage, source, handler, 0);
                        }
                        break; // One chemical type per tick
                    } else {
                        continue;
                    }

                    // ── Common: extract from ME, insert into target handler ──
                    // First, compute total remaining capacity across all tanks
                    long totalRemainingCapacity = 0;
                    for (int ti = 0; ti < tanks; ti++) {
                        long cap = (long) getChemTankCapacity.invoke(handler, ti);
                        var existing = getChemInTank.invoke(handler, ti);
                        long current = 0;
                        if (existing != null && !(boolean) emptyMethod.invoke(existing)) {
                            current = (long) amountMethod.invoke(existing);
                        }
                        long remaining = cap - current;
                        if (remaining > 0) totalRemainingCapacity += remaining;
                    }
                    if (totalRemainingCapacity <= 0) continue; // all tanks full — skip

                    long toExtract = Math.min(availableAmount, Math.min(totalRemainingCapacity, remainingBudget));
                    long extracted = netStorage.extract(storageKey, toExtract,
                            Actionable.MODULATE, source);
                    if (extracted <= 0) continue;

                    var toInsert = stackCtor.newInstance(chemObj, extracted);
                    long totalInserted = 0;
                    for (int ti = 0; ti < tanks; ti++) {
                        long currentAmt = (long) amountMethod.invoke(toInsert);
                        if (currentAmt <= 0) break;
                        var leftover = insertChemical.invoke(handler, ti, toInsert, actionExec);
                        if ((boolean) emptyMethod.invoke(leftover)) {
                            totalInserted += currentAmt;
                            break;
                        }
                        long leftoverAmt = (long) amountMethod.invoke(leftover);
                        totalInserted += (currentAmt - leftoverAmt);
                        toInsert = leftover;
                    }

                    if (totalInserted > 0) { setChanged(); moved = true; }
                    if (totalInserted < extracted) {
                        netStorage.insert(storageKey, extracted - totalInserted,
                                Actionable.MODULATE, source);
                    }
                    remainingBudget -= totalInserted;
                    break;
                }
            } else {
                // ═══ Input: target chemical handler → ME storage ═══
                for (int t = 0; t < tanks; t++) {
                    var stack = getChemInTank.invoke(handler, t);
                    if (stack == null) continue;
                    boolean empty = (boolean) emptyMethod.invoke(stack);
                    if (empty) continue;

                    long amount = (long) amountMethod.invoke(stack);

                    // Check chemical filter (use Holder for proper equals)
                    var chemObj = getChemicalMethod.invoke(stack);
                    var chemHolder = getChemicalHolderMethod.invoke(stack);
                    if (chemMarkers.isEmpty()) {
                        if (isWhitelist) continue;
                    } else {
                        boolean chemFound = chemMarkers.contains(chemHolder);
                        if (isWhitelist ? !chemFound : chemFound) continue;
                    }

                    long toExtract = Math.min(amount, remainingBudget);
                    var drained = extractChemical.invoke(handler, t, toExtract, actionExec);

                    boolean drainedEmpty = (boolean) emptyMethod.invoke(drained);
                    if (drainedEmpty) continue;

                    long drainedAmount = (long) amountMethod.invoke(drained);
                    if (drainedAmount <= 0) continue;

                    if (mekKeyClass != null && mekKeyOfMethod != null) {
                        // AppMek: native MekanismKey storage
                        var mekKey = mekKeyOfMethod.invoke(null, drained);
                        long inserted = netStorage.insert((AEKey) mekKey, drainedAmount,
                                Actionable.MODULATE, source);
                        if (inserted > 0) { setChanged(); moved = true; }
                        remainingBudget -= inserted;
                        if (inserted < drainedAmount) {
                            var chemRefund = getChemicalMethod.invoke(drained);
                            var refundStack = stackCtor.newInstance(chemRefund, drainedAmount - inserted);
                            insertChemical.invoke(handler, t, refundStack, actionExec);
                        }
                    } else {
                        // Fallback: store chemical in basic_chemical_tank items
                        remainingBudget -= storeChemicalAsTank(drained, basicTankItem,
                                chemStackClass, actionExec, chemItemCap,
                                netStorage, source, handler, t);
                    }
                    break;
                }
            }
        } catch (Exception e) {
            com.portint.PortableInterface.LOGGER.error("Chemical transfer failed", e);
            return false;
        }
        return moved;
    }

    /** Get IChemicalHandler from an ItemStack via Mekanism item capability (reflection). */
    private Object getTankCapability(ItemStack stack,
                                      net.neoforged.neoforge.capabilities.ItemCapability<?, Void> chemItemCap) {
        try {
            // ItemStack.getCapability(ItemCapability<T, Void>) → T
            var getCapMethod = ItemStack.class.getMethod("getCapability",
                    net.neoforged.neoforge.capabilities.ItemCapability.class);
            return getCapMethod.invoke(stack, chemItemCap);
        } catch (Exception e) {
            return null;
        }
    }

    /** Store a ChemicalStack as filled basic_chemical_tank items into ME storage. */
    private long storeChemicalAsTank(Object drained,
                                      net.minecraft.world.item.Item basicTankItem,
                                      Class<?> chemStackClass, Object actionExec,
                                      net.neoforged.neoforge.capabilities.ItemCapability<?, Void> chemItemCap,
                                      appeng.api.storage.MEStorage netStorage,
                                      appeng.api.networking.security.IActionSource source,
                                      Object handler, int tankIndex) throws Exception {
        var emptyMethod = chemStackClass.getMethod("isEmpty");
        var amountMethod = chemStackClass.getMethod("getAmount");
        var getChemicalMethod = chemStackClass.getMethod("getChemical");

        long original = (long) amountMethod.invoke(drained);
        long remaining = original;
        var chemObj = getChemicalMethod.invoke(drained);

        // Basic tank capacity: 64,000 mB. One tank per batch.
        long tankCapacity = 64000;
        while (remaining > 0) {
            long toFill = Math.min(remaining, tankCapacity);
            var tankStack = new ItemStack(basicTankItem, 1);

            var tankHandler = getTankCapability(tankStack, chemItemCap);
            if (tankHandler == null) break;

            var csCtor = chemStackClass.getConstructor(
                    chemStackClass.getMethod("getChemical").getReturnType(), long.class);
            var toInsert = csCtor.newInstance(chemObj, toFill);

            var leftover = tankHandler.getClass()
                    .getMethod("insertChemical", int.class, chemStackClass, actionExec.getClass())
                    .invoke(tankHandler, 0, toInsert, actionExec);

            long actuallyStored = toFill;
            if (leftover != null && !(boolean) emptyMethod.invoke(leftover)) {
                actuallyStored = toFill - (long) amountMethod.invoke(leftover);
            }
            if (actuallyStored <= 0) break;

            var cellKey = AEItemKey.of(tankStack);
            long inserted = netStorage.insert(cellKey, 1, Actionable.MODULATE, source);
            if (inserted <= 0) {
                // ME full — refund to source handler
                refundChemicalToHandler(tankHandler, 0, actuallyStored,
                        chemStackClass, actionExec, handler, tankIndex);
                break;
            }
            remaining -= actuallyStored;
        }

        if (remaining < original) {
            setChanged();
            // Refund any leftover
            if (remaining > 0 && handler != null) {
                var refundStack = chemStackClass.getConstructor(chemObj.getClass(), long.class)
                        .newInstance(chemObj, remaining);
                handler.getClass().getMethod("insertChemical", int.class, chemStackClass, actionExec.getClass())
                        .invoke(handler, tankIndex, refundStack, actionExec);
            }
        }
        return original - remaining;
    }

    /** Refund chemical from a tank item back to a block handler. */
    private void refundChemicalToHandler(Object tankHandler, int tankSlot, long amount,
                                          Class<?> chemStackClass, Object actionExec,
                                          Object blockHandler, int blockTank) throws Exception {
        var emptyMethod = chemStackClass.getMethod("isEmpty");
        var amountMethod = chemStackClass.getMethod("getAmount");
        var extractMethod = tankHandler.getClass()
                .getMethod("extractChemical", int.class, long.class, actionExec.getClass());
        var drained = extractMethod.invoke(tankHandler, tankSlot, amount, actionExec);
        if (drained == null || (boolean) emptyMethod.invoke(drained)) return;
        long drainedAmt = (long) amountMethod.invoke(drained);
        if (drainedAmt <= 0) return;
        if (blockHandler != null) {
            blockHandler.getClass().getMethod("insertChemical", int.class, chemStackClass, actionExec.getClass())
                    .invoke(blockHandler, blockTank, drained, actionExec);
        }
    }

    /** Find Mekanism Chemical from an AEFluidKey by matching fluid→chemical ResourceLocation. */
    private Object findChemicalForFluid(AEFluidKey fluidKey) {
        try {
            // Mekanism 10.7: MekanismAPI.CHEMICAL_REGISTRY (DefaultedRegistry<Chemical>)
            var apiClass = Class.forName("mekanism.api.MekanismAPI");
            var registry = apiClass.getField("CHEMICAL_REGISTRY").get(null);
            // registry.get(ResourceLocation)
            var fluidId = fluidKey.getFluid().builtInRegistryHolder().key().location();
            var getMethod = registry.getClass().getMethod("get",
                    net.minecraft.resources.ResourceLocation.class);
            var chemical = getMethod.invoke(registry, fluidId);
            if (chemical != null) return chemical;

            // Fallback: try alternate namespaces (mekanismgenerators etc)
            // and strip "flowing_" prefix
            String path = fluidId.getPath();
            if (path.startsWith("flowing_")) {
                path = path.substring(8);
                var fallbackId = net.minecraft.resources.ResourceLocation.parse(fluidId.getNamespace() + ":" + path);
                return getMethod.invoke(registry, fallbackId);
            }
        } catch (Exception e) {
            // ignored
        }
        return null;
    }

    // ── Filter helpers ──────────────────────────────────────────────

    private static final int MARKERS_PER_COL = 27;

    private Set<AEItemKey> collectMarkerKeys(int col) {
        Set<AEItemKey> markers = new HashSet<>();
        for (int i = 0; i < MARKERS_PER_COL; i++) {
            ItemStack m = markerInv.getItem(col * MARKERS_PER_COL + i);
            if (!m.isEmpty()) {
                markers.add(AEItemKey.of(m));
            }
        }
        return markers;
    }

    private boolean passesFilter(ItemStack stack, Set<AEItemKey> markers,
                                  boolean isWhitelist) {
        if (markers.isEmpty()) return !isWhitelist;
        AEItemKey key = AEItemKey.of(stack);
        boolean found = markers.contains(key);
        return isWhitelist ? found : !found;
    }

    private boolean passesAeFilter(AEItemKey key, Set<AEItemKey> markers,
                                    boolean isWhitelist) {
        if (markers.isEmpty()) return !isWhitelist;
        boolean found = markers.contains(key);
        return isWhitelist ? found : !found;
    }

    /** Collect chemicals from tank items in ghost marker slots for a given column. */
    private Set<Object> collectChemicalMarkers(int col) {
        Set<Object> markers = new HashSet<>();
        for (int i = 0; i < MARKERS_PER_COL; i++) {
            ItemStack m = markerInv.getItem(col * MARKERS_PER_COL + i);
            if (!m.isEmpty()) {
                ResourceLocation rl = m.getItem().builtInRegistryHolder().key().location();
                if (MEK_TANK_IDS.contains(rl)) {
                    Object chem = extractChemicalFromTankItem(m);
                    if (chem != null) markers.add(chem);
                }
            }
        }
        return markers;
    }

    /** Collect AEFluidKey markers from ghost slots tagged with portint_fluid_id. */
    private Set<AEFluidKey> collectFluidMarkerKeys(int col) {
        Set<AEFluidKey> markers = new HashSet<>();
        for (int i = 0; i < MARKERS_PER_COL; i++) {
            ItemStack m = markerInv.getItem(col * MARKERS_PER_COL + i);
            if (m.isEmpty()) continue;
            var custom = m.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            if (custom == null) continue;
            var tag = custom.copyTag();
            if (tag.contains("portint_fluid_id")) {
                String fluidId = tag.getString("portint_fluid_id");
                if (!fluidId.isEmpty()) {
                    var fluid = net.minecraft.core.registries.BuiltInRegistries.FLUID
                            .get(ResourceLocation.parse(fluidId));
                    if (fluid != null) {
                        markers.add(AEFluidKey.of(fluid));
                    }
                }
            }
        }
        return markers;
    }

    private boolean passesFluidFilter(AEFluidKey key, Set<AEFluidKey> markers,
                                       boolean isWhitelist) {
        if (markers.isEmpty()) return !isWhitelist;
        boolean found = markers.contains(key);
        return isWhitelist ? found : !found;
    }

    /** Extract the Chemical Holder from a Mekanism tank ItemStack via reflection. */
    private Object extractChemicalFromTankItem(ItemStack tankStack) {
        try {
            Class<?> chemCapClass = Class.forName("mekanism.common.capabilities.Capabilities");
            var chemCapField = chemCapClass.getField("CHEMICAL");
            var chemMultiCap = chemCapField.get(null);
            var itemMethod = chemMultiCap.getClass().getMethod("item");
            @SuppressWarnings("unchecked")
            var chemItemCap = (net.neoforged.neoforge.capabilities.ItemCapability<?, Void>) itemMethod.invoke(chemMultiCap);

            var getCapMethod = ItemStack.class.getMethod("getCapability",
                net.neoforged.neoforge.capabilities.ItemCapability.class);
            var handler = getCapMethod.invoke(tankStack, chemItemCap);
            if (handler == null) return null;

            int tanks = (int) handler.getClass().getMethod("getChemicalTanks").invoke(handler);
            if (tanks == 0) return null;

            var chemStack = handler.getClass().getMethod("getChemicalInTank", int.class).invoke(handler, 0);
            if (chemStack == null) return null;

            boolean empty = (boolean) Class.forName("mekanism.api.chemical.ChemicalStack")
                .getMethod("isEmpty").invoke(chemStack);
            if (empty) return null;

            // Return Holder<Chemical> for proper equals-based comparison
            return chemStack.getClass().getMethod("getChemicalHolder").invoke(chemStack);
        } catch (Exception e) {
            return null;
        }
    }

    /** Get Holder<Chemical> from a Chemical object (legacy gas-as-fluid path). */
    private Object getAsHolderFromChemical(Object chemical) {
        try {
            // Try builtInRegistryHolder() first (Chemical extends from NeoForge registry entry)
            return chemical.getClass().getMethod("builtInRegistryHolder").invoke(chemical);
        } catch (NoSuchMethodException e) {
            try {
                // Fallback: try getAsHolder()
                return chemical.getClass().getMethod("getAsHolder").invoke(chemical);
            } catch (Exception e2) {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Force chunk loading
    // ═════════════════════════════════════════════════════════════════

    private void syncForceLoads() {
        if (!(level instanceof ServerLevel serverLevel)) return;

        boolean dimCard = hasDimCard() && com.portint.PortConfig.CHUNK_LOADING_ENABLED.get();
        Set<Long> neededChunks = new HashSet<>();

        if (dimCard) {
            for (int col = 0; col < 9; col++) {
                ItemStack bs = bindingInv.getItem(col);
                if (!bs.isEmpty() && bs.is(ModItems.BINDING_CARD.get())) {
                    var opt = bs.get(ModDataComponents.BOUND_TARGET.get());
                    if (opt != null && opt.isPresent()) {
                        BoundTarget t = opt.get();
                        ServerLevel tl = serverLevel.getServer().getLevel(t.dimension());
                        if (tl != null) {
                            ChunkPos cp = new ChunkPos(t.pos());
                            long key = ChunkPos.asLong(cp.x, cp.z);
                            neededChunks.add(key);
                            if (!forcedChunks.contains(key)) {
                                tl.setChunkForced(cp.x, cp.z, true);
                            }
                        }
                    }
                }
            }
        }

        // Release chunks that are no longer needed
        Iterator<Long> it = forcedChunks.iterator();
        while (it.hasNext()) {
            long key = it.next();
            if (!neededChunks.contains(key)) {
                ChunkPos cp = new ChunkPos(key);
                serverLevel.setChunkForced(cp.x, cp.z, false);
                it.remove();
            }
        }
        // Add new needed chunks
        for (long key : neededChunks) {
            if (!forcedChunks.contains(key)) {
                ChunkPos cp = new ChunkPos(key);
                serverLevel.setChunkForced(cp.x, cp.z, true);
                forcedChunks.add(key);
            }
        }
    }

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
        // AE2 InterfaceLogic data
        logic.writeToNBT(tag, reg);
        // Portint custom data
        tag.put("UpgradeInv",
                ContainerHelper.saveAllItems(new CompoundTag(), upgradeInv.getItems(), reg));
        tag.put("BindingInv",
                ContainerHelper.saveAllItems(new CompoundTag(), bindingInv.getItems(), reg));
        tag.put("MarkerInv",
                ContainerHelper.saveAllItems(new CompoundTag(), markerInv.getItems(), reg));
        tag.putByte("OutputModes", packBooleansByte(outputModes));
        tag.putByte("WhitelistModes", packBooleansByte(whitelistModes));
        tag.putIntArray("BindingPriorities", bindingPriorities);
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadTag(tag, reg);
        // AE2 InterfaceLogic data
        logic.readFromNBT(tag, reg);
        // Portint custom data
        if (tag.contains("UpgradeInv"))
            ContainerHelper.loadAllItems(tag.getCompound("UpgradeInv"),
                    upgradeInv.getItems(), reg);
        if (tag.contains("BindingInv"))
            ContainerHelper.loadAllItems(tag.getCompound("BindingInv"),
                    bindingInv.getItems(), reg);
        if (tag.contains("MarkerInv")) {
            CompoundTag markerTag = tag.getCompound("MarkerInv");
            int oldSize = markerTag.getInt("Size");
            if (oldSize == 81) {
                // Migrate old 9-per-col to new 27-per-col format
                CompoundTag newTag = new CompoundTag();
                newTag.putInt("Size", 243);
                ListTag oldItems = markerTag.getList("Items", 10);
                ListTag newItems = new ListTag();
                for (int i = 0; i < oldItems.size(); i++) {
                    CompoundTag itemTag = oldItems.getCompound(i).copy();
                    int oldSlot = itemTag.getByte("Slot") & 0xFF;
                    int oldCol = oldSlot / 9;
                    int oldIdx = oldSlot % 9;
                    int newSlot = oldCol * 27 + oldIdx;
                    itemTag.putByte("Slot", (byte) newSlot);
                    newItems.add(itemTag);
                }
                newTag.put("Items", newItems);
                ContainerHelper.loadAllItems(newTag, markerInv.getItems(), reg);
            } else {
                ContainerHelper.loadAllItems(markerTag, markerInv.getItems(), reg);
            }
        }
        unpackBooleansByte(outputModes, tag.getByte("OutputModes"));
        unpackBooleansByte(whitelistModes, tag.getByte("WhitelistModes"));
        // 修复历史存档：输出模式的列必须强制白名单
        for (int i = 0; i < BINDING_COUNT; i++) {
            if (outputModes[i]) whitelistModes[i] = true;
        }
        if (tag.contains("BindingPriorities")) {
            int[] loaded = tag.getIntArray("BindingPriorities");
            for (int i = 0; i < Math.min(loaded.length, BINDING_COUNT); i++) {
                bindingPriorities[i] = loaded[i];
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Cleanup
    // ═════════════════════════════════════════════════════════════════

    public void dropContents() {
        if (level == null) return;
        for (int i = 0; i < UPGRADE_COUNT; i++)
            net.minecraft.world.Containers.dropItemStack(level,
                    worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                    upgradeInv.getItem(i));
        for (int i = 0; i < BINDING_COUNT; i++)
            net.minecraft.world.Containers.dropItemStack(level,
                    worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                    bindingInv.getItem(i));
        releaseAllForcedChunks();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        releaseAllForcedChunks();
    }

    // ═════════════════════════════════════════════════════════════════
    //  Helpers
    // ═════════════════════════════════════════════════════════════════

    private static int packBooleans(boolean[] arr) {
        int v = 0;
        for (int i = 0; i < 9; i++) if (arr[i]) v |= (1 << i);
        return v;
    }

    private static void unpackBooleans(boolean[] arr, int v) {
        for (int i = 0; i < 9; i++) arr[i] = ((v >> i) & 1) == 1;
    }

    private static byte packBooleansByte(boolean[] arr) {
        byte v = 0;
        for (int i = 0; i < 9; i++) if (arr[i]) v |= (1 << i);
        return v;
    }

    private static void unpackBooleansByte(boolean[] arr, byte v) {
        for (int i = 0; i < 9; i++) arr[i] = ((v >> i) & 1) == 1;
    }
}

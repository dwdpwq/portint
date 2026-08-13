package com.portint.block;

import com.mojang.serialization.MapCodec;
import com.portint.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class ExternalStorageBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public ExternalStorageBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ACTIVE, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(ExternalStorageBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, ACTIVE);
    }

    @Override
    public RenderShape getRenderShape(BlockState s) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING,
                ctx.getNearestLookingDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ExternalStorageBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level l, BlockState s, BlockEntityType<T> t) {
        if (l.isClientSide) {
            return createTickerHelper(t, ModBlockEntities.EXTERNAL_STORAGE_BLOCK_ENTITY.get(),
                    ExternalStorageBlockEntity::clientTick);
        }
        return createTickerHelper(t, ModBlockEntities.EXTERNAL_STORAGE_BLOCK_ENTITY.get(),
                ExternalStorageBlockEntity::serverTick);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                             BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ExternalStorageBlockEntity tile) {
                tile.dropContents();
            }
            super.onRemove(state, level, pos, newState, moved);
        }
    }

    // ── Interaction ─────────────────────────────────────────────────

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ExternalStorageBlockEntity tile))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        // Binding card → insert into binding inventory
        if (stack.is(ModItems.BINDING_CARD.get())) {
            var inv = tile.getBindingInventory();
            for (int i = 0; i < ExternalStorageBlockEntity.BINDING_COUNT; i++) {
                if (inv.getItem(i).isEmpty()) {
                    ItemStack c = stack.copy();
                    c.setCount(1);
                    inv.setItem(i, c);
                    stack.shrink(1);
                    tile.setChanged();
                    return ItemInteractionResult.CONSUME;
                }
            }
            return ItemInteractionResult.FAIL;
        }

        // Range / dimension cards → rejected (only capacity cards allowed in upgrade slots)
        if (stack.is(ModItems.RANGE_CARD.get()) || stack.is(ModItems.DIMENSION_CARD.get())) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("gui.portint.only_capacity_card"),
                    true);
            return ItemInteractionResult.FAIL;
        }

        // Capacity card → insert into upgrade inventory
        if (stack.is(ModItems.CAPACITY_CARD.get())) {
            var inv = tile.getUpgradeInventory();
            for (int i = 0; i < ExternalStorageBlockEntity.UPGRADE_COUNT; i++) {
                if (inv.getItem(i).isEmpty()) {
                    ItemStack c = stack.copy();
                    c.setCount(1);
                    inv.setItem(i, c);
                    stack.shrink(1);
                    tile.setChanged();
                    return ItemInteractionResult.CONSUME;
                }
            }
            return ItemInteractionResult.FAIL;
        }

        // Other items → open menu
        tile.openCustomMenu(player);
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ExternalStorageBlockEntity tile) {
            tile.openCustomMenu(player);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    // ── Rotation ────────────────────────────────────────────────────

    @Override
    public BlockState rotate(BlockState s, Rotation r) {
        return s.setValue(FACING, r.rotate(s.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState s, Mirror m) {
        return s.rotate(m.getRotation(s.getValue(FACING)));
    }
}

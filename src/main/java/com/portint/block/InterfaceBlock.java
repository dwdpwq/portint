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
import net.minecraft.world.level.BlockGetter;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class InterfaceBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public InterfaceBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ACTIVE, false));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(InterfaceBlock::new);
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
        return new InterfaceBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level l, BlockState s, BlockEntityType<T> t) {
        if (l.isClientSide)
            return createTickerHelper(t, ModBlockEntities.INTERFACE_BLOCK_ENTITY.get(),
                    InterfaceBlockEntity::clientTick);
        return createTickerHelper(t, ModBlockEntities.INTERFACE_BLOCK_ENTITY.get(),
                InterfaceBlockEntity::serverTick);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                             BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof InterfaceBlockEntity tile) {
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
        if (!(be instanceof InterfaceBlockEntity tile))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        // Place binding card into first empty binding slot
        if (stack.is(ModItems.BINDING_CARD.get())) {
            var inv = tile.getBindingInventory();
            for (int i = 0; i < 9; i++) {
                if (inv.getItem(i).isEmpty()) {
                    ItemStack c = stack.copy();
                    c.setCount(1);
                    inv.setItem(i, c);
                    stack.shrink(1);
                    tile.setChanged();
                    return ItemInteractionResult.CONSUME;
                }
            }
            // Binding slots full → open menu to manage existing bindings
            tile.openCustomMenu(player);
            return ItemInteractionResult.CONSUME;
        }

        // Place upgrade cards (both types accepted in both slots)
        if (stack.is(ModItems.RANGE_CARD.get()) || stack.is(ModItems.DIMENSION_CARD.get())) {
            var inv = tile.getUpgradeInventory();
            for (int i = 0; i < 2; i++) {
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

        // Place long card into upgrade slot 1 only (slot 0 is for acceleration cards)
        if (stack.is(ModItems.LONG_CARD.get())) {
            var inv = tile.getUpgradeInventory();
            if (inv.getItem(1).isEmpty()) {
                ItemStack c = stack.copy();
                c.setCount(1);
                inv.setItem(1, c);
                stack.shrink(1);
                tile.setChanged();
                return ItemInteractionResult.CONSUME;
            }
            return ItemInteractionResult.FAIL;
        }

        // For any other item in hand, open the custom portint menu
        tile.openCustomMenu(player);
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof InterfaceBlockEntity tile) {
            // Shift+right-click = AE2 native Interface menu
            if (player.isShiftKeyDown()) {
                tile.openAe2Menu(player);
            } else {
                tile.openCustomMenu(player);
            }
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

package com.portint.item;

import com.portint.BoundTarget;
import com.portint.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;

public class BindingCardItem extends Item {

    public BindingCardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        ItemStack stack = context.getItemInHand();

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        ServerLevel serverLevel = (ServerLevel) level;
        BlockState clickedState = level.getBlockState(clickedPos);

        BlockEntity be = level.getBlockEntity(clickedPos);

        if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) {
            if (be != null) {
                // Shift + right-click on entity block → bind
                ResourceKey<Level> dim = serverLevel.dimension();
                BoundTarget target = new BoundTarget(dim, clickedPos, context.getClickedFace());
                stack.set(ModDataComponents.BOUND_TARGET.get(), Optional.of(target));
                stack.set(ModDataComponents.BOUND_BLOCK_NAME.get(),
                    Optional.of(clickedState.getBlock().getName().getString()));
                context.getPlayer().displayClientMessage(
                    Component.translatable("item.portint.binding_card.bound",
                        clickedPos.getX(), clickedPos.getY(), clickedPos.getZ(),
                        dim.location().toString()
                    ).withStyle(ChatFormatting.GREEN),
                    true
                );
                level.playSound(null, clickedPos, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.PLAYERS, 0.5f, 1.5f);
            } else {
                // Shift + right-click on normal block → clear
                stack.remove(ModDataComponents.BOUND_TARGET.get());
                stack.remove(ModDataComponents.BOUND_BLOCK_NAME.get());
                context.getPlayer().displayClientMessage(
                    Component.translatable("item.portint.binding_card.cleared").withStyle(ChatFormatting.GRAY),
                    true
                );
                level.playSound(null, clickedPos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.5f, 1.0f);
            }
            return InteractionResult.CONSUME;
        }

        // Normal right-click: no-op
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(ModDataComponents.BOUND_TARGET.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Optional<BoundTarget> target = stack.get(ModDataComponents.BOUND_TARGET.get());
        if (target != null && target.isPresent()) {
            BoundTarget bt = target.get();
            tooltip.add(Component.translatable("item.portint.binding_card.tooltip.bound",
                bt.pos().getX(), bt.pos().getY(), bt.pos().getZ(),
                bt.dimension().location().toString()
            ).withStyle(ChatFormatting.AQUA));

            // Append bound block name
            Optional<String> blockName = stack.get(ModDataComponents.BOUND_BLOCK_NAME.get());
            if (blockName != null && blockName.isPresent()) {
                tooltip.add(Component.literal(blockName.get()).withStyle(ChatFormatting.YELLOW));
            }
        } else {
            tooltip.add(Component.translatable("item.portint.binding_card.tooltip.empty").withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("item.portint.binding_card.tooltip.sneak_clear").withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}

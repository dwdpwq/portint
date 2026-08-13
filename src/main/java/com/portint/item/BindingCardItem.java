package com.portint.item;

import com.portint.BoundTarget;
import com.portint.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
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
                stack.remove(ModDataComponents.PRIORITY.get());
                stack.remove(ModDataComponents.FILTER_MODE.get());
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
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !stack.has(ModDataComponents.BOUND_TARGET.get())) {
            return InteractionResultHolder.pass(stack);
        }

        // 重置绑定卡：清除绑定信息
        stack.remove(ModDataComponents.BOUND_TARGET.get());
        stack.remove(ModDataComponents.BOUND_BLOCK_NAME.get());
        stack.remove(ModDataComponents.PRIORITY.get());
        stack.remove(ModDataComponents.FILTER_MODE.get());
        player.displayClientMessage(
            Component.translatable("item.portint.binding_card.cleared").withStyle(ChatFormatting.GRAY),
            true
        );
        level.playSound(null, player.blockPosition(),
            SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.5f, 1.0f);
        return InteractionResultHolder.success(stack);
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

            // Priority
            int priority = getPriority(stack);
            tooltip.add(Component.translatable("item.portint.binding_card.tooltip.priority", priority)
                .withStyle(priority >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED));

            // Filter mode
            byte mode = stack.getOrDefault(ModDataComponents.FILTER_MODE.get(), (byte) 0);
            String modeKey = mode == 1 ? "item.portint.binding_card.filter_mode.whitelist"
                                       : "item.portint.binding_card.filter_mode.blacklist";
            tooltip.add(Component.translatable(modeKey).withStyle(
                mode == 1 ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("item.portint.binding_card.tooltip.empty").withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("item.portint.binding_card.tooltip.sneak_clear").withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }

    public static int getPriority(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.PRIORITY.get(), 0);
    }

    public static byte getFilterMode(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.FILTER_MODE.get(), (byte) 0);
    }
}

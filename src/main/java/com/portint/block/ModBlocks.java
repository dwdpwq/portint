package com.portint.block;

import com.portint.PortableInterface;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS =
        DeferredRegister.createBlocks(PortableInterface.MOD_ID);

    public static final DeferredRegister.Items BLOCK_ITEMS =
        DeferredRegister.createItems(PortableInterface.MOD_ID);

    public static final DeferredBlock<Block> INTERFACE_BLOCK = BLOCKS.register("interface_block",
        () -> new InterfaceBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(3.0f, 6.0f)
            .sound(SoundType.METAL)
            .requiresCorrectToolForDrops()));

    public static final DeferredItem<BlockItem> INTERFACE_BLOCK_ITEM =
        BLOCK_ITEMS.registerSimpleBlockItem(INTERFACE_BLOCK);

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        BLOCK_ITEMS.register(bus);
    }
}

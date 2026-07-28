package com.portint.block;

import com.portint.PortableInterface;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, PortableInterface.MOD_ID);

    public static final Supplier<BlockEntityType<InterfaceBlockEntity>> INTERFACE_BLOCK_ENTITY =
        BLOCK_ENTITIES.register("interface_block_entity",
            () -> BlockEntityType.Builder.of(InterfaceBlockEntity::new, ModBlocks.INTERFACE_BLOCK.get())
                .build(null));

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }
}

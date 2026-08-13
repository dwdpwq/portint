package com.portint;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Optional;
import java.util.function.Supplier;

public class ModDataComponents {
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS =
        DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, PortableInterface.MOD_ID);

    public static final Supplier<DataComponentType<Optional<BoundTarget>>> BOUND_TARGET =
        COMPONENTS.register("bound_target",
            () -> DataComponentType.<Optional<BoundTarget>>builder()
                .persistent(BoundTarget.CODEC.optionalFieldOf("target").codec())
                .networkSynchronized(BoundTarget.STREAM_CODEC.apply(ByteBufCodecs::optional))
                .build()
        );

    public static final Supplier<DataComponentType<Boolean>> HAS_RANGE_CARD =
        COMPONENTS.register("has_range_card",
            () -> DataComponentType.<Boolean>builder()
                .persistent(Codec.BOOL)
                .networkSynchronized(ByteBufCodecs.BOOL)
                .build()
        );

    public static final Supplier<DataComponentType<Boolean>> HAS_DIM_CARD =
        COMPONENTS.register("has_dim_card",
            () -> DataComponentType.<Boolean>builder()
                .persistent(Codec.BOOL)
                .networkSynchronized(ByteBufCodecs.BOOL)
                .build()
        );

    public static final Supplier<DataComponentType<Optional<String>>> BOUND_BLOCK_NAME =
        COMPONENTS.register("bound_block_name",
            () -> DataComponentType.<Optional<String>>builder()
                .persistent(Codec.STRING.optionalFieldOf("name").codec())
                .networkSynchronized(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs::optional))
                .build()
        );

    public static final Supplier<DataComponentType<Integer>> PRIORITY =
        COMPONENTS.register("priority",
            () -> DataComponentType.<Integer>builder()
                .persistent(Codec.INT)
                .networkSynchronized(ByteBufCodecs.INT)
                .build()
        );

    /** 0 = blacklist, 1 = whitelist */
    public static final Supplier<DataComponentType<Byte>> FILTER_MODE =
        COMPONENTS.register("filter_mode",
            () -> DataComponentType.<Byte>builder()
                .persistent(Codec.BYTE)
                .networkSynchronized(ByteBufCodecs.BYTE)
                .build()
        );

    public static void register(IEventBus bus) {
        COMPONENTS.register(bus);
    }
}

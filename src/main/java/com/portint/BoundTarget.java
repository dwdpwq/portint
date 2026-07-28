package com.portint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record BoundTarget(ResourceKey<Level> dimension, BlockPos pos, Direction side) {
    public static final Codec<BoundTarget> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(BoundTarget::dimension),
            BlockPos.CODEC.fieldOf("pos").forGetter(BoundTarget::pos),
            Direction.CODEC.optionalFieldOf("side", Direction.NORTH).forGetter(BoundTarget::side)
        ).apply(instance, BoundTarget::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, BoundTarget> STREAM_CODEC = StreamCodec.composite(
        ResourceKey.streamCodec(Registries.DIMENSION),
        BoundTarget::dimension,
        BlockPos.STREAM_CODEC,
        BoundTarget::pos,
        Direction.STREAM_CODEC,
        BoundTarget::side,
        BoundTarget::new
    );

    public GlobalPos toGlobalPos() {
        return GlobalPos.of(dimension, pos);
    }
}

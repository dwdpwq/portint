package com.portint.packet;

import com.portint.PortableInterface;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record SetGhostItemPayload(int column, int slotIndex, ItemStack stack) implements CustomPacketPayload {

    public static final Type<SetGhostItemPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(PortableInterface.MOD_ID, "set_ghost_item"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetGhostItemPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetGhostItemPayload::column,
            ByteBufCodecs.VAR_INT, SetGhostItemPayload::slotIndex,
            ItemStack.OPTIONAL_STREAM_CODEC, SetGhostItemPayload::stack,
            SetGhostItemPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

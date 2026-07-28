package com.portint.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import net.minecraft.network.chat.Component;
import com.portint.block.InterfaceBlockEntity;

public class PortableInterfaceStorageAdapter implements MEStorage {

    private final InterfaceBlockEntity blockEntity;

    public PortableInterfaceStorageAdapter(InterfaceBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        return 0;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        return 0;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        return false;
    }

    @Override
    public Component getDescription() {
        return Component.literal("Portable Interface");
    }
}

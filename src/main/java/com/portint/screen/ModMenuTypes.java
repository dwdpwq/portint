package com.portint.screen;

import com.portint.PortableInterface;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(BuiltInRegistries.MENU, PortableInterface.MOD_ID);

    public static final Supplier<MenuType<InterfaceMenu>> INTERFACE_MENU =
            MENU_TYPES.register("interface_menu",
                    () -> IMenuTypeExtension.create(
                            (windowId, inv, data) -> {
                                var level = inv.player.level();
                                var pos = data.readBlockPos();
                                var be = level.getBlockEntity(pos);
                                if (be instanceof com.portint.block.InterfaceBlockEntity ifce)
                                    return new InterfaceMenu(windowId, inv, ifce);
                                if (be instanceof com.portint.block.ExternalStorageBlockEntity esbe)
                                    return new InterfaceMenu(windowId, inv, esbe);
                                return null;
                            }));

    public static final Supplier<MenuType<ExternalStorageMenu>> EXTERNAL_STORAGE_MENU =
            MENU_TYPES.register("external_storage_menu",
                    () -> IMenuTypeExtension.create(
                            (windowId, inv, data) -> {
                                var level = inv.player.level();
                                var pos = data.readBlockPos();
                                var be = level.getBlockEntity(pos);
                                if (be instanceof com.portint.block.ExternalStorageBlockEntity tile)
                                    return new ExternalStorageMenu(windowId, inv, tile);
                                return null;
                            }));

    public static final Supplier<MenuType<FilterMenu>> FILTER_MENU =
            MENU_TYPES.register("filter_menu",
                    () -> IMenuTypeExtension.create(
                            (windowId, inv, data) -> {
                                BlockPos pos = data.readBlockPos();
                                int column = data.readByte() & 0xFF;
                                var level = inv.player.level();
                                var be = level.getBlockEntity(pos);
                                if (be instanceof com.portint.block.InterfaceBlockEntity ifce)
                                    return new FilterMenu(windowId, inv, ifce, column);
                                return null;
                            }));

    public static void register(IEventBus bus) {
        MENU_TYPES.register(bus);
    }
}

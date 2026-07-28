package com.portint.item;

import com.portint.PortableInterface;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS =
        DeferredRegister.createItems(PortableInterface.MOD_ID);

    public static final DeferredItem<Item> BINDING_CARD = ITEMS.register("binding_card",
        () -> new BindingCardItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> RANGE_CARD = ITEMS.register("range_card",
        () -> new RangeCardItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> DIMENSION_CARD = ITEMS.register("dimension_card",
        () -> new DimensionCardItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> LONG_CARD = ITEMS.register("long",
        () -> new LongCardItem(new Item.Properties().stacksTo(1)));

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}

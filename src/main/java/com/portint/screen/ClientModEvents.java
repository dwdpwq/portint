package com.portint.screen;

import com.portint.PortableInterface;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = PortableInterface.MOD_ID, value = Dist.CLIENT)
public class ClientModEvents {

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.INTERFACE_MENU.get(), InterfaceScreen::new);
        event.register(ModMenuTypes.FILTER_MENU.get(), FilterScreen::new);
    }
}

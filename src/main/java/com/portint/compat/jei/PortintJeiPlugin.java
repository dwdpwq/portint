package com.portint.compat.jei;

import com.portint.PortableInterface;
import com.portint.screen.FilterScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

@JeiPlugin
public class PortintJeiPlugin implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(PortableInterface.MOD_ID, "jei_plugin");
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(FilterScreen.class, new IGhostIngredientHandler<>() {
            @Override
            public <I> List<Target<I>> getTargetsTyped(FilterScreen screen, mezz.jei.api.ingredients.ITypedIngredient<I> typed, boolean doStart) {
                return screen.getTargetsTyped(screen, typed, doStart);
            }

            @Override
            public void onComplete() {}
        });
    }
}

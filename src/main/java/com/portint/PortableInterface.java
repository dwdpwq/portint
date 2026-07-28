package com.portint;

import appeng.api.AECapabilities;
import appeng.api.networking.GridServices;
import appeng.blockentity.AEBaseBlockEntity;
import com.portint.block.InterfaceBlockEntity;
import com.portint.service.IMyCustomGridService;
import com.portint.service.MyCustomGridService;
import com.portint.block.ModBlockEntities;
import com.portint.block.ModBlocks;
import com.portint.item.ModItems;
import com.portint.packet.SetGhostItemPayload;
import com.portint.screen.ModMenuTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.function.Supplier;

@Mod(PortableInterface.MOD_ID)
public class PortableInterface {
    public static final String MOD_ID = "portint";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final Supplier<CreativeModeTab> PORTINT_TAB = CREATIVE_TABS.register("tab",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.portint"))
            .icon(() -> new ItemStack(ModBlocks.INTERFACE_BLOCK.get()))
            .displayItems((params, output) -> {
                output.accept(ModBlocks.INTERFACE_BLOCK.get());
                output.accept(ModItems.BINDING_CARD.get());
                output.accept(ModItems.RANGE_CARD.get());
                output.accept(ModItems.DIMENSION_CARD.get());
                output.accept(ModItems.LONG_CARD.get());
            })
            .build()
    );

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public PortableInterface(IEventBus modEventBus, ModContainer container) {
        if (!ModList.get().isLoaded("ae2")) {
            throw new RuntimeException(
                "[PortableInterface] Applied Energistics 2 is required for this mod to function!\n" +
                "Please install AE2 (appliedenergistics2-neoforge) for Minecraft 1.21.1."
            );
        }

        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModDataComponents.register(modEventBus);
        ModMenuTypes.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);

        // Register network payload handlers
        modEventBus.addListener(this::registerPayloads);

        // Register AE2 grid node host capability
        modEventBus.addListener(this::registerCapabilities);

        // Register block entity → item mapping so AE2's Network Tool can display the visual icon.
        // Without this, AENetworkedBlockEntity constructor calls setVisualRepresentation(null)
        // and NetworkStatus.getKey() skips the node (returns null → device not shown).
        // FMLCommonSetupEvent fires after ALL registrations are complete — both DeferredHolders are bound.
        modEventBus.addListener((FMLCommonSetupEvent event) -> {
            AEBaseBlockEntity.registerBlockEntityItem(
                ModBlockEntities.INTERFACE_BLOCK_ENTITY.get(),
                ModBlocks.INTERFACE_BLOCK_ITEM.get()
            );
        });

        // Register custom grid service
        GridServices.register(IMyCustomGridService.class, MyCustomGridService.class);
        LOGGER.info("Registered custom grid service: IMyCustomGridService");

        LOGGER.info("Portable Interface initialized successfully.");
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
            .playToServer(
                SetGhostItemPayload.TYPE,
                SetGhostItemPayload.STREAM_CODEC,
                (payload, context) -> {
                    ServerPlayer player = (ServerPlayer) context.player();
                    if (!(player.containerMenu instanceof com.portint.screen.FilterMenu filterMenu)) return;
                    filterMenu.onSetGhostItem(payload);
                }
            );
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            AECapabilities.IN_WORLD_GRID_NODE_HOST,
            ModBlockEntities.INTERFACE_BLOCK_ENTITY.get(),
            (be, ctx) -> be
        );
    }
}

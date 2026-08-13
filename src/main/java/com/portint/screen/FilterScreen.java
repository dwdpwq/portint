package com.portint.screen;

import appeng.client.gui.Icon;

import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FilterScreen extends AbstractContainerScreen<FilterMenu> implements IGhostIngredientHandler<FilterScreen> {

    private static final ResourceLocation CLOSE_ICON =
            ResourceLocation.parse("portint:textures/gui/close_icon.png");

    private static final ResourceLocation FILTER_BG =
            ResourceLocation.parse("portint:textures/gui/filter_bg.png");

    private static final ResourceLocation WL_ICON =
            ResourceLocation.parse("portint:textures/gui/whitelist_icon.png");

    private static final ResourceLocation BL_ICON =
            ResourceLocation.parse("portint:textures/gui/blacklist_icon.png");

    // AE2 color palette
    private static final int TEXT_COLOR       = 0xFFC6C6C6;
    private static final int MUTED_TEXT_COLOR = 0xFF7A7FA0;

    // Layout
    private static final int GHOST_ROWS = 3;
    private static final int GHOST_COLS = 9;
    private static final int GHOST_Y    = 23; // shifted down 6px from original 17
    private static final int RIGHT_OFFSET = 20; // shift entire GUI right

    // ── Chemical ghost ingredient support (Mekanism) ──
    private static Class<?> chemStackClass;
    private static Class<?> actionClass;
    private static Object actionExec;
    private static Item basicTankItem;
    private static Object chemItemCap;
    private static java.lang.reflect.Method insertChemicalMethod;
    private static java.lang.reflect.Method emptyMethod;

    // Hover state
    private boolean hoveredCloseBtn;
    private boolean hoveredWlBtn;
    private boolean forcedWlHovered;
    private int     closeX, closeY;
    private int     wlX, wlY;

    public FilterScreen(FilterMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth  = 176;
        this.imageHeight = 207;
        this.titleLabelX = 28; // clear of close button (x+8, 16px)
        this.titleLabelY = 6;
    }

    // ── Whether the parent column is in input mode (forced whitelist) ──
    private boolean isForcedWhitelist() {
        return menu.tile.getOutputModes()[menu.getColumn()];
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos += RIGHT_OFFSET;
    }

    // ══════════════ labels ══════════════

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // Title X-centered in full panel width (close button overlaps area, title fits beside it)
        int titleWidth = this.font.width(this.title);
        int titleX = (imageWidth - titleWidth) / 2;
        g.drawString(this.font, this.title, titleX, this.titleLabelY, TEXT_COLOR);
        g.drawString(this.font, this.playerInventoryTitle,
                8, 123 - this.font.lineHeight - 3, MUTED_TEXT_COLOR);
    }

    // ══════════════ background ══════════════

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        // Background texture (256×256 → 176×207, auto-scaled)
        g.blit(FILTER_BG, x, y, 0, 0, imageWidth, imageHeight, 256, 256);

        // Ghost slot backgrounds (no per-slot border)
        for (int row = 0; row < GHOST_ROWS; row++) {
            for (int col = 0; col < GHOST_COLS; col++) {
                int sx = x + 8 + col * 18;
                int sy = y + GHOST_Y + row * 18;
                Icon.SLOT_BACKGROUND.getBlitter().dest(sx, sy, 16, 16).blit(g);
            }
        }

        // Single 1px white outer border around the ghost slot grid
        int gx1 = x + 7, gy1 = y + GHOST_Y - 1;
        int gx2 = x + 169, gy2 = y + GHOST_Y + 53;
        g.fill(gx1, gy1, gx2, gy1 + 1, 0xFFFFFFFF);
        g.fill(gx1, gy2 - 1, gx2, gy2, 0xFFFFFFFF);
        g.fill(gx1, gy1, gx1 + 1, gy2, 0xFFFFFFFF);
        g.fill(gx2 - 1, gy1, gx2, gy2, 0xFFFFFFFF);

        // ── Close button (left, x aligned with first ghost slot column) ──
        closeX = x + 8;
        closeY = y + 5;
        boolean closeHover = mouseX >= closeX && mouseX < closeX + 16
                && mouseY >= closeY && mouseY < closeY + 16;
        hoveredCloseBtn = closeHover;

        Icon closeBg = closeHover ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER : Icon.TOOLBAR_BUTTON_BACKGROUND;
        closeBg.getBlitter().dest(closeX, closeY, 16, 16).blit(g);
        g.blit(CLOSE_ICON, closeX, closeY, 0, 0, 16, 16, 16, 16);

        // ── Whitelist/Blacklist toggle (right, x aligned with last ghost slot column) ──
        wlX = x + 152;
        wlY = y + 5;
        boolean forced = isForcedWhitelist();
        boolean wlHover = !forced && mouseX >= wlX && mouseX < wlX + 16
                && mouseY >= wlY && mouseY < wlY + 16;
        hoveredWlBtn = wlHover;
        forcedWlHovered = forced && mouseX >= wlX && mouseX < wlX + 16
                && mouseY >= wlY && mouseY < wlY + 16;

        Icon wlBg = wlHover ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER : Icon.TOOLBAR_BUTTON_BACKGROUND;
        wlBg.getBlitter().dest(wlX, wlY, 16, 16).blit(g);

        ResourceLocation wlIcon = (forced || menu.isWhitelist()) ? WL_ICON : BL_ICON;
        g.blit(wlIcon, wlX, wlY, 0, 0, 16, 16, 16, 16);

        // Dim overlay when forced (input mode locks whitelist)
        if (forced) {
            g.fill(wlX, wlY, wlX + 16, wlY + 16, 0x55FFFFFF);
        }

        // ── Hover highlight for ghost marker slots ──
        for (int row = 0; row < GHOST_ROWS; row++) {
            for (int col = 0; col < GHOST_COLS; col++) {
                int sx = x + 8 + col * 18;
                int sy = y + GHOST_Y + row * 18;
                if (mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16) {
                    int slotIdx = row * 9 + col;
                    Slot slot = menu.slots.get(slotIdx);
                    if (slot.hasItem()) {
                        g.fill(sx, sy, sx + 16, sy + 16, 0x33DAFFFF);
                    }
                }
            }
        }
    }

    // ══════════════ render ══════════════

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        // ── Chemical ghost slot overlay: render Mekanism chemical icons ──
        for (int row = 0; row < GHOST_ROWS; row++) {
            for (int col = 0; col < GHOST_COLS; col++) {
                int slotIdx = row * 9 + col;
                Slot slot = menu.slots.get(slotIdx);
                if (!slot.hasItem()) continue;
                ItemStack stack = slot.getItem();
                var custom = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                if (custom != null) {
                    var tag = custom.copyTag();
                    if (tag.contains("portint_chem_id")) {
                        // Convert legacy GLASS_PANE to LIGHT on the fly
                        if (stack.getItem() != net.minecraft.world.item.Items.LIGHT) {
                            ItemStack fixed = new ItemStack(net.minecraft.world.item.Items.LIGHT);
                            fixed.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, custom);
                            slot.setByPlayer(fixed);
                        }
                        int sx = leftPos + slot.x;
                        int sy = topPos + slot.y;
                        var pose = g.pose();
                        pose.pushPose();
                        pose.translate(0, 0, 300);
                        renderChemicalIcon(g, tag, sx, sy);
                        pose.popPose();
                    } else if (tag.contains("portint_fluid_id")) {
                        // Convert legacy placeholder to LIGHT on the fly
                        if (stack.getItem() != net.minecraft.world.item.Items.LIGHT) {
                            ItemStack fixed = new ItemStack(net.minecraft.world.item.Items.LIGHT);
                            fixed.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, custom);
                            slot.setByPlayer(fixed);
                        }
                        int sx = leftPos + slot.x;
                        int sy = topPos + slot.y;
                        var pose = g.pose();
                        pose.pushPose();
                        pose.translate(0, 0, 300);
                        renderFluidIcon(g, tag, sx, sy);
                        pose.popPose();
                    }
                }
            }
        }

        // ── Hover analysis for ghost marker slots via hoveredSlot ──
        if (this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            int idx = this.hoveredSlot.index;
            if (idx >= 0 && idx < FilterMenu.PLAYER_START) {
                Slot hs = this.hoveredSlot;
                ItemStack stack = hs.getItem();
                g.fill(leftPos + hs.x, topPos + hs.y,
                        leftPos + hs.x + 16, topPos + hs.y + 16, 0x33DAFFFF);

                List<Component> analysis = buildGhostTooltip(stack);
                if (!analysis.isEmpty()) {
                    g.renderTooltip(this.font, analysis, Optional.empty(),
                            mouseX, mouseY + 12);
                }
            }
        }

        // ── AE2-style hover highlight for player inventory slots ──
        if (this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            int idx = this.hoveredSlot.index;
            if (idx >= FilterMenu.PLAYER_START) {
                Slot hs = this.hoveredSlot;
                g.fill(leftPos + hs.x, topPos + hs.y,
                        leftPos + hs.x + 16, topPos + hs.y + 16, 0x33DAFFFF);
            }
        }
        if (hoveredCloseBtn) {
            g.renderTooltip(font, Component.translatable("gui.portint.close_esc"), mouseX, mouseY);
        }
        if (hoveredWlBtn) {
            String mode = menu.isWhitelist() ? Component.translatable("gui.portint.whitelist").getString() : Component.translatable("gui.portint.blacklist").getString();
            g.renderTooltip(font, Component.translatable("gui.portint.mode_toggle", mode), mouseX, mouseY);
        }
        // Show tooltip even when forced (no hover state, but mouse is over the button)
        if (!hoveredWlBtn && forcedWlHovered) {
            g.renderTooltip(font, Component.translatable("gui.portint.output_forced_whitelist"),
                    mouseX, mouseY);
        }
    }

    @Override
    protected void renderSlot(GuiGraphics g, Slot slot) {
        if (slot.hasItem()) {
            var custom = slot.getItem().get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            if (custom != null) {
                var tag = custom.copyTag();
                if (tag.contains("portint_chem_id") || tag.contains("portint_fluid_id")) {
                    // Skip rendering for chemical/fluid ghost slots — icons drawn in render()
                    return;
                }
            }
        }
        super.renderSlot(g, slot);
    }

    /**
     * Render a Mekanism chemical's real icon (with dynamic effects) via
     * reflection into MekanismRenderer / GuiUtils.
     * Falls back to a pure-color rectangle if reflection fails.
     */
    private void renderChemicalIcon(GuiGraphics g, net.minecraft.nbt.CompoundTag tag, int x, int y) {
        String chemId = tag.getString("portint_chem_id");
        if (chemId.isEmpty()) return;

        int tint = tag.getInt("portint_chem_tint");
        int fallbackColor = 0xFF000000 | (tint & 0x00FFFFFF);

        try {
            // ── 1. Get Chemical from registry ──
            Class<?> apiClass = Class.forName("mekanism.api.MekanismAPI");
            java.lang.reflect.Field regField = apiClass.getField("CHEMICAL_REGISTRY");
            Object registry = regField.get(null);
            ResourceLocation rl = ResourceLocation.parse(chemId);

            java.lang.reflect.Method getValueMethod;
            try {
                getValueMethod = registry.getClass().getMethod("get", ResourceLocation.class);
            } catch (NoSuchMethodException e) {
                getValueMethod = registry.getClass().getMethod("getValue", ResourceLocation.class);
            }
            Object chemical = getValueMethod.invoke(registry, rl);
            if (chemical == null) {
                g.fill(x, y, x + 16, y + 16, fallbackColor);
                return;
            }

            // ── 2. Create ChemicalStack ──
            Class<?> chemStackClass = Class.forName("mekanism.api.chemical.ChemicalStack");
            Object chemStack = chemStackClass.getConstructor(
                Class.forName("mekanism.api.chemical.Chemical"), long.class
            ).newInstance(chemical, 1L);

            // ── 3. Set render color ──
            Class<?> rendererClass = Class.forName("mekanism.client.render.MekanismRenderer");
            try {
                rendererClass.getMethod("color", GuiGraphics.class, chemStackClass)
                    .invoke(null, g, chemStack);
            } catch (NoSuchMethodException nsme) {
                int argb = (int) rendererClass.getMethod("getColorARGB", chemStackClass, float.class)
                    .invoke(null, chemStack, 1.0f);
                rendererClass.getMethod("color", GuiGraphics.class, int.class)
                    .invoke(null, g, argb);
            }

            // ── 4. Get texture sprite ──
            Object sprite;
            try {
                sprite = rendererClass.getMethod("getChemicalTexture", chemStackClass)
                    .invoke(null, chemStack);
            } catch (NoSuchMethodException e) {
                var holder = chemStack.getClass().getMethod("getChemicalHolder").invoke(chemStack);
                sprite = rendererClass.getMethod("getChemicalTexture", net.minecraft.core.Holder.class)
                    .invoke(null, holder);
            }

            // ── 5. Draw icon ──
            if (sprite != null) {
                Class<?> guiUtilsClass = Class.forName("mekanism.client.gui.GuiUtils");
                Class<?> tilingDirClass = Class.forName("mekanism.client.gui.GuiUtils$TilingDirection");
                Object upRight = tilingDirClass.getField("UP_RIGHT").get(null);

                guiUtilsClass.getMethod("drawTiledSprite",
                    GuiGraphics.class, int.class, int.class, int.class, int.class, int.class,
                    net.minecraft.client.renderer.texture.TextureAtlasSprite.class,
                    int.class, int.class, int.class, tilingDirClass)
                    .invoke(null, g, x, y, 16, 16, 16, sprite, 16, 16, 100, upRight);
            } else {
                rendererClass.getMethod("renderColorOverlay", GuiGraphics.class, int.class, int.class, int.class)
                    .invoke(null, g, x, y, 16);
            }

            // ── 6. Reset color ──
            rendererClass.getMethod("resetColor", GuiGraphics.class).invoke(null, g);
        } catch (Exception e) {
            com.portint.PortableInterface.LOGGER.warn(
                "Chemical icon render failed for {}: {}", chemId, e.toString());
            g.fill(x, y, x + 16, y + 16, fallbackColor);
        }
    }

    /**
     * Render a fluid's still-texture icon (tinted) for fluid ghost slots.
     * Falls back to a colored rectangle if the sprite is unavailable.
     */
    private void renderFluidIcon(GuiGraphics g, net.minecraft.nbt.CompoundTag tag, int x, int y) {
        String fluidId = tag.getString("portint_fluid_id");
        if (fluidId.isEmpty()) return;

        try {
            var rl = ResourceLocation.parse(fluidId);
            var fluid = BuiltInRegistries.FLUID.get(rl);
            if (fluid == null) return;

            var fluidType = fluid.getFluidType();
            int color = IClientFluidTypeExtensions.of(fluidType).getTintColor();
            int alphaColor = 0xFF000000 | (color & 0x00FFFFFF);

            // Get still texture sprite
            var stillTexture = IClientFluidTypeExtensions.of(fluidType).getStillTexture();
            if (stillTexture != null) {
                var sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(stillTexture);
                float r = ((color >> 16) & 0xFF) / 255f;
                float gr = ((color >> 8) & 0xFF) / 255f;
                float b = (color & 0xFF) / 255f;
                float a = 1.0f;
                com.mojang.blaze3d.systems.RenderSystem.setShaderColor(r, gr, b, a);
                g.blit(x, y, 0, 16, 16, sprite);
                com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1, 1, 1, 1);
            } else {
                g.fill(x, y, x + 16, y + 16, alphaColor);
            }
        } catch (Exception e) {
            com.portint.PortableInterface.LOGGER.warn(
                "Fluid icon render failed for {}: {}", fluidId, e.toString());
            g.fill(x, y, x + 16, y + 16, 0xFF4444AA);
        }
    }

    private List<Component> buildGhostTooltip(ItemStack stack) {
        List<Component> lines = new ArrayList<>();

        // Check for chemical NBT first (fast path: read metadata from tagged tank ItemStack)
        var custom = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        boolean isChem = false;
        boolean isFluid = false;
        if (custom != null) {
            var tag = custom.copyTag();
            if (tag.contains("portint_chem_id")) {
                isChem = true;
                lines.add(Component.translatable("gui.portint.ghost_marker_item")
                        .withStyle(ChatFormatting.GOLD));
                String chemName = tag.getString("portint_chem_name");
                if (!chemName.isEmpty()) {
                    lines.add(Component.translatable("gui.portint.ghost_chemical", chemName)
                            .withStyle(ChatFormatting.AQUA));
                }
            } else if (tag.contains("portint_fluid_id")) {
                isFluid = true;
                lines.add(Component.translatable("gui.portint.ghost_fluid")
                        .withStyle(ChatFormatting.GOLD));
                String fluidName = tag.getString("portint_fluid_name");
                if (!fluidName.isEmpty()) {
                    lines.add(Component.literal(fluidName)
                            .withStyle(ChatFormatting.AQUA));
                }
            }
        }

        if (!isChem && !isFluid) {
            lines.add(Component.translatable("gui.portint.ghost_marker_item")
                    .withStyle(ChatFormatting.GOLD));
            // Fallback: check for chemical tank item via item registry (backwards compat)
            if (custom != null) {
                // already handled above
            } else {
                ResourceLocation rl = stack.getItem().builtInRegistryHolder().key().location();
                boolean isChemTank = rl.getNamespace().equals("mekanism") && rl.getPath().contains("chemical_tank");
                if (isChemTank) {
                    String chemName = readChemicalName(stack);
                    if (chemName != null) {
                        lines.add(Component.translatable("gui.portint.ghost_chemical", chemName)
                                .withStyle(ChatFormatting.AQUA));
                    }
                }
            }
        }

        // For chemical/fluid markers, show the registry ID instead of the placeholder item name
        if (custom != null) {
            var tag = custom.copyTag();
            if (tag.contains("portint_chem_id")) {
                String chemId = tag.getString("portint_chem_id");
                if (!chemId.isEmpty()) {
                    lines.add(Component.literal(chemId)
                            .withStyle(ChatFormatting.DARK_GRAY));
                }
                return lines;
            }
            if (tag.contains("portint_fluid_id")) {
                String fluidId = tag.getString("portint_fluid_id");
                if (!fluidId.isEmpty()) {
                    lines.add(Component.literal(fluidId)
                            .withStyle(ChatFormatting.DARK_GRAY));
                }
                return lines;
            }
        }
        lines.add(stack.getHoverName().copy()
                .withStyle(ChatFormatting.WHITE));
        return lines;
    }

    /** Try to read chemical name from a tank ItemStack via Mekanism capability. */
    private String readChemicalName(ItemStack tankStack) {
        try {
            ensureChemApi();
            var getCapMethod = ItemStack.class.getMethod("getCapability",
                net.neoforged.neoforge.capabilities.ItemCapability.class);
            var handler = getCapMethod.invoke(tankStack, chemItemCap);
            if (handler == null) return null;

            int tanks = (int) handler.getClass().getMethod("getChemicalTanks").invoke(handler);
            if (tanks == 0) return null;

            var chemStack = handler.getClass().getMethod("getChemicalInTank", int.class).invoke(handler, 0);
            if (chemStack == null || (boolean) emptyMethod.invoke(chemStack)) return null;

            // Get Chemical → getTextComponent()
            var chem = chemStack.getClass().getMethod("getChemical").invoke(chemStack);
            if (chem == null) return null;
            var comp = chem.getClass().getMethod("getTextComponent").invoke(chem);
            return comp != null ? ((net.minecraft.network.chat.Component) comp).getString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ══════════════ mouse ══════════════

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);

        // Close button
        if (hoveredCloseBtn) {
            this.onClose();
            return true;
        }

        // Whitelist/Blacklist toggle
        if (hoveredWlBtn) {
            if (this.minecraft != null && this.minecraft.gameMode != null) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 0);
            }
            return true;
        }

        return super.mouseClicked(mx, my, button);
    }

    // ══════════════ keyboard ══════════════

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ══════════════ close ══════════════

    @Override
    public void onClose() {
        // Send reopen request to server before closing locally
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 255);
        }
        // Close locally without sending close packet (server handles via button click)
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    // ── JEI ghost ingredient handler ────────────────────────────────

    @Override
    public <I> List<Target<I>> getTargetsTyped(FilterScreen screen, ITypedIngredient<I> typed, boolean exclusive) {
        List<Target<I>> targets = new ArrayList<>();
        var ing = typed.getIngredient();

        boolean isItem = ing instanceof ItemStack;
        boolean isChem = isChemicalStack(ing);

        if (!isItem && !isChem) return targets;

        // Lazy-init Mekanism API once
        if (isChem) ensureChemApi();

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int sx = leftPos + 8 + col * 18;
                int sy = topPos + GHOST_Y + row * 18;
                Rect2i area = new Rect2i(sx, sy, 16, 16);
                final int slotIdx = row * 9 + col;
                targets.add(new Target<I>() {
                    @Override public Rect2i getArea() { return area; }
                    @Override public void accept(I ingredient) {
                        if (slotIdx >= menu.slots.size()) return;
                        if (ingredient instanceof ItemStack s) {
                            var copy = s.copyWithCount(1);
                            menu.slots.get(slotIdx).setByPlayer(copy);
                            ((FilterMenu) menu).syncGhostSlot(slotIdx, copy);
                        } else if (isChem && ingredient != null) {
                            acceptChemical(slotIdx, ingredient);
                            ((FilterMenu) menu).syncGhostSlot(slotIdx, menu.slots.get(slotIdx).getItem());
                        }
                    }
                });
            }
        }
        return targets;
    }

    @Override
    public void onComplete() {}

    // ── ChemicalStack support ──────────────────────────────────────

    private boolean isChemicalStack(Object obj) {
        try {
            if (chemStackClass == null)
                chemStackClass = Class.forName("mekanism.api.chemical.ChemicalStack");
            return chemStackClass.isInstance(obj);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private void ensureChemApi() {
        try {
            if (chemStackClass == null)
                chemStackClass = Class.forName("mekanism.api.chemical.ChemicalStack");
            if (actionClass == null) {
                actionClass = Class.forName("mekanism.api.Action");
                actionExec = actionClass.getField("EXECUTE").get(null);
            }
            if (basicTankItem == null)
                basicTankItem = net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("mekanism", "basic_chemical_tank"));
            if (chemItemCap == null) {
                Class<?> chemCapClass = Class.forName("mekanism.common.capabilities.Capabilities");
                var chemCapField = chemCapClass.getField("CHEMICAL");
                var chemMultiCap = chemCapField.get(null);
                var itemMethod = chemMultiCap.getClass().getMethod("item");
                chemItemCap = itemMethod.invoke(chemMultiCap);
            }
            if (insertChemicalMethod == null) {
                Class<?> chemHandlerClass = Class.forName("mekanism.api.chemical.IChemicalHandler");
                insertChemicalMethod = chemHandlerClass.getMethod("insertChemical",
                    int.class, chemStackClass, actionClass);
            }
            if (emptyMethod == null)
                emptyMethod = chemStackClass.getMethod("isEmpty");
        } catch (Exception e) {
            com.portint.PortableInterface.LOGGER.error("Failed to init Mekanism API for ghost slot", e);
        }
    }

    private void acceptChemical(int slotIdx, Object chemStack) {
        try {
            ensureChemApi();

            // Extract chemical metadata via reflection
            var getChemicalMethod = chemStackClass.getMethod("getChemical");
            var chem = getChemicalMethod.invoke(chemStack);
            if (chem == null) return;

            // Tint color (0 = transparent/unknown → fallback to opaque white)
            int tint = 0xFFFFFFFF;
            try {
                tint = (int) chem.getClass().getMethod("getTint").invoke(chem);
            } catch (Exception ignored) {}

            // Readable name
            String chemName = Component.translatable("gui.portint.chemical").getString();
            try {
                var comp = chem.getClass().getMethod("getTextComponent").invoke(chem);
                if (comp != null) chemName = ((net.minecraft.network.chat.Component) comp).getString();
            } catch (Exception ignored) {}

            // Registry ID from Holder → reverse lookup via registry as fallback
            String chemId = "";
            try {
                var holder = chemStackClass.getMethod("getChemicalHolder").invoke(chemStack);
                var optKey = holder.getClass().getMethod("unwrapKey").invoke(holder);
                if (optKey instanceof java.util.Optional<?> opt && opt.isPresent()) {
                    chemId = ((net.minecraft.resources.ResourceKey<?>) opt.get()).location().toString();
                }
            } catch (Exception ignored) {}

            // Fallback: reverse lookup via CHEMICAL_REGISTRY.getKey(chemical)
            if (chemId.isEmpty()) {
                try {
                    Object registry = Class.forName("mekanism.api.MekanismAPI")
                        .getField("CHEMICAL_REGISTRY").get(null);
                    java.lang.reflect.Method getKeyMethod = null;
                    try {
                        getKeyMethod = registry.getClass().getMethod("getKey", Object.class);
                    } catch (NoSuchMethodException nsme) {
                        // Try getResourceKey
                        getKeyMethod = registry.getClass().getMethod("getResourceKey",
                            Class.forName("mekanism.api.chemical.Chemical"));
                    }
                    Object key = getKeyMethod.invoke(registry, chem);
                    if (key instanceof java.util.Optional<?> opt && opt.isPresent()) {
                        var rk = opt.get();
                        if (rk instanceof net.minecraft.resources.ResourceKey<?> resKey) {
                            chemId = resKey.location().toString();
                        } else if (rk instanceof net.minecraft.resources.ResourceLocation rl) {
                            chemId = rl.toString();
                        }
                    }
                } catch (Exception ignored) {}
            }

            // Use an invisible placeholder (Items.LIGHT — fully transparent icon)
            ItemStack ghostItem = new ItemStack(net.minecraft.world.item.Items.LIGHT);

            final int finalTint = tint;
            final String finalName = chemName;
            final String finalId = chemId;
            ghostItem.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY.update(tag -> {
                    tag.putInt("portint_chem_tint", finalTint);
                    tag.putString("portint_chem_name", finalName);
                    if (!finalId.isEmpty()) tag.putString("portint_chem_id", finalId);
                }));

            menu.slots.get(slotIdx).setByPlayer(ghostItem);
        } catch (Exception e) {
            com.portint.PortableInterface.LOGGER.error("Failed to accept chemical ghost ingredient", e);
        }
    }
}

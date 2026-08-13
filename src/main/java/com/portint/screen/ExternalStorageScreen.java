package com.portint.screen;

import com.portint.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ExternalStorageScreen extends AbstractContainerScreen<ExternalStorageMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.parse("portint:textures/gui/external_storage.png");

    private static final int TEXT_COLOR = 0xFFC6C6C6;
    private static final int MUTED_TEXT_COLOR = 0xFF7A7FA0;

    private boolean hoveredUpgrade0;
    private boolean hoveredUpgrade1;

    public ExternalStorageScreen(ExternalStorageMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = ExternalStorageMenu.IMAGE_WIDTH;
        this.imageHeight = ExternalStorageMenu.IMAGE_HEIGHT;
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, TEXT_COLOR);
        g.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX,
                this.imageHeight - 94, MUTED_TEXT_COLOR);

        // Binding slots label
        g.drawString(this.font, Component.translatable("gui.portint.binding_card")
                        .withStyle(ChatFormatting.GRAY),
                8, 24, MUTED_TEXT_COLOR);

        // Upgrade slots label
        g.drawString(this.font, Component.translatable("gui.portint.upgrade_card")
                        .withStyle(ChatFormatting.GRAY),
                134, 6, MUTED_TEXT_COLOR);
    }

    // ══════════════ AE2 hover highlight (蓝白边框 + 半透明蓝填充) ══════════════

    @Override
    protected void renderSlotHighlight(GuiGraphics g, Slot slot, int mouseX, int mouseY,
                                       float partialTick) {
        if (!slot.isHighlightable()) return;
        int x = slot.x;
        int y = slot.y;
        int borderColor = 0xFFDBEDFF;
        int fillColor   = 0x6699BFFF;
        g.hLine(x, x + 16, y - 1, borderColor);
        g.hLine(x - 1, x + 16, y + 16, borderColor);
        g.vLine(x - 1, y - 2, y + 16, borderColor);
        g.vLine(x + 16, y - 2, y + 16, borderColor);
        g.fillGradient(RenderType.guiOverlay(), x, y, x + 16, y + 16, fillColor, fillColor, 0);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        g.blit(TEXTURE, x, y, 0.0f, 0.0f, imageWidth, imageHeight, 256, 256);

        // Binding card slot hover highlight
        for (int col = 0; col < 9; col++) {
            int sx = x + 8 + col * 18;
            int sy = y + 35;
            if (mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16) {
                ItemStack stack = menu.tile.getBindingInventory().getItem(col);
                if (!stack.isEmpty()
                        && stack.has(ModDataComponents.BOUND_TARGET.get())) {
                    g.fill(sx, sy, sx + 16, sy + 16, 0x33DAFFFF);
                }
            }
        }

        hoveredUpgrade0 = mouseX >= x + 134 && mouseX < x + 134 + 16
                && mouseY >= y + 18 && mouseY < y + 18 + 16;
        hoveredUpgrade1 = mouseX >= x + 134 && mouseX < x + 134 + 16
                && mouseY >= y + 40 && mouseY < y + 40 + 16;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        // Binding card tooltip
        if (this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            int idx = this.hoveredSlot.index;
            if (idx >= ExternalStorageMenu.BINDING_START
                    && idx < ExternalStorageMenu.PLAYER_START) {
                Slot hs = this.hoveredSlot;
                ItemStack stack = hs.getItem();
                if (stack.has(ModDataComponents.BOUND_TARGET.get())) {
                    g.fill(leftPos + hs.x, topPos + hs.y,
                            leftPos + hs.x + 16, topPos + hs.y + 16, 0x33DAFFFF);

                    List<Component> analysis = buildAnalysisTooltip(stack);
                    if (!analysis.isEmpty()) {
                        g.renderTooltip(this.font, analysis, Optional.empty(),
                                mouseX, mouseY + 12);
                    }
                }
            } else if (idx >= ExternalStorageMenu.UPGRADE_START
                    && idx < ExternalStorageMenu.BINDING_START) {
                // Standard tooltip for upgrade slots
                g.renderTooltip(this.font, this.hoveredSlot.getItem(), mouseX, mouseY);
            } else {
                g.renderTooltip(this.font, this.hoveredSlot.getItem(), mouseX, mouseY);
            }
        }

        // Upgrade slot hints
        if (hoveredUpgrade0 || hoveredUpgrade1) {
            var upgradeInv = menu.tile.getUpgradeInventory();
            boolean slot0Empty = upgradeInv.getItem(0).isEmpty();
            boolean slot1Empty = upgradeInv.getItem(1).isEmpty();
            boolean hoveredSlotEmpty = (hoveredUpgrade0 && slot0Empty)
                                    || (hoveredUpgrade1 && slot1Empty);
            if (hoveredSlotEmpty) {
                List<Component> lines = new ArrayList<>();
                lines.add(Component.translatable("gui.portint.external_storage_upgrade_hint")
                        .withStyle(ChatFormatting.WHITE));
                lines.add(Component.translatable("gui.portint.external_storage_capacity_hint")
                        .withStyle(ChatFormatting.GRAY));
                g.renderTooltip(font, lines, Optional.empty(), mouseX, mouseY);
            }
        }
    }

    private List<Component> buildAnalysisTooltip(ItemStack stack) {
        List<Component> lines = new ArrayList<>();
        var opt = stack.get(ModDataComponents.BOUND_TARGET.get());
        if (opt != null && opt.isPresent()) {
            var bt = opt.get();
            lines.add(Component.translatable("gui.portint.bound_target")
                    .withStyle(ChatFormatting.GOLD));

            var nameOpt = stack.get(ModDataComponents.BOUND_BLOCK_NAME.get());
            if (nameOpt != null && nameOpt.isPresent()) {
                lines.add(Component.translatable("gui.portint.bound_block", nameOpt.get())
                        .withStyle(ChatFormatting.WHITE));
            }

            lines.add(Component.translatable("gui.portint.bound_coords",
                    bt.pos().getX(), bt.pos().getY(), bt.pos().getZ())
                    .withStyle(ChatFormatting.YELLOW));
            lines.add(Component.literal(
                    "  " + bt.dimension().location())
                    .withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }
}

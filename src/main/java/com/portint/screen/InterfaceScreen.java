package com.portint.screen;

import appeng.client.gui.Icon;
import com.portint.block.InterfaceBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class InterfaceScreen extends AbstractContainerScreen<InterfaceMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.parse("portint:textures/gui/interface.png");

    private static final ResourceLocation GEAR_NORMAL =
            ResourceLocation.parse("portint:textures/gui/gear_normal.png");
    private static final ResourceLocation GEAR_HOVER  =
            ResourceLocation.parse("portint:textures/gui/gear_hover.png");
    private static final ResourceLocation PRIORITY_ICON =
            ResourceLocation.parse("portint:textures/gui/priority_button.png");

    private static final int TEXT_COLOR       = 0xFF413F54;

    private static final int COL_START_X  = 8;
    private static final int COL_SPACING  = 18;
    private static final int GEAR_Y          = 35;
    private static final int GEAR_SIZE       = 16;
    private static final int DIR_BTN_Y       = 74;
    private static final int DIR_BTN_SIZE    = 10;
    private static final int PRIORITY_TEXT_Y = 70;

    private static final int PRIORITY_POS_COLOR = 0xFFFFD700;
    private static final int PRIORITY_NEG_COLOR = 0xFFFF4444;
    private static final int PRIORITY_ZERO_COLOR = 0xFFFFFFFF;

    private int hoveredGearCol = -1;
    private int hoveredDirCol  = -1;
    private int hoveredPriorityCol = -1;
    private boolean hoveredUpgrade0;
    private boolean hoveredUpgrade1;

    public InterfaceScreen(InterfaceMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth  = InterfaceMenu.IMAGE_WIDTH;
        this.imageHeight = InterfaceMenu.IMAGE_HEIGHT;
    }

    // ══════════════ labels ══════════════

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, TEXT_COLOR, false);
        g.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX,
                123 - this.font.lineHeight - 3, TEXT_COLOR, false);
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

    // ══════════════ background ══════════════

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        g.blit(TEXTURE, x, y, 0.0f, 0.0f, imageWidth, imageHeight, 256, 256);

        // Upgrade slot backgrounds (transparent by design)
        drawUpgradeSlotBg(g, x + 175, y + 6);
        drawUpgradeSlotBg(g, x + 175, y + 22);

        // Render column gear & direction buttons
        if (menu.tile.hasColumnConfig()) {
            renderColumns(g, x, y, mouseX, mouseY);
        } else {
            renderPriorityButtons(g, x, y, mouseX, mouseY);
        }

        // ── Hover analysis highlight for binding card slots ──
        for (int col = 0; col < 9; col++) {
            int sx = x + 8 + col * 18;
            int sy = y + 53;
            if (mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16) {
                ItemStack stack = menu.tile.getBindingInventory().getItem(col);
                if (!stack.isEmpty()
                        && stack.has(com.portint.ModDataComponents.BOUND_TARGET.get())) {
                    g.fill(sx, sy, sx + 16, sy + 16, 0x33DAFFFF);
                }
            }
        }

        // ── Upgrade slot hover detection ──
        hoveredUpgrade0 = mouseX >= x + 175 && mouseX < x + 191
                && mouseY >= y + 6 && mouseY < y + 22;
        hoveredUpgrade1 = mouseX >= x + 175 && mouseX < x + 191
                && mouseY >= y + 22 && mouseY < y + 38;
    }

    private void drawUpgradeSlotBg(GuiGraphics g, int sx, int sy) {
        // Transparent: upgrade slots show only the card item, no grey slot background
    }

    // ══════════════ columns ══════════════

    private void renderColumns(GuiGraphics g, int baseX, int baseY, int mx, int my) {
        hoveredGearCol = -1;
        hoveredDirCol  = -1;

        for (int col = 0; col < 9; col++) {
            int cx = baseX + COL_START_X + col * COL_SPACING;

            // ── Gear button ──
            {
                int gx = cx;
                int gy = baseY + GEAR_Y;
                boolean hover = mx >= gx && mx < gx + GEAR_SIZE
                        && my >= gy && my < gy + GEAR_SIZE;
                if (hover) hoveredGearCol = col;

                ResourceLocation gearTex = hover ? GEAR_HOVER : GEAR_NORMAL;
                g.blit(gearTex, gx, gy, 0, 0, GEAR_SIZE, GEAR_SIZE, GEAR_SIZE, GEAR_SIZE);
            }

            // ── Direction button ──
            {
                int dx = cx + 3;
                int dy = baseY + DIR_BTN_Y;
                boolean hover = mx >= dx && mx < dx + DIR_BTN_SIZE
                        && my >= dy && my < dy + DIR_BTN_SIZE;
                if (hover) hoveredDirCol = col;
                boolean isOut = menu.isOutputMode(col);

                int borderColor = isOut ? 0xFFCC3333 : 0xFF00AA44;
                int fromColor = isOut
                    ? (hover ? 0xFF8B4040 : 0xFF5A2020)
                    : (hover ? 0xFF408B40 : 0xFF205A20);
                int toColor = isOut
                    ? (hover ? 0xFF9B5A5A : 0xFF6A3030)
                    : (hover ? 0xFF5A9B5A : 0xFF306A30);

                g.fill(dx - 1, dy - 1, dx + DIR_BTN_SIZE + 1, dy + DIR_BTN_SIZE + 1, borderColor);
                g.fillGradient(dx, dy, dx + DIR_BTN_SIZE, dy + DIR_BTN_SIZE, fromColor, toColor);

                Icon arrowIcon = isOut ? Icon.ARROW_UP : Icon.ARROW_DOWN;
                arrowIcon.getBlitter().dest(dx, dy, DIR_BTN_SIZE, DIR_BTN_SIZE).blit(g);

                if (hover) {
                    g.fill(dx, dy, dx + DIR_BTN_SIZE, dy + DIR_BTN_SIZE, 0x33FFFFFF);
                }
            }

        }
    }

    // ══════════════ priority buttons ══════════════

    private void renderPriorityButtons(GuiGraphics g, int baseX, int baseY, int mx, int my) {
        hoveredPriorityCol = -1;

        for (int col = 0; col < 9; col++) {
            int cx = baseX + COL_START_X + col * COL_SPACING;
            int cy = baseY + GEAR_Y;

            boolean hover = mx >= cx && mx < cx + GEAR_SIZE
                    && my >= cy && my < cy + GEAR_SIZE;
            if (hover) hoveredPriorityCol = col;

            // Render AE2-style priority icon (gray when not hovered, white when hovered)
            if (hover) {
                g.blit(PRIORITY_ICON, cx, cy, 0, 0, GEAR_SIZE, GEAR_SIZE, GEAR_SIZE, GEAR_SIZE);
            } else {
                g.setColor(0.5f, 0.5f, 0.5f, 1.0f);
                g.blit(PRIORITY_ICON, cx, cy, 0, 0, GEAR_SIZE, GEAR_SIZE, GEAR_SIZE, GEAR_SIZE);
                g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
            }

            // Hover highlight
            if (hover) {
                g.fill(cx, cy, cx + GEAR_SIZE, cy + GEAR_SIZE, 0x33FFFFFF);
            }

            // Priority value centered below the slot
            int priority = menu.tile.getSlotPriority(col);
            String prioText = String.valueOf(priority);
            int textWidth = font.width(prioText);
            int textColor = priority > 0 ? PRIORITY_POS_COLOR
                    : (priority < 0 ? PRIORITY_NEG_COLOR : PRIORITY_ZERO_COLOR);
            g.drawString(font, prioText,
                    cx + 8 - textWidth / 2, baseY + PRIORITY_TEXT_Y, textColor, false);
        }
    }

    // ══════════════ render ══════════════

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        // ── Standard item tooltip for non-ghost slots (safety net) ──
        if (this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            int idx = this.hoveredSlot.index;
            // Ghost marker slots (hidden at -2000) and already-handled binding slots
            if (idx >= InterfaceMenu.MARKER_START && idx < InterfaceMenu.PLAYER_START) {
                // skip hidden ghost slots
            } else if (idx >= InterfaceMenu.BINDING_START && idx < InterfaceMenu.MARKER_START) {
                // Binding card slots: gold highlight + custom tooltip
                Slot hs = this.hoveredSlot;
                ItemStack stack = hs.getItem();
                if (stack.has(com.portint.ModDataComponents.BOUND_TARGET.get())) {
                    g.fill(leftPos + hs.x, topPos + hs.y,
                            leftPos + hs.x + 16, topPos + hs.y + 16, 0x33DAFFFF);

                    List<Component> analysis = buildAnalysisTooltip(stack);
                    if (!analysis.isEmpty()) {
                        g.renderTooltip(this.font, analysis, Optional.empty(),
                                mouseX, mouseY + 12);
                    }
                }
            } else {
                // Player inventory / upgrade slots: show full item tooltip
                g.renderTooltip(this.font, this.hoveredSlot.getItem(), mouseX, mouseY);
            }
        }

        // ── Column widget tooltips ──
        if (menu.tile.hasColumnConfig()) {
            if (hoveredGearCol >= 0) {
                g.renderTooltip(font,
                        Component.translatable("gui.portint.configure_column", hoveredGearCol + 1),
                        mouseX, mouseY);
            }
            if (hoveredDirCol >= 0) {
                boolean isOut = menu.isOutputMode(hoveredDirCol);
                Component tip = Component.translatable(isOut ? "gui.portint.mode_output" : "gui.portint.mode_input");
                g.renderTooltip(font,
                        Component.translatable("gui.portint.column_direction", hoveredDirCol + 1, tip),
                        mouseX, mouseY);
            }
        } else {
            if (hoveredPriorityCol >= 0) {
                int priority = menu.tile.getSlotPriority(hoveredPriorityCol);
                g.renderTooltip(font,
                        Component.translatable("gui.portint.priority_tooltip",
                                hoveredPriorityCol + 1, priority),
                        mouseX, mouseY);
            }
        }

        // ── Upgrade slot tooltips ──
        if (hoveredUpgrade0 || hoveredUpgrade1) {
            Container upgradeInv = menu.tile.getUpgradeInventory();
            boolean slot0Empty = upgradeInv.getItem(0).isEmpty();
            boolean slot1Empty = upgradeInv.getItem(1).isEmpty();

            // Only show hint when the hovered slot is empty
            boolean hoveredSlotEmpty = (hoveredUpgrade0 && slot0Empty)
                                    || (hoveredUpgrade1 && slot1Empty);
            if (hoveredSlotEmpty) {
                List<Component> lines = new ArrayList<>();
                lines.add(Component.translatable("gui.portint.available_upgrades").withStyle(ChatFormatting.WHITE));
                lines.add(Component.translatable("gui.portint.upgrade_range").withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("gui.portint.upgrade_dimension").withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("gui.portint.upgrade_long", InterfaceBlockEntity.LONG_CARD_ITEM_RATE).withStyle(ChatFormatting.GRAY));
                g.renderTooltip(font, lines, Optional.empty(), mouseX, mouseY);
            }
        }
    }

    private List<Component> buildAnalysisTooltip(ItemStack stack) {
        List<Component> lines = new ArrayList<>();
        var opt = stack.get(com.portint.ModDataComponents.BOUND_TARGET.get());
        if (opt != null && opt.isPresent()) {
            var bt = opt.get();
            lines.add(Component.translatable("gui.portint.bound_target")
                    .withStyle(ChatFormatting.GOLD));

            // Block name
            var nameOpt = stack.get(com.portint.ModDataComponents.BOUND_BLOCK_NAME.get());
            if (nameOpt != null && nameOpt.isPresent()) {
                lines.add(Component.translatable("gui.portint.bound_block", nameOpt.get())
                        .withStyle(ChatFormatting.WHITE));
            }

            lines.add(Component.translatable("gui.portint.bound_coords",
                    bt.pos().getX(), bt.pos().getY(), bt.pos().getZ())
                    .withStyle(ChatFormatting.YELLOW));
            lines.add(Component.literal(
                    "  维度: " + bt.dimension().location())
                    .withStyle(ChatFormatting.GRAY));

            // Distance check
            var tile = menu.tile;
            if (tile instanceof com.portint.block.InterfaceBlockEntity ifce) {
                if (ifce.getLevel() != null && bt.dimension().equals(ifce.getLevel().dimension())) {
                    double dist = Math.sqrt(bt.pos().distSqr(ifce.getBlockPos()));
                    int iDist = (int) Math.round(dist);
                    boolean hasRange = ifce.hasRangeCard();
                    boolean hasDim = ifce.hasDimCard();
                    int maxRange = (hasRange || hasDim) ? Integer.MAX_VALUE : 32;
                    boolean exceeded = iDist > maxRange;

                    ChatFormatting distColor = exceeded ? ChatFormatting.RED : ChatFormatting.GREEN;
                    String rangeLabel = (hasRange || hasDim) ? "∞" : "32";
                    lines.add(Component.translatable("gui.portint.bound_distance", iDist, rangeLabel)
                            .withStyle(distColor));
                    if (exceeded) {
                        lines.add(Component.translatable("gui.portint.distance_exceeded")
                                .withStyle(ChatFormatting.RED));
                    }
                }
            }
        }
        return lines;
    }

    // ══════════════ mouse ══════════════

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // Allow right-click for priority buttons
        if (button != 0 && button != 1) return super.mouseClicked(mx, my, button);
        if (button != 0 && !menu.tile.hasColumnConfig() && hoveredPriorityCol < 0)
            return super.mouseClicked(mx, my, button);

        if (menu.tile.hasColumnConfig()) {
            // Gear button → open FilterScreen via server
            if (hoveredGearCol >= 0) {
                if (minecraft != null && minecraft.gameMode != null) {
                    minecraft.gameMode.handleInventoryButtonClick(
                            menu.containerId, 20 + hoveredGearCol);
                }
                return true;
            }

            // Direction button → toggle mode
            if (hoveredDirCol >= 0) {
                if (minecraft != null && minecraft.gameMode != null) {
                    minecraft.gameMode.handleInventoryButtonClick(
                            menu.containerId, hoveredDirCol);
                }
                return true;
            }

        } else {
            // Priority icon button: left = +1, right = -1, shift = ×10
            if (hoveredPriorityCol >= 0) {
                if (minecraft != null && minecraft.gameMode != null) {
                    boolean shift = net.minecraft.client.gui.screens.Screen.hasShiftDown();
                    int delta = (button == 1) ? -1 : 1; // right-click = decrease
                    if (shift) delta *= 10;
                    int id = delta > 0
                            ? (shift ? 60 + hoveredPriorityCol : 40 + hoveredPriorityCol)
                            : (shift ? 50 + hoveredPriorityCol : 30 + hoveredPriorityCol);
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
                }
                return true;
            }
        }

        return super.mouseClicked(mx, my, button);
    }
}

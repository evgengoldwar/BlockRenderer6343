package blockrenderer6343.integration.nei;

import static blockrenderer6343.client.utils.BRUtil.FAKE_PLAYER;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import org.jetbrains.annotations.NotNull;
import org.joml.Vector2i;

import blockrenderer6343.client.utils.BRButton;
import blockrenderer6343.client.utils.BRUtil;
import blockrenderer6343.client.utils.ColorUtils;
import codechicken.nei.PositionedStack;

/**
 * Scrollable list of the blocks that can be placed at the selected position, grouped by their kind (e.g. every tier of
 * an input bus under one heading). Every variant gets its own slot, nothing cycles.
 */
public class CandidatePanel {

    private static final int SLOT = 18;
    private static final int PADDING = 3;
    private static final int HEADER_HEIGHT = 12;
    private static final int LABEL_HEIGHT = 10;
    private static final int SCROLLBAR_WIDTH = 3;
    private static final int MAX_COLUMNS = 5;
    private static final int MIN_WIDTH = 72;

    private final List<Group> groups = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private final List<PositionedStack> visibleStacks = new ArrayList<>();
    private int totalCandidates;
    private int columns = 1;
    private int firstRow;
    private int maxFirstRow;
    private int lastVisibleRow;
    private boolean dirty = true;

    private int x, y, width, height;
    private Runnable closeAction;

    public void setCloseAction(Runnable closeAction) {
        this.closeAction = closeAction;
    }

    public void setCandidates(@NotNull List<List<ItemStack>> candidates) {
        groups.clear();
        totalCandidates = 0;
        firstRow = 0;

        List<ItemStack> ungrouped = new ArrayList<>();
        for (List<ItemStack> candidate : candidates) {
            if (candidate.isEmpty()) continue;
            totalCandidates += candidate.size();
            if (candidate.size() > 1) {
                groups.add(new Group(getGroupName(candidate.get(0)), candidate));
            } else {
                ungrouped.add(candidate.get(0));
            }
        }

        if (!ungrouped.isEmpty()) {
            // only label the leftovers when there are named groups to tell them apart from
            String name = groups.isEmpty() ? null : I18n.format("blockrenderer6343.nei.candidates.other");
            groups.add(new Group(name, ungrouped));
        }

        int widest = 1;
        for (Group group : groups) {
            widest = Math.max(widest, group.stacks.size());
        }
        columns = Math.min(MAX_COLUMNS, widest);
        dirty = true;
    }

    private static String getGroupName(ItemStack stack) {
        List<String> tooltip = stack.getTooltip(FAKE_PLAYER, false);
        if (tooltip.size() > 1) {
            String line = EnumChatFormatting.getTextWithoutFormattingCodes(tooltip.get(1));
            if (line != null && !line.trim().isEmpty()) return line.trim();
        }
        return EnumChatFormatting.getTextWithoutFormattingCodes(stack.getDisplayName());
    }

    public boolean isEmpty() {
        return totalCandidates == 0;
    }

    public int getPreferredWidth() {
        return Math.max(MIN_WIDTH, columns * SLOT + PADDING * 2 + SCROLLBAR_WIDTH + 1);
    }

    public void setBounds(int x, int y, int width, int height) {
        if (this.x != x || this.y != y || this.width != width || this.height != height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            dirty = true;
        }
    }

    private int contentTop() {
        return y + HEADER_HEIGHT + 2;
    }

    private int contentHeight() {
        return height - HEADER_HEIGHT - 2 - PADDING;
    }

    private void rebuild() {
        dirty = false;
        rows.clear();
        for (Group group : groups) {
            if (group.name != null) rows.add(new Row(group.name, null));
            for (int i = 0; i < group.stacks.size(); i += columns) {
                rows.add(new Row(null, group.stacks.subList(i, Math.min(group.stacks.size(), i + columns))));
            }
        }

        // the furthest we can scroll while still filling the visible area
        int area = contentHeight();
        int used = 0;
        maxFirstRow = rows.size();
        for (int i = rows.size() - 1; i >= 0; i--) {
            used += rows.get(i).height();
            if (used > area) break;
            maxFirstRow = i;
        }
        firstRow = Math.max(0, Math.min(firstRow, maxFirstRow));

        visibleStacks.clear();
        int rowY = contentTop();
        int bottom = contentTop() + area;
        lastVisibleRow = firstRow;
        for (int i = firstRow; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (rowY + row.height() > bottom) break;
            row.y = rowY;
            lastVisibleRow = i + 1;
            if (row.stacks != null) {
                for (int col = 0; col < row.stacks.size(); col++) {
                    visibleStacks.add(
                            new PositionedStack(row.stacks.get(col), x + PADDING + col * SLOT + 1, rowY + 1, false));
                }
            }
            rowY += row.height();
        }
    }

    public List<PositionedStack> getVisibleStacks() {
        if (isEmpty()) return Collections.emptyList();
        if (dirty) rebuild();
        return visibleStacks;
    }

    public void draw(Vector2i mouse) {
        if (isEmpty()) return;
        if (dirty) rebuild();

        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRenderer;

        BRUtil.drawBorderedRect(
                x,
                y,
                width,
                height,
                ColorUtils.withAlpha(ColorUtils.widgetBackground, 0xE8),
                ColorUtils.opaque(ColorUtils.widgetBorder));
        BRUtil.drawRect(x + 1, y + 1, x + width - 1, y + HEADER_HEIGHT, ColorUtils.opaque(ColorUtils.widgetHover));

        String title = I18n.format("blockrenderer6343.nei.candidates.title", totalCandidates);
        font.drawStringWithShadow(font.trimStringToWidth(title, width - 16), x + 4, y + 3, 0xFFFFFF);

        boolean closeHovered = isOverClose(mouse);
        font.drawStringWithShadow("x", x + width - 9, y + 2, closeHovered ? 0xFF5555 : 0xA0A6B4);

        for (int i = firstRow; i < lastVisibleRow; i++) {
            Row row = rows.get(i);
            if (row.label != null) {
                font.drawStringWithShadow(
                        trimmedLabel(font, row.label),
                        x + PADDING + 1,
                        row.y + 1,
                        ColorUtils.accent.getColor() & 0xFFFFFF);
            } else {
                for (int col = 0; col < row.stacks.size(); col++) {
                    BRUtil.drawSlot(x + PADDING + col * SLOT, row.y);
                }
            }
        }

        if (maxFirstRow > 0) {
            int trackX = x + width - SCROLLBAR_WIDTH - 1;
            int trackTop = contentTop();
            int trackHeight = contentHeight();
            BRUtil.drawRect(trackX, trackTop, trackX + SCROLLBAR_WIDTH, trackTop + trackHeight, 0x40FFFFFF);
            int thumbHeight = Math.max(6, trackHeight * (rows.size() - maxFirstRow) / rows.size());
            int thumbY = trackTop + (trackHeight - thumbHeight) * firstRow / maxFirstRow;
            BRUtil.drawRect(
                    trackX,
                    thumbY,
                    trackX + SCROLLBAR_WIDTH,
                    thumbY + thumbHeight,
                    ColorUtils.opaque(ColorUtils.accent));
        }
    }

    private String trimmedLabel(FontRenderer font, String label) {
        int available = width - PADDING * 2 - SCROLLBAR_WIDTH - 2;
        if (font.getStringWidth(label) <= available) return label;
        return font.trimStringToWidth(label, available - font.getStringWidth("..")) + "..";
    }

    public boolean isMouseOver(Vector2i mouse) {
        return !isEmpty() && mouse.x >= x && mouse.x < x + width && mouse.y >= y && mouse.y < y + height;
    }

    private boolean isOverClose(Vector2i mouse) {
        return mouse.x >= x + width - 11 && mouse.x < x + width - 1 && mouse.y >= y + 1 && mouse.y < y + HEADER_HEIGHT;
    }

    /**
     * @return true if the click was used by the panel itself. Clicks on items are left to NEI.
     */
    public boolean mouseClicked(Vector2i mouse) {
        if (!isMouseOver(mouse)) return false;
        if (isOverClose(mouse)) {
            BRButton.playClickSound();
            if (closeAction != null) closeAction.run();
            return true;
        }
        return mouse.y < y + HEADER_HEIGHT;
    }

    public boolean mouseScrolled(Vector2i mouse, int scroll) {
        if (!isMouseOver(mouse) || scroll == 0) return false;
        if (dirty) rebuild();
        int target = Math.max(0, Math.min(maxFirstRow, firstRow + (scroll > 0 ? -1 : 1)));
        if (target != firstRow) {
            firstRow = target;
            dirty = true;
        }
        return true;
    }

    public @NotNull List<String> getTooltip(Vector2i mouse) {
        if (!isMouseOver(mouse)) return Collections.emptyList();
        if (isOverClose(mouse)) {
            return Collections.singletonList(I18n.format("blockrenderer6343.nei.candidates.close"));
        }
        if (mouse.y < y + HEADER_HEIGHT) {
            List<String> tooltip = new ArrayList<>();
            String title = I18n.format("blockrenderer6343.nei.candidates.title", totalCandidates);
            tooltip.add(EnumChatFormatting.WHITE + title);
            tooltip.add(EnumChatFormatting.GRAY + I18n.format("blockrenderer6343.nei.candidates.help"));
            return tooltip;
        }
        for (int i = firstRow; i < lastVisibleRow; i++) {
            Row row = rows.get(i);
            if (row.label != null && mouse.y >= row.y && mouse.y < row.y + row.height()) {
                return Collections.singletonList(row.label);
            }
        }
        return Collections.emptyList();
    }

    private static class Group {

        final String name;
        final List<ItemStack> stacks;

        Group(String name, List<ItemStack> stacks) {
            this.name = name;
            this.stacks = stacks;
        }
    }

    private static class Row {

        final String label;
        final List<ItemStack> stacks;
        int y;

        Row(String label, List<ItemStack> stacks) {
            this.label = label;
            this.stacks = stacks;
        }

        int height() {
            return label != null ? LABEL_HEIGHT : SLOT;
        }
    }
}

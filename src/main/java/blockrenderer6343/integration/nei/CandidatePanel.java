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
 * Compact strip docked under the 3D view listing the blocks that can be placed at the selected position. Candidates
 * are grouped by kind (e.g. every tier of an input bus); one group is shown at a time as a single row of slots, the
 * header switches between groups. The 3D view is shortened while the strip is open, so it never covers the structure.
 */
public class CandidatePanel {

    public static final int HEIGHT = 35;

    private static final int SLOT = 18;
    private static final int HEADER_HEIGHT = 12;
    private static final int ARROW_WIDTH = 9;
    private static final int CLOSE_WIDTH = 10;

    private final List<Group> groups = new ArrayList<>();
    private final List<PositionedStack> visibleStacks = new ArrayList<>();
    private int totalCandidates;
    private int currentGroup;
    private int itemOffset;
    private boolean dirty = true;

    private int x, y, width;
    private Runnable closeAction;

    public void setCloseAction(Runnable closeAction) {
        this.closeAction = closeAction;
    }

    public void setCandidates(@NotNull List<List<ItemStack>> candidates) {
        groups.clear();
        totalCandidates = 0;
        currentGroup = 0;
        itemOffset = 0;

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
            String name = groups.isEmpty() ? null : I18n.format("blockrenderer6343.nei.candidates.other");
            groups.add(new Group(name, ungrouped));
        }
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

    public void setBounds(int x, int y, int width) {
        if (this.x != x || this.y != y || this.width != width) {
            this.x = x;
            this.y = y;
            this.width = width;
            dirty = true;
        }
    }

    private Group group() {
        return groups.get(currentGroup);
    }

    private int rowY() {
        return y + HEADER_HEIGHT + 1;
    }

    private int visibleSlots() {
        return Math.max(1, (width - 6) / SLOT);
    }

    private int maxOffset() {
        return Math.max(0, group().stacks.size() - visibleSlots());
    }

    private boolean hasMultipleGroups() {
        return groups.size() > 1;
    }

    private void rebuild() {
        dirty = false;
        visibleStacks.clear();
        if (isEmpty()) return;
        itemOffset = Math.max(0, Math.min(itemOffset, maxOffset()));
        List<ItemStack> stacks = group().stacks;
        int end = Math.min(stacks.size(), itemOffset + visibleSlots());
        for (int i = itemOffset; i < end; i++) {
            int slotX = x + 3 + (i - itemOffset) * SLOT;
            visibleStacks.add(new PositionedStack(stacks.get(i), slotX + 1, rowY() + 1, false));
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

        FontRenderer font = Minecraft.getMinecraft().fontRenderer;

        BRUtil.drawRect(x, y, x + width, y + HEIGHT, ColorUtils.withAlpha(ColorUtils.widgetBackground, 0xF0));
        BRUtil.drawRect(x, y, x + width, y + 1, ColorUtils.opaque(ColorUtils.accent));
        BRUtil.drawRect(x, y + 1, x + width, y + HEADER_HEIGHT, ColorUtils.opaque(ColorUtils.widgetHover));

        // header: < group name  1/3 >   x
        int textLeft = x + 3;
        int textRight = x + width - CLOSE_WIDTH - 1;
        if (hasMultipleGroups()) {
            drawArrow(font, "<", x + 1, isOverPrevious(mouse));
            drawArrow(font, ">", textRight - ARROW_WIDTH, isOverNext(mouse));
            textLeft = x + ARROW_WIDTH + 2;
            textRight -= ARROW_WIDTH + 1;
        }

        String counter = hasMultipleGroups() ? " " + (currentGroup + 1) + "/" + groups.size() : "";
        String name = group().name != null ? group().name
                : I18n.format("blockrenderer6343.nei.candidates.title", totalCandidates);
        int nameSpace = textRight - textLeft - font.getStringWidth(counter);
        String shownName = font.getStringWidth(name) <= nameSpace ? name
                : font.trimStringToWidth(name, Math.max(0, nameSpace - font.getStringWidth(".."))) + "..";
        font.drawStringWithShadow(shownName, textLeft, y + 3, 0xFFFFFF);
        font.drawStringWithShadow(counter, textLeft + font.getStringWidth(shownName), y + 3, 0x8A92A6);

        font.drawStringWithShadow("x", x + width - 8, y + 2, isOverClose(mouse) ? 0xFF5555 : 0xA0A6B4);

        // the row of slots
        int shown = visibleStacks.size();
        for (int i = 0; i < shown; i++) {
            BRUtil.drawSlot(x + 3 + i * SLOT, rowY());
        }

        // horizontal scrollbar under the row
        int max = maxOffset();
        if (max > 0) {
            int trackX = x + 3;
            int trackWidth = width - 6;
            int trackY = rowY() + SLOT + 1;
            BRUtil.drawRect(trackX, trackY, trackX + trackWidth, trackY + 2, 0x40FFFFFF);
            int size = group().stacks.size();
            int thumbWidth = Math.max(6, trackWidth * visibleSlots() / size);
            int thumbX = trackX + (trackWidth - thumbWidth) * itemOffset / max;
            BRUtil.drawRect(thumbX, trackY, thumbX + thumbWidth, trackY + 2, ColorUtils.opaque(ColorUtils.accent));
        }
    }

    private void drawArrow(FontRenderer font, String arrow, int arrowX, boolean hovered) {
        if (hovered) {
            BRUtil.drawRect(arrowX, y + 1, arrowX + ARROW_WIDTH, y + HEADER_HEIGHT, 0x40FFFFFF);
        }
        font.drawStringWithShadow(arrow, arrowX + 2, y + 3, hovered ? 0xFFFFFF : 0xA0A6B4);
    }

    public boolean isMouseOver(Vector2i mouse) {
        return !isEmpty() && mouse.x >= x && mouse.x < x + width && mouse.y >= y && mouse.y < y + HEIGHT;
    }

    private boolean isOverHeader(Vector2i mouse) {
        return isMouseOver(mouse) && mouse.y < y + HEADER_HEIGHT;
    }

    private boolean isOverClose(Vector2i mouse) {
        return isOverHeader(mouse) && mouse.x >= x + width - CLOSE_WIDTH;
    }

    private boolean isOverPrevious(Vector2i mouse) {
        return hasMultipleGroups() && isOverHeader(mouse) && mouse.x < x + 1 + ARROW_WIDTH;
    }

    private boolean isOverNext(Vector2i mouse) {
        int arrowX = x + width - CLOSE_WIDTH - 1 - ARROW_WIDTH;
        return hasMultipleGroups() && isOverHeader(mouse) && mouse.x >= arrowX && mouse.x < arrowX + ARROW_WIDTH;
    }

    private void switchGroup(int direction) {
        currentGroup = Math.floorMod(currentGroup + direction, groups.size());
        itemOffset = 0;
        dirty = true;
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
        if (isOverPrevious(mouse) || isOverNext(mouse)) {
            BRButton.playClickSound();
            switchGroup(isOverPrevious(mouse) ? -1 : 1);
            return true;
        }
        return isOverHeader(mouse);
    }

    public boolean mouseScrolled(Vector2i mouse, int scroll) {
        if (!isMouseOver(mouse) || scroll == 0) return false;
        if (isOverHeader(mouse)) {
            if (hasMultipleGroups()) switchGroup(scroll > 0 ? -1 : 1);
            return true;
        }
        int target = Math.max(0, Math.min(maxOffset(), itemOffset + (scroll > 0 ? -1 : 1)));
        if (target != itemOffset) {
            itemOffset = target;
            dirty = true;
        }
        return true;
    }

    public @NotNull List<String> getTooltip(Vector2i mouse) {
        if (!isOverHeader(mouse)) return Collections.emptyList();
        if (isOverClose(mouse)) {
            return Collections.singletonList(I18n.format("blockrenderer6343.nei.candidates.close"));
        }
        if (isOverPrevious(mouse)) {
            return Collections.singletonList(I18n.format("blockrenderer6343.nei.candidates.previous"));
        }
        if (isOverNext(mouse)) {
            return Collections.singletonList(I18n.format("blockrenderer6343.nei.candidates.next"));
        }
        List<String> tooltip = new ArrayList<>();
        if (group().name != null) tooltip.add(EnumChatFormatting.WHITE + group().name);
        tooltip.add(
                EnumChatFormatting.GRAY + I18n.format("blockrenderer6343.nei.candidates.title", totalCandidates));
        for (int i = 0; i < groups.size(); i++) {
            Group other = groups.get(i);
            if (other.name == null) continue;
            EnumChatFormatting color = i == currentGroup ? EnumChatFormatting.AQUA : EnumChatFormatting.DARK_GRAY;
            tooltip.add(color + " - " + other.name + " (" + other.stacks.size() + ")");
        }
        tooltip.add(EnumChatFormatting.DARK_GRAY + I18n.format("blockrenderer6343.nei.candidates.help"));
        return tooltip;
    }

    private static class Group {

        final String name;
        final List<ItemStack> stacks;

        Group(String name, List<ItemStack> stacks) {
            this.name = name;
            this.stacks = stacks;
        }
    }
}

package blockrenderer6343.client.utils;

import static blockrenderer6343.integration.nei.GuiMultiblockHandler.ICON_SIZE_X;
import static blockrenderer6343.integration.nei.GuiMultiblockHandler.ICON_SIZE_Y;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;
import org.joml.Vector2i;
import org.lwjgl.opengl.GL11;

import cpw.mods.fml.client.config.GuiButtonExt;

public class BRButton extends GuiButtonExt {

    protected Runnable clickAction;
    protected BooleanSupplier activeSupplier;
    protected String tooltip = "";
    protected int index;

    public BRButton(int id, int xPos, int yPos, int width, int height, String displayString) {
        super(id, xPos, yPos, width, height, displayString);
    }

    public BRButton(int xPos, int yPos, String displayString) {
        this(0, xPos, yPos, ICON_SIZE_X, ICON_SIZE_Y, displayString);
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        if (!visible || !isMouseOver(mouseX, mouseY)) return false;
        if (clickAction == null) return true;
        playClickSound();
        clickAction.run();
        return true;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!visible) return;
        boolean hovered = isMouseOver(mouseX, mouseY);
        boolean active = isActive();

        int background = hovered ? ColorUtils.withAlpha(ColorUtils.widgetHover, 0xE6)
                : ColorUtils.withAlpha(ColorUtils.widgetBackground, 0xD0);
        int border;
        if (active) {
            border = ColorUtils.opaque(ColorUtils.accent);
            background = ColorUtils.withAlpha(ColorUtils.accent, hovered ? 0x90 : 0x60);
        } else {
            border = ColorUtils.opaque(hovered ? ColorUtils.widgetBorderHover : ColorUtils.widgetBorder);
        }

        GL11.glDisable(GL11.GL_LIGHTING);
        BRUtil.drawBorderedRect(xPosition, yPosition, width, height, background, border);

        FontRenderer font = mc.fontRenderer;
        String text = font.trimStringToWidth(displayString, width - 2);
        int color = hovered || active ? 0xFFFFFF : ColorUtils.widgetText.getColor() & 0xFFFFFF;
        int textX = xPosition + (width - font.getStringWidth(text) + 1) / 2;
        int textY = yPosition + (height - 7) / 2;
        font.drawStringWithShadow(text, textX, textY, color);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    public void mouseDragged(int mouseX, int mouseY) {}

    public boolean mouseScrolled(int mouseX, int mouseY, int scroll) {
        return false;
    }

    public BRButton setTooltip(String tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    public BRButton setClickAction(Runnable action) {
        this.clickAction = action;
        return this;
    }

    /**
     * Makes this button render as a toggle, highlighted while the supplier returns true.
     */
    public BRButton setActiveSupplier(BooleanSupplier supplier) {
        this.activeSupplier = supplier;
        return this;
    }

    public boolean isActive() {
        return activeSupplier != null && activeSupplier.getAsBoolean();
    }

    public BRButton setIndex(int index) {
        this.index = index;
        return this;
    }

    public BRButton setBounds(int x, int y, int width, int height) {
        this.xPosition = x;
        this.yPosition = y;
        this.width = width;
        this.height = height;
        return this;
    }

    public boolean isMouseOver(Vector2i mousePos) {
        return isMouseOver(mousePos.x, mousePos.y);
    }

    public boolean isMouseOver(int mouseX, int mouseY) {
        return visible && mouseX >= this.xPosition
                && mouseY >= this.yPosition
                && mouseX < this.xPosition + this.width
                && mouseY < this.yPosition + this.height;
    }

    public static void playClickSound() {
        Minecraft.getMinecraft().getSoundHandler()
                .playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
    }

    public @NotNull List<String> getTooltip(Vector2i mousePos) {
        if (!isMouseOver(mousePos) || tooltip.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(tooltip.split("\n"));
    }

    public void scalePosition(int scaledScene, float scale) {
        this.yPosition = scaledScene + ((height + 1) * (index / 2));
    }
}

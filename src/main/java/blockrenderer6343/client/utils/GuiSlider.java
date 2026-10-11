package blockrenderer6343.client.utils;

import static blockrenderer6343.integration.nei.GuiMultiblockHandler.SCENE_HEIGHT;

import java.util.function.IntSupplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.MathHelper;

import org.jetbrains.annotations.NotNull;
import org.lwjgl.opengl.GL11;

import it.unimi.dsi.fastutil.ints.Int2ObjectFunction;
import it.unimi.dsi.fastutil.ints.IntConsumer;

public class GuiSlider extends BRButton {

    private int maxValue;
    private int minValue;
    private int value;
    private boolean dragging;
    private IntConsumer valueListener;
    private IntSupplier maxValueSupplier;
    private Int2ObjectFunction<String> valueStringSupplier;
    public final String name;
    private float scale = 1f;
    private final int originalHeight;

    public GuiSlider(String name, int x, int y, int width, int height, int value, int minValue, int maxValue) {
        super(0, x, y, width, height, "");
        this.name = name;
        this.value = value;
        this.maxValue = maxValue;
        this.minValue = minValue;
        this.originalHeight = height;
    }

    public GuiSlider(String name, int x, int y, int width, int height, int value, int minValue) {
        this(name, x, y, width, height, value, minValue, 0);
    }

    public GuiSlider setMaxValueSupplier(@NotNull IntSupplier supplier) {
        this.maxValueSupplier = supplier;
        return this;
    }

    public GuiSlider setValueListener(@NotNull IntConsumer listener) {
        this.valueListener = listener;
        return this;
    }

    public GuiSlider setTextSupplier(@NotNull Int2ObjectFunction<String> supplier) {
        this.valueStringSupplier = supplier;
        return this;
    }

    @Override
    public int getHoverState(boolean mouseOver) {
        return 0;
    }

    public int getMaxValue() {
        return maxValueSupplier == null ? maxValue : maxValueSupplier.getAsInt();
    }

    public int getValue() {
        return value;
    }

    private boolean isLocked() {
        return maxValue <= minValue;
    }

    @Override
    public void scalePosition(int scaledGuiHeight, float scale) {
        this.scale = scale;
        height = Math.round(originalHeight * scale);
        yPosition = Math.round(SCENE_HEIGHT * scale + (height + 1) * index);
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!visible) return;
        maxValue = getMaxValue();
        boolean hovered = isMouseOver(mouseX, mouseY) || dragging;
        boolean locked = isLocked();

        GL11.glDisable(GL11.GL_LIGHTING);
        int border = hovered && !locked ? ColorUtils.opaque(ColorUtils.widgetBorderHover)
                : ColorUtils.opaque(ColorUtils.widgetBorder);
        int background = hovered && !locked ? ColorUtils.withAlpha(ColorUtils.widgetHover, 0xE6)
                : ColorUtils.withAlpha(ColorUtils.widgetBackground, 0xD0);
        BRUtil.drawBorderedRect(xPosition, yPosition, width, height, background, border);

        if (!locked) {
            float progress = (float) (value - minValue) / (maxValue - minValue);
            int inner = width - 2;
            int fill = MathHelper.clamp_int(Math.round(inner * progress), 0, inner);
            if (fill > 0) {
                BRUtil.drawRect(
                        xPosition + 1,
                        yPosition + 1,
                        xPosition + 1 + fill,
                        yPosition + height - 1,
                        ColorUtils.withAlpha(ColorUtils.accent, hovered ? 0x90 : 0x60));
            }
            int knobX = MathHelper.clamp_int(xPosition + 1 + fill, xPosition + 2, xPosition + width - 2);
            BRUtil.drawRect(knobX - 1, yPosition + 1, knobX + 1, yPosition + height - 1, 0xFFFFFFFF);
        }

        FontRenderer font = mc.fontRenderer;
        String text = getText(font);
        int color = locked ? 0x8A8F9C : hovered ? 0xFFFFFF : ColorUtils.widgetText.getColor() & 0xFFFFFF;
        BRUtil.drawCenteredScaledString(
                text,
                xPosition + (double) width / 2,
                yPosition + (int) ((height - 7 * scale) / 2),
                color,
                scale);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    public void setValue(int newValue) {
        setValue(newValue, true);
    }

    public void setValue(int newValue, boolean notify) {
        value = MathHelper.clamp_int(newValue, minValue, Math.max(minValue, maxValue));
        if (notify && valueListener != null) valueListener.accept(value);
    }

    public void setValueFromMouse(int mouseX) {
        if (isLocked()) return;
        float progress = (float) (mouseX - xPosition - 1) / Math.max(1, width - 2);
        setValue(Math.round(minValue + (maxValue - minValue) * progress));
    }

    public String getText(FontRenderer font) {
        String val = ": " + (valueStringSupplier == null ? value + "" : valueStringSupplier.apply(value));
        int available = (int) ((width - 4) / scale) - font.getStringWidth(val);
        String trimmed = font.trimStringToWidth(name, Math.max(0, available));
        return trimmed + val;
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        if (!isMouseOver(mouseX, mouseY)) return false;
        maxValue = getMaxValue();
        setValueFromMouse(mouseX);
        dragging = true;
        playClickSound();
        return true;
    }

    @Override
    public void mouseDragged(int mousePosX, int mousePosY) {
        if (dragging) {
            setValueFromMouse(mousePosX);
        }
    }

    @Override
    public boolean mouseScrolled(int mouseX, int mouseY, int scroll) {
        if (scroll == 0 || !isMouseOver(mouseX, mouseY) && !dragging) return false;
        maxValue = getMaxValue();
        if (scroll > 0) setValue(value + 1);
        else setValue(value - 1);
        return true;
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY) {
        dragging = false;
    }
}

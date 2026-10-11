package blockrenderer6343.client.utils;

import com.gtnewhorizon.gtnhlib.color.ColorResource;

public class ColorUtils {

    private static final ColorResource.Factory color = new ColorResource.Factory("blockrenderer6343");

    public static final ColorResource
    // spotless:off
        neiBackground       = color.rgb("neiBackground",        "0xC6C6C6"),
        sceneBackgroundTop  = color.rgb("sceneBackgroundTop",   "0x4B5263"),
        sceneBackgroundBot  = color.rgb("sceneBackgroundBot",   "0x1E2128"),
        sceneBorder         = color.rgb("sceneBorder",          "0x0E0F12"),
        widgetBackground    = color.rgb("widgetBackground",     "0x23262E"),
        widgetHover         = color.rgb("widgetHover",          "0x363B47"),
        widgetBorder        = color.rgb("widgetBorder",         "0x4A5060"),
        widgetBorderHover   = color.rgb("widgetBorderHover",    "0x9AA3B8"),
        widgetText          = color.rgb("widgetText",           "0xE2E5EC"),
        accent              = color.rgb("accent",               "0x3D9BFF");
    // spotless:on

    /** Returns the color of the resource with the given alpha (0-255). */
    public static int withAlpha(ColorResource resource, int alpha) {
        return (resource.getColor() & 0xFFFFFF) | (alpha & 0xFF) << 24;
    }

    public static int opaque(ColorResource resource) {
        return withAlpha(resource, 0xFF);
    }
}

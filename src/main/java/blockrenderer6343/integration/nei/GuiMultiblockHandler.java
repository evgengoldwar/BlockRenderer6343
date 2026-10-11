package blockrenderer6343.integration.nei;

import static blockrenderer6343.client.utils.BRUtil.FAKE_PLAYER;

import java.awt.Point;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MovingObjectPosition;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector2i;
import org.joml.Vector3f;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import com.gtnewhorizon.gtnhlib.eventbus.EventBusSubscriber;
import com.gtnewhorizon.gtnhlib.util.CoordinatePacker;
import com.gtnewhorizon.structurelib.StructureEvent;
import com.gtnewhorizon.structurelib.StructureLibAPI;
import com.gtnewhorizon.structurelib.alignment.constructable.ChannelDataAccessor;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureElement;

import blockrenderer6343.BlockRenderer6343;
import blockrenderer6343.client.renderer.ImmediateWorldSceneRenderer;
import blockrenderer6343.client.renderer.WorldSceneRenderer;
import blockrenderer6343.client.utils.BRButton;
import blockrenderer6343.client.utils.BRUtil;
import blockrenderer6343.client.utils.ColorUtils;
import blockrenderer6343.client.utils.ConstructableData;
import blockrenderer6343.client.utils.GuiSlider;
import blockrenderer6343.client.world.TrackedDummyWorld;
import codechicken.lib.gui.GuiDraw;
import codechicken.lib.math.MathHelper;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.recipe.GuiRecipe;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import it.unimi.dsi.fastutil.longs.Long2BooleanMap;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

@EventBusSubscriber(side = Side.CLIENT)
public abstract class GuiMultiblockHandler {

    protected static ImmediateWorldSceneRenderer renderer;

    public static final int SLOT_SIZE = 18;
    public static final int ICON_SIZE_X = 20;
    public static final int ICON_SIZE_Y = 12;
    public static final long NO_SELECTED_BLOCK = CoordinatePacker.pack(-100000, 0, -100000);
    public static final int UNDER_PREVIEW_Y = 136;
    public static final int BUTTON_RIGHT = 145;
    public static final int RECIPE_LAYOUT_X = 6;
    public static final int RECIPE_LAYOUT_Y = 33;
    public static final int RECIPE_WIDTH = 163;
    public static final int SCENE_HEIGHT = RECIPE_WIDTH - 10;
    public static final Long2ObjectMap<IStructureElement<Object>> structureElementMap = new Long2ObjectOpenHashMap<>();

    protected static final int BETWEEN_BUTTON_X = ICON_SIZE_X + 2;
    protected static final int SLIDER_WIDTH = BUTTON_RIGHT - BETWEEN_BUTTON_X - 2;
    protected static final float DEFAULT_RANGE_MULTIPLIER = 3.5f;
    protected static final int MAX_PLACE_ROUNDS = 2000;
    public static final BlockPos MB_PLACE_POS = new BlockPos(0, 64, 0);
    protected static final BlockPos SELECTED_BLOCK = new BlockPos().set(NO_SELECTED_BLOCK);
    protected static final ItemStack DEFAULT_TRIGGER = new ItemStack(StructureLibAPI.getDefaultHologramItem());

    // layout of the widgets around the preview
    protected static final int TOOLBAR_BUTTON_SIZE = 11;
    protected static final int WIDGET_HEIGHT = 12;
    protected static final int WIDGET_GAP = 2;
    protected static final int ACTION_COLUMN_WIDTH = 46;
    protected static final float MIN_ZOOM = 2f, MAX_ZOOM = 999f;
    protected static final float AUTO_ROTATE_SPEED = 18f; // degrees per second
    protected static final long DOUBLE_CLICK_MS = 300;

    protected static int guiMouseX, guiMouseY, guiLeft, guiTop;
    protected static int lastGuiMouseX, lastGuiMouseY;
    protected static Vector3f center = new Vector3f();
    protected static float rotationYaw, rotationPitch;
    protected static float zoom;
    protected static IConstructable renderingController, lastRenderingController;
    protected static int layerIndex = -1;
    /** When a layer is selected, show every layer below it as well instead of only the selected one. */
    protected static boolean cumulativeLayers = false;
    protected static boolean autoRotate = false;

    private static final boolean[] mouseDown = new boolean[3];
    private static final boolean[] draggingScene = new boolean[3];
    private static long lastFrameNanos;
    private static long lastSceneClick;

    protected ItemStack tooltipBlockStack;

    protected ConstructableData constructableData;
    protected ItemStack stackForm;

    protected Consumer<List<List<ItemStack>>> onCandidateChanged;
    protected Consumer<List<ItemStack>> onIngredientChanged;

    protected int scrolled = 0;
    protected float scaleFactor;

    protected ItemStack trigger;
    protected int lastHeight;
    protected final ObjectList<BRButton> allButtons = new ObjectArrayList<>();
    protected final ObjectList<GuiSlider> sliders = new ObjectArrayList<>();
    protected final ObjectList<BRButton> actionButtons = new ObjectArrayList<>();
    protected final ObjectList<BRButton> toolbarButtons = new ObjectArrayList<>();
    protected GuiSlider tierSlider;
    protected GuiSlider layerSlider;
    protected Vector2i relativeMousePos = new Vector2i();
    /** Position and size of the preview, relative to the recipe origin. */
    protected int sceneX, sceneY, sceneWidth, sceneHeight;
    private int buttonsInRow;
    protected String lastSearch = "";
    protected static Minecraft mc = Minecraft.getMinecraft();

    protected abstract void placeMultiblock();

    public void loadMultiblock(IConstructable multiblock, ItemStack stackForm, @NotNull ConstructableData data) {
        renderingController = multiblock;
        constructableData = data;

        this.stackForm = stackForm;
        if (stackForm.stackSize == 0) stackForm.stackSize = 1;
        if (lastRenderingController != renderingController) {
            initGui();
            loadNewMultiblock();
        } else {
            initializeSceneRenderer(false);
        }
    }

    protected void initGui() {
        allButtons.clear();
        sliders.clear();
        actionButtons.clear();
        toolbarButtons.clear();
        buttonsInRow = 0;

        addSlider(
                tierSlider = new GuiSlider(
                        I18n.format("blockrenderer6343.nei.tier"),
                        0,
                        UNDER_PREVIEW_Y,
                        SLIDER_WIDTH,
                        WIDGET_HEIGHT,
                        1,
                        1,
                        constructableData.getMaxTotalTier()).setValueListener(this::setTier));
        addSlider(
                layerSlider = new GuiSlider(
                        I18n.format("blockrenderer6343.nei.layer"),
                        0,
                        UNDER_PREVIEW_Y,
                        SLIDER_WIDTH,
                        WIDGET_HEIGHT,
                        -1,
                        -1).setTextSupplier(
                                value -> value == -1 ? I18n.format("blockrenderer6343.nei.all")
                                        : String.valueOf(value + 1))
                                .setMaxValueSupplier(() -> (int) renderer.world.getSize().y - 1)
                                .setValueListener(this::setActiveLayer));
        loadChannels();

        // toolbar inside the preview
        addToolbarButton("↺", tooltip("reset_view")).setClickAction(this::resetView);
        addToolbarButton("↻", tooltip("auto_rotate")).setClickAction(() -> autoRotate = !autoRotate)
                .setActiveSupplier(() -> autoRotate);
        addToolbarButton("≡", tooltip("cumulative_layers")).setClickAction(() -> {
            cumulativeLayers = !cumulativeLayers;
            if (layerIndex > -1) updateRenderer();
        }).setActiveSupplier(() -> cumulativeLayers);

        // actions under the preview
        addButtonInRow(I18n.format("blockrenderer6343.nei.button.project"))
                .setTooltip(I18n.format("blockrenderer6343.multiblock.project")).setClickAction(
                        () -> BRUtil.projectMultiblock(
                                getBuildTriggerStack(),
                                stackForm,
                                MathHelper.floor_double(MB_PLACE_POS.y - renderer.world.getMinPos().y)));
        addButtonInRow(I18n.format("blockrenderer6343.nei.button.overlay"))
                .setTooltip(I18n.format("blockrenderer6343.multiblock.overlay"))
                .setClickAction(() -> BRUtil.neiOverlay(renderer));
        if (!constructableData.getChannelMaxTierMap().isEmpty()) {
            addButtonInRow(I18n.format("blockrenderer6343.nei.button.copy"))
                    .setTooltip(I18n.format("blockrenderer6343.multiblock.copy_channels"))
                    .setClickAction(() -> BRUtil.copyToHologram(trigger));
        }
    }

    /** Called after the subclasses had the chance to add their own widgets, so the help button is always last. */
    private void addHelpButton() {
        for (BRButton button : toolbarButtons) {
            if ("?".equals(button.displayString)) return;
        }
        addToolbarButton("?", helpTooltip());
    }

    protected static String tooltip(String key) {
        return EnumChatFormatting.WHITE + I18n.format("blockrenderer6343.nei.toolbar." + key)
                + "\n"
                + EnumChatFormatting.GRAY
                + I18n.format("blockrenderer6343.nei.toolbar." + key + ".desc");
    }

    private static String helpTooltip() {
        StringBuilder builder = new StringBuilder();
        builder.append(EnumChatFormatting.WHITE).append(I18n.format("blockrenderer6343.nei.help.title"));
        for (String key : new String[] { "rotate", "zoom", "pan", "select", "reset", "layers" }) {
            builder.append('\n').append(EnumChatFormatting.GRAY)
                    .append(I18n.format("blockrenderer6343.nei.help." + key));
        }
        return builder.toString();
    }

    /**
     * Adds a button to the row of actions under the preview.
     */
    protected BRButton addButtonInRow(@NotNull String displayString) {
        BRButton button = new BRButton(0, 0, displayString).setIndex(buttonsInRow++);
        actionButtons.add(button);
        allButtons.add(button);
        return button;
    }

    /**
     * Adds a small icon button to the toolbar in the top right corner of the preview.
     */
    protected BRButton addToolbarButton(@NotNull String icon, @NotNull String tooltip) {
        BRButton button = new BRButton(0, 0, 0, TOOLBAR_BUTTON_SIZE, TOOLBAR_BUTTON_SIZE, icon).setTooltip(tooltip)
                .setIndex(toolbarButtons.size());
        toolbarButtons.add(button);
        allButtons.add(button);
        return button;
    }

    protected GuiSlider addSlider(@NotNull GuiSlider slider) {
        slider.setIndex(sliders.size());
        sliders.add(slider);
        allButtons.add(slider);
        return slider;
    }

    protected void loadChannels() {
        Object2IntMap<String> channels = constructableData.getChannelMaxTierMap();
        if (channels.isEmpty()) return;
        for (Object2IntMap.Entry<String> entry : channels.object2IntEntrySet()) {
            String channel = entry.getKey();
            int startVal = constructableData.getCurrentChannel().equals(channel) ? constructableData.getCurrentTier()
                    : 0;
            addSlider(
                    new GuiSlider(
                            StringUtils.capitalize(channel),
                            0,
                            UNDER_PREVIEW_Y,
                            SLIDER_WIDTH,
                            WIDGET_HEIGHT,
                            startVal,
                            0,
                            entry.getIntValue()).setValueListener(val -> setChannelTier(channel, val))
                                    .setTextSupplier(
                                            value -> value == 0 ? I18n.format("blockrenderer6343.nei.not_set")
                                                    : String.valueOf(value)));
        }
    }

    protected void loadNewMultiblock() {
        addHelpButton();
        trigger = DEFAULT_TRIGGER.copy();
        int tier = constructableData.getCurrentTier();
        String channel = constructableData.getCurrentChannel();

        if (channel.isEmpty()) {
            tierSlider.setValue(trigger.stackSize = tier, false);
        } else {
            setChannelTier(channel, tier, false);
        }

        layerIndex = -1;
        layerSlider.setValue(-1, false);
        lastSearch = "";
        initializeSceneRenderer(true);
        lastRenderingController = renderingController;
    }

    private void setTier(int tier) {
        if (tier <= 0) {
            tier = 1;
        }

        if (renderer != null && tier == trigger.stackSize) return;

        trigger.stackSize = tier;
        initializeSceneRenderer(false);
    }

    protected void setChannelTier(String channel, int tier) {
        setChannelTier(channel, tier, true);
    }

    private void setChannelTier(String channel, int tier, boolean rebuild) {
        if (tier < 0) return;

        int current = ChannelDataAccessor.hasSubChannel(trigger, channel)
                ? ChannelDataAccessor.getChannelData(trigger, channel)
                : 0;

        if (tier > 0) {
            ChannelDataAccessor.setChannelData(trigger, channel, tier);
        } else {
            ChannelDataAccessor.unsetChannelData(trigger, channel);
        }

        if (current != tier && rebuild) {
            initializeSceneRenderer(false);
        }
    }

    private void setActiveLayer(int newLayer) {
        int height = (int) renderer.world.getSize().y() - 1;
        if (newLayer < 0 || newLayer > height) {
            // if current layer index is more than max height, reset it
            // to display all layers
            newLayer = -1;
        }

        if (newLayer == layerIndex) return;

        layerIndex = newLayer;
        if (layerSlider != null && layerSlider.getValue() != newLayer) {
            layerSlider.setValue(newLayer, false);
        }

        updateRenderer();
    }

    private void updateRenderer() {
        if (renderer == null) return;

        if (layerIndex > -1) {
            if (cumulativeLayers) {
                renderer.setRenderUpToYLayer(layerIndex);
            } else {
                renderer.setRenderYLayer(layerIndex);
            }
        } else {
            renderer.setRenderAllBlocks();
        }

        onIngredientChanged.accept(BRUtil.getIngredients(renderer));

        if (lastSearch != null && !lastSearch.isEmpty()) {
            Long2BooleanMap checkedBlocks = new Long2BooleanOpenHashMap();

            boolean foundMatch = false;

            LongIterator iter = renderer.renderedBlocks.longIterator();

            while (iter.hasNext()) {
                long coord = iter.nextLong();

                boolean matches = checkedBlocks.computeIfAbsent(BRUtil.hashBlock(renderer.world, coord), b -> {
                    int x = CoordinatePacker.unpackX(coord);
                    int y = CoordinatePacker.unpackY(coord);
                    int z = CoordinatePacker.unpackZ(coord);

                    Block block = renderer.world.getBlock(x, y, z);

                    ItemStack stack = new ItemStack(block, 1, BRUtil.getDamageValue(renderer.world, block, coord));

                    return matchesSearch(stack, lastSearch);
                });

                foundMatch |= matches;

                if (!matches) {
                    renderer.renderTranslucentBlocks.add(coord);
                    iter.remove();
                }
            }

            if (foundMatch) {
                renderer.setRenderAllFaces(true);
            }
        }
    }

    private void centerCamera() {
        TrackedDummyWorld world = renderer.world;
        Vector3f size = world.getSize();
        Vector3f minPos = world.getMinPos();
        center.set(minPos.x + size.x / 2, minPos.y + size.y / 2, minPos.z + size.z / 2);
    }

    private void applyCamera() {
        renderer.setCameraLookAt(center, zoom, Math.toRadians(rotationPitch), Math.toRadians(rotationYaw));
    }

    /**
     * Moves the camera back to its default position, framing the whole structure.
     */
    public void resetView() {
        if (renderer == null) return;
        Vector3f size = renderer.world.getSize();
        float max = Math.max(Math.max(size.x, size.y), size.z);
        float baseZoom = (float) (DEFAULT_RANGE_MULTIPLIER * Math.sqrt(max));
        float sizeFactor = (float) (1.0f + Math.log(max) / Math.log(10));

        zoom = baseZoom * sizeFactor / 1.5f;
        rotationYaw = 20.0f;
        rotationPitch = 50f;
        centerCamera();
        applyCamera();
    }

    public void drawMultiblock(int recipeIndex) {
        GuiRecipe<?> recipeGui = (GuiRecipe<?>) NEIClientUtils.getGuiContainer();
        guiMouseX = GuiDraw.getMousePosition().x;
        guiMouseY = GuiDraw.getMousePosition().y;
        guiLeft = recipeGui.guiLeft;
        guiTop = recipeGui.guiTop;

        int guiHeight = recipeGui.height;
        scaleFactor = Math.min((float) recipeGui.height / 500, 1f);
        int scaledScene = Math.round(SCENE_HEIGHT * scaleFactor);

        Point recipePos = recipeGui.getRecipePosition(recipeIndex);
        relativeMousePos.set(guiMouseX - guiLeft - recipePos.x, guiMouseY - guiTop - recipePos.y);
        sceneX = RECIPE_LAYOUT_X - recipePos.x;
        sceneY = RECIPE_LAYOUT_Y - recipePos.y;
        sceneWidth = RECIPE_WIDTH;
        sceneHeight = scaledScene;
        layoutWidgets();

        handleCameraInput();

        // background & frame of the preview
        BRUtil.drawRect(
                sceneX - 1,
                sceneY - 1,
                sceneX + sceneWidth + 1,
                sceneY + sceneHeight + 1,
                ColorUtils.opaque(ColorUtils.sceneBorder));
        BRUtil.drawGradientRect(
                sceneX,
                sceneY,
                sceneX + sceneWidth,
                sceneY + sceneHeight,
                ColorUtils.opaque(ColorUtils.sceneBackgroundTop),
                ColorUtils.opaque(ColorUtils.sceneBackgroundBot));

        renderer.render(
                RECIPE_LAYOUT_X + guiLeft,
                RECIPE_LAYOUT_Y + guiTop,
                RECIPE_WIDTH,
                scaledScene,
                guiMouseX,
                guiMouseY);

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        drawSceneInfo();
        for (BRButton button : allButtons) {
            button.drawButton(mc, relativeMousePos.x, relativeMousePos.y);
        }
        GL11.glPopAttrib();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        tooltipBlockStack = null;
        MovingObjectPosition rayTraceResult = renderer.getLastTraceResult();
        boolean dragging = draggingScene[0] || draggingScene[1] || draggingScene[2];
        if (!dragging && rayTraceResult != null
                && !isOverWidget()
                && !renderer.world.isAirBlock(rayTraceResult.blockX, rayTraceResult.blockY, rayTraceResult.blockZ)) {
            Block block = renderer.world.getBlock(rayTraceResult.blockX, rayTraceResult.blockY, rayTraceResult.blockZ);
            tooltipBlockStack = block.getPickBlock(
                    rayTraceResult,
                    renderer.world,
                    rayTraceResult.blockX,
                    rayTraceResult.blockY,
                    rayTraceResult.blockZ,
                    mc.thePlayer);
        }

        lastHeight = guiHeight;
        lastGuiMouseX = guiMouseX;
        lastGuiMouseY = guiMouseY;
    }

    /**
     * Places the toolbar inside the preview and the sliders/actions under it.
     */
    protected void layoutWidgets() {
        int toolbarX = sceneX + sceneWidth - TOOLBAR_BUTTON_SIZE - 2;
        for (int i = toolbarButtons.size() - 1; i >= 0; i--) {
            toolbarButtons.get(i).setBounds(toolbarX, sceneY + 2, TOOLBAR_BUTTON_SIZE, TOOLBAR_BUTTON_SIZE);
            toolbarX -= TOOLBAR_BUTTON_SIZE + 1;
        }

        int top = sceneY + sceneHeight + 3;
        int sliderWidth = actionButtons.isEmpty() ? sceneWidth : sceneWidth - ACTION_COLUMN_WIDTH - WIDGET_GAP;
        for (int i = 0; i < sliders.size(); i++) {
            sliders.get(i).setBounds(sceneX, top + i * (WIDGET_HEIGHT + WIDGET_GAP), sliderWidth, WIDGET_HEIGHT);
        }

        int actionX = sceneX + sceneWidth - ACTION_COLUMN_WIDTH;
        for (int i = 0; i < actionButtons.size(); i++) {
            actionButtons.get(i)
                    .setBounds(actionX, top + i * (WIDGET_HEIGHT + WIDGET_GAP), ACTION_COLUMN_WIDTH, WIDGET_HEIGHT);
        }
    }

    private void handleCameraInput() {
        long now = System.nanoTime();
        float deltaSeconds = lastFrameNanos == 0 ? 0 : Math.min((now - lastFrameNanos) / 1.0e9f, 0.1f);
        lastFrameNanos = now;

        // a drag only controls the camera when it started on the preview (and not on a widget),
        // but it keeps working when the cursor leaves the preview afterwards
        boolean canStartDrag = isInsideView() && !isOverWidget();
        for (int i = 0; i < mouseDown.length; i++) {
            boolean down = Mouse.isButtonDown(i);
            if (down && !mouseDown[i]) {
                draggingScene[i] = canStartDrag;
            } else if (!down) {
                draggingScene[i] = false;
            }
            mouseDown[i] = down;
        }

        int mouseDeltaX = guiMouseX - lastGuiMouseX;
        int mouseDeltaY = guiMouseY - lastGuiMouseY;

        if (draggingScene[0]) {
            rotationPitch = (rotationPitch + mouseDeltaX + 360) % 360;
            rotationYaw = (float) MathHelper.clip(rotationYaw + mouseDeltaY, -89.9, 89.9);
        } else if (draggingScene[1]) {
            zoom = (float) MathHelper.clip(zoom * Math.exp(mouseDeltaY * 0.01), MIN_ZOOM, MAX_ZOOM);
        }

        if (draggingScene[2] && (mouseDeltaX != 0 || mouseDeltaY != 0)) {
            Vector3f lookAt = renderer.getLookAt();
            Vector3f eyePos = renderer.getEyePos();
            Vector3f worldUp = renderer.getWorldUp();
            Vector3f lookDir = new Vector3f(lookAt).sub(eyePos);
            Vector3f rightDir = lookDir.cross(worldUp, new Vector3f()).normalize();
            Vector3f upDir = rightDir.cross(lookDir, new Vector3f()).normalize();
            // move the structure with the cursor: world units per gui pixel at the focus distance (fov = 60)
            float unitsPerPixel = 1.1547f * zoom / Math.max(1, sceneHeight);
            Vector3f offset = new Vector3f(
                    -mouseDeltaX * rightDir.x() + mouseDeltaY * upDir.x(),
                    -mouseDeltaX * rightDir.y() + mouseDeltaY * upDir.y(),
                    -mouseDeltaX * rightDir.z() + mouseDeltaY * upDir.z()).mul(unitsPerPixel);
            center.add(offset);
        }

        if (scrolled != 0) {
            zoom = (float) MathHelper.clip(zoom * (scrolled > 0 ? 0.87f : 1.15f), MIN_ZOOM, MAX_ZOOM);
            scrolled = 0;
        }

        if (autoRotate && !draggingScene[0]) {
            rotationPitch = (rotationPitch + AUTO_ROTATE_SPEED * deltaSeconds) % 360;
        }

        applyCamera();
    }

    /**
     * Draws the structure size and the current layer in the top left corner of the preview.
     */
    protected void drawSceneInfo() {
        Vector3f size = renderer.world.getSize();
        int height = (int) size.y;
        String dimensions = I18n.format("blockrenderer6343.nei.size", (int) size.x, height, (int) size.z);
        drawInfoLine(dimensions, sceneX + 3, sceneY + 3, 0xFFFFFF);

        if (layerIndex > -1) {
            String layer = cumulativeLayers ? I18n.format("blockrenderer6343.nei.layers_up_to", layerIndex + 1, height)
                    : I18n.format("blockrenderer6343.nei.layer_of", layerIndex + 1, height);
            drawInfoLine(layer, sceneX + 3, sceneY + 15, ColorUtils.accent.getColor() & 0xFFFFFF);
        }
    }

    private void drawInfoLine(String text, int x, int y, int color) {
        int width = mc.fontRenderer.getStringWidth(text);
        BRUtil.drawRect(x - 2, y - 2, x + width + 1, y + 9, 0x90000000);
        mc.fontRenderer.drawStringWithShadow(text, x, y, color);
    }

    protected boolean isOverWidget() {
        for (BRButton button : allButtons) {
            if (button.isMouseOver(relativeMousePos)) return true;
        }
        return false;
    }

    private boolean isInsideView() {
        return guiMouseX >= guiLeft + RECIPE_LAYOUT_X && guiMouseX <= guiLeft + RECIPE_LAYOUT_X + RECIPE_WIDTH
                && guiMouseY >= guiTop + RECIPE_LAYOUT_Y
                && guiMouseY <= guiTop + RECIPE_LAYOUT_Y + (SCENE_HEIGHT * scaleFactor);
    }

    protected @NotNull String getMultiblockName() {
        return stackForm == null ? "" : I18n.format(stackForm.getDisplayName());
    }

    protected void initializeSceneRenderer(boolean resetCamera) {
        boolean keepCamera = !resetCamera && renderer != null;
        Vector3f previousCenter = new Vector3f(center);

        renderer = new ImmediateWorldSceneRenderer(new TrackedDummyWorld());
        renderer.world.updateEntitiesForNEI();

        FAKE_PLAYER.setWorld(renderer.world);
        renderer.world.unloadEntities(Collections.singletonList(FAKE_PLAYER));

        if (!StructureLibAPI.isInstrumentEnabled()) {
            StructureLibAPI.enableInstrument(BlockRenderer6343.MOD_ID);
        }

        structureElementMap.clear();
        placeMultiblock();

        if (StructureLibAPI.isInstrumentEnabled()) {
            StructureLibAPI.disableInstrument();
        }

        renderer.setClearColor(false);
        renderer.setRenderAllBlocks();

        renderer.setOnLookingAt(ray -> {});

        renderer.setBeforeWorldRender(this::drawGroundGrid);
        renderer.setPostBlockRender(this::drawHighlights);

        SELECTED_BLOCK.set(NO_SELECTED_BLOCK);
        onCandidateChanged.accept(Collections.emptyList());

        setActiveLayer(layerIndex);
        updateRenderer();

        if (keepCamera) {
            center.set(previousCenter);
            applyCamera();
        } else {
            resetView();
        }
    }

    public boolean handleMouseScrollUp(int scrolled) {
        for (BRButton button : allButtons) {
            if (button.mouseScrolled(relativeMousePos.x, relativeMousePos.y, scrolled)) {
                return true;
            }
        }

        if (isInsideView()) {
            this.scrolled = scrolled;
            return true;
        }

        return false;
    }

    /**
     * Draws a faint grid under the visible part of the structure.
     */
    protected void drawGroundGrid(WorldSceneRenderer renderer) {
        TrackedDummyWorld world = renderer.world;
        Vector3f minPos = world.getMinPos();
        Vector3f size = world.getSize();
        float y = minPos.y + (layerIndex > -1 && !cumulativeLayers ? layerIndex : 0) - 0.01f;
        int minX = (int) minPos.x - 1, maxX = (int) (minPos.x + size.x) + 1;
        int minZ = (int) minPos.z - 1, maxZ = (int) (minPos.z + size.z) + 1;

        GL11.glPushAttrib(
                GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT
                        | GL11.GL_LINE_BIT
                        | GL11.GL_DEPTH_BUFFER_BIT
                        | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);
        GL11.glLineWidth(1.0f);
        GL11.glColor4f(1f, 1f, 1f, 0.12f);
        GL11.glBegin(GL11.GL_LINES);
        for (int x = minX; x <= maxX; x++) {
            GL11.glVertex3f(x, y, minZ);
            GL11.glVertex3f(x, y, maxZ);
        }
        for (int z = minZ; z <= maxZ; z++) {
            GL11.glVertex3f(minX, y, z);
            GL11.glVertex3f(maxX, y, z);
        }
        GL11.glEnd();
        GL11.glPopAttrib();
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    /**
     * Outlines the hovered and the selected block, then lets subclasses draw their own overlays.
     */
    protected void drawHighlights(WorldSceneRenderer renderer) {
        MovingObjectPosition lookingPos = renderer.getLastTraceResult();
        long lookingBlock = lookingPos == null ? NO_SELECTED_BLOCK
                : CoordinatePacker.pack(lookingPos.blockX, lookingPos.blockY, lookingPos.blockZ);
        long selectedBlock = SELECTED_BLOCK.asLong();

        GL11.glPushAttrib(
                GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT
                        | GL11.GL_LINE_BIT
                        | GL11.GL_DEPTH_BUFFER_BIT
                        | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);

        if (selectedBlock != NO_SELECTED_BLOCK) {
            highlightBlock(selectedBlock, ColorUtils.accent.getColor() & 0xFFFFFF);
        }
        if (lookingBlock != NO_SELECTED_BLOCK && lookingBlock != selectedBlock) {
            highlightBlock(lookingBlock, 0xFFFFFF);
        }

        GL11.glPopAttrib();
        GL11.glColor4f(1f, 1f, 1f, 1f);

        onPostBlocksRendered(renderer);
    }

    private void highlightBlock(long pos, int color) {
        int x = CoordinatePacker.unpackX(pos);
        int y = CoordinatePacker.unpackY(pos);
        int z = CoordinatePacker.unpackZ(pos);
        Block block = renderer.world.getBlock(x, y, z);
        if (renderer.world.isAirBlock(x, y, z)) return;

        block.setBlockBoundsBasedOnState(renderer.world, x, y, z);
        double e = 0.004;
        double minX = x + block.getBlockBoundsMinX() - e, maxX = x + block.getBlockBoundsMaxX() + e;
        double minY = y + block.getBlockBoundsMinY() - e, maxY = y + block.getBlockBoundsMaxY() + e;
        double minZ = z + block.getBlockBoundsMinZ() - e, maxZ = z + block.getBlockBoundsMaxZ() + e;

        // faint "x-ray" outline so the selection stays visible behind other blocks
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glLineWidth(1.0f);
        BRUtil.drawBoxHighlight(minX, minY, minZ, maxX, maxY, maxZ, color, 0f, 0.3f);

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glLineWidth(2.5f);
        BRUtil.drawBoxHighlight(minX, minY, minZ, maxX, maxY, maxZ, color, 0.2f, 1f);
    }

    public boolean mouseClicked(int button) {
        if (button == 0) {
            for (BRButton buttons : allButtons) {
                if (buttons.mousePressed(mc, relativeMousePos.x, relativeMousePos.y)) {
                    SELECTED_BLOCK.set(NO_SELECTED_BLOCK);
                    onCandidateChanged.accept(Collections.emptyList());
                    return true;
                }
            }

            if (renderer != null && isInsideView()) {
                long now = System.currentTimeMillis();
                if (now - lastSceneClick < DOUBLE_CLICK_MS) {
                    lastSceneClick = 0;
                    resetView();
                    return true;
                }
                lastSceneClick = now;
            }
            return false;
        }
        if (button == 1 && renderer != null) {
            if (isOverWidget()) return true;
            MovingObjectPosition rayTrace = renderer.getLastTraceResult();
            if (rayTrace == null) {
                if (SELECTED_BLOCK.asLong() != NO_SELECTED_BLOCK) {
                    SELECTED_BLOCK.set(NO_SELECTED_BLOCK);
                    onCandidateChanged.accept(Collections.emptyList());
                    return true;
                }
                return false;
            }
            long pos = CoordinatePacker.pack(rayTrace.blockX, rayTrace.blockY, rayTrace.blockZ);
            if (pos == SELECTED_BLOCK.asLong()) {
                // clicking the selected block again deselects it
                SELECTED_BLOCK.set(NO_SELECTED_BLOCK);
                onCandidateChanged.accept(Collections.emptyList());
                return true;
            }
            onCandidateChanged.accept(
                    BRUtil.scanCandidates(
                            getContextObject(),
                            structureElementMap.get(pos),
                            getOriginalTriggerStack(),
                            SELECTED_BLOCK.set(pos)));
        }
        return false;
    }

    public void onMouseDragged() {
        for (BRButton button : allButtons) {
            button.mouseDragged(relativeMousePos.x, relativeMousePos.y);
        }
    }

    public void onMouseReleased() {
        for (BRButton button : allButtons) {
            button.mouseReleased(relativeMousePos.x, relativeMousePos.y);
        }
    }

    @NotNull
    protected ItemStack getOriginalTriggerStack() {
        return DEFAULT_TRIGGER;
    }

    @NotNull
    protected ItemStack getBuildTriggerStack() {
        return trigger;
    }

    public void setOnCandidateChanged(Consumer<List<List<ItemStack>>> callback) {
        onCandidateChanged = callback;
    }

    public void setOnIngredientChanged(Consumer<List<ItemStack>> callback) {
        onIngredientChanged = callback;
    }

    public void recalculateSearch(String searchText) {
        if (renderer == null) return;

        if (searchText.equals(lastSearch)) return;
        lastSearch = searchText;

        updateRenderer();
    }

    public boolean matchesSearch(ItemStack stack, String searchText) {
        boolean matches = StringUtils.containsIgnoreCase(stack.getDisplayName(), searchText);
        if (!matches) {
            List<String> tooltip = stack.getTooltip(mc.thePlayer, mc.gameSettings.advancedItemTooltips);
            matches = tooltip.stream().anyMatch(s -> StringUtils.containsIgnoreCase(s, searchText));
        }
        return matches;
    }

    public List<String> getHoveredTooltip(@NotNull ItemStack stack) {
        return stack.getTooltip(mc.thePlayer, mc.gameSettings.advancedItemTooltips);
    }

    public @NotNull List<String> getTooltip() {
        if (tooltipBlockStack != null) {
            return getHoveredTooltip(tooltipBlockStack);
        }

        for (BRButton button : allButtons) {
            List<String> tooltip = button.getTooltip(relativeMousePos);
            if (!tooltip.isEmpty()) {
                return tooltip;
            }
        }

        return Collections.emptyList();
    }

    protected Object getContextObject() {
        return renderingController;
    }

    protected void onPostBlocksRendered(WorldSceneRenderer renderer) {}

    protected void onElementAdded(@NotNull IStructureElement<Object> element, long pos) {}

    @SubscribeEvent
    @SuppressWarnings({ "unused", "unchecked" })
    public static void OnStructureEvent(StructureEvent.StructureElementVisitedEvent event) {
        if (!BlockRenderer6343.MOD_ID.equals(event.getInstrumentIdentifier())) return;
        GuiMultiblockHandler handler = MultiblockHandler.getCurrentGuiHandler();
        if (handler == null) return;
        IStructureElement<Object> element = (IStructureElement<Object>) event.getElement();
        long pos = CoordinatePacker.pack(event.getX(), event.getY(), event.getZ());
        handler.onElementAdded(element, pos);
        structureElementMap.put(pos, element);
    }
}

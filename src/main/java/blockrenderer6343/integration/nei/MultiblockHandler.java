package blockrenderer6343.integration.nei;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.gtnewhorizon.gtnhlib.eventbus.EventBusSubscriber;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;

import blockrenderer6343.client.utils.ConstructableData;
import codechicken.nei.LayoutManager;
import codechicken.nei.PositionedStack;
import codechicken.nei.RecipeSearchField;
import codechicken.nei.SearchField;
import codechicken.nei.SearchTokenParser;
import codechicken.nei.api.API;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.GuiRecipeButton.UpdateRecipeButtonsEvent;
import codechicken.nei.recipe.RecipeCatalysts;
import codechicken.nei.recipe.TemplateRecipeHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import it.unimi.dsi.fastutil.objects.ObjectSet;

@EventBusSubscriber
public abstract class MultiblockHandler extends TemplateRecipeHandler {

    public static final int CANDIDATE_SLOTS_X = 150;
    public static final int CANDIDATE_SLOTS_Y = 20;
    public static final int CANDIDATE_IN_COlUMN = 6;
    private static ItemStack lastStack;
    protected RecipeCacher recipeCacher = new RecipeCacher();
    protected GuiMultiblockHandler guiHandler;
    protected IConstructable[] currentMultiblocks;
    protected int oldRecipe;
    /** True while showing every multiblock (NEI "View all categories"), false when looking up a single item. */
    protected boolean showingAll;
    /**
     * Controllers of {@link #currentMultiblocks}, placed off screen. They identify the recipe for NEI, so it can find
     * the same multiblock again (e.g. when switching to "View all categories") and search for it by name.
     */
    protected PositionedStack[] controllerStacks;
    protected static RecipeSearchField recipeSearchField;
    protected static final PositionedStack DUMMY_STACK = new PositionedStack(
            new ItemStack(Items.poisonous_potato),
            0,
            9999,
            false);

    static {
        // this is a hack to let us hijack the "in recipe search" field while in the preview
        API.addSearchProvider(
                new SearchField.SearchParserProvider(
                        '䌡',
                        "inpreview",
                        EnumChatFormatting.RESET,
                        a -> new InPreviewFilter()) {

                    @Override
                    public SearchTokenParser.SearchMode getSearchMode() {
                        return SearchTokenParser.SearchMode.ALWAYS;
                    }
                });
        try {
            recipeSearchField = (RecipeSearchField) ReflectionHelper.findField(GuiRecipe.class, "searchField")
                    .get(null);
        } catch (Exception ignored) {}
    }

    public MultiblockHandler(GuiMultiblockHandler guiHandler) {
        this.guiHandler = guiHandler;
        guiHandler.setOnIngredientChanged(this::resetPositionedIngredients);
        guiHandler.setOnCandidateChanged(this::setResults);
    }

    public abstract @NotNull ItemStack getConstructableStack(IConstructable multiblock);

    protected abstract @NotNull ObjectSet<IConstructable> tryLoadingMultiblocks(ItemStack candidate);

    /**
     * @return every multiblock this handler can preview, used when NEI asks for all recipes.
     */
    protected abstract @NotNull Collection<IConstructable> getAllMultiblocks();

    @Override
    public void loadCraftingRecipes(String outputId, Object... results) {
        if ("all".equals(outputId)) {
            loadAllMultiblocks();
            return;
        }
        super.loadCraftingRecipes(outputId, results);
    }

    @Override
    public void loadUsageRecipes(String inputId, Object... ingredients) {
        if ("all".equals(inputId)) {
            loadAllMultiblocks();
            return;
        }
        super.loadUsageRecipes(inputId, ingredients);
    }

    @Override
    public void loadCraftingRecipes(ItemStack result) {
        loadRecipes(result);
        super.loadCraftingRecipes(result);
    }

    @Override
    public void loadUsageRecipes(ItemStack ingredient) {
        loadRecipes(ingredient);
        super.loadUsageRecipes(ingredient);
    }

    private void loadAllMultiblocks() {
        currentMultiblocks = null;
        controllerStacks = null;
        oldRecipe = -1;
        showingAll = true;

        List<Map.Entry<String, IConstructable>> entries = new ArrayList<>();
        for (IConstructable multiblock : getAllMultiblocks()) {
            try {
                ItemStack stack = getConstructableStack(multiblock);
                // noinspection ConstantConditions
                if (stack == null || stack.getItem() == null) continue;
                entries.add(new AbstractMap.SimpleEntry<>(stack.getDisplayName(), multiblock));
            } catch (Exception ignored) {
                // a broken multiblock should not prevent listing the others
            }
        }
        if (entries.isEmpty()) return;

        entries.sort(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER));
        currentMultiblocks = entries.stream().map(Map.Entry::getValue).toArray(IConstructable[]::new);
        controllerStacks = createControllerStacks(currentMultiblocks);
    }

    private void loadRecipes(ItemStack stack) {
        currentMultiblocks = null;
        controllerStacks = null;
        oldRecipe = -1;
        showingAll = false;
        ObjectSet<IConstructable> multiblocks = tryLoadingMultiblocks(stack);
        if (multiblocks.isEmpty()) return;
        lastStack = stack;
        currentMultiblocks = multiblocks.toArray(new IConstructable[0]);
        controllerStacks = createControllerStacks(currentMultiblocks);
    }

    private PositionedStack[] createControllerStacks(IConstructable[] multiblocks) {
        PositionedStack[] stacks = new PositionedStack[multiblocks.length];
        for (int i = 0; i < multiblocks.length; i++) {
            // off screen like DUMMY_STACK, never drawn
            stacks[i] = new PositionedStack(getConstructableStack(multiblocks[i]), 0, 9999, false);
        }
        return stacks;
    }

    @Override
    public void drawBackground(int recipe) {
        super.drawBackground(recipe);
        if (currentMultiblocks == null) return;
        if (oldRecipe != recipe) {
            oldRecipe = recipe;
            IConstructable multi = currentMultiblocks[recipe];
            ItemStack controller = getConstructableStack(multi);
            guiHandler.loadMultiblock(
                    multi,
                    controller,
                    ConstructableData.getTierData(multi).setTierFromStack(showingAll ? controller : lastStack));
        }

        // while listing every multiblock the search field looks for multiblocks instead of blocks in the preview
        guiHandler.recalculateSearch(showingAll ? "" : recipeSearchField.text());
        guiHandler.drawMultiblock(recipe);
    }

    public static @Nullable MultiblockHandler getHandlerFromGui(GuiScreen gui) {
        // noinspection rawtypes
        if (!(gui instanceof GuiRecipe recipe)) return null;
        if (recipe.getHandler() instanceof MultiblockHandler handler) {
            return handler;
        }
        return null;
    }

    public static @Nullable GuiMultiblockHandler getCurrentGuiHandler() {
        MultiblockHandler handler = getHandlerFromGui(Minecraft.getMinecraft().currentScreen);
        if (handler != null) {
            return handler.getGuiHandler();
        }
        return null;
    }

    @Override
    public String getOverlayIdentifier() {
        return getClass().getName();
    }

    @Override
    public int numRecipes() {
        return currentMultiblocks == null ? 0 : currentMultiblocks.length;
    }

    @Override
    public String getGuiTexture() {
        return "blockrenderer6343:textures/void.png";
    }

    @Override
    public String getRecipeName() {
        return StringUtils.abbreviate(guiHandler.getMultiblockName(), 25);
    }

    public String getFullRecipeName() {
        return guiHandler.getMultiblockName();
    }

    @Override
    public String getRecipeTabName() {
        return StatCollector.translateToLocal("blockrenderer6343.multiblock.structure");
    }

    protected @NotNull GuiMultiblockHandler getGuiHandler() {
        return guiHandler;
    }

    @Override
    public List<PositionedStack> getIngredientStacks(int recipe) {
        // NEI identifies a recipe by its ingredients, this lets it reopen the same multiblock in another view
        PositionedStack controller = getControllerStack(recipe);
        return controller == null ? Collections.emptyList() : Collections.singletonList(controller);
    }

    private @Nullable PositionedStack getControllerStack(int recipe) {
        PositionedStack[] stacks = controllerStacks;
        return stacks != null && recipe >= 0 && recipe < stacks.length ? stacks[recipe] : null;
    }

    @Override
    public PositionedStack getResultStack(int recipe) {
        if (showingAll) {
            PositionedStack controller = getControllerStack(recipe);
            if (controller != null) return controller;
        }
        // There needs to be some sort of result stack for the in recipe search to work
        return DUMMY_STACK;
    }

    @Override
    public List<PositionedStack> getOtherStacks(int recipe) {
        return arecipes.isEmpty() ? Collections.emptyList() : arecipes.get(0).getOtherStacks();
    }

    public void resetPositionedIngredients(List<ItemStack> ingredients) {
        RecipeCatalysts.putRecipeCatalysts(getOverlayIdentifier(), ingredients);
    }

    public void setResults(List<List<ItemStack>> results) {
        arecipes.clear();
        guiHandler.getCandidatePanel().setCandidates(results);
        arecipes.add(recipeCacher);
    }

    public class RecipeCacher extends CachedRecipe {

        @Override
        public PositionedStack getResult() {
            return null;
        }

        @Override
        public List<PositionedStack> getOtherStacks() {
            return guiHandler.getCandidatePanel().getVisibleStacks();
        }
    }

    public static class InPreviewFilter implements ItemFilter {

        @Override
        public boolean matches(ItemStack item) {
            MultiblockHandler current = getHandlerFromGui(Minecraft.getMinecraft().currentScreen);
            // when listing every multiblock, let the search filter them by their controller
            if (current != null && current.showingAll) return false;
            GuiMultiblockHandler handler = getCurrentGuiHandler();
            if (handler != null && !LayoutManager.searchField.focused()) {
                return recipeSearchField.isVisible() && recipeSearchField.focused()
                        && !recipeSearchField.text().isEmpty();
            }
            return false;
        }
    }

    @SubscribeEvent
    @SuppressWarnings({ "unused", "rawtypes" })
    public static void onPostOverlay(UpdateRecipeButtonsEvent.Post event) {
        if (event.gui instanceof GuiRecipe recipe && recipe.getHandler() instanceof MultiblockHandler) {
            // We have to remove the recipe overlay now that this handler has a result stack
            event.buttonList.clear();
        }
    }
}

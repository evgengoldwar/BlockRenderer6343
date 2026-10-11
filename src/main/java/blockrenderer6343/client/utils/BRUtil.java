package blockrenderer6343.client.utils;

import static blockrenderer6343.integration.nei.GuiMultiblockHandler.MB_PLACE_POS;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.StatCollector;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.opengl.GL11;

import com.github.vfyjxf.nee.network.NEENetworkHandler;
import com.github.vfyjxf.nee.network.packet.PacketNEIPatternRecipe;
import com.gtnewhorizon.gtnhlib.blockpos.BlockPos;
import com.gtnewhorizon.gtnhlib.util.CoordinatePacker;
import com.gtnewhorizon.structurelib.StructureLib;
import com.gtnewhorizon.structurelib.alignment.constructable.ConstructableUtility;
import com.gtnewhorizon.structurelib.item.ItemConstructableTrigger;
import com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.mojang.authlib.GameProfile;

import blockrenderer6343.BlockRenderer6343;
import blockrenderer6343.api.utils.CreativeItemSource;
import blockrenderer6343.client.renderer.WorldSceneRenderer;
import blockrenderer6343.client.world.ClientFakePlayer;
import blockrenderer6343.client.world.DummyWorld;
import blockrenderer6343.integration.nei.BRNEIConfig;
import blockrenderer6343.integration.nei.MultiblockHandler;
import codechicken.lib.math.MathHelper;
import codechicken.nei.ItemStackAmount;
import codechicken.nei.recipe.GuiRecipe;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import it.unimi.dsi.fastutil.longs.LongSet;

public class BRUtil {

    public static Predicate<ItemStack> hatchFilter = stack -> false;

    public static final ClientFakePlayer FAKE_PLAYER = new ClientFakePlayer(
            DummyWorld.INSTANCE,
            new GameProfile(UUID.randomUUID(), "MultiblockBuilder"));
    public static final AutoPlaceEnvironment AUTO_PLACE_ENVIRONMENT = AutoPlaceEnvironment
            .fromLegacy(CreativeItemSource.instance, FAKE_PLAYER, a -> {});

    public static void projectMultiblock(ItemStack buildStack, ItemStack multiStack, int blocksBelowController) {
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        World baseWorld = Minecraft.getMinecraft().theWorld;
        Vec3 lookVec = player.getLookVec();
        MovingObjectPosition lookingPos = player.rayTrace(10, 1);
        int blockX, blockY, blockZ;
        if (lookingPos == null || lookingPos.typeOfHit == MovingObjectPosition.MovingObjectType.MISS) {
            blockX = MathHelper.floor_double(player.posX + lookVec.xCoord * 2);
            blockZ = MathHelper.floor_double(player.posZ + lookVec.zCoord * 2);
            blockY = baseWorld.getPrecipitationHeight(blockX, blockZ) + blocksBelowController;
        } else {
            blockX = lookingPos.blockX;
            blockY = lookingPos.blockY + blocksBelowController + 1;
            blockZ = lookingPos.blockZ;
        }
        ItemStack copy = multiStack.copy();

        if (!baseWorld.isAirBlock(blockX, blockY, blockZ)) {
            player.addChatMessage(new ChatComponentText(StatCollector.translateToLocal("blockrenderer6343.no_space")));
            return;
        }

        // noinspection ConstantConditions
        if (!copy.getItem().onItemUse(copy, player, baseWorld, blockX, blockY, blockZ, 0, blockX, blockY - 1, blockZ)) {
            player.addChatMessage(new ChatComponentText(StatCollector.translateToLocal("blockrenderer6343.no_block")));
            return;
        }
        ConstructableUtility.handle(buildStack, player, baseWorld, blockX, blockY, blockZ, 0);
        baseWorld.setBlockToAir(blockX, blockY, blockZ);
        baseWorld.removeTileEntity(blockX, blockY, blockZ);
    }

    public static void neiOverlay(WorldSceneRenderer renderer) {
        if (!BlockRenderer6343.isNEELoaded) return;
        NBTTagCompound recipeInputs = new NBTTagCompound();
        GuiRecipe<?> currentScreen = (GuiRecipe<?>) Minecraft.getMinecraft().currentScreen;
        String recipeName = ((MultiblockHandler) currentScreen.getHandler()).getFullRecipeName();
        Minecraft.getMinecraft().displayGuiScreen(currentScreen.firstGui);
        List<ItemStack> ingredients = getIngredients(renderer);
        boolean filterHatches = BRNEIConfig.getConfigValue(BRNEIConfig.FILTER_HATCH);
        int slotIndex = 0;
        for (ItemStack itemStack : ingredients) {
            if (itemStack == null) continue;
            if (filterHatches && hatchFilter.test(itemStack)) continue;
            NBTTagCompound itemStackNBT = new NBTTagCompound();
            itemStack.writeToNBT(itemStackNBT);
            itemStackNBT.setInteger("Count", itemStack.stackSize);
            recipeInputs.setTag("#" + slotIndex, itemStackNBT);
            slotIndex++;
        }
        NBTTagCompound recipeOutputs = new NBTTagCompound();
        if (BRNEIConfig.getConfigValue(BRNEIConfig.AUTO_FILL_PATTERN)) {
            ItemStack paper = new ItemStack(Items.paper);
            paper.setStackDisplayName(recipeName);
            NBTTagCompound paperNBT = new NBTTagCompound();
            paper.writeToNBT(paperNBT);
            paperNBT.setInteger("Count", 1);
            recipeOutputs.setTag("Outputs0", paperNBT);
        }
        NEENetworkHandler.getInstance().sendToServer(new PacketNEIPatternRecipe(recipeInputs, recipeOutputs));
    }

    public static void copyToHologram(ItemStack triggerStack) {
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        ItemStack stack = player.getHeldItem();
        if (stack == null || !(stack.getItem() instanceof ItemConstructableTrigger)) {
            player.addChatMessage(
                    new ChatComponentText(StatCollector.translateToLocal("blockrenderer6343.no_projector")));
        } else {
            StructureLib.instance().proxy().uploadChannels(triggerStack);
        }
    }

    public static List<ItemStack> getIngredients(WorldSceneRenderer renderer) {
        ItemStackAmount controllers = new ItemStackAmount();
        ItemStackAmount hatches = new ItemStackAmount();
        ItemStackAmount blocks = new ItemStackAmount();

        for (long renderedBlock : renderer.renderedBlocks) {
            if (renderer.decorativeBlocks.contains(renderedBlock)) continue;
            int x = CoordinatePacker.unpackX(renderedBlock);
            int y = CoordinatePacker.unpackY(renderedBlock);
            int z = CoordinatePacker.unpackZ(renderedBlock);
            World world = renderer.world;

            Block block = world.getBlock(x, y, z);
            if (block.equals(Blocks.air)) continue;

            int meta = world.getBlockMetadata(x, y, z);

            ArrayList<ItemStack> drops;
            int qty = block.quantityDropped(world.rand);

            if (qty != 1) {
                drops = new ArrayList<>();
                drops.add(new ItemStack(block));
            } else {
                drops = block.getDrops(world, x, y, z, meta, 0);
            }

            if (drops.isEmpty()) continue;

            ItemStack stack = drops.get(0).copy();

            if (BlockRenderer6343.isGT5uNHLoaded) {
                TileEntity te = world.getTileEntity(x, y, z);
                IMetaTileEntity mte = null;

                if (te instanceof IGregTechTileEntity gregTechTileEntity) {
                    mte = gregTechTileEntity.getMetaTileEntity();
                }

                if (mte instanceof MTEMultiBlockBase) {
                    controllers.add(stack);
                    continue;
                } else if (mte instanceof MTEHatch) {
                    hatches.add(stack);
                    continue;
                }
            }

            if (x == MB_PLACE_POS.x && y == MB_PLACE_POS.y && z == MB_PLACE_POS.z) {
                controllers.add(stack);
            } else {
                blocks.add(stack);
            }
        }

        List<ItemStack> result = new ArrayList<>();

        addItemStacksWithSorting(hatches, result);
        addItemStacksWithSorting(blocks, result);
        addItemStacksWithSorting(controllers, result);

        return result;
    }

    private static void addItemStacksWithSorting(ItemStackAmount amount, List<ItemStack> result) {
        amount.values().stream().sorted(
                Comparator.comparingLong((ItemStack stack) -> stack.stackSize).thenComparing(ItemStack::getDisplayName))
                .collect(Collectors.toCollection(() -> result));
    }

    public static List<List<ItemStack>> scanCandidates(Object multi, IStructureElement<Object> element,
            ItemStack buildStack, BlockPos block) {
        if (element == null) return Collections.emptyList();
        IStructureElement.BlocksToPlace blocksToPlace = element.getBlocksToPlace(
                multi,
                DummyWorld.INSTANCE,
                block.x,
                block.y,
                block.z,
                buildStack,
                AUTO_PLACE_ENVIRONMENT);
        if (blocksToPlace == null) return Collections.emptyList();

        Set<ItemStack> rawCandidates = CreativeItemSource.instance
                .takeEverythingMatches(blocksToPlace.getPredicate(), false, 0).keySet();
        List<List<ItemStack>> stackedCandidates = new ArrayList<>();
        for (ItemStack rawCandidate : rawCandidates) {
            boolean added = false;
            for (List<ItemStack> stackedCandidate : stackedCandidates) {
                List<String> firstCandidateTooltip = stackedCandidate.get(0).getTooltip(FAKE_PLAYER, false);
                List<String> rawCandidateTooltip = rawCandidate.getTooltip(FAKE_PLAYER, false);
                if (firstCandidateTooltip.size() > 1 && rawCandidateTooltip.size() > 1
                        && firstCandidateTooltip.get(1).equals(rawCandidateTooltip.get(1))) {
                    stackedCandidate.add(rawCandidate);
                    added = true;
                    break;
                }
            }
            if (!added) {
                List<ItemStack> newStackedCandidate = new ArrayList<>();
                newStackedCandidate.add(rawCandidate);
                stackedCandidates.add(newStackedCandidate);
            }
        }

        return stackedCandidates;
    }

    public static long hashStack(ItemStack stack) {
        // noinspection ConstantConditions
        return stack.getItem().hashCode() * 31L + stack.getItemDamage() * 31L;
    }

    public static long hashBlock(World world, long pos) {
        int x = CoordinatePacker.unpackX(pos);
        int y = CoordinatePacker.unpackY(pos);
        int z = CoordinatePacker.unpackZ(pos);
        Block block = world.getBlock(x, y, z);
        return block.hashCode() * 31L + block.getDamageValue(world, x, y, z) * 31L;
    }

    public static int getDamageValue(World world, Block block, long pos) {
        return block.getDamageValue(
                world,
                CoordinatePacker.unpackX(pos),
                CoordinatePacker.unpackY(pos),
                CoordinatePacker.unpackZ(pos));
    }

    public static void renderOverlay(LongSet positions, long pos, int color, int alpha) {
        Tessellator t = Tessellator.instance;
        int posX = CoordinatePacker.unpackX(pos);
        int posY = CoordinatePacker.unpackY(pos);
        int posZ = CoordinatePacker.unpackZ(pos);

        GL11.glPushMatrix();
        GL11.glTranslatef(posX, posY, posZ);

        t.startDrawingQuads();
        t.setColorRGBA_I(color, alpha);

        for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            long neighborPos = CoordinatePacker.pack(posX + dir.offsetX, posY + dir.offsetY, posZ + dir.offsetZ);
            if (!positions.contains(neighborPos)) {
                renderBlockOverlaySide(t, dir);
            }
        }

        t.draw();

        GL11.glPopMatrix();
    }

    public static void renderBlockOverlaySide(Tessellator tessellator, ForgeDirection side) {
        switch (side) {
            case DOWN:
                tessellator.addVertex(0, 0, 0);
                tessellator.addVertex(1, 0, 0);
                tessellator.addVertex(1, 0, 1);
                tessellator.addVertex(0, 0, 1);
                break;
            case UP:
                tessellator.addVertex(1, 1, 0);
                tessellator.addVertex(0, 1, 0);
                tessellator.addVertex(0, 1, 1);
                tessellator.addVertex(1, 1, 1);
                break;
            case NORTH:
                tessellator.addVertex(0, 1, 0);
                tessellator.addVertex(1, 1, 0);
                tessellator.addVertex(1, 0, 0);
                tessellator.addVertex(0, 0, 0);
                break;
            case SOUTH:
                tessellator.addVertex(1, 1, 1);
                tessellator.addVertex(0, 1, 1);
                tessellator.addVertex(0, 0, 1);
                tessellator.addVertex(1, 0, 1);
                break;
            case WEST:
                tessellator.addVertex(0, 1, 1);
                tessellator.addVertex(0, 1, 0);
                tessellator.addVertex(0, 0, 0);
                tessellator.addVertex(0, 0, 1);
                break;
            case EAST:
                tessellator.addVertex(1, 1, 0);
                tessellator.addVertex(1, 1, 1);
                tessellator.addVertex(1, 0, 1);
                tessellator.addVertex(1, 0, 0);
                break;
        }
    }

    public static void drawCenteredScaledString(String text, double textX, double textY, int fontColor,
            double fontScale) {
        drawScaledString(text, textX, textY, fontColor, true, true, fontScale);
    }

    public static void drawScaledString(String text, double textX, double textY, int fontColor, boolean centered,
            boolean fontShadow, double fontScale) {
        final FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;

        GL11.glPushMatrix();

        if (fontScale != 1.0) {
            textX /= fontScale;
            textY /= fontScale;
            GL11.glScaled(fontScale, fontScale, 0);
        }

        double dTextX = textX - (double) (int) textX;
        double dTextY = textY - (double) (int) textY;
        double textWidth = fontRenderer.getStringWidth(text);
        GL11.glTranslated(dTextX, dTextY, 0.0);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        if (fontShadow) {
            fontRenderer.drawStringWithShadow(
                    text,
                    (centered ? (int) (textX - textWidth / 2.0) : (int) textX),
                    (int) textY,
                    fontColor);
        } else {
            fontRenderer.drawString(
                    text,
                    (centered ? (int) (textX - textWidth / 2.0) : (int) textX),
                    (int) textY,
                    fontColor);
        }
        GL11.glPopMatrix();
    }
    public static void drawRect(int x1, int y1, int x2, int y2, int argb) {
        Gui.drawRect(x1, y1, x2, y2, argb);
    }

    /** Draws a filled rectangle with a 1px border. */
    public static void drawBorderedRect(int x, int y, int width, int height, int background, int border) {
        Gui.drawRect(x, y, x + width, y + height, border);
        Gui.drawRect(x + 1, y + 1, x + width - 1, y + height - 1, background);
    }

    public static void drawGradientRect(int x1, int y1, int x2, int y2, int topColor, int bottomColor) {
        float a1 = (topColor >> 24 & 255) / 255.0F;
        float r1 = (topColor >> 16 & 255) / 255.0F;
        float g1 = (topColor >> 8 & 255) / 255.0F;
        float b1 = (topColor & 255) / 255.0F;
        float a2 = (bottomColor >> 24 & 255) / 255.0F;
        float r2 = (bottomColor >> 16 & 255) / 255.0F;
        float g2 = (bottomColor >> 8 & 255) / 255.0F;
        float b2 = (bottomColor & 255) / 255.0F;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_F(r1, g1, b1, a1);
        tessellator.addVertex(x2, y1, 0);
        tessellator.addVertex(x1, y1, 0);
        tessellator.setColorRGBA_F(r2, g2, b2, a2);
        tessellator.addVertex(x1, y2, 0);
        tessellator.addVertex(x2, y2, 0);
        tessellator.draw();
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    /** Draws a dark translucent slot frame, used behind item stacks drawn over the preview. */
    public static void drawSlot(int x, int y) {
        drawBorderedRect(x, y, 18, 18, 0xA0101216, 0x80FFFFFF);
    }

    /**
     * Draws the outline (and a faint fill) of the given world-space box. Expects texturing to be disabled and blending
     * enabled.
     */
    public static void drawBoxHighlight(double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
            int rgb, float fillAlpha, float lineAlpha) {
        float r = (rgb >> 16 & 255) / 255.0F;
        float g = (rgb >> 8 & 255) / 255.0F;
        float b = (rgb & 255) / 255.0F;

        if (fillAlpha > 0) {
            GL11.glColor4f(r, g, b, fillAlpha);
            GL11.glBegin(GL11.GL_QUADS);
            // bottom & top
            GL11.glVertex3d(minX, minY, minZ);
            GL11.glVertex3d(maxX, minY, minZ);
            GL11.glVertex3d(maxX, minY, maxZ);
            GL11.glVertex3d(minX, minY, maxZ);
            GL11.glVertex3d(minX, maxY, minZ);
            GL11.glVertex3d(minX, maxY, maxZ);
            GL11.glVertex3d(maxX, maxY, maxZ);
            GL11.glVertex3d(maxX, maxY, minZ);
            // north & south
            GL11.glVertex3d(minX, minY, minZ);
            GL11.glVertex3d(minX, maxY, minZ);
            GL11.glVertex3d(maxX, maxY, minZ);
            GL11.glVertex3d(maxX, minY, minZ);
            GL11.glVertex3d(minX, minY, maxZ);
            GL11.glVertex3d(maxX, minY, maxZ);
            GL11.glVertex3d(maxX, maxY, maxZ);
            GL11.glVertex3d(minX, maxY, maxZ);
            // west & east
            GL11.glVertex3d(minX, minY, minZ);
            GL11.glVertex3d(minX, minY, maxZ);
            GL11.glVertex3d(minX, maxY, maxZ);
            GL11.glVertex3d(minX, maxY, minZ);
            GL11.glVertex3d(maxX, minY, minZ);
            GL11.glVertex3d(maxX, maxY, minZ);
            GL11.glVertex3d(maxX, maxY, maxZ);
            GL11.glVertex3d(maxX, minY, maxZ);
            GL11.glEnd();
        }

        if (lineAlpha > 0) {
            GL11.glColor4f(r, g, b, lineAlpha);
            GL11.glBegin(GL11.GL_LINE_STRIP);
            GL11.glVertex3d(minX, minY, minZ);
            GL11.glVertex3d(maxX, minY, minZ);
            GL11.glVertex3d(maxX, minY, maxZ);
            GL11.glVertex3d(minX, minY, maxZ);
            GL11.glVertex3d(minX, minY, minZ);
            GL11.glEnd();
            GL11.glBegin(GL11.GL_LINE_STRIP);
            GL11.glVertex3d(minX, maxY, minZ);
            GL11.glVertex3d(maxX, maxY, minZ);
            GL11.glVertex3d(maxX, maxY, maxZ);
            GL11.glVertex3d(minX, maxY, maxZ);
            GL11.glVertex3d(minX, maxY, minZ);
            GL11.glEnd();
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex3d(minX, minY, minZ);
            GL11.glVertex3d(minX, maxY, minZ);
            GL11.glVertex3d(maxX, minY, minZ);
            GL11.glVertex3d(maxX, maxY, minZ);
            GL11.glVertex3d(maxX, minY, maxZ);
            GL11.glVertex3d(maxX, maxY, maxZ);
            GL11.glVertex3d(minX, minY, maxZ);
            GL11.glVertex3d(minX, maxY, maxZ);
            GL11.glEnd();
        }
    }
}

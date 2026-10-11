package blockrenderer6343.integration.gregtech;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;

import blockrenderer6343.BlockRenderer6343;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/**
 * Some multiblocks replace (part of) their structure with a special render once formed, e.g. the star of the Forge of
 * the Gods. This recreates those renders in the preview by doing what the machine itself does in game.
 * <p>
 * Everything is looked up reflectively by name, so a missing or changed machine only disables its render.
 */
public final class GTCustomRenders {

    @FunctionalInterface
    private interface RenderFactory {

        void create(IMetaTileEntity mte, ItemStack trigger) throws Exception;
    }

    private static final Map<String, RenderFactory> FACTORIES = new HashMap<>();

    static {
        // star and rings, replaces the ring blocks with the render
        FACTORIES.put("tectech.thing.metaTileEntity.multi.godforge.MTEForgeOfGods", (mte, trigger) -> {
            Object data = getField(mte, "data");
            int rings = Math.max(1, Math.min(3, trigger.stackSize));
            data.getClass().getMethod("setRingAmount", int.class).invoke(data, rings);
            invoke(mte, "createRenderer");
        });

        // the black hole only renders on a formed machine
        FACTORIES.put(
                "gregtech.common.tileentities.machines.multi.compressor.MTEBlackHoleCompressor",
                (mte, trigger) -> {
                    ((MTEMultiBlockBase) mte).mMachine = true;
                    invoke(mte, "createRenderBlock");
                });

        // the wormhole render is created on demand when its radius is set
        FACTORIES.put(
                "gregtech.common.tileentities.machines.multi.MTEWormholeGenerator",
                (mte, trigger) -> mte.getClass().getMethod("updateRenderRadius", double.class)
                        .invoke(mte, 2.999 / Math.sqrt(3)));

        FACTORIES.put(
                "goodgenerator.blocks.tileEntity.AntimatterForge",
                (mte, trigger) -> mte.getClass().getMethod("createAntimatterRender").invoke(mte));

        // the machine needs a running recipe to create it, so place the render block like it does
        FACTORIES.put("tectech.thing.metaTileEntity.multi.MTEEyeOfHarmony", (mte, trigger) -> {
            Block renderBlock = (Block) Class.forName("tectech.thing.casing.TTCasingsContainer")
                    .getField("eyeOfHarmonyRenderBlock").get(null);
            placeBehind(mte, 16, renderBlock);
        });
    }

    private GTCustomRenders() {}

    public static boolean hasCustomRender(@Nullable Object mte) {
        return mte != null && findFactory(mte.getClass()) != null;
    }

    /**
     * Creates the custom render of the given (already built) multiblock in its world.
     *
     * @return whether the render was created
     */
    public static boolean createRender(@NotNull IMetaTileEntity mte, @NotNull ItemStack trigger) {
        RenderFactory factory = findFactory(mte.getClass());
        if (factory == null) return false;
        try {
            factory.create(mte, trigger);
            return true;
        } catch (Throwable t) {
            BlockRenderer6343.error("Failed to create the custom render of " + mte.getClass().getName());
            t.printStackTrace();
            return false;
        }
    }

    private static @Nullable RenderFactory findFactory(Class<?> clazz) {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            RenderFactory factory = FACTORIES.get(c.getName());
            if (factory != null) return factory;
        }
        return null;
    }

    private static void placeBehind(IMetaTileEntity mte, int distance, Block block) throws Exception {
        IGregTechTileEntity base = mte.getBaseMetaTileEntity();
        World world = base.getWorld();
        ExtendedFacing facing = (ExtendedFacing) findMethod(mte.getClass(), "getExtendedFacing").invoke(mte);
        ForgeDirection back = facing.getRelativeBackInWorld();
        int x = base.getXCoord() + distance * back.offsetX;
        int y = base.getYCoord() + distance * back.offsetY;
        int z = base.getZCoord() + distance * back.offsetZ;
        world.setBlockToAir(x, y, z);
        world.setBlock(x, y, z, block);
    }

    private static Object getField(Object target, String name) throws Exception {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }

    private static Object invoke(Object target, String name) throws Exception {
        return findMethod(target.getClass(), name).invoke(target);
    }

    private static Method findMethod(Class<?> clazz, String name) throws NoSuchMethodException {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            try {
                Method method = c.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {}
        }
        throw new NoSuchMethodException(name);
    }
}

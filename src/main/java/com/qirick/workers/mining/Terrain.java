package com.qirick.workers.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The three questions the whole behaviour is built on, and nothing else.
 *
 * <p>None of them asks what KIND of place this is. There is no test for a cave, a ravine or
 * water anywhere in this mod - a cave is simply a region where {@link #walkable} happens to
 * be true across a lot of cells, and a ravine is one where it is true and the drop beside it
 * is deep. The same three predicates decide every step in solid rock and in open air alike.
 */
public final class Terrain {

    /** How deep a drop has to be before opening onto it counts as dangerous. */
    private static final int VOID_DEPTH = 3;

    /** The block a mob leaves behind to say where the way down is. */
    public static final net.minecraft.world.level.block.Block MARKER = Blocks.TORCH;

    private Terrain() {
    }

    /** Room to stand: two empty cells here, on something solid. */
    public static boolean walkable(LevelReader level, BlockPos cell) {
        return isSolid(level, cell.below()) && isEmpty(level, cell) && isEmpty(level, cell.above());
    }

    /** A step from one cell to a neighbour, allowed while the height difference is at most one. */
    public static boolean stepOk(BlockPos from, BlockPos to) {
        return Math.abs(from.getY() - to.getY()) <= 1;
    }

    /** Nothing between this block and the sky. */
    public static boolean skyExposed(LevelReader level, BlockPos pos) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) <= pos.getY() + 1;
    }

    /**
     * Taking this block out would open onto liquid, onto a long drop, or let loose ground
     * down on top of whoever took it.
     */
    public static boolean hazardous(LevelReader level, BlockPos pos) {
        if (level.getBlockState(pos.above()).getBlock() instanceof FallingBlock) {
            return true;
        }
        for (Direction face : Direction.values()) {
            BlockPos side = pos.relative(face);
            if (!level.getFluidState(side).isEmpty()) {
                return true;
            }
        }
        for (int drop = 1; drop <= VOID_DEPTH; drop++) {
            if (isSolid(level, pos.below(drop))) {
                return false;
            }
        }
        return true;
    }

    /** Whether the only thing wrong with this block is liquid, which can be walled off instead. */
    public static boolean hazardIsLiquid(LevelReader level, BlockPos pos) {
        for (Direction face : Direction.values()) {
            if (!level.getFluidState(pos.relative(face)).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** The face of this block that the liquid is behind. */
    public static BlockPos liquidFace(LevelReader level, BlockPos pos) {
        for (Direction face : Direction.values()) {
            BlockPos side = pos.relative(face);
            if (!level.getFluidState(side).isEmpty()) {
                return side;
            }
        }
        return null;
    }

    public static boolean isEmpty(LevelReader level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    public static boolean isSolid(LevelReader level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getFluidState().isEmpty() && state.isFaceSturdy(level, pos, Direction.UP);
    }

    public static boolean diggable(LevelReader level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && state.getFluidState().isEmpty()
                && state.getDestroySpeed(level, pos) >= 0.0F;
    }

    public static boolean isOre(LevelReader level, BlockPos pos) {
        return level.getBlockState(pos).is(net.minecraftforge.common.Tags.Blocks.ORES);
    }

    public static boolean isMarker(LevelReader level, BlockPos pos) {
        return level.getBlockState(pos).is(MARKER);
    }
}

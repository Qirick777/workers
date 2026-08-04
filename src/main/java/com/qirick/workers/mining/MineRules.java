package com.qirick.workers.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

/**
 * What the world is, in the fewest questions that will do.
 *
 * <p>Deliberately small. A previous attempt grew a rule for every hazard it met and then a
 * rescue for every rule, and the rescues killed more citizens than the hazards. Here a
 * hazard has exactly two answers: wall it off, or give up that direction.
 */
public final class MineRules {

    /** What the citizens are sent down for. Stone counts, which is what makes them self-sufficient. */
    private static final Set<Block> WANTED = Set.of(
            Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE,
            Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE,
            Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE,
            Blocks.STONE, Blocks.DEEPSLATE, Blocks.COBBLESTONE);

    /** The ores proper: what is worth reaching into a wall for. */
    private static final Set<Block> ORES = Set.of(
            Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE,
            Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE,
            Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE);

    /** What gets laid to close a hole. */
    public static final Block FILL = Blocks.COBBLESTONE;

    public static boolean isWanted(LevelReader level, BlockPos pos) {
        return WANTED.contains(level.getBlockState(pos).getBlock());
    }

    public static boolean isOre(LevelReader level, BlockPos pos) {
        return ORES.contains(level.getBlockState(pos).getBlock());
    }

    /** Empty enough to stand in. */
    public static boolean isOpen(LevelReader level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty();
    }

    /** Solid enough to stand on. */
    public static boolean isFloor(LevelReader level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && state.getFluidState().isEmpty()
                && state.isFaceSturdy(level, pos, Direction.UP);
    }

    /** A hole or a puddle: something that must be closed before a citizen stands beside it. */
    public static boolean needsSealing(LevelReader level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || !state.getFluidState().isEmpty();
    }

    /** Lava in the cell or against it. Never opened, never approached. */
    public static boolean isHot(LevelReader level, BlockPos pos) {
        if (level.getFluidState(pos).is(FluidTags.LAVA)) {
            return true;
        }
        for (Direction direction : Direction.values()) {
            if (level.getFluidState(pos.relative(direction)).is(FluidTags.LAVA)) {
                return true;
            }
        }
        return false;
    }

    /** Sand or gravel resting on this cell, waiting for it to be taken away. */
    public static boolean isLoose(LevelReader level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof FallingBlock;
    }

    /** Loose ground stacked over this cell, which is what buries a citizen that digs under it. */
    public static boolean hasLooseAbove(LevelReader level, BlockPos pos) {
        for (int up = 1; up <= 8; up++) {
            BlockState state = level.getBlockState(pos.above(up));
            if (state.getBlock() instanceof FallingBlock) {
                return true;
            }
            if (!state.isAir()) {
                return false;
            }
        }
        return false;
    }

    /** Breakable at all; bedrock and its kind are not. */
    public static boolean isBreakable(LevelReader level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && state.getDestroySpeed(level, pos) >= 0.0F;
    }

    private MineRules() {
    }
}

package com.qirick.workers.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * A cheapest-first sweep of the ground within arm's reach of where the mob is standing.
 *
 * <p>Local on purpose: a radius of a dozen cells, never the whole world. What it returns is
 * not a route to anywhere in particular but the cost of getting to everything nearby, which
 * is all the rules need - they pick the cell that best serves the goal and take one step
 * towards it.
 *
 * <p>The set of cells this reaches is also what the mob can be said to perceive. That falls
 * out of the costs rather than being coded: walking is cheap, so in open air the sweep runs
 * a long way for its budget, and cutting is dear, so in solid rock it barely leaves the
 * mob's feet. Nothing here knows what a cave is.
 */
public final class Local {

    /** What the sweep returns: how much each cell cost, and which cell it came from. */
    public record Field(Map<BlockPos, Integer> cost, Map<BlockPos, BlockPos> from,
                        BlockPos found) {
    }

    private Local() {
    }

    /**
     * @param dig    the price of cutting a block out of the way
     * @param sky    the surcharge for cutting one that has open sky above it
     * @param radius how far from the start the sweep may look
     * @param canPlace whether liquid faces can be walled off rather than avoided
     */
    public static Field sweep(LevelReader level, BlockPos start, int dig, int sky,
                              int radius, int budget, boolean canPlace,
                              java.util.function.Predicate<BlockPos> reached) {
        Map<BlockPos, Integer> cost = new HashMap<>();
        Map<BlockPos, BlockPos> from = new HashMap<>();
        PriorityQueue<BlockPos> queue =
                new PriorityQueue<>(Comparator.comparingInt(pos -> cost.getOrDefault(pos, Integer.MAX_VALUE)));

        cost.put(start, 0);
        queue.add(start);
        int visited = 0;

        while (!queue.isEmpty() && visited++ < budget) {
            BlockPos cell = queue.poll();
            int here = cost.getOrDefault(cell, Integer.MAX_VALUE);

            // Cheapest-first, so the first cell popped that answers the goal is the end of
            // the cheapest whole route to it. Choosing the cheapest single STEP instead is
            // what produced level tunnels and never a stair: going down costs three blocks
            // of cutting and going along costs two, so a step-at-a-time chooser goes along
            // for ever. Priced over the whole route, n cells along and then down is
            // 2Kn + 3K, which is dearer than 3K for every n, and the stair falls out
            // without anything in the price knowing which way is down.
            if (!cell.equals(start) && reached.test(cell) && Terrain.walkable(level, cell)) {
                return new Field(cost, from, cell);
            }

            for (Direction side : Direction.Plane.HORIZONTAL) {
                for (int dy = 1; dy >= -1; dy--) {
                    BlockPos to = cell.relative(side).above(dy);
                    if (Math.abs(to.getX() - start.getX()) > radius
                            || Math.abs(to.getZ() - start.getZ()) > radius
                            || Math.abs(to.getY() - start.getY()) > radius) {
                        continue;
                    }
                    if (!Terrain.stepOk(cell, to)) {
                        continue;
                    }
                    int price = price(level, to, dig, sky, canPlace);
                    if (price < 0) {
                        continue;
                    }
                    int next = here + price;
                    if (next < cost.getOrDefault(to, Integer.MAX_VALUE)) {
                        cost.put(to, next);
                        from.put(to, cell);
                        queue.add(to);
                    }
                }
            }
        }
        return new Field(cost, from, null);
    }

    /**
     * What one step into this cell costs, or -1 if the rules forbid it.
     *
     * <p>Walking is one. Cutting is the dig price, and the dig price again on top if the
     * block has sky over it - which is the whole of what keeps a mob from opening the
     * ground when a hillside would do. A cut that would leave nothing to stand on is
     * refused, and that alone is what stops a mine turning into a vertical drop.
     */
    private static int price(LevelReader level, BlockPos to, int dig, int sky, boolean canPlace) {
        if (Terrain.walkable(level, to)) {
            return 1;
        }
        if (!Terrain.isSolid(level, to.below())) {
            return -1;
        }
        int total = 0;
        for (BlockPos part : new BlockPos[]{to, to.above()}) {
            if (Terrain.isEmpty(level, part)) {
                continue;
            }
            if (!Terrain.diggable(level, part)) {
                return -1;
            }
            if (Terrain.hazardous(level, part)
                    && !(canPlace && Terrain.hazardIsLiquid(level, part))) {
                return -1;
            }
            total += dig;
            if (Terrain.skyExposed(level, part)) {
                total += sky;
            }
        }
        return total == 0 ? 1 : total;
    }

    /** The first step of the way to {@code target}, or null if it was never reached. */
    public static BlockPos firstStep(Field field, BlockPos start, BlockPos target) {
        BlockPos cursor = target;
        if (!field.from().containsKey(cursor) && !cursor.equals(start)) {
            return null;
        }
        while (cursor != null && !start.equals(field.from().get(cursor))) {
            cursor = field.from().get(cursor);
            if (cursor == null) {
                return null;
            }
        }
        return cursor;
    }
}

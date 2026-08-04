package com.qirick.workers.debug;

import com.qirick.workers.mining.MineShape;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Holds every broken block to account.
 *
 * <p>This exists because its absence is what let a previous attempt run for hours,
 * scratching pits across a meadow, and report success at four sites out of five. That
 * attempt counted things - blocks broken, clusters, depths - and never once asked the only
 * question that mattered: <b>was this block allowed to be broken?</b>
 *
 * <p>A block is allowed if it is a cell of the planned mine, or an ore touching one. Every
 * other broken block is a hole the citizen had no business making, and one is a failure.
 * The count of holes reaching daylight is taken as it is, not clustered - clustering is
 * exactly how a field of pits passed for a single staircase last time.
 */
public final class MineAudit {

    public record Report(int planned, int ore, int sealed, int stray, int daylight,
                         List<String> strayExamples) {

        public boolean clean() {
            return this.stray == 0 && this.daylight <= 1;
        }
    }

    /**
     * @param home    the return point the mine was planned from
     * @param depth   the depth it was sent to
     * @param ids     the entity ids of the crew, so each one's tunnel can be re-derived
     * @param length  how long a tunnel was allowed to be
     */
    public static Report audit(ServerLevel level, BlockPos home, int depth, List<Integer> ids, int length) {
        // Re-derive what was permitted from the plan itself, rather than trusting the log's
        // own account of why it broke something.
        Set<BlockPos> permitted = new HashSet<>();
        for (int id : ids) {
            MineShape shape = new MineShape(home, depth, id);
            List<BlockPos> stair = shape.stair(level);
            BlockPos foot = stair.isEmpty() ? home : stair.get(stair.size() - 1);
            permitted.addAll(shape.permitted(level, foot, length));
        }

        int planned = 0;
        int ore = 0;
        int sealed = 0;
        int stray = 0;
        int daylight = 0;
        List<String> examples = new ArrayList<>();

        for (MineLog.Entry entry : MineLog.entries()) {
            switch (entry.act()) {
                case SEAL -> sealed++;
                case DIG_PLAN -> {
                    if (permitted.contains(entry.pos())) {
                        planned++;
                    } else {
                        stray++;
                        if (examples.size() < 8) {
                            examples.add(entry.citizen() + " broke " + entry.pos().toShortString()
                                    + " claiming plan: " + entry.detail());
                        }
                    }
                }
                case DIG_ORE -> {
                    if (touches(permitted, entry.pos())) {
                        ore++;
                    } else {
                        stray++;
                        if (examples.size() < 8) {
                            examples.add(entry.citizen() + " broke " + entry.pos().toShortString()
                                    + " claiming ore, but it touches no planned cell");
                        }
                    }
                }
                default -> {
                }
            }
        }

        // Holes that reach the sky, counted one by one.
        Set<BlockPos> lit = new HashSet<>();
        for (MineLog.Entry entry : MineLog.entries()) {
            if ((entry.act() == MineLog.Act.DIG_PLAN || entry.act() == MineLog.Act.DIG_ORE)
                    && level.getBrightness(LightLayer.SKY, entry.pos()) > 0
                    && level.getBlockState(entry.pos()).isAir()) {
                lit.add(entry.pos());
            }
        }
        daylight = lit.size();

        return new Report(planned, ore, sealed, stray, daylight, examples);
    }

    private static boolean touches(Set<BlockPos> permitted, BlockPos pos) {
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
            if (permitted.contains(pos.relative(direction))) {
                return true;
            }
        }
        return false;
    }

    /**
     * A picture of what was actually cut, as text.
     *
     * <p>Looking at the shape is the step that was missing before. Numbers said the mine
     * was fine; a picture would have shown a meadow full of dimples in a second.
     */
    public static List<String> section(ServerLevel level, BlockPos home, int fromY, int toY, int radius) {
        List<String> lines = new ArrayList<>();
        for (int y = fromY; y >= toY; y--) {
            StringBuilder row = new StringBuilder(String.format("y=%4d ", y));
            for (int dx = -radius; dx <= radius; dx++) {
                BlockPos pos = new BlockPos(home.getX() + dx, y, home.getZ());
                if (level.getBlockState(pos).isAir()) {
                    row.append(level.getBrightness(LightLayer.SKY, pos) > 0 ? '"' : '.');
                } else {
                    row.append('#');
                }
            }
            lines.add(row.toString());
        }
        return lines;
    }

    private MineAudit() {
    }
}

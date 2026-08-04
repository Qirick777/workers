package com.qirick.workers.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The shape of the mine, decided before a single block is touched.
 *
 * <p>Pure arithmetic: no world, no entity, no state. Given the return point and which
 * citizen is asking, it names every cell that citizen will stand in, in order. The terrain
 * never changes the shape - it only decides how far along it the citizen gets.
 *
 * <p>Having a shape at all is the point. A mine with no intended shape cannot be checked
 * against anything, which is how a previous attempt spent hours scratching pits in a meadow
 * and reported success. Every block broken must be answerable to this class.
 *
 * <p>Two parts:
 * <ul>
 *   <li><b>The stair</b> - a spiral down a two-by-two footprint, one block down per step, so
 *       nothing is ever a fall and no ladders (and therefore no wood) are needed. Every
 *       citizen computes it from the return point alone, so the crew shares one stair
 *       without exchanging a word: the first to arrive cuts it, the rest walk down it.</li>
 *   <li><b>The drift</b> - a level tunnel from the foot of the stair, one cell wide, running
 *       in a direction the citizen takes from its own id. No two of the first four citizens
 *       take the same one, and again nobody is told anything.</li>
 * </ul>
 */
public final class MineShape {

    /** The stair's footprint: two by two, four cells to a turn. */
    private static final int[][] SPIRAL = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};

    /** Head height. Every cell of the mine is the standing cell and the one above it. */
    public static final int BODY = 2;

    /** A hard cap, so a shape is always finite however strange the return point. */
    private static final int MAX_STAIR = 320;

    private final BlockPos home;
    private final int targetY;
    private final Direction driftFacing;

    public MineShape(BlockPos home, int targetY, int citizenId) {
        this.home = home.immutable();
        this.targetY = targetY;
        this.driftFacing = Direction.from2DDataValue(Math.floorMod(citizenId, 4));
    }

    public Direction driftFacing() {
        return this.driftFacing;
    }

    /**
     * Where the stair breaks ground: one cell from the return point, and always the same one.
     *
     * <p>The return point is a player's feet, which is a cell of AIR standing on the ground -
     * or worse, a cell of air with nothing under it at all if the order was given from a
     * ledge. Taking it literally had citizens laying a floor in mid-air and then walling in
     * the sky around it, a cobblestone cross hanging over their heads which they jumped at.
     * The mouth is snapped down to whatever the ground actually is.
     */
    public BlockPos mouth(net.minecraft.world.level.LevelReader level) {
        BlockPos column = this.home.offset(1, 0, 0);
        for (int drop = 0; drop <= 32; drop++) {
            BlockPos cell = column.below(drop);
            if (MineRules.isFloor(level, cell.below()) && MineRules.isOpen(level, cell)) {
                return cell;
            }
        }
        return column;
    }

    /**
     * The cells the citizen stands in on the way down, top first.
     *
     * <p>Step k is the k-th cell of the spiral, k blocks below the mouth. Consecutive cells
     * are horizontal neighbours one block apart in height, which is a step, never a drop.
     */
    public List<BlockPos> stair(net.minecraft.world.level.LevelReader level) {
        BlockPos mouth = this.mouth(level);
        List<BlockPos> cells = new ArrayList<>();
        for (int k = 0; k < MAX_STAIR; k++) {
            int[] corner = SPIRAL[k % SPIRAL.length];
            BlockPos cell = new BlockPos(mouth.getX() + corner[0], mouth.getY() - k, mouth.getZ() + corner[1]);
            cells.add(cell);
            if (cell.getY() <= this.targetY) {
                break;
            }
        }
        return cells;
    }

    /** The cells of this citizen's own tunnel, from the foot of the stair outward. */
    public List<BlockPos> drift(BlockPos foot, int length) {
        List<BlockPos> cells = new ArrayList<>(length);
        BlockPos cursor = foot;
        for (int i = 0; i < length; i++) {
            cursor = cursor.relative(this.driftFacing);
            cells.add(cursor);
        }
        return cells;
    }

    /**
     * Every cell the whole mine is allowed to open, stair and drift together.
     *
     * <p>This is the permit list the audit checks broken blocks against. Anything broken
     * that is not in here and not an ore touching it is a hole the citizen had no business
     * making.
     */
    public Set<BlockPos> permitted(net.minecraft.world.level.LevelReader level,
                                   BlockPos foot, int driftLength) {
        Set<BlockPos> cells = new LinkedHashSet<>();
        for (BlockPos stand : this.stair(level)) {
            addBody(cells, stand);
        }
        if (foot != null) {
            for (BlockPos stand : this.drift(foot, driftLength)) {
                addBody(cells, stand);
            }
        }
        return cells;
    }

    private static void addBody(Set<BlockPos> cells, BlockPos stand) {
        for (int up = 0; up < BODY; up++) {
            cells.add(stand.above(up));
        }
    }

    /** The cells a citizen standing here needs empty: its feet and its head. */
    public static List<BlockPos> body(BlockPos stand) {
        List<BlockPos> cells = new ArrayList<>(BODY);
        for (int up = 0; up < BODY; up++) {
            cells.add(stand.above(up));
        }
        return cells;
    }
}

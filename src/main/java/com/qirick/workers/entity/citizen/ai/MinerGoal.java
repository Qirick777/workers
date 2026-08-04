package com.qirick.workers.entity.citizen.ai;

import com.qirick.workers.debug.MineLog;
import com.qirick.workers.entity.citizen.CitizenEntity;
import com.qirick.workers.mining.Local;
import com.qirick.workers.mining.Terrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * One loop. A goal is chosen, a step is priced, a step is taken.
 *
 * <p>There are no modes here - no digging mode, no cave mode, no going-home mode. Going
 * home is a goal like any other and the same step rules carry it out. There is no test for
 * what kind of place the mob is in either: solid rock, a cavern, a gorge and a flooded
 * passage are all handled by the same prices, because the difference between them is
 * already expressed in what walking and cutting cost there.
 *
 * <p>Nothing is shared. The mob reads the blocks around itself and its own trail, and that
 * is the whole of what it knows. Where several mobs converge on one shaft it is because a
 * cut passage is cheap to walk and fresh rock is dear to cut, not because any of them was
 * told about the others.
 */
public class MinerGoal extends Goal {

    /** The price of cutting a block; walking a clear cell costs one. */
    private static final int K = 8;

    /** Added again for cutting a block with open sky above it. */
    private static final int S = 64;

    /**
     * How long a trail may get before the mob turns for home.
     *
     * <p>This one number sets the depth as well as the range, because the rules have no
     * depth of their own - rule 4 only says "lower". With the safety factor it turns the
     * mob round after about twenty-six net steps, which from a surface at sixty-four is
     * iron and coal country and no deeper.
     */
    private static final int BUDGET = 40;

    /** Margin on the way home, so the trail is never walked to the last step. */
    private static final double SAFETY = 1.5D;

    private static final int RADIUS = 12;
    private static final int SWEEP = 1200;

    private static final int BREAK_TICKS = 10;
    private static final int STEP_TIMEOUT = 60;

    private final CitizenEntity citizen;

    /** Every cell stood in, oldest first. The only memory there is. */
    private final List<BlockPos> stack = new ArrayList<>();

    @Nullable
    private BlockPos stepping;
    private int stepTicks;

    @Nullable
    private BlockPos breaking;
    private int breakTicks;

    /** The way the last step went, used only when the sweep finds nothing at all. */
    @Nullable
    private Direction inertia;

    private String said = "";

    public MinerGoal(CitizenEntity citizen) {
        this.citizen = citizen;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return this.citizen.isMining();
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.stack.clear();
        this.stepping = null;
        this.breaking = null;
        this.inertia = null;
    }

    @Override
    public void stop() {
        this.citizen.getNavigation().stop();
    }

    public String describe() {
        return "stack=" + this.stack.size() + " " + this.said;
    }

    @Override
    public void tick() {
        if (!(this.citizen.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos here = this.citizen.blockPosition();

        // Finishing a cut takes precedence over choosing again; nothing else is remembered
        // between ticks.
        if (this.breaking != null) {
            this.cut(level, this.breaking);
            return;
        }
        if (this.stepping != null) {
            this.walk(level, here);
            return;
        }

        boolean goingHome = this.overBudget();

        // Rule 12: while the goal is home the trail is walked backwards, and only a blocked
        // cell sends it back through the ordinary step rules.
        if (goingHome) {
            this.said = "home";
            BlockPos back = this.stack.isEmpty() ? null : this.stack.get(this.stack.size() - 1);
            if (back == null) {
                this.citizen.setMining(false);
                return;
            }
            if (here.equals(back)) {
                this.stack.remove(this.stack.size() - 1);
                return;
            }
            if (Terrain.walkable(level, back) && Terrain.stepOk(here, back)) {
                this.begin(back);
                return;
            }
        }

        java.util.function.Predicate<BlockPos> reached = this.goal(level, here, goingHome);
        Local.Field field = Local.sweep(level, here, K, S, RADIUS, SWEEP, true, reached);
        if (field.found() == null) {
            this.drift(level, here);
            return;
        }
        BlockPos step = Local.firstStep(field, here, field.found());
        if (step == null) {
            this.drift(level, here);
            return;
        }
        this.begin(step);
    }

    /**
     * Rules 1 to 4: the first that applies says what counts as having arrived. Nothing here
     * scores or steers; it only states the condition, and the sweep finds the cheapest way
     * to meet it.
     */
    private java.util.function.Predicate<BlockPos> goal(ServerLevel level, BlockPos here,
                                                        boolean goingHome) {
        if (goingHome) {
            BlockPos back = this.stack.isEmpty() ? here : this.stack.get(this.stack.size() - 1);
            this.said = "home";
            return cell -> cell.equals(back);
        }
        BlockPos ore = this.sighted(level, here, true);
        if (ore != null) {
            this.said = "ore";
            BlockPos seen = ore;
            return cell -> cell.distSqr(seen) <= 2.5D;
        }
        BlockPos marker = this.sighted(level, here, false);
        if (marker != null) {
            this.said = "marker";
            BlockPos under = marker.below();
            return cell -> cell.equals(under);
        }
        this.said = "down";
        int from = here.getY();
        return cell -> cell.getY() < from;
    }

    /**
     * What the mob can see: blocks reached by a straight line through open air, stopping at
     * the first solid face.
     *
     * <p>Sight is deliberately not the sweep's reach. In stone a mob sees the faces of its
     * own corridor and no further, which is right - ore is visible where it is exposed and
     * nowhere else, and it is that narrowness that makes a sighted vein worth turning aside
     * for. In a cavern the same rays run until they hit something, and the mob sees far
     * without a line of code about caverns.
     */
    @Nullable
    private BlockPos sighted(ServerLevel level, BlockPos here, boolean ore) {
        BlockPos eye = here.above();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dy = -RADIUS; dy <= RADIUS; dy++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    BlockPos aim = eye.offset(dx, dy, dz);
                    boolean want = ore ? Terrain.isOre(level, aim) : Terrain.isMarker(level, aim);
                    if (want && this.clearLine(level, eye, aim)) {
                        return aim;
                    }
                }
            }
        }
        return null;
    }

    /** Whether nothing solid stands between here and there, the target itself excepted. */
    private boolean clearLine(ServerLevel level, BlockPos eye, BlockPos aim) {
        int steps = Math.max(Math.max(Math.abs(aim.getX() - eye.getX()),
                Math.abs(aim.getY() - eye.getY())), Math.abs(aim.getZ() - eye.getZ()));
        for (int i = 1; i < steps; i++) {
            BlockPos between = new BlockPos(
                    eye.getX() + (aim.getX() - eye.getX()) * i / steps,
                    eye.getY() + (aim.getY() - eye.getY()) * i / steps,
                    eye.getZ() + (aim.getZ() - eye.getZ()) * i / steps);
            if (!Terrain.isEmpty(level, between)) {
                return false;
            }
        }
        return true;
    }

    /** Rule 8's fallback, and the only use of the last direction that worked. */
    private void drift(ServerLevel level, BlockPos here) {
        this.said += "/drift";
        List<Direction> tries = new ArrayList<>();
        if (this.inertia != null) {
            tries.add(this.inertia);
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            tries.add(side);
        }
        for (Direction side : tries) {
            for (int dy = 1; dy >= -1; dy--) {
                BlockPos to = here.relative(side).above(dy);
                if (Terrain.stepOk(here, to) && Terrain.walkable(level, to)) {
                    this.begin(to);
                    return;
                }
            }
        }
    }

    /** Take one step: cut what stands in it, then move into it. */
    private void begin(BlockPos cell) {
        this.stepping = cell.immutable();
        this.stepTicks = 0;
    }

    private void walk(ServerLevel level, BlockPos here) {
        BlockPos cell = this.stepping;
        if (cell == null) {
            return;
        }

        // Rule 7's exception: liquid behind a face is walled off instead of opened, and
        // rule 9 forbids doing that to a passage that is already open.
        for (BlockPos part : new BlockPos[]{cell, cell.above()}) {
            if (Terrain.isEmpty(level, part)) {
                continue;
            }
            if (Terrain.hazardous(level, part) && Terrain.hazardIsLiquid(level, part)) {
                BlockPos face = Terrain.liquidFace(level, part);
                if (face != null && !Terrain.walkable(level, face)) {
                    this.place(level, face);
                    return;
                }
            }
            this.breaking = part.immutable();
            this.breakTicks = 0;
            return;
        }

        if (here.equals(cell)) {
            this.record(cell);
            this.inertia = Direction.getNearest(
                    cell.getX() - here.getX(), 0, cell.getZ() - here.getZ());
            this.stepping = null;
            return;
        }
        if (++this.stepTicks > STEP_TIMEOUT) {
            this.stepping = null;
            return;
        }
        this.citizen.getLookControl().setLookAt(
                cell.getX() + 0.5D, this.citizen.getEyeY(), cell.getZ() + 0.5D);
        this.citizen.getMoveControl().setWantedPosition(
                cell.getX() + 0.5D, cell.getY(), cell.getZ() + 0.5D, 1.0D);
        if (cell.getY() > here.getY()) {
            this.citizen.getJumpControl().jump();
        }
    }

    private void cut(ServerLevel level, BlockPos target) {
        if (Terrain.isEmpty(level, target)) {
            this.breaking = null;
            return;
        }
        this.citizen.getLookControl().setLookAt(
                target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D);
        if (++this.breakTicks < BREAK_TICKS) {
            return;
        }
        this.breakTicks = 0;
        BlockState was = level.getBlockState(target);
        if (level.destroyBlock(target, true, this.citizen)) {
            MineLog.record(level.getGameTime(), this.citizen.mineName(),
                    MineLog.Act.DIG_PLAN, target, was.getBlock().getDescriptionId());
        }
        this.breaking = null;
    }

    /** Rule 9. */
    private void place(ServerLevel level, BlockPos face) {
        if (Terrain.walkable(level, face)) {
            return;
        }
        if (!level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                new net.minecraft.world.phys.AABB(face)).isEmpty()) {
            return;
        }
        level.setBlockAndUpdate(face, Blocks.COBBLESTONE.defaultBlockState());
        MineLog.record(level.getGameTime(), this.citizen.mineName(), MineLog.Act.SEAL, face, "liquid");
    }

    /** Rules 10 and 11. */
    private void record(BlockPos cell) {
        int seen = this.stack.indexOf(cell);
        if (seen >= 0) {
            while (this.stack.size() > seen) {
                this.stack.remove(this.stack.size() - 1);
            }
        }
        this.stack.add(cell.immutable());
    }

    /** Rule 1, with the safety factor, and with water counted in breaths rather than steps. */
    private boolean overBudget() {
        double budget = BUDGET;
        if (this.citizen.isInWater() && this.citizen.getMaxAirSupply() > 0) {
            budget *= (double) this.citizen.getAirSupply() / this.citizen.getMaxAirSupply();
        }
        return this.stack.size() * SAFETY > budget;
    }
}

package com.qirick.workers.entity.citizen.ai;

import com.qirick.workers.debug.MineLog;
import com.qirick.workers.entity.citizen.CitizenEntity;
import com.qirick.workers.mining.MineRules;
import com.qirick.workers.mining.MineShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * A citizen cuts a mine, works it, and walks back out of it.
 *
 * <p>Three states, one shape, and one answer to trouble.
 *
 * <p>The shape comes from {@link MineShape} and is fixed before anything is touched, so
 * every block broken can be held to account afterwards. The citizen never steps outside it:
 * ore is taken by reaching into the wall from a cell of the plan, never by following a vein
 * out of the corridor. Everything that killed citizens in the previous attempt - falls off
 * ledges and towers, drowning, burial, suffocation by another's block - happened outside
 * the corridor, and there is now no outside to be in.
 *
 * <p>Trouble has two answers and no others: <b>wall it off</b>, or <b>abandon the
 * direction and go home</b>. There is no rescue planner, no escape search and no
 * pathfinding, because the way home is not a route to find: it is the list of cells already
 * walked, read backwards.
 *
 * <p>Nothing here consults or commands another citizen. The stair is agreed on by everyone
 * computing it from the same return point; the tunnels are told apart by each citizen's own
 * id; and two citizens meeting in a corridor is settled by the one going down waiting.
 */
public class MinerGoal extends Goal {

    /** Ticks of swinging per block. */
    private static final int BREAK_TICKS = 12;

    /** How long one block may resist before the citizen gives up on that direction. */
    private static final int BREAK_TIMEOUT = 200;

    /** How long one step may take before the citizen gives up and goes home. */
    private static final int STEP_TIMEOUT = 80;

    /** How far a tunnel runs before the shift is done. */
    private static final int DRIFT_LENGTH = 64;

    /** How many blocks may be laid to hold one stretch open before it is not worth it. */
    private static final int SEAL_BUDGET = 96;

    /** Close enough to the return point to be home. */
    private static final double HOME_RANGE_SQ = 6.0D;

    /** How far a citizen can work: arm's length, as a player's is. */
    private static final double REACH = 4.5D;

    private enum State {
        APPROACH,
        STAIR,
        DRIFT,
        RETURN
    }

    private final CitizenEntity citizen;

    private MineShape shape;
    private State state = State.STAIR;

    /** The cells still to be entered, in order. */
    private final List<BlockPos> ahead = new ArrayList<>();

    /** Every cell entered, oldest first. Read backwards, it is the way out. */
    private final List<BlockPos> trail = new ArrayList<>();

    /** The foot of the stair, once reached: where the tunnel starts. */
    @Nullable
    private BlockPos foot;

    @Nullable
    private BlockPos stepping;
    private int stepTicks;

    @Nullable
    private BlockPos breaking;
    private int breakTicks;
    private int breakTotal;

    /** An ore in the wall that has been spotted and not yet taken out. */
    @Nullable
    private BlockPos pendingOre;

    private int sealed;
    private String why = "";

    public MinerGoal(CitizenEntity citizen) {
        this.citizen = citizen;
        // MOVE and LOOK only. The float goal claims JUMP whenever the citizen is in water,
        // and a goal that also asks for it is simply never run.
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return this.citizen.isMining() && this.citizen.getReturnPoint() != null;
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
        BlockPos home = this.citizen.getReturnPoint();
        if (home == null) {
            return;
        }
        this.shape = new MineShape(home, this.citizen.getMineDepth(), this.citizen.getId());
        this.state = State.APPROACH;
        this.ahead.clear();
        this.trail.clear();
        this.foot = null;
        this.stepping = null;
        this.breaking = null;
        this.pendingOre = null;
        this.sealed = 0;
        this.why = "";
        this.note("shift begins, drift " + this.shape.driftFacing());
    }

    @Override
    public void stop() {
        this.citizen.getNavigation().stop();
        this.stepping = null;
        this.breaking = null;
    }

    /** Told to knock off: walk out the way we came in, digging nothing new. */
    public void sendHome(String reason) {
        if (this.state != State.RETURN) {
            this.why = reason;
            this.state = State.RETURN;
            this.ahead.clear();
            this.breaking = null;
            this.pendingOre = null;
            this.stepping = null;
            this.stepTicks = 0;
            this.note("going home: " + reason);
        }
    }

    public String describe() {
        return this.state + " ahead=" + this.ahead.size() + " trail=" + this.trail.size()
                + " sealed=" + this.sealed + (this.why.isEmpty() ? "" : " (" + this.why + ")");
    }

    @Override
    public void tick() {
        if (!(this.citizen.level() instanceof ServerLevel level) || this.shape == null) {
            return;
        }
        switch (this.state) {
            case APPROACH -> this.approach(level);
            case STAIR, DRIFT -> this.advance(level);
            case RETURN -> this.retreat(level);
        }
    }

    // ------------------------------------------------------------------
    // Going in
    // ------------------------------------------------------------------

    /**
     * Walk to the mouth before touching anything.
     *
     * <p>Nothing else here uses pathfinding, and this is why it must: until the citizen is
     * standing at the mine it has no corridor to walk along, only open country. Without
     * this the citizen worked the mouth from wherever it happened to be - laying blocks it
     * could not reach, into air it could not see - and the crew built a cobblestone cross
     * hanging over the meadow and jumped at it.
     */
    private void approach(ServerLevel level) {
        BlockPos mouth = this.shape.mouth(level);
        if (this.citizen.blockPosition().closerThan(mouth, REACH)) {
            this.citizen.getNavigation().stop();
            this.state = State.STAIR;
            this.ahead.addAll(this.shape.stair(level));
            this.note("at the mouth " + mouth.toShortString());
            return;
        }
        if (++this.stepTicks > STEP_TIMEOUT * 8) {
            this.sendHome("cannot reach the mouth");
            return;
        }
        if (this.citizen.getNavigation().isDone()) {
            this.citizen.getNavigation().moveTo(
                    mouth.getX() + 0.5D, mouth.getY(), mouth.getZ() + 0.5D, 1.0D);
        }
    }

    private void advance(ServerLevel level) {
        // An ore spotted in the wall is taken before moving on, from where the citizen
        // already stands. It never steps out of the corridor to reach one.
        if (this.pendingOre != null) {
            if (MineRules.isOre(level, this.pendingOre)) {
                this.dig(level, this.pendingOre, MineLog.Act.DIG_ORE, "wall ore");
                return;
            }
            this.pendingOre = null;
        }
        if (this.ahead.isEmpty()) {
            this.reachedEnd(level);
            return;
        }
        BlockPos next = this.ahead.get(0);

        if (!this.prepare(level, next)) {
            return;
        }
        this.walkTo(level, next, () -> {
            this.trail.add(next.immutable());
            this.ahead.remove(0);
            this.harvest(level, next);
        });
    }

    private void reachedEnd(ServerLevel level) {
        if (this.state == State.STAIR) {
            this.foot = this.trail.isEmpty() ? this.citizen.blockPosition() : this.trail.get(this.trail.size() - 1);
            this.state = State.DRIFT;
            this.ahead.addAll(this.shape.drift(this.foot, DRIFT_LENGTH));
            this.note("at depth " + this.foot.getY() + ", cutting " + this.shape.driftFacing());
            return;
        }
        this.sendHome("tunnel finished");
    }

    /**
     * Make the next cell fit to stand in: a floor under it, its body clear, and every hole
     * around it closed. Returns true when the citizen may step in.
     *
     * <p>Each of these is a way a citizen died in the previous attempt, and none of them
     * gets a rescue - only a block laid, or a direction given up.
     */
    private boolean prepare(ServerLevel level, BlockPos cell) {
        if (this.sealed > SEAL_BUDGET) {
            this.sendHome("too much open ground to hold");
            return false;
        }

        // Lava is never opened and never approached.
        for (BlockPos part : MineShape.body(cell)) {
            if (MineRules.isHot(level, part)) {
                this.sendHome("lava at " + part.toShortString());
                return false;
            }
        }

        // A floor first, because a step into nothing is a fall, and falls killed the most.
        BlockPos floor = cell.below();
        if (MineRules.isLoose(level, floor)) {
            // Sand underfoot goes away the moment something updates it. Replace it now,
            // while standing somewhere else, rather than discover it later.
            if (!this.dig(level, floor, MineLog.Act.DIG_PLAN, "loose floor")) {
                return false;
            }
            return false;
        }
        if (!MineRules.isFloor(level, floor)) {
            this.seal(level, floor, "floor");
            return false;
        }

        // Then the body of the cell.
        for (BlockPos part : MineShape.body(cell)) {
            if (MineRules.isOpen(level, part)) {
                continue;
            }
            if (MineRules.hasLooseAbove(level, part)) {
                this.sendHome("loose ground over " + part.toShortString());
                return false;
            }
            if (!MineRules.isBreakable(level, part)) {
                this.sendHome("unbreakable at " + part.toShortString());
                return false;
            }
            return this.dig(level, part, MineLog.Act.DIG_PLAN, "corridor");
        }

        // Then anything that would run in. Only fluids: air is not a leak, and treating it
        // as one is what had the crew bricking up the open sky around itself. What keeps a
        // citizen from falling out of the mine is that it never walks anywhere but the next
        // cell of its own corridor - not a wall around the corridor.
        for (BlockPos part : MineShape.body(cell)) {
            for (Direction direction : Direction.values()) {
                BlockPos side = part.relative(direction);
                if (this.isCorridor(side) || level.getFluidState(side).isEmpty()) {
                    continue;
                }
                this.seal(level, side, "fluid");
                return false;
            }
        }
        return true;
    }

    /** Whether this cell belongs to the corridor and so must be left open. */
    private boolean isCorridor(BlockPos cell) {
        for (BlockPos stand : this.trail) {
            if (this.inBody(stand, cell)) {
                return true;
            }
        }
        for (BlockPos stand : this.ahead) {
            if (this.inBody(stand, cell)) {
                return true;
            }
        }
        return false;
    }

    private boolean inBody(BlockPos stand, BlockPos cell) {
        return cell.getX() == stand.getX() && cell.getZ() == stand.getZ()
                && cell.getY() >= stand.getY() && cell.getY() < stand.getY() + MineShape.BODY;
    }

    /**
     * Ore in the walls of a cell the citizen is standing in, and nothing further.
     *
     * <p>Only blocks touching a planned cell, never blocks touching a hole the citizen has
     * just made in the wall. That cap is what keeps a vein from turning into a burrow, and
     * it is checkable afterwards without ambiguity.
     */
    private void harvest(ServerLevel level, BlockPos stand) {
        for (BlockPos part : MineShape.body(stand)) {
            for (Direction direction : Direction.values()) {
                if (direction == Direction.DOWN) {
                    continue; // The floor stays whole; there is nothing to stand on otherwise.
                }
                BlockPos ore = part.relative(direction);
                if (!MineRules.isOre(level, ore) || this.isCorridor(ore)) {
                    continue;
                }
                if (MineRules.isHot(level, ore) || MineRules.hasLooseAbove(level, ore)) {
                    continue; // Not worth what is behind it.
                }
                boolean wet = false;
                for (Direction face : Direction.values()) {
                    if (!level.getFluidState(ore.relative(face)).isEmpty()) {
                        wet = true;
                        break;
                    }
                }
                if (!wet) {
                    this.pendingOre = ore.immutable();
                    return;
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Coming out
    // ------------------------------------------------------------------

    private void retreat(ServerLevel level) {
        BlockPos home = this.citizen.getReturnPoint();
        if (home != null && this.citizen.blockPosition().distSqr(home) <= HOME_RANGE_SQ
                && this.citizen.onGround()) {
            this.note("home");
            this.citizen.setMining(false);
            return;
        }
        if (this.trail.isEmpty()) {
            // Nothing left to retrace. Standing still is a poor end but an honest one, and
            // it is not a death.
            this.note("trail spent, standing");
            this.citizen.setMining(false);
            return;
        }

        BlockPos back = this.trail.get(this.trail.size() - 1);
        // The corridor is the citizen's own work, so it may re-open it if something has
        // fallen in. This is the only digging the way home does.
        for (BlockPos part : MineShape.body(back)) {
            if (!MineRules.isOpen(level, part) && MineRules.isBreakable(level, part)) {
                this.dig(level, part, MineLog.Act.DIG_PLAN, "reopening");
                return;
            }
        }
        this.walkTo(level, back, () -> this.trail.remove(this.trail.size() - 1));
    }

    // ------------------------------------------------------------------
    // Doing
    // ------------------------------------------------------------------

    private boolean dig(ServerLevel level, BlockPos target, MineLog.Act act, String reason) {
        if (!this.citizen.blockPosition().closerThan(target, REACH)) {
            this.sendHome("out of reach of " + target.toShortString());
            return false;
        }
        if (!target.equals(this.breaking)) {
            this.breaking = target.immutable();
            this.breakTicks = 0;
            this.breakTotal = 0;
        }
        this.citizen.getLookControl().setLookAt(
                target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D);
        if (++this.breakTotal > BREAK_TIMEOUT) {
            this.sendHome("block will not break at " + target.toShortString());
            return false;
        }
        if (++this.breakTicks < BREAK_TICKS) {
            return false;
        }
        this.breakTicks = 0;
        BlockState was = level.getBlockState(target);
        if (level.destroyBlock(target, true, this.citizen)) {
            MineLog.record(level.getGameTime(), this.citizen.mineName(), act, target,
                    was.getBlock().getDescriptionId() + " " + reason);
            this.breaking = null;
        }
        return false;
    }

    private void seal(ServerLevel level, BlockPos target, String reason) {
        if (!this.citizen.blockPosition().closerThan(target, REACH)) {
            this.sendHome("out of reach of " + target.toShortString());
            return;
        }
        // Never into a body: a block set where somebody stands is how one citizen
        // suffocated another in the previous attempt.
        if (!level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                new AABB(target)).isEmpty()) {
            return;
        }
        if (!level.getFluidState(target).isEmpty()) {
            level.setBlockAndUpdate(target, MineRules.FILL.defaultBlockState());
        } else if (level.getBlockState(target).isAir()) {
            level.setBlockAndUpdate(target, MineRules.FILL.defaultBlockState());
        } else {
            return;
        }
        this.sealed++;
        MineLog.record(level.getGameTime(), this.citizen.mineName(), MineLog.Act.SEAL, target, reason);
    }

    /**
     * One cell of walking, and nothing more.
     *
     * <p>No pathfinding. The destination is always the next cell of a corridor this citizen
     * cut, so there is nothing to search for - and asking the pathfinder for the cell next
     * door was itself the fault that froze every citizen at every site for a whole round of
     * the previous attempt.
     */
    private void walkTo(ServerLevel level, BlockPos cell, Runnable arrived) {
        if (!cell.equals(this.stepping)) {
            this.stepping = cell.immutable();
            this.stepTicks = 0;
        }
        if (this.citizen.blockPosition().equals(cell) && this.citizen.onGround()) {
            this.stepping = null;
            this.stepTicks = 0;
            arrived.run();
            return;
        }
        if (++this.stepTicks > STEP_TIMEOUT) {
            this.sendHome("stuck walking to " + cell.toShortString());
            return;
        }
        // Somebody in the way is waited for, not negotiated with. The one going down is the
        // one that waits, which is a decision each citizen makes on its own.
        if (!level.getEntitiesOfClass(CitizenEntity.class, new AABB(cell),
                other -> other != this.citizen).isEmpty()) {
            this.stepTicks = 0;
            return;
        }
        this.citizen.getLookControl().setLookAt(
                cell.getX() + 0.5D, this.citizen.getEyeY(), cell.getZ() + 0.5D);
        this.citizen.getMoveControl().setWantedPosition(
                cell.getX() + 0.5D, cell.getY(), cell.getZ() + 0.5D, 1.0D);
        if (cell.getY() > this.citizen.blockPosition().getY()) {
            this.citizen.getJumpControl().jump();
        }
    }

    private void note(String what) {
        if (this.citizen.level() instanceof ServerLevel level) {
            MineLog.record(level.getGameTime(), this.citizen.mineName(), MineLog.Act.NOTE,
                    this.citizen.blockPosition(), what);
        }
    }
}

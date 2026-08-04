package com.qirick.workers.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.qirick.workers.debug.MineAudit;
import com.qirick.workers.debug.MineLog;
import com.qirick.workers.entity.citizen.CitizenEntity;
import com.qirick.workers.mining.MineShape;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * The commands the mine is judged by.
 *
 * <p>Written to be looked at, not to be counted. {@code audit} is the one that matters: it
 * re-derives what the plan permitted and asks every broken block to justify itself, and
 * {@code section} draws the result so it can be seen rather than believed.
 */
public final class MineCommand {

    /** How far from the caller citizens are picked up. */
    private static final double CREW_RANGE = 24.0D;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mine").requires(source -> source.hasPermission(2))
                // /mine start [depth] - the caller's feet become the return point.
                .then(Commands.literal("start")
                        .executes(ctx -> start(ctx.getSource(), CitizenEntity.DEFAULT_DEPTH))
                        .then(Commands.argument("depth", IntegerArgumentType.integer(-60, 200))
                                .executes(ctx -> start(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "depth")))))
                // /mine stop - everyone walks out the way they came.
                .then(Commands.literal("stop").executes(ctx -> stop(ctx.getSource())))
                // /mine status - what each citizen is doing right now.
                .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
                // /mine plan - the shape, before anything is dug.
                .then(Commands.literal("plan").executes(ctx -> plan(ctx.getSource())))
                // /mine audit - was every broken block allowed?
                .then(Commands.literal("audit").executes(ctx -> audit(ctx.getSource())))
                // /mine section [radius] - a picture of the cut.
                .then(Commands.literal("section")
                        .executes(ctx -> section(ctx.getSource(), 12))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(2, 40))
                                .executes(ctx -> section(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "radius")))))
                // /mine clear - forget the log, so the next run is judged alone.
                .then(Commands.literal("clear").executes(ctx -> {
                    MineLog.clear();
                    ctx.getSource().sendSuccess(() -> Component.literal("log cleared"), false);
                    return 1;
                })));
    }

    private static List<CitizenEntity> crew(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        return level.getEntitiesOfClass(CitizenEntity.class,
                new AABB(BlockPos.containing(source.getPosition())).inflate(CREW_RANGE),
                CitizenEntity::isAlive);
    }

    private static int start(CommandSourceStack source, int depth) {
        BlockPos home = BlockPos.containing(source.getPosition());
        List<CitizenEntity> crew = crew(source);
        MineLog.clear();
        for (CitizenEntity citizen : crew) {
            citizen.setReturnPoint(home);
            citizen.setMineDepth(depth);
            citizen.setMining(true);
        }
        source.sendSuccess(() -> Component.literal(
                "sent " + crew.size() + " to depth " + depth + " from " + home.toShortString()), false);
        return crew.size();
    }

    private static int stop(CommandSourceStack source) {
        List<CitizenEntity> crew = crew(source);
        for (CitizenEntity citizen : crew) {
            citizen.sendHome("ordered");
        }
        source.sendSuccess(() -> Component.literal("recalled " + crew.size()), false);
        return crew.size();
    }

    private static int status(CommandSourceStack source) {
        List<CitizenEntity> crew = crew(source);
        for (CitizenEntity citizen : crew) {
            source.sendSuccess(() -> Component.literal(
                    citizen.mineName() + " " + citizen.blockPosition().toShortString()
                            + " " + citizen.mineStatus()), false);
        }
        return crew.size();
    }

    private static int plan(CommandSourceStack source) {
        List<CitizenEntity> crew = crew(source);
        if (crew.isEmpty()) {
            source.sendFailure(Component.literal("no citizens nearby"));
            return 0;
        }
        for (CitizenEntity citizen : crew) {
            BlockPos home = citizen.getReturnPoint() == null
                    ? BlockPos.containing(source.getPosition()) : citizen.getReturnPoint();
            MineShape shape = new MineShape(home, citizen.getMineDepth(), citizen.getId());
            List<BlockPos> stair = shape.stair();
            BlockPos foot = stair.isEmpty() ? home : stair.get(stair.size() - 1);
            source.sendSuccess(() -> Component.literal(
                    citizen.mineName() + " mouth=" + shape.mouth().toShortString()
                            + " stair=" + stair.size() + " steps to " + foot.toShortString()
                            + " drift=" + shape.driftFacing()), false);
        }
        return crew.size();
    }

    private static int audit(CommandSourceStack source) {
        List<CitizenEntity> crew = crew(source);
        if (crew.isEmpty()) {
            source.sendFailure(Component.literal("no citizens nearby"));
            return 0;
        }
        BlockPos home = crew.get(0).getReturnPoint() == null
                ? BlockPos.containing(source.getPosition()) : crew.get(0).getReturnPoint();
        List<Integer> ids = new ArrayList<>();
        crew.forEach(citizen -> ids.add(citizen.getId()));

        MineAudit.Report report = MineAudit.audit(source.getLevel(), home,
                crew.get(0).getMineDepth(), ids, 64);

        source.sendSuccess(() -> Component.literal(
                "planned=" + report.planned() + " ore=" + report.ore() + " sealed=" + report.sealed()
                        + " STRAY=" + report.stray() + " daylight-holes=" + report.daylight()), false);
        for (String example : report.strayExamples()) {
            source.sendSuccess(() -> Component.literal("  " + example), false);
        }
        source.sendSuccess(() -> Component.literal(
                report.clean() ? "CLEAN" : "FAILED - a block was broken that the plan did not permit"), false);
        return report.clean() ? 1 : 0;
    }

    private static int section(CommandSourceStack source, int radius) {
        List<CitizenEntity> crew = crew(source);
        BlockPos home = crew.isEmpty() || crew.get(0).getReturnPoint() == null
                ? BlockPos.containing(source.getPosition()) : crew.get(0).getReturnPoint();
        int depth = crew.isEmpty() ? CitizenEntity.DEFAULT_DEPTH : crew.get(0).getMineDepth();
        for (String line : MineAudit.section(source.getLevel(), home, home.getY() + 2, depth - 2, radius)) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        source.sendSuccess(() -> Component.literal("# solid   . cut   \" cut and open to sky"), false);
        return 1;
    }

    private MineCommand() {
    }
}

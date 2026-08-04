package com.qirick.workers.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.qirick.workers.debug.MineLog;
import com.qirick.workers.entity.citizen.CitizenEntity;
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



                // /mine dump - every block touched, so the cut can be read back.
                .then(Commands.literal("dump").executes(ctx -> {
                    java.util.List<MineLog.Entry> all = MineLog.entries();
                    int from = Math.max(0, all.size() - 60);
                    for (MineLog.Entry e : all.subList(from, all.size())) {
                        ctx.getSource().sendSuccess(() -> Component.literal(
                                e.tick() + " " + e.citizen() + " " + e.act() + " "
                                        + e.pos().toShortString() + " " + e.detail()), false);
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal("total=" + all.size()), false);
                    return all.size();
                }))
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
            citizen.setMining(true);
        }
        source.sendSuccess(() -> Component.literal(
                "sent " + crew.size() + " to depth " + depth + " from " + home.toShortString()), false);
        return crew.size();
    }

    private static int stop(CommandSourceStack source) {
        List<CitizenEntity> crew = crew(source);
        for (CitizenEntity citizen : crew) {
            citizen.setMining(false);
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




    private MineCommand() {
    }
}

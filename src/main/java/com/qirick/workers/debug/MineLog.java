package com.qirick.workers.debug;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * What was done, and on whose authority.
 *
 * <p>The second field is the point. Every block broken carries the reason it was allowed to
 * be broken, and the audit checks that reason against the plan rather than taking it on
 * trust. A previous attempt logged far more than this and still failed to notice its
 * citizens digging pits in a meadow, because nothing ever asked a broken block to justify
 * itself.
 */
public final class MineLog {

    public enum Act {
        /** A cell the plan said to open. */
        DIG_PLAN,
        /** An ore in the wall of a planned cell. */
        DIG_ORE,
        /** A block laid to close a hole or make a floor. */
        SEAL,
        /** Anything worth reading back afterwards. */
        NOTE
    }

    public record Entry(long tick, String citizen, Act act, BlockPos pos, String detail) {
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static boolean recording = true;

    public static void record(long tick, String citizen, Act act, BlockPos pos, String detail) {
        if (recording) {
            ENTRIES.add(new Entry(tick, citizen, act, pos.immutable(), detail));
        }
    }

    public static List<Entry> entries() {
        return List.copyOf(ENTRIES);
    }

    public static void clear() {
        ENTRIES.clear();
    }

    public static void setRecording(boolean on) {
        recording = on;
    }

    private MineLog() {
    }
}

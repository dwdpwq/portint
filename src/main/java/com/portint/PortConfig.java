package com.portint;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side configuration for the Portint interface.
 * File: config/portint-server.toml
 */
public class PortConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ── Transfer rates ─────────────────────────────────────────────

    /** Per-tick item transfer cap when no Long Card is installed (items/tick). Default: 1024 */
    public static final ModConfigSpec.IntValue BASE_ITEM_RATE = BUILDER
            .comment("Per-tick item transfer cap when no Long Card is installed (items/tick).",
                    "Range: [1, 2147483647]")
            .defineInRange("baseItemRate", 1024, 1, Integer.MAX_VALUE);

    /** Per-tick item transfer cap per Long Card (items/tick), multiplied by the number of cards. Default: 16384 */
    public static final ModConfigSpec.IntValue LONG_CARD_ITEM_RATE = BUILDER
            .comment("Per-tick item transfer cap per Long Card (items/tick).",
                    "The effective rate is this value multiplied by the number of Long Cards installed.",
                    "Range: [1, 2147483647]")
            .defineInRange("longCardItemRate", 16384, 1, Integer.MAX_VALUE);

    /** Per-tick fluid/chemical transfer cap (mB/tick). -1 = unlimited. Default: -1 */
    public static final ModConfigSpec.IntValue FLUID_RATE = BUILDER
            .comment("Per-tick fluid/chemical transfer cap (mB/tick).",
                    "-1 = unlimited (default). Other values must be natural numbers >= 1.",
                    "Range: [-1, 2147483647]")
            .defineInRange("fluidRate", -1, -1, Integer.MAX_VALUE);

    // ── Wireless link (no upgrade cards) ───────────────────────────

    /** Wireless range (block distance) for bound blocks when no Range/Dimension card is installed.
     *  -1 = infinite; otherwise natural number >= 16; values below 16 fall back to 32. Default: 32 */
    public static final ModConfigSpec.IntValue WIRELESS_RANGE_NO_UPGRADE = BUILDER
            .comment("Wireless transfer range (block distance) for bound blocks when neither a Range card",
                    "nor a Dimension card is installed.",
                    "-1 = infinite. Otherwise a natural number >= 16 is required;",
                    "values below 16 are invalid and fall back to the default (32).",
                    "Default: 32")
            .defineInRange("wirelessRangeNoUpgrade", 32, -1, Integer.MAX_VALUE);

    // ── Chunk loading ──────────────────────────────────────────────

    /** Whether the Dimension card force-loads chunks of bound blocks. Default: true */
    public static final ModConfigSpec.BooleanValue CHUNK_LOADING_ENABLED = BUILDER
            .comment("Whether installing a Dimension card force-loads the chunks of bound blocks.",
                    "Default: true")
            .define("chunkLoadingEnabled", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private PortConfig() {}

    /** Effective wireless range: -1 = infinite; values below 16 fall back to the default (32). */
    public static int getEffectiveWirelessRange() {
        int v = WIRELESS_RANGE_NO_UPGRADE.get();
        if (v == -1) return -1;
        if (v < 16) return 32;
        return v;
    }

    /** Effective fluid/chemical budget per tick (mB): -1 (or any negative) = unlimited. */
    public static long getFluidBudget() {
        int v = FLUID_RATE.get();
        return v < 0 ? Integer.MAX_VALUE : v;
    }
}

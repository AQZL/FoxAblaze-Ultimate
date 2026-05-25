package com.foxablazeultimate.world;

import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameRules.BooleanValue;
import net.minecraft.world.level.GameRules.Category;

public final class FoxAblazeGameRules {

    public static GameRules.Key<BooleanValue> RAPHAEL_TRULY_UNIQUE;

    public static GameRules.Key<BooleanValue> BEELZEBUB_TRULY_UNIQUE;

    public static GameRules.Key<BooleanValue> BEELZEBUB_DISABLE_CAPTURE;

    public static GameRules.Key<BooleanValue> PROTECT_ULTIMATE_ON_RESET;

    private FoxAblazeGameRules() {}

    public static void init() {
        RAPHAEL_TRULY_UNIQUE = GameRules.register(
                "raphaelTrulyUnique", Category.PLAYER, BooleanValue.create(true));
        BEELZEBUB_TRULY_UNIQUE = GameRules.register(
                "beelzebubTrulyUnique", Category.PLAYER, BooleanValue.create(true));
        BEELZEBUB_DISABLE_CAPTURE = GameRules.register(
                "beelzebubDisableCapture", Category.PLAYER, BooleanValue.create(false));
        PROTECT_ULTIMATE_ON_RESET = GameRules.register(
                "protectUltimateOnReset", Category.PLAYER, BooleanValue.create(false));
    }
}

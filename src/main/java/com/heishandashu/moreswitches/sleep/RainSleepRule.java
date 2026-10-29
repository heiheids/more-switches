package com.heishandashu.moreswitches.sleep;

import com.heishandashu.moreswitches.config.ModSettings;
import com.heishandashu.moreswitches.config.SwitchIds;
import net.minecraft.world.level.Level;

/**
 * Decides whether rain makes a bed usable in daylight.
 *
 * <p>Vanilla only lets a player sleep when the sky is dark, so a rainy midday cannot be slept
 * through even though it looks like night. With the switch on, rain counts as "dark enough", the
 * bed can be used, and vanilla then advances the clock to its own wake-up marker — the same skip
 * a night's sleep performs.</p>
 *
 * <p>The decision itself is a pure function over three booleans, so it is testable without a
 * running game; {@link #allowsSleep(Level)} is the one-line adapter that reads those booleans off
 * the world. The mixin calls the adapter and nothing else.</p>
 */
public final class RainSleepRule {

    private RainSleepRule() {
    }

    /**
     * Should a "sleep only when dark" rule be treated as satisfied right now?
     *
     * <p>All three conditions matter: the switch must be on, the dimension must have weather at
     * all (the Nether and the End do not, so they keep vanilla behaviour), and it must actually be
     * raining.</p>
     */
    public static boolean allowsSleep(Level level) {
        return allowsSleep(isActive(), level.isRaining(), level.canHaveWeather());
    }

    /**
     * The rule itself.
     *
     * @param switchOn         whether the player turned the switch on
     * @param raining          whether rain is falling
     * @param weatherDimension whether this dimension has weather at all
     */
    public static boolean allowsSleep(boolean switchOn, boolean raining, boolean weatherDimension) {
        return switchOn && weatherDimension && raining;
    }

    /** Whether the player turned the switch on. Separate so a refusal can be explained. */
    public static boolean isActive() {
        return ModSettings.get().isEnabled(SwitchIds.RAIN_SLEEP);
    }
}

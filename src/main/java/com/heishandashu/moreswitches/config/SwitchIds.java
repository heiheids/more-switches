package com.heishandashu.moreswitches.config;

import java.util.List;

/**
 * Canonical list of switches, in the order they appear in the settings screen.
 *
 * <p>Row order in the UI is exactly this order, so "add a feature" means: append here, add its
 * module class, add its language keys. Nothing else in the UI changes.</p>
 */
public final class SwitchIds {

    /** Freeze the game window's size. */
    public static final String WINDOW_LOCK = "window_lock";

    /** Force a three-second cooldown between firework rocket uses. */
    public static final String FIREWORK_COOLDOWN = "firework_cooldown";

    /** Allow sleeping during the day while it rains. */
    public static final String RAIN_SLEEP = "rain_sleep";

    /** Ignore the drop key, so a mis-press cannot throw an item away. */
    public static final String DROP_KEY_LOCK = "drop_key_lock";

    /** Ignore the full-screen key, so an accidental press cannot change the mode. */
    public static final String FULLSCREEN_KEY_LOCK = "fullscreen_key_lock";

    /** Hide status-effect particles without touching the effects themselves. */
    public static final String EFFECT_PARTICLES_OFF = "effect_particles_off";

    /** Draw light-level numbers for the block a held light source would light. */
    public static final String LIGHT_PREVIEW = "light_preview";

    /** Brighten the surroundings of a held light source, visually only. */
    public static final String MOVING_LIGHT = "moving_light";

    private static final List<SwitchDefinition> ALL = List.of(
            new SwitchDefinition(WINDOW_LOCK, false),
            new SwitchDefinition(FIREWORK_COOLDOWN, false),
            new SwitchDefinition(RAIN_SLEEP, false),
            new SwitchDefinition(DROP_KEY_LOCK, false),
            new SwitchDefinition(FULLSCREEN_KEY_LOCK, false),
            new SwitchDefinition(EFFECT_PARTICLES_OFF, false),
            new SwitchDefinition(LIGHT_PREVIEW, false),
            new SwitchDefinition(MOVING_LIGHT, false));

    private SwitchIds() {
    }

    public static List<SwitchDefinition> all() {
        return ALL;
    }
}

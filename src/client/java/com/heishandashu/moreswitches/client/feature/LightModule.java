package com.heishandashu.moreswitches.client.feature;

import com.heishandashu.moreswitches.MoreSwitchesMod;
import com.heishandashu.moreswitches.config.SwitchIds;

/**
 * The two light switches.
 *
 * <p>Both effects are read directly by their mixins from the settings file, so neither module owns
 * per-frame work. They exist because every switch must have a module: a switch without one is a
 * wiring error the settings screen would otherwise hide.</p>
 */
public final class LightModule implements ClientFeature {

    private final String switchId;

    private boolean active;

    public LightModule(String switchId) {
        this.switchId = switchId;
    }

    @Override
    public void register(String boundSwitchId) {
        this.active = enabled(boundSwitchId);
        MoreSwitchesMod.LOGGER.info("[{}] switch '{}' starts {}", MoreSwitchesMod.MOD_ID,
                this.switchId, this.active ? "on" : "off");
    }

    @Override
    public void onEnabledChanged(boolean enabled) {
        if (this.active == enabled) {
            return;
        }
        this.active = enabled;
        MoreSwitchesMod.LOGGER.info("[{}] switch '{}' -> {}",
                MoreSwitchesMod.MOD_ID, this.switchId, enabled);
    }
}

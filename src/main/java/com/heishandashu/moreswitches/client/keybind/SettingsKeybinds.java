package com.heishandashu.moreswitches.client.keybind;

import com.heishandashu.moreswitches.MoreSwitchesMod;
import com.heishandashu.moreswitches.client.ClientTickHook;
import com.heishandashu.moreswitches.client.ui.SettingsScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * The one key binding the mod owns.
 *
 * <p>Default is <b>J</b> rather than the T from the original request: T is vanilla chat, and a
 * binding there would mean the chat key silently stops working (the mod's screen would replace the
 * chat screen on the same key press). J is unbound in vanilla, so nothing is taken away.</p>
 *
 * <p>The binding is registered through {@code RegisterKeyMappingsEvent}, so it shows up under
 * Options → Controls → Key Binds instead of forcing the player to edit a config file.</p>
 */
public final class SettingsKeybinds {

    /**
     * Own category, so the binding is easy to find in the vanilla key-bindings list.
     *
     * <p>1.21.1 identifies a category by a translation key string; the newer {@code Category} type
     * does not exist yet.</p>
     */
    public static final String CATEGORY = "key.categories." + MoreSwitchesMod.MOD_ID;

    public static final KeyMapping OPEN_SETTINGS = new KeyMapping(
            "key." + MoreSwitchesMod.MOD_ID + ".open_settings",
            InputConstants.KEY_J,
            CATEGORY);

    private SettingsKeybinds() {
    }

    /** Called once from the client initialiser; the key needs no other setup on this platform. */
    public static void register() {
        ClientTickHook.onEndTick(SettingsKeybinds::onEndTick);
        MoreSwitchesMod.LOGGER.info("[{}] settings key bound by default to J", MoreSwitchesMod.MOD_ID);
    }

    private static void onEndTick(Minecraft minecraft) {
        while (OPEN_SETTINGS.consumeClick()) {
            Screen current = minecraft.screen;
            if (current instanceof SettingsScreen) {
                // The key toggles: pressing it again closes the screen it opened.
                minecraft.setScreen(null);
            } else if (current == null) {
                minecraft.setScreen(new SettingsScreen(null));
            }
            // Any other screen (chat, inventory, pause) keeps the key to itself so this binding can
            // never steal a key press from a screen the player is typing in.
        }
    }
}

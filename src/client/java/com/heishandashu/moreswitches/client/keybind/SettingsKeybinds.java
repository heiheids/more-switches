package com.heishandashu.moreswitches.client.keybind;

import com.heishandashu.moreswitches.MoreSwitchesMod;
import com.heishandashu.moreswitches.client.ui.SettingsScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

/**
 * The one key binding the mod owns.
 *
 * <p>Default is <b>J</b> rather than the T from the original request: T is vanilla chat, and a
 * binding there would mean the chat key silently stops working (the mod's screen would replace
 * the chat screen on the same key press). J is unbound in vanilla, so nothing is taken away.</p>
 *
 * <p>The key is registered through Fabric's helper, which adds it to Options → Controls → Key
 * Binds instead of forcing the player to edit a config file.</p>
 */
public final class SettingsKeybinds {

    /** Own category so the binding is easy to find in the vanilla key-bindings list. */
    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MoreSwitchesMod.MOD_ID, "main"));

    public static final KeyMapping OPEN_SETTINGS = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key." + MoreSwitchesMod.MOD_ID + ".open_settings",
            InputConstants.KEY_J,
            CATEGORY));

    private SettingsKeybinds() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(SettingsKeybinds::onEndTick);
    }

    private static void onEndTick(Minecraft minecraft) {
        while (OPEN_SETTINGS.consumeClick()) {
            Screen current = minecraft.gui.screen();
            if (current instanceof SettingsScreen) {
                // The key toggles: pressing it again closes the screen it opened.
                minecraft.gui.setScreen(null);
            } else if (current == null) {
                minecraft.gui.setScreen(new SettingsScreen(null));
            }
            // Any other screen (chat, inventory, pause) keeps the key to itself so this binding
            // can never steal a key press from a screen the player is typing in.
        }
    }
}

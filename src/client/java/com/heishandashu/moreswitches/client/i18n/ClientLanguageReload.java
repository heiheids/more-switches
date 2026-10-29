package com.heishandashu.moreswitches.client.i18n;

import com.heishandashu.moreswitches.i18n.ModLang;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Reads the mod's two language files whenever the client reloads its resources.
 *
 * <p>Doing it in a reload listener (instead of lazily on first screen open) means the text is
 * already there when the screen is drawn, and a resource pack that ships a translation is picked
 * up on {@code F3+T} like any other resource.</p>
 */
public final class ClientLanguageReload implements SimpleSynchronousResourceReloadListener {

    private static final Identifier LISTENER_ID =
            Identifier.fromNamespaceAndPath("moreswitches", "language_tables");

    private ClientLanguageReload() {
    }

    public static void register() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new ClientLanguageReload());
    }

    @Override
    public Identifier getFabricId() {
        return LISTENER_ID;
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        ModLang.reload(manager, Minecraft.getInstance().getLanguageManager().getSelected());
    }
}

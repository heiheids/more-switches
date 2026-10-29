package com.heishandashu.moreswitches.client.mixin;

import com.heishandashu.moreswitches.fullscreen.FullscreenKeyRule;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets the full-screen key switch work without actually changing the display mode.
 *
 * <p>{@code toggleFullscreen} is a private method whose only caller is the key handler, so
 * cancelling it here disables exactly the key: the video-settings button, a launch argument and
 * the operating system's own shortcuts all still work, because none of them come through this
 * method.</p>
 *
 * <p>Cancelling at {@code HEAD} is necessary rather than tidy: the method resizes the window and
 * recreates the framebuffer as it goes, so a cancel afterwards would leave the display changed and
 * the internal state half-updated.</p>
 */
@Mixin(Minecraft.class)
public class MixinMinecraftFullscreen {

    @Inject(method = "toggleFullscreen", at = @At("HEAD"), cancellable = true, require = 1)
    private void moreswitches$blockFullscreenKey(CallbackInfo cir) {
        if (FullscreenKeyRule.blocksToggle()) {
            cir.cancel();
        }
    }
}

package com.heishandashu.moreswitches.client.mixin;

import com.heishandashu.moreswitches.sound.SoundRules;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Drops the configured sound events before the engine starts playing them.
 *
 * <p>This is the one place every sound passes through: sounds sent by the server as position
 * packets, sounds bound to an entity, and long-lived looping instances such as the minecart rattle
 * all end up here as a {@code SoundInstance}, so a single check covers every path.</p>
 *
 * <p>Answering {@code NOT_STARTED} makes the engine skip the sound exactly as if it had failed to
 * load. Nothing else changes: the sound is not muted globally, and turning the switch off brings it
 * straight back.</p>
 */
@Mixin(SoundEngine.class)
public class MixinSoundEngine {

    @Inject(method = "play", at = @At("HEAD"), cancellable = true, require = 1)
    private void moreswitches$silence(SoundInstance instance,
                                       CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        if (!SoundRules.anyEnabled()) {
            return;
        }
        if (SoundRules.blocked(instance.getIdentifier().toString())) {
            cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
        }
    }
}

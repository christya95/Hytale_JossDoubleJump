package ca.joss.jossdoublejump.mixin;

import ca.joss.jossdoublejump.input.JossQueueJumpTracker;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Records each {@link PlayerInput.SetMovementStates} as it enters the queue, before movement merge — gives a cleaner
 * jump bit and rising edges when {@link com.hypixel.hytale.protocol.MovementStates} on the entity lags or sticks.
 */
@Mixin(PlayerInput.class)
public class PlayerInputQueueMixin {

    @Inject(method = "queue", at = @At("HEAD"))
    private void jossDoubleJump$onInputQueued(PlayerInput.InputUpdate update, CallbackInfo ci) {
        JossQueueJumpTracker.onMovementUpdateQueued((PlayerInput) (Object) this, update);
    }
}

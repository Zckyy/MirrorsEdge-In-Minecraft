package dev.faithrunner.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.faithrunner.Faith;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

/** While Faith has the player, the mouse turns her (her look limits and turns), not the player. */
@Mixin(Entity.class)
public abstract class EntityTurnMixin {
	@Inject(method = "turn", at = @At("HEAD"), cancellable = true)
	private void faithrunner$look(double xo, double yo, CallbackInfo ci) {
		if (Faith.active && (Object) this instanceof LocalPlayer) {
			Faith.addLook(xo, yo);
			ci.cancel();
		}
	}
}

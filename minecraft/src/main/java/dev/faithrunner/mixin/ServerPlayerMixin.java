package dev.faithrunner.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.faithrunner.Faith;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Singleplayer: the server takes her moves as she makes them ("moved wrongly" is skipped, as just
 * after a knockback), and Minecraft's fall damage doesn't land on top of Mirror's Edge's landings.
 */
@Mixin(LivingEntity.class)
public abstract class ServerPlayerMixin {
	private boolean faithrunner$hers() {
		return Faith.driving() && (Object) this instanceof ServerPlayer sp && sp.getUUID().equals(Faith.owner);
	}

	@Inject(method = "isInPostImpulseGraceTime", at = @At("HEAD"), cancellable = true)
	private void faithrunner$grace(CallbackInfoReturnable<Boolean> cir) {
		if (this.faithrunner$hers()) {
			cir.setReturnValue(true);
		}
	}

}

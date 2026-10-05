package dev.faithrunner.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.faithrunner.Faith;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/** Minecraft's own walking, jumping and falling stand aside: Faith moves the player. */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMoveMixin {
	@Inject(method = "move", at = @At("HEAD"), cancellable = true)
	private void faithrunner$move(MoverType moverType, Vec3 delta, CallbackInfo ci) {
		if (Faith.driving() && (moverType == MoverType.SELF || moverType == MoverType.PLAYER)) {
			ci.cancel();
		}
	}
}

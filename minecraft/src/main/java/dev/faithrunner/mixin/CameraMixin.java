package dev.faithrunner.mixin;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.faithrunner.Faith;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.phys.Vec3;

/** Faith steps once a rendered frame, and in first person the camera is hers (with its roll). */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow @Final private static Vector3fc FORWARDS;
	@Shadow @Final private static Vector3fc UP;
	@Shadow @Final private static Vector3fc LEFT;
	@Shadow @Final private Quaternionf rotation;
	@Shadow @Final private Vector3f forwards;
	@Shadow @Final private Vector3f up;
	@Shadow @Final private Vector3f left;
	@Shadow private int matrixPropertiesDirty;

	@Shadow protected abstract void setPosition(Vec3 position);

	@Inject(method = "update", at = @At("HEAD"))
	private void faithrunner$step(DeltaTracker deltaTracker, CallbackInfo ci) {
		Faith.frame();
	}

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void faithrunner$camera(float partialTicks, CallbackInfo ci) {
		if (!Faith.drivesCamera()) {
			return;
		}
		this.setPosition(Faith.cameraPos());
		this.rotation.set(Faith.cameraRotation());
		FORWARDS.rotate(this.rotation, this.forwards);
		UP.rotate(this.rotation, this.up);
		LEFT.rotate(this.rotation, this.left);
		this.matrixPropertiesDirty |= 3;
	}
}

package dev.faithrunner.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import dev.faithrunner.FaithBody;
import net.minecraft.client.Minecraft;

/** Her arms' field of view for Iris's hand pass too, which reads the hand's own (GameRendererMixin). */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.pathways.HandRenderer", remap = false)
public abstract class IrisHandFovMixin {
	@ModifyExpressionValue(method = "setupGlState", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/state/level/CameraRenderState;hudFov:F"), require = 0)
	private float faithrunner$armsFov(float hudFov) {
		if (!FaithBody.drawing()) {
			return hudFov;
		}
		Minecraft mc = Minecraft.getInstance();
		float aspect = (float) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
		return FaithBody.armsFov(mc.gameRenderer.mainCamera().getFov(), aspect);
	}
}

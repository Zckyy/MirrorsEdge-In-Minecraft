package dev.faithrunner.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.faithrunner.Faith;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.AABB;

/**
 * Singleplayer only (the integrated server is in this JVM): Faith's moves (wallruns, vaults, ledge
 * grabs) aren't Minecraft's, so its movement checks don't apply to her player while she's on.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerMovementMixin {
	@Shadow public ServerPlayer player;

	private boolean faithrunner$hers() {
		return Faith.active && this.player.level().getServer() instanceof IntegratedServer && this.player.getUUID().equals(Faith.owner);
	}

	@Inject(method = "shouldCheckPlayerMovement", at = @At("HEAD"), cancellable = true)
	private void faithrunner$speed(boolean isFallFlying, CallbackInfoReturnable<Boolean> cir) {
		if (this.faithrunner$hers()) {
			cir.setReturnValue(false);
		}
	}

	/**
	 * Her box may graze a block (feet a hair into the floor from float rounding, a corner during
	 * a vault or a mantle): taken as she made it, not snapped back to the last accepted spot.
	 */
	@Inject(method = "isEntityCollidingWithAnythingNew", at = @At("HEAD"), cancellable = true)
	private void faithrunner$grazing(LevelReader level, Entity entity, AABB oldAABB, double newX, double newY, double newZ, CallbackInfoReturnable<Boolean> cir) {
		if (this.faithrunner$hers()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "getMaximumFlyingTicks", at = @At("HEAD"), cancellable = true)
	private void faithrunner$floating(Entity entity, CallbackInfoReturnable<Integer> cir) {
		if (this.faithrunner$hers()) {
			cir.setReturnValue(Integer.MAX_VALUE);
		}
	}
}

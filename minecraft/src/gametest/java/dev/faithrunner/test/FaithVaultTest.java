package dev.faithrunner.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import dev.faithrunner.FaithTesting;

/** Vault a block-high wall and mantle a two-high one, logging her and the camera every tick. */
public class FaithVaultTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getServer().runCommand("time set noon");
			context.waitTicks(40);
			context.waitFor(mc -> FaithTesting.loaded(), 2000);
			// Spawn is (0.5, -60, 0.5) facing +Z: a one-high wall 6 blocks on, a two-high one past it.
			world.getServer().runCommand("fill -4 -60 6 4 -60 6 minecraft:stone");
			world.getServer().runCommand("fill -4 -60 14 4 -59 16 minecraft:stone");
			context.waitTicks(20);
			context.runOnClient(FaithTesting::toggle);
			context.waitTicks(20);
			context.getInput().holdKey(o -> o.keyUp);
			boolean jumped1 = false, jumped2 = false;
			for (int i = 0; i < 90; i++) {
				double z = context.computeOnClient(mc -> mc.player.getZ());
				if (!jumped1 && z > 4.6) {
					context.getInput().holdKeyFor(o -> o.keyJump, 3);
					jumped1 = true;
				}
				if (!jumped2 && z > 12.4) {
					context.getInput().holdKeyFor(o -> o.keyJump, 3);
					jumped2 = true;
				}
				context.waitTick();
				context.runOnClient(mc -> System.out.println("FAITH-VAULT " + FaithTesting.where(mc)));
			}
			context.getInput().releaseKey(o -> o.keyUp);
		}
	}
}

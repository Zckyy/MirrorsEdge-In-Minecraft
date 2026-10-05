package dev.faithrunner.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import dev.faithrunner.FaithTesting;

/**
 * Her step sounds' surfaces: a run over strips of floor (stone, planks, iron, glass, wool,
 * water, an iron trapdoor, a copper grate), then a wallrun along an iron wall. Logs what her
 * feet and hands touch every tick ("feet/hands", faith.h's numbers).
 */
public class FaithSurfacesTest implements FabricClientGameTest {
	private static final String[] FLOORS = {"stone", "oak_planks", "iron_block", "glass", "white_wool", "water", "iron_trapdoor", "copper_grate"};

	@Override
	public void runTest(ClientGameTestContext context) {
		if (TestFilter.skip(FaithSurfacesTest.class)) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			var server = world.getServer();
			server.runCommand("time set noon");
			// Spawn is (0.5, -60, 0.5) facing +Z, on grass: three-block strips from z = 3 on.
			for (int i = 0; i < FLOORS.length; i++) {
				int z = 3 + i * 3;
				String floor = FLOORS[i];
				if (floor.equals("water")) {
					// A one-deep ditch she wades through.
					server.runCommand("fill -2 -61 " + z + " 2 -61 " + (z + 2) + " minecraft:water");
				} else if (floor.equals("iron_trapdoor")) {
					server.runCommand("fill -2 -61 " + z + " 2 -61 " + (z + 2) + " minecraft:air");
					server.runCommand("fill -2 -61 " + z + " 2 -61 " + (z + 2) + " minecraft:iron_trapdoor[half=top]");
				} else {
					server.runCommand("fill -2 -61 " + z + " 2 -61 " + (z + 2) + " minecraft:" + floor);
				}
			}
			// An iron wall to wallrun along, on her right (-X) past the strips.
			server.runCommand("fill -1 -60 32 -1 -57 44 minecraft:iron_block");
			context.waitTicks(40);
			context.waitFor(mc -> FaithTesting.loaded(), 2000);
			context.runOnClient(FaithTesting::toggle);
			context.waitTicks(20);

			context.getInput().holdKey(o -> o.keyUp);
			boolean jumped = false;
			for (int i = 0; i < 140; i++) {
				double z = context.computeOnClient(mc -> mc.player.getZ());
				if (!jumped && z > 35.5) {
					// Angle into the wall on her right (-X) and jump: a wallrun.
					context.runOnClient(mc -> FaithTesting.turn(15, 0));
					context.getInput().holdKeyFor(o -> o.keyJump, 3);
					jumped = true;
				}
				context.waitTick();
				context.runOnClient(mc -> System.out.println("FAITH-SURF " + FaithTesting.surfaces(mc) + " " + FaithTesting.where(mc)
					+ " on " + mc.level.getBlockState(mc.player.blockPosition().below()).getBlock().getDescriptionId()));
				if (z > 46) {
					break;
				}
			}
			context.getInput().releaseKey(o -> o.keyUp);
		}
	}
}

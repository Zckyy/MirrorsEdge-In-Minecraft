package dev.faithrunner.test;

import java.util.LinkedHashSet;
import java.util.Set;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import dev.faithrunner.FaithTesting;

/** Minecraft's blocks as Mirror's Edge's: ladders, doors, stairs, slabs, hay, bars, a held sword. */
public class FaithBlocksTest implements FabricClientGameTest {
	private ClientGameTestContext context;
	private TestSingleplayerContext world;

	private void cmd(String c) {
		world.getServer().runCommand(c);
	}

	/** Puts her somewhere facing +Z (south), runs `ticks` holding W (jumping at `jumpZ`), and logs what she did. */
	private void run(String label, double x, double y, double z, int ticks, double jumpZ) {
		run(label, x, y, z, ticks, jumpZ, false);
	}

	/** As above, pressing melee instead of jump at `jumpZ` when `melee`. */
	private void run(String label, double x, double y, double z, int ticks, double jumpZ, boolean melee) {
		cmd(String.format("tp @p %.2f %.2f %.2f 0 0", x, y, z));
		context.waitTicks(10);
		Set<String> states = new LinkedHashSet<>();
		context.getInput().holdKey(o -> o.keyUp);
		boolean jumped = jumpZ > 1e8;
		for (int i = 0; i < ticks; i++) {
			double pz = context.computeOnClient(mc -> mc.player.getZ());
			if (!jumped && pz > jumpZ) {
				if (melee) {
					context.getInput().holdKeyFor(FaithTesting.meleeKey(), 2);
				} else {
					context.getInput().holdKeyFor(o -> o.keyJump, 3);
				}
				jumped = true;
			}
			context.waitTick();
			String state = context.computeOnClient(mc -> FaithTesting.state());
			// Her hands up in view (on the bar): what the body looks like close up.
			if (states.add(state) && state.equals("Swing")) {
				context.waitTicks(4);
				context.takeScreenshot(label + "-swing");
			}
		}
		context.getInput().releaseKey(o -> o.keyUp);
		context.waitTicks(10);
		context.runOnClient(mc -> System.out.println("FAITH-BLOCKS " + label + ": " + states + " -> " + FaithTesting.where(mc)));
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (TestFilter.skip(FaithBlocksTest.class)) {
			return;
		}
		this.context = context;
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			this.world = world;
			cmd("time set noon");
			cmd("gamerule doDaylightCycle false");
			context.waitTicks(40);
			context.waitFor(mc -> FaithTesting.loaded(), 2000);
			// Ground is y -61 (top at -60). Each piece at its own x, approached going +Z.
			// Ladder: a 4 high wall at z 6, ladder on its north face, roof on top.
			cmd("fill -2 -60 6 2 -57 8 minecraft:stone");
			cmd("fill 0 -60 5 0 -57 5 minecraft:ladder[facing=north]");
			// Door: a wall at z 6 with an oak door in it (x 20).
			cmd("fill 18 -60 6 22 -58 6 minecraft:stone");
			cmd("setblock 20 -60 6 minecraft:oak_door[facing=north,half=lower]");
			cmd("setblock 20 -59 6 minecraft:oak_door[facing=north,half=upper]");
			// Stairs going up south (x 40), then a slab step (x 60).
			cmd("setblock 40 -60 4 minecraft:oak_stairs[facing=south]");
			cmd("setblock 40 -59 5 minecraft:oak_stairs[facing=south]");
			cmd("setblock 40 -58 6 minecraft:oak_stairs[facing=south]");
			cmd("fill 39 -60 5 41 -60 12 minecraft:stone");
			cmd("fill 39 -59 6 41 -59 12 minecraft:stone");
			cmd("fill 39 -58 7 41 -58 12 minecraft:stone");
			cmd("setblock 40 -58 6 minecraft:oak_stairs[facing=south]");
			cmd("fill 59 -60 5 61 -60 12 minecraft:smooth_stone_slab[type=bottom]");
			// Hay: a 6 high tower to run off (x 80), hay at its foot.
			cmd("fill 79 -60 0 81 -55 4 minecraft:stone");
			cmd("fill 79 -60 5 81 -60 9 minecraft:hay_block");
			// Iron bars 3 high across the way (x 100).
			cmd("fill 98 -57 6 102 -57 6 minecraft:iron_bars");
			cmd("fill 98 -57 10 102 -57 10 minecraft:iron_bars");
			context.waitTicks(20);
			context.runOnClient(FaithTesting::toggle);
			context.waitTicks(20);

			if (!Boolean.getBoolean("faithrunner.heldOnly")) {
			run("ladder", 0.5, -60, 1.5, 200, 1e9);
			run("door", 20.5, -60, 0.5, 60, 4.2, true);
				context.runOnClient(mc -> System.out.println("FAITH-BLOCKS door block: " + mc.level.getBlockState(new net.minecraft.core.BlockPos(20, -60, 6))));
			run("stairs", 40.5, -60, 1.5, 60, 1e9);
			run("slab", 60.5, -60, 2.5, 40, 1e9);
			run("hay", 80.5, -54, 1.5, 60, 1e9);
			run("bars", 100.5, -60, 2.0, 120, 4.6);
			}

			// A sword in her hand, running.
			cmd("give @p minecraft:diamond_sword");
			context.waitTicks(10);
			cmd("tp @p 120.5 -60 0.5 0 0");
			context.waitTicks(10);
			context.getInput().holdKey(o -> o.keyUp);
			for (int i = 0; i < 4; i++) {
				context.waitTicks(4);
				context.takeScreenshot("held-" + i);
				context.runOnClient(mc -> System.out.println("FAITH-BONES " + FaithTesting.bones("RightWeapon", "RightHand", "RightForeArm", "RightHandMiddle0", "RightHandIndex0", "RightHandPinky0", "RightHandThumb0")));
			}
			context.getInput().releaseKey(o -> o.keyUp);
			// A punch: her hand comes up into the middle of the view.
			context.waitTicks(30);
			context.getInput().holdKeyFor(FaithTesting.meleeKey(), 2);
			for (int i = 0; i < 8; i++) {
				context.takeScreenshot("punch-" + i);
				context.waitTick();
			}
			// Her shadow, in the morning sun (east): looking down, then round the compass.
			cmd("time set 1000");
			context.waitTicks(20);
			context.runOnClient(mc -> FaithTesting.turn(0, 50));
			for (int i = 0; i < 4; i++) {
				context.waitTicks(5);
				context.takeScreenshot("shadow-" + i);
				context.runOnClient(mc -> FaithTesting.turn(90, 0));
			}
		}
	}
}

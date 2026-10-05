package dev.faithrunner.test;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;

import dev.faithrunner.FaithTesting;

/**
 * Minecraft's blocks as more of Mirror's Edge's fixtures: chains up a wall as a drainpipe, a line
 * of chains stepping down as a zipline, fences and walls along a one-wide wall as balance beams,
 * and a fence on the ground as a railing she vaults. Fails on the first one she doesn't use.
 */
public class FaithFixturesTest implements FabricClientGameTest {
	private ClientGameTestContext context;
	private TestSingleplayerContext world;
	/** The highest her feet got in the last run. */
	private double maxY;

	private void cmd(String c) {
		world.getServer().runCommand(c);
	}

	/** At (x, y, z) facing south, by way of a spot well away (a short teleport she wouldn't notice). */
	private void start(double x, double y, double z) {
		cmd(String.format("tp @p %.2f %.2f %.2f 0 0", x, y, z - 10));
		context.waitTicks(5);
		cmd(String.format("tp @p %.2f %.2f %.2f 0 0", x, y, z));
		context.waitTicks(20);
	}

	private static double y(Minecraft mc) {
		return mc.player.getY();
	}

	private static double z(Minecraft mc) {
		return mc.player.getZ();
	}

	/**
	 * Holds W for up to `ticks` (pressing jump once she's past `jumpZ`), collecting her moves, until
	 * `done` holds of them; then checks `want` was among them and `ok` of where she ended up.
	 */
	private void run(String label, int ticks, double jumpZ, String want, Predicate<Set<String>> done, Predicate<Minecraft> ok, String okWhat) {
		Set<String> states = new LinkedHashSet<>();
		context.getInput().holdKey(o -> o.keyUp);
		boolean jumped = jumpZ > 1e8;
		maxY = -1e9;
		for (int i = 0; i < ticks && !done.test(states); i++) {
			if (!jumped && context.computeOnClient(FaithFixturesTest::z) > jumpZ) {
				context.getInput().holdKeyFor(o -> o.keyJump, 3);
				jumped = true;
			}
			context.waitTick();
			states.add(context.computeOnClient(mc -> FaithTesting.state()));
			maxY = Math.max(maxY, context.computeOnClient(FaithFixturesTest::y));
			if (i % 10 == 0) {
				context.takeScreenshot(label + "-" + i);
			}
		}
		context.getInput().releaseKey(o -> o.keyUp);
		context.waitTicks(10);
		String where = context.computeOnClient(FaithTesting::where);
		System.out.println("FAITH-FIXTURES " + label + ": " + states + " -> " + where);
		if (!states.contains(want)) {
			throw new AssertionError(label + ": never " + want + ": " + states + " -> " + where);
		}
		if (!context.computeOnClient(ok::test)) {
			throw new AssertionError(label + ": " + okWhat + ": " + states + " -> " + where);
		}
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (TestFilter.skip(FaithFixturesTest.class)) {
			return;
		}
		this.context = context;
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			this.world = world;
			cmd("time set noon");
			cmd("gamerule doDaylightCycle false");
			cmd("effect give @p minecraft:resistance infinite 4 true");
			context.waitTicks(40);
			context.waitFor(mc -> FaithTesting.loaded(), 2000);
			// Ground is y -61 (top at -60). Each piece at its own x, approached going +Z (south).
			// Drainpipe (x 0): chains up the north face of a 6 high block, its roof to climb out onto.
			cmd("fill -1 -60 6 3 -55 9 minecraft:stone");
			cmd("fill 1 -60 5 1 -55 5 minecraft:iron_chain[axis=y]");
			// Zipline (x 20): a tower to jump from, a cable of chains stepping down a block every
			// three, 2.5 m over its roof at the top.
			cmd("fill 18 -60 -8 22 -54 0 minecraft:stone");
			for (int i = 0; i < 6; i++) {
				int z0 = -2 + i * 3;
				cmd("fill 20 " + (-51 - i) + " " + z0 + " 20 " + (-51 - i) + " " + (z0 + 2) + " minecraft:iron_chain[axis=z]");
			}
			// Balance beams: a one-wide wall two high, a fence (x 40) or a cobblestone wall (x 60) along
			// its top, and a block to step onto it from, level with its top.
			for (int x : new int[] { 40, 60 }) {
				cmd("fill " + x + " -60 4 " + x + " -59 14 minecraft:stone");
				cmd("fill " + x + " -58 4 " + x + " -58 14 minecraft:" + (x == 40 ? "oak_fence" : "cobblestone_wall"));
				cmd("fill " + (x - 1) + " -60 0 " + (x + 1) + " -58 3 minecraft:stone");
			}
			// A fence across the way on the ground (x 80): a railing, as high as it looks.
			cmd("fill 77 -60 6 83 -60 6 minecraft:oak_fence");
			context.waitTicks(20);
			context.runOnClient(FaithTesting::toggle);
			context.waitTicks(20);

			start(1.5, -60, 1.5);
			run("drainpipe", 300, 1e9, "Ladder", s -> false,
				mc -> maxY > -54.1, "never up on the roof");
			start(20.5, -53, -6.5);
			run("zipline", 240, -3.6, "Zipline", s -> false,
				mc -> z(mc) > 6, "didn't ride it down");
			start(40.5, -57, 1.5);
			run("fence beam", 120, 1e9, "Balance", s -> false, mc -> z(mc) > 8, "didn't walk along it");
			start(60.5, -57, 1.5);
			run("wall beam", 120, 1e9, "Balance", s -> false, mc -> z(mc) > 8, "didn't walk along it");
			start(80.5, -60, 1.5);
			run("railing", 60, 4.6, "Vault", s -> false, mc -> z(mc) > 7, "didn't get over it");
		}
	}
}

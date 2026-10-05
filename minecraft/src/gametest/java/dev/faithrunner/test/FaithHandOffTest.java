package dev.faithrunner.test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;

import dev.faithrunner.FaithTesting;

/**
 * What she has no move for goes to Minecraft and comes back: a dive into a pool and a swim out, a
 * wade through lava, a boat, and an elytra off a tower. Fails on the first thing that doesn't.
 */
public class FaithHandOffTest implements FabricClientGameTest {
	private ClientGameTestContext context;
	private TestSingleplayerContext world;
	/** Who had the player, each change, for the log. */
	private final List<String> modes = new ArrayList<>();

	private void cmd(String c) {
		world.getServer().runCommand(c);
	}

	private String mode() {
		String m = context.computeOnClient(mc -> FaithTesting.mode());
		if (modes.isEmpty() || !modes.getLast().equals(m)) {
			modes.add(m);
		}
		return m;
	}

	/** Ticks until `ok` holds of who has the player and where (up to `ticks`), else fails. */
	private void waitFor(String what, int ticks, Predicate<String> ok) {
		for (int i = 0; i < ticks; i++) {
			if (ok.test(mode())) {
				return;
			}
			context.waitTick();
		}
		throw new AssertionError(what + ": still " + mode() + " after " + ticks + " ticks; " + modes + " at " + context.computeOnClient(FaithTesting::where));
	}

	private void log(String label) {
		context.runOnClient(mc -> System.out.println("FAITH-HANDOFF " + label + ": " + modes + " -> " + FaithTesting.where(mc)));
		modes.clear();
	}

	/**
	 * Puts the player at (x, y, z) facing south, by way of a spot well away: a short teleport is too
	 * small for her to notice, and she'd carry on from where (and which way) she was.
	 */
	private void start(double x, double y, double z) {
		cmd(String.format("tp @p %.2f %.2f %.2f 0 0", x, y, z - 8));
		context.waitTicks(5);
		cmd(String.format("tp @p %.2f %.2f %.2f 0 0", x, y, z));
		context.waitTicks(10);
	}

	private static double z(Minecraft mc) {
		return mc.player.getZ();
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (TestFilter.skip(FaithHandOffTest.class)) {
			return;
		}
		this.context = context;
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			this.world = world;
			cmd("time set noon");
			cmd("gamerule doDaylightCycle false");
			cmd("gamemode survival @p");
			cmd("effect give @p minecraft:fire_resistance infinite 0 true");
			cmd("effect give @p minecraft:resistance infinite 4 true");
			context.waitTicks(40);
			context.waitFor(mc -> FaithTesting.loaded(), 2000);
			// Ground is y -61 (top at -60). Each piece at its own x, approached going +Z (south).
			// Pool (x 0): three deep from z 6 to 14, surface level with the ground.
			cmd("fill -3 -63 6 3 -61 14 minecraft:water");
			// A one-deep ditch (x 50): she wades along its floor, no hand-off.
			cmd("fill 49 -61 4 51 -61 12 minecraft:water");
			// Lava (x 100): one deep, level with the ground.
			cmd("fill 99 -61 5 101 -61 9 minecraft:lava");
			// Boat (x 200) on the grass.
			cmd("summon minecraft:oak_boat 200.5 -60 4.5");
			// Tower (x 300): 20 high, an elytra to glide off it with.
			cmd("fill 299 -60 -2 301 -41 2 minecraft:stone");
			context.waitTicks(20);
			context.runOnClient(FaithTesting::toggle);
			context.waitTicks(20);
			waitFor("on", 20, "faith"::equals);

			// Into the pool at a run: Minecraft swims. Swim on (W, jump to stay up) and out the far side.
			start(0.5, -60, 1.5);
			context.getInput().holdKey(o -> o.keyUp);
			waitFor("pool: swimming", 80, "minecraft: swimming"::equals);
			context.getInput().holdKey(o -> o.keyJump);
			waitFor("pool: out and hers again", 400, m -> m.equals("faith") && context.computeOnClient(FaithHandOffTest::z) > 14.5);
			context.getInput().releaseKey(o -> o.keyJump);
			context.waitTicks(20);
			context.getInput().releaseKey(o -> o.keyUp);
			log("pool");

			// The ditch: she runs along its floor, hers all the way.
			start(50.5, -60, 1.5);
			context.getInput().holdKey(o -> o.keyUp);
			for (int i = 0; i < 40; i++) {
				if (!mode().equals("faith")) {
					throw new AssertionError("ditch: handed off in one-deep water: " + modes);
				}
				context.waitTick();
			}
			context.getInput().releaseKey(o -> o.keyUp);
			log("ditch");

			// Lava: Minecraft wades through it (slowly), she takes over on the far side.
			start(100.5, -60, 1.5);
			context.getInput().holdKey(o -> o.keyUp);
			waitFor("lava: in", 80, "minecraft: lava"::equals);
			// Its floor is a block down: jump to climb out, as in Minecraft.
			context.getInput().holdKey(o -> o.keyJump);
			waitFor("lava: out and hers again", 400, m -> m.equals("faith") && context.computeOnClient(FaithHandOffTest::z) > 9.5);
			context.getInput().releaseKey(o -> o.keyJump);
			context.getInput().releaseKey(o -> o.keyUp);
			log("lava");

			// The boat: in it, Minecraft rows; out of it, she runs.
			start(200.5, -60, 1.5);
			cmd("ride @p mount @e[type=minecraft:oak_boat,limit=1,sort=nearest]");
			waitFor("boat: riding", 40, "minecraft: riding"::equals);
			cmd("ride @p dismount");
			waitFor("boat: hers again", 100, "faith"::equals);
			double before = context.computeOnClient(FaithHandOffTest::z);
			context.getInput().holdKey(o -> o.keyUp);
			context.waitTicks(20);
			context.getInput().releaseKey(o -> o.keyUp);
			double after = context.computeOnClient(FaithHandOffTest::z);
			if (after - before < 1.0) {
				throw new AssertionError("boat: she didn't run after getting out (" + before + " -> " + after + ")");
			}
			log("boat");

			// The elytra: run off the tower, jump while falling, glide, land, hers again.
			cmd("item replace entity @p armor.chest with minecraft:elytra");
			start(300.5, -40, 0.5);
			context.getInput().holdKey(o -> o.keyUp);
			waitFor("elytra: off the edge", 80, m -> context.computeOnClient(mc -> mc.player.getY()) < -42);
			context.getInput().holdKeyFor(o -> o.keyJump, 2);
			waitFor("elytra: gliding", 20, "minecraft: gliding"::equals);
			context.getInput().releaseKey(o -> o.keyUp);
			waitFor("elytra: landed and hers again", 600, "faith"::equals);
			log("elytra");
		}
	}
}

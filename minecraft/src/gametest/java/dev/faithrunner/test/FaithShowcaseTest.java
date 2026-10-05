package dev.faithrunner.test;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

import dev.faithrunner.FaithTesting;

/**
 * Footage for the README, not a check: a white course with red pieces at sunset, Faith through
 * each move in turn (vault, wallrun, drainpipe, balance beam, zipline, swing bars, slide, a dive
 * into a pool). Logs when each part starts (wall clock) so a recording of the window can be cut.
 * Runs only when asked for by name: -Ptests=Showcase (with -PwithIris -PwithShader for the look,
 * and -PwindowSize=1280x720 to record it).
 */
public class FaithShowcaseTest implements FabricClientGameTest {
	private ClientGameTestContext context;
	private TestSingleplayerContext world;

	private void cmd(String c) {
		world.getServer().runCommand(c);
	}

	/**
	 * At (x, y, z) facing south, by way of a spot well away (a short teleport she wouldn't notice),
	 * building `build` there first: blocks only go into loaded chunks, so each piece of the course
	 * is built once she's beside it.
	 */
	private void start(double x, double y, double z, String... build) {
		cmd(String.format("tp @p %.2f %.2f %.2f 0 0", x, y, z - 10));
		context.waitTicks(10);
		cmd(String.format("fill %d -61 %d %d -61 %d minecraft:white_concrete", (int) x - 8, (int) z - 14, (int) x + 8, (int) z + 30));
		for (String b : build) {
			cmd(b);
		}
		context.waitTicks(20);
		cmd(String.format("tp @p %.2f %.2f %.2f 0 0", x, y, z));
		context.waitTicks(30);
	}

	private void mark(String what) {
		System.out.println("FAITH-SHOW " + what + " " + System.currentTimeMillis());
	}

	/**
	 * Holds W (and `extra`, if any) for up to `ticks`, pressing jump the first time `jumpWhen`
	 * holds and crouch the first time `crouchWhen` does, until `stop` holds.
	 */
	private void segment(String label, int ticks, Function<Options, KeyMapping> extra, Predicate<Minecraft> jumpWhen, Predicate<Minecraft> crouchWhen,
		Predicate<Minecraft> stop) {
		mark(label + " start");
		Set<String> states = new LinkedHashSet<>();
		context.getInput().holdKey(o -> o.keyUp);
		if (extra != null) {
			context.getInput().holdKey(extra::apply);
		}
		boolean jumped = false, crouched = false;
		for (int i = 0; i < ticks; i++) {
			if (!jumped && context.computeOnClient(jumpWhen::test)) {
				context.getInput().holdKeyFor(o -> o.keyJump, 3);
				jumped = true;
			}
			if (!crouched && context.computeOnClient(crouchWhen::test)) {
				// The test input's key presses don't reach the sneak binding: crouch through her input.
				context.runOnClient(mc -> FaithTesting.crouch(true));
				crouched = true;
			}
			context.waitTick();
			states.add(context.computeOnClient(mc -> FaithTesting.state()));
			if (context.computeOnClient(stop::test)) {
				break;
			}
		}
		context.getInput().releaseKey(o -> o.keyUp);
		context.runOnClient(mc -> FaithTesting.crouch(false));
		if (extra != null) {
			context.getInput().releaseKey(extra::apply);
		}
		context.waitTicks(25);
		mark(label + " end");
		System.out.println("FAITH-SHOW " + label + ": " + states + " -> " + context.computeOnClient(FaithTesting::where));
	}

	private interface Function<A, B> {
		B apply(A a);
	}

	private static final Predicate<Minecraft> NEVER = mc -> false;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!System.getProperty("faithrunner.tests", "").toLowerCase().contains("showcase")) {
			return;
		}
		this.context = context;
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			this.world = world;
			cmd("time set 12200");
			cmd("gamerule doDaylightCycle false");
			cmd("gamerule doMobSpawning false");
			cmd("weather clear");
			cmd("effect give @p minecraft:resistance infinite 4 true");
			context.waitTicks(40);
			context.waitFor(mc -> FaithTesting.loaded(), 2000);
			context.waitTicks(40);
			context.runOnClient(FaithTesting::toggle);
			context.waitTicks(20);
			mark("ready");

			start(0.5, -60, -2.5, "fill -3 -60 14 3 -60 14 minecraft:crimson_fence");
			segment("vault", 70, null, mc -> mc.player.getZ() > 12.6, NEVER, NEVER);

			// As faith_move's own wallrun test: alongside the wall, half a metre off it, jump, and
			// angle into it (holding a little toward it).
			start(18.65, -60, -6.5, "fill 17 -60 -8 17 -55 40 minecraft:white_concrete", "fill 17 -57 -8 17 -57 40 minecraft:red_concrete");
			mark("wallrun start");
			java.util.Set<String> wr = new LinkedHashSet<>();
			context.getInput().holdKey(o -> o.keyUp);
			for (int i = 0; i < 90; i++) {
				if (i == 0) {
					while (context.computeOnClient(mc -> mc.player.getZ()) < 5) {
						context.waitTick();
						wr.add(context.computeOnClient(mc -> FaithTesting.state()));
					}
					context.getInput().holdKeyFor(o -> o.keyJump, 3);
					context.getInput().holdKey(o -> o.keyRight);
					context.runOnClient(mc -> FaithTesting.turn(25, 0));
				}
				context.waitTick();
				wr.add(context.computeOnClient(mc -> FaithTesting.state()));
			}
			context.getInput().releaseKey(o -> o.keyRight);
			context.getInput().releaseKey(o -> o.keyUp);
			context.waitTicks(25);
			mark("wallrun end");
			System.out.println("FAITH-SHOW wallrun: " + wr + " -> " + context.computeOnClient(FaithTesting::where));

			start(41.5, -60, 0.5, "fill 37 -60 6 45 -54 12 minecraft:white_concrete", "fill 37 -54 6 45 -54 6 minecraft:red_concrete",
				"fill 41 -60 5 41 -54 5 minecraft:iron_chain[axis=y]");
			segment("drainpipe", 260, null, NEVER, NEVER, mc -> mc.player.getY() > -53.1 && mc.player.getZ() > 7.5);
			start(60.5, -57, -1.5, "fill 60 -60 4 60 -59 18 minecraft:white_concrete", "fill 60 -58 4 60 -58 18 minecraft:crimson_fence",
				"fill 59 -60 -2 61 -58 3 minecraft:white_concrete");
			segment("beam", 140, null, NEVER, NEVER, mc -> mc.player.getZ() > 17);
			String[] zip = new String[8];
			zip[0] = "fill 78 -60 -10 82 -54 0 minecraft:white_concrete";
			zip[1] = "fill 78 -54 0 82 -54 0 minecraft:red_concrete";
			for (int i = 0; i < 6; i++) {
				int z0 = -2 + i * 3;
				zip[2 + i] = "fill 80 " + (-51 - i) + " " + z0 + " 80 " + (-51 - i) + " " + (z0 + 2) + " minecraft:iron_chain[axis=z]";
			}
			start(80.5, -53, -8.5, zip);
			segment("zipline", 200, null, mc -> mc.player.getZ() > -3.6, NEVER, mc -> mc.player.getZ() > 22);
			start(100.5, -60, 2.0, "fill 97 -57 7 103 -57 7 minecraft:iron_bars", "fill 97 -57 11 103 -57 11 minecraft:iron_bars",
				"fill 97 -60 7 97 -57 7 minecraft:red_concrete", "fill 103 -60 7 103 -57 7 minecraft:red_concrete",
				"fill 97 -60 11 97 -57 11 minecraft:red_concrete", "fill 103 -60 11 103 -57 11 minecraft:red_concrete");
			segment("swing", 120, null, mc -> mc.player.getZ() > 5.6, NEVER, mc -> mc.player.getZ() > 16);
			start(120.5, -60, -6.5, "fill 119 -61 4 121 -61 24 minecraft:red_concrete");
			segment("slide", 70, null, NEVER, mc -> {
				if (mc.player.getZ() > 4) {
					System.out.println("FAITH-SHOW slide crouch at " + String.format("%.2f m/s", mc.player.getDeltaMovement().horizontalDistance() * 20) + " " + FaithTesting.state());
					return true;
				}
				return false;
			}, NEVER);
			start(140.5, -60, 1.5, "fill 137 -63 8 143 -61 18 minecraft:water");
			segment("pool", 140, null, NEVER, NEVER, NEVER);
		}
	}
}

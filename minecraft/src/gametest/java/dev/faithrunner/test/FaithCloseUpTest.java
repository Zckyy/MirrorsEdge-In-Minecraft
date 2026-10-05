package dev.faithrunner.test;

import java.util.LinkedHashSet;
import java.util.Set;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import dev.faithrunner.FaithTesting;

/**
 * Her body close up and large, to judge its shading by eye: a punch at a wall, a kick at a door, a
 * climb onto a ledge. At 1600x900, a screenshot every couple of ticks of each.
 */
public class FaithCloseUpTest implements FabricClientGameTest {
	private ClientGameTestContext context;
	private TestSingleplayerContext world;

	private void cmd(String c) {
		world.getServer().runCommand(c);
	}

	/** At (x, y, z) facing south, by way of a spot well away (a short teleport she wouldn't notice). */
	private void start(double x, double y, double z) {
		cmd(String.format("tp @p %.2f %.2f %.2f 0 0", x, y, z - 8));
		context.waitTicks(5);
		cmd(String.format("tp @p %.2f %.2f %.2f 0 0", x, y, z));
		context.waitTicks(20);
	}

	private void shots(String label, int count, int every) {
		Set<String> states = new LinkedHashSet<>();
		for (int i = 0; i < count; i++) {
			states.add(context.computeOnClient(mc -> FaithTesting.state()));
			context.takeScreenshot(label + "-" + i);
			context.waitTicks(every);
		}
		System.out.println("FAITH-CLOSEUP " + label + ": " + states);
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (TestFilter.skip(FaithCloseUpTest.class)) {
			return;
		}
		this.context = context;
		context.getInput().resizeWindow(1600, 900);
		// -PresourcePack: that pack on (a LabPBR one, to see the blocks' maps beside hers).
		String pack = System.getProperty("faithrunner.resourcePack", "");
		if (!pack.isEmpty()) {
			java.util.concurrent.CompletableFuture<Void> reload = context.computeOnClient(mc -> {
				var repo = mc.getResourcePackRepository();
				repo.reload();
				if (!repo.addPack("file/" + pack)) {
					throw new AssertionError("resource pack not found: " + pack + " (have " + repo.getAvailableIds() + ")");
				}
				return mc.reloadResourcePacks();
			});
			context.waitFor(mc -> reload.isDone(), 2400);
			System.out.println("FAITH-CLOSEUP resource packs: " + context.computeOnClient(mc -> mc.getResourcePackRepository().getSelectedIds()));
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			this.world = world;
			cmd("time set noon");
			cmd("gamerule doDaylightCycle false");
			context.waitTicks(40);
			context.waitFor(mc -> FaithTesting.loaded(), 2000);
			// Ground is y -61 (top at -60). A stone wall (x 0), a door in a wall (x 20), a 2 high ledge (x 40).
			cmd("fill -3 -60 6 3 -56 6 minecraft:stone");
			cmd("fill 18 -60 6 22 -58 6 minecraft:stone");
			cmd("setblock 20 -60 6 minecraft:oak_door[facing=north,half=lower]");
			cmd("setblock 20 -59 6 minecraft:oak_door[facing=north,half=upper]");
			cmd("fill 38 -60 6 42 -59 9 minecraft:stone");
			// A row of materials (x 60): bricks, polished stone, glass, gold, iron, quartz, planks, ore.
			String[] blocks = { "bricks", "polished_andesite", "white_stained_glass", "gold_block", "iron_block", "quartz_block", "oak_planks", "diamond_ore" };
			for (int i = 0; i < blocks.length; i++) {
				cmd("fill " + (57 + i) + " -60 6 " + (57 + i) + " -57 6 minecraft:" + blocks[i]);
			}
			context.waitTicks(20);
			context.runOnClient(FaithTesting::toggle);
			context.waitTicks(20);

			// A punch, standing a step from the wall.
			start(0.5, -60, 4.6);
			context.getInput().holdKeyFor(FaithTesting.meleeKey(), 2);
			shots("wall-punch", 8, 1);
			// A kick at the door, standing.
			start(20.5, -60, 4.8);
			context.getInput().holdKeyFor(FaithTesting.meleeKey(), 2);
			shots("door-kick", 8, 2);
			// Run at the ledge and jump: hands on its edge, pulling up.
			start(40.5, -60, 1.5);
			context.getInput().holdKey(o -> o.keyUp);
			context.waitTicks(4);
			context.getInput().holdKeyFor(o -> o.keyJump, 3);
			shots("ledge", 10, 2);
			context.getInput().releaseKey(o -> o.keyUp);
			// The row of materials, a punch in front of it.
			start(60.5, -60, 2.5);
			context.getInput().holdKeyFor(FaithTesting.meleeKey(), 2);
			shots("materials", 3, 2);
		}
	}
}

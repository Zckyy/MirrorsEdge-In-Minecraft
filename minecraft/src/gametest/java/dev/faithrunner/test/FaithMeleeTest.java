package dev.faithrunner.test;

import java.util.UUID;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.entity.LivingEntity;

import dev.faithrunner.FaithTesting;

/**
 * Her attacks on a mob: a punch standing, a sliding kick and a jump kick, each at a husk (it
 * doesn't burn at noon) in front of her. Logs its health and where it is every tick.
 */
public class FaithMeleeTest implements FabricClientGameTest {
	private static final UUID HUSK = new UUID(0x5ACC_0000_0000_0000L, 1L);
	private static final String HUSK_NBT = "{UUID:[I;1523318784,0,0,1],PersistenceRequired:1b}";

	private static String husk(TestSingleplayerContext world) {
		return world.getServer().computeOnServer(server -> {
			var e = server.overworld().getEntity(HUSK);
			if (!(e instanceof LivingEntity l)) {
				return "husk gone";
			}
			return String.format("husk hp %.1f at (%.2f %.2f %.2f)%s", l.getHealth(), l.getX(), l.getY(), l.getZ(), l.isAlive() ? "" : " dead");
		});
	}

	/** Player back at spawn facing +Z, a fresh husk `ahead` blocks on. */
	private static void reset(ClientGameTestContext context, TestSingleplayerContext world, double ahead) {
		context.getInput().releaseKey(o -> o.keyUp);
		context.runOnClient(mc -> FaithTesting.crouch(false));
		world.getServer().runCommand("kill @e[type=minecraft:husk]");
		world.getServer().runCommand("tp @p 0.5 -60 0.5 0 0");
		context.waitTicks(20);
		world.getServer().runCommand("summon minecraft:husk 0.5 -60 " + (0.5 + ahead) + " " + HUSK_NBT);
		context.waitTicks(10);
	}

	private static void log(ClientGameTestContext context, TestSingleplayerContext world, String label, int tick) {
		String h = husk(world);
		context.runOnClient(mc -> System.out.println("FAITH-MELEE " + label + " " + tick + " " + FaithTesting.where(mc) + " | " + h));
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (TestFilter.skip(FaithMeleeTest.class)) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("difficulty easy");
			context.waitTicks(40);
			context.waitFor(mc -> FaithTesting.loaded(), 2000);
			context.runOnClient(FaithTesting::toggle);
			context.waitTicks(20);

			// A punch, standing: the husk just in reach.
			reset(context, world, 1.5);
			log(context, world, "punch", -1);
			context.getInput().holdKeyFor(FaithTesting.meleeKey(), 2);
			for (int i = 0; i < 30; i++) {
				context.waitTick();
				log(context, world, "punch", i);
				if (i == 8) {
					context.takeScreenshot("melee-punch");
				}
			}

			// A sliding kick: run at it, slide, kick.
			reset(context, world, 10);
			context.getInput().holdKey(o -> o.keyUp);
			boolean slid = false, kicked = false;
			for (int i = 0; i < 60; i++) {
				// How far ahead the husk is (it walks at her).
				double gap = world.getServer().computeOnServer(server -> {
					var e = server.overworld().getEntity(HUSK);
					return e == null ? 99.0 : e.getZ();
				}) - context.computeOnClient(mc -> mc.player.getZ());
				if (!slid && gap < 6.0) {
					context.runOnClient(mc -> FaithTesting.crouch(true));
					slid = true;
				}
				// The kick's hit detection comes on 0.25 s after the press: a few metres out.
				if (slid && !kicked && gap < 3.5) {
					context.getInput().holdKeyFor(FaithTesting.meleeKey(), 2);
					kicked = true;
				}
				context.waitTick();
				log(context, world, "slide", i);
				if (kicked && i % 4 == 0) {
					context.takeScreenshot("melee-slide-" + i);
				}
			}

			// A jump kick: run at it, jump, kick in the air.
			reset(context, world, 12);
			context.getInput().holdKey(o -> o.keyUp);
			boolean jumped = false;
			kicked = false;
			for (int i = 0; i < 60; i++) {
				double z = context.computeOnClient(mc -> mc.player.getZ());
				if (!jumped && z > 7.5) {
					context.getInput().holdKeyFor(o -> o.keyJump, 3);
					jumped = true;
				}
				if (jumped && !kicked && z > 8.5) {
					context.getInput().holdKeyFor(FaithTesting.meleeKey(), 2);
					kicked = true;
				}
				context.waitTick();
				log(context, world, "air", i);
				if (kicked && i % 4 == 0) {
					context.takeScreenshot("melee-air-" + i);
				}
			}
			context.getInput().releaseKey(o -> o.keyUp);

			// Left click is her attack while she's on: Minecraft's mining is off (the block stays),
			// and back once she's off (it breaks). Dirt: a fist breaks it in under a second in survival.
			reset(context, world, 99);
			world.getServer().runCommand("kill @e[type=minecraft:husk]");
			world.getServer().runCommand("setblock 0 -59 2 minecraft:dirt");
			context.waitTicks(5);
			context.runOnClient(mc -> System.out.println("FAITH-MELEE aiming at " + mc.hitResult + " mode " + mc.gameMode.getPlayerMode()));
			context.getInput().holdKeyFor(o -> o.keyAttack, 40);
			String on = world.getServer().computeOnServer(server -> server.overworld().getBlockState(new net.minecraft.core.BlockPos(0, -59, 2)).getBlock().getDescriptionId());
			System.out.println("FAITH-MELEE mining with Faith on: " + on);
			context.runOnClient(FaithTesting::toggle);
			context.waitTicks(5);
			context.getInput().holdKeyFor(o -> o.keyAttack, 40);
			String off = world.getServer().computeOnServer(server -> server.overworld().getBlockState(new net.minecraft.core.BlockPos(0, -59, 2)).getBlock().getDescriptionId());
			System.out.println("FAITH-MELEE mining with Faith off: " + off);
		}
	}
}

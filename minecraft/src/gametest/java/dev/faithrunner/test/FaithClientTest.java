package dev.faithrunner.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import dev.faithrunner.FaithTesting;

/** A world with Faith on: screenshots of her arms and legs standing, running and looking down. */
public class FaithClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("weather clear");
			context.waitTicks(60);
			context.waitFor(mc -> FaithTesting.loaded(), 2000);
			context.runOnClient(FaithTesting::toggle);
			context.waitTicks(40);
			context.waitTicks(10);
			context.takeScreenshot("faith-standing");
			context.runOnClient(mc -> System.out.println("FAITH-TEST standing " + FaithTesting.body() + " player " + mc.player.position()));
			context.getInput().holdKey(o -> o.keyUp);
			context.waitTicks(30);
			context.takeScreenshot("faith-running");
			context.waitTicks(7);
			context.takeScreenshot("faith-running-2");
			context.runOnClient(mc -> FaithTesting.turn(0, 25));
			context.waitTicks(5);
			context.takeScreenshot("faith-running-down");
			context.getInput().releaseKey(o -> o.keyUp);
			context.runOnClient(mc -> FaithTesting.turn(0, -25));
			context.waitTicks(20);
			context.runOnClient(mc -> System.out.println("FAITH-TEST running " + FaithTesting.body()));
			context.runOnClient(mc -> FaithTesting.turn(0, 70));
			context.waitTicks(10);
			context.takeScreenshot("faith-looking-down");
			context.runOnClient(mc -> System.out.println("FAITH-TEST down " + FaithTesting.body()));
			context.runOnClient(mc -> System.out.println("FAITH-TEST state " + FaithTesting.state()));
		}
	}
}

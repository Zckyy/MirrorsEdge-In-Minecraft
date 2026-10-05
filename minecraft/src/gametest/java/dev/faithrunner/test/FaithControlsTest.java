package dev.faithrunner.test;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.PauseScreen;

import dev.faithrunner.FaithControlsScreen;
import dev.faithrunner.FaithTesting;

/** The pause menu's "Faith Runner controls" button and the screen it opens (scrolled to the end too). */
public class FaithControlsTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (TestFilter.skip(FaithControlsTest.class)) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(40);
			context.waitFor(mc -> FaithTesting.loaded(), 2000);
			context.runOnClient(FaithTesting::toggle);
			context.waitTicks(10);
			context.setScreen(() -> new PauseScreen(true));
			context.waitTicks(5);
			context.takeScreenshot("controls-pause");
			context.clickScreenButton("Faith Runner controls");
			context.waitForScreen(FaithControlsScreen.class);
			context.waitTicks(5);
			context.takeScreenshot("controls-screen");
			context.runOnClient(mc -> mc.gui.screen().mouseScrolled(0, 0, 0, -100));
			context.waitTicks(2);
			context.takeScreenshot("controls-screen-end");
			context.clickScreenButton("gui.done");
			context.waitForScreen(PauseScreen.class);
			context.setScreen(() -> null);
		}
	}
}

package dev.faithrunner;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Faith's controls, from the pause menu: each key (as it's bound now) and what she does with it,
 * and the tips for her harder moves. Scrolls when the window is short.
 */
public final class FaithControlsScreen extends Screen {
	private static final int KEY = 0xFFFFD34D, TEXT = 0xFFE0E0E0, HEAD = 0xFFFFFFFF, DIM = 0xFFA0A0A0;
	private static final int TOP = 32, BOTTOM = 44, KEY_COLUMN = 110, MAX_WIDTH = 420;

	private final Screen parent;
	private final List<Line> lines = new ArrayList<>();
	private int contentHeight;
	private double scroll;

	/** A row: a key (or null) and its text, or a heading. */
	private record Line(FormattedCharSequence key, FormattedCharSequence text, int color, int gapBefore) {}

	FaithControlsScreen(Screen parent) {
		super(Component.literal("Faith Runner controls"));
		this.parent = parent;
	}

	/** The button on the pause menu, top left (clear of the menu's own grid). */
	static void register() {
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (screen instanceof PauseScreen pause && pause.showsPauseMenu()) {
				Screens.getWidgets(screen).add(Button.builder(Component.literal("Faith Runner controls"), b -> mc.gui.setScreen(new FaithControlsScreen(screen)))
					.bounds(6, 6, 130, 20)
					.build());
			}
		});
	}

	private static Component key(KeyMapping k) {
		return k.isUnbound() ? Component.literal("(unbound)") : k.getTranslatedKeyMessage();
	}

	@Override
	protected void init() {
		Options o = minecraft.options;
		lines.clear();
		String status = !Faith.active ? "Faith is off: press " + key(FaithRunner.TOGGLE).getString() + " to take over the player."
			: Faith.handedOff ? "Faith is on, Minecraft has the player for now (" + Faith.handOffReason + ")." : "Faith is on.";
		row(null, Component.literal(status), DIM, 0);

		heading("Moves");
		row(key(FaithRunner.TOGGLE), "Faith on / off");
		row(Component.literal(key(o.keyUp).getString() + " " + key(o.keyLeft).getString() + " " + key(o.keyDown).getString() + " "
			+ key(o.keyRight).getString() + ", mouse"), "Move, look");
		row(key(o.keyJump), "Jump, vault, wallrun, wallclimb, grab a ledge, pull up, kick off a wall");
		row(key(o.keyShift), "Crouch; slide at a run; coil in the air; roll just before landing; let go of a ledge or pole");
		row(key(FaithRunner.TURN), "180° turn (on the ground, in the air, on a wall, ledge or pole). On a wallrun: look out from the wall to jump across to another");
		row(key(FaithRunner.MELEE), "Attack: punches, jump kick, slide kick, wallrun kick. Barges or kicks a door open. Hurts mobs in singleplayer");

		heading("Tips");
		row(Component.literal("Sprint"), "Speed builds up the longer you run (up to 7.2 m/s after 7 s). Whipping the view round sheds it");
		row(Component.literal("Wallrun"), "Run at a wall at an angle, jump, hold forward. Beside a wall already: hold a little left or right toward it as you jump");
		row(Component.literal("Roll"), "Press " + key(o.keyShift).getString() + " in the last 0.2 s before landing a drop over 2 m. 5.3 m+ without a roll stops you dead");
		row(Component.literal("Dodge"), key(o.keyLeft).getString() + " or " + key(o.keyRight).getString() + " + " + key(o.keyJump).getString());
		row(Component.literal("Slide kick"), "Kick 2-3 blocks before the mob: the kick connects a moment after the press");

		heading("Blocks she uses");
		row(Component.literal("Ladder, vines"), "Climb, and off the top onto the roof");
		row(Component.literal("Chains, rods"), "Upright against a wall (3+): a drainpipe. In a line sloping down (6+): a zipline");
		row(Component.literal("Fences, walls"), "On the ground: a railing to vault. Along the top of a thin wall: a balance beam");
		row(Component.literal("Bars, fences"), "With 2 clear blocks below: a swing pole");
		row(Component.literal("Hay, slime"), "A soft landing for a big drop");
		row(Component.literal("Water, vehicles"), "Swimming, riding and the elytra are Minecraft's while they last; she's back once you're on your feet");

		contentHeight = 0;
		for (Line l : lines) {
			contentHeight += l.gapBefore() + font.lineHeight + 2;
		}
		int bw = 150;
		addRenderableWidget(Button.builder(Component.literal("Key bindings..."), b -> minecraft.gui.setScreen(new KeyBindsScreen(this, minecraft.options)))
			.bounds(width / 2 - bw - 4, height - 28, bw, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(width / 2 + 4, height - 28, bw, 20).build());
		scroll = Math.clamp(scroll, 0, maxScroll());
	}

	private void heading(String text) {
		lines.add(new Line(null, Component.literal(text).getVisualOrderText(), HEAD, 8));
	}

	private void row(Component key, String text) {
		row(key, Component.literal(text), TEXT, 2);
	}

	/** One entry, its text wrapped beside the key; or a line on its own (no key). */
	private void row(Component key, Component text, int color, int gap) {
		int width = Math.min(MAX_WIDTH, this.width - 40);
		int textWidth = key == null ? width : width - KEY_COLUMN;
		List<FormattedCharSequence> wrapped = font.split(text, Math.max(40, textWidth));
		for (int i = 0; i < wrapped.size(); i++) {
			FormattedCharSequence k = i == 0 && key != null ? key.getVisualOrderText() : null;
			lines.add(new Line(k, wrapped.get(i), color, i == 0 ? gap : 0));
		}
		if (key != null && wrapped.isEmpty()) {
			lines.add(new Line(key.getVisualOrderText(), FormattedCharSequence.EMPTY, color, gap));
		}
	}

	private double maxScroll() {
		return Math.max(0, contentHeight - (height - TOP - BOTTOM));
	}

	@Override
	public boolean mouseScrolled(double x, double y, double dx, double dy) {
		scroll = Math.clamp(scroll - dy * 12, 0, maxScroll());
		return true;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
		super.extractRenderState(g, mouseX, mouseY, partial);
		g.centeredText(font, title, width / 2, 12, HEAD);
		int w = Math.min(MAX_WIDTH, width - 40);
		int left = (width - w) / 2;
		int y = TOP - (int) scroll;
		int top = TOP, bottom = height - BOTTOM;
		for (Line l : lines) {
			y += l.gapBefore();
			if (y >= top - 1 && y + font.lineHeight <= bottom + 1) {
				boolean entry = l.color() == TEXT;
				if (l.key() != null) {
					g.text(font, l.key(), left, y, KEY);
				}
				g.text(font, l.text(), entry ? left + KEY_COLUMN : left, y, l.color());
			}
			y += font.lineHeight + 2;
		}
		if (maxScroll() > 0) {
			g.centeredText(font, scroll < maxScroll() ? "scroll for more" : "", width / 2, height - BOTTOM + 4, DIM);
		}
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}

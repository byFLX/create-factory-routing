package dev.failzockt.factoryrouting.client;

import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Forwards keyboard input to screens that host an embedded routing panel. */
public final class EmbeddedRoutingInputEvents {

	private EmbeddedRoutingInputEvents() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener(EmbeddedRoutingInputEvents::onKeyPressed);
		NeoForge.EVENT_BUS.addListener(EmbeddedRoutingInputEvents::onCharacterTyped);
	}

	private static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
		if (event.getScreen() instanceof EmbeddedRoutingInput input
			&& input.createFactoryRouting$keyPressed(event.getKeyCode(), event.getScanCode(),
				event.getModifiers()))
			event.setCanceled(true);
	}

	private static void onCharacterTyped(ScreenEvent.CharacterTyped.Pre event) {
		if (event.getScreen() instanceof EmbeddedRoutingInput input
			&& input.createFactoryRouting$charTyped(event.getCodePoint(), event.getModifiers()))
			event.setCanceled(true);
	}
}

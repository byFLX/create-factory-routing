package dev.failzockt.factoryrouting.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Shared colours and drawing primitives for the compact routing screens. */
final class RoutingUi {

	static final int FRAME = 0xFF111318;
	static final int PANEL = 0xFF2B2F38;
	static final int HEADER = 0xFF7890A6;
	static final int HEADER_DARK = 0xFF465A6C;
	static final int ACCENT = 0xFFB58B4A;
	static final int CARD = 0xFF22262E;
	static final int CARD_ALT = 0xFF272C35;
	static final int CARD_BORDER = 0xFF484E59;
	static final int INPUT = 0xFF101217;
	static final int TEXT = 0xFFF0EADF;
	static final int MUTED = 0xFFA7ADB8;
	static final int INHERITED = 0xFFC7B27D;
	static final int OVERRIDE = 0xFF55D9C0;

	private RoutingUi() {
	}

	static void window(GuiGraphics graphics, Font font, int x, int y, int width, int height,
		Component title) {
		graphics.fill(x, y, x + width, y + height, FRAME);
		graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, PANEL);
		graphics.fill(x + 2, y + 2, x + width - 2, y + 25, HEADER);
		graphics.fill(x + 2, y + 23, x + width - 2, y + 25, HEADER_DARK);
		graphics.fill(x + 2, y + 25, x + width - 2, y + 27, ACCENT);
		String label = clipped(font, title.getString(), width - 8);
		graphics.drawString(font, label, x + width / 2 - font.width(label) / 2, y + 9,
			0xFF202936, false);
	}

	static void card(GuiGraphics graphics, int x, int y, int width, int height, boolean alternate) {
		graphics.fill(x, y, x + width, y + height, CARD_BORDER);
		graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, alternate ? CARD_ALT : CARD);
	}

	static void item(GuiGraphics graphics, ItemStack stack, int x, int y) {
		graphics.fill(x, y, x + 22, y + 22, 0xFF16191F);
		graphics.fill(x + 1, y + 1, x + 21, y + 21, 0xFF343944);
		graphics.renderItem(stack, x + 3, y + 3);
	}

	static void routeStripe(GuiGraphics graphics, int x, int y, int height, boolean override) {
		graphics.fill(x, y, x + 3, y + height, override ? OVERRIDE : INHERITED);
	}

	static String clipped(Font font, String text, int width) {
		return font.plainSubstrByWidth(text, width);
	}
}

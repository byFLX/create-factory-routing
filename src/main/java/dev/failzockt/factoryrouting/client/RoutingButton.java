package dev.failzockt.factoryrouting.client;

import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** Flat, compact button matching the routing panels instead of the vanilla bevel. */
final class RoutingButton extends AbstractButton {

	enum Style {
		PRIMARY,
		SECONDARY
	}

	private final Style style;
	private final Consumer<RoutingButton> press;

	RoutingButton(int x, int y, int width, int height, Component message, Style style,
		Consumer<RoutingButton> press) {
		super(x, y, width, height, message);
		this.style = style;
		this.press = press;
	}

	@Override
	public void onPress() {
		press.accept(this);
	}

	@Override
	protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		boolean hovered = isHoveredOrFocused();
		int border;
		int fill;
		int text;
		if (!active) {
			border = 0xFF3B3F48;
			fill = 0xFF292C33;
			text = 0xFF6F747E;
		} else if (style == Style.PRIMARY) {
			border = hovered ? 0xFF8BE6D5 : 0xFF55BDAA;
			fill = hovered ? 0xFF39766B : 0xFF305F58;
			text = 0xFFF3FFFC;
		} else {
			border = hovered ? 0xFF98A3B2 : 0xFF626A77;
			fill = hovered ? 0xFF454C58 : 0xFF353A44;
			text = RoutingUi.TEXT;
		}

		graphics.fill(getX(), getY(), getX() + width, getY() + height, border);
		graphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, fill);
		if (style == Style.PRIMARY && active)
			graphics.fill(getX() + 1, getY() + 1, getX() + 3, getY() + height - 1, RoutingUi.OVERRIDE);

		var font = Minecraft.getInstance().font;
		String label = RoutingUi.clipped(font, getMessage().getString(), width - 8);
		graphics.drawString(font, label, getX() + (width - font.width(label)) / 2,
			getY() + (height - 8) / 2, text, false);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}

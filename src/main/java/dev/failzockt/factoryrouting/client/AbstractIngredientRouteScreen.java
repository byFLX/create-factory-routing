package dev.failzockt.factoryrouting.client;

import dev.failzockt.factoryrouting.routing.IngredientRoute;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Shared compact editor used by physical and virtual factory gauges. */
abstract class AbstractIngredientRouteScreen extends AbstractSimiScreen {

	private static final int WIDTH = 244;
	private static final int HEIGHT = 141;

	protected final Screen parent;
	private EditBox addressBox;
	private RoutingButton inheritButton;
	private String syncedAddress;
	private boolean edited;

	protected AbstractIngredientRouteScreen(Screen parent, String initialAddress) {
		super(Component.translatable("gui.create_factory_routing.ingredient_routing"));
		this.parent = parent;
		this.syncedAddress = initialAddress == null ? "" : initialAddress;
	}

	@Override
	protected void init() {
		setWindowSize(WIDTH, HEIGHT);
		super.init();
		clearWidgets();

		int x = guiLeft;
		int y = guiTop;
		if (addressBox == null) {
			addressBox = new EditBox(font, x + 15, y + 77, 214, 12, Component.empty());
			addressBox.setBordered(false);
			addressBox.setMaxLength(IngredientRoute.MAX_ADDRESS_LENGTH);
			addressBox.setTextColor(RoutingUi.TEXT);
			addressBox.setValue(syncedAddress);
			addressBox.setResponder(value -> edited = true);
		}
		addressBox.setX(x + 15);
		addressBox.setY(y + 77);
		addRenderableWidget(addressBox);

		inheritButton = addRenderableWidget(new RoutingButton(x + 10, y + 115, 94, 18,
			Component.translatable("gui.create_factory_routing.inherit"), RoutingButton.Style.SECONDARY,
			button -> addressBox.setValue("")));
		addRenderableWidget(new RoutingButton(x + 110, y + 115, 58, 18,
			Component.translatable("gui.create_factory_routing.cancel"), RoutingButton.Style.SECONDARY,
			button -> minecraft.setScreen(parent)));
		addRenderableWidget(new RoutingButton(x + 174, y + 115, 60, 18,
			Component.translatable("gui.create_factory_routing.save"), RoutingButton.Style.PRIMARY,
			button -> saveAndReturn()));

		requestSync();
		updateButtons();
	}

	@Override
	public void tick() {
		super.tick();

		String authoritative = currentRoute().address();
		if (!edited && !authoritative.equals(syncedAddress)) {
			syncedAddress = authoritative;
			addressBox.setValue(authoritative);
			edited = false;
		}
		updateButtons();
	}

	private void updateButtons() {
		if (inheritButton != null)
			inheritButton.active = !addressBox.getValue().isBlank();
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == 257 && addressBox.isFocused()) {
			saveAndReturn();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
		int x = guiLeft;
		int y = guiTop;
		RoutingUi.window(graphics, font, x, y, WIDTH, HEIGHT,
			Component.translatable("gui.create_factory_routing.ingredient_routing"));

		RoutingUi.card(graphics, x + 10, y + 32, WIDTH - 20, 25, false);
		ItemStack item = ingredient();
		RoutingUi.item(graphics, item, x + 12, y + 33);
		String itemName = item.isEmpty()
			? Component.translatable("gui.create_factory_routing.unknown_item").getString()
			: item.getHoverName().getString();
		String itemLine = RoutingUi.clipped(font, itemName + "  ×" + amount(), WIDTH - 58);
		graphics.drawString(font, itemLine, x + 40, y + 41, RoutingUi.TEXT, false);

		graphics.drawString(font, Component.translatable("gui.create_factory_routing.target_address"),
			x + 11, y + 63, RoutingUi.MUTED, false);
		graphics.fill(x + 10, y + 73, x + WIDTH - 10, y + 94, RoutingUi.CARD_BORDER);
		graphics.fill(x + 11, y + 74, x + WIDTH - 11, y + 93, RoutingUi.INPUT);

		boolean override = !addressBox.getValue().isBlank();
		String effective = override ? addressBox.getValue() : inheritedAddress();
		RoutingUi.card(graphics, x + 10, y + 98, WIDTH - 20, 13, false);
		RoutingUi.routeStripe(graphics, x + 10, y + 98, 13, override);
		String mode = Component.translatable(override
			? "gui.create_factory_routing.route_mode_override"
			: "gui.create_factory_routing.route_mode_inherited").getString();
		String status = mode + "  ·  " + (effective.isBlank() ? "—" : effective);
		graphics.drawString(font, RoutingUi.clipped(font, status, WIDTH - 34), x + 18, y + 101,
			override ? RoutingUi.OVERRIDE : RoutingUi.INHERITED, false);
	}

	private void saveAndReturn() {
		saveRoute(new IngredientRoute(addressBox.getValue()));
		minecraft.setScreen(parent);
	}

	protected void requestSync() {
	}

	protected abstract ItemStack ingredient();

	protected abstract int amount();

	protected abstract String inheritedAddress();

	protected abstract IngredientRoute currentRoute();

	protected abstract void saveRoute(IngredientRoute route);

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}

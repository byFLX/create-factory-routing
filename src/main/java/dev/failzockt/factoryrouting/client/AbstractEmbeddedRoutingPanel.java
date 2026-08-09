package dev.failzockt.factoryrouting.client;

import java.util.List;

import dev.failzockt.factoryrouting.routing.IngredientRoute;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/** Shared side-panel routing interface hosted by physical and virtual recipe screens. */
abstract class AbstractEmbeddedRoutingPanel<T> {

	private static boolean rememberedOpen;

	private static final int PREFERRED_LEFT_WIDTH = 184;
	private static final int PREFERRED_RIGHT_WIDTH = 224;
	private static final int MIN_LEFT_WIDTH = 82;
	private static final int MIN_RIGHT_WIDTH = 82;
	private static final int MIN_HEIGHT = 146;
	private static final int MAX_HEIGHT = 184;
	private static final int SCREEN_MARGIN = 4;
	private static final int PANEL_GAP = 6;
	private static final int ROW_HEIGHT = 27;
	private static final int LIST_TOP = 30;
	private static final int LIST_FOOTER = 15;

	private final List<T> entries;
	private final Font font;
	private final EditBox addressBox;
	private final RoutingButton inheritButton;
	private final RoutingButton cancelButton;
	private final RoutingButton saveButton;

	private boolean layoutAvailable;
	private boolean open = rememberedOpen;
	private boolean edited;
	private String syncedAddress = "";
	private int leftX;
	private int leftWidth;
	private int rightX;
	private int rightWidth;
	private int panelY;
	private int panelHeight;
	private boolean stackedButtons;
	private int rowsPerPage = 1;
	private int page;
	private int selectedIndex = -1;

	protected AbstractEmbeddedRoutingPanel(List<T> entries) {
		this.entries = entries;
		font = Minecraft.getInstance().font;
		addressBox = new EditBox(font, 0, 0, 100, 15, Component.empty());
		addressBox.setBordered(false);
		addressBox.setMaxLength(IngredientRoute.MAX_ADDRESS_LENGTH);
		addressBox.setTextColor(RoutingUi.TEXT);
		addressBox.setResponder(value -> edited = true);

		inheritButton = new RoutingButton(0, 0, 86, 18,
			Component.translatable("gui.create_factory_routing.inherit"), RoutingButton.Style.SECONDARY,
			button -> addressBox.setValue(""));
		cancelButton = new RoutingButton(0, 0, 54, 18,
			Component.translatable("gui.create_factory_routing.cancel"), RoutingButton.Style.SECONDARY,
			button -> resetEditor());
		saveButton = new RoutingButton(0, 0, 56, 18,
			Component.translatable("gui.create_factory_routing.save"), RoutingButton.Style.PRIMARY,
			button -> saveEditor());
		setWidgetsVisible(false);
	}

	public void layout(int screenWidth, int screenHeight, int hostX, int hostY,
		int hostWidth, int hostHeight) {
		int height = Mth.clamp(hostHeight, MIN_HEIGHT, MAX_HEIGHT);
		int availableLeft = hostX - PANEL_GAP - SCREEN_MARGIN;
		int availableRight = screenWidth - SCREEN_MARGIN - hostX - hostWidth - PANEL_GAP;
		int candidateLeftWidth = Math.min(PREFERRED_LEFT_WIDTH, availableLeft);
		int candidateRightWidth = Math.min(PREFERRED_RIGHT_WIDTH, availableRight);
		layoutAvailable = screenHeight >= height + SCREEN_MARGIN * 2
			&& candidateLeftWidth >= MIN_LEFT_WIDTH
			&& candidateRightWidth >= MIN_RIGHT_WIDTH;
		if (!layoutAvailable) {
			setWidgetsVisible(false);
			return;
		}

		panelHeight = height;
		leftWidth = candidateLeftWidth;
		rightWidth = candidateRightWidth;
		panelY = Mth.clamp(hostY, SCREEN_MARGIN, screenHeight - SCREEN_MARGIN - panelHeight);
		leftX = hostX - PANEL_GAP - leftWidth;
		rightX = hostX + hostWidth + PANEL_GAP;
		rowsPerPage = Math.max(1, (panelHeight - LIST_TOP - LIST_FOOTER) / ROW_HEIGHT);
		page = Mth.clamp(page, 0, pageCount() - 1);

		addressBox.setX(rightX + 14);
		addressBox.setY(panelY + 77);
		addressBox.setWidth(rightWidth - 28);
		layoutButtons();
		if (open && selectedEntry() == null && !entries.isEmpty())
			select(0);
		else
			setWidgetsVisible(open && selectedEntry() != null);
	}

	private void layoutButtons() {
		stackedButtons = rightWidth < 212;
		int controlsX = rightX + 10;
		int innerWidth = rightWidth - 20;
		if (stackedButtons) {
			int upperY = panelY + Math.min(119, panelHeight - 46);
			inheritButton.setX(controlsX);
			inheritButton.setY(upperY);
			inheritButton.setWidth(innerWidth);

			int lowerWidth = (innerWidth - 4) / 2;
			int lowerY = upperY + 21;
			cancelButton.setX(controlsX);
			cancelButton.setY(lowerY);
			cancelButton.setWidth(lowerWidth);
			saveButton.setX(controlsX + 4 + lowerWidth);
			saveButton.setY(lowerY);
			saveButton.setWidth(innerWidth - lowerWidth - 4);
			return;
		}

		int totalWidth = innerWidth - 8;
		int inheritWidth = totalWidth * 86 / 196;
		int cancelWidth = totalWidth * 54 / 196;
		int saveWidth = totalWidth - inheritWidth - cancelWidth;
		int buttonsY = panelY + Math.min(119, panelHeight - 25);
		inheritButton.setX(controlsX);
		inheritButton.setY(buttonsY);
		inheritButton.setWidth(inheritWidth);
		cancelButton.setX(inheritButton.getX() + inheritWidth + 4);
		cancelButton.setY(buttonsY);
		cancelButton.setWidth(cancelWidth);
		saveButton.setX(cancelButton.getX() + cancelWidth + 4);
		saveButton.setY(buttonsY);
		saveButton.setWidth(saveWidth);
	}

	public boolean isLayoutAvailable() {
		return layoutAvailable;
	}

	public boolean isOpen() {
		return open;
	}

	public List<Rect2i> extraAreas() {
		if (!open || !layoutAvailable)
			return List.of();
		return List.of(
			new Rect2i(leftX, panelY, leftWidth, panelHeight),
			new Rect2i(rightX, panelY, rightWidth, panelHeight));
	}

	public void toggle() {
		if (!layoutAvailable)
			return;
		open = !open;
		rememberedOpen = open;
		if (open) {
			if (selectedEntry() == null && !entries.isEmpty())
				select(0);
			requestSync();
		} else {
			addressBox.setFocused(false);
		}
		setWidgetsVisible(open && selectedEntry() != null);
	}

	public void openEntry(int index) {
		if (!layoutAvailable || index < 0 || index >= entries.size())
			return;
		open = true;
		rememberedOpen = true;
		select(index);
		requestSync();
	}

	public void tick() {
		if (!open || selectedEntry() == null)
			return;
		String authoritative = route(selectedEntry()).address();
		if (!edited && !authoritative.equals(syncedAddress))
			setEditorValue(authoritative);
		updateButtons();
	}

	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
		if (!open || !layoutAvailable)
			return;
		renderList(graphics, mouseX, mouseY);
		renderEditor(graphics, mouseX, mouseY, partialTicks);
	}

	private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
		RoutingUi.window(graphics, font, leftX, panelY, leftWidth, panelHeight,
			Component.translatable("gui.create_factory_routing.routing_overview"));
		if (entries.isEmpty()) {
			RoutingUi.card(graphics, leftX + 8, panelY + 32, leftWidth - 16, 24, false);
			Component empty = Component.translatable("gui.create_factory_routing.no_ingredients");
			graphics.drawString(font, RoutingUi.clipped(font, empty.getString(), leftWidth - 24),
				leftX + 12, panelY + 40, RoutingUi.MUTED, false);
			return;
		}

		int start = page * rowsPerPage;
		int end = Math.min(start + rowsPerPage, entries.size());
		for (int index = start; index < end; index++) {
			int row = index - start;
			int x = leftX + 7;
			int y = panelY + LIST_TOP + row * ROW_HEIGHT;
			int width = leftWidth - 14;
			boolean selected = index == selectedIndex;
			boolean hovered = contains(mouseX, mouseY, x, y, width, 25);
			RoutingUi.card(graphics, x, y, width, 25, (row & 1) != 0);
			if (selected)
				graphics.fill(x + 1, y + 1, x + width - 1, y + 24, 0xFF34434A);
			else if (hovered)
				graphics.fill(x + 1, y + 1, x + width - 1, y + 24, 0xFF303640);

			T entry = entries.get(index);
			IngredientRoute route = route(entry);
			RoutingUi.routeStripe(graphics, x, y, 25, route.isOverride());
			ItemStack stack = ingredient(entry);
			RoutingUi.item(graphics, stack, x + 4, y + 2);
			String itemName = stack.isEmpty()
				? Component.translatable("gui.create_factory_routing.unknown_item").getString()
				: stack.getHoverName().getString();
			int textWidth = width - 35;
			graphics.drawString(font, RoutingUi.clipped(font, itemName + "  ×" + amount(entry), textWidth),
				x + 31, y + 3, RoutingUi.TEXT, false);
			String mode = Component.translatable(route.isOverride()
				? "gui.create_factory_routing.route_mode_override_short"
				: "gui.create_factory_routing.route_mode_inherited_short").getString();
			String address = route.isOverride() ? route.address() : inheritedAddress();
			String routeLine = mode + "  ·  " + (address.isBlank() ? "—" : address);
			graphics.drawString(font, RoutingUi.clipped(font, routeLine, textWidth), x + 31, y + 14,
				route.isOverride() ? RoutingUi.OVERRIDE : RoutingUi.INHERITED, false);
		}

		if (pageCount() > 1) {
			String pages = (page + 1) + " / " + pageCount();
			graphics.drawString(font, pages, leftX + (leftWidth - font.width(pages)) / 2,
				panelY + panelHeight - 11, RoutingUi.MUTED, false);
		}
	}

	private void renderEditor(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
		RoutingUi.window(graphics, font, rightX, panelY, rightWidth, panelHeight,
			Component.translatable("gui.create_factory_routing.ingredient_routing"));
		T selected = selectedEntry();
		if (selected == null) {
			RoutingUi.card(graphics, rightX + 10, panelY + 32, rightWidth - 20, 24, false);
			Component prompt = Component.translatable("gui.create_factory_routing.select_ingredient");
			String label = RoutingUi.clipped(font, prompt.getString(), rightWidth - 24);
			graphics.drawString(font, label, rightX + (rightWidth - font.width(label)) / 2,
				panelY + 40, RoutingUi.MUTED, false);
			return;
		}

		RoutingUi.card(graphics, rightX + 10, panelY + 31, rightWidth - 20, 25, false);
		ItemStack stack = ingredient(selected);
		RoutingUi.item(graphics, stack, rightX + 12, panelY + 32);
		String itemName = stack.isEmpty()
			? Component.translatable("gui.create_factory_routing.unknown_item").getString()
			: stack.getHoverName().getString();
		graphics.drawString(font,
			RoutingUi.clipped(font, itemName + "  ×" + amount(selected), rightWidth - 58),
			rightX + 40, panelY + 40, RoutingUi.TEXT, false);

		graphics.drawString(font, Component.translatable("gui.create_factory_routing.target_address"),
			rightX + 11, panelY + 62, RoutingUi.MUTED, false);
		graphics.fill(rightX + 10, panelY + 72, rightX + rightWidth - 10, panelY + 93,
			RoutingUi.CARD_BORDER);
		graphics.fill(rightX + 11, panelY + 73, rightX + rightWidth - 11, panelY + 92,
			RoutingUi.INPUT);
		addressBox.render(graphics, mouseX, mouseY, partialTicks);

		boolean override = !addressBox.getValue().isBlank();
		String effective = override ? addressBox.getValue() : inheritedAddress();
		if (!stackedButtons || panelHeight >= 164) {
			RoutingUi.card(graphics, rightX + 10, panelY + 97, rightWidth - 20, 15, false);
			RoutingUi.routeStripe(graphics, rightX + 10, panelY + 97, 15, override);
			String mode = Component.translatable(override
				? "gui.create_factory_routing.route_mode_override"
				: "gui.create_factory_routing.route_mode_inherited").getString();
			String status = mode + "  ·  " + (effective.isBlank() ? "—" : effective);
			graphics.drawString(font, RoutingUi.clipped(font, status, rightWidth - 34),
				rightX + 18, panelY + 101, override ? RoutingUi.OVERRIDE : RoutingUi.INHERITED, false);
		}

		inheritButton.render(graphics, mouseX, mouseY, partialTicks);
		cancelButton.render(graphics, mouseX, mouseY, partialTicks);
		saveButton.render(graphics, mouseX, mouseY, partialTicks);
	}

	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (!open || !layoutAvailable)
			return false;
		if (button == 0 && contains(mouseX, mouseY, leftX, panelY, leftWidth, panelHeight)) {
			addressBox.setFocused(false);
			int row = (int) ((mouseY - panelY - LIST_TOP) / ROW_HEIGHT);
			if (mouseY >= panelY + LIST_TOP && row >= 0 && row < rowsPerPage) {
				int index = page * rowsPerPage + row;
				if (index < entries.size())
					select(index);
			}
			return true;
		}
		if (!contains(mouseX, mouseY, rightX, panelY, rightWidth, panelHeight)) {
			addressBox.setFocused(false);
			return false;
		}
		if (selectedEntry() == null)
			return true;

		if (addressBox.mouseClicked(mouseX, mouseY, button)) {
			addressBox.setFocused(true);
			return true;
		}
		addressBox.setFocused(false);
		if (inheritButton.mouseClicked(mouseX, mouseY, button)
			|| cancelButton.mouseClicked(mouseX, mouseY, button)
			|| saveButton.mouseClicked(mouseX, mouseY, button))
			return true;
		return true;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		if (!open || !layoutAvailable || scrollY == 0 || pageCount() <= 1
			|| !contains(mouseX, mouseY, leftX, panelY, leftWidth, panelHeight))
			return false;
		page = Mth.clamp(page + (scrollY < 0 ? 1 : -1), 0, pageCount() - 1);
		return true;
	}

	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (!open || !layoutAvailable)
			return false;
		if (keyCode == 257 && addressBox.isFocused()) {
			saveEditor();
			return true;
		}
		if (addressBox.isFocused() && addressBox.keyPressed(keyCode, scanCode, modifiers))
			return true;
		if (keyCode == 256) {
			open = false;
			rememberedOpen = false;
			addressBox.setFocused(false);
			setWidgetsVisible(false);
			return true;
		}
		return addressBox.isFocused();
	}

	public boolean charTyped(char codePoint, int modifiers) {
		return open && layoutAvailable && addressBox.isFocused()
			&& addressBox.charTyped(codePoint, modifiers);
	}

	private void select(int index) {
		selectedIndex = index;
		page = index / rowsPerPage;
		setEditorValue(route(entries.get(index)).address());
		setWidgetsVisible(open);
	}

	private void setEditorValue(String value) {
		syncedAddress = value == null ? "" : value;
		addressBox.setValue(syncedAddress);
		edited = false;
		updateButtons();
	}

	private void resetEditor() {
		T selected = selectedEntry();
		if (selected != null)
			setEditorValue(route(selected).address());
	}

	private void saveEditor() {
		T selected = selectedEntry();
		if (selected == null)
			return;
		IngredientRoute next = new IngredientRoute(addressBox.getValue());
		saveRoute(selected, next);
		setEditorValue(next.address());
	}

	private void updateButtons() {
		boolean hasSelection = selectedEntry() != null;
		inheritButton.active = hasSelection && !addressBox.getValue().isBlank();
		cancelButton.active = hasSelection && edited;
		saveButton.active = hasSelection && edited;
	}

	private void setWidgetsVisible(boolean visible) {
		addressBox.visible = visible;
		inheritButton.visible = visible;
		cancelButton.visible = visible;
		saveButton.visible = visible;
	}

	private T selectedEntry() {
		return selectedIndex >= 0 && selectedIndex < entries.size() ? entries.get(selectedIndex) : null;
	}

	private int pageCount() {
		return Math.max(1, (entries.size() + rowsPerPage - 1) / rowsPerPage);
	}

	private static boolean contains(double mouseX, double mouseY, int x, int y, int width, int height) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	protected void requestSync() {
	}

	protected abstract ItemStack ingredient(T entry);

	protected abstract int amount(T entry);

	protected abstract IngredientRoute route(T entry);

	protected abstract String inheritedAddress();

	protected abstract void saveRoute(T entry, IngredientRoute route);
}

package dev.failzockt.factoryrouting.client;

import java.util.ArrayList;
import java.util.List;

import dev.failzockt.factoryrouting.routing.IngredientRoute;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Shared paged ingredient list for physical panels and Factory Controller gauges. */
abstract class AbstractRoutingOverviewScreen<T> extends AbstractSimiScreen {

	private static final int WIDTH = 260;
	private static final int ROW_HEIGHT = 32;
	private static final int PAGE_SIZE = 4;
	private static final int FOOTER_HEIGHT = 32;

	protected final Screen parent;
	protected final List<T> connections;
	private int page;
	private int visibleRows;

	protected AbstractRoutingOverviewScreen(Screen parent, List<T> connections) {
		super(Component.translatable("gui.create_factory_routing.routing_overview"));
		this.parent = parent;
		this.connections = new ArrayList<>(connections);
	}

	@Override
	protected void init() {
		visibleRows = Math.max(1, Math.min(PAGE_SIZE, connections.size()));
		setWindowSize(WIDTH, 29 + visibleRows * ROW_HEIGHT + FOOTER_HEIGHT);
		super.init();
		clearWidgets();

		int start = page * PAGE_SIZE;
		int end = Math.min(start + PAGE_SIZE, connections.size());
		for (int i = start; i < end; i++) {
			T connection = connections.get(i);
			int localRow = i - start;
			addRenderableWidget(new RoutingButton(guiLeft + WIDTH - 55,
				guiTop + 35 + localRow * ROW_HEIGHT, 43, 18,
				Component.translatable("gui.create_factory_routing.edit"), RoutingButton.Style.SECONDARY,
				button -> minecraft.setScreen(editorFor(connection))));
		}

		int footerY = guiTop + 29 + visibleRows * ROW_HEIGHT + 7;
		if (pageCount() > 1) {
			RoutingButton previous = addRenderableWidget(new RoutingButton(guiLeft + 10, footerY, 22, 18,
				Component.literal("‹"), RoutingButton.Style.SECONDARY,
				button -> changePage(page - 1)));
			previous.active = page > 0;
			RoutingButton next = addRenderableWidget(new RoutingButton(guiLeft + 36, footerY, 22, 18,
				Component.literal("›"), RoutingButton.Style.SECONDARY,
				button -> changePage(page + 1)));
			next.active = page < pageCount() - 1;
		}
		addRenderableWidget(new RoutingButton(guiLeft + WIDTH - 68, footerY, 56, 18,
			Component.translatable("gui.create_factory_routing.back"), RoutingButton.Style.PRIMARY,
			button -> minecraft.setScreen(parent)));
		requestSync();
	}

	private int pageCount() {
		return Math.max(1, (connections.size() + PAGE_SIZE - 1) / PAGE_SIZE);
	}

	private void changePage(int next) {
		page = Math.max(0, Math.min(pageCount() - 1, next));
		rebuildWidgets();
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (pageCount() > 1 && mouseX >= guiLeft && mouseX < guiLeft + WIDTH
			&& mouseY >= guiTop && mouseY < guiTop + windowHeight && scrollY != 0) {
			changePage(page + (scrollY < 0 ? 1 : -1));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button))
			return true;
		if (button != 0 || connections.isEmpty())
			return false;
		if (mouseY < guiTop + 31 || mouseY >= guiTop + 31 + visibleRows * ROW_HEIGHT)
			return false;
		int row = (int) ((mouseY - (guiTop + 31)) / ROW_HEIGHT);
		if (row < 0 || row >= visibleRows || mouseX < guiLeft + 9 || mouseX >= guiLeft + WIDTH - 9)
			return false;
		int index = page * PAGE_SIZE + row;
		if (index >= connections.size())
			return false;
		minecraft.setScreen(editorFor(connections.get(index)));
		return true;
	}

	@Override
	protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
		RoutingUi.window(graphics, font, guiLeft, guiTop, WIDTH, windowHeight,
			Component.translatable("gui.create_factory_routing.routing_overview"));

		if (connections.isEmpty()) {
			RoutingUi.card(graphics, guiLeft + 10, guiTop + 34, WIDTH - 20, 23, false);
			Component empty = Component.translatable("gui.create_factory_routing.no_ingredients");
			graphics.drawString(font, empty, guiLeft + WIDTH / 2 - font.width(empty) / 2,
				guiTop + 41, RoutingUi.MUTED, false);
		} else {
			int start = page * PAGE_SIZE;
			int end = Math.min(start + PAGE_SIZE, connections.size());
			for (int i = start; i < end; i++)
				renderRow(graphics, connections.get(i), i - start, mouseX, mouseY);
		}

		if (pageCount() > 1) {
			String pages = (page + 1) + " / " + pageCount();
			int footerY = guiTop + 29 + visibleRows * ROW_HEIGHT + 12;
			graphics.drawString(font, pages, guiLeft + 65, footerY, RoutingUi.MUTED, false);
		}
	}

	private void renderRow(GuiGraphics graphics, T connection, int row, int mouseX, int mouseY) {
		int x = guiLeft + 9;
		int y = guiTop + 31 + row * ROW_HEIGHT;
		int width = WIDTH - 18;
		IngredientRoute route = route(connection);
		boolean override = route.isOverride();
		RoutingUi.card(graphics, x, y, width, 28, (row & 1) != 0);
		if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + 28)
			graphics.fill(x + 1, y + 1, x + width - 1, y + 27, 0x184F91C4);
		RoutingUi.routeStripe(graphics, x, y, 28, override);

		ItemStack stack = ingredient(connection);
		RoutingUi.item(graphics, stack, x + 5, y + 3);
		String itemName = stack.isEmpty()
			? Component.translatable("gui.create_factory_routing.unknown_item").getString()
			: stack.getHoverName().getString();
		graphics.drawString(font, RoutingUi.clipped(font, itemName + "  ×" + amount(connection), 139),
			x + 32, y + 4, RoutingUi.TEXT, false);

		String mode = Component.translatable(override
			? "gui.create_factory_routing.route_mode_override_short"
			: "gui.create_factory_routing.route_mode_inherited_short").getString();
		String address = override ? route.address() : inheritedAddress();
		String routeLine = mode + "  ·  " + (address.isBlank() ? "—" : address);
		graphics.drawString(font, RoutingUi.clipped(font, routeLine, 139), x + 32, y + 16,
			override ? RoutingUi.OVERRIDE : RoutingUi.INHERITED, false);
	}

	protected void requestSync() {
	}

	protected abstract ItemStack ingredient(T connection);

	protected abstract int amount(T connection);

	protected abstract IngredientRoute route(T connection);

	protected abstract String inheritedAddress();

	protected abstract Screen editorFor(T connection);

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}

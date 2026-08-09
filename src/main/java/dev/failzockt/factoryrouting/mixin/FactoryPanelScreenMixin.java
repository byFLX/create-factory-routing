package dev.failzockt.factoryrouting.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelScreen;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;

import dev.failzockt.factoryrouting.client.EmbeddedFactoryRoutingPanel;
import dev.failzockt.factoryrouting.client.EmbeddedRoutingInput;
import dev.failzockt.factoryrouting.client.IngredientRouteScreen;
import dev.failzockt.factoryrouting.client.RoutingOverviewScreen;
import dev.failzockt.factoryrouting.network.FactoryRoutingNetwork;
import dev.failzockt.factoryrouting.routing.IngredientRoute;
import dev.failzockt.factoryrouting.routing.PanelRouting;
import dev.failzockt.factoryrouting.routing.RoutingAccess;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Adds embedded routing controls to Create's recipe-panel screen. */
@Mixin(FactoryPanelScreen.class)
public abstract class FactoryPanelScreenMixin implements EmbeddedRoutingInput {

	private static final int BUTTON_SIZE = 8;
	private static final int OVERVIEW_BUTTON_SIZE = 18;

	@Shadow
	private FactoryPanelBehaviour behaviour;
	@Shadow
	private boolean restocker;
	@Shadow
	private boolean craftingActive;
	@Shadow
	private List<FactoryPanelConnection> connections;
	@Unique
	private EmbeddedFactoryRoutingPanel createFactoryRouting$panel;

	@Inject(method = "init", at = @At("TAIL"))
	private void createFactoryRouting$requestRoutes(CallbackInfo ci) {
		if (restocker)
			return;
		FactoryRoutingNetwork.request(behaviour.getPanelPosition());
		createFactoryRouting$panel = new EmbeddedFactoryRoutingPanel(behaviour, connections);
		createFactoryRouting$layoutPanel();
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void createFactoryRouting$tickPanel(CallbackInfo ci) {
		if (createFactoryRouting$panel != null)
			createFactoryRouting$panel.tick();
	}

	@Inject(method = "renderWindow", at = @At("TAIL"))
	private void createFactoryRouting$renderRouteButtons(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks, CallbackInfo ci) {
		if (restocker)
			return;

		Screen screen = (Screen) (Object) this;
		int guiLeft = (screen.width - AllGuiTextures.FACTORY_GAUGE_BOTTOM.getWidth()) / 2;
		int guiTop = (screen.height - AllGuiTextures.FACTORY_GAUGE_RECIPE.getHeight()
			- AllGuiTextures.FACTORY_GAUGE_BOTTOM.getHeight()) / 2;
		createFactoryRouting$layoutPanel();
		PanelRouting routing = RoutingAccess.get(behaviour);
		ButtonBounds overview = overviewBounds(guiLeft, guiTop);
		boolean overviewHovered = overview.contains(mouseX, mouseY, OVERVIEW_BUTTON_SIZE);
		AllGuiTextures overviewTexture = overviewHovered ? AllGuiTextures.BUTTON_HOVER
			: routing.isEmpty() ? AllGuiTextures.BUTTON : AllGuiTextures.BUTTON_GREEN;
		overviewTexture.render(graphics, overview.x(), overview.y());
		AllIcons.I_TUNNEL_SPLIT.render(graphics, overview.x() + 1, overview.y() + 1);
		if (!craftingActive) {
			for (int i = 0; i < connections.size(); i++) {
				ButtonBounds bounds = bounds(guiLeft, guiTop, i);
				IngredientRoute route = routing.get(connections.get(i).from);
				boolean hovered = bounds.contains(mouseX, mouseY);
				int border = route.isOverride() ? 0xFF47C9B0 : 0xFF777B86;
				int fill = hovered ? 0xFF4A4E59 : 0xFF242730;
				graphics.fill(bounds.x(), bounds.y(), bounds.x() + BUTTON_SIZE, bounds.y() + BUTTON_SIZE, border);
				graphics.fill(bounds.x() + 1, bounds.y() + 1, bounds.x() + BUTTON_SIZE - 1,
					bounds.y() + BUTTON_SIZE - 1, fill);
				graphics.drawString(Minecraft.getInstance().font, "R", bounds.x() + 2, bounds.y(),
					0xFFFFFFFF, false);
			}
		}

		if (createFactoryRouting$panel != null)
			createFactoryRouting$panel.render(graphics, mouseX, mouseY, partialTicks);

		if (overviewHovered) {
			Component action = createFactoryRouting$panel != null
				&& createFactoryRouting$panel.isLayoutAvailable()
				? Component.translatable("gui.create_factory_routing.toggle_routing_panels")
				: Component.translatable("gui.create_factory_routing.open_compact_routing");
			graphics.renderComponentTooltip(Minecraft.getInstance().font,
				List.of(Component.translatable("gui.create_factory_routing.routing_overview"),
					action.copy().withStyle(ChatFormatting.DARK_GRAY)), mouseX, mouseY);
		}
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void createFactoryRouting$openRouteScreen(double mouseX, double mouseY, int button,
		CallbackInfoReturnable<Boolean> cir) {
		if (button != 0 || restocker)
			return;
		if (createFactoryRouting$panel != null
			&& createFactoryRouting$panel.mouseClicked(mouseX, mouseY, button)) {
			cir.setReturnValue(true);
			return;
		}

		Screen screen = (Screen) (Object) this;
		int guiLeft = (screen.width - AllGuiTextures.FACTORY_GAUGE_BOTTOM.getWidth()) / 2;
		int guiTop = (screen.height - AllGuiTextures.FACTORY_GAUGE_RECIPE.getHeight()
			- AllGuiTextures.FACTORY_GAUGE_BOTTOM.getHeight()) / 2;
		if (overviewBounds(guiLeft, guiTop).contains(mouseX, mouseY, OVERVIEW_BUTTON_SIZE)) {
			if (createFactoryRouting$panel != null && createFactoryRouting$panel.isLayoutAvailable())
				createFactoryRouting$panel.toggle();
			else
				Minecraft.getInstance().setScreen(new RoutingOverviewScreen(screen, behaviour, connections));
			cir.setReturnValue(true);
			return;
		}
		if (craftingActive)
			return;
		for (int i = 0; i < connections.size(); i++) {
			if (!bounds(guiLeft, guiTop, i).contains(mouseX, mouseY, BUTTON_SIZE))
				continue;
			if (createFactoryRouting$panel != null && createFactoryRouting$panel.isLayoutAvailable())
				createFactoryRouting$panel.openEntry(i);
			else
				Minecraft.getInstance().setScreen(
					new IngredientRouteScreen(screen, behaviour, connections.get(i).from));
			cir.setReturnValue(true);
			return;
		}
	}

	@Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
	private void createFactoryRouting$scrollPanel(double mouseX, double mouseY, double scrollX,
		double scrollY, CallbackInfoReturnable<Boolean> cir) {
		if (createFactoryRouting$panel != null
			&& createFactoryRouting$panel.mouseScrolled(mouseX, mouseY, scrollY))
			cir.setReturnValue(true);
	}

	@Override
	public boolean createFactoryRouting$keyPressed(int keyCode, int scanCode, int modifiers) {
		return createFactoryRouting$panel != null
			&& createFactoryRouting$panel.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean createFactoryRouting$charTyped(char codePoint, int modifiers) {
		return createFactoryRouting$panel != null
			&& createFactoryRouting$panel.charTyped(codePoint, modifiers);
	}

	@Unique
	private void createFactoryRouting$layoutPanel() {
		if (createFactoryRouting$panel == null)
			return;
		Screen screen = (Screen) (Object) this;
		int recipeHeight = AllGuiTextures.FACTORY_GAUGE_RECIPE.getHeight();
		int bottomWidth = AllGuiTextures.FACTORY_GAUGE_BOTTOM.getWidth();
		int bottomHeight = AllGuiTextures.FACTORY_GAUGE_BOTTOM.getHeight();
		int guiLeft = (screen.width - bottomWidth) / 2;
		int guiTop = (screen.height - recipeHeight - bottomHeight) / 2;
		createFactoryRouting$panel.layout(screen.width, screen.height, guiLeft, guiTop,
			bottomWidth, recipeHeight + bottomHeight);
	}

	private static ButtonBounds bounds(int guiLeft, int guiTop, int index) {
		int inputX = guiLeft + 68 + (index % 3 * 20);
		int inputY = guiTop + 28 + (index / 3 * 20);
		return new ButtonBounds(inputX + 12, inputY - 4);
	}

	private static ButtonBounds overviewBounds(int guiLeft, int guiTop) {
		return new ButtonBounds(guiLeft + 11, guiTop + 67);
	}

	private record ButtonBounds(int x, int y) {

		boolean contains(double mouseX, double mouseY) {
			return contains(mouseX, mouseY, BUTTON_SIZE);
		}

		boolean contains(double mouseX, double mouseY, int size) {
			return mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size;
		}
	}
}

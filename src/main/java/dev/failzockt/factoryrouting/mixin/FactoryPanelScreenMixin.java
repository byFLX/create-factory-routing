package dev.failzockt.factoryrouting.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelScreen;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;

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

/** Adds a compact routing control to every ingredient tile of Create's recipe-panel screen. */
@Mixin(FactoryPanelScreen.class)
public abstract class FactoryPanelScreenMixin {

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

	@Inject(method = "init", at = @At("TAIL"))
	private void createFactoryRouting$requestRoutes(CallbackInfo ci) {
		if (!restocker)
			FactoryRoutingNetwork.request(behaviour.getPanelPosition());
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
		PanelRouting routing = RoutingAccess.get(behaviour);
		ButtonBounds overview = overviewBounds(guiLeft, guiTop);
		boolean overviewHovered = overview.contains(mouseX, mouseY, OVERVIEW_BUTTON_SIZE);
		AllGuiTextures overviewTexture = overviewHovered ? AllGuiTextures.BUTTON_HOVER
			: routing.isEmpty() ? AllGuiTextures.BUTTON : AllGuiTextures.BUTTON_GREEN;
		overviewTexture.render(graphics, overview.x(), overview.y());
		AllIcons.I_TUNNEL_SPLIT.render(graphics, overview.x() + 1, overview.y() + 1);
		if (overviewHovered)
			graphics.renderComponentTooltip(Minecraft.getInstance().font,
				List.of(Component.translatable("gui.create_factory_routing.routing_overview"),
					Component.translatable("gui.create_factory_routing.open_routing_overview")
						.withStyle(ChatFormatting.DARK_GRAY)),
				mouseX, mouseY);

		if (craftingActive)
			return;

		for (int i = 0; i < connections.size(); i++) {
			ButtonBounds bounds = bounds(guiLeft, guiTop, i);
			IngredientRoute route = routing.get(connections.get(i).from);
			boolean hovered = bounds.contains(mouseX, mouseY);
			int border = route.isOverride() ? 0xFF47C9B0 : 0xFF777B86;
			int fill = hovered ? 0xFF4A4E59 : 0xFF242730;
			graphics.fill(bounds.x(), bounds.y(), bounds.x() + BUTTON_SIZE, bounds.y() + BUTTON_SIZE, border);
			graphics.fill(bounds.x() + 1, bounds.y() + 1, bounds.x() + BUTTON_SIZE - 1,
				bounds.y() + BUTTON_SIZE - 1, fill);
			graphics.drawString(Minecraft.getInstance().font, "R", bounds.x() + 2, bounds.y(), 0xFFFFFFFF, false);

			if (hovered) {
				String effective = routing.resolve(connections.get(i).from, behaviour.recipeAddress);
				Component routeLine = route.isOverride()
					? Component.translatable("gui.create_factory_routing.route_override", effective)
						.withStyle(ChatFormatting.AQUA)
					: Component.translatable("gui.create_factory_routing.route_inherited", effective)
						.withStyle(ChatFormatting.GRAY);
				graphics.renderComponentTooltip(Minecraft.getInstance().font,
					List.of(Component.translatable("gui.create_factory_routing.ingredient_routing"), routeLine,
						Component.translatable("gui.create_factory_routing.click_to_configure")
							.withStyle(ChatFormatting.DARK_GRAY)),
					mouseX, mouseY);
			}
		}
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void createFactoryRouting$openRouteScreen(double mouseX, double mouseY, int button,
		CallbackInfoReturnable<Boolean> cir) {
		if (button != 0 || restocker)
			return;

		Screen screen = (Screen) (Object) this;
		int guiLeft = (screen.width - AllGuiTextures.FACTORY_GAUGE_BOTTOM.getWidth()) / 2;
		int guiTop = (screen.height - AllGuiTextures.FACTORY_GAUGE_RECIPE.getHeight()
			- AllGuiTextures.FACTORY_GAUGE_BOTTOM.getHeight()) / 2;
		if (overviewBounds(guiLeft, guiTop).contains(mouseX, mouseY, OVERVIEW_BUTTON_SIZE)) {
			Minecraft.getInstance().setScreen(new RoutingOverviewScreen(screen, behaviour, connections));
			cir.setReturnValue(true);
			return;
		}
		if (craftingActive)
			return;
		for (int i = 0; i < connections.size(); i++) {
			if (!bounds(guiLeft, guiTop, i).contains(mouseX, mouseY, BUTTON_SIZE))
				continue;
			Minecraft.getInstance().setScreen(new IngredientRouteScreen(screen, behaviour, connections.get(i).from));
			cir.setReturnValue(true);
			return;
		}
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

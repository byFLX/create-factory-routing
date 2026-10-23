package dev.failzockt.factoryrouting.mixin.compat;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.failzockt.factoryrouting.client.EmbeddedVirtualRoutingPanel;
import dev.failzockt.factoryrouting.client.VirtualIngredientRouteScreen;
import dev.failzockt.factoryrouting.client.VirtualRoutingOverviewScreen;
import dev.failzockt.factoryrouting.compat.factorycontroller.VirtualRoutingAccess;
import dev.failzockt.factoryrouting.routing.IngredientRoute;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import io.github.nbcss.createfactorycontroller.content.GaugeWorkMode;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.gauge.VirtualGaugeBehaviour;
import io.github.nbcss.createfactorycontroller.content.gui.screen.recipe.ConfigureRecipeScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Adds the embedded routing interface to Factory Controller's recipe editor. */
@Pseudo
@Mixin(targets = "io.github.nbcss.createfactorycontroller.content.gui.screen.recipe.ConfigureRecipeScreen")
public abstract class FactoryControllerScreenMixin {

	private static final int BUTTON_SIZE = 18;
	private static final int SHORTCUT_SIZE = 8;
	private static final int PANEL_WIDTH = 200;
	private static final int PANEL_HEIGHT = 184;

	@Shadow
	int panelX;
	@Shadow
	int panelY;
	@Shadow
	@Final
	private VirtualComponentPosition gaugePos;
	@Shadow
	@Final
	private List<VirtualComponentPosition> inputConnections;
	@Shadow
	@Final
	private List<Integer> inputTotals;
	@Shadow
	GaugeWorkMode workMode;
	@Unique
	private EmbeddedVirtualRoutingPanel createFactoryRouting$panel;

	@Shadow
	abstract boolean isFluidConn(int index);

	@Shadow
	abstract ItemStack ingredientOf(VirtualComponentPosition position);

	@Inject(method = "init", at = @At("TAIL"))
	private void createFactoryRouting$initPanel(CallbackInfo ci) {
		ConfigureRecipeScreen screen = (ConfigureRecipeScreen) (Object) this;
		FactoryControllerMenu menu = screen.getMenu();
		createFactoryRouting$panel = new EmbeddedVirtualRoutingPanel(menu, menu.controllerPos,
			gaugePos, inputConnections);
		createFactoryRouting$layoutPanel();
	}

	@Inject(method = "containerTick", at = @At("TAIL"))
	private void createFactoryRouting$tickPanel(CallbackInfo ci) {
		if (createFactoryRouting$panel != null)
			createFactoryRouting$panel.tick();
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void createFactoryRouting$renderButton(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks, CallbackInfo ci) {
		createFactoryRouting$layoutPanel();
		int x = panelX + 11;
		int y = panelY + 67;
		boolean hovered = contains(mouseX, mouseY, x, y);
		ConfigureRecipeScreen screen = (ConfigureRecipeScreen) (Object) this;
		FactoryControllerMenu menu = screen.getMenu();
		boolean configured = menu.componentAt(gaugePos) instanceof VirtualGaugeBehaviour gauge
			&& !VirtualRoutingAccess.get(gauge).isEmpty();
		AllGuiTextures buttonTexture = hovered ? AllGuiTextures.BUTTON_HOVER
			: configured ? AllGuiTextures.BUTTON_GREEN : AllGuiTextures.BUTTON;
		buttonTexture.render(graphics, x, y);
		AllIcons.I_TUNNEL_SPLIT.render(graphics, x + 1, y + 1);

		VirtualGaugeBehaviour targetGauge = menu.componentAt(gaugePos) instanceof VirtualGaugeBehaviour value
			? value : null;
		for (int i = 0; i < inputConnections.size(); i++) {
			int displaySlot = createFactoryRouting$displaySlot(i);
			if (displaySlot < 0)
				continue;
			int shortcutX = createFactoryRouting$shortcutX(displaySlot);
			int shortcutY = createFactoryRouting$shortcutY(displaySlot);
			IngredientRoute route = targetGauge == null ? IngredientRoute.NONE
				: VirtualRoutingAccess.get(targetGauge).get(inputConnections.get(i));
			boolean shortcutHovered = contains(mouseX, mouseY, shortcutX, shortcutY, SHORTCUT_SIZE);
			int border = route.isOverride() ? 0xFF47C9B0 : 0xFF777B86;
			int fill = shortcutHovered ? 0xFF4A4E59 : 0xFF242730;
			graphics.fill(shortcutX, shortcutY, shortcutX + SHORTCUT_SIZE, shortcutY + SHORTCUT_SIZE, border);
			graphics.fill(shortcutX + 1, shortcutY + 1, shortcutX + SHORTCUT_SIZE - 1,
				shortcutY + SHORTCUT_SIZE - 1, fill);
			graphics.drawString(Minecraft.getInstance().font, "R", shortcutX + 2, shortcutY,
				0xFFFFFFFF, false);
		}

		if (createFactoryRouting$panel != null)
			createFactoryRouting$panel.render(graphics, mouseX, mouseY, partialTicks);

		if (hovered) {
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
	private void createFactoryRouting$openOverview(double mouseX, double mouseY, int button,
		CallbackInfoReturnable<Boolean> cir) {
		if (button != 0)
			return;
		if (createFactoryRouting$panel != null
			&& createFactoryRouting$panel.mouseClicked(mouseX, mouseY, button)) {
			cir.setReturnValue(true);
			return;
		}

		int x = panelX + 11;
		int y = panelY + 67;
		ConfigureRecipeScreen screen = (ConfigureRecipeScreen) (Object) this;
		FactoryControllerMenu menu = screen.getMenu();
		if (contains(mouseX, mouseY, x, y)) {
			if (createFactoryRouting$panel != null && createFactoryRouting$panel.isLayoutAvailable())
				createFactoryRouting$panel.toggle();
			else
				Minecraft.getInstance().setScreen(new VirtualRoutingOverviewScreen((Screen) (Object) this,
					menu, menu.controllerPos, gaugePos, inputConnections));
			cir.setReturnValue(true);
			return;
		}

		for (int i = 0; i < inputConnections.size(); i++) {
			int displaySlot = createFactoryRouting$displaySlot(i);
			if (displaySlot < 0 || !contains(mouseX, mouseY,
				createFactoryRouting$shortcutX(displaySlot), createFactoryRouting$shortcutY(displaySlot),
				SHORTCUT_SIZE))
				continue;
			if (createFactoryRouting$panel != null && createFactoryRouting$panel.isLayoutAvailable())
				createFactoryRouting$panel.openEntry(i);
			else
				Minecraft.getInstance().setScreen(new VirtualIngredientRouteScreen((Screen) (Object) this,
					menu, menu.controllerPos, gaugePos, inputConnections.get(i)));
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

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void createFactoryRouting$keyPressed(int keyCode, int scanCode, int modifiers,
		CallbackInfoReturnable<Boolean> cir) {
		if (createFactoryRouting$panel != null
			&& createFactoryRouting$panel.keyPressed(keyCode, scanCode, modifiers))
			cir.setReturnValue(true);
	}

	@Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
	private void createFactoryRouting$charTyped(char codePoint, int modifiers,
		CallbackInfoReturnable<Boolean> cir) {
		if (createFactoryRouting$panel != null
			&& createFactoryRouting$panel.charTyped(codePoint, modifiers))
			cir.setReturnValue(true);
	}

	@Unique
	private void createFactoryRouting$layoutPanel() {
		if (createFactoryRouting$panel == null)
			return;
		Screen screen = (Screen) (Object) this;
		createFactoryRouting$panel.layout(screen.width, screen.height, panelX, panelY,
			PANEL_WIDTH, PANEL_HEIGHT);
	}

	@Inject(method = "getExtraAreas", at = @At("RETURN"), cancellable = true)
	private void createFactoryRouting$addJeiExclusionAreas(CallbackInfoReturnable<List<Rect2i>> cir) {
		if (createFactoryRouting$panel == null)
			return;
		List<Rect2i> panelAreas = createFactoryRouting$panel.extraAreas();
		if (panelAreas.isEmpty())
			return;
		List<Rect2i> areas = new ArrayList<>(cir.getReturnValue());
		areas.addAll(panelAreas);
		cir.setReturnValue(areas);
	}

	@Unique
	private int createFactoryRouting$displaySlot(int connectionIndex) {
		if (workMode != GaugeWorkMode.REGULAR)
			return -1;
		int slot = 0;
		for (int i = 0; i < connectionIndex; i++) {
			if (isFluidConn(i)) {
				slot++;
				continue;
			}
			ItemStack ingredient = ingredientOf(inputConnections.get(i));
			int stackSize = Math.max(1, ingredient.getMaxStackSize());
			int amount = Math.max(1, inputTotals.get(i));
			slot += (amount + stackSize - 1) / stackSize;
		}
		return slot < 9 ? slot : -1;
	}

	@Unique
	private int createFactoryRouting$shortcutX(int displaySlot) {
		return panelX + 80 + displaySlot % 3 * 20;
	}

	@Unique
	private int createFactoryRouting$shortcutY(int displaySlot) {
		return panelY + 24 + displaySlot / 3 * 20;
	}

	private static boolean contains(double mouseX, double mouseY, int x, int y) {
		return contains(mouseX, mouseY, x, y, BUTTON_SIZE);
	}

	private static boolean contains(double mouseX, double mouseY, int x, int y, int size) {
		return mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size;
	}
}

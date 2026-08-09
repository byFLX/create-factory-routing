package dev.failzockt.factoryrouting.mixin.compat;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.failzockt.factoryrouting.client.VirtualRoutingOverviewScreen;
import dev.failzockt.factoryrouting.compat.factorycontroller.VirtualRoutingAccess;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.VirtualGaugeBehaviour;
import io.github.nbcss.createfactorycontroller.content.gui.screen.recipe.ConfigureRecipeScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Adds the same routing overview button to Factory Controller's recipe editor. */
@Pseudo
@Mixin(targets = "io.github.nbcss.createfactorycontroller.content.gui.screen.recipe.ConfigureRecipeScreen")
public abstract class FactoryControllerScreenMixin {

	private static final int BUTTON_SIZE = 18;

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

	@Inject(method = "render", at = @At("TAIL"))
	private void createFactoryRouting$renderButton(GuiGraphics graphics, int mouseX, int mouseY,
		float partialTicks, CallbackInfo ci) {
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
		if (hovered)
			graphics.renderComponentTooltip(Minecraft.getInstance().font,
				List.of(Component.translatable("gui.create_factory_routing.routing_overview"),
					Component.translatable("gui.create_factory_routing.open_routing_overview")
						.withStyle(ChatFormatting.DARK_GRAY)), mouseX, mouseY);
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void createFactoryRouting$openOverview(double mouseX, double mouseY, int button,
		CallbackInfoReturnable<Boolean> cir) {
		int x = panelX + 11;
		int y = panelY + 67;
		if (button != 0 || !contains(mouseX, mouseY, x, y))
			return;

		ConfigureRecipeScreen screen = (ConfigureRecipeScreen) (Object) this;
		FactoryControllerMenu menu = screen.getMenu();
		Minecraft.getInstance().setScreen(new VirtualRoutingOverviewScreen((Screen) (Object) this,
			menu, menu.controllerPos, gaugePos, inputConnections));
		cir.setReturnValue(true);
	}

	private static boolean contains(double mouseX, double mouseY, int x, int y) {
		return mouseX >= x && mouseX < x + BUTTON_SIZE && mouseY >= y && mouseY < y + BUTTON_SIZE;
	}
}

package dev.failzockt.factoryrouting.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;

import dev.failzockt.factoryrouting.dispatch.RoutedDispatch;

/**
 * Hands recipe requests over to {@link RoutedDispatch} when the panel has per-ingredient address
 * overrides. Panels without overrides run entirely through Create's own code.
 *
 * <p>The injection sits after {@code resetTimer()} on purpose: every early return and the timer
 * countdown have already run at that point, so cancelling cannot leave the panel spinning, and we
 * are still ahead of the {@code recipeAddress.isBlank()} guard that we need to relax. See
 * SPEC.md §6.2.
 */
@Mixin(FactoryPanelBehaviour.class)
public abstract class FactoryPanelBehaviourMixin {

	@Inject(
		method = "tickRequests",
		at = @At(
			value = "INVOKE",
			target = "Lcom/simibubi/create/content/logistics/factoryBoard/FactoryPanelBehaviour;resetTimer()V",
			shift = At.Shift.AFTER),
		cancellable = true)
	private void createFactoryRouting$routedDispatch(CallbackInfo ci) {
		if (RoutedDispatch.handle((FactoryPanelBehaviour) (Object) this))
			ci.cancel();
	}
}

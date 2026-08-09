package dev.failzockt.factoryrouting.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;

/** Exposes the controller field from Factory Controller's component base class. */
@Pseudo
@Mixin(targets = "io.github.nbcss.createfactorycontroller.content.component.AbstractVirtualComponent")
public interface FactoryControllerComponentAccessor {

	@Accessor("controller")
	FactoryControllerBlockEntity createFactoryRouting$getOwningController();
}

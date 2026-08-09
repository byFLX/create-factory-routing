package dev.failzockt.factoryrouting.compat.factorycontroller;

import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;

/** Implemented on Factory Controller virtual gauges by the compatibility mixin. */
public interface VirtualRoutingHolder {

	VirtualPanelRouting createFactoryRouting$getRouting();

	void createFactoryRouting$setRouting(VirtualPanelRouting routing);

	FactoryControllerBlockEntity createFactoryRouting$getController();
}

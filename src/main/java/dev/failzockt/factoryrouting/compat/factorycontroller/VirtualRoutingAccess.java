package dev.failzockt.factoryrouting.compat.factorycontroller;

import io.github.nbcss.createfactorycontroller.content.component.VirtualGaugeBehaviour;

/** Safe access to the routing field mixed into Factory Controller's virtual gauges. */
public final class VirtualRoutingAccess {

	private VirtualRoutingAccess() {
	}

	public static VirtualPanelRouting get(VirtualGaugeBehaviour gauge) {
		return gauge instanceof VirtualRoutingHolder holder
			? holder.createFactoryRouting$getRouting()
			: VirtualPanelRouting.EMPTY;
	}

	public static void set(VirtualGaugeBehaviour gauge, VirtualPanelRouting routing) {
		if (gauge instanceof VirtualRoutingHolder holder)
			holder.createFactoryRouting$setRouting(routing);
	}
}

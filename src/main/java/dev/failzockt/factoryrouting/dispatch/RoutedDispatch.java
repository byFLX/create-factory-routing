package dev.failzockt.factoryrouting.dispatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import com.google.common.collect.Multimap;
import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour.ItemStackConnections;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelEffectPacket;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour.RequestType;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.packagerLink.RequestPromise;
import com.simibubi.create.content.logistics.packagerLink.RequestPromiseQueue;
import com.simibubi.create.content.logistics.stockTicker.PackageOrder;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.foundation.advancement.AllAdvancements;

import dev.failzockt.factoryrouting.routing.PanelRouting;
import dev.failzockt.factoryrouting.routing.RoutingAccess;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
import net.minecraft.world.level.Level;

/**
 * Recipe request dispatch with per-ingredient target addresses.
 *
 * <p>This mirrors {@code FactoryPanelBehaviour.tickRequests()} from the published Create
 * {@code 1.21.1-6.0.10-281} artifact with exactly two changes:
 *
 * <ol>
 * <li>ingredients are split into {@code (network, address)} buckets before dispatch instead of
 * going out as one order per network, and</li>
 * <li>a {@link StockLedger} keeps the planning passes of those buckets from claiming the same
 * physical items twice.</li>
 * </ol>
 *
 * <p>Availability checking, the busy check, execution and the output promise are deliberately
 * unchanged. See SPEC.md §6.
 *
 * <p><b>When updating Create, diff {@code tickRequests} against this class.</b>
 */
public final class RoutedDispatch {

	private RoutedDispatch() {
	}

	/**
	 * @return {@code true} when this class has taken over the request cycle and the caller must
	 *         cancel Create's own logic, {@code false} to let Create proceed untouched
	 */
	public static boolean handle(FactoryPanelBehaviour behaviour) {
		FactoryPanelBlockEntity panelBE = behaviour.panelBE();

		// Restocker panels have a single target by definition -- not our business.
		if (panelBE.restocker)
			return false;

		RoutingAccess.prune(behaviour);
		PanelRouting routing = RoutingAccess.get(panelBE, behaviour.slot);

		// Passthrough: without overrides Create's own code stays in charge, bit for bit.
		if (routing.isEmpty())
			return false;

		Level level = behaviour.getWorld();
		if (level == null)
			return false;

		// 1. Aggregate ingredients by (network, item). Mirrors tickRequests:430-445.
		Map<UUID, Map<ItemStack, ItemStackConnections>> consolidated = new HashMap<>();
		for (FactoryPanelConnection connection : behaviour.targetedBy.values()) {
			FactoryPanelBehaviour source = FactoryPanelBehaviour.at(level, connection);
			if (source == null)
				return true;

			ItemStack item = source.getFilter();
			Map<ItemStack, ItemStackConnections> perItem = consolidated.computeIfAbsent(source.network,
				$ -> new Object2ObjectOpenCustomHashMap<>(ItemStackLinkedSet.TYPE_AND_TAG));
			ItemStackConnections group = perItem.computeIfAbsent(item, ItemStackConnections::new);
			group.add(connection);
			group.totalAmount += connection.amount;
		}

		// 2. Check availability on the aggregate, never per address. Mirrors tickRequests:449-469.
		// Splitting first would let two links for the same item each pass their own check and
		// overcommit the network between them. See SPEC.md §6.
		boolean failed = false;
		for (Entry<UUID, Map<ItemStack, ItemStackConnections>> networkEntry : consolidated.entrySet()) {
			InventorySummary summary = LogisticsManager.getSummaryOfNetwork(networkEntry.getKey(), true);
			for (ItemStackConnections group : networkEntry.getValue()
				.values()) {
				boolean available = group.totalAmount > 0 && !group.item.isEmpty()
					&& summary.getCountOf(group.item) >= group.totalAmount;
				for (FactoryPanelConnection connection : group)
					sendEffect(behaviour, connection.from, available);
				if (!available)
					failed = true;
			}
		}

		if (failed)
			return true;

		// 3. Split into (network, address) buckets. This is the actual feature.
		Map<UUID, Map<String, List<BigItemStack>>> buckets = new LinkedHashMap<>();
		for (Entry<UUID, Map<ItemStack, ItemStackConnections>> networkEntry : consolidated.entrySet()) {
			Map<String, List<BigItemStack>> perAddress =
				buckets.computeIfAbsent(networkEntry.getKey(), $ -> new LinkedHashMap<>());

			for (ItemStackConnections group : networkEntry.getValue()
				.values()) {
				Map<String, Integer> amountPerAddress = new LinkedHashMap<>();
				for (FactoryPanelConnection connection : group) {
					String address = routing.resolve(connection.from, behaviour.recipeAddress);

					// An ingredient with no resolvable address aborts the whole craft rather than
					// letting a partial recipe reach the machine. SPEC.md §1.
					if (address.isBlank())
						return true;

					amountPerAddress.merge(address, connection.amount, Integer::sum);
				}
				amountPerAddress.forEach((address, amount) -> perAddress
					.computeIfAbsent(address, $ -> new ArrayList<>())
					.add(new BigItemStack(group.item, amount)));
			}
		}

		// Panel may enforce item arrangement. Mirrors tickRequests:476-480.
		PackageOrderWithCrafts craftingContext = PackageOrderWithCrafts.empty();
		if (!behaviour.activeCraftingArrangement.isEmpty())
			craftingContext = PackageOrderWithCrafts.singleRecipe(behaviour.activeCraftingArrangement.stream()
				.map(stack -> new BigItemStack(stack.copyWithCount(1)))
				.toList());

		List<Multimap<PackagerBlockEntity, PackagingRequest>> plans = new ArrayList<>();
		StockLedger ledger = new StockLedger();

		try {
			// 4. Plan every bucket. findPackagersForRequest does not reserve anything, so without
			// the ledger two buckets on the same network would plan the same items twice.
			// SPEC.md §6.1.
			for (Entry<UUID, Map<String, List<BigItemStack>>> networkEntry : buckets.entrySet()) {
				UUID network = networkEntry.getKey();
				for (Entry<String, List<BigItemStack>> bucket : networkEntry.getValue()
					.entrySet()) {
					PackageOrderWithCrafts order = new PackageOrderWithCrafts(
						new PackageOrder(new ArrayList<>(bucket.getValue())), craftingContext.orderedCrafts());
					Multimap<PackagerBlockEntity, PackagingRequest> plan =
						LogisticsManager.findPackagersForRequest(network, order, null, bucket.getKey());
					plans.add(plan);
					ledger.reserve(plan);
				}
			}

			// 5. Cancel everything if any packager is busy. Mirrors tickRequests:492-495.
			for (Multimap<PackagerBlockEntity, PackagingRequest> plan : plans)
				for (PackagerBlockEntity packager : plan.keySet())
					if (packager.isTooBusyFor(RequestType.RESTOCK))
						return true;

		} finally {
			// Release before sending: the real deduction happens inside performPackageRequests.
			ledger.release();
		}

		// 6. Send. Mirrors tickRequests:498-499.
		for (Multimap<PackagerBlockEntity, PackagingRequest> plan : plans)
			LogisticsManager.performPackageRequests(plan);

		// 7. Keep the output promise -- exactly one, on the panel's own output item. Splitting
		// ingredients across addresses does not affect promise accounting. Mirrors :502-504.
		RequestPromiseQueue promises = Create.LOGISTICS.getQueuedPromises(behaviour.network);
		if (promises != null)
			promises.add(new RequestPromise(new BigItemStack(behaviour.getFilter(), behaviour.recipeOutput)));

		panelBE.advancements.awardPlayer(AllAdvancements.FACTORY_GAUGE);
		return true;
	}

	private static void sendEffect(FactoryPanelBehaviour behaviour, FactoryPanelPosition from, boolean success) {
		if (behaviour.getWorld() instanceof ServerLevel serverLevel)
			CatnipServices.NETWORK.sendToClientsAround(serverLevel, behaviour.getPos(), 64,
				new FactoryPanelEffectPacket(from, behaviour.getPanelPosition(), success));
	}

}

package dev.failzockt.factoryrouting.compat.factorycontroller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.IdentifiedInventory;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.stockTicker.PackageOrder;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;

import dev.failzockt.factoryrouting.dispatch.RoutedPlanner;

import io.github.nbcss.createfactorycontroller.content.GaugeWorkMode;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.component.RecipeSlot;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.VirtualGaugeBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.component.connection.LogisticsConnection;
import net.minecraft.world.item.ItemStack;

/** Splits Factory Controller's already-validated item order into per-address planning passes. */
public final class VirtualRoutedDispatch {

	private VirtualRoutedDispatch() {
	}

	public static Multimap<PackagerBlockEntity, PackagingRequest> plan(VirtualGaugeBehaviour gauge,
		FactoryControllerBlockEntity controller, UUID network, PackageOrderWithCrafts order,
		IdentifiedInventory excludedInventory, String defaultAddress) {
		VirtualPanelRouting routing = VirtualRoutingAccess.get(gauge);
		if (routing.isEmpty() || controller == null)
			return LogisticsManager.findPackagersForRequest(network, order, excludedInventory, defaultAddress);

		List<SourceRoute> sources = sourcesFor(gauge, controller, network, routing, defaultAddress);
		if (sources.isEmpty())
			return LogisticsManager.findPackagersForRequest(network, order, excludedInventory, defaultAddress);
		if (sources.stream().anyMatch(source -> source.address().isBlank()))
			return ArrayListMultimap.create();

		Map<String, List<BigItemStack>> buckets = gauge.mode == GaugeWorkMode.CUSTOM
			? splitCustom(gauge, order, sources, defaultAddress)
			: splitMerged(gauge, order, sources, defaultAddress);
		buckets.entrySet().removeIf(entry -> !hasDemand(entry.getValue()));
		if (buckets.isEmpty())
			return LogisticsManager.findPackagersForRequest(network, order, excludedInventory, defaultAddress);

		// One walk over the network's links covering every address, so two addresses can never be
		// planned against the same physical stock. See SPEC.md §6.1.
		List<RoutedPlanner.AddressOrder> addressOrders = new ArrayList<>();
		for (Map.Entry<String, List<BigItemStack>> bucket : buckets.entrySet())
			addressOrders.add(new RoutedPlanner.AddressOrder(bucket.getKey(),
				new PackageOrderWithCrafts(new PackageOrder(bucket.getValue()), order.orderedCrafts())));

		RoutedPlanner.Plan plan = RoutedPlanner.plan(network, addressOrders, excludedInventory);

		// An incomplete plan aborts the cycle: Factory Controller sends nothing for an empty result.
		if (!plan.complete())
			return ArrayListMultimap.create();

		// The redirect must hand back a single multimap, so the per-address plans are flattened
		// block by block to keep every order's requests contiguous per packager.
		return RoutedPlanner.mergeContiguously(plan.addressPlans());
	}

	private static List<SourceRoute> sourcesFor(VirtualGaugeBehaviour gauge,
		FactoryControllerBlockEntity controller, UUID network, VirtualPanelRouting routing, String defaultAddress) {
		List<SourceRoute> sources = new ArrayList<>();
		for (Map.Entry<VirtualComponentPosition, Connection> entry : gauge.targetedBy().entrySet()) {
			if (!(entry.getValue() instanceof LogisticsConnection connection))
				continue;
			if (!(controller.components.get(entry.getKey()) instanceof VirtualGaugeBehaviour source))
				continue;
			if (!network.equals(source.networkId) || source.filter.isEmpty())
				continue;
			sources.add(new SourceRoute(entry.getKey(), source, connection.amount(),
				routing.resolve(entry.getKey(), defaultAddress)));
		}
		return sources;
	}

	/**
	 * Distributes an already-merged order back onto addresses, weighted by how much each source
	 * link contributes.
	 *
	 * <p>Unlike the plain Create path, where every ingredient link carries its own exact amount,
	 * Factory Controller has already summed identical items across links by the time we see the
	 * order. The split is therefore exact only while the requested count equals the sum of the
	 * contributing link amounts — the normal case. When Factory Controller asks for less than full
	 * demand (partial restock), the remainder is apportioned proportionally and the last bucket
	 * absorbs the rounding, which is a heuristic rather than a reconstruction of the original
	 * per-link amounts.
	 */
	private static Map<String, List<BigItemStack>> splitMerged(VirtualGaugeBehaviour gauge,
		PackageOrderWithCrafts order, List<SourceRoute> sources, String defaultAddress) {
		Map<SourceRoute, Integer> craftingWeights = craftingWeights(gauge, sources);
		Map<String, List<BigItemStack>> buckets = new LinkedHashMap<>();
		for (BigItemStack item : order.stacks()) {
			if (item.stack.isEmpty() || item.count <= 0) {
				buckets.computeIfAbsent(defaultAddress, ignored -> new ArrayList<>()).add(copy(item, item.count));
				continue;
			}

			Map<String, Integer> weights = new LinkedHashMap<>();
			for (SourceRoute source : sources) {
				if (!matches(source.gauge(), item.stack))
					continue;
				int weight = gauge.mode == GaugeWorkMode.CRAFTING
					? craftingWeights.getOrDefault(source, 0) : source.amount();
				if (weight > 0)
					weights.merge(source.address(), weight, Integer::sum);
			}
			if (weights.isEmpty()) {
				buckets.computeIfAbsent(defaultAddress, ignored -> new ArrayList<>()).add(copy(item, item.count));
				continue;
			}

			int totalWeight = weights.values().stream().mapToInt(Integer::intValue).sum();
			int remainingCount = item.count;
			int remainingWeight = totalWeight;
			int index = 0;
			for (Map.Entry<String, Integer> route : weights.entrySet()) {
				int amount = ++index == weights.size()
					? remainingCount
					: Math.min(remainingCount, (int) ((long) item.count * route.getValue() / totalWeight));
				if (amount <= 0 && remainingCount > 0 && route.getValue() == remainingWeight)
					amount = remainingCount;
				remainingCount -= amount;
				remainingWeight -= route.getValue();
				if (amount > 0)
					buckets.computeIfAbsent(route.getKey(), ignored -> new ArrayList<>()).add(copy(item, amount));
			}
		}
		return buckets;
	}

	private static Map<String, List<BigItemStack>> splitCustom(VirtualGaugeBehaviour gauge,
		PackageOrderWithCrafts order, List<SourceRoute> sources, String defaultAddress) {
		Map<VirtualComponentPosition, String> addressBySource = new LinkedHashMap<>();
		sources.forEach(source -> addressBySource.put(source.position(), source.address()));
		Map<String, List<BigItemStack>> buckets = new LinkedHashMap<>();
		List<BigItemStack> original = order.stacks();

		for (int i = 0; i < original.size(); i++) {
			BigItemStack item = original.get(i);
			if (item.count <= 0)
				continue;
			String address = defaultAddress;
			if (i < gauge.recipeSlots.size()) {
				RecipeSlot slot = gauge.recipeSlots.get(i);
				if (!slot.isEmpty())
					address = addressBySource.getOrDefault(slot.source(), defaultAddress);
			}
			List<BigItemStack> target = customBucket(buckets, address, original.size());
			target.set(i, copy(item, item.count));
		}

		// Factory Controller appends a zero-count arrangement marker. Every routed package needs it,
		// while ordinary empty cells remain the pre-filled placeholders at their original indices.
		for (int i = 0; i < original.size(); i++) {
			BigItemStack item = original.get(i);
			if (item.count <= 0 && !item.stack.isEmpty())
				for (List<BigItemStack> bucket : buckets.values())
					bucket.set(i, copy(item, item.count));
		}
		return buckets;
	}

	private static List<BigItemStack> customBucket(Map<String, List<BigItemStack>> buckets,
		String address, int size) {
		return buckets.computeIfAbsent(address, ignored -> {
			List<BigItemStack> slots = new ArrayList<>(size);
			for (int i = 0; i < size; i++)
				slots.add(new BigItemStack(ItemStack.EMPTY, 0));
			return slots;
		});
	}

	private static Map<SourceRoute, Integer> craftingWeights(VirtualGaugeBehaviour gauge,
		List<SourceRoute> sources) {
		Map<SourceRoute, Integer> weights = new LinkedHashMap<>();
		if (gauge.mode != GaugeWorkMode.CRAFTING)
			return weights;
		for (ItemStack cell : gauge.activeCraftingArrangement) {
			if (cell.isEmpty())
				continue;
			for (SourceRoute source : sources)
				if (matches(source.gauge(), cell)) {
					weights.merge(source, 1, Integer::sum);
					break;
				}
		}
		return weights;
	}

	private static boolean matches(VirtualGaugeBehaviour source, ItemStack requested) {
		return source.ignoreData
			? source.filter.is(requested.getItem())
			: ItemStack.isSameItemSameComponents(source.filter, requested);
	}

	private static BigItemStack copy(BigItemStack source, int count) {
		return new BigItemStack(source.stack.isEmpty() ? ItemStack.EMPTY : source.stack.copyWithCount(1), count);
	}

	private static boolean hasDemand(List<BigItemStack> items) {
		return items.stream().anyMatch(item -> !item.stack.isEmpty() && item.count > 0);
	}

	private record SourceRoute(VirtualComponentPosition position, VirtualGaugeBehaviour gauge,
		int amount, String address) {
	}
}

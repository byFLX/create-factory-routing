/*
 * Portions of this file are adapted from Create, specifically from
 * LogisticsManager.findPackagersForRequest(). The link selection and the per-address request
 * construction deliberately reproduce Create's own logic so that package grouping, defragmentation
 * and order completion behave identically.
 *
 * Create — Copyright (c) The Create Team / The Creators of Create
 * Source:  https://github.com/Creators-of-Create/Create
 * License: MIT — https://github.com/Creators-of-Create/Create/blob/mc1.21.1/dev/LICENSE.md
 *
 * See THIRD_PARTY_NOTICES.md for the complete notice.
 */
package dev.failzockt.factoryrouting.dispatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jetbrains.annotations.Nullable;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.simibubi.create.api.packager.InventoryIdentifier;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.IdentifiedInventory;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;

/**
 * Plans several target addresses against one logistics network in a single walk over its links.
 *
 * <p>Replaces {@code LogisticsManager.findPackagersForRequest} for the routed dispatch path.
 * Create's own method plans exactly one address per call and reads
 * {@code packager.getAvailableItems()} without reserving anything. Calling it once per address
 * therefore lets two addresses claim the same physical stock: each call sees the full inventory,
 * both pick the same packager, and one address is silently under-delivered while stock sits
 * untouched on another packager. Because the panel has already recorded its single output promise
 * by then — and promises do not expire under the default panel settings — that panel stops
 * requesting altogether.
 *
 * <p>The fix is structural rather than compensatory: visit every link once, subtract what earlier
 * addresses took from a local tally, and never touch Create's cached summaries at all.
 *
 * <p>Planning runs in two phases on purpose.
 *
 * <ol>
 * <li><b>Allocation</b> walks the links once and decides how much of each item each address draws
 * from each packager. This is the part that differs from Create.</li>
 * <li><b>Request construction</b> replays Create's own loop shape per address — items outer, links
 * inner — using the amounts phase one decided. {@code orderId}, the order context carried by the
 * first package, {@code linkIndex} and the shared {@code finalLink} flag are produced in exactly
 * the sequence Create produces them, so package grouping, defragmentation and order completion are
 * unchanged.</li>
 * </ol>
 *
 * <p>Each address is an independent order: it gets its own {@code orderId}, its own context, its own
 * {@code finalLink} tracker and its own multimap. Create only ever places a single order into one
 * multimap, and {@code attemptToSend} relies on that — it packs consecutive requests only while
 * address and {@code orderId} match, and a mismatch ends the current package. Two orders sharing a
 * packager key in an unordered multimap could therefore interleave, restarting the package counter
 * in the middle of an order and moving its context into a later package. The only place orders are
 * combined is {@link #mergeContiguously}, which preserves per-order contiguity explicitly.
 *
 * <p>Planning is side-effect free. Nothing is withdrawn until
 * {@code LogisticsManager.performPackageRequests} runs.
 */
public final class RoutedPlanner {

	private static final Random RANDOM = new Random();

	private RoutedPlanner() {
	}

	/** One target address and the order that should reach it. */
	public record AddressOrder(String address, PackageOrderWithCrafts order) {
	}

	/**
	 * @param addressPlans one plan per address, each holding exactly one order — never merge them
	 *                     into a shared multimap without preserving per-order contiguity, see
	 *                     {@link #mergeContiguously}. Empty when {@link #complete()} is false.
	 * @param complete     whether every requested amount could be covered by the network
	 */
	public record Plan(List<Multimap<PackagerBlockEntity, PackagingRequest>> addressPlans, boolean complete) {

		static Plan incomplete() {
			return new Plan(List.of(), false);
		}
	}

	/**
	 * Flattens per-address plans into one multimap for callers that can only return a single one,
	 * such as the Factory Controller redirect.
	 *
	 * <p>Uses an {@link ArrayListMultimap} and inserts address by address, so all requests of one
	 * order stay contiguous per packager. {@code PackagerBlockEntity.attemptToSend} consumes
	 * consecutive requests only while address and {@code orderId} match and abandons the current
	 * package on the first mismatch; interleaving two orders would restart the package counter
	 * mid-order and could move the order context into a later package.
	 *
	 * <p>Ordering within a single address plan is irrelevant — it holds one address and one
	 * {@code orderId}, so {@code attemptToSend} never sees a mismatch inside it.
	 *
	 * <p>Remaining deviation: {@code performPackageRequests} retries a packager at most 100 times
	 * per call, and in a merged plan that budget is shared across all addresses of that packager.
	 * Reachable with very large or very many multi-address orders on one packager.
	 */
	public static Multimap<PackagerBlockEntity, PackagingRequest> mergeContiguously(
		List<Multimap<PackagerBlockEntity, PackagingRequest>> addressPlans) {

		ArrayListMultimap<PackagerBlockEntity, PackagingRequest> merged = ArrayListMultimap.create();
		for (Multimap<PackagerBlockEntity, PackagingRequest> addressPlan : addressPlans)
			for (Map.Entry<PackagerBlockEntity, PackagingRequest> entry : addressPlan.entries())
				merged.put(entry.getKey(), entry.getValue());
		return merged;
	}

	/**
	 * @return a complete plan, or an incomplete one when any address could not be fully covered.
	 *         Partial plans are never returned — a short delivery would stall the requesting panel.
	 */
	public static Plan plan(UUID network, List<AddressOrder> addressOrders,
		@Nullable IdentifiedInventory ignoredHandler) {

		if (addressOrders.isEmpty())
			return Plan.incomplete();

		List<LinkTarget> links = collectLinks(network, ignoredHandler);
		if (links.isEmpty())
			return Plan.incomplete();

		// Requested stacks per address, filtered exactly like Create does.
		List<List<BigItemStack>> demands = new ArrayList<>(addressOrders.size());
		for (AddressOrder addressOrder : addressOrders) {
			List<BigItemStack> stacks = new ArrayList<>();
			for (BigItemStack stack : addressOrder.order()
				.stacks())
				if (!stack.stack.isEmpty() && stack.count > 0)
					stacks.add(stack);
			demands.add(stacks);
		}

		// --- Phase 1: allocation. allocation[address][stack][link]
		int[][][] allocation = new int[addressOrders.size()][][];
		int[][] outstanding = new int[addressOrders.size()][];
		for (int a = 0; a < addressOrders.size(); a++) {
			List<BigItemStack> stacks = demands.get(a);
			allocation[a] = new int[stacks.size()][links.size()];
			outstanding[a] = new int[stacks.size()];
			for (int s = 0; s < stacks.size(); s++)
				outstanding[a][s] = stacks.get(s).count;
		}

		for (int l = 0; l < links.size(); l++) {
			InventorySummary summary = links.get(l)
				.packager()
				.getAvailableItems();

			// What this packager has already promised to earlier addresses in this same walk.
			Object2IntMap<ItemStack> claimed =
				new Object2IntOpenCustomHashMap<>(ItemStackLinkedSet.TYPE_AND_TAG);

			for (int a = 0; a < demands.size(); a++) {
				List<BigItemStack> stacks = demands.get(a);
				for (int s = 0; s < stacks.size(); s++) {
					int remaining = outstanding[a][s];
					if (remaining <= 0)
						continue;

					ItemStack item = stacks.get(s).stack;
					int stock = summary.getCountOf(item) - claimed.getInt(item);
					if (stock <= 0)
						continue;

					int taken = Math.min(remaining, stock);
					allocation[a][s][l] = taken;
					outstanding[a][s] = remaining - taken;
					claimed.put(item, claimed.getInt(item) + taken);
				}
			}
		}

		for (int[] perAddress : outstanding)
			for (int remaining : perAddress)
				if (remaining > 0)
					return Plan.incomplete();

		// --- Phase 2: request construction, mirroring LogisticsManager.findPackagersForRequest.
		// One multimap per address, because Create only ever puts a single order into one.
		List<Multimap<PackagerBlockEntity, PackagingRequest>> addressPlans = new ArrayList<>();

		for (int a = 0; a < addressOrders.size(); a++) {
			AddressOrder addressOrder = addressOrders.get(a);
			List<BigItemStack> stacks = demands.get(a);
			Multimap<PackagerBlockEntity, PackagingRequest> requests = HashMultimap.create();

			// Each address is its own order.
			PackageOrderWithCrafts context = addressOrder.order();
			int orderId = RANDOM.nextInt();
			List<Integer> usedLinks = new ArrayList<>();
			MutableBoolean finalLinkTracker = new MutableBoolean(false);

			for (int s = 0; s < stacks.size(); s++) {
				boolean finalEntry = s == stacks.size() - 1;
				ItemStack requestedItem = stacks.get(s).stack;

				for (int l = 0; l < links.size(); l++) {
					int taken = allocation[a][s][l];
					if (taken <= 0)
						continue;

					int usedIndex = usedLinks.indexOf(l);
					int linkIndex = usedIndex == -1 ? usedLinks.size() : usedIndex;
					MutableBoolean isFinalLink = new MutableBoolean(false);
					if (linkIndex == usedLinks.size() - 1)
						isFinalLink = finalLinkTracker;

					requests.put(links.get(l)
						.packager(),
						PackagingRequest.create(requestedItem, taken, addressOrder.address(), linkIndex,
							isFinalLink, 0, orderId, context));

					if (usedIndex == -1) {
						// Only the first package of an order carries its context.
						context = null;
						usedLinks.add(l);
						finalLinkTracker = isFinalLink;
					}
				}

				if (finalEntry)
					finalLinkTracker.setTrue();
			}

			addressPlans.add(requests);
		}

		return new Plan(addressPlans, true);
	}

	/**
	 * Reproduces Create's link selection: one link per distinct target inventory, chosen at random
	 * among the links sharing it, so parallel links do not each contribute the same chest.
	 */
	private static List<LinkTarget> collectLinks(UUID network, @Nullable IdentifiedInventory ignoredHandler) {
		Map<InventoryIdentifier, List<LinkTarget>> byInventory = new HashMap<>();
		List<LinkTarget> selected = new ArrayList<>();

		for (LogisticallyLinkedBehaviour link : LogisticallyLinkedBehaviour.getAllPresent(network, true)) {
			if (!(link.blockEntity instanceof PackagerLinkBlockEntity linkBE))
				continue;
			PackagerBlockEntity packager = linkBE.getPackager();
			if (packager == null || packager.isTargetingSameInventory(ignoredHandler))
				continue;

			LinkTarget target = new LinkTarget(packager);
			InventoryIdentifier identifier = identifierOf(packager);
			if (identifier == null)
				selected.add(target);
			else
				byInventory.computeIfAbsent(identifier, $ -> new ArrayList<>())
					.add(target);
		}

		for (List<LinkTarget> group : byInventory.values())
			if (!group.isEmpty())
				selected.add(group.get(RANDOM.nextInt(group.size())));

		return selected;
	}

	@Nullable
	private static InventoryIdentifier identifierOf(PackagerBlockEntity packager) {
		if (!packager.targetInventory.hasInventory())
			return null;
		IdentifiedInventory identified = packager.targetInventory.getIdentifiedInventory();
		return identified == null ? null : identified.identifier();
	}

	private record LinkTarget(PackagerBlockEntity packager) {
	}
}

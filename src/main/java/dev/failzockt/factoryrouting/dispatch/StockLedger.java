package dev.failzockt.factoryrouting.dispatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;

import com.google.common.collect.Multimap;
import com.mojang.logging.LogUtils;
import com.simibubi.create.content.logistics.crate.BottomlessItemHandler;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.VersionedInventoryWrapper;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Temporarily deducts planned withdrawals from the packagers' cached inventory summaries so that a
 * later planning pass in the same tick sees stock an earlier pass already claimed.
 *
 * <p>{@code LogisticsManager.findPackagersForRequest} reserves nothing — it only reads
 * {@code packager.getAvailableItems()} and returns a plan. Create never notices because it issues
 * exactly one planning pass per logistics network, and networks hold disjoint inventories. Routing
 * ingredients to several addresses means several passes against the *same* network, so without this
 * ledger two buckets can plan the same physical items twice and one address silently comes up
 * short.
 *
 * <p>Mirrors what {@code LogisticallyLinkedBehaviour.deductFromAccurateSummary} does at send time.
 * A direct reference to each summary object is held rather than re-fetching it, so a summary that
 * got rebuilt in the meantime can never be corrupted by the rollback.
 *
 * <p>Release the ledger <em>before</em> the requests are performed: the real deduction happens
 * inside {@code performPackageRequests}.
 *
 * <p><b>Known limitation.</b> A packager whose target inventory is not a
 * {@link VersionedInventoryWrapper} rebuilds its summary on every call, so a reservation could not
 * survive to the next planning pass anyway. Those packagers are skipped outright — see
 * {@link #reserve} for why mutating them would be actively harmful. Requests for the same item
 * across different addresses can still be planned twice there; the complete fix is the single
 * link walk described in SPEC.md §6.1.
 *
 * <p>Shared by the plain Create dispatch and the Factory Controller one — keep it that way, a fix
 * here has to reach both.
 */
public final class StockLedger {

	private static final Logger LOGGER = LogUtils.getLogger();

	/** Handler class names already reported, so one odd mod does not spam the log per position. */
	private static final Set<String> WARNED_HANDLERS = ConcurrentHashMap.newKeySet();

	private record Reservation(InventorySummary summary, ItemStack stack, int count) {
	}

	private final List<Reservation> reservations = new ArrayList<>();

	public void reserve(Multimap<PackagerBlockEntity, PackagingRequest> plan) {
		for (Entry<PackagerBlockEntity, PackagingRequest> entry : plan.entries()) {
			PackagingRequest request = entry.getValue();
			int requested = request.getCount();
			if (requested <= 0)
				continue;

			PackagerBlockEntity packager = entry.getKey();
			IItemHandler inventory = packager.targetInventory.getInventory();

			// Bottomless sources report infinite stock, so planning them twice cannot overdraw.
			if (inventory instanceof BottomlessItemHandler)
				continue;

			// Without a versioned inventory the packager rebuilds its summary on every call, so a
			// deduction here would never reach the next planning pass. Worse, that rebuild feeds
			// our lowered summary into PackagerBlockEntity.submitNewArrivals, which subtracts the
			// fresh summary from it and reports every negative entry through
			// RequestPromiseQueue.itemEnteredSystem -- retiring promises that were never
			// fulfilled. The panel would consider its order delivered and stop re-requesting.
			// Skipping loses the reservation; mutating would stall production.
			if (!(inventory instanceof VersionedInventoryWrapper)) {
				warnOnce(packager, inventory);
				continue;
			}

			InventorySummary summary = packager.getAvailableItems();
			int reserved = Math.min(summary.getCountOf(request.item()), requested);
			if (reserved <= 0)
				continue;

			summary.add(request.item(), -reserved);
			reservations.add(new Reservation(summary, request.item(), reserved));
		}
	}

	public void release() {
		for (int i = reservations.size() - 1; i >= 0; i--) {
			Reservation reservation = reservations.get(i);
			reservation.summary()
				.add(reservation.stack(), reservation.count());
		}
		reservations.clear();
	}

	private static void warnOnce(PackagerBlockEntity packager, IItemHandler inventory) {
		String handler = inventory == null ? "<none>" : inventory.getClass()
			.getName();
		if (!WARNED_HANDLERS.add(handler))
			return;
		LOGGER.warn(
			"Packager at {} exposes a non-versioned inventory ({}). Per-address stock reservation "
				+ "is skipped for it, so requests for the same item routed to different addresses "
				+ "may be planned twice against that packager.",
			packager.getBlockPos(), handler);
	}
}

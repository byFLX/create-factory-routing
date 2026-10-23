package dev.failzockt.factoryrouting.mixin.compat;

import java.util.HashSet;
import java.util.Set;
import java.util.function.UnaryOperator;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.failzockt.factoryrouting.compat.factorycontroller.VirtualPanelRouting;
import dev.failzockt.factoryrouting.compat.factorycontroller.VirtualRoutedDispatch;
import dev.failzockt.factoryrouting.compat.factorycontroller.VirtualRoutingHolder;
import com.google.common.collect.Multimap;
import com.simibubi.create.content.logistics.packager.IdentifiedInventory;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour.NbtProfile;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.component.connection.LogisticsConnection;
import io.github.nbcss.createfactorycontroller.content.component.gauge.VirtualGaugeBehaviour;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import java.util.UUID;

/** Persists and synchronises routing data with a Factory Controller virtual gauge. */
@Pseudo
@Mixin(targets = "io.github.nbcss.createfactorycontroller.content.component.gauge.VirtualGaugeBehaviour")
public abstract class FactoryControllerGaugeMixin implements VirtualRoutingHolder {

	@Unique
	private static final String CREATE_FACTORY_ROUTING_TAG = "CreateFactoryRouting";

	@Unique
	private VirtualPanelRouting createFactoryRouting$routing = VirtualPanelRouting.EMPTY;

	@Override
	public VirtualPanelRouting createFactoryRouting$getRouting() {
		VirtualGaugeBehaviour gauge = (VirtualGaugeBehaviour) (Object) this;
		// Only logistics wires carry routing. Since Factory Controller 1.2 keys connections by type as
		// well as by position, one source can target this gauge over several wires at once, so pruning
		// against every incoming connection would keep the override of an ingredient whose logistics
		// wire was pulled while, say, a redstone wire from the same position remained.
		Set<VirtualComponentPosition> liveSources = new HashSet<>();
		for (Connection incoming : gauge.incomingConnections(LogisticsConnection.TYPE))
			liveSources.add(incoming.from);
		createFactoryRouting$routing = createFactoryRouting$routing.prunedTo(liveSources);
		return createFactoryRouting$routing;
	}

	@Override
	public void createFactoryRouting$setRouting(VirtualPanelRouting routing) {
		createFactoryRouting$routing = routing == null ? VirtualPanelRouting.EMPTY : routing;
	}

	@Override
	public FactoryControllerBlockEntity createFactoryRouting$getController() {
		return createFactoryRouting$controller();
	}

	@Unique
	private FactoryControllerBlockEntity createFactoryRouting$controller() {
		return ((FactoryControllerComponentAccessor) (Object) this)
			.createFactoryRouting$getOwningController();
	}

	@Redirect(method = "tickRequests", at = @At(value = "INVOKE", target =
		"Lcom/simibubi/create/content/logistics/packagerLink/LogisticsManager;findPackagersForRequest(" +
			"Ljava/util/UUID;Lcom/simibubi/create/content/logistics/stockTicker/PackageOrderWithCrafts;" +
			"Lcom/simibubi/create/content/logistics/packager/IdentifiedInventory;Ljava/lang/String;)" +
			"Lcom/google/common/collect/Multimap;"))
	private Multimap<PackagerBlockEntity, PackagingRequest> createFactoryRouting$routeOrder(UUID network,
		PackageOrderWithCrafts order, IdentifiedInventory excludedInventory, String address) {
		return VirtualRoutedDispatch.plan((VirtualGaugeBehaviour) (Object) this,
			createFactoryRouting$controller(), network,
			order, excludedInventory, address);
	}

	@Inject(method = "writeClient", at = @At("TAIL"))
	private void createFactoryRouting$writeClient(RegistryFriendlyByteBuf buffer, CallbackInfo ci) {
		createFactoryRouting$getRouting().write(buffer);
	}

	@Inject(method = "readClientBody", at = @At("TAIL"))
	private void createFactoryRouting$readClient(RegistryFriendlyByteBuf buffer, CallbackInfo ci) {
		createFactoryRouting$routing = VirtualPanelRouting.read(buffer);
	}

	@Inject(method = "toNBT", at = @At("RETURN"))
	private void createFactoryRouting$writeNbt(HolderLookup.Provider registries, NbtProfile profile,
		CallbackInfoReturnable<CompoundTag> cir) {
		VirtualPanelRouting routing = createFactoryRouting$getRouting();
		if (!routing.isEmpty())
			cir.getReturnValue().put(CREATE_FACTORY_ROUTING_TAG, routing.toTag());
	}

	@Inject(method = "readGaugeNBT", at = @At("TAIL"))
	private void createFactoryRouting$readNbt(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
		createFactoryRouting$routing = tag.contains(CREATE_FACTORY_ROUTING_TAG)
			? VirtualPanelRouting.fromTag(tag.getCompound(CREATE_FACTORY_ROUTING_TAG))
			: VirtualPanelRouting.EMPTY;
	}

	@Inject(method = "onComponentsRelocated", at = @At("TAIL"))
	private void createFactoryRouting$remapSources(
		UnaryOperator<VirtualComponentPosition> remap, CallbackInfo ci) {
		createFactoryRouting$routing = createFactoryRouting$routing.remapped(remap);
	}
}

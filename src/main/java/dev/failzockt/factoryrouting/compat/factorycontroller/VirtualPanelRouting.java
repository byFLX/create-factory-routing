package dev.failzockt.factoryrouting.compat.factorycontroller;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import dev.failzockt.factoryrouting.routing.IngredientRoute;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;

/** Per-ingredient routing stored directly on a Factory Controller virtual gauge. */
public record VirtualPanelRouting(Map<VirtualComponentPosition, IngredientRoute> routes) {

	public static final VirtualPanelRouting EMPTY = new VirtualPanelRouting(Map.of());

	public VirtualPanelRouting {
		routes = Map.copyOf(routes);
	}

	public IngredientRoute get(VirtualComponentPosition source) {
		return routes.getOrDefault(source, IngredientRoute.NONE);
	}

	public String resolve(VirtualComponentPosition source, String panelDefault) {
		IngredientRoute route = get(source);
		return route.isOverride() ? route.address() : panelDefault;
	}

	public boolean isEmpty() {
		return routes.isEmpty();
	}

	public VirtualPanelRouting with(VirtualComponentPosition source, IngredientRoute route) {
		Map<VirtualComponentPosition, IngredientRoute> copy = new HashMap<>(routes);
		if (route.isOverride())
			copy.put(source, route);
		else
			copy.remove(source);
		return copy.isEmpty() ? EMPTY : new VirtualPanelRouting(copy);
	}

	public VirtualPanelRouting prunedTo(Set<VirtualComponentPosition> liveLinks) {
		if (routes.isEmpty() || liveLinks.containsAll(routes.keySet()))
			return this;
		Map<VirtualComponentPosition, IngredientRoute> copy = new HashMap<>(routes);
		copy.keySet().retainAll(liveLinks);
		return copy.isEmpty() ? EMPTY : new VirtualPanelRouting(copy);
	}

	public VirtualPanelRouting remapped(Function<VirtualComponentPosition, VirtualComponentPosition> remap) {
		if (routes.isEmpty())
			return this;
		Map<VirtualComponentPosition, IngredientRoute> copy = new HashMap<>();
		routes.forEach((source, route) -> copy.put(remap.apply(source), route));
		return new VirtualPanelRouting(copy);
	}

	public CompoundTag toTag() {
		CompoundTag root = new CompoundTag();
		ListTag entries = new ListTag();
		routes.forEach((source, route) -> {
			CompoundTag entry = new CompoundTag();
			entry.putInt("X", source.x());
			entry.putInt("Y", source.y());
			entry.putString("Address", route.address());
			entries.add(entry);
		});
		root.put("Routes", entries);
		return root;
	}

	public static VirtualPanelRouting fromTag(CompoundTag root) {
		Map<VirtualComponentPosition, IngredientRoute> routes = new HashMap<>();
		ListTag entries = root.getList("Routes", Tag.TAG_COMPOUND);
		for (int i = 0; i < entries.size(); i++) {
			CompoundTag entry = entries.getCompound(i);
			IngredientRoute route = new IngredientRoute(entry.getString("Address"));
			if (route.isOverride())
				routes.put(new VirtualComponentPosition(entry.getInt("X"), entry.getInt("Y")), route);
		}
		return routes.isEmpty() ? EMPTY : new VirtualPanelRouting(routes);
	}

	public void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeVarInt(routes.size());
		routes.forEach((source, route) -> {
			buffer.writeInt(source.x());
			buffer.writeInt(source.y());
			IngredientRoute.STREAM_CODEC.encode(buffer, route);
		});
	}

	public static VirtualPanelRouting read(RegistryFriendlyByteBuf buffer) {
		int size = buffer.readVarInt();
		Map<VirtualComponentPosition, IngredientRoute> routes = new HashMap<>(size);
		for (int i = 0; i < size; i++) {
			VirtualComponentPosition source = new VirtualComponentPosition(buffer.readInt(), buffer.readInt());
			IngredientRoute route = IngredientRoute.STREAM_CODEC.decode(buffer);
			if (route.isOverride())
				routes.put(source, route);
		}
		return routes.isEmpty() ? EMPTY : new VirtualPanelRouting(routes);
	}
}

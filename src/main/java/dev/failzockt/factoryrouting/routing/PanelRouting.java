package dev.failzockt.factoryrouting.routing;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * All ingredient routing overrides of a single panel slot.
 *
 * <p>Keyed by the source panel's position, exactly like {@code FactoryPanelBehaviour.targetedBy},
 * so both maps stay in one-to-one correspondence.
 */
public record PanelRouting(Map<FactoryPanelPosition, IngredientRoute> routes) {

	public static final PanelRouting EMPTY = new PanelRouting(Map.of());

	public static final Codec<PanelRouting> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.unboundedMap(FactoryPanelPosition.CODEC, IngredientRoute.CODEC)
			.optionalFieldOf("routes", Map.of())
			.forGetter(PanelRouting::routes)
	).apply(instance, PanelRouting::new));

	public static final StreamCodec<ByteBuf, PanelRouting> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.map(HashMap::new, FactoryPanelPosition.STREAM_CODEC, IngredientRoute.STREAM_CODEC),
		PanelRouting::routes,
		PanelRouting::new);

	public PanelRouting {
		routes = Map.copyOf(routes);
	}

	public IngredientRoute get(FactoryPanelPosition source) {
		return routes.getOrDefault(source, IngredientRoute.NONE);
	}

	/**
	 * Effective target address of one ingredient link. Implements SPEC.md §3: an explicit override
	 * wins, otherwise the panel's own address is inherited. A blank result means the link cannot be
	 * routed at all.
	 */
	public String resolve(FactoryPanelPosition source, String panelDefault) {
		IngredientRoute route = get(source);
		return route.isOverride() ? route.address() : panelDefault;
	}

	public boolean isEmpty() {
		return routes.isEmpty();
	}

	public PanelRouting with(FactoryPanelPosition source, IngredientRoute route) {
		Map<FactoryPanelPosition, IngredientRoute> copy = new HashMap<>(routes);
		if (route.isOverride())
			copy.put(source, route);
		else
			copy.remove(source);
		return new PanelRouting(copy);
	}

	/**
	 * Drops overrides whose ingredient link no longer exists.
	 *
	 * @return {@code this} when nothing changed, so callers can avoid marking the block entity dirty
	 */
	public PanelRouting prunedTo(Set<FactoryPanelPosition> liveLinks) {
		if (routes.isEmpty() || liveLinks.containsAll(routes.keySet()))
			return this;
		Map<FactoryPanelPosition, IngredientRoute> copy = new HashMap<>(routes);
		copy.keySet().retainAll(liveLinks);
		return new PanelRouting(copy);
	}
}

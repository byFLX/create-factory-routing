package dev.failzockt.factoryrouting.routing;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock.PanelSlot;

/**
 * Routing data of one {@code FactoryPanelBlockEntity}, which hosts up to four panel slots.
 *
 * <p>Stored as a NeoForge data attachment rather than mixed into Create's block entity, so the
 * data survives Create updates and degrades harmlessly when this mod is removed.
 */
public record BoardRouting(Map<PanelSlot, PanelRouting> slots) {

	public static final BoardRouting EMPTY = new BoardRouting(Map.of());

	public static final Codec<BoardRouting> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.unboundedMap(PanelSlot.CODEC, PanelRouting.CODEC)
			.optionalFieldOf("slots", Map.of())
			.forGetter(BoardRouting::slots)
	).apply(instance, BoardRouting::new));

	public BoardRouting {
		slots = Map.copyOf(slots);
	}

	public PanelRouting get(PanelSlot slot) {
		return slots.getOrDefault(slot, PanelRouting.EMPTY);
	}

	public BoardRouting with(PanelSlot slot, PanelRouting routing) {
		Map<PanelSlot, PanelRouting> copy = new HashMap<>(slots);
		if (routing.isEmpty())
			copy.remove(slot);
		else
			copy.put(slot, routing);
		return new BoardRouting(copy);
	}

	public boolean isEmpty() {
		return slots.isEmpty();
	}
}

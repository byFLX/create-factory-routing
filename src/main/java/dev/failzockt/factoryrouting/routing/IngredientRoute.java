package dev.failzockt.factoryrouting.routing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Routing override for a single ingredient link of a factory panel.
 *
 * <p>An empty address means "inherit from the panel's own {@code recipeAddress}". See SPEC.md §3
 * for the resolution order.
 */
public record IngredientRoute(String address) {

	/** Maximum accepted address length, matching Create's {@code AddressEditBox}. */
	public static final int MAX_ADDRESS_LENGTH = 25;

	public static final IngredientRoute NONE = new IngredientRoute("");

	public static final Codec<IngredientRoute> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.optionalFieldOf("address", "")
			.forGetter(IngredientRoute::address)
	).apply(instance, IngredientRoute::new));

	public static final StreamCodec<ByteBuf, IngredientRoute> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.STRING_UTF8, IngredientRoute::address,
		IngredientRoute::new);

	public IngredientRoute {
		address = address == null ? "" : address.trim();
		if (address.length() > MAX_ADDRESS_LENGTH)
			address = address.substring(0, MAX_ADDRESS_LENGTH);
	}

	public boolean isOverride() {
		return !address.isBlank();
	}
}

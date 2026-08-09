package dev.failzockt.factoryrouting.client;

/** Input bridge for Create screens that inherit their keyboard handlers from Screen. */
public interface EmbeddedRoutingInput {

	boolean createFactoryRouting$keyPressed(int keyCode, int scanCode, int modifiers);

	boolean createFactoryRouting$charTyped(char codePoint, int modifiers);
}

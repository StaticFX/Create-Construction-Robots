package net.neoforged.neoforge.client.event;

import net.minecraftforge.eventbus.api.Event;

/**
 * Shim for Forge 1.20.1: provides ClientTickEvent.Post as a type for shared code compilation.
 * The event bus requires a public no-arg constructor for listener list computation.
 * These events are never actually dispatched — forge entry point registers manual tick listeners.
 */
public abstract class ClientTickEvent {
    private ClientTickEvent() {}

    public static class Post extends Event {
        public Post() {}
    }
}

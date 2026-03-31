package net.neoforged.neoforge.event.tick;

import net.minecraftforge.eventbus.api.Event;

/**
 * Shim for Forge 1.20.1: provides PlayerTickEvent.Post as a type for shared code compilation.
 * The event bus requires a public no-arg constructor for listener list computation.
 * These events are never actually dispatched — forge entry point registers manual tick listeners.
 */
public abstract class PlayerTickEvent {
    private PlayerTickEvent() {}

    public static class Post extends Event {
        public Post() {}
    }
}

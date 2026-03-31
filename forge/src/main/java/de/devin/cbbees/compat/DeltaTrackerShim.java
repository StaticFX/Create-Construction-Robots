package de.devin.cbbees.compat;

/**
 * Shim for Forge 1.20.1: DeltaTracker was introduced in Minecraft 1.21.
 * Shared HUD code accepts this as a parameter type but never calls methods on it.
 * The Forge entry point wraps the float partialTick into this for type compatibility.
 *
 * Lives in mod package to avoid module conflict with net.minecraft.client.
 */
public class DeltaTrackerShim {
    private final float partialTick;

    public DeltaTrackerShim(float partialTick) {
        this.partialTick = partialTick;
    }

    public float getGameTimeDeltaPartialTick(boolean runningNormally) {
        return partialTick;
    }

    public float getRealtimeDeltaTicks() {
        return partialTick;
    }
}

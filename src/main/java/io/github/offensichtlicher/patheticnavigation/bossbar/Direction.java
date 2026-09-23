package io.github.offensichtlicher.patheticnavigation.bossbar;

import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * A heading relative to where the player is currently looking — the arrow behaves like a HUD
 * compass and rotates as the player turns, rather than naming an absolute cardinal direction.
 */
public enum Direction {

    FORWARD("↑"),
    FORWARD_RIGHT("↗"),
    RIGHT("→"),
    BACKWARD_RIGHT("↘"),
    BACKWARD("↓"),
    BACKWARD_LEFT("↙"),
    LEFT("←"),
    FORWARD_LEFT("↖");

    private static final Direction[] VALUES = values();
    private static final float SECTOR_DEGREES = 360.0f / VALUES.length;

    private final String arrow;

    Direction(String arrow) {
        this.arrow = arrow;
    }

    public @NotNull String arrow() {
        return arrow;
    }

    /**
     * Resolves the arrow the player has to follow.
     * <p>
     * Minecraft yaw runs clockwise with 0 pointing towards +Z, which is what
     * {@code atan2(-dx, dz)} reproduces. The difference between the target heading and the
     * player's yaw is then bucketed into eight 45 degree sectors. The vertical component is
     * ignored.
     *
     * @param playerYaw the player's current yaw in degrees
     * @param from      where the player stands
     * @param to        what to point at
     */
    public static @NotNull Direction of(float playerYaw, @NotNull Vector from, @NotNull Vector to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        if (dx == 0.0 && dz == 0.0) {
            return FORWARD;
        }

        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double difference = targetYaw - playerYaw;
        difference = ((difference % 360.0) + 360.0) % 360.0;

        int index = (int) (Math.round(difference / SECTOR_DEGREES) % VALUES.length);
        return VALUES[index];
    }
}

package com.heishandashu.moreswitches.light;

import net.minecraft.world.phys.Vec3;

/**
 * Projects a world position onto the screen for the light-level overlay.
 *
 * <p>The overlay is drawn as ordinary screen text rather than as world geometry. Two earlier
 * attempts to submit text into the world pass produced nothing that could be seen, so the numbers
 * are now placed by projecting each block's top-centre with plain arithmetic and letting the GUI
 * text renderer draw them. The cost is that the digits no longer depth-test against terrain, which
 * for a light preview reads fine: the numbers are meant to sit on top of the blocks.</p>
 *
 * <p>The projection is kept free of engine state: the caller supplies the eye position, the look
 * vector, the vertical field of view and the viewport size, all of which are plain values. That
 * makes the arithmetic testable without a running game.</p>
 */
public final class ScreenProjection {

    private ScreenProjection() {
    }

    /** A point on screen, or {@link #MISS} when the position is behind the camera. */
    public record Point(double x, double y) {
    }

    /** Returned for positions behind the camera, which have no meaningful screen position. */
    public static final Point MISS = new Point(Double.NaN, Double.NaN);

    /**
     * Projects {@code world} onto a viewport of {@code width} by {@code height}.
     *
     * @param eye the camera position
     * @param look the unit vector the camera is facing
     * @param world the position to project
     * @param fovDegrees the vertical field of view
     * @param width viewport width
     * @param height viewport height
     * @return the screen point, or {@link #MISS} when the position is behind the camera
     */
    public static Point project(Vec3 eye, Vec3 look, Vec3 world,
                                double fovDegrees, int width, int height) {
        Vec3 forward = look.normalize();
        Vec3 relative = world.subtract(eye);

        // Depth along the view direction. Everything at or behind the eye plane is skipped: the
        // perspective divide would otherwise mirror it to the opposite side of the screen.
        double depth = relative.dot(forward);
        if (depth <= 1.0E-4D) {
            return MISS;
        }

        Vec3 right = forward.cross(new Vec3(0.0D, 1.0D, 0.0D)).normalize();
        if (right.lengthSqr() < 1.0E-8D) {
            // Looking straight up or down, where the usual "right" is undefined; any perpendicular
            // will do, and the caller's numbers stay stable because the look vector barely moves.
            right = forward.cross(new Vec3(1.0D, 0.0D, 0.0D)).normalize();
        }
        Vec3 up = right.cross(forward).normalize();

        double halfHeight = Math.tan(Math.toRadians(fovDegrees) / 2.0D);
        double aspect = (double) width / (double) height;

        double ndcX = (relative.dot(right) / depth) / (halfHeight * aspect);
        double ndcY = (relative.dot(up) / depth) / halfHeight;

        double x = (ndcX * 0.5D + 0.5D) * width;
        double y = (0.5D - ndcY * 0.5D) * height;
        return new Point(x, y);
    }

    /** Whether a projected point lies inside the viewport, with a margin so half-glyphs are skipped. */
    public static boolean onScreen(Point point, int width, int height, int margin) {
        if (point == null || Double.isNaN(point.x()) || Double.isNaN(point.y())) {
            return false;
        }
        return point.x() >= -margin && point.x() <= width + margin
                && point.y() >= -margin && point.y() <= height + margin;
    }
}

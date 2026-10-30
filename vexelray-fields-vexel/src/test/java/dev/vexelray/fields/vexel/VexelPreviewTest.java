package dev.vexelray.fields.vexel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class VexelPreviewTest {

    private static final VexelPreview.Camera CAMERA = new VexelPreview.Camera(new Vec3(4, 4, 14), new Vec3(4, 4, 4),
            new Vec3(0, 1, 0), 40);

    @Test
    void aSphereShowsInTheMiddleAndNotInTheCorner() {
        VexelField f = VexelField.bake(Solid.sphere(new Vec3(4, 4, 4), 3), Vec3.ZERO, 0.5, 16, 16, 16, 4);
        int[] frame = VexelPreview.render(f, CAMERA, 32, 32, new VexelHash(0, 16));
        int[] solid = VexelPreview.reference(Solid.sphere(new Vec3(4, 4, 4), 3), CAMERA, 32, 32, 40);
        int back = solid[0];
        assertNotEquals(back, frame[16 * 32 + 16], "the middle is the sphere");
        assertEquals(back, frame[0], "the corner is background");
        assertNotEquals(back, solid[16 * 32 + 16]);
    }

    @Test
    void aGranularCellIsOrange() {
        VexelField cloud = VexelField.bake(Solid.sphere(new Vec3(4, 4, 4), 3), Vec3.ZERO, 0.5, 16, 16, 16, 4)
                .coarser().coarser().coarser().coarser();
        int middle = VexelPreview.render(cloud, CAMERA, 32, 32, new VexelHash(0, 16))[16 * 32 + 16];
        assertEquals(0xfa94, middle >> 8, "granular orange, " + Integer.toHexString(middle));
    }
}

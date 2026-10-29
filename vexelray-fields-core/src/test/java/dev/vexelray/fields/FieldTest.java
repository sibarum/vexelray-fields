package dev.vexelray.fields;

import dev.vexelray.fields.shape.Bounds;
import dev.vexelray.fields.shape.Vec2;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FieldTest {

    @Test
    void aFramingFitsAndRoundTrips() {
        Framing f = Framing.fit(new Bounds(0, 0, 10, 20), 32, 32, 4, true);
        assertEquals(24 / 20.0, f.scale(), 1e-12);
        Vec2 top = f.toTexels(new Vec2(5, 20));
        assertEquals(16, top.x(), 1e-9);
        assertEquals(4, top.y(), 1e-9);   // y up: the shape's top is the field's top row
        Vec2 p = new Vec2(3.25, 7.5);
        Vec2 back = f.toShape(f.toTexels(p).x(), f.toTexels(p).y());
        assertEquals(0, back.minus(p).length(), 1e-12);
    }

    @Test
    void bytesRoundTripWithinHalfAStep() {
        Field f = new Field(FieldKind.MTSDF, 2, 1, new float[]{-3, -1, 0, 1, 2.5f, 3.9f, -4, 0.2f});
        byte[] b = Encoding.packed(f, 8);
        Field g = Encoding.unpack(FieldKind.MTSDF, 2, 1, b, 8);
        for (int i = 0; i < 8; i++) {
            assertEquals(f.data()[i], g.data()[i], 8 / 510.0 + 1e-6);
        }
        byte[] rgba = Encoding.rgba(new Field(FieldKind.SDF, 1, 1, new float[]{0}), 8);
        assertEquals(128, rgba[0] & 0xFF);
        assertEquals(rgba[0], rgba[2]);
        assertEquals(255, rgba[3] & 0xFF);
    }

    @Test
    void theMedianIsTakenAfterInterpolating() {
        Field f = new Field(FieldKind.MSDF, 2, 1, new float[]{1, -1, 0.5f, -1, 1, 0.5f});
        // each texel's median is 0.5, but halfway the channels are 0, 0 and 0.5
        assertEquals(0.5f, f.distance(0, 0));
        assertEquals(0f, f.sample(1, 0.5), 1e-6);
    }
}

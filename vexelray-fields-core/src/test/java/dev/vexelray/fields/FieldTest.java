package dev.vexelray.fields;

import dev.vexelray.fields.shape.Bounds;
import dev.vexelray.fields.shape.Vec2;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void logBytesReachFarAndKeepTheEdge() {
        LogEncoding log = LogEncoding.matching(16, 32);
        assertEquals(16, log.edgeRange(), 1e-9);
        assertEquals(16 / 255.0, log.step(0), 1e-9);   // a step at the edge as linear 16's
        assertEquals(128, log.encode(0) & 0xFF);
        assertEquals(255, log.encode(32) & 0xFF);
        assertEquals(0, log.encode(-32) & 0xFF);
        for (double d = -32; d <= 32; d += 0.37) {
            assertEquals(d, log.decode(log.encode(d)), log.step(d) / 2 + 1e-4);
            assertEquals(d, log.distance(log.value(d)), 1e-9);
        }
        // linear 16 has run out by 8 texels; this tells 20 from 21
        assertTrue(log.encode(21) != log.encode(20));
        // the curve rises, so the median of the bytes is the byte of the median
        int a = log.encode(3) & 0xFF, b = log.encode(-1) & 0xFF, c = log.encode(0.5f) & 0xFF;
        assertEquals(log.encode(Field.median(3, -1, 0.5f)) & 0xFF, Math.max(Math.min(a, b), Math.min(Math.max(a, b), c)));
        Field f = new Field(FieldKind.SDF, 3, 1, new float[]{-20, 0.25f, 30});
        Field g = log.unpack(FieldKind.SDF, 3, 1, log.packed(f));
        for (int i = 0; i < 3; i++) {
            assertEquals(f.data()[i], g.data()[i], log.step(f.data()[i]) / 2 + 1e-4);
        }
        assertThrows(IllegalArgumentException.class, () -> LogEncoding.matching(64, 32));
    }

    @Test
    void aFieldExtendsPastItsEdgeByItsNearestPoints() {
        // a disc of radius 8 at (16, 16), exact at every texel centre
        Field sdf = new Field(FieldKind.SDF, 32, 32);
        Field mtsdf = new Field(FieldKind.MTSDF, 32, 32);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                float d = (float) (8 - Math.hypot(x + 0.5 - 16, y + 0.5 - 16));
                sdf.set(x, y, 0, d);
                mtsdf.set(x, y, 3, d);   // red, green and blue left 0: only the true distance is followed
            }
        }
        assertEquals(sdf.sample(10.3, 20.7), sdf.extended(10.3, 20.7));
        // straight out from the disc: exact, 76 texels past its edge where a clamped sampler says 7.5
        assertEquals(-76, sdf.extended(100, 16), 0.02);
        // off to one side: a little far, never near
        double truth = Math.hypot(60 - 16, 80 - 16) - 8;
        for (Field f : new Field[]{sdf, mtsdf}) {
            double d = -f.extended(60, 80);
            assertTrue(d >= truth - 0.02 && d < truth * 1.02, "was " + d + ", truly " + truth);
        }
    }

    @Test
    void theMedianIsTakenAfterInterpolating() {
        Field f = new Field(FieldKind.MSDF, 2, 1, new float[]{1, -1, 0.5f, -1, 1, 0.5f});
        // each texel's median is 0.5, but halfway the channels are 0, 0 and 0.5
        assertEquals(0.5f, f.distance(0, 0));
        assertEquals(0f, f.sample(1, 0.5), 1e-6);
    }
}

package dev.vexelray.fields.sdg.schema;

import sibarum.intrabuf.Compat;
import sibarum.intrabuf.annotation.IntrabufMessage;

/** A colour texture: a rectangle of a page, mapped onto the canvas by its origin and scale. */
@IntrabufMessage(name = "vexelray.sdg.Sampled", compat = Compat.FULL)
public record Sampled(int page, int x, int y, int width, int height, double originX, double originY, double scale) {
}

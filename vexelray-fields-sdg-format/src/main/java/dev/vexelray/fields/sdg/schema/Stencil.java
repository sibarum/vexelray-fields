package dev.vexelray.fields.sdg.schema;

import sibarum.intrabuf.Compat;
import sibarum.intrabuf.annotation.Field;
import sibarum.intrabuf.annotation.IntrabufMessage;

/**
 * Where a layer is. Every kind, known or not, is placed by these fields, so a reader can tell where a stencil of a
 * kind it does not know lies. What a new kind needs besides goes in a field on the end.
 *
 * @param kind {@code "sdf"} or {@code "msdf"}, or a name this reader does not know
 */
@IntrabufMessage(name = "vexelray.sdg.Stencil", compat = Compat.FULL)
public record Stencil(@Field(nonNull = true) String kind, int page, int channel, int x, int y, int width, int height,
                      double originX, double originY, double scale, float range) {
}

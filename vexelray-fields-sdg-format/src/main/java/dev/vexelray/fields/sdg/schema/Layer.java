package dev.vexelray.fields.sdg.schema;

import sibarum.intrabuf.Compat;
import sibarum.intrabuf.annotation.Field;
import sibarum.intrabuf.annotation.IntrabufMessage;

/**
 * One layer: where it is, what colour, and how opaque; or, since 1.1 of this message (SDG 1.2), a group, whose stencil
 * is of the kind {@code "group"} and whose contents are {@code group}.
 */
@IntrabufMessage(name = "vexelray.sdg.Layer", minor = 1, compat = Compat.FULL)
public record Layer(@Field(nonNull = true) String name, float opacity,
                    @Field(nonNull = true) Stencil stencil,
                    @Field(nonNull = true) Paint paint,
                    @Field(since = 1) Group group) {
}

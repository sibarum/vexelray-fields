package dev.vexelray.fields.sdg.schema;

import sibarum.intrabuf.Compat;
import sibarum.intrabuf.annotation.Field;
import sibarum.intrabuf.annotation.IntrabufMessage;

/** One layer: where it is, what colour, and how opaque. */
@IntrabufMessage(name = "vexelray.sdg.Layer", compat = Compat.FULL)
public record Layer(@Field(nonNull = true) String name, float opacity,
                    @Field(nonNull = true) Stencil stencil,
                    @Field(nonNull = true) Paint paint) {
}

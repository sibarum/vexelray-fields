package dev.vexelray.fields.sdg.future;

import sibarum.intrabuf.Compat;
import sibarum.intrabuf.annotation.Field;
import sibarum.intrabuf.annotation.IntrabufMessage;

/** A Layer from a writer one minor past this one: 1.2's group at 1, and a field of its own at 2. */
@IntrabufMessage(name = "vexelray.sdg.Layer", minor = 2, compat = Compat.FULL)
public record FutureLayer(@Field(nonNull = true) String name, float opacity,
                          @Field(nonNull = true) FutureStencil stencil,
                          @Field(nonNull = true) FuturePaint paint,
                          @Field(since = 1) FutureGroup group,
                          @Field(since = 2) String blend) {
}

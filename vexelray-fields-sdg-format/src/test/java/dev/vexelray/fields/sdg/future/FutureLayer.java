package dev.vexelray.fields.sdg.future;

import dev.vexelray.fields.sdg.schema.Text;
import sibarum.intrabuf.Compat;
import sibarum.intrabuf.annotation.Field;
import sibarum.intrabuf.annotation.IntrabufMessage;

/** A Layer from a writer one minor past this one: 1.2's group at 1, 1.3's text at 2, and a field of its own at 3. */
@IntrabufMessage(name = "vexelray.sdg.Layer", minor = 3, compat = Compat.FULL)
public record FutureLayer(@Field(nonNull = true) String name, float opacity,
                          @Field(nonNull = true) FutureStencil stencil,
                          @Field(nonNull = true) FuturePaint paint,
                          @Field(since = 1) FutureGroup group,
                          @Field(since = 2) Text text,
                          @Field(since = 3) String blend) {
}

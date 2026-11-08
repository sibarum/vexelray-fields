package dev.vexelray.fields.sdg.future;

import sibarum.intrabuf.Compat;
import sibarum.intrabuf.annotation.Field;
import sibarum.intrabuf.annotation.IntrabufMessage;

@IntrabufMessage(name = "vexelray.sdg.Layer", minor = 1, compat = Compat.FULL)
public record FutureLayer(@Field(nonNull = true) String name, float opacity,
                          @Field(nonNull = true) FutureStencil stencil,
                          @Field(nonNull = true) FuturePaint paint,
                          @Field(since = 1) String blend) {
}

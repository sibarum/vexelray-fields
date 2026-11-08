package dev.vexelray.fields.sdg.past;

import dev.vexelray.fields.sdg.schema.Stencil;
import sibarum.intrabuf.Compat;
import sibarum.intrabuf.annotation.Field;
import sibarum.intrabuf.annotation.IntrabufMessage;

@IntrabufMessage(name = "vexelray.sdg.Layer", compat = Compat.FULL)
public record PastLayer(@Field(nonNull = true) String name, float opacity,
                        @Field(nonNull = true) Stencil stencil,
                        @Field(nonNull = true) PastPaint paint) {
}

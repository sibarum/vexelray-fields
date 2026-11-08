package dev.vexelray.fields.sdg.future;

import dev.vexelray.fields.sdg.schema.Sampled;
import dev.vexelray.fields.sdg.schema.Solid;
import sibarum.intrabuf.Compat;
import sibarum.intrabuf.annotation.Field;
import sibarum.intrabuf.annotation.IntrabufMessage;

/** A paint with a kind added after this reader: its own message on the end. */
@IntrabufMessage(name = "vexelray.sdg.Paint", minor = 1, compat = Compat.FULL)
public record FuturePaint(@Field(nonNull = true) String kind, Solid solid, Sampled sampled,
                          @Field(since = 1) FutureLinear linear) {
}

package dev.vexelray.fields.sdg.schema;

import sibarum.intrabuf.Compat;
import sibarum.intrabuf.annotation.Field;
import sibarum.intrabuf.annotation.IntrabufMessage;

/**
 * What colour a layer is: a kind, and the fields of that kind, the others null. A new kind is a new message on the
 * end, {@code @Field(since = n)}; a reader that does not know it skips the field, and reads the paint as unknown.
 *
 * @param kind    {@code "solid"} or {@code "sampled"}, or a name this reader does not know
 * @param solid   when {@code kind} is {@code "solid"}
 * @param sampled when {@code kind} is {@code "sampled"}
 */
@IntrabufMessage(name = "vexelray.sdg.Paint", compat = Compat.FULL)
public record Paint(@Field(nonNull = true) String kind, Solid solid, Sampled sampled) {
}

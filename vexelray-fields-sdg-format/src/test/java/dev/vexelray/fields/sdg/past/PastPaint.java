package dev.vexelray.fields.sdg.past;

import dev.vexelray.fields.sdg.schema.Sampled;
import sibarum.intrabuf.Compat;
import sibarum.intrabuf.annotation.Field;
import sibarum.intrabuf.annotation.IntrabufMessage;

@IntrabufMessage(name = "vexelray.sdg.Paint", compat = Compat.FULL)
public record PastPaint(@Field(nonNull = true) String kind, PastSolid solid, Sampled sampled) {
}

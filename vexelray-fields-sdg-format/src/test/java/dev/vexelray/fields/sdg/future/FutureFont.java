package dev.vexelray.fields.sdg.future;

import sibarum.intrabuf.Compat;
import sibarum.intrabuf.annotation.Field;
import sibarum.intrabuf.annotation.IntrabufMessage;

import java.util.List;

/** A 1.3 font, its glyphs' stencils grown as a later writer's are. */
@IntrabufMessage(name = "vexelray.sdg.Font", compat = Compat.FULL)
public record FutureFont(@Field(nonNull = true) String family, @Field(nonNull = true) String style,
                         double ascender, double descender, double lineHeight,
                         @Field(nonNull = true) List<FutureGlyph> glyphs,
                         String license) {
}

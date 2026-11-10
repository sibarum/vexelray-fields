package dev.vexelray.fields.sdg.future;

import sibarum.intrabuf.Compat;
import sibarum.intrabuf.annotation.IntrabufMessage;

@IntrabufMessage(name = "vexelray.sdg.Glyph", compat = Compat.FULL)
public record FutureGlyph(FutureStencil field, double advance, String codepoints) {
}

package dev.vexelray.fields.sdg.schema;

import sibarum.intrabuf.Compat;
import sibarum.intrabuf.annotation.IntrabufMessage;

/**
 * One glyph of a {@link Font}: a stencil whose origin and scale are in ems from the pen on the baseline, absent for a
 * glyph with nothing to draw (a space); the ems the pen moves past it; and the characters it stands for, absent if
 * none (a ligature stands for more than one).
 */
@IntrabufMessage(name = "vexelray.sdg.Glyph", compat = Compat.FULL)
public record Glyph(Stencil field, double advance, String codepoints) {
}

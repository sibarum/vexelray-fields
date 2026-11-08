package dev.vexelray.fields.sdg.past;

import sibarum.intrabuf.Compat;
import sibarum.intrabuf.annotation.Field;
import sibarum.intrabuf.annotation.IntrabufMessage;

/** A solid paint at 1.0: its colour, and no role. */
@IntrabufMessage(name = "vexelray.sdg.Solid", minor = 0, compat = Compat.FULL)
public record PastSolid(@Field(fixed = true) int rgba) {
}

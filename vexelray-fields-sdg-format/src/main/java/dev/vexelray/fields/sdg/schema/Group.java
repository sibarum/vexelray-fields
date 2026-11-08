package dev.vexelray.fields.sdg.schema;

import sibarum.intrabuf.Compat;
import sibarum.intrabuf.annotation.Field;
import sibarum.intrabuf.annotation.IntrabufMessage;

import java.util.List;

/**
 * A group's contents (SDG 1.2): its layers, back to front; a clip, a stencil, or null for none; a mask, or null for none.
 */
@IntrabufMessage(name = "vexelray.sdg.Group", compat = Compat.FULL)
public record Group(@Field(nonNull = true) List<Layer> layers, Stencil clip, Mask mask) {
}

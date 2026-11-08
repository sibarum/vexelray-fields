package dev.vexelray.fields.sdg.schema;

import sibarum.intrabuf.Compat;
import sibarum.intrabuf.annotation.Field;
import sibarum.intrabuf.annotation.IntrabufMessage;

import java.util.List;

/** A group's mask (SDG 1.2): {@code "luminance"} or {@code "alpha"} of its layers, drawn apart. */
@IntrabufMessage(name = "vexelray.sdg.Mask", compat = Compat.FULL)
public record Mask(@Field(nonNull = true) String kind, @Field(nonNull = true) List<Layer> layers) {
}

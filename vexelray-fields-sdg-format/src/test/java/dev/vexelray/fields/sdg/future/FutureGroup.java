package dev.vexelray.fields.sdg.future;

import sibarum.intrabuf.Compat;
import sibarum.intrabuf.annotation.Field;
import sibarum.intrabuf.annotation.IntrabufMessage;

import java.util.List;

@IntrabufMessage(name = "vexelray.sdg.Group", compat = Compat.FULL)
public record FutureGroup(@Field(nonNull = true) List<FutureLayer> layers, FutureStencil clip, FutureMask mask) {
}

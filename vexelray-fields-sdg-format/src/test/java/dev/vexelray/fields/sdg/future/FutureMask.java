package dev.vexelray.fields.sdg.future;

import sibarum.intrabuf.Compat;
import sibarum.intrabuf.annotation.Field;
import sibarum.intrabuf.annotation.IntrabufMessage;

import java.util.List;

@IntrabufMessage(name = "vexelray.sdg.Mask", compat = Compat.FULL)
public record FutureMask(@Field(nonNull = true) String kind, @Field(nonNull = true) List<FutureLayer> layers) {
}

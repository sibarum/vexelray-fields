package dev.vexelray.fields.sdg.future;

import sibarum.intrabuf.Compat;
import sibarum.intrabuf.annotation.Field;
import sibarum.intrabuf.annotation.IntrabufMessage;

@IntrabufMessage(name = "vexelray.sdg.Stencil", minor = 1, compat = Compat.FULL)
public record FutureStencil(@Field(nonNull = true) String kind, int page, int channel, int x, int y, int width,
                            int height, double originX, double originY, double scale, float range,
                            @Field(since = 1) float softness) {
}

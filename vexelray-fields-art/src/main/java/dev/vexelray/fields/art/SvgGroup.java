package dev.vexelray.fields.art;

import java.util.List;
import java.util.Optional;

/**
 * Parts drawn apart and laid over once (SVG's isolated group): an element with a group {@code opacity} under 1, a
 * {@code clip-path} or a {@code mask}. Its {@code clip} is the clip path's shapes, each a fill in view-box units, their
 * union the clip; its {@code mask}, the mask's content, of the kind {@code maskKind} ({@code "luminance"}, SVG's default,
 * or {@code "alpha"}), and null when it has none.
 *
 * @param id       the element's id, or the outermost id it is drawn under; null if none
 * @param opacity  the group's own opacity
 * @param clip     the clip path's shapes; empty for none
 * @param maskKind the mask's kind, or null for no mask
 * @param mask     the mask's content; empty for none
 * @param children what the group draws, back to front
 */
public record SvgGroup(String id, float opacity, List<SvgPart> clip, String maskKind, List<SvgNode> mask,
                       List<SvgNode> children) implements SvgNode {

    public SvgGroup {
        clip = List.copyOf(clip);
        mask = List.copyOf(mask);
        children = List.copyOf(children);
    }

    public Optional<String> name() {
        return Optional.ofNullable(id);
    }
}

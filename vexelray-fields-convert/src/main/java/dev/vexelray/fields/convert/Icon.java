package dev.vexelray.fields.convert;

import dev.vexelray.fields.art.Raster;
import dev.vexelray.fields.sdg.Png;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * An application icon: a square raster per size the OS asks for, packed as one {@code .ico} of PNGs (what Windows
 * has read since Vista and what {@code rc.exe} links). Written without {@code java.desktop}.
 *
 * @param sizes one square raster per size, 1 to 256 pixels, no two the same size; smallest first once made
 */
public record Icon(List<Raster> sizes) {

    /** The sizes Windows asks an application's icon for: the shell's, the taskbar's and Explorer's, at each scale. */
    public static final int[] WINDOWS = {16, 20, 24, 32, 40, 48, 64, 96, 128, 256};

    public Icon {
        List<Raster> s = new ArrayList<>(sizes);
        s.sort(Comparator.comparingInt(Raster::width));
        for (int i = 0; i < s.size(); i++) {
            Raster r = s.get(i);
            if (r.width() != r.height() || r.width() < 1 || r.width() > 256
                    || i > 0 && s.get(i - 1).width() == r.width()) {
                throw new IllegalArgumentException("an icon size " + r.width() + " × " + r.height());
            }
        }
        if (s.isEmpty()) {
            throw new IllegalArgumentException("an icon with no sizes");
        }
        sizes = List.copyOf(s);
    }

    /** The {@code .ico}: its header, a directory entry per size, then each size's PNG. */
    public byte[] ico() {
        List<byte[]> pngs = new ArrayList<>();
        for (Raster r : sizes) {
            pngs.add(Png.write(r.width(), r.height(), r.rgba()));
        }
        int n = sizes.size();
        ByteBuffer head = ByteBuffer.allocate(6 + 16 * n).order(ByteOrder.LITTLE_ENDIAN);
        head.putShort((short) 0).putShort((short) 1).putShort((short) n);
        int offset = 6 + 16 * n;
        for (int i = 0; i < n; i++) {
            int side = sizes.get(i).width();
            head.put((byte) (side == 256 ? 0 : side)).put((byte) (side == 256 ? 0 : side));
            head.put((byte) 0).put((byte) 0);              // no palette, reserved
            head.putShort((short) 1).putShort((short) 32); // planes, bits per pixel
            head.putInt(pngs.get(i).length).putInt(offset);
            offset += pngs.get(i).length;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(offset);
        out.writeBytes(head.array());
        pngs.forEach(out::writeBytes);
        return out.toByteArray();
    }
}

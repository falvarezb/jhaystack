package fjab.haystack;

import java.util.List;

public record Png(Chunk ihdr, List<Chunk> idat, Chunk iend, ImageSize imageSize, byte[] imageData) {
    public static final byte[] PNG_SIGNATURE = new byte[]{-119, 80, 78, 71, 13, 10, 26, 10};
}

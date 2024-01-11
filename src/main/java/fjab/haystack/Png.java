package fjab.haystack;

import java.util.List;

public record Png(Chunk ihdr, List<Chunk> idat, Chunk iend, ImageSize imageSize, byte[] imageData) {
}

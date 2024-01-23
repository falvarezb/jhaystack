package fjab.haystack;

/**
 * @param width         in pixels
 * @param height        in pixels
 * @param bytesPerPixel number of bytes used to represent each pixel
 * @param stride        sequence of bytes corresponding to a row of pixels (width * bytesPerPixel)
 */
public record ImageSize(int width, int height, int bytesPerPixel, int stride) {
}

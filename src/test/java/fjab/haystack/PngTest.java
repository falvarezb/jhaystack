package fjab.haystack;


import org.junit.jupiter.api.Test;

public class PngTest {
    @Test
    public void testDecode() {
        Png png = new PngDecoder("src/test/resources/srcImage.png").decode();
        System.out.println(png);
    }

    @Test
    public void testDecoding() {
        Png png = new PngDecoder("src/test/resources/image.png").decode();
        new PngEncoder("src/test/resources/image-modified.png").encode(png);

    }
}

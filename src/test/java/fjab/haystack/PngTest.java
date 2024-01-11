package fjab.haystack;


import org.junit.jupiter.api.Test;

public class PngTest {
    @Test
    public void testDecode() {
        Png png = new PngDecoder("src/test/resources/image.png").decode();
        System.out.println(png);
    }

    public void testEncode() {
        Png png = new PngDecoder("src/test/resources/image.png").decode();
        new PngEncoder().encode(png);

    }
}

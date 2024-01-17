package fjab.haystack;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class PngTest {
    @Test
    public void testDecode() {
        Png png = new PngDecoder("src/test/resources/srcImage.png").decode();
        System.out.println(png);
    }

    /*
        Files meeting the following conditions should remain unchanged after decoding and encoding back
        - without filter (filter_type == 0)
        - size of the IDAT section is less than 2**16-1
        - there is one and only one IDAT section
     */
    @Test
    public void testFileTransformedIntoItself() throws IOException {
        Png png = new PngDecoder("src/test/resources/event-bridge.png").decode();
        new PngEncoder("src/test/resources/event-bridge-modified.png").encode(png);
        assertFileEquals("src/test/resources/event-bridge.png", "src/test/resources/event-bridge-modified.png");

        //checking intermediate results
        assertFileEquals("src/test/resources/compressed_data_bytes", "src/test/resources/testOutput/compressedData");
        assertFileEquals("src/test/resources/decompressed_data_bytes", "src/test/resources/testOutput/decompressedData");
        assertFileEquals("src/test/resources/filtered_data_bytes", "src/test/resources/testOutput/filteredData");
        assertFileEquals("src/test/resources/unfiltered_data_bytes", "src/test/resources/testOutput/unfilteredData");
    }

    private void assertFileEquals(String path1, String path2) throws IOException {
        Assertions.assertArrayEquals(Files.readAllBytes(Paths.get(path1)), Files.readAllBytes(Paths.get(path2)));
    }
}

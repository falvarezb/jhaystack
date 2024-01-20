package fjab.haystack;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PngTest {
    /*
        Files meeting the following conditions should remain unchanged after decoding and encoding back
        - filter_type == 0 (no filter)
        - there is one and only one IDAT section
        - size of the IDAT section is less than 2**16
     */
    @Test
    public void testFileTransformedIntoItself() throws IOException {
        var testName = "event-bridge";
        runTestCase(testName);
    }

    @Test
    public void testFileLambda() throws IOException {
        var testName = "lambda";
        runTestCase(testName);
    }

    @Test
    public void testFileLargeImage() throws IOException {
        var testName = "large-image";
        runTestCase(testName);
    }

    private void runTestCase(String testName) throws IOException {
        var originalTestFileName = testName + ".png";
        var resultTestFileName = testName + "-modified.png";
        var testFolderPath = Paths.get("src/test/resources",testName);
        Png png = new PngDecoder(testFolderPath.resolve(originalTestFileName).toString()).decode();

        var testOutputPath = testFolderPath.resolve("testOutput");
        new PngEncoder(testOutputPath.resolve(resultTestFileName).toString()).encode(png);

        checkIntermediateResults(testFolderPath, testOutputPath);
        //assertFileEquals(testFolderPath.resolve(originalTestFileName), testOutputPath.resolve(resultTestFileName));
        assertFileEquals(testFolderPath.resolve(resultTestFileName), testOutputPath.resolve(resultTestFileName));
    }

    private void checkIntermediateResults(Path testFolderPath, Path testOutputPath) throws IOException {
        assertFileEquals(testFolderPath.resolve("decompressed_data_bytes"), testOutputPath.resolve("decompressedData"));
        assertFileEquals(testFolderPath.resolve("unfiltered_data_bytes"), testOutputPath.resolve("unfilteredData"));
        assertFileEquals(testFolderPath.resolve("filtered_data_bytes"), testOutputPath.resolve("filteredData"));
        assertFileEquals(testFolderPath.resolve("compressed_data_bytes"), testOutputPath.resolve("compressedData"));
    }

    private void assertFileEquals(Path path1, Path path2) throws IOException {
        Assertions.assertArrayEquals(Files.readAllBytes(path1), Files.readAllBytes(path2));
    }
}

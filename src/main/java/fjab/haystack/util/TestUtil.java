package fjab.haystack.util;

import fjab.haystack.App;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class TestUtil {
    public static void write_test_output(String filename, String additionalPathElement, byte[] data) throws IOException {
        if (App.testMode) {
            Files.write(Paths.get("src/test/resources", additionalPathElement, "testOutput", filename), data);
        }
    }

    private static String baseName(String filename) {
        String baseNameWithExtension = Paths.get(filename).getFileName().toString();
        return baseNameWithExtension.substring(0, baseNameWithExtension.lastIndexOf('.'));
    }

    public static String testName(String filename) {
        return baseName(filename).replace("-modified", "");
    }
}

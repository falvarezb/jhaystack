package fjab.haystack;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Util {

    public static void write_test_output(String filename, String additionalPathElement, byte[] data) throws IOException {
        Files.write(Paths.get("src/test/resources", additionalPathElement, "testOutput", filename), data);
    }

    private static String baseName(String filename) {
        String baseNameWithExtension = Paths.get(filename).getFileName().toString();
        return baseNameWithExtension.substring(0, baseNameWithExtension.lastIndexOf('.'));
    }

    public static String testName(String filename) {
        return baseName(filename).replace("-modified", "");
    }

//    public static void write_test_output(String filename, byte[] data) {
//        try {
//            FileOutputStream fos = new FileOutputStream("src/test/resources/testoutput/" + filename);
//            fos.write(data);
//            fos.close();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
}

package fjab.haystack;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Util {

    public static void write_test_output(String filename, byte[] data) throws IOException {
        Files.write(Paths.get("src/test/resources/testoutput/", filename), data);
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

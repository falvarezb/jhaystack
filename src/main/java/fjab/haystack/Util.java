package fjab.haystack;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

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

    public static byte[] decompress(List<InputStream> ins) throws IOException {
        return new InflaterInputStream(new SequenceInputStream(Collections.enumeration(ins))).readAllBytes();
    }

    public static byte[] compress(byte[] input) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             DeflaterOutputStream dos = new DeflaterOutputStream(bos, new Deflater(Deflater.DEFAULT_COMPRESSION))) {

            dos.write(input);
            dos.finish();
            return bos.toByteArray();
        }
    }


}

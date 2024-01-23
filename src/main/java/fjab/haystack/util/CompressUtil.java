package fjab.haystack.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.Collections;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public class CompressUtil {
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

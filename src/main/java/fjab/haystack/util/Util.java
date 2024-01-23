package fjab.haystack.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

import static fjab.haystack.domain.Png.PNG_SIGNATURE;

public class Util {

    public static void checkPngSignature(ByteBuffer byteBuffer) {
        byte[] signature = new byte[8];
        byteBuffer.get(signature);
        if (!Arrays.equals(signature, PNG_SIGNATURE)) {
            throw new RuntimeException("File is not a PNG file");
        }
    }

    public static ByteBuffer loadFileIntoByteBuffer(String sourceFile) throws IOException {
        try (FileChannel channel = FileChannel.open(Paths.get(sourceFile), StandardOpenOption.READ)) {
            int fileSize = (int) channel.size();
            ByteBuffer byteBuffer = ByteBuffer.allocate(fileSize);
            channel.read(byteBuffer);
            byteBuffer.flip();
            return byteBuffer;
        }
    }
}

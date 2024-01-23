package fjab.haystack;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

import static fjab.haystack.FilterUtil.unfilter;
import static fjab.haystack.TestUtil.testName;
import static fjab.haystack.TestUtil.write_test_output;
import static fjab.haystack.Util.*;

public class PngDecoder {

    private final String sourceFile;

    public PngDecoder(String sourceFile) {
        this.sourceFile = sourceFile;
    }


    public Png decode() throws IOException {
        ByteBuffer byteBuffer = loadFileIntoByteBuffer(sourceFile);

        checkPngSignature(byteBuffer);

        Chunk ihdr = null;
        Chunk iend = null;
        List<Chunk> idats = new ArrayList<>();
        CRC32 checkSum = new CRC32();
        while (byteBuffer.hasRemaining()) {
            Chunk chunk = decodeChunk(byteBuffer, checkSum);
            if (chunk.isIHDR())
                ihdr = chunk;
            else if (chunk.isIEND())
                iend = chunk;
            else if (chunk.isIDAT())
                idats.add(chunk);
        }
        assert ihdr != null;
        ImageSize imageSize = decodeIhdrData(ihdr);
        return new Png(
                ihdr,
                idats,
                iend,
                imageSize,
                decodeIdatData(idats, imageSize)
        );
    }

    private static Chunk decodeChunk(ByteBuffer byteBuffer, CRC32 checkSum) {
        int chunkLength = byteBuffer.getInt();
        byte[] chunkType = new byte[4];
        byteBuffer.get(chunkType);
        byte[] chunkData = new byte[chunkLength];
        byteBuffer.get(chunkData);
        int chunkCrc = byteBuffer.getInt();

        checkSum.reset();
        checkSum.update(chunkType);
        checkSum.update(chunkData);
        if ((int) checkSum.getValue() != chunkCrc) {
            throw new RuntimeException("CRC check failed");
        }
        return new Chunk(chunkType, chunkData, chunkLength, chunkCrc);
    }

    private static ImageSize decodeIhdrData(Chunk ihdr) {
        byte[] data = ihdr.data();
        int width = ByteBuffer.wrap(data, 0, 4).getInt();
        int height = ByteBuffer.wrap(data, 4, 4).getInt();
        byte bitDepth = data[8];
        byte colorType = data[9];
        byte compressionMethod = data[10];
        byte filterMethod = data[11];
        byte interlaceMethod = data[12];

        if (compressionMethod != 0) {
            throw new RuntimeException("Compression method not supported");
        }
        if (filterMethod != 0) {
            throw new RuntimeException("Filter method not supported");
        }
        if (interlaceMethod != 0) {
            throw new RuntimeException("Interlace method not supported");
        }
        if (bitDepth != 8) {
            throw new RuntimeException("Bit depth not supported");
        }
        byte trueColour = 2;
        byte trueColourWithAlpha = 6;
        if (colorType != trueColour && colorType != trueColourWithAlpha) {
            throw new RuntimeException("Color type not supported");
        }
        int bytesPerPixel = colorType == trueColour ? 3 : 4;
        int stride = bytesPerPixel * width;
        return new ImageSize(width, height, bytesPerPixel, stride);
    }

    private byte[] decodeIdatData(List<Chunk> idats, ImageSize imageSize) throws IOException {
        byte[] decompressedIdatData = CompressUtil.decompress(concatenateIdatChunks(idats));
        assert decompressedIdatData.length == (imageSize.height() * imageSize.stride()) + imageSize.height() : "Decompressed data length does not match expected length";
        write_test_output("decompressedData", testName(sourceFile), decompressedIdatData);

        byte[] unfilteredData = unfilter(decompressedIdatData, imageSize);
        write_test_output("unfilteredData", testName(sourceFile), unfilteredData);
        return unfilteredData;
    }

    private static List<InputStream> concatenateIdatChunks(List<Chunk> idats) {
        return idats.stream()
                .map(idat -> (InputStream) new ByteArrayInputStream(idat.data()))
                .toList();
    }


}

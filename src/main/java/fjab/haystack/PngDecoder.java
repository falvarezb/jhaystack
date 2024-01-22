package fjab.haystack;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

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
        while (byteBuffer.hasRemaining()) {
            Chunk chunk = decodeChunk(byteBuffer);
            if(chunk.isIHDR())
                ihdr = chunk;
            else if(chunk.isIEND())
                iend = chunk;
            else if(chunk.isIDAT())
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

    /**
     * Chunk structure (<a href="https://www.w3.org/TR/png/#5Chunk-layout">Chunk layout</a>):<br>
     * - Length: 4-byte unsigned integer giving the number of bytes in the chunk's data field.<br>
     * - Chunk type: a sequence of 4 bytes defining the chunk type, e.g. for IHDR chunks, this sequence is 73 72 68 82.<br>
     * - Chunk data: the data bytes appropriate to the chunk type, if any<br>
     * - CRC: 32-bit CRC calculated on the preceding bytes in the chunk, including the chunk type field and chunk data fields,
     * but not including the length field. The CRC is always present, even for chunks containing no data
     */
    private Chunk decodeChunk(ByteBuffer byteBuffer) {
        int chunkLength = byteBuffer.getInt();
        byte[] chunkType = new byte[4];
        byteBuffer.get(chunkType);
        byte[] chunkData = new byte[chunkLength];
        byteBuffer.get(chunkData);
        int chunkCrc = byteBuffer.getInt();

        CRC32 checkSum = new CRC32();
        checkSum.update(chunkType);
        checkSum.update(chunkData);
        if((int) checkSum.getValue() != chunkCrc) {
            throw new RuntimeException("CRC check failed");
        }
        return new Chunk(chunkType, chunkData, chunkLength, chunkCrc);
    }

    private ImageSize decodeIhdrData(Chunk ihdr) {
        byte[] data = ihdr.data();
        int width = ByteBuffer.wrap(data, 0, 4).getInt();
        int height = ByteBuffer.wrap(data, 4, 4).getInt();
        byte bitDepth = data[8];
        byte colorType = data[9];
        byte compressionMethod = data[10];
        byte filterMethod = data[11];
        byte interlaceMethod = data[12];

        if(compressionMethod != 0) {
            throw new RuntimeException("Compression method not supported");
        }
        if(filterMethod != 0) {
            throw new RuntimeException("Filter method not supported");
        }
        if(interlaceMethod != 0) {
            throw new RuntimeException("Interlace method not supported");
        }
        if(bitDepth != 8) {
            throw new RuntimeException("Bit depth not supported");
        }
        byte trueColour = 2;
        byte trueColourWithAlpha = 6;
        if(colorType != trueColour && colorType != trueColourWithAlpha) {
            throw new RuntimeException("Color type not supported");
        }
        int bytesPerPixel = colorType == trueColour ? 3 : 4;
        int stride = bytesPerPixel * width;
        return new ImageSize(width, height, bytesPerPixel, stride);
    }

    private byte[] decodeIdatData(List<Chunk> idats, ImageSize imageSize) throws IOException {
        // concatenate all idat chunks
        byte[] decompressedIdatData = decompress(idats.stream()
                .map(idat -> (InputStream)new ByteArrayInputStream(idat.data()))
                .toList());
        write_test_output("decompressedData", testName(sourceFile), decompressedIdatData);
        if(decompressedIdatData.length != (imageSize.height() * imageSize.stride()) + imageSize.height()) {
            throw new RuntimeException("Decompressed data length does not match expected length");
        }
        byte[] unfilteredData = unfilter(decompressedIdatData, imageSize);
        write_test_output("unfilteredData", testName(sourceFile), unfilteredData);
        return unfilteredData;
    }

    /**
     * Filtering concepts (<a href="https://www.w3.org/TR/png/#9Filters">Filters</a>) <br>
     * Named filter bytes (<a href="https://www.w3.org/TR/png/#table-named-filter-bytes">Table: named filter bytes</a>): <br>
     * - x: byte being filtered <br>
     * - a: the byte corresponding to x in the pixel immediately before the pixel containing x <br>
     * - b: the byte corresponding to x in the previous scanline <br>
     * - c: the byte corresponding to b in the pixel immediately before the pixel containing b <br>
     * Note: for any given byte, the corresponding byte to its left is the one offset by the number of bytes per pixel <br><br>
     * | c | b | <br>
     * | a | x | <br><br>
     *
     * Filter types (<a href="https://www.w3.org/TR/png/#9-table91">Filter types</a>): <br>
     * 0: None <br>
     * 1: Sub <br>
     * 2: Up <br>
     * 3: Average <br>
     * 4: Paeth <br>
     */
    public byte[] unfilter(byte[] decompressedIdatData, ImageSize imageSize) throws IOException {
        int height = imageSize.height();
        int stride = imageSize.stride();
        int bytesPerPixel = imageSize.bytesPerPixel();
        try(DataInputStream di = new DataInputStream(new ByteArrayInputStream(decompressedIdatData))) {
            byte[] unfilteredData = new byte[height * stride];

            byte[] previousRow = new byte[stride];
            for (int scanline_idx = 0; scanline_idx < height; scanline_idx++) {
                int offset = scanline_idx * stride;
                byte filterType = di.readByte();
                byte[] scanline = new byte[stride];
                di.readFully(scanline);
                switch (filterType) {
                    case 0 -> System.arraycopy(scanline, 0, unfilteredData, scanline_idx * stride, stride); //None
                    case 1 -> { //Sub
                        for (int byte_idx = 0; byte_idx < stride; byte_idx++) {
                            byte x = scanline[byte_idx];
                            byte a = reconA(scanline_idx, byte_idx, unfilteredData, bytesPerPixel, stride);
                            unfilteredData[byte_idx+(offset)] = (byte) (x + a);
                        }
                    }
                    case 2 -> { //Up
                        for (int byte_idx = 0; byte_idx < stride; byte_idx++) {
                            byte x = scanline[byte_idx];
                            byte b = reconB(scanline_idx, byte_idx, previousRow);
                            unfilteredData[byte_idx+(offset)] = (byte) (x + b);
                        }
                    }
                    case 3 -> {//Average
                        for (int byte_idx = 0; byte_idx < stride; byte_idx++) {
                            byte x = scanline[byte_idx];
                            byte a = reconA(scanline_idx, byte_idx, unfilteredData, bytesPerPixel, stride);
                            byte b = reconB(scanline_idx, byte_idx, previousRow);
                            unfilteredData[byte_idx+(offset)] = (byte) (x + (a + b) / 2);
                        }
                    }
                    case 4 -> { //Paeth
                        for (int byte_idx = 0; byte_idx < stride; byte_idx++) {
                            byte x = scanline[byte_idx];
                            byte a = reconA(scanline_idx, byte_idx, unfilteredData, bytesPerPixel, stride);
                            byte b = reconB(scanline_idx, byte_idx, previousRow);
                            byte c = reconC(scanline_idx, byte_idx, previousRow, bytesPerPixel);
                            unfilteredData[byte_idx+(offset)] = (byte) (x + paethPredictor(a, b, c));
                        }
                    }
                    default -> throw new RuntimeException("Unsupported filter type: " + filterType);
                }
                System.arraycopy(unfilteredData, offset, previousRow, 0, stride);
            }
            assert decompressedIdatData.length == unfilteredData.length + height;
            return unfilteredData;
        }
    }

    private static byte reconC(int scanline_idx, int byte_idx, byte[] previousRow, int bytesPerPixel) {
        return byte_idx < bytesPerPixel || scanline_idx == 0 ? 0 : previousRow[byte_idx - bytesPerPixel];
    }

    private static byte reconB(int scanline_idx, int byte_idx, byte[] previousRow) {
        return scanline_idx == 0 ? 0 : previousRow[byte_idx];
    }

    private static byte reconA(int scanline_index, int byte_index_in_scanline, byte[] unfilteredData, int bytesPerPixel, int stride) {
        return byte_index_in_scanline < bytesPerPixel ? 0 : unfilteredData[byte_index_in_scanline + (scanline_index * stride) - bytesPerPixel];
    }

    private static byte paethPredictor(byte a, byte b, byte c) {
        /*
            In Java, the byte data type has a range from -128 to 127
            However, in the context of this function, bytes are treated as unsigned and have a range from 0 to 255.
            To fix this and avoid unexpected results due to overflow, byte values need to be converted to int in the range of 0 to 255.
         */
        int aInt = a & 0xFF;
        int bInt = b & 0xFF;
        int cInt = c & 0xFF;

        int p = aInt + bInt - cInt;
        int pa = Math.abs(p - aInt);
        int pb = Math.abs(p - bInt);
        int pc = Math.abs(p - cInt);
        if(pa <= pb && pa <= pc)
            return a;
        else if(pb <= pc)
            return b;
        else
            return c;
    }
}

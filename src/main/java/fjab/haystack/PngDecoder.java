package fjab.haystack;

import java.awt.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;

import static fjab.haystack.Util.*;

public class PngDecoder {

    static final byte[] PNG_SIGNATURE = new byte[] {-119, 80, 78, 71, 13, 10, 26, 10};
    private final byte TRUECOLOUR = 2;
    private final byte TRUECOLOUR_WITH_ALPHA = 6;
    private final byte BIT_DEPTH = 8;
    private final byte COMPRESSION_METHOD = 0;
    private final byte FILTER_METHOD = 0;
    private final byte INTERLACE_METHOD = 0;

    private int width; //in pixels
    private int height; //in pixels
    private int bytesPerPixel;
    private int stride;
    private Chunk ihdr;
    private Chunk iend;
    private List<Chunk> idats = new ArrayList<>();

    private String sourceFile;

    public PngDecoder(String sourceFile) {
        this.sourceFile = sourceFile;
    }


    public Png decode() {
        // read first 8 bytes to check if it is a PNG file
        try(FileChannel channel = FileChannel.open(Paths.get(sourceFile), StandardOpenOption.READ)) {
            int fileSize = (int) channel.size();
            ByteBuffer byteBuffer = ByteBuffer.allocate(fileSize);
            channel.read(byteBuffer);
            byteBuffer.flip();

            byte[] signature = new byte[8];
            byteBuffer.get(signature);
            if(!Arrays.equals(signature, PNG_SIGNATURE)) {
                throw new RuntimeException("File is not a PNG file");
            }

            while (byteBuffer.hasRemaining()) {
                Chunk chunk = decodeChunk(byteBuffer);
                if(chunk.isIHDR())
                    this.ihdr = chunk;
                else if(chunk.isIEND())
                    this.iend = chunk;
                else if(chunk.isIDAT())
                    this.idats.add(chunk);
            }
            ImageSize imageSize = decodeIhdrData();
            return new Png(
                    this.ihdr,
                    this.idats,
                    this.iend,
                    imageSize,
                    decodeIdatData(this.idats, imageSize)
            );

        } catch (Exception e) {
            e.printStackTrace();
        }



        return null;
    }

    /**
     * Chunk structure (https://www.w3.org/TR/png/#5Chunk-layout):<br>
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

    private ImageSize decodeIhdrData() {
        byte[] data = ihdr.data();
        this.width = ByteBuffer.wrap(data, 0, 4).getInt();
        this.height = ByteBuffer.wrap(data, 4, 4).getInt();
        byte bitDepth = data[8];
        byte colorType = data[9];
        byte compressionMethod = data[10];
        byte filterMethod = data[11];
        byte interlaceMethod = data[12];

        if(compressionMethod != COMPRESSION_METHOD) {
            throw new RuntimeException("Compression method not supported");
        }
        if(filterMethod != FILTER_METHOD) {
            throw new RuntimeException("Filter method not supported");
        }
        if(interlaceMethod != INTERLACE_METHOD) {
            throw new RuntimeException("Interlace method not supported");
        }
        if(bitDepth != BIT_DEPTH) {
            throw new RuntimeException("Bit depth not supported");
        }
        if(colorType != TRUECOLOUR && colorType != TRUECOLOUR_WITH_ALPHA) {
            throw new RuntimeException("Color type not supported");
        }
        this.bytesPerPixel = colorType == TRUECOLOUR ? 3 : 4;
        this.stride = this.bytesPerPixel * this.width;
        return new ImageSize(this.width, this.height, this.bytesPerPixel, this.stride);
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
        byte[] unfilteredData = unfilter(decompressedIdatData);
        write_test_output("unfilteredData", testName(sourceFile), unfilteredData);
        return unfilteredData;
    }

    /**
     * Filtering concepts (https://www.w3.org/TR/png/#9Filters) <br>
     * Named filter bytes (https://www.w3.org/TR/png/#table-named-filter-bytes): <br>
     * - x: byte being filtered <br>
     * - a: the byte corresponding to x in the pixel immediately before the pixel containing x <br>
     * - b: the byte corresponding to x in the previous scanline <br>
     * - c: the byte corresponding to b in the pixel immediately before the pixel containing b <br>
     * Note: for any given byte, the corresponding byte to its left is the one offset by the number of bytes per pixel <br><br>
     * | c | b | <br>
     * | a | x | <br><br>
     *
     * Filter types (https://www.w3.org/TR/png/#9-table91): <br>
     * 0: None <br>
     * 1: Sub <br>
     * 2: Up <br>
     * 3: Average <br>
     * 4: Paeth <br>
     *
     * @param decompressedIdatData
     * @return
     * @throws IOException
     */
    public byte[] unfilter(byte[] decompressedIdatData) throws IOException {
        try(DataInputStream di = new DataInputStream(new ByteArrayInputStream(decompressedIdatData))) {
            byte[] unfilteredData = new byte[this.height * this.stride];

            byte[] previousRow = new byte[this.stride];
            for (int scanline_idx = 0; scanline_idx < this.height; scanline_idx++) {
                byte filterType = di.readByte();
                byte[] scanline = new byte[this.stride];
                di.readFully(scanline);
                switch (filterType) {
                    case 0 -> System.arraycopy(scanline, 0, unfilteredData, scanline_idx * this.stride, this.stride); //None
                    case 1 -> { //Sub
                        for (int byte_idx = 0; byte_idx < this.stride; byte_idx++) {
                            byte x = scanline[byte_idx];
                            byte a = reconA(scanline_idx, byte_idx, unfilteredData);
                            unfilteredData[byte_idx+(scanline_idx*this.stride)] = (byte) (x + a);
                        }
                    }
                    case 2 -> { //Up
                        for (int byte_idx = 0; byte_idx < this.stride; byte_idx++) {
                            byte x = scanline[byte_idx];
                            byte b = reconB(scanline_idx, byte_idx, previousRow);
                            unfilteredData[byte_idx+(scanline_idx*this.stride)] = (byte) (x + b);
                        }
                    }
                    case 3 -> {//Average
                        for (int byte_idx = 0; byte_idx < this.stride; byte_idx++) {
                            byte x = scanline[byte_idx];
                            byte a = reconA(scanline_idx, byte_idx, unfilteredData);
                            byte b = reconB(scanline_idx, byte_idx, previousRow);
                            unfilteredData[byte_idx+(scanline_idx*this.stride)] = (byte) (x + (a + b) / 2);
                        }
                    }
                    case 4 -> { //Paeth
                        for (int byte_idx = 0; byte_idx < this.stride; byte_idx++) {
                            byte x = scanline[byte_idx];
                            byte a = reconA(scanline_idx, byte_idx, unfilteredData);
                            byte b = reconB(scanline_idx, byte_idx, previousRow);
                            byte c = reconC(scanline_idx, byte_idx, previousRow);
                            unfilteredData[byte_idx+(scanline_idx*this.stride)] = (byte) (x + paethPredictor(a, b, c));
                        }
                    }
                    default -> throw new RuntimeException("Unsupported filter type: " + filterType);
                }
                System.arraycopy(unfilteredData, scanline_idx*this.stride, previousRow, 0, this.stride);
            }
            assert decompressedIdatData.length == unfilteredData.length + this.height;
            return unfilteredData;
        }
    }

    private byte reconC(int scanline_idx, int byte_idx, byte[] previousRow) {
        return byte_idx < this.bytesPerPixel || scanline_idx == 0 ? 0 : previousRow[byte_idx - this.bytesPerPixel];
    }

    private static byte reconB(int scanline_idx, int byte_idx, byte[] previousRow) {
        return scanline_idx == 0 ? 0 : previousRow[byte_idx];
    }

    private byte reconA(int scanline_index, int byte_index_in_scanline, byte[] unfilteredData) {
        return byte_index_in_scanline < this.bytesPerPixel ? 0 : unfilteredData[byte_index_in_scanline + (scanline_index * this.stride) - this.bytesPerPixel];
    }

    private byte paethPredictor(byte a, byte b, byte c) {
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

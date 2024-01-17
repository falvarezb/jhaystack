package fjab.haystack;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import static fjab.haystack.Util.testName;
import static fjab.haystack.Util.write_test_output;

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
            decodeIhdrData();
            return new Png(
                    this.ihdr,
                    this.idats,
                    this.iend,
                    new ImageSize(this.width, this.height, this.bytesPerPixel, this.stride),
                    decodeIdatData()
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
     *
     * @param byteBuffer
     * @return
     * @throws IOException
     */
    private Chunk decodeChunk(ByteBuffer byteBuffer) throws IOException {
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

    private void decodeIhdrData() {
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
    }

    private byte[] decodeIdatData() throws DataFormatException, IOException {
        // concatenate all idat chunks
        byte[] idatData = new byte[idats.stream().mapToInt(Chunk::length).sum()];
        int offset = 0;
        for(Chunk chunk : idats) {
            System.arraycopy(chunk.data(), 0, idatData, offset, chunk.length());
            offset += chunk.length();
        }

        byte[] decompressedIdatData = decompress2();
        write_test_output("decompressedData", testName(sourceFile), decompressedIdatData);
        if(decompressedIdatData.length != (this.height * this.stride) + this.height) {
            throw new RuntimeException("Decompressed data length does not match expected length");
        }
        byte[] unfilteredData = unfilter(decompressedIdatData);
        write_test_output("unfilteredData", testName(sourceFile), unfilteredData);
        return unfilteredData;
    }

    /**
     * Filtering concepts (https://www.w3.org/TR/png/#9Filters)
     * Named filter bytes https://www.w3.org/TR/png/#table-named-filter-bytes:
     * x: byte being filtered
     * a: the byte corresponding to x in the pixel immediately before the pixel containing x
     * b: the byte corresponding to x in the previous scanline
     * c: the byte corresponding to b in the pixel immediately before the pixel containing b
     * | c | b |
     * | a | x |
     *
     * Filter types (https://www.w3.org/TR/png/#9-table91):
     * 0: None
     * 1: Sub
     * 2: Up
     * 3: Average
     * 4: Paeth
     *
     * @param decompressedIdatData
     * @return
     * @throws IOException
     */
    public byte[] unfilter(byte[] decompressedIdatData) throws IOException {
//        byte[] row = new byte[1];
//        row[0] = (byte) 240;
        try(DataInputStream di = new DataInputStream(new ByteArrayInputStream(decompressedIdatData))) {
            byte[] unfilteredData = new byte[this.height * this.stride];

            byte[] previousRow = new byte[this.stride];
            for (int i = 0; i < this.height; i++) {
                byte filterType = di.readByte();
                byte[] scanline = new byte[this.stride];
                di.readFully(scanline);
                switch (filterType) {
                    case 0 -> { //None
                        for (int j = 0; j < this.stride; j++) {
                            unfilteredData[j+(i*this.stride)] = scanline[j];
                        }
                    }
                    case 1 -> { //Sub
                        for (int j = 0; j < this.stride; j++) {
                            byte x = scanline[j];
                            byte a = j < this.bytesPerPixel ? 0 : unfilteredData[j - this.bytesPerPixel];
                            unfilteredData[j+(i*this.stride)] = (byte) (x + a);
                        }
                    }
                    case 2 -> { //Up
                        for (int j = 0; j < this.stride; j++) {
                            byte x = scanline[j];
                            byte b = i == 0 ? 0 : previousRow[j];
                            unfilteredData[j+(i*this.stride)] = (byte) (x + b);
                        }
                    }
                    case 3 -> {//Average
                        for (int j = 0; j < this.stride; j++) {
                            byte x = scanline[j];
                            byte a = j < this.bytesPerPixel ? 0 : unfilteredData[j - this.bytesPerPixel];
                            byte b = i == 0 ? 0 : previousRow[j];
                            unfilteredData[j+(i*this.stride)] = (byte) (x + (a + b) / 2);
                        }
                    }
                    case 4 -> { //Paeth
                        for (int j = 0; j < this.stride; j++) {
                            byte x = scanline[j];
                            byte a = j < this.bytesPerPixel ? 0 : unfilteredData[j - this.bytesPerPixel];
                            byte b = i == 0 ? 0 : previousRow[j];
                            byte c = j < this.bytesPerPixel || i == 0 ? 0 : previousRow[j - this.bytesPerPixel];
                            unfilteredData[j+(i*this.stride)] = (byte) (x + paethPredictor(a, b, c));
                        }
                    }
                    default -> throw new RuntimeException("Unsupported filter type: " + filterType);
                }
                System.arraycopy(scanline, 0, previousRow, 0, this.stride);
            }
            assert decompressedIdatData.length == unfilteredData.length + this.height;
            return unfilteredData;
        }
    }

    private byte paethPredictor(byte a, byte b, byte c) {
        int p = a + b - c;
        int pa = Math.abs(p - a);
        int pb = Math.abs(p - b);
        int pc = Math.abs(p - c);
        if(pa <= pb && pa <= pc)
            return a;
        else if(pb <= pc)
            return b;
        else
            return c;
    }

    private static byte[] decompress(byte[] data) throws IOException, DataFormatException {
        Inflater inflater = new Inflater();
        inflater.setInput(data);

        try(ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length)) {
            byte[] buffer = new byte[1024];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                outputStream.write(buffer, 0, count);
            }
            return outputStream.toByteArray();
        }
    }

    private byte[] decompress2() throws IOException {
        List<InputStream> ins = this.idats.stream()
                .map(idat -> (InputStream)new ByteArrayInputStream(idat.data()))
                .toList();
        var in0 = new SequenceInputStream(Collections.enumeration(ins));
        var in1 = new InflaterInputStream(in0);
        return in1.readAllBytes();
    }
}

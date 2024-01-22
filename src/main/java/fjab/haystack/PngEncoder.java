package fjab.haystack;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;

import static fjab.haystack.Util.*;

public class PngEncoder {

        private final int chunkLengthLength = 4;
        private final int chunkTypeLength = 4;
        private final int chunkCrcLength = 4;
        private final int chunkMetadataLength = chunkLengthLength + chunkTypeLength + chunkCrcLength;
        private final String destFile;
        public PngEncoder(String destFile) {
                this.destFile = destFile;
        }

        public void encode(Png png) {
                try(
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        DataOutputStream dos = new DataOutputStream(baos);
                        FileOutputStream fos = new FileOutputStream(destFile)
                ) {
                        dos.write(PNG_SIGNATURE);
                        dos.write(encodeChunk(png.ihdr()).array());
                        dos.write(encodeIdat(png.imageSize(), png.imageData()).array());
                        dos.write(encodeChunk(png.iend()).array());
                        dos.flush();
                        fos.write(baos.toByteArray());
                        //return baos.toByteArray();
                } catch (IOException e) {
                        throw new RuntimeException(e);
                }
        }

        /**
         * Chunk structure (<a href="https://www.w3.org/TR/png/#5Chunk-layout">Chunk layout</a>):
         * Length: 4-byte unsigned integer giving the number of bytes in the chunk's data field.
         * Chunk type: a sequence of 4 bytes defining the chunk type, e.g. for IHDR chunks, this sequence is 73 72 68 82.
         * Chunk data: the data bytes appropriate to the chunk type, if any
         * CRC: 32-bit CRC calculated on the preceding bytes in the chunk, including the chunk type field and chunk data fields,
         * but not including the length field. The CRC is always present, even for chunks containing no data
         */
        private ByteBuffer encodeChunk(Chunk chunk) {
                ByteBuffer buffer = ByteBuffer.allocate(chunkMetadataLength + chunk.length());
                buffer.putInt(chunk.length());
                buffer.put(chunk.type());
                buffer.put(chunk.data());
                buffer.putInt(chunk.crc());
                buffer.flip();
                return buffer;
        }

        private ByteBuffer encodeIdat(ImageSize imageSize, byte[] imageData) throws IOException {
                try(
                        ByteArrayOutputStream baos = new ByteArrayOutputStream((imageSize.stride() + 1) + imageSize.height())
                ) {
                        for (int i = 0; i < imageSize.height(); i++) {
                                byte filterType = 0;
                                baos.write(filterType);
                                baos.write(imageData, i*imageSize.stride(), imageSize.stride());
                        }
                        byte[] filteredData = baos.toByteArray();
                        write_test_output("filteredData", testName(destFile), filteredData);
                        byte[] compressedData = compress(filteredData);
                        write_test_output("compressedData", testName(destFile), compressedData);
                        // split compressed data into IDAT chunks of at most 2^16 - 1 bytes
                        int chunkSize = 65535;
                        int numChunks = compressedData.length / chunkSize;
                        int bufferCapacity = numChunks * (chunkMetadataLength + chunkSize);
                        int lastChunkSize = compressedData.length % chunkSize;
                        if(lastChunkSize != 0) {
                                numChunks++;
                                bufferCapacity += (chunkMetadataLength + lastChunkSize);
                        }
                        ByteBuffer buffer = ByteBuffer.allocate(bufferCapacity);
                        CRC32 checkSum = new CRC32();
                        for (int i = 0; i < numChunks; i++) {
                                int offset = i * chunkSize;
                                int remainingBytes = compressedData.length - offset;
                                int length = Math.min(chunkSize, remainingBytes);
                                byte[] chunkData = new byte[length];
                                System.arraycopy(compressedData, offset, chunkData, 0, length);
                                byte[] chunkType = new byte[] {73, 68, 65, 84};
                                checkSum.reset();
                                checkSum.update(chunkType);
                                checkSum.update(chunkData);
                                Chunk chunk = new Chunk(chunkType, chunkData, length, (int) checkSum.getValue());
                                buffer.put(encodeChunk(chunk));
                        }
                        return buffer;
                }
        }







}


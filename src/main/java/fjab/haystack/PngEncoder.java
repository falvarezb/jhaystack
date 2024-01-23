package fjab.haystack;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;

import static fjab.haystack.Chunk.CHUNK_METADATA_LENGTH;
import static fjab.haystack.Chunk.IDAT_SIGNATURE;
import static fjab.haystack.Util.*;

public class PngEncoder {


        private final String destFile;
        public PngEncoder(String destFile) {
                this.destFile = destFile;
        }

        public void encode(Png png) throws IOException {
                try(
                        FileOutputStream fos = new FileOutputStream(destFile)
                ) {
                        fos.write(PNG_SIGNATURE);
                        fos.write(encodeChunk(png.ihdr()).array());
                        fos.write(encodeIdat(png.imageSize(), png.imageData()).array());
                        fos.write(encodeChunk(png.iend()).array());
                }
        }

        private ByteBuffer encodeChunk(Chunk chunk) {
                ByteBuffer buffer = ByteBuffer.allocate(CHUNK_METADATA_LENGTH + chunk.length());
                buffer.putInt(chunk.length());
                buffer.put(chunk.type());
                buffer.put(chunk.data());
                buffer.putInt(chunk.crc());
                buffer.flip();
                return buffer;
        }

        private ByteBuffer encodeIdat(ImageSize imageSize, byte[] imageData) throws IOException {
                byte[] filteredData = filter(imageSize, imageData);
                write_test_output("filteredData", testName(destFile), filteredData);
                byte[] compressedData = compress(filteredData);
                write_test_output("compressedData", testName(destFile), compressedData);
                // split compressed data into IDAT chunks of at most 2^16 - 1 bytes
                int chunkSize = 65535;
                return splitDataIntoIdatChunks(chunkSize, compressedData);

        }

        /**
         * Apply filterType = 0 (no filter) to each scanline
         */
        private byte[] filter(ImageSize imageSize, byte[] imageData) throws IOException {
                try(ByteArrayOutputStream baos = new ByteArrayOutputStream((imageSize.stride() + 1) + imageSize.height())) {
                        byte filterType = 0;
                        for (int i = 0; i < imageSize.height(); i++) {
                                baos.write(filterType);
                                baos.write(imageData, i*imageSize.stride(), imageSize.stride());
                        }
                        return baos.toByteArray();
                }
        }

        private ByteBuffer splitDataIntoIdatChunks(int chunkSize, byte[] compressedData) {
                CRC32 checkSum = new CRC32();
                int numChunks = (int)Math.ceil(compressedData.length / (double)chunkSize);
                int bufferCapacity = numChunks * CHUNK_METADATA_LENGTH + compressedData.length;
                ByteBuffer buffer = ByteBuffer.allocate(bufferCapacity);
                byte[] chunkType = IDAT_SIGNATURE;
                for (int i = 0; i < numChunks; i++) {
                        int offset = i * chunkSize;
                        int remainingBytes = compressedData.length - offset;
                        int chunkLength = Math.min(chunkSize, remainingBytes);
                        byte[] chunkData = new byte[chunkLength];
                        System.arraycopy(compressedData, offset, chunkData, 0, chunkLength);
                        checkSum.reset();
                        checkSum.update(chunkType);
                        checkSum.update(chunkData);
                        Chunk chunk = new Chunk(chunkType, chunkData, chunkLength, (int) checkSum.getValue());
                        buffer.put(encodeChunk(chunk));
                }
                return buffer;
        }


}


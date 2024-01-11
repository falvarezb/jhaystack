package fjab.haystack;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public class PngEncoder {

        public byte[] encode(Png png) {
                try(
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        DataOutputStream dos = new DataOutputStream(baos)
                ) {
                        dos.write(PngDecoder.PNG_SIGNATURE);
                        dos.write(encodeChunk(png.ihdr()).array());
                        dos.write(encodeIdat(png.imageSize(), png.imageData()).array());
                        dos.write(encodeChunk(png.iend()).array());
                        dos.flush();
                        return baos.toByteArray();
                } catch (IOException e) {
                        throw new RuntimeException(e);
                }
        }

        private ByteBuffer encodeChunk(Chunk chunk) {
                ByteBuffer buffer = ByteBuffer.allocate(12 + chunk.length());
                buffer.putInt(chunk.length());
                buffer.put(chunk.type());
                buffer.put(chunk.data());
                buffer.putInt(chunk.crc());
                buffer.flip();
                return buffer;
        }

        private ByteBuffer encodeIdat(ImageSize imageSize, byte[] imageData) throws IOException {
                try(
                        ByteArrayOutputStream baos = new ByteArrayOutputStream((imageSize.stride() + 1) + imageSize.height());
                        DeflaterOutputStream dos = new DeflaterOutputStream(baos)
                ) {
                        for (int i = 0; i < imageSize.height(); i++) {
                                byte filterType = 0;
                                dos.write(filterType);
                                dos.write(imageData, 1, imageSize.stride());
                        }
                        byte[] compressedData = baos.toByteArray();
                        // split compressed data into IDAT chunks of at most 2^16 - 1 bytes
                        int chunkSize = 65535;
                        int numChunks = compressedData.length / chunkSize;
                        int bufferCapacity = numChunks * (12 + chunkSize);
                        int lastChunkSize = compressedData.length % chunkSize;
                        if(compressedData.length % chunkSize != 0) {
                                numChunks++;
                                bufferCapacity += 12 + lastChunkSize;
                        }
                        ByteBuffer buffer = ByteBuffer.allocate(bufferCapacity);
                        for (int i = 0; i < numChunks; i++) {
                                int offset = i * chunkSize;
                                int length = Math.min(chunkSize, compressedData.length - offset);
                                byte[] chunkData = new byte[length];
                                System.arraycopy(compressedData, offset, chunkData, 0, length);
                                byte[] chunkType = new byte[] {73, 68, 65, 84};
                                CRC32 checkSum = new CRC32();
                                checkSum.update(chunkType);
                                checkSum.update(chunkData);
                                Chunk chunk = new Chunk(chunkType, chunkData, length, (int) checkSum.getValue());
                                buffer.put(encodeChunk(chunk));
                        }
                        return buffer;
                }
        }



}


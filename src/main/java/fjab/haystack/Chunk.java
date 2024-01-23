package fjab.haystack;

import java.util.Arrays;

/**
 * Chunk structure (<a href="https://www.w3.org/TR/png/#5Chunk-layout">Chunk layout</a>):<br>
 * - Length: 4-byte unsigned integer giving the number of bytes in the chunk's data field.<br>
 * - Chunk type: a sequence of 4 bytes defining the chunk type, e.g. for IHDR chunks, this sequence is 73 72 68 82.<br>
 * - Chunk data: the data bytes appropriate to the chunk type, if any<br>
 * - CRC: 32-bit CRC calculated on the preceding bytes in the chunk, including the chunk type field and chunk data fields,
 * but not including the length field. The CRC is always present, even for chunks containing no data
 */
public record Chunk(byte[] type, byte[] data, int length, int crc) {

    public static final byte[] IHDR_SIGNATURE = new byte[] {73, 72, 68, 82};
    public static final byte[] IDAT_SIGNATURE = new byte[] {73, 68, 65, 84};
    public static final byte[] IEND_SIGNATURE = new byte[] {73, 69, 78, 68};

    private static final int CHUNK_LENGTH_LENGTH = 4;
    private static final int CHUNK_TYPE_LENGTH = 4;
    private static final int CHUNK_CRC_LENGTH = 4;
    public static final int CHUNK_METADATA_LENGTH = CHUNK_LENGTH_LENGTH + CHUNK_TYPE_LENGTH + CHUNK_CRC_LENGTH;

    public boolean isIHDR() {
        return Arrays.equals(this.type, IHDR_SIGNATURE);
    }

    public boolean isIDAT() {
        return Arrays.equals(this.type, IDAT_SIGNATURE);
    }

    public boolean isIEND() {
        return Arrays.equals(this.type, IEND_SIGNATURE);
    }
}

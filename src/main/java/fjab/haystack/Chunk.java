package fjab.haystack;

import java.util.Arrays;

public record Chunk(byte[] type, byte[] data, int length, int crc) {

    public static byte[] iHdrSignature = new byte[] {73, 72, 68, 82};
    public static byte[] iDatSignature = new byte[] {73, 68, 65, 84};
    public static byte[] iEndSignature = new byte[] {73, 69, 78, 68};

    public boolean isIHDR() {
        return Arrays.equals(this.type, iHdrSignature);
    }

    public boolean isIDAT() {
        return Arrays.equals(this.type, iDatSignature);
    }

    public boolean isIEND() {
        return Arrays.equals(this.type, iEndSignature);
    }
}

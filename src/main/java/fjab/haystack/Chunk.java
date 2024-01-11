package fjab.haystack;

public record Chunk(byte[] type, byte[] data, int length, int crc) {

    public boolean isIHDR() {
        return type[0] == 73 && type[1] == 72 && type[2] == 68 && type[3] == 82;
    }

    public boolean isIDAT() {
        return type[0] == 73 && type[1] == 68 && type[2] == 65 && type[3] == 84;
    }

    public boolean isIEND() {
        return type[0] == 73 && type[1] == 69 && type[2] == 78 && type[3] == 68;
    }
}

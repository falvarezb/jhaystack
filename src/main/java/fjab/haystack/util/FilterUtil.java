package fjab.haystack.util;

import fjab.haystack.domain.ImageSize;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;

public class FilterUtil {

    /**
     * Apply filterType = 0 (no filter) to each scanline
     */
    public static byte[] filter(ImageSize imageSize, byte[] imageData) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream((imageSize.stride() + 1) + imageSize.height())) {
            byte filterType = 0;
            for (int i = 0; i < imageSize.height(); i++) {
                baos.write(filterType);
                baos.write(imageData, i * imageSize.stride(), imageSize.stride());
            }
            return baos.toByteArray();
        }
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
     * <p>
     * Filter types (<a href="https://www.w3.org/TR/png/#9-table91">Filter types</a>): <br>
     * 0: None <br>
     * 1: Sub <br>
     * 2: Up <br>
     * 3: Average <br>
     * 4: Paeth <br>
     */
    public static byte[] unfilter(byte[] decompressedIdatData, ImageSize imageSize) throws IOException {
        int height = imageSize.height();
        int stride = imageSize.stride();
        int bytesPerPixel = imageSize.bytesPerPixel();
        try (DataInputStream di = new DataInputStream(new ByteArrayInputStream(decompressedIdatData))) {
            byte[] unfilteredData = new byte[height * stride];

            byte[] previousRow = new byte[stride];
            for (int scanline_idx = 0; scanline_idx < height; scanline_idx++) {
                int unfilteredDataOffset = scanline_idx * stride;
                byte filterType = di.readByte();
                byte[] scanline = new byte[stride];
                di.readFully(scanline);
                switch (filterType) {
                    case 0 -> System.arraycopy(scanline, 0, unfilteredData, unfilteredDataOffset, stride); //None
                    case 1 -> { //Sub
                        for (int byte_idx = 0; byte_idx < stride; byte_idx++) {
                            byte x = scanline[byte_idx];
                            byte a = reconA(scanline_idx, byte_idx, unfilteredData, bytesPerPixel, stride);
                            unfilteredData[byte_idx + unfilteredDataOffset] = (byte) (x + a);
                        }
                    }
                    case 2 -> { //Up
                        for (int byte_idx = 0; byte_idx < stride; byte_idx++) {
                            byte x = scanline[byte_idx];
                            byte b = reconB(scanline_idx, byte_idx, previousRow);
                            unfilteredData[byte_idx + unfilteredDataOffset] = (byte) (x + b);
                        }
                    }
                    case 3 -> {//Average
                        for (int byte_idx = 0; byte_idx < stride; byte_idx++) {
                            byte x = scanline[byte_idx];
                            byte a = reconA(scanline_idx, byte_idx, unfilteredData, bytesPerPixel, stride);
                            byte b = reconB(scanline_idx, byte_idx, previousRow);
                            /*
                                In Java, the byte data type has a range from -128 to 127
                                However, in the context of this function, bytes are treated as unsigned and have a range from 0 to 255.
                                To fix this and make sure that the result of the division is correct,
                                byte values need to be converted to int in the range of 0 to 255.
                             */
                            int aInt = a & 0xFF;
                            int bInt = b & 0xFF;
                            unfilteredData[byte_idx + unfilteredDataOffset] = (byte) (x + (aInt + bInt) / 2);
                        }
                    }
                    case 4 -> { //Paeth
                        for (int byte_idx = 0; byte_idx < stride; byte_idx++) {
                            byte x = scanline[byte_idx];
                            byte a = reconA(scanline_idx, byte_idx, unfilteredData, bytesPerPixel, stride);
                            byte b = reconB(scanline_idx, byte_idx, previousRow);
                            byte c = reconC(scanline_idx, byte_idx, previousRow, bytesPerPixel);
                            unfilteredData[byte_idx + unfilteredDataOffset] = (byte) (x + paethPredictor(a, b, c));
                        }
                    }
                    default -> throw new RuntimeException("Unsupported filter type: " + filterType);
                }
                System.arraycopy(unfilteredData, unfilteredDataOffset, previousRow, 0, stride);
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
            To fix this and make sure that comparisons between values 'pa', 'pb' and 'pc' are correct,
            byte values need to be converted to int in the range of 0 to 255.
         */
        int aInt = a & 0xFF;
        int bInt = b & 0xFF;
        int cInt = c & 0xFF;

        int p = aInt + bInt - cInt;
        int pa = Math.abs(p - aInt);
        int pb = Math.abs(p - bInt);
        int pc = Math.abs(p - cInt);
        if (pa <= pb && pa <= pc) return a;
        if (pb <= pc) return b;
        return c;
    }
}

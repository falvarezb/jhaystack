package fjab.haystack.util;

import fjab.haystack.domain.ImageSize;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

    public static byte[] unfilterPar(byte[] decompressedIdatData, ImageSize imageSize) {
        Map<Byte, List<Integer>> scanlinesByFilterType = groupScanlinesByFilterType(decompressedIdatData, imageSize);
        int height = imageSize.height();
        int stride = imageSize.stride();

        byte[] unfilteredData = new byte[height * stride];

        // parallel processing of scanlines with filter type 0 or 1
        scanlinesByFilterType.entrySet().parallelStream()
                .filter(entry -> entry.getKey() == 0 || entry.getKey() == 1)
                .flatMap(entry -> entry.getValue().stream())
                .forEach(scanline_idx -> processScanline(scanline_idx, decompressedIdatData, unfilteredData, imageSize));

        // sequential processing of scanlines with filter type 2, 3 or 4
        scanlinesByFilterType.entrySet().stream()
                .filter(entry -> entry.getKey() != 0 && entry.getKey() != 1)
                .flatMap(entry -> entry.getValue().stream())
                .sorted()
                .forEach(scanline_idx -> processScanline(scanline_idx, decompressedIdatData, unfilteredData, imageSize));

        assert decompressedIdatData.length == unfilteredData.length + height;
        return unfilteredData;
    }


    public static byte[] unfilter(byte[] decompressedIdatData, ImageSize imageSize) {
        int height = imageSize.height();
        int stride = imageSize.stride();

        byte[] unfilteredData = new byte[height * stride];
        for (int scanline_idx = 0; scanline_idx < height; scanline_idx++) {
            processScanline(scanline_idx, decompressedIdatData, unfilteredData, imageSize);
        }
        assert decompressedIdatData.length == unfilteredData.length + height;
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
     * <p>
     * Filter types (<a href="https://www.w3.org/TR/png/#9-table91">Filter types</a>): <br>
     * 0: None <br>
     * 1: Sub <br>
     * 2: Up <br>
     * 3: Average <br>
     * 4: Paeth <br>
     */
    private static void processScanline(int scanline_idx, byte[] decompressedIdatData, byte[] unfilteredData, ImageSize imageSize) {
        int stride = imageSize.stride();
        int bytesPerPixel = imageSize.bytesPerPixel();
        byte[] scanline = new byte[stride];
        int decompressedDataOffset = scanline_idx * (stride + 1); // +1 because of the filter type byte
        int unfilteredDataOffset = scanline_idx * stride;
        byte filterType = decompressedIdatData[decompressedDataOffset];
        System.arraycopy(decompressedIdatData, decompressedDataOffset + 1, scanline, 0, stride);
        switch (filterType) {
            case 0 -> System.arraycopy(scanline, 0, unfilteredData, unfilteredDataOffset, scanline.length); //None
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
                    byte b = reconB(scanline_idx, byte_idx, unfilteredData, stride);
                    unfilteredData[byte_idx + unfilteredDataOffset] = (byte) (x + b);
                }
            }
            case 3 -> {//Average
                for (int byte_idx = 0; byte_idx < stride; byte_idx++) {
                    byte x = scanline[byte_idx];
                    byte a = reconA(scanline_idx, byte_idx, unfilteredData, bytesPerPixel, stride);
                    byte b = reconB(scanline_idx, byte_idx, unfilteredData, stride);
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
                    byte b = reconB(scanline_idx, byte_idx, unfilteredData, stride);
                    byte c = reconC(scanline_idx, byte_idx, unfilteredData, bytesPerPixel, stride);
                    unfilteredData[byte_idx + unfilteredDataOffset] = (byte) (x + paethPredictor(a, b, c));
                }
            }
            default -> throw new RuntimeException("Unsupported filter type: " + filterType);
        }
    }

    private static Map<Byte, List<Integer>> groupScanlinesByFilterType(byte[] decompressedIdatData, ImageSize imageSize) {
        return IntStream.range(0, imageSize.height())
                .boxed()
                .collect(Collectors.groupingBy(i -> decompressedIdatData[i * (imageSize.stride() + 1)]));
    }

    private static byte reconC(int scanline_index, int byte_index_in_scanline, byte[] unfilteredData, int bytesPerPixel, int stride) {
        return byte_index_in_scanline < bytesPerPixel || scanline_index == 0 ? 0 : unfilteredData[(scanline_index-1) * stride + byte_index_in_scanline - bytesPerPixel];
    }

    private static byte reconB(int scanline_index, int byte_index_in_scanline, byte[] unfilteredData, int stride) {
        return scanline_index == 0 ? 0 : unfilteredData[(scanline_index-1) * stride + byte_index_in_scanline];
    }

    private static byte reconA(int scanline_index, int byte_index_in_scanline, byte[] unfilteredData, int bytesPerPixel, int stride) {
        return byte_index_in_scanline < bytesPerPixel ? 0 : unfilteredData[ scanline_index * stride + byte_index_in_scanline - bytesPerPixel];
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

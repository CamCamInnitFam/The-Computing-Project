import com.github.luben.zstd.ZstdInputStream;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import org.jtransforms.dct.DoubleDCT_2D;

public class GameByteDecompressor {

    private static final int BLOCK_SIZE = 8;
    private static final int[][] LUMINANCE_QUANT_MATRIX = {
            {16, 11, 10, 16, 24, 40, 51, 61},
            {12, 12, 14, 19, 26, 58, 60, 55},
            {14, 13, 16, 24, 40, 57, 69, 56},
            {14, 17, 22, 29, 51, 87, 80, 62},
            {18, 22, 37, 56, 68, 109, 103, 77},
            {24, 35, 55, 64, 81, 104, 113, 92},
            {49, 64, 78, 87, 103, 121, 120, 101},
            {72, 92, 95, 98, 112, 100, 103, 99}
    };
    private static final int[][] CHROMINANCE_QUANT_MATRIX = {
            {17, 18, 24, 47, 99, 99, 99, 99},
            {18, 21, 26, 66, 99, 99, 99, 99},
            {24, 26, 56, 99, 99, 99, 99, 99},
            {47, 66, 99, 99, 99, 99, 99, 99},
            {99, 99, 99, 99, 99, 99, 99, 99},
            {99, 99, 99, 99, 99, 99, 99, 99},
            {99, 99, 99, 99, 99, 99, 99, 99},
            {99, 99, 99, 99, 99, 99, 99, 99}
    };
    private static final int[] ZIGZAG_ORDER = {
            0, 1, 8, 16, 9, 2, 3, 10, 17, 24, 32, 25, 18, 11, 4, 5,
            12, 19, 26, 33, 40, 48, 41, 34, 27, 20, 13, 6, 7, 14, 21, 28,
            35, 42, 49, 56, 57, 50, 43, 36, 29, 22, 15, 23, 30, 37, 44, 51,
            58, 59, 52, 45, 38, 31, 39, 46, 53, 60, 61, 54, 47, 55, 62, 63
    };

    public static void main(String[] args) {
        String inputFile = "E:\\Uni work\\Computing Project\\The-Computing-Project\\GameByte\\src\\main\\resources\\assets\\textures\\948688.byt";
        String outputFile = "E:\\Uni work\\Computing Project\\The-Computing-Project\\GameByte\\src\\main\\resources\\assets\\textures\\948688_decompressed.jpg";

        long startTime = System.nanoTime();
        try (ZstdInputStream zis = new ZstdInputStream(new FileInputStream(inputFile));
             DataInputStream dis = new DataInputStream(zis)) {
            // Read header
            int originalWidth = dis.readInt();
            int originalHeight = dis.readInt();
            int paddedWidthY = dis.readInt();
            int paddedHeightY = dis.readInt();
            int paddedWidthChroma = dis.readInt();
            int paddedHeightChroma = dis.readInt();
            int numQualityLevels = dis.readInt();
            float[] qualityLevels = new float[numQualityLevels];
            for (int i = 0; i < numQualityLevels; i++) {
                qualityLevels[i] = dis.readFloat();
            }

            // Process channels
            double[][] paddedY = processChannel(dis, paddedHeightY, paddedWidthY, LUMINANCE_QUANT_MATRIX, qualityLevels);
            double[][] paddedCb = processChannel(dis, paddedHeightChroma, paddedWidthChroma, CHROMINANCE_QUANT_MATRIX, qualityLevels);
            double[][] paddedCr = processChannel(dis, paddedHeightChroma, paddedWidthChroma, CHROMINANCE_QUANT_MATRIX, qualityLevels);

            // Upsample Cb and Cr
            double[][] fullCb = upsample(paddedCb, paddedHeightY, paddedWidthY);
            double[][] fullCr = upsample(paddedCr, paddedHeightY, paddedWidthY);

            // Convert to RGB and create image
            BufferedImage image = new BufferedImage(originalWidth, originalHeight, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < originalHeight; y++) {
                for (int x = 0; x < originalWidth; x++) {
                    double yVal = paddedY[y][x];
                    double cbVal = fullCb[y][x];
                    double crVal = fullCr[y][x];
                    int r = (int) Math.round(yVal + 1.402 * (crVal - 128));
                    int g = (int) Math.round(yVal - 0.34414 * (cbVal - 128) - 0.71414 * (crVal - 128));
                    int b = (int) Math.round(yVal + 1.772 * (cbVal - 128));
                    r = Math.max(0, Math.min(255, r));
                    g = Math.max(0, Math.min(255, g));
                    b = Math.max(0, Math.min(255, b));
                    int rgb = (r << 16) | (g << 8) | b;
                    image.setRGB(x, y, rgb);
                }
            }

            // Write JPEG image
            ImageIO.write(image, "jpg", new File(outputFile));
            System.out.println("Decompression complete and saved as: " + outputFile);

            //Time
            long endTime = System.nanoTime();
            double timeTakenMs = (endTime - startTime) / 1_000_000.0;
            double timeTakenS = (endTime - startTime) / 1_000_000_000.0;
            System.out.println("\nTime taken: " + String.format("%.2f", timeTakenMs) + " Ms/" + String.format("%.2f", timeTakenS) + " S");

            //Size
            File compressedFile = new File(inputFile);
            long fileSizeBytes1 = compressedFile.length();
            double fileSizeKb1 = fileSizeBytes1 / 1024.0;
            System.out.println("\nCompressed File Size (.byt): " + fileSizeBytes1 + " bytes (" + String.format("%.2f", fileSizeKb1) + " KB)");

            File decompressedFile = new File(outputFile);
            long fileSizeBytes = decompressedFile.length();
            double fileSizeKB = fileSizeBytes / 1024.0;
            System.out.println("\nDecompressed File size (jpg): " + fileSizeBytes + " bytes (" +
                    String.format("%.2f", fileSizeKB) + " KB)");

            long fileSizeDifferenceBytes = fileSizeBytes - fileSizeBytes1;
            double fileSizeDifferenceKB = fileSizeKB - fileSizeKb1;

            System.out.println("\nDifference in file size: " + fileSizeDifferenceBytes + " bytes (" + String.format("%.2f", fileSizeDifferenceKB) + " KB)");
        } catch (IOException e) {
            System.err.println("Error during decompression: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static double[][] processChannel(DataInputStream dis, int height, int width, int[][] quantMatrix, float[] qualityLevels) throws IOException {
        double[][] channel = new double[height][width];
        DoubleDCT_2D dct = new DoubleDCT_2D(BLOCK_SIZE, BLOCK_SIZE);
        for (int y = 0; y < height; y += BLOCK_SIZE) {
            for (int x = 0; x < width; x += BLOCK_SIZE) {
                double[] zigZag = unpackIndices(dis, qualityLevels);
                double[][] block = inverseZigZagScan(zigZag, quantMatrix);
                dct.inverse(block, true);
                for (int i = 0; i < BLOCK_SIZE; i++) {
                    for (int j = 0; j < BLOCK_SIZE; j++) {
                        channel[y + i][x + j] = block[i][j] + 128;
                    }
                }
            }
        }
        return channel;
    }

    private static double[] unpackIndices(DataInputStream dis, float[] qualityLevels) throws IOException {
        int length = dis.readByte();
        if (length == 0) {
            return new double[BLOCK_SIZE * BLOCK_SIZE];
        }
        double[] zigZag = new double[BLOCK_SIZE * BLOCK_SIZE];
        for (int i = 0; i < length; i++) {
            int value = dis.readByte();
            if (value == Byte.MAX_VALUE + 1) {
                value = dis.readInt();
            }
            zigZag[i] = value;
        }
        return zigZag;
    }

    private static double[][] inverseZigZagScan(double[] zigZag, int[][] quantMatrix) {
        double[][] block = new double[BLOCK_SIZE][BLOCK_SIZE];
        for (int i = 0; i < ZIGZAG_ORDER.length; i++) {
            int row = ZIGZAG_ORDER[i] / BLOCK_SIZE;
            int col = ZIGZAG_ORDER[i] % BLOCK_SIZE;
            block[row][col] = zigZag[i] * quantMatrix[row][col];
        }
        return block;
    }

    private static double[][] upsample(double[][] subsampled, int targetHeight, int targetWidth) {
        double[][] upsampled = new double[targetHeight][targetWidth];
        int subsampledHeight = subsampled.length;
        int subsampledWidth = subsampled[0].length;
        for (int y = 0; y < targetHeight; y++) {
            for (int x = 0; x < targetWidth; x++) {
                int srcY = y / 2;
                int srcX = x / 2;
                if (srcY < subsampledHeight && srcX < subsampledWidth) {
                    upsampled[y][x] = subsampled[srcY][srcX];
                } else {
                    upsampled[y][x] = 128; // Default padding
                }
            }
        }
        return upsampled;
    }
}
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.zip.GZIPInputStream;
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
        long startTime = System.nanoTime();
        String inputFile = "E:\\Uni work\\Computing Project\\The-Computing-Project\\GameByte\\src\\main\\resources\\assets\\textures\\1304 2.byt";
        String outputFile = "E:\\Uni work\\Computing Project\\The-Computing-Project\\GameByte\\src\\main\\resources\\assets\\textures\\1304 2.jpg";

        try (DataInputStream dis = new DataInputStream(new GZIPInputStream(new FileInputStream(inputFile)))) {
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
            long endTime = System.nanoTime();
            double timeTakenMs = (endTime - startTime) / 1_000_000.0;
            double timeTakenS = (endTime - startTime) / 1_000_000_000.0;
            System.out.println("Decompression complete and saved as: " + outputFile);
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
        int blocksDown = height / BLOCK_SIZE;
        int blocksAcross = width / BLOCK_SIZE;
        int numBlocks = blocksDown * blocksAcross;

        // Read packed indices
        int packedSize = dis.readInt();
        byte[] packedIndices = new byte[packedSize];
        dis.readFully(packedIndices);
        int[] indices = unpackIndices(packedIndices, numBlocks);

        double[][] channel = new double[height][width];
        DoubleDCT_2D dct = new DoubleDCT_2D(BLOCK_SIZE, BLOCK_SIZE);

        for (int by = 0; by < blocksDown; by++) {
            for (int bx = 0; bx < blocksAcross; bx++) {
                int index = indices[by * blocksAcross + bx];
                float quality = qualityLevels[index];
                byte[] zigzag = new byte[64];
                dis.readFully(zigzag);
                int[][] quantBlock = inverseZigZagScan(zigzag);
                double[][] dctBlock = dequantizeBlock(quantBlock, quantMatrix, quality);
                dct.inverse(dctBlock, true);
                for (int i = 0; i < BLOCK_SIZE; i++) {
                    for (int j = 0; j < BLOCK_SIZE; j++) {
                        channel[by * BLOCK_SIZE + i][bx * BLOCK_SIZE + j] = dctBlock[i][j] + 128.0;
                    }
                }
            }
        }
        return channel;
    }

    private static int[] unpackIndices(byte[] packed, int numBlocks) {
        int[] indices = new int[numBlocks];
        for (int m = 0; m < packed.length; m++) {
            byte b = packed[m];
            for (int j = 0; j < 4 && 4 * m + j < numBlocks; j++) {
                indices[4 * m + j] = (b >> (6 - 2 * j)) & 3;
            }
        }
        return indices;
    }

    private static int[][] inverseZigZagScan(byte[] zigzag) {
        int[][] block = new int[BLOCK_SIZE][BLOCK_SIZE];
        for (int k = 0; k < 64; k++) {
            int index = ZIGZAG_ORDER[k];
            int row = index / BLOCK_SIZE;
            int col = index % BLOCK_SIZE;
            block[row][col] = zigzag[k];
        }
        return block;
    }

    private static double[][] dequantizeBlock(int[][] quantBlock, int[][] quantMatrix, float quality) {
        double[][] dctBlock = new double[BLOCK_SIZE][BLOCK_SIZE];
        for (int i = 0; i < BLOCK_SIZE; i++) {
            for (int j = 0; j < BLOCK_SIZE; j++) {
                dctBlock[i][j] = quantBlock[i][j] * quantMatrix[i][j] * quality;
            }
        }
        return dctBlock;
    }

    private static double[][] upsample(double[][] channel, int targetHeight, int targetWidth) {
        int height = channel.length;
        int width = channel[0].length;
        double[][] upsampled = new double[targetHeight][targetWidth];
        for (int y = 0; y < targetHeight; y++) {
            int sy = Math.min(y * height / targetHeight, height - 1);
            for (int x = 0; x < targetWidth; x++) {
                int sx = Math.min(x * width / targetWidth, width - 1);
                upsampled[y][x] = channel[sy][sx];
            }
        }
        return upsampled;
    }
}
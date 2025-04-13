import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.zip.GZIPOutputStream;
import org.jtransforms.dct.DoubleDCT_2D;

public class GameByteCompressor {

    private static final int BLOCK_SIZE = 8;
    private static final float[] QUALITY_LEVELS = {0.75f, 1.0f, 1.25f, 1.5f};
    private static final double BASE_QUALITY = 1.0;
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
        String inputFile = "E:\\Uni work\\Computing Project\\The-Computing-Project\\GameByte\\src\\main\\resources\\assets\\textures\\948688.jpg";
        String outputFile = "E:\\Uni work\\Computing Project\\The-Computing-Project\\GameByte\\src\\main\\resources\\assets\\textures\\1304 2.byt";
        try {
            //Get time
            long startTime = System.nanoTime();
            // Read JPEG image
            BufferedImage image = ImageIO.read(new File(inputFile));
            if (image == null) {
                System.err.println("Failed to read image: " + inputFile);
                return;
            }
            int width = image.getWidth();
            int height = image.getHeight();

            // Convert to YCbCr
            double[][] Y = new double[height][width];
            double[][] Cb = new double[height][width];
            double[][] Cr = new double[height][width];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int rgb = image.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;
                    Y[y][x] = 0.299 * r + 0.587 * g + 0.114 * b;
                    Cb[y][x] = -0.1687 * r - 0.3313 * g + 0.5 * b + 128;
                    Cr[y][x] = 0.5 * r - 0.4187 * g - 0.0813 * b + 128;
                }
            }

            // Subsample Cb and Cr (4:2:0)
            int subsampledHeight = (height + 1) / 2;
            int subsampledWidth = (width + 1) / 2;
            double[][] subsampledCb = subsample(Cb, height, width);
            double[][] subsampledCr = subsample(Cr, height, width);

            // Pad channels
            int paddedHeightY = ((height + BLOCK_SIZE - 1) / BLOCK_SIZE) * BLOCK_SIZE;
            int paddedWidthY = ((width + BLOCK_SIZE - 1) / BLOCK_SIZE) * BLOCK_SIZE;
            double[][] paddedY = padChannel(Y, paddedHeightY, paddedWidthY, height, width);

            int paddedHeightChroma = ((subsampledHeight + BLOCK_SIZE - 1) / BLOCK_SIZE) * BLOCK_SIZE;
            int paddedWidthChroma = ((subsampledWidth + BLOCK_SIZE - 1) / BLOCK_SIZE) * BLOCK_SIZE;
            double[][] paddedCb = padChannel(subsampledCb, paddedHeightChroma, paddedWidthChroma, subsampledHeight, subsampledWidth);
            double[][] paddedCr = padChannel(subsampledCr, paddedHeightChroma, paddedWidthChroma, subsampledHeight, subsampledWidth);

            // Compress and write output
            try (DataOutputStream dos = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(outputFile)))) {
                // Write header
                dos.writeInt(width);
                dos.writeInt(height);
                dos.writeInt(paddedWidthY);
                dos.writeInt(paddedHeightY);
                dos.writeInt(paddedWidthChroma);
                dos.writeInt(paddedHeightChroma);
                dos.writeInt(QUALITY_LEVELS.length);
                for (float level : QUALITY_LEVELS) {
                    dos.writeFloat(level);
                }

                // Process channels
                processChannel(paddedY, paddedHeightY, paddedWidthY, LUMINANCE_QUANT_MATRIX, dos);
                processChannel(paddedCb, paddedHeightChroma, paddedWidthChroma, CHROMINANCE_QUANT_MATRIX, dos);
                processChannel(paddedCr, paddedHeightChroma, paddedWidthChroma, CHROMINANCE_QUANT_MATRIX, dos);
                long endTime = System.nanoTime();
                System.out.println("Image Compressed Successfully and Saved as: " + outputFile);
                double timeTakenMs = (endTime - startTime) / 1_000_000.0;
                double timeTakenS = (endTime - startTime) / 1_000_000_000.0;
                System.out.printf("\nTime taken to compress: %.2f ms%n", timeTakenMs);
                System.out.println(String.format("%.2f", timeTakenS) + " seconds.");

                //Output size & Comparison

                File uncompressedFile = new File(inputFile);
                long fileSizeBytes = uncompressedFile.length();
                double fileSizeKB1 = fileSizeBytes / 1024.0;
                System.out.println("\nOriginal File Size: " +
                        fileSizeBytes + " bytes(" + String.format("%.2f", fileSizeKB1) + " KB)");

                File compressedFile = new File(outputFile);
                fileSizeBytes = compressedFile.length();
                double fileSizeKB2 = fileSizeBytes / 1024.0;
                System.out.println("\nCompressed File Size: " +
                        fileSizeBytes + " bytes (" + String.format("%.2f", fileSizeKB2) + " KB)");

                System.out.println("\nSize Difference: " + String.format("%.2f", (fileSizeKB2 - fileSizeKB1)) + " KB");

            }
        } catch (IOException e) {
            System.err.println("Error during compression: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static double[][] subsample(double[][] channel, int height, int width) {
        int subsampledHeight = (height + 1) / 2;
        int subsampledWidth = (width + 1) / 2;
        double[][] subsampled = new double[subsampledHeight][subsampledWidth];
        for (int sy = 0; sy < subsampledHeight; sy++) {
            for (int sx = 0; sx < subsampledWidth; sx++) {
                double sum = 0;
                int count = 0;
                for (int dy = 0; dy < 2; dy++) {
                    int y = sy * 2 + dy;
                    if (y >= height) continue;
                    for (int dx = 0; dx < 2; dx++) {
                        int x = sx * 2 + dx;
                        if (x >= width) continue;
                        sum += channel[y][x];
                        count++;
                    }
                }
                subsampled[sy][sx] = count > 0 ? sum / count : 0;
            }
        }
        return subsampled;
    }

    private static double[][] padChannel(double[][] channel, int paddedHeight, int paddedWidth, int originalHeight, int originalWidth) {
        double[][] padded = new double[paddedHeight][paddedWidth];
        for (int y = 0; y < paddedHeight; y++) {
            int srcY = Math.min(y, originalHeight - 1);
            for (int x = 0; x < paddedWidth; x++) {
                int srcX = Math.min(x, originalWidth - 1);
                padded[y][x] = channel[srcY][srcX];
            }
        }
        return padded;
    }

    private static void processChannel(double[][] channel, int height, int width, int[][] quantMatrix, DataOutputStream dos) throws IOException {
        int blocksDown = height / BLOCK_SIZE;
        int blocksAcross = width / BLOCK_SIZE;
        int numBlocks = blocksDown * blocksAcross;
        int[] indices = new int[numBlocks];
        ByteArrayOutputStream blockDataStream = new ByteArrayOutputStream();
        DoubleDCT_2D dct = new DoubleDCT_2D(BLOCK_SIZE, BLOCK_SIZE);

        for (int by = 0; by < blocksDown; by++) {
            for (int bx = 0; bx < blocksAcross; bx++) {
                double[][] block = new double[BLOCK_SIZE][BLOCK_SIZE];
                for (int i = 0; i < BLOCK_SIZE; i++) {
                    for (int j = 0; j < BLOCK_SIZE; j++) {
                        int y = by * BLOCK_SIZE + i;
                        int x = bx * BLOCK_SIZE + j;
                        block[i][j] = channel[y][x];
                    }
                }
                double variance = computeVariance(block);
                double quality = adjustQuality(variance, BASE_QUALITY);
                int index = findClosestLevel(quality, QUALITY_LEVELS);
                indices[by * blocksAcross + bx] = index;
                for (int i = 0; i < BLOCK_SIZE; i++) {
                    for (int j = 0; j < BLOCK_SIZE; j++) {
                        block[i][j] -= 128.0;
                    }
                }
                dct.forward(block, true);
                int[][] quantBlock = quantizeBlock(block, quantMatrix, QUALITY_LEVELS[index]);
                byte[] zigzag = zigZagScan(quantBlock);
                blockDataStream.write(zigzag);
            }
        }
        byte[] packedIndices = packIndices(indices);
        dos.writeInt(packedIndices.length);
        dos.write(packedIndices);
        dos.write(blockDataStream.toByteArray());
    }

    private static double computeVariance(double[][] block) {
        double mean = 0;
        for (int i = 0; i < BLOCK_SIZE; i++) {
            for (int j = 0; j < BLOCK_SIZE; j++) {
                mean += block[i][j];
            }
        }
        mean /= (BLOCK_SIZE * BLOCK_SIZE);
        double variance = 0;
        for (int i = 0; i < BLOCK_SIZE; i++) {
            for (int j = 0; j < BLOCK_SIZE; j++) {
                double diff = block[i][j] - mean;
                variance += diff * diff;
            }
        }
        return variance / (BLOCK_SIZE * BLOCK_SIZE);
    }

    private static double adjustQuality(double variance, double baseQuality) {
        if (variance < 100) return baseQuality * 0.75;
        else if (variance < 500) return baseQuality * 1.0;
        else return baseQuality * 1.5;
    }

    private static int findClosestLevel(double quality, float[] qualityLevels) {
        int closestIndex = 0;
        double minDiff = Math.abs(quality - qualityLevels[0]);
        for (int i = 1; i < qualityLevels.length; i++) {
            double diff = Math.abs(quality - qualityLevels[i]);
            if (diff < minDiff) {
                minDiff = diff;
                closestIndex = i;
            }
        }
        return closestIndex;
    }

    private static int[][] quantizeBlock(double[][] dctBlock, int[][] quantMatrix, float quality) {
        int[][] quantBlock = new int[BLOCK_SIZE][BLOCK_SIZE];
        for (int i = 0; i < BLOCK_SIZE; i++) {
            for (int j = 0; j < BLOCK_SIZE; j++) {
                int quantValue = (int) Math.round(dctBlock[i][j] / (quantMatrix[i][j] * quality));
                quantBlock[i][j] = Math.max(-128, Math.min(127, quantValue));
            }
        }
        return quantBlock;
    }

    private static byte[] zigZagScan(int[][] block) {
        byte[] zigzag = new byte[64];
        for (int k = 0; k < 64; k++) {
            int index = ZIGZAG_ORDER[k];
            int row = index / BLOCK_SIZE;
            int col = index % BLOCK_SIZE;
            zigzag[k] = (byte) block[row][col];
        }
        return zigzag;
    }

    private static byte[] packIndices(int[] indices) {
        int N = indices.length;
        int packedSize = (N + 3) / 4;
        byte[] packed = new byte[packedSize];
        for (int m = 0; m < packedSize; m++) {
            int b = 0;
            for (int j = 0; j < 4 && 4 * m + j < N; j++) {
                b |= (indices[4 * m + j] & 3) << (6 - 2 * j);
            }
            packed[m] = (byte) b;
        }
        return packed;
    }
}
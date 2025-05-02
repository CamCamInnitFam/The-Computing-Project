import com.github.luben.zstd.ZstdOutputStream;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
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

    public static void main(String[] args) throws Exception{
        if(args.length <2){
            System.err.println("Usage: java GameByteCompressor <input.jpg/input.png> <output.byt>");
            System.exit(1);
        }
        compress(args[0], args[1]);
    }

    public static void compress(String inputPath, String outputPath) throws Exception{

        try {
            if(!inputPath.contains(".jpg") && !inputPath.contains(".png")){
                System.err.println("Error: Input file must be .jpg or .png");
                return;
            }

            long startTime = System.nanoTime();
            // Read JPEG image
            BufferedImage image = ImageIO.read(new File(inputPath));
            if (image == null) {
                System.err.println("Failed to read image: " + inputPath);
                return;
            }
            int width = image.getWidth();
            int height = image.getHeight();

            // Convert to YCbCr
            double[][] Y = new double[height][width];
            double[][] Cb = new double[height][width];
            double[][] Cr = new double[height][width];
            for (int y = 0; y < height; y++) {
                for (int x =
                     0; x < width; x++) {
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
            double[][] paddedCb = padChannel(subsampledCb, paddedHeightChroma,
                    paddedWidthChroma, subsampledHeight, subsampledWidth);
            double[][] paddedCr = padChannel(subsampledCr, paddedHeightChroma,
                    paddedWidthChroma, subsampledHeight, subsampledWidth);

            // Compress and write output using Zstd
            try (ZstdOutputStream zos = new ZstdOutputStream(new FileOutputStream(outputPath));
                 DataOutputStream dos = new DataOutputStream(zos)) {
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

                System.out.println("Image Compressed Successfully and Saved as: " + outputPath);

                //Evaluation
                long endTime = System.nanoTime();
                double timeTakenMs = (endTime - startTime) / 1_000_000.0;
                double timeTakenS = (endTime - startTime) / 1_000_000_000.0;
                System.out.printf("\nTime taken to compress: %.2f ms%n", timeTakenMs);
                System.out.println(String.format("%.2f", timeTakenS) + " seconds.");

                //clear
                dos.close();
                zos.close();

                //Output size & Comparison
                File uncompressedFile = new File(inputPath);
                long fileSizeBytes = uncompressedFile.length();
                double fileSizeKB1 = fileSizeBytes / 1024.0;
                System.out.println("\nOriginal File Size: " +
                        fileSizeBytes + " bytes(" + String.format("%.2f", fileSizeKB1) + " KB)");

                File compressedFile = new File(outputPath);
                //if(compressedFile.exists() && compressedFile.canRead())
                fileSizeBytes = compressedFile.length();
                double fileSizeKB2 = fileSizeBytes / 1024.0;
                System.out.println("\nCompressed File Size: " +
                        fileSizeBytes + " bytes (" + String.format("%.2f", fileSizeKB2) + " KB)");

                System.out.println("\nSize Reduction: " + String.format("%.2f", (fileSizeKB2 - fileSizeKB1)) + " KB");

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
        for (int y = 0; y < subsampledHeight; y++) {
            for (int x = 0; x < subsampledWidth; x++) {
                int srcY = y * 2;
                int srcX = x * 2;
                double sum = 0;
                int count = 0;
                for (int dy = 0; dy < 2 && srcY + dy < height; dy++) {
                    for (int dx = 0; dx < 2 && srcX + dx < width; dx++) {
                        sum += channel[srcY + dy][srcX + dx];
                        count++;
                    }
                }
                subsampled[y][x] = sum / count;
            }
        }
        return subsampled;
    }

    private static double[][] padChannel(double[][] channel, int paddedHeight, int paddedWidth, int originalHeight, int originalWidth) {
        double[][] padded = new double[paddedHeight][paddedWidth];
        for (int y = 0; y < paddedHeight; y++) {
            for (int x = 0; x < paddedWidth; x++) {
                if (y < originalHeight && x < originalWidth) {
                    padded[y][x] = channel[y][x];
                } else {
                    padded[y][x] = 128; // Padding with neutral value
                }
            }
        }
        return padded;
    }

    private static void processChannel(double[][] channel, int height, int width,
                                       int[][] quantMatrix, DataOutputStream dos)
                                        throws IOException {
        DoubleDCT_2D dct = new DoubleDCT_2D(BLOCK_SIZE, BLOCK_SIZE);
        for (int y = 0; y < height; y += BLOCK_SIZE) {
            for (int x = 0; x < width; x += BLOCK_SIZE) {
                double[][] block = new double[BLOCK_SIZE][BLOCK_SIZE];
                for (int i = 0; i < BLOCK_SIZE; i++) {
                    for (int j = 0; j < BLOCK_SIZE; j++) {
                        block[i][j] = channel[y + i][x + j] - 128;
                    }
                }
                dct.forward(block, true);
                double[] zigZag = new double[BLOCK_SIZE * BLOCK_SIZE];
                for (int i = 0; i < ZIGZAG_ORDER.length; i++) {
                    int row = ZIGZAG_ORDER[i] / BLOCK_SIZE;
                    int col = ZIGZAG_ORDER[i] % BLOCK_SIZE;
                    double q = quantMatrix[row][col] * BASE_QUALITY;
                    zigZag[i] = Math.round(block[row][col] / q);
                }
                packIndices(zigZag, QUALITY_LEVELS, dos);
            }
        }
    }

    private static void packIndices(double[] zigZag,
                                    float[] qualityLevels,
                                    DataOutputStream dos) throws IOException {
        int lastNonZero = -1;
        for (int i = zigZag.length - 1; i >= 0; i--) {
            if (zigZag[i] != 0) {
                lastNonZero = i;
                break;
            }
        }
        if (lastNonZero == -1) {
            dos.writeByte(0);
            return;
        }
        dos.writeByte(lastNonZero + 1);
        for (int i = 0; i <= lastNonZero; i++) {
            int value = (int) zigZag[i];
            if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
                dos.writeByte(Byte.MAX_VALUE + 1);
                dos.writeInt(value);
            } else {
                dos.writeByte(value);
            }
        }
    }
}
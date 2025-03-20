import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.jtransforms.dct.FloatDCT_2D;

public class ComplexCompressor {

    // Standard JPEG quantization matrices
    private static final int[][] LUMINANCE_QUANT_TABLE = {
            {16, 11, 10, 16, 24, 40, 51, 61},
            {12, 12, 14, 19, 26, 58, 60, 55},
            {14, 13, 16, 24, 40, 57, 69, 56},
            {14, 17, 22, 29, 51, 87, 80, 62},
            {18, 22, 37, 56, 68, 109, 103, 77},
            {24, 35, 55, 64, 81, 104, 113, 92},
            {49, 64, 78, 87, 103, 121, 120, 101},
            {72, 92, 95, 98, 112, 100, 103, 99}
    };

    private static final int[][] CHROMINANCE_QUANT_TABLE = {
            {17, 18, 24, 47, 99, 99, 99, 99},
            {18, 21, 26, 66, 99, 99, 99, 99},
            {24, 26, 56, 99, 99, 99, 99, 99},
            {47, 66, 99, 99, 99, 99, 99, 99},
            {99, 99, 99, 99, 99, 99, 99, 99},
            {99, 99, 99, 99, 99, 99, 99, 99},
            {99, 99, 99, 99, 99, 99, 99, 99},
            {99, 99, 99, 99, 99, 99, 99, 99}
    };

    // Zigzag order for 8x8 block
    private static final int[] ZIGZAG_ORDER = {
            0, 1, 8, 16, 9, 2, 3, 10, 17, 24, 32, 25, 18, 11, 4, 5,
            12, 19, 26, 33, 40, 48, 41, 34, 27, 20, 13, 6, 7, 14, 21, 28,
            35, 42, 49, 56, 57, 50, 43, 36, 29, 22, 15, 23, 30, 37, 44, 51,
            58, 59, 52, 45, 38, 31, 39, 46, 53, 60, 61, 54, 47, 55, 62, 63
    };

    // Base quality factor
    private static final float BASE_QUALITY = 50.0f;
    private static final float VARIANCE_THRESHOLD = 100.0f;

    // Compresses an image to a .byt file
    public static void compress(String inputPath, String outputPath) throws IOException {
        // Load image
        BufferedImage image = ImageIO.read(new File(inputPath));
        if (image == null) {
            System.err.println("Failed to load image: " + inputPath);
            return;
        }
        int w = image.getWidth();
        int h = image.getHeight();

        // Pad dimensions to next multiple of 16
        int wPadded = ((w + 15) / 16) * 16;
        int hPadded = ((h + 15) / 16) * 16;

        // Convert to YCbCr and pad
        float[][] yChannel = new float[hPadded][wPadded];
        float[][] cbChannel = new float[hPadded][wPadded];
        float[][] crChannel = new float[hPadded][wPadded];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                yChannel[y][x] = 0.299f * r + 0.587f * g + 0.114f * b;
                cbChannel[y][x] = -0.1687f * r - 0.3313f * g + 0.5f * b + 128;
                crChannel[y][x] = 0.5f * r - 0.4187f * g - 0.0813f * b + 128;
            }
        }
        // Pad by replicating border pixels
        padChannel(yChannel, w, h, wPadded, hPadded);
        padChannel(cbChannel, w, h, wPadded, hPadded);
        padChannel(crChannel, w, h, wPadded, hPadded);

        // Subsample chrominance (4:2:0)
        float[][] cbSub = subsample(cbChannel, wPadded, hPadded);
        float[][] crSub = subsample(crChannel, wPadded, hPadded);

        // Process channels and write to byte array
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(w);
        dos.writeInt(h);
        dos.writeInt(wPadded);
        dos.writeInt(hPadded);

        processChannel(yChannel, wPadded, hPadded, LUMINANCE_QUANT_TABLE, dos);
        processChannel(cbSub, wPadded / 2, hPadded / 2, CHROMINANCE_QUANT_TABLE, dos);
        processChannel(crSub, wPadded / 2, hPadded / 2, CHROMINANCE_QUANT_TABLE, dos);

        // Compress with GZIP
        try (FileOutputStream fos = new FileOutputStream(outputPath);
             GZIPOutputStream gzos = new GZIPOutputStream(fos)) {
            gzos.write(baos.toByteArray());
        }
    }

    /** Decompresses a .byt file back to a JPEG */
    public static void decompress(String inputPath, String outputPath) throws IOException {
        // Decompress GZIP to byte array
        byte[] data;
        try (FileInputStream fis = new FileInputStream(inputPath);
             GZIPInputStream gzis = new GZIPInputStream(fis);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzis.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
            data = baos.toByteArray();
        }

        // Read headers
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream dis = new DataInputStream(bais);
        int w = dis.readInt();
        int h = dis.readInt();
        int wPadded = dis.readInt();
        int hPadded = dis.readInt();

        // Reconstruct channels
        float[][] yChannel = reconstructChannel(wPadded, hPadded, LUMINANCE_QUANT_TABLE, dis);
        float[][] cbSub = reconstructChannel(wPadded / 2, hPadded / 2, CHROMINANCE_QUANT_TABLE, dis);
        float[][] crSub = reconstructChannel(wPadded / 2, hPadded / 2, CHROMINANCE_QUANT_TABLE, dis);

        // Upsample chrominance
        float[][] cbChannel = upsample(cbSub, wPadded, hPadded);
        float[][] crChannel = upsample(crSub, wPadded, hPadded);

        // Convert back to RGB and crop to original dimensions
        BufferedImage outputImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float yVal = yChannel[y][x];
                float cbVal = cbChannel[y][x] - 128;
                float crVal = crChannel[y][x] - 128;
                int r = (int) Math.min(255, Math.max(0, yVal + 1.402f * crVal));
                int g = (int) Math.min(255, Math.max(0, yVal - 0.34414f * cbVal - 0.71414f * crVal));
                int b = (int) Math.min(255, Math.max(0, yVal + 1.772f * cbVal));
                outputImage.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }

        // Save as JPEG
        ImageIO.write(outputImage, "jpeg", new File(outputPath));
    }

    /** Pads a channel by replicating border pixels */
    private static void padChannel(float[][] channel, int w, int h, int wPadded, int hPadded) {
        // Pad right edge
        for (int y = 0; y < h; y++) {
            for (int x = w; x < wPadded; x++) {
                channel[y][x] = channel[y][w - 1];
            }
        }
        // Pad bottom edge and padded corners
        for (int y = h; y < hPadded; y++) {
            for (int x = 0; x < wPadded; x++) {
                channel[y][x] = channel[h - 1][x];
            }
        }
    }

    /** Subsamples a channel using 4:2:0 (averaging 2x2 blocks) */
    private static float[][] subsample(float[][] channel, int wPadded, int hPadded) {
        int wSub = wPadded / 2;
        int hSub = hPadded / 2;
        float[][] sub = new float[hSub][wSub];
        for (int y = 0; y < hSub; y++) {
            for (int x = 0; x < wSub; x++) {
                float avg = (channel[2 * y][2 * x] + channel[2 * y][2 * x + 1] +
                        channel[2 * y + 1][2 * x] + channel[2 * y + 1][2 * x + 1]) / 4.0f;
                sub[y][x] = avg;
            }
        }
        return sub;
    }

    /** Processes a channel into 8x8 blocks with adaptive quantization */
    private static void processChannel(float[][] channel, int width, int height, int[][] quantTable,
                                       DataOutputStream dos) throws IOException {
        FloatDCT_2D dct = new FloatDCT_2D(8, 8);
        float[] block = new float[64];
        for (int y = 0; y < height; y += 8) {
            for (int x = 0; x < width; x += 8) {
                // Extract 8x8 block and center
                float mean = 0;
                for (int i = 0; i < 8; i++) {
                    for (int j = 0; j < 8; j++) {
                        float val = channel[y + i][x + j];
                        block[i * 8 + j] = val - 128;
                        mean += val;
                    }
                }
                mean /= 64;

                // Compute variance
                float variance = 0;
                for (int i = 0; i < 64; i++) {
                    float diff = block[i] + 128 - mean;
                    variance += diff * diff;
                }
                variance /= 64;

                // Adaptive quality factor
                float qEffective = Math.min(100, Math.max(1, BASE_QUALITY * (variance / VARIANCE_THRESHOLD)));
                float multiplier = 50.0f / qEffective;
                dos.writeByte((int) (qEffective - 1)); // Store Q as byte (0-99 for 1-100)

                // Apply DCT
                dct.forward(block, false);

                // Quantize and clamp
                byte[] quantized = new byte[64];
                for (int i = 0; i < 8; i++) {
                    for (int j = 0; j < 8; j++) {
                        int idx = i * 8 + j;
                        float coeff = block[idx];
                        float qVal = quantTable[i][j] * multiplier;
                        int q = Math.round(coeff / qVal);
                        block[idx] = Math.min(127, Math.max(-128, q));
                    }
                }

                // Zigzag order
                for (int i = 0; i < 64; i++) {
                    quantized[i] = (byte) block[ZIGZAG_ORDER[i]];
                }
                dos.write(quantized);
            }
        }
    }

    /** Reconstructs a channel from the compressed data */
    private static float[][] reconstructChannel(int width, int height, int[][] quantTable,
                                                DataInputStream dis) throws IOException {
        FloatDCT_2D dct = new FloatDCT_2D(8, 8);
        float[][] channel = new float[height][width];
        float[] block = new float[64];
        for (int y = 0; y < height; y += 8) {
            for (int x = 0; x < width; x += 8) {
                // Read Q_effective and coefficients
                int qByte = dis.readUnsignedByte();
                float qEffective = qByte + 1;
                float multiplier = 50.0f / qEffective;
                byte[] serialized = new byte[64];
                dis.readFully(serialized);

                // Reverse zigzag
                for (int i = 0; i < 64; i++) {
                    block[ZIGZAG_ORDER[i]] = serialized[i];
                }

                // Dequantize
                for (int i = 0; i < 8; i++) {
                    for (int j = 0; j < 8; j++) {
                        int idx = i * 8 + j;
                        block[idx] *= quantTable[i][j] * multiplier;
                    }
                }

                // Inverse DCT
                dct.inverse(block, false);

                // Add 128 and place in channel
                for (int i = 0; i < 8; i++) {
                    for (int j = 0; j < 8; j++) {
                        channel[y + i][x + j] = block[i * 8 + j] + 128;
                    }
                }
            }
        }
        return channel;
    }

    /** Upsamples a subsampled channel by replication */
    private static float[][] upsample(float[][] sub, int wPadded, int hPadded) {
        float[][] full = new float[hPadded][wPadded];
        for (int y = 0; y < hPadded / 2; y++) {
            for (int x = 0; x < wPadded / 2; x++) {
                float val = sub[y][x];
                full[2 * y][2 * x] = val;
                full[2 * y][2 * x + 1] = val;
                full[2 * y + 1][2 * x] = val;
                full[2 * y + 1][2 * x + 1] = val;
            }
        }
        return full;
    }

    public static void main(String[] args) {
        try {
            // Example usage
            compress("E:\\Uni work\\Computing Project\\The-Computing-Project\\GameByte\\src\\main\\resources\\assets\\textures\\948688.jpg", "E:\\Uni work\\Computing Project\\The-Computing-Project\\GameByte\\src\\main\\resources\\assets\\textures\\2Compressed.byt");
            System.out.println("Image Compressed!");
            decompress("E:\\Uni work\\Computing Project\\The-Computing-Project\\GameByte\\src\\main\\resources\\assets\\textures\\2Compressed.byt", "E:\\Uni work\\Computing Project\\The-Computing-Project\\GameByte\\src\\main\\resources\\assets\\textures\\2decomp.jpg");
            System.out.println("Image Decompressed!");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.jtransforms.dct.FloatDCT_2D;

public class AggressiveImageCompressor {

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

    /** Compresses an image with a fixed quality factor */
    public static void compress(String inputPath, String outputPath, int quality) throws IOException {
        BufferedImage image = ImageIO.read(new File(inputPath));
        if (image == null) {
            System.err.println("Failed to load image: " + inputPath);
            return;
        }
        int w = image.getWidth();
        int h = image.getHeight();
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
        padChannel(yChannel, w, h, wPadded, hPadded);
        padChannel(cbChannel, w, h, wPadded, hPadded);
        padChannel(crChannel, w, h, wPadded, hPadded);

        // Subsample chrominance (4:2:0)
        float[][] cbSub = subsample(cbChannel, wPadded, hPadded);
        float[][] crSub = subsample(crChannel, wPadded, hPadded);

        // Compute multiplier and scaled quantization tables
        float multiplier = 50.0f / quality;
        float[][] scaledLuminance = computeScaledQuantTable(LUMINANCE_QUANT_TABLE, multiplier);
        float[][] scaledChrominance = computeScaledQuantTable(CHROMINANCE_QUANT_TABLE, multiplier);

        // Process channels into byte stream
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(w);
        dos.writeInt(h);
        dos.writeInt(wPadded);
        dos.writeInt(hPadded);
        dos.writeByte(quality - 1); // Store quality - 1 (0-99)

        processChannel(yChannel, wPadded, hPadded, scaledLuminance, dos);
        processChannel(cbSub, wPadded / 2, hPadded / 2, scaledChrominance, dos);
        processChannel(crSub, wPadded / 2, hPadded / 2, scaledChrominance, dos);

        // Compress with GZIP
        try (FileOutputStream fos = new FileOutputStream(outputPath);
             GZIPOutputStream gzos = new GZIPOutputStream(fos)) {
            gzos.write(baos.toByteArray());
        }
    }

    /** Decompresses back to a JPEG */
    public static void decompress(String inputPath, String outputPath) throws IOException {
        // Decompress GZIP
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
        int qByte = dis.readUnsignedByte();
        int quality = qByte + 1;
        float multiplier = 50.0f / quality;

        // Compute scaled quantization tables
        float[][] scaledLuminance = computeScaledQuantTable(LUMINANCE_QUANT_TABLE, multiplier);
        float[][] scaledChrominance = computeScaledQuantTable(CHROMINANCE_QUANT_TABLE, multiplier);

        // Reconstruct channels
        float[][] yChannel = reconstructChannel(wPadded, hPadded, scaledLuminance, dis);
        float[][] cbSub = reconstructChannel(wPadded / 2, hPadded / 2, scaledChrominance, dis);
        float[][] crSub = reconstructChannel(wPadded / 2, hPadded / 2, scaledChrominance, dis);

        // Upsample chrominance
        float[][] cbChannel = upsample(cbSub, wPadded, hPadded);
        float[][] crChannel = upsample(crSub, wPadded, hPadded);

        // Convert to RGB and crop
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

        ImageIO.write(outputImage, "jpeg", new File(outputPath));
    }

    /** Pads a channel by replicating borders */
    private static void padChannel(float[][] channel, int w, int h, int wPadded, int hPadded) {
        for (int y = 0; y < h; y++) {
            for (int x = w; x < wPadded; x++) {
                channel[y][x] = channel[y][w - 1];
            }
        }
        for (int y = h; y < hPadded; y++) {
            for (int x = 0; x < wPadded; x++) {
                channel[y][x] = channel[h - 1][x];
            }
        }
    }

    /** Subsamples using 4:2:0 */
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

    /** Computes scaled quantization table */
    private static float[][] computeScaledQuantTable(int[][] quantTable, float multiplier) {
        float[][] scaled = new float[8][8];
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                scaled[i][j] = quantTable[i][j] * multiplier;
            }
        }
        return scaled;
    }

    /** Processes a channel with RLE-like encoding */
    private static void processChannel(float[][] channel, int width, int height, float[][] scaledQuantTable, DataOutputStream dos) throws IOException {
        FloatDCT_2D dct = new FloatDCT_2D(8, 8);
        float[] block = new float[64];
        for (int y = 0; y < height; y += 8) {
            for (int x = 0; x < width; x += 8) {
                // Extract and center block
                for (int i = 0; i < 8; i++) {
                    for (int j = 0; j < 8; j++) {
                        block[i * 8 + j] = channel[y + i][x + j] - 128;
                    }
                }
                dct.forward(block, false);
                // Quantize
                List<Integer> positions = new ArrayList<>();
                List<Byte> values = new ArrayList<>();
                for (int zig = 0; zig < 64; zig++) {
                    int idx = ZIGZAG_ORDER[zig];
                    int row = idx / 8;
                    int col = idx % 8;
                    float coeff = block[idx];
                    float qVal = scaledQuantTable[row][col];
                    int q = Math.round(coeff / qVal);
                    q = Math.min(127, Math.max(-128, q));
                    if (q != 0) {
                        positions.add(zig);
                        values.add((byte) q);
                    }
                }
                int k = positions.size();
                dos.writeByte(k);
                for (int i = 0; i < k; i++) {
                    dos.writeByte(positions.get(i));
                    dos.writeByte(values.get(i));
                }
            }
        }
    }

    /** Reconstructs a channel */
    private static float[][] reconstructChannel(int width, int height, float[][] scaledQuantTable, DataInputStream dis) throws IOException {
        FloatDCT_2D dct = new FloatDCT_2D(8, 8);
        float[][] channel = new float[height][width];
        float[] block = new float[64];
        for (int y = 0; y < height; y += 8) {
            for (int x = 0; x < width; x += 8) {
                int k = dis.readUnsignedByte();
                byte[] zigzag = new byte[64];
                for (int i = 0; i < k; i++) {
                    int pos = dis.readUnsignedByte();
                    byte val = dis.readByte();
                    zigzag[pos] = val;
                }
                for (int zig = 0; zig < 64; zig++) {
                    block[ZIGZAG_ORDER[zig]] = zigzag[zig];
                }
                // Dequantize
                for (int i = 0; i < 64; i++) {
                    int row = i / 8;
                    int col = i % 8;
                    block[i] *= scaledQuantTable[row][col];
                }
                dct.inverse(block, false);
                for (int i = 0; i < 8; i++) {
                    for (int j = 0; j < 8; j++) {
                        channel[y + i][x + j] = block[i * 8 + j] + 128;
                    }
                }
            }
        }
        return channel;
    }

    /** Upsamples by replication */
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
            compress("E:\\Uni work\\Computing Project\\The-Computing-Project\\GameByte\\src\\main\\resources\\assets\\textures\\948688.jpg", "E:\\Uni work\\Computing Project\\The-Computing-Project\\GameByte\\src\\main\\resources\\assets\\textures\\aggressiveCompressed.byt", 100); // Aggressive compression with quality=10
            System.out.println("Compressed!");
            decompress("E:\\Uni work\\Computing Project\\The-Computing-Project\\GameByte\\src\\main\\resources\\assets\\textures\\aggressiveCompressed.byt", "E:\\Uni work\\Computing Project\\The-Computing-Project\\GameByte\\src\\main\\resources\\assets\\textures\\aggressiveDeCompressed.jpg");
            System.out.println("Decompressed!");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
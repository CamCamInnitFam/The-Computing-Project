import java.awt.image.BufferedImage;
import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.imageio.ImageIO;

public class ImageCompressor {

    // Hardcoded dimensions for demonstration (normally provided externally)
    private static final int WIDTH = 2000;
    private static final int HEIGHT = 2000;
    //private static final String INPUT_FILE = "E:\\Uni work\\Computing Project\\The-Computing-Project\\GameByte\\src\\main\\resources\\assets\\textures\\Pickaxe200.jpg";
    //private static final String COMPRESSED_FILE = "E:\\Uni work\\Computing Project\\The-Computing-Project\\GameByte\\src\\main\\resources\\assets\\textures\\2003compressed.byt";
    //private static final String DECOMPRESSED_FILE = "E:\\Uni work\\Computing Project\\The-Computing-Project\\GameByte\\src\\main\\resources\\assets\\textures\\2003deCompressed.jpg";

    private static final String INPUT_FILE = "E:\\Uni work\\Computing Project\\The-Computing-Project\\GameByte\\src\\main\\resources\\assets\\textures\\948688.jpg";
    private static final String COMPRESSED_FILE = "E:\\Uni work\\Computing Project\\The-Computing-Project\\GameByte\\src\\main\\resources\\assets\\textures\\948688Compressed.byt";
    private static final String DECOMPRESSED_FILE = "E:\\Uni work\\Computing Project\\The-Computing-Project\\GameByte\\src\\main\\resources\\assets\\textures\\948688Decompressed.jpg";



    public static void main(String[] args) throws IOException {
        compress();
        decompress();
    }

    public static void compress() throws IOException {
        // Load image using ImageIO
        BufferedImage img = ImageIO.read(new File(INPUT_FILE));
        int width = img.getWidth();
        int height = img.getHeight();

        // Step 1: Quantize pixels into rawData
        byte[] rawData = new byte[width * height];
        int index = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF; // Red (0-255)
                int g = (rgb >> 8) & 0xFF;  // Green (0-255)
                int b = rgb & 0xFF;         // Blue (0-255)
                int rQuant = (r / 36) & 0x07; // 3 bits (0-7)
                int gQuant = (g / 36) & 0x07; // 3 bits (0-7)
                int bQuant = (b / 85) & 0x03; // 2 bits (0-3)
                int packedColor = (rQuant << 5) | (gQuant << 2) | bQuant;
                rawData[index++] = (byte) packedColor;
            }
        }

        // Step 2: Delta encoding
        byte[] filteredData = new byte[rawData.length];
        filteredData[0] = rawData[0]; // First pixel unchanged
        for (int i = 1; i < rawData.length; i++) {
            int curr = rawData[i] & 0xFF;
            int prev = rawData[i - 1] & 0xFF;
            int diff = (curr - prev) & 0xFF; // Modulo 256
            filteredData[i] = (byte) diff;
        }



        // Step 3: Compress with GZIP
        try (FileOutputStream fos = new FileOutputStream(COMPRESSED_FILE);
             GZIPOutputStream gzipOut = new GZIPOutputStream(fos)) {
            gzipOut.write(filteredData);
        }

        System.out.println("Image compressed and saved as " + COMPRESSED_FILE);

        //Output size
        File compressedFile = new File(COMPRESSED_FILE);
        long fileSizeBytes = compressedFile.length();
        double fileSizeKB = fileSizeBytes / 1024.0;
        System.out.println("Compressed File Size: " + fileSizeBytes + " bytes (" + String.format("%.2f", fileSizeKB) + " KB)");
    }

    public static void decompress() throws IOException {
        // Step 1: Decompress with GZIP
        byte[] filteredData;
        try (FileInputStream fis = new FileInputStream(COMPRESSED_FILE);
             GZIPInputStream gzipIn = new GZIPInputStream(fis)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzipIn.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
            filteredData = baos.toByteArray();
        }

        // Verify length matches expected dimensions
        if (filteredData.length != WIDTH * HEIGHT) {
            throw new IllegalStateException("Decompressed data size does not match expected dimensions");
        }

        // Step 2: Reverse delta encoding
        byte[] rawData = new byte[filteredData.length];
        rawData[0] = filteredData[0]; // First pixel unchanged
        for (int i = 1; i < rawData.length; i++) {
            int prev = rawData[i - 1] & 0xFF;
            int diff = filteredData[i] & 0xFF;
            int curr = (prev + diff) & 0xFF;
            rawData[i] = (byte) curr;
        }

        // Step 3: Unpack and reconstruct image
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        int index = 0;
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int packedColor = rawData[index++] & 0xFF;
                int r = (packedColor >> 5) & 0x07; // 0-7
                int g = (packedColor >> 2) & 0x07; // 0-7
                int b = packedColor & 0x03;        // 0-3
                int rFull = (r * 255) / 7;         // Scale to 0-255
                int gFull = (g * 255) / 7;
                int bFull = (b * 255) / 3;
                int rgb = (rFull << 16) | (gFull << 8) | bFull;
                image.setRGB(x, y, rgb);
            }
        }

        // Step 4: Save as JPG
        ImageIO.write(image, "jpg", new File(DECOMPRESSED_FILE));
        System.out.println("Image decompressed and saved as " + DECOMPRESSED_FILE);
    }
}
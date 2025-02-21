import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.dsl.FXGL;
import com.almasb.fxgl.texture.Texture;

import javafx.scene.image.PixelReader;
import javafx.scene.paint.Color;
import org.jtransforms.dct.DoubleDCT_2D;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
//import java.lang.runtime.TemplateRuntime;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.sql.SQLOutput;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.zip.CheckedOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GameByte extends GameApplication
{
    //User selected compression ratio
    enum CompressionLevel{
        LOW,
        MEDIUM,
        HIGH
    }

    private static final int BLOCK_SIZE = 8;
    private static final int REDUCE_BITS = 4; //Reduce 8 bits to 4 bits per channel

    @Override
    protected void initSettings(GameSettings gameSettings)
    {
        gameSettings.setTitle("GameByte Compression Solution!");
    }

    @Override
    protected void initGame(){
        /*var t = FXGL.texture("Pickaxe.jpg");
        var reader = t.getImage().getPixelReader();
        System.out.println(reader);
        for(int y = 0; y < t.getHeight(); y++){
            for(int x = 0; x < t.getWidth(); x++){
                var c = reader.getColor(x,y);
                System.out.println(c.getRed() + " " + c.getGreen() + " " + c.getBlue());;
                //System.out.println(reader.getArgb(x,y));
            }
        }*/
        try {
            test();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String image = "Pickaxe200.jpg";
        String outputFile = "M:\\GameByte Novel Compression Solution\\GameByte\\src\\main\\resources\\assets\\textures\\test.byt";
        try {
            test2(image, outputFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws IOException {
        //System.out.println("HELLO FXGL!");
        //GameByte gamebyt = new GameByte();
        launch(args);

        //test();
        String inputImagePath = "M:\\GameByte Novel Compression Solution\\GameByte\\src\\main\\resources\\assets\\textures\\Pickaxe200.jpg";
        String outputBytFile = "M:\\GameByte Novel Compression Solution\\GameByte\\src\\main\\resources\\assets\\textures\\PickaxeCompressed.byt";

        BufferedImage image = ImageIO.read(new File(inputImagePath));
        //System.out.println(image);

        //Make sure we are working with 200x200 only!
        if(image.getWidth() != 200 || image.getHeight() != 200){
            throw new IllegalArgumentException("Image must be 200x200 Pixels!");
        }

        //Remove Alpha channel (if present) and convert from RGBA to RGB //TODO fix this as it doesn't actually remove alpha channel...
        BufferedImage rgbImage = removeAlphaChannel(image);


        //System.out.println(rgbImage.getGraphics());
        //java.awt.color.ColorSpace.getInstance().fromRGB()

        //Reduce color profile (bit depth reduction)
        BufferedImage reducedImage = reduceColourBitDepth(rgbImage);

        //Convert RGB to YCbCR and apply optimised chroma subsampling
        double[][][] ycbcrImage = rgbToYCbCrWithOptimisedSubsampling(reducedImage);

        //Apply DCT, adaptive quantisation
        double[][]compressedDataY = compressWithDCT(ycbcrImage[0], true); //Y (luminance)
        double[][]compressedDataCb = compressWithDCT(ycbcrImage[1], false); //CB, subsampled
        double[][]compressedDataCr = compressWithDCT(ycbcrImage[2], false); //CR, subsampled

       /* int temp = 0;
        for(int i = 0; i < compressedDataCr.length; i++){
            for (int j = 0; j < compressedDataCr[i].length; j++){
                System.out.println(compressedDataCr[i][j] + " ");
                temp++;
            }
            System.out.println("Temp: " + temp);
        }*/

        //TODO look at this
        //the above temp gets to 10,000
        //meaning the double[][] has 10,000 elements (i think) meaning it is 100 x 100 array
        //compress with DCT returns the coeficients as the double[][] which is usign a height and width which must be
        //appearing as 100 each.
        //check to see if compressedDataY should be an 8x8 block for instance

        //Apply huffman coding
        HuffmanCompressor huffmanCompressor = new HuffmanCompressor();
        byte[] huffmanCompressedY = huffmanCompressor.encode(compressedDataY);
        byte[] huffmanCompressedCb = huffmanCompressor.encode(compressedDataCb);
        byte[] huffmanCompressedCr = huffmanCompressor.encode(compressedDataCr);

        //Save compressed data to a .byt file
        saveToBytFile(outputBytFile, huffmanCompressedY, huffmanCompressedCb, huffmanCompressedCr);

        System.out.println("Image Compressed and saved to: " + outputBytFile );
    }

    private static void saveToBytFile(String outputPath, byte[] yData, byte[] cbData, byte[] crData) throws IOException{
        try{
            FileOutputStream fos = new FileOutputStream(outputPath);
            ObjectOutputStream oos = new ObjectOutputStream(fos);

            oos.writeObject(yData);
            oos.writeObject(cbData);
            oos.writeObject(crData);
        }catch(Exception ex){
            System.out.println("Something is wrong here! " + ex);
        }

    }

    private static double[][] compressWithDCT(double[][] channel, boolean isLuminance)
    {
        int width = channel[0].length;
        int height = channel.length;

        double[][] dctCoefficients = new double[height][width];
        DoubleDCT_2D dct = new DoubleDCT_2D(BLOCK_SIZE, BLOCK_SIZE);

        int[][] quantisationMatrix = isLuminance ? getAdaptiveQuantisationMatrix(channel) : getChromaQuantisationMatrix();

        for(int i = 0; i < height; i += BLOCK_SIZE){
            for(int j = 0; j < width; j+= BLOCK_SIZE){
                double[][] block = new double[BLOCK_SIZE][BLOCK_SIZE];

                //Extract 8x8 block (handle boundaries by padding if necessary)
                for(int x = 0; x < BLOCK_SIZE; x++){
                    for(int y = 0; y < BLOCK_SIZE; y++){
                        int pixelX = i+x;
                        int pixelY = j+y;

                        if(pixelX < height && pixelY < width){
                            block[x][y] = channel[pixelX][pixelY] - 128; //Shift for DCT
                        }else{
                            block[x][y] = 0; //Pad with 0 if outside the image bounds
                        }
                    }
                }
                //Perform DCT on the block
                dct.forward(block, true);

                //Adaptive quantisation
                for(int  x = 0; x < BLOCK_SIZE; x++){
                    for(int y = 0; y < BLOCK_SIZE; y++){
                        block[x][y] = Math.round(block[x][y] / quantisationMatrix[x][y]);
                    }
                }

                //Store quantized DCT coefficients
                for(int x = 0; x < BLOCK_SIZE; x++){
                    for(int y = 0; y < BLOCK_SIZE; y++){
                        int pixelX = i +x;
                        int pixelY = j+y;

                        if(pixelX < height && pixelY < width){
                            dctCoefficients[i+x][j+y] = block[x][y];
                        }
                    }
                }
            }
        }
        return dctCoefficients;
    }

    private static int[][] getChromaQuantisationMatrix(){
        //Standard JPEG chroma quantisation matrix for yCbCr channels
        return new int[][]{
                {17, 18, 24, 47, 99, 99, 99, 99},
                {18, 21, 26, 66, 99, 99, 99, 99},
                {24, 26, 56, 99, 99, 99, 99, 99},
                {47, 66, 99, 99, 99, 99, 99, 99},
                {99, 99, 99, 99, 99, 99, 99, 99},
                {99, 99, 99, 99, 99, 99, 99, 99},
                {99, 99, 99, 99, 99, 99, 99, 99},
                {99, 99, 99, 99, 99, 99, 99, 99}
        };
    }

    private static int[][] getAdaptiveQuantisationMatrix(double[][] channel){
        //BASE JPEG quantisation matrix for Y channel
        int[][] baseMatrix = new int[][]{
                {16, 11, 10, 16, 24, 40, 51, 61},
                {12, 12, 14, 19, 26, 58, 60, 55},
                {14, 13, 16, 24, 40, 57, 69, 56},
                {14, 17, 22, 29, 51, 87, 80, 62},
                {18, 22, 37, 56, 68, 109, 103, 77},
                {24, 35, 55, 64, 81, 104, 113, 92},
                {49, 64, 78, 87, 103, 121, 120, 101},
                {72, 92, 95, 98, 112, 100, 103, 99}
        };

        //Calculate the variance of the image channel
        double variance = calculateVariance(channel);

        //Adjust the base matrix based on the variance (lower variance = more aggressive quantisation)
        int[][] adaptiveMatrix = new int[BLOCK_SIZE][BLOCK_SIZE];
        double scalingFactor = getScalingFactor(variance); //Scale based on variance

        for(int i = 0; i < BLOCK_SIZE; i++){
            for(int j = 0; j < BLOCK_SIZE; j++){
                adaptiveMatrix[i][j] = (int)(baseMatrix[i][j] * scalingFactor);
                if(adaptiveMatrix[i][j] < 1)
                    adaptiveMatrix[i][j] = 1; //ensure minimum quantisation step
            }
        }
        return adaptiveMatrix;
    }

    private static double getScalingFactor(double variance){
        if(variance > 1000)
            return 0.5; //Less aggressive for high variance (detailed) areas
        else if(variance > 500)
            return 0.75; //moderate quantisation for medium variance
        else
            return 1.0; //more aggressive for low variance (smooth) areas
    }

    private static double calculateVariance(double[][] channel){
        double sum = 0.0;
        double mean = 0.0;
        int count = 0;

        for(double[] row : channel){
            for(double value : row){
                sum+= value;
                count++;
            }
        }
        mean = sum / count;

        double variance = 0.0;
        for(double[] row :  channel){
            for(double value : row){
                variance += Math.pow(value - mean, 2);
            }
        }

        return variance/count;
    }


    private static double[][][] rgbToYCbCrWithOptimisedSubsampling(BufferedImage image)
    {
        int width = image.getWidth();
        int height = image.getHeight();

        double[][] yChannel = new double[height][width];
        double[][] cbChannel = new double[height/2][width/2]; //4:2:0 subsampling //TODO this is where the 100 x 100 is coming from ?
        double[][] crChannel = new double[height/2][width/2]; //4:2:0 subsampling

        for(int y = 0; y < height; y++){
            for(int x = 0; x < width; x++){
                int rgb = image.getRGB(x,y);

                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                //RGB to YCBCR
                double yVal = 0.299 * r + 0.587 * g + 0.114 * b;
                double cbVal = 128 - 0.168736 * r - 0.331264 * g + 0.5 * b;
                double crVal = 128 + 0.5 * r - 0.418688 * g - 0.081312 * b;

                yChannel[y][x] = yVal;

                //Apply optimised chroma subsampling (bilinear interpolation for smoother results)
                if(y % 2 == 0 && x % 2 == 0){
                    cbChannel[y/2][x/2] = cbVal;
                    crChannel[y/2][x/2] = crVal;
                }
            }
        }
        return new double[][][]{yChannel, cbChannel, crChannel};
    }
    public static BufferedImage removeAlphaChannel(BufferedImage image)
    {
        BufferedImage rgbImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics g = rgbImage.getGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return rgbImage;
    }

    public static BufferedImage reduceColourBitDepth(BufferedImage image)
    {
        BufferedImage reducedImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);

        for(int y= 0; y < image.getHeight(); y++){
            for(int x = 0; x < image.getWidth(); x++){
                int rgb = image.getRGB(x,y);

                //EXTRACT RGB COMPONENTS
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                //Reduced bit depth for each component (8 to REDUCED_BITS)
                r = reduceBitDepth(r, REDUCE_BITS);
                g = reduceBitDepth(g, REDUCE_BITS);
                b = reduceBitDepth(b, REDUCE_BITS);

                //Combine back into rgb
                int reducedRGB = (r << 16) | (g<<8) | b;
                reducedImage.setRGB(x,y,reducedRGB);
            }
        }
        return reducedImage;
    }

    private static int reduceBitDepth(int colourValue, int bits)
    {
     int shift = 8-bits;
     return(colourValue >> shift) << shift; //Shift out the least significant bits and shift back
    }

    public static void GameByteCompress(CompressionLevel compressionLevel)
    {
        //TODO

    }

    public static void GameByteDecompress(CompressionLevel compressionLevel)
    {
        //TODO
    }

    public static void test() throws IOException {
        var source = FXGL.texture("Pickaxe200.jpg");
        String outputBytFile = "M:\\GameByte Novel Compression Solution\\GameByte\\src\\main\\resources\\assets\\textures\\compressed.byt";

        // Load the image as a texture using FXGL
        Texture texture = FXGL.texture("Pickaxe200.jpg");
        PixelReader reader = texture.getImage().getPixelReader();
        int width = (int) texture.getWidth();
        int height = (int) texture.getHeight();

        // Allocate a ByteBuffer with one byte per pixel.
        ByteBuffer buffer = ByteBuffer.allocate(width * height);

        // Iterate over each pixel in the image.
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color c = reader.getColor(x, y);

                // Quantize the color channels:
                // - Red and Green: 3 bits (0-7) --> Multiply by 7.
                // - Blue: 2 bits (0-3)  --> Multiply by 3.
                int r = (int) (c.getRed() * 7) & 0x07;   // 3 bits for red.
                int g = (int) (c.getGreen() * 7) & 0x07;   // 3 bits for green.
                int b = (int) (c.getBlue() * 3) & 0x03;    // 2 bits for blue.

                // Pack the bits into a single byte (8 bits):
                // Bit layout: RRR GGG BB
                int packedColor = (r << 5) | (g << 2) | b;
                buffer.put((byte) packedColor);
            }
        }

        byte[] rawData = buffer.array();


        //RLE
        // byte[] rleData = runLengthEncode(rawData);

        //Pack bits encode
        byte[] packBitsData = packBitsEncode(rawData);

        try(GZIPOutputStream gzipOut = new GZIPOutputStream(new FileOutputStream(outputBytFile))){
            gzipOut.write(packBitsData);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        System.out.println("Image compressed and saved as " + outputBytFile);
    }

    private static byte[] runLengthEncode(byte[] data) throws IOException{
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int i = 0;
        while(i < data.length){
            byte current = data[i];
            int count = 1;
            while(i + count < data.length && data[i + count] == current && count <255){
                count++;
            }
            out.write(count);
            out.write(current);
            i+=count;
        }
        return out.toByteArray();
    }
    private static byte[] packBitsEncode(byte[] data) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int i = 0;
        while (i < data.length) {
            // Check for a run of repeated bytes.
            int runLength = 1;
            while (i + runLength < data.length && data[i + runLength] == data[i] && runLength < 128) {
                runLength++;
            }
            if (runLength >= 3) {
                // Write a run-length packet.
                out.write(-(runLength - 1)); // Control byte (negative).
                out.write(data[i]);           // The byte value.
                i += runLength;
            } else {
                // Otherwise, accumulate a literal block.
                int literalStart = i;
                // Find the extent of the literal block.
                i++;
                while (i < data.length) {
                    // Look ahead for a run.
                    int lookaheadRun = 1;
                    while (i + lookaheadRun < data.length && data[i + lookaheadRun] == data[i] && lookaheadRun < 128) {
                        lookaheadRun++;
                    }
                    // Break if a run of 3 or more is detected.
                    if (lookaheadRun >= 3) {
                        break;
                    }
                    i++;
                    if (i - literalStart == 128) {
                        break;
                    }
                }
                int literalCount = i - literalStart;
                out.write(literalCount - 1); // Control byte for literal block.
                out.write(data, literalStart, literalCount);
            }
        }
        return out.toByteArray();
    }

    private static void test2(String imagePath, String outPutFileName) throws IOException {
        // Load the image using FXGL
        Texture texture = FXGL.texture(imagePath);
        if (texture == null) {
            System.err.println("Failed to load image: " + imagePath);
            return;
        }
        PixelReader reader = texture.getImage().getPixelReader();
        int width = (int) texture.getWidth();
        int height = (int) texture.getHeight();

        // Quantize the image using RGB332:
        // - Red and Green: 3 bits each (0-7)
        // - Blue: 2 bits (0-3)
        int imageDataSize = width * height;
        byte[] quantizedData = new byte[imageDataSize];
        int index = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Color c = reader.getColor(x, y);
                int r = (int) (c.getRed() * 7) & 0x07;
                int g = (int) (c.getGreen() * 7) & 0x07;
                int b = (int) (c.getBlue() * 3) & 0x03;
                int packedColor = (r << 5) | (g << 2) | b;
                quantizedData[index++] = (byte) packedColor;
            }
        }

        // Build frequency table from quantized data.
        Map<Byte, Integer> freqTable = new HashMap<>();
        for (byte b : quantizedData) {
            freqTable.put(b, freqTable.getOrDefault(b, 0) + 1);
        }

        // Build Huffman Tree.
        HuffmanNode root = buildHuffmanTree(freqTable);

        // Generate Huffman codes.
        Map<Byte, String> codes = new HashMap<>();
        buildCodes(root, "", codes);

        // Encode the quantized data into a bit string.
        StringBuilder encodedBits = new StringBuilder();
        for (byte b : quantizedData) {
            encodedBits.append(codes.get(b));
        }
        int bitLength = encodedBits.length();

        // Pack bits into a byte array.
        int byteLength = (bitLength + 7) / 8;
        byte[] encodedBytes = new byte[byteLength];
        for (int i = 0; i < bitLength; i++) {
            if (encodedBits.charAt(i) == '1') {
                encodedBytes[i / 8] |= (1 << (7 - (i % 8)));
            }
        }

        // Build the final output file.
        // We'll write:
        // [width (4 bytes), height (4 bytes),
        //  number of symbols (1 byte),
        //  for each symbol: [symbol (1 byte), frequency (4 bytes)],
        //  bit length of encoded data (4 bytes),
        //  encoded data bytes... ]
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();

        // Write width and height.
        //ByteBuffer headerBuffer = ByteBuffer.allocate(8);
        //headerBuffer.putInt(width);
        //headerBuffer.putInt(height);
        //outStream.write(headerBuffer.array());

        // Write frequency table.
       /* outStream.write((byte) freqTable.size());
        for (Map.Entry<Byte, Integer> entry : freqTable.entrySet()) {
            outStream.write(entry.getKey());
            ByteBuffer freqBuffer = ByteBuffer.allocate(4);
            freqBuffer.putInt(entry.getValue());
            outStream.write(freqBuffer.array());
        }

        // Write bit length of the encoded data.
        ByteBuffer bitLengthBuffer = ByteBuffer.allocate(4);
        bitLengthBuffer.putInt(bitLength);
        outStream.write(bitLengthBuffer.array());*/

        // Write the encoded data.
        outStream.write(encodedBytes);

        // Save to file.
        try (FileOutputStream fos = new FileOutputStream(outPutFileName)) {
            fos.write(outStream.toByteArray());
        }

        System.out.println("Image compressed (with quantization and custom Huffman encoding) and saved as " + outPutFileName);
    }

    // Huffman Node class for building the Huffman tree.
    private static class HuffmanNode implements Comparable<HuffmanNode> {
        Byte symbol;
        int frequency;
        HuffmanNode left, right;

        HuffmanNode(Byte symbol, int frequency) {
            this.symbol = symbol;
            this.frequency = frequency;
        }

        HuffmanNode(HuffmanNode left, HuffmanNode right) {
            this.symbol = null;
            this.frequency = left.frequency + right.frequency;
            this.left = left;
            this.right = right;
        }

        @Override
        public int compareTo(HuffmanNode other) {
            return Integer.compare(this.frequency, other.frequency);
        }
    }

    // Build the Huffman tree given the frequency table.
    private static HuffmanNode buildHuffmanTree(Map<Byte, Integer> freqTable) {
        PriorityQueue<HuffmanNode> queue = new PriorityQueue<>();
        for (Map.Entry<Byte, Integer> entry : freqTable.entrySet()) {
            queue.add(new HuffmanNode(entry.getKey(), entry.getValue()));
        }
        while (queue.size() > 1) {
            HuffmanNode node1 = queue.poll();
            HuffmanNode node2 = queue.poll();
            queue.add(new HuffmanNode(node1, node2));
        }
        return queue.poll();
    }

    // Recursively build the code table.
    private static void buildCodes(HuffmanNode node, String code, Map<Byte, String> codes) {
        if (node == null) return;
        if (node.symbol != null) {
            codes.put(node.symbol, code);
        } else {
            buildCodes(node.left, code + "0", codes);
            buildCodes(node.right, code + "1", codes);
        }
    }

}



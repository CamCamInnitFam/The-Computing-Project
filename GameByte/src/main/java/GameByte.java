import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.dsl.FXGL;

import org.jtransforms.dct.DoubleDCT_2D;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
//import java.lang.runtime.TemplateRuntime;
import java.nio.Buffer;
import java.sql.SQLOutput;
import java.util.PriorityQueue;
import java.util.zip.CheckedOutputStream;

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
        int loops = 0;
        var reader = t.getImage().getPixelReader();
        System.out.println(reader);
        for(int y = 0; y < t.getHeight(); y++){
            for(int x = 0; x < t.getWidth(); x++){
                var c = reader.getColor(x,y);
                System.out.println(c.getRed() + " " + c.getGreen() + " " + c.getBlue());
                //loops++;
                //System.out.println("Number of loops: " + loops);
                //System.out.println(reader.getArgb(x,y));
            }
        }*/





    }

    public static void main(String[] args) throws IOException {
        //System.out.println("HELLO FXGL!");
        //GameByte gamebyt = new GameByte();
        //launch(args);

        String inputImagePath = "M:\\GameByte Novel Compression Solution\\GameByte\\src\\main\\resources\\assets\\textures\\Pickaxe200.jpg";
        String outputImagePath = "/resources/assets/textures/PickaxeCompressed.byt";

        BufferedImage image = ImageIO.read(new File(inputImagePath));
        //System.out.println(image);

        //Make sure we are working with 200x200 only!
        if(image.getWidth() != 200 || image.getHeight() != 200){
            throw new IllegalArgumentException("Image must be 200x200 Pixels!");
        }

        //Remove Alpha channel (if present) and convert from RGBA to RGB
        BufferedImage rgbImage = removeAlphaChannel(image);

        //Reduce color profile (bit depth reduction)
        BufferedImage reducedImage = reduceColourBitDepth(rgbImage);

        //Convert RGB to YCbCR and apply optimised chroma subsampling
        double[][][] ycbcrImage = rgbToYCbCrWithOptimisedSubsampling(reducedImage);

        //Apply DCT, adaptive quantisation
        double[][]compressedDataY = compressWithDCT(ycbcrImage[0], true); //Y (luminance)
        double[][]compressedDataCb = compressWithDCT(ycbcrImage[1], false); //CB, subsampled
        double[][]compressedDataCr = compressWithDCT(ycbcrImage[2], false); //CR, subsampled

        //Apply huffman coding


        //Save compressed data to a .byt file

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
        double[][] cbChannel = new double[height/2][width/2]; //4:2:0 subsampling
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

}



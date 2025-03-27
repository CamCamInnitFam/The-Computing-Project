import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class HuffmanCompressor {
    public byte[] encode(double[][] quantisedData) throws IOException{
        //calc symbol frequencies
        Map<Integer, Integer> frequencies = calculateFrequencies(quantisedData);

        //Build huffman tree and generate codes
        HuffmanTree tree = new HuffmanTree();
        tree.build(frequencies);

        //Encode quantised data using huffman codes
        StringBuilder encodedData = new StringBuilder();
        for(double[] row : quantisedData){
            for(double value : row){
                int symbol = (int) value;
                String code = tree.getCode(symbol);
                encodedData.append(code);
            }
        }

        //Convert the encoded binary string to bytes
        return binaryStringToByteArray(encodedData.toString());
    }

    public double[][] decode(byte[] encodedData, int width, int height, HuffmanTree tree) throws IOException {
        double[][] quantisedData = new double[height][width];
        StringBuilder binaryString = byteArraytoBinaryString(encodedData);

        Node current = tree.getRoot();
        int row = 0, col = 0;

        //Traverse the binary string and decode the symbols
        for(char bit : binaryString.toString().toCharArray()){
            if(bit == '0')
                current = current.left;
            else
                current = current.right;

            if(current.isLeaf()){
                quantisedData[row][col] = current.symbol;
                current = tree.getRoot();

                col++;
                if(col == width){
                    col = 0;
                    row++;
                    if(row == height) break;
                }
            }
        }
        return quantisedData;
    }

    private Map<Integer, Integer> calculateFrequencies(double[][] quantisedData){
        Map<Integer, Integer> frequencies = new HashMap<>();
        for(double[] row : quantisedData){
            for(double value: row){
                int symbol = (int) value;
                frequencies.put(symbol, frequencies.getOrDefault(symbol, 0) + 1);
            }
        }
        return frequencies;
    }

    //convert a binary string to a byte array
    private byte[] binaryStringToByteArray(String binaryString){
        int byteLength = (binaryString.length() + 7) / 8; //Round up to the nearest byte
        byte[] byteArray = new byte[byteLength];
        for(int i = 0; i < binaryString.length(); i++){
            int byteIndex = i / 8;
            int bitIndex = i % 8;
            if(binaryString.charAt(i) == '1'){
                byteArray[byteIndex] |= 1 << (7 - bitIndex);
            }
        }
        return byteArray;
    }

    //convert a byte array to a binary string
    private StringBuilder byteArraytoBinaryString(byte[] byteArray){
        StringBuilder binaryString = new StringBuilder();
        for(byte b : byteArray){
            for(int i = 7; i >= 0; i--){
                binaryString.append((b >> i) & 1);
            }
        }
        return binaryString;
    }
}

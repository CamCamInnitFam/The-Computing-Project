import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

public class HuffmanTree {
    private Node root;
    private Map<Integer, String> codes = new HashMap<>(); //Symbol -> Huffman code

    //Build the Huffman tree from symbol frequencies
    public void build(Map<Integer, Integer> frequencies){
        PriorityQueue<Node> queue = new PriorityQueue<>();

        //Create leaf nodes for each symbol and add to priority queue
        for(Map.Entry<Integer, Integer> entry : frequencies.entrySet()){
            queue.add(new Node(entry.getKey(), entry.getValue()));
        }

        //Build tree: combine the two lowest-frequency nodes until one tree remains
        while(queue.size() > 1){
            Node left = queue.poll();
            Node right = queue.poll();
            Node parent = new Node(left, right);
            queue.add(parent);
        }

        //Root of tree is final remaining code
        root = queue.poll();

        //generate huffman codes by traversing the tree
        generateCodes(root, "");
    }

    private void generateCodes(Node node, String code){
        if(node.isLeaf())
            codes.put(node.symbol, code);
        else {
            generateCodes(node.left, code + "0");
            generateCodes(node.right, code + "1");
        }
    }
    public String getCode(int symbol){
        return codes.get(symbol);
    }
    public Map<Integer, String> getCodes(){
        return codes;
    }
    public Node getRoot(){
        return root;
    }
}

//Represents a node on the huffman tree
public class Node implements Comparable<Node>{
    int frequency;
    Node left, right;

    int symbol;

    //Constructor for leaf node
    Node(int symbol, int frequency){
        this.symbol = symbol;
        this.frequency = frequency;
    }

    //Internal Node
    Node(Node left, Node right){
        this.left = left;
        this.right = right;
        this.frequency = left.frequency + right.frequency;
    }

    //compare nodes by frequency (used by priority queue)
    @Override
    public int compareTo(Node other){
        return Integer.compare(this.frequency, other.frequency);
    }

    //check if the node is a leaf node
    boolean isLeaf(){
        return left == null && right == null;
    }
}

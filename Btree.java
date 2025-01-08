//Keaton Ylanan
//kky210002

import java.util.Scanner;
import java.io.*;

public class Btree {
    //global block size value
    public static final int BLOCK_SIZE = 512;
    public static final int[] MAGIC_ARRAY = {52, 51, 51, 55, 80, 82, 74, 51};

    //class for global values
    public static final int MIN_DEGREE = 10;

    static public class Node {
        //fields
        long bid;
        long parBid;
        long numPairs;
        long[] keyArr;       //19
        long[] valArr;       //19
        long[] childOffsets; //20    if a child is a leaf, the corresponding ID is zero (these are block IDs?)
        //24 bytes are unused

        //constructor
        public Node(RandomAccessFile raf, long blockId) throws IOException{
            bid = blockId;
            raf.seek(blockId * BLOCK_SIZE + 8);
            parBid = raf.readLong();
            numPairs = raf.readLong();
            keyArr = new long[2 * MIN_DEGREE - 1];
            //untested.
            for(int i = 0; i < 19; i++) {
                long curLong = raf.readLong();
                if (curLong == 0) break;
                keyArr[i] = curLong;
            }

            raf.seek(blockId * BLOCK_SIZE + 24 + 152);
            valArr = new long[2 * MIN_DEGREE - 1];
            for(int i = 0; i < 19; i++) {
                long curLong = raf.readLong();
                if (curLong == 0) break;
                valArr[i] = curLong;
            }

            raf.seek(blockId * BLOCK_SIZE + 24 + 152 + 152);
            childOffsets = new long[2 * MIN_DEGREE];
            for(int i = 0; i < 20; i++) {
                long curLong = raf.readLong();
                if (curLong == 0) break;
                childOffsets[i] = curLong;
            }
        }

        //this is only used for testing.
        public void printNode() {
            System.out.println("Block ID: " + bid);
            System.out.println("Parent Block ID: " + parBid);
            System.out.println("Number of Key/Val Pairs: " + numPairs);
            for(int i = 0; i < numPairs; i++) {
                //if (keyArr[i] == 0) break;
                System.out.print("Key " + i + ": " + keyArr[i] + " ");
                System.out.print("Value " + i + ": " + valArr[i] + " ");
                System.out.println("Child " + i + ": " + childOffsets[i]);
            }
            System.out.println("Child " + (int)numPairs + ": "  + childOffsets[(int)numPairs]);
        }

        public void writeNode(RandomAccessFile raf) throws IOException {
            raf.seek(bid * BLOCK_SIZE);
            raf.writeLong(bid);
            raf.writeLong(parBid);
            raf.writeLong(numPairs);
            for(int i = 0; i < numPairs; i++) {
                raf.writeLong(keyArr[i]);
            }
            for(int i = 0; i < 19 - numPairs; i++) {
                raf.writeLong(0);
            }

            raf.seek(bid * BLOCK_SIZE + 24 + 152);
            for(int i = 0; i < numPairs; i++) {
                raf.writeLong(valArr[i]);
            }
            for(int i = 0; i < 19 - numPairs; i++) {
                raf.writeLong(0);
            }

            raf.seek(bid * BLOCK_SIZE + 24 + 152 + 152);
            for(int i = 0; i < numPairs + 1; i++) {
                raf.writeLong(childOffsets[i]);
            }
            for(int i = 0; i < 20 - numPairs; i++) {
                raf.writeLong(0);
            }
            
        }
    }

    public static void main(String[] args) throws Exception {
        Scanner scnr = new Scanner(System.in);
        String commandString = "";
        String argString = "";
        int key = -1;
        int val = -1;
        boolean validInt = false;
        
        RandomAccessFile raf = null;
        RandomAccessFile old_raf = null;
        
        do {
            System.out.print("\n~~~~~ COMMAND LIST ~~~~~\nCREATE OPEN INSERT SEARCH LOAD PRINT EXTRACT QUIT\nEnter command: ");
            commandString = scnr.nextLine();
            commandString = commandString.substring(0,commandString.length());  //cut out endline
            commandString = commandString.toUpperCase();    //make string all capitals

            switch(commandString) {
                case "CREATE":
                    //Create a new index file
                    System.out.print("Enter file name: ");
                    argString = scnr.nextLine();
                    argString = argString.substring(0, argString.length());
                    
                    //Check and prompt for overwrite, if applicable - if denied, break
                    if (DoesFileExist(argString)) {
                        System.out.print("\"" + argString + "\" already exists. Would you like to overwrite it? Y/N: ");
                        String answer = Capitalize(scnr.nextLine());
                        if (!answer.equals("Y")) {
                            //assume any answer that is not "Y" is a no
                            System.out.println("Error: Not allowed to overwrite file.");
                            break;
                        }
                        System.out.println("\"" + argString + "\" has been overwritten.");
                    }

                    //If an old file was open, close it
                    if (raf != null) {
                        System.out.println("The previous index file was closed.");
                        raf.close();
                    }
                    
                    //open new file with read and write
                    raf = new RandomAccessFile(argString, "rwd");
                    raf.setLength(0);   //erase old contents of file

                    System.out.println("Index file \"" + argString + "\" successfully opened.");
                    //format empty file
                    raf.writeBytes("4337PRJ3");     //magic number
                    raf.writeLong(0);               //id for root (0 if null)
                    raf.writeLong(1);               //id for next node

                    //Following instructions should pad the whole 512 (BLOCK_SIZE) bytes of the first block... 488 empty bytes
                    raf.seek(BLOCK_SIZE - 1);
                    raf.write(0);

                break;

                case "OPEN":
                    //Open existing index file
                    System.out.print("Enter file name: ");
                    argString = scnr.nextLine();
                    argString = argString.substring(0, argString.length());

                    //Check if file exists
                    if(!DoesFileExist(argString)) {
                        System.out.println("Error: The file \"" + argString + "\" was not found.");
                        break;
                    }

                    //If an old file was open, close it
                    //Save value of old raf, in case the new one turns out to be invalid
                    old_raf = raf;

                    //Attempt to open file, give error message in case of failure
                    try {
                        raf = new RandomAccessFile(argString, "rwd");

                        boolean valid = true;       //if file is too short or does not have magic number, it is not valid.
                        if (raf.length() > 8) {
                            byte[] magicCheck = new byte[8];
                            raf.read(magicCheck, 0, 8);
                            for (int i = 0; i < 8; i++) {
                                if (magicCheck[i] != MAGIC_ARRAY[i]) {
                                    valid = false;
                                    break;
                                }
                            }
                        }
                        else valid = false;

                        if (valid) {
                            if (old_raf != null) {
                                System.out.println("The previous index file was closed.");
                                old_raf.close();
                            }
                            System.out.println("Index file \"" + argString + "\" successfully opened.");
                        }
                        else {
                            System.out.println("Error: The given file is not a valid index file.");
                            raf.close();
                            raf = old_raf;
                        }
                    }
                    catch (Exception e) {
                        System.out.println("Error: File was unable to be opened.");
                        raf = old_raf;
                    }

                break;
                
                case "INSERT":
                    if (raf == null) {
                        System.out.println("Error: There is no open index file.");
                        break;
                    }
                    //Prompt for key and value, and enter at index
                    validInt = false;
                    while(!validInt) {
                        System.out.print("Enter key: ");
                        argString = scnr.nextLine();
                        key = StringToInt(argString);
                        if (key != -1) validInt = true;
                    }
                    validInt = false;
                    while (!validInt) {
                        System.out.print("Enter value: ");
                        argString = scnr.nextLine();
                        val = StringToInt(argString);
                        if (val != -1) validInt = true;
                    }
                    
                    insertInSubtree(key, val, raf);
                break;
                
                case "SEARCH":
                    if (raf == null) {
                        System.out.println("Error: There is no open index file.");
                        break;
                    }
                    //Prompt and search for key
                    validInt = false;
                    while (!validInt) {
                        System.out.print("Enter key: ");
                        argString = scnr.nextLine();
                        key = StringToInt(argString);
                        if (key != -1) validInt = true;
                    }
                    //print if found, print error if not
                    long foundVal = SearchForKey(raf, (long)key, -1);
                    if (foundVal != -1) {
                        System.out.println("Key: " + key + "\nValue: " + foundVal);
                    }
                    else {
                        System.out.println("Error: Key not found.");
                    }
                break;
                
                case "LOAD":
                    if (raf == null) {
                        System.out.println("Error: There is no open index file.");
                        break;
                    }
                    //read file of comma separated integers, insert each pair as in "INSERT"
                    System.out.print("Enter name of CSV file: ");
                    argString = scnr.nextLine();
                    argString = argString.substring(0, argString.length());
                    //Check if file exists
                    if(!DoesFileExist(argString)) {
                        System.out.println("Error: The file \"" + argString + "\" was not found.");
                        break;
                    }
                    FileInputStream loadedFile = new FileInputStream(argString);
                    Scanner fileScanner = new Scanner(loadedFile);
                    fileScanner.useDelimiter(",");
                    while(fileScanner.hasNextLong()) {
                        long loadKey = fileScanner.nextLong();
                        long loadVal = fileScanner.nextLong();
                        insertInSubtree(loadKey, loadVal, raf);
                    }

                break;
                
                case "PRINT":
                    if (raf == null) {
                        System.out.println("Error: There is no open index file.");
                        break;
                    }
                    //print every key value pair in the index
                    raf.seek(8);
                    key = (int)raf.readLong();      //"key" holds the root
                    PrintBlock(raf, key, null);
                break;
                
                case "EXTRACT":
                    if (raf == null) {
                        System.out.println("Error: There is no open index file.");
                        break;
                    }
                    //save every key value pair in the index to a CSV file
                    System.out.print("Enter file name: ");
                    argString = scnr.nextLine();
                    argString = argString.substring(0, argString.length());

                    //Check and prompt for overwrite, if applicable - if denied, break
                    if (DoesFileExist(argString)) {
                        System.out.print("\"" + argString + "\" already exists. Would you like to overwrite it? Y/N: ");
                        String answer = Capitalize(scnr.nextLine());
                        if (!answer.equals("Y")) {
                            //assume any answer that is not "Y" is a no
                            System.out.println("Error: Not allowed to overwrite file.");
                            break;
                        }
                        System.out.println("\"" + argString + "\" has been overwritten.");
                    }
                    raf.seek(8);
                    key = (int)raf.readLong();      //"key" holds the root
                    PrintWriter writer = new PrintWriter(argString);
                    PrintBlock(raf, key, writer);
                    System.out.println("Tree has been extracted to \"" + argString + "\".");
                    writer.close();
                break;
                
                case "QUIT":
                    //If an old file was open, close it
                    if (raf != null) {
                        System.out.println("Closing index file.");
                        raf.close();
                    }
                    //Exit program
                    System.out.println("Exiting program.");
                break;
                
                default:
                    //Invalid command
                    System.out.println("Error: invalid command.");
                break;
            }

        } while (!commandString.equals("QUIT"));


        scnr.close();
    }

    public static void insertInSubtree(long key, long val, RandomAccessFile raf) throws IOException {
        //if there is no root, create the first block and insert key/val there
        raf.seek(8);
        long rootBlock = raf.readLong();
        if (rootBlock == 0) {
            //there is no root
            createNewBlock(raf, 0, key, val);

            //update position of root
            raf.seek(8);
            raf.writeLong(1);   //1st block is now the root
        }
        else {
            //if there is already a root...
            //traverse until a leaf is reached.
            long curBlock = rootBlock;

            while (!IsLeaf(raf, curBlock)) {
                Node curNode = new Node(raf, curBlock);
                //Must find the next appropriate block to enter into...
                //Compare current Key to Key X: if current is smaller, follow childOffset X and check if leaf.
                for (int i = 0; i < curNode.numPairs; i++) {
                    if (key < curNode.keyArr[i]) {
                        //current is smaller than key i
                        //follow child offset in position i
                        curBlock = curNode.childOffsets[i];
                        break;
                    }
                    else if (key > curNode.keyArr[i] && i == curNode.numPairs - 1) {
                        curBlock = curNode.childOffsets[i + 1];
                        break;
                    }
                    else if (key > curNode.keyArr[i]) {
                        //current is larger than key i, so keep going
                    }
                    else {
                        //This is an error! Get out and don't make any changes
                        System.out.println("Error: The key entered already exists.");
                        return;
                    }
                } 
            }
            Node leafNode = new Node(raf, curBlock);
            //A leaf has been reached

            //check if this node is full
            raf.seek(curBlock * BLOCK_SIZE + 16);
            if (raf.readLong() < 19) {
                leafNode = insertInBlock(raf, leafNode, key, val, 0);
                if (leafNode == null) return;   //this means an error has happened...
            }
            else {
                //this node is full
                splitNode(raf, leafNode, false);

                //put the actual key in the tree
                insertInSubtree(key, val, raf);
            }
        }

    }

    //splits a node into two
    //this function creates either 1 or 2 new nodes that go out of scope at the end of the function
    public static long splitNode(RandomAccessFile raf, Node originalNode, boolean returnLeft) throws IOException{
        //split node into 2 children and promote the median of the node
        //new node should be written to id stored in header of file. - but there could possibly be two new nodes.
        raf.seek(16);
        long newNodeBid = raf.readLong();

        createNewBlock(raf, originalNode.parBid, originalNode.keyArr[10], originalNode.valArr[10]);

        Node partnerNode = new Node(raf, newNodeBid);
        partnerNode.parBid = originalNode.parBid;       //the two nodes should have the same parent
        //first half of keys and vals stay in the orginal leaf node (first 9)
        //second half of keys and vals go into into the partner node (last 9)
        for (int i = 0; i < 9; i++) {
            partnerNode.keyArr[i] = originalNode.keyArr[i + 10];
            partnerNode.valArr[i] = originalNode.valArr[i + 10];
        }
        originalNode.numPairs = 9;
        partnerNode.numPairs = 9;
        //first 10 childOffsets stay in the leaf, last 10 child offsets go to the partner (10 - 19)
        for (int i = 0; i < 10; i++) {
            partnerNode.childOffsets[i] = originalNode.childOffsets[i + 10];
        }

        //promote center key & val
        long centerKey = originalNode.keyArr[9];
        long centerVal = originalNode.valArr[9];
        long centerChild = partnerNode.bid;                 //used later if there happens to be a promotion to a full node

        originalNode.writeNode(raf);
        //System.out.println("LEFT NODE:");
        //originalNode.printNode();                       //Original node should go out of scope after this...
        long ogBid = originalNode.bid;
        long ogParbid = originalNode.parBid;

        partnerNode.writeNode(raf);
        //System.out.println("RIGHT NODE");
        //partnerNode.printNode();                        //Partner node should go out of scope after this...
        long ptBid = partnerNode.bid;
        long ptParbid = partnerNode.parBid;

        if (ogParbid == 0) {
            //there is no parent... make a new node that will be Leaf and Partner's parent
            createNewBlock(raf, ogParbid, centerKey, centerVal);
            Node parentNode = new Node(raf, newNodeBid + 1);
            parentNode.numPairs = 1;

            parentNode.keyArr[0] = centerKey;
            parentNode.valArr[0] = centerVal;

            //original leaf node is left child, partner node is right child
            parentNode.childOffsets[0] = ogBid;
            parentNode.childOffsets[1] = newNodeBid;

            //Parent of the new parent be the parent of the original node
            ogParbid = parentNode.bid;
            ptParbid = parentNode.bid;

            raf.seek(8);
            long rootBlock = raf.readLong();

            if (rootBlock == ogBid) {
                //update the root node
                raf.seek(8);
                raf.writeLong(parentNode.bid);
            }
            
            parentNode.writeNode(raf);
            //System.out.println("PARENT NODE:");
            //parentNode.printNode();
        }
        else {
            //there is a parent, so put the center key/val into it... -> Promote
            //get the parent of leafNode
            Node parNode = new Node(raf, ogParbid);
            long nextBid = -1;
            if (parNode.numPairs < 19) {
                //The parent is not full
                insertInBlock(raf, parNode, centerKey, centerVal, newNodeBid);
            }
            else {
                //the parent is full
                //choose left or right
                if (centerKey < parNode.keyArr[9]) {   //median
                    nextBid = splitNode(raf, parNode, true);
                }
                else {
                    nextBid = splitNode(raf, parNode, false);
                }
                parNode = new Node(raf, nextBid);

                //the above splitNode()'s parNode is what we want... one of its children...
                //centerChild (set above)
                insertInBlock(raf, parNode, centerKey, centerVal, centerChild);
                ogParbid = parNode.bid;
                ptParbid = parNode.bid;
                for (int i = 0; i < 9; i++) {
                    raf.seek(parNode.childOffsets[i] * BLOCK_SIZE + 8);
                    raf.writeLong(parNode.bid);
                }
            }
        }

        raf.seek(ogBid * BLOCK_SIZE + 8);
        raf.writeLong(ogParbid);

        raf.seek(ptBid * BLOCK_SIZE + 8);
        raf.writeLong(ptParbid);

        if (returnLeft) return originalNode.bid;
        else return partnerNode.bid;
    }

    //creates a new block in the file, requires one key/val pair
    public static void createNewBlock(RandomAccessFile raf, long parentBlockID, long key, long val) throws IOException {
        //look for the next position to insert a block
        raf.seek(16);
        long nextInsertion = raf.readLong();

        //Update next position for block... Should never be done anywhere else.
        raf.seek(16);
        raf.writeLong(nextInsertion + 1);

        //insert block at that position
        raf.seek(nextInsertion * BLOCK_SIZE);
        raf.writeLong(nextInsertion);       //block id of this block
        raf.writeLong(parentBlockID);       //block id of this block's parent
        raf.writeLong(1);                 //number of key/val pairs - is 1, currently.
        raf.seek((nextInsertion + 1) * BLOCK_SIZE - 1);
        raf.write(0);                     //pad rest of block

        //Find the location for the key
        raf.seek(nextInsertion * BLOCK_SIZE + 24);
        raf.writeLong(key);
        //Find the location for the value
        raf.seek(nextInsertion * BLOCK_SIZE + 24 + 152);
        raf.writeLong(val);
    }

    //return null means a key has been repeated; the function calling this should also return right away.
    //this function assumes that the block is not full!
    //inserts key & val into leafNode
    public static Node insertInBlock(RandomAccessFile raf, Node leafNode, long key, long val, long child) throws IOException {
        //this node is not full
        int insertPosition = 0;
        //insertion should happen here
        while(insertPosition < leafNode.numPairs) {
            if (key < leafNode.keyArr[insertPosition]) {
                //current is smaller than key i, so it should be inserted here.
                //System.out.println(key + " is smaller than " + leafNode.keyArr[insertPosition]);
                break;                        
            }
            else if (key > leafNode.keyArr[insertPosition]) {
                //current is larger than key i, so keep going
                //System.out.println(key + " is larger than " + leafNode.keyArr[insertPosition]);
                insertPosition++;
            }
            else {
                //[!!!] THIS IS AN ERROR! Get out and don't make any changes
                System.out.println("Error: The key entered already exists.");
                return null;
            }
        }
        //insertPosition now holds the correct position to insert at.
        for (int i = (int)leafNode.numPairs - 1; i >= insertPosition; i--) {
            leafNode.keyArr[i + 1] = leafNode.keyArr[i];
            leafNode.valArr[i + 1] = leafNode.valArr[i];
        }
        //handling childOffsets... might be incorrect? not tested.
        for (int i = (int)leafNode.numPairs; i >= insertPosition; i--) {
            leafNode.childOffsets[i + 1] = leafNode.childOffsets[i];
        }
        leafNode.keyArr[insertPosition] = key;
        leafNode.valArr[insertPosition] = val;
        leafNode.childOffsets[insertPosition + 1] = child;      //+1
        leafNode.numPairs++;
        //leafNode.printNode();
        leafNode.writeNode(raf);
        return leafNode;
    }

    public static int StringToInt(String s) {
        int num = -1;
        s = s.substring(0, s.length()); //cut out endline
        try {
            num = Integer.valueOf(s);   //return an error if what was entered was not an integer
        }
        catch (Exception e) {
            System.out.println("Error: input was not an integer.");
            num = -1;
        }
        return num;
    }

    public static String Capitalize(String s) {
            s = s.substring(0, s.length());
            s = s.toUpperCase();
        return s;
    }

    public static boolean DoesFileExist(String s) {
        File f = new File(s);
        if (f.isFile()) return true;
        else return false;
    }

    //not sure if this is necessary, but to be safe...
    //if a node doesn't have a first child, then it should be a leaf.
    public static boolean IsLeaf(RandomAccessFile raf, long blockNum) throws IOException {
        raf.seek(blockNum * BLOCK_SIZE + 328);
        if (raf.readLong() == 0) return true;
        else return false;
    }

    //if searchBid == -1, then use the root's position.
    public static long SearchForKey(RandomAccessFile raf, long key, long searchBid) throws IOException {
        if (searchBid == -1) {
            //find root
            raf.seek(8);
            searchBid = raf.readLong();
        }

        long currentKey = -1;
        long offsetToFollow = -1;
        //get number of keys
        raf.seek(searchBid * BLOCK_SIZE + 16);
        long numKeys = raf.readLong();
        for(int i = 0; i < numKeys; i++) {
            raf.seek(searchBid * BLOCK_SIZE + 24 + i * 8);
            currentKey = raf.readLong();

            if (currentKey == key) {
                //we found it! Now, let's get the value
                raf.seek(searchBid * BLOCK_SIZE + 24 + 152 + i * 8);
                return raf.readLong();
            }
            else if (key < currentKey) {
                //take childOffset
                raf.seek(searchBid * BLOCK_SIZE + 24 + 152 + 152 + i * 8);
                offsetToFollow = raf.readLong();     //this is the childOffset
                return SearchForKey(raf, key, offsetToFollow);
            }
            //else go around the for loop again
        }
        //we fell through, which means we should check if child[i + 1] is a thing...
        raf.seek(searchBid * BLOCK_SIZE + 24 + 152 + 152 + (numKeys) * 8);
        offsetToFollow = raf.readLong();
        if (offsetToFollow == 0) {
            //the key does not exist!
            return -1;
        }
        else {
            return SearchForKey(raf, key, offsetToFollow);
        }
    }

    public static void PrintBlock(RandomAccessFile raf, long bid, PrintWriter writer) throws IOException{
        raf.seek(bid * BLOCK_SIZE + 16);
        long numPairs = raf.readLong();
        for(int i = 0; i < numPairs; i++) {
            raf.seek(bid * BLOCK_SIZE + 24 + 152 + 152 + i * 8);        //locate childOffset[i]
            long childBid = raf.readLong();
            if (childBid != 0) PrintBlock(raf, childBid, writer);               //if childOffset[i] is not 0, explore it

            //print the key and value
            raf.seek(bid * BLOCK_SIZE + 24 + i * 8);
            long key = raf.readLong();
            raf.seek(bid * BLOCK_SIZE + 24 + 152 + i * 8);
            long val = raf.readLong();
            if (writer == null) System.out.println("Key: " + key + "   \tValue: " + val);
            else {
                writer.print(key + "," + val + ",");
            }
        }
        //check if childOffest[numpairs] is empty; if not, explore it.
        raf.seek(bid * BLOCK_SIZE + 24 + 152 + 152 + numPairs * 8);
        long childBid = raf.readLong();
        if (childBid != 0) PrintBlock(raf, childBid, writer);

        return;
    }
}

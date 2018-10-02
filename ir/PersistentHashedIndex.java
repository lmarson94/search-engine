/*  
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 * 
 *   Johan Boye, KTH, 2018
 */  

package ir;

import java.io.*;
import java.util.*;
import java.nio.ByteBuffer;

/*
 *   Implements an inverted index as a hashtable on disk.
 *   
 *   Both the words (the dictionary) and the data (the postings list) are
 *   stored in RandomAccessFiles that permit fast (almost constant-time)
 *   disk seeks. 
 *
 *   When words are read and indexed, they are first put in an ordinary,
 *   main-memory HashMap. When all words are read, the index is committed
 *   to disk.
 */
public class PersistentHashedIndex implements Index {

    /** The directory where the persistent index files are stored. */
    public static final String INDEXDIR = "index";

    /** The dictionary file name */
    public static final String DICTIONARY_FNAME = "dictionary";

    /** The dictionary file name */
    public static final String DATA_FNAME = "data";

    /** The terms file name */
    public static final String TERMS_FNAME = "terms";

    /** The doc info file name */
    public static final String DOCINFO_FNAME = "docInfo";

    /** The dictionary hash table on disk can fit this many entries. */
    public static final long TABLESIZE = 611953L;  // 50,000th prime number
    //public final static long TABLESIZE = 3500017L; // guardian
    
    public static final int ENTRY_BYTE_SIZE = 14;

    /** The dictionary hash table is stored in this file. */
    RandomAccessFile dictionaryFile;

    /** The data (the PostingsLists) are stored in this file. */
    RandomAccessFile dataFile;

    /** Pointer to the first free memory cell in the data file. */
    long free = 0L;

    /** The cache as a main-memory hash map. */
    HashMap<String,PostingsList> index = new HashMap<String,PostingsList>();
    
    // ===================================================================

    /**
     *   A helper class representing one entry in the dictionary hashtable.
     */ 
    public class Entry {
    	String token;
		long ptr;
		int size;
		Entry(String t, long p, int s) {
			ptr = p;
			size = s;
			token = t;
		}
    }

    // ==================================================================

    
    /**
     *  Constructor. Opens the dictionary file and the data file.
     *  If these files don't exist, they will be created. 
     */
    public PersistentHashedIndex() {
        try {
            dictionaryFile = new RandomAccessFile( INDEXDIR + "/" + DICTIONARY_FNAME, "rw" );
            dataFile = new RandomAccessFile( INDEXDIR + "/" + DATA_FNAME, "rw" );
        }
        catch ( IOException e ) {
            e.printStackTrace();
        }
        try {
            readDocInfo();
        }
        catch ( FileNotFoundException e ) {
        }
        catch ( IOException e ) {
            e.printStackTrace();
        }
    }
    
    public PersistentHashedIndex(int a) {
    	
    }

    /**
     *  Writes data to the data file at a specified place.
     *
     *  @return The number of bytes written.
     */ 
    int writeData( RandomAccessFile file, String dataString, long ptr ) {
        try {
            file.seek( ptr ); 
            byte[] data = dataString.getBytes();
            file.write( data );
            return data.length;
        }
        catch ( IOException e ) {
            e.printStackTrace();
            return -1;
        }
    }


    /**
     *  Reads data from the data file
     */ 
    String readData(RandomAccessFile file, long ptr, int size ) {
        try {
            file.seek( ptr );
            byte[] data = new byte[size];
            file.readFully( data );
            return new String(data);
        }
        catch ( IOException e ) {
            e.printStackTrace();
            return null;
        }
    }


    // ==================================================================
    //
    //  Reading and writing to the dictionary file.

    /*
     *  Writes an entry to the dictionary hash table file. 
     *
     *  @param entry The key of this entry is assumed to have a fixed length
     *  @param ptr   The place in the dictionary file to store the entry
     */
    void writeEntry(RandomAccessFile file,  Entry entry, long ptr) {
    	try {
    		file.seek( ptr ); 
            byte[] data =  entryToByteArray(entry);   	            
            file.write( data );       	
        }
        catch ( IOException e ) {
            e.printStackTrace();
            return;
        }
    }

    /**
     *  Reads an entry from the dictionary file.
     *
     *  @param ptr The place in the dictionary file where to start reading.
     */
    Entry readEntry(RandomAccessFile dictionaryFile, RandomAccessFile dataFile, long ptr) {  
    	try {    		
    		dictionaryFile.seek(ptr);
            byte[] data = new byte[ENTRY_BYTE_SIZE];
            dictionaryFile.readFully( data );
//            Entry e = byteArrayToEntry(data);
//            if(e != null) System.err.println(e.token + " " + e.ptr + " " + e.size);
            return byteArrayToEntry(dataFile, data);
        }
        catch ( IOException e ) {
            return null;
        }
    }

    //Larson's hash function
    long hash1(String token) {
    	long h = 0L;
		for (int i=0 ; i < token.length() ; i++) {
			h = h*101 + (int)token.charAt(i);
		}
    	return Math.abs(h);
    }
    
    //Bernstein's hash function
    long hash2(String token) {
    	long h = 5381L;
		for (int i=0 ; i < token.length() ; i++) {
			h = h*33 + (int)token.charAt(i);
		}
		return Math.abs(h);
    }
    
    long hash(String token, int collisions, long tableSize) {
    	
    	return (Math.abs((hash1(token) + collisions*hash2(token))) % tableSize) * ENTRY_BYTE_SIZE;
    	//return (Math.abs((hash1(token) + collisions)) % tableSize) * ENTRY_BYTE_SIZE;
    	
    }
    
    byte[] entryToByteArray(Entry e) {
    	ByteBuffer buffer = ByteBuffer.allocate(ENTRY_BYTE_SIZE);
    	
    	buffer.putChar((char)0x2F79);
    	buffer.putLong(ENTRY_BYTE_SIZE-4-8, e.ptr);
    	buffer.putInt(ENTRY_BYTE_SIZE-4, e.size);
    	
        return buffer.array();
    }
    
    Entry byteArrayToEntry(RandomAccessFile file, byte[] b) {
    	ByteBuffer buffer;	
    	String token;
    	long ptr;
    	int size;
    	
    	//read flag
    	buffer = ByteBuffer.wrap(b, 0, 2);
    	if(buffer.getChar() != (char)0x2F79) return null;
    	
    	//read ptr
    	buffer = ByteBuffer.wrap(b, ENTRY_BYTE_SIZE-4-8, 8);
    	ptr = buffer.getLong();
    	
    	//read size
    	buffer = ByteBuffer.wrap(b, ENTRY_BYTE_SIZE-4, 4);
    	size = buffer.getInt();
    	
    	//read token
    	token = readData(file, ptr, size);
    	token = (new StringTokenizer(token)).nextToken();
    	
    	return new Entry(token, ptr, size);
    }

    // ==================================================================

    /**
     *  Writes the document names and document lengths to file.
     *
     * @throws IOException  { exception_description }
     */
    protected void writeDocInfo(int counter) throws IOException {
    	FileOutputStream fout;
    	if(counter < 0) fout = new FileOutputStream( INDEXDIR + "/docInfo" );
    	else fout = new FileOutputStream( INDEXDIR + "/docInfo" + counter );
        for (Map.Entry<Integer,String> entry : docNames.entrySet()) {
            Integer key = entry.getKey();
            String docInfoEntry = key + ";" + entry.getValue() + ";" + docLengths.get(key) + "\n";
            fout.write(docInfoEntry.getBytes());
        }
        fout.close();
    }


    /**
     *  Reads the document names and document lengths from file, and
     *  put them in the appropriate data structures.
     *
     * @throws     IOException  { exception_description }
     */
    protected void readDocInfo() throws IOException {
        File file = new File( INDEXDIR + "/docInfo" );
        FileReader freader = new FileReader(file);
        try (BufferedReader br = new BufferedReader(freader)) {
            String line;
            while ((line = br.readLine()) != null) {
               String[] data = line.split(";");
               docNames.put(new Integer(data[0]), data[1]);
               docLengths.put(new Integer(data[0]), new Integer(data[2]));
            }
        }
        freader.close();
    }


    /**
     *  Write the index to files.
     */
    public void writeIndex(boolean scalable) {
        int i, len, collisions = 0;
        long dicptr;
        String plString;
        Entry dicEntry;
        try {
        	if(!scalable) {
	            // Write the 'docNames' and 'docLengths' hash maps to a file
	            writeDocInfo(-1);
        	}
        	
            // Write the dictionary and the postings list
            for(Map.Entry<String,PostingsList> entry : index.entrySet()) {
            	
            	//Write posting list
            	plString = postingsListToString(entry.getKey(), entry.getValue());
            	len = writeData(dataFile, plString, free);
            	
            	//Write Dictionary
            	dicEntry = new Entry(entry.getKey(), free, len);
            	dicptr = hash(entry.getKey(), 0, TABLESIZE);
            	//deal with collisions
            	i=0;
            	while(readEntry(dictionaryFile, dataFile, dicptr) != null) {
            		collisions++;
            		dicptr = hash(entry.getKey(), ++i, TABLESIZE);
            	}
            	writeEntry(dictionaryFile, dicEntry, dicptr);
            	
            	free += len;
            }
            
        }
        catch ( IOException e ) {
            e.printStackTrace();
        }
        System.err.println( collisions + " collisions." );
    }
    
    protected String postingsListToString(String t, PostingsList p) {
    	StringBuffer str = new StringBuffer();
    	PostingsEntry entry;
    	
    	str.append(t + " ");
    	for(int e=0; e<p.list.size(); e++) {
    		entry = p.list.get(e);
    		str.append(entry.docID + ":");
    		
    		for(int o=0; o<entry.offset.size(); o++) {
    			str.append(entry.offset.get(o));
    			if(o < entry.offset.size()-1)  str.append(".");
    		}
    		
    		if(e < p.list.size()-1) str.append(":");
    	}
    	
    	return str.toString();
    }
    
    protected PostingsList stringToPostingList(String s) {
    	PostingsList pl = new PostingsList();
    	StringTokenizer offsetToken, docToken = new StringTokenizer(s);
    	ArrayList<Integer> offset;
    	String buf;
    	int docID;
    	
    	buf = docToken.nextToken(); // ignore token string
    	docToken = new StringTokenizer(s.substring(buf.length()+1, s.length()), ":");
    	while(docToken.hasMoreTokens()) {
    		buf = docToken.nextToken();
    		docID = Integer.parseInt(buf);
    		
    		offset = new ArrayList<>();
    		offsetToken = new StringTokenizer(docToken.nextToken(), ".");
    		while(offsetToken.hasMoreTokens()) {
    			buf = offsetToken.nextToken();
    			offset.add(Integer.parseInt(buf));
    		}
    		
    		pl.addEntry(docID, offset);
    	}
    	
    	return pl;
    }
 
    // ==================================================================

    
    //Testing method
    public boolean testPostingsList(PostingsList p1, PostingsList p2, String token) {
    	PostingsEntry entry1, entry2;
    	
    	if(p1.list.size() != p2.list.size()) {
    		System.err.println(token + "list size " + p1.list.size() + " " + p2.list.size());
    		return false;
    	}
    	
    	for(int e=0; e<p1.list.size(); e++) {
    			entry1 = p1.list.get(e);
    			entry2 = p2.list.get(e);
    			
    			if(entry1.docID != entry2.docID) {
    				System.err.println(token + "docID " + entry1.docID + " " + entry2.docID);
    				return false;
    			}
    			
    			if(entry1.offset.size() != entry2.offset.size()) {
    				System.err.println(token + "offset size" + entry1.docID + " " + entry1.offset.size() + " " + entry2.offset.size());
    				return false;
    			}
    			for(int o=0; o<entry1.offset.size(); o++) {
    				if((int)entry1.offset.get(o) != (int)entry2.offset.get(o)) {
    					System.err.println(token + " " + "offset " + entry1.docID + " " + entry1.offset.get(o) + " " + entry2.offset.get(o));
    					return false;
    				}
    			}
    	}
    	return true;
    }

    /**
     *  Returns the postings for a specific term, or null
     *  if the term is not in the index.
     */
    public PostingsList getPostings( String token ) {
    	int i = 0;
    	long ptr = hash(token, 0, TABLESIZE);
    	Entry dicEntry = readEntry(dictionaryFile, dataFile, ptr);
    	if(dicEntry == null) return null;
    	while(!dicEntry.token.equals(token)) {
    		ptr = hash(token, ++i, TABLESIZE);
    		dicEntry = readEntry(dictionaryFile, dataFile, ptr);
    		if(dicEntry == null) return null;
    	}
    	
    	String plString = readData(dataFile, dicEntry.ptr, dicEntry.size); 
    	
    	return stringToPostingList(plString);
    }

    /**
     *  Inserts this token in the main-memory hashtable.
     */
    public void insert( String token, int docID, int offset ) {
    	if(index.get(token) == null) {
    		index.put(token, new PostingsList(docID, offset));
    	} else {
    		index.get(token).addEntry(docID, offset);
    	}
    }

    /**
     *  Write index to file after indexing is done.
     */
    public void cleanup() {
    	
        System.err.println( index.keySet().size() + " unique words" );
        System.err.print( "Writing index to disk..." );
        writeIndex(false);
        System.err.println( "done!" );
        
        boolean errors = false;
        for(Map.Entry<String,PostingsList> entry : index.entrySet()) {
    		if(!testPostingsList(entry.getValue(), getPostings(entry.getKey()), entry.getKey())) errors = true;
    	}
        if(!errors) System.out.println("No errors!!");
     }


}
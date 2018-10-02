/*  
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 * 
 *   Johan Boye, 2017
 */  

package ir;

import java.util.ArrayList;

public class PostingsEntry implements Comparable<PostingsEntry> {
    
    public int docID;
    public ArrayList<Integer> offset = new ArrayList<>();
    public double score = 0;

    public PostingsEntry(int docID) {
    	this.docID = docID;
    }
    
    public PostingsEntry(int docID, int offset) {
    	this.docID = docID;
    	addOffset(offset);
    }
    
    public PostingsEntry(int docID, ArrayList<Integer> offset) {
    	this.docID = docID;
    	this.offset = offset;
    }
    
    /**
     *  PostingsEntries are compared by their score (only relevant
     *  in ranked retrieval).
     *
     *  The comparison is defined so that entries will be put in 
     *  descending order.
     */
    public int compareTo( PostingsEntry other ) {
    	return Double.compare(other.score, score);
    }

    public void addOffset(int offset) {
    	this.offset.add(offset);
    	
    }
    
}

    

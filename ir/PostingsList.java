/*  
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 * 
 *   Johan Boye, 2017
 */  

package ir;

import java.util.ArrayList;
import java.util.Collections;

public class PostingsList {
    
    /** The postings list */
    public ArrayList<PostingsEntry> list = new ArrayList<PostingsEntry>();
    private int skipSize = 50;
    
    public PostingsList() {}
    
    public PostingsList(int docID, int offset) {
    	addEntry(docID, offset);
    }
    
    public PostingsList(ArrayList<PostingsEntry> list) {
    	this.list = list;
    }
    
    /** Number of postings in this list. */
    public int size() {
    	return list.size();
    }

    /** Returns the ith posting. */
    public PostingsEntry get( int i ) {
    	return list.get( i );
    }

    public void addEntry(int docID) {
    	this.list.add(new PostingsEntry(docID));
    }
    
    public void addEntry(int docID, ArrayList<Integer> offset) {
    	list.add(new PostingsEntry(docID, offset));
    }
    
    public void addEntry(PostingsEntry entry) {
    	list.add(entry);
    }
    
    public void addEntry(int docID, int offset) {
    	if(list.size() > 0 && list.get(list.size()-1).docID == docID)
    		list.get(list.size()-1).addOffset(offset);    	
    	else
    		this.list.add(new PostingsEntry(docID, offset));
    }
    
}
	

			   

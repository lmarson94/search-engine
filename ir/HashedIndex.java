/*  
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 * 
 *   Johan Boye, 2017
 */  


package ir;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


/**
 *   Implements an inverted index as a Hashtable from words to PostingsLists.
 */
public class HashedIndex implements Index {


    /** The index as a hashtable. */
    public HashMap<String,PostingsList> index = new HashMap<String,PostingsList>(32768);
        
    /**
     *  Inserts this token in the hashtable.
     */
    public void insert( String token, int docID, int offset ) {
    	
    	if(getPostings(token) == null) {
    		index.put(token, new PostingsList(docID, offset));
    	} else {
    		index.get(token).addEntry(docID, offset);
    	}
    	
    }


    /**
     *  Returns the postings for a specific term, or null
     *  if the term is not in the index.
     */
    public PostingsList getPostings( String token ) {
    	return index.get(token);
    }


    /**
     *  No need for cleanup in a HashedIndex.
     */
    public void cleanup() {
    }


	public double normD(int docID) {
		int N = Index.docLengths.size();
		double df, idf, norm = 0;
		
		for(Map.Entry<String,PostingsList> entry : index.entrySet()) {
			df = entry.getValue().size();
			idf = Math.log(N / df);
			
			for(PostingsEntry e:entry.getValue().list) {
				if(e.docID == docID) {
					norm += e.offset.size() * idf;
					break;
				}
			}
		}
		return norm;
	}
}

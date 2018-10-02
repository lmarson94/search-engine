/*  
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 * 
 *   Johan Boye, 2017
 */  

package ir;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.StringTokenizer;
import java.util.Iterator;
import java.util.Map;
import java.nio.charset.*;
import java.io.*;


/**
 *  A class for representing a query as a list of words, each of which has
 *  an associated weight.
 */
public class Query {

    /**
     *  Help class to represent one query term, with its associated weight. 
     */
    class QueryTerm {
		String term;
		double weight;
		QueryTerm( String t, double w ) {
		    term = t;
		    weight = w;
		}
    }

    /** 
     *  Representation of the query as a list of terms with associated weights.
     *  In assignments 1 and 2, the weight of each term will always be 1.
     */
    public ArrayList<QueryTerm> queryterm = new ArrayList<QueryTerm>();

    /**  
     *  Relevance feedback constant alpha (= weight of original query terms). 
     *  Should be between 0 and 1.
     *  (only used in assignment 3).
     */
    double alpha = 0.1;

    /**  
     *  Relevance feedback constant beta (= weight of query terms obtained by
     *  feedback from the user). 
     *  (only used in assignment 3).
     */
    double beta = 1 - alpha;
    
    
    /**
     *  Creates a new empty Query 
     */
    public Query() {
    }
    
    
    /**
     *  Creates a new Query from a string of words
     */
    public Query( String queryString  ) {
		StringTokenizer tok = new StringTokenizer( queryString );
		while ( tok.hasMoreTokens() ) {
		    queryterm.add( new QueryTerm(tok.nextToken(), 1.0) );
		}    
    }
    
    
    /**
     *  Returns the number of terms
     */
    public int size() {
    	return queryterm.size();
    }
    
    
    /**
     *  Returns the Manhattan query length
     */
    public double length() {
		double len = 0;
		for ( QueryTerm t : queryterm ) {
		    len += t.weight; 
		}
		return len;
    }
    
    
    /**
     *  Returns a copy of the Query
     */
    public Query copy() {
		Query queryCopy = new Query();
		
		for ( QueryTerm t : queryterm ) {
		    queryCopy.queryterm.add( new QueryTerm(t.term, t.weight) );
		}
		
		return queryCopy;
    }
    
    public void addQueryTerm(String term, double weight) {
    	this.queryterm.add(new QueryTerm(term, weight));
    }
    
    public void removeLast() {
    	this.queryterm.remove(queryterm.size() - 1);
    }
    
    /**
     *  Expands the Query using Relevance Feedback
     *
     *  @param results The results of the previous query.
     *  @param docIsRelevant A boolean array representing which query results the user deemed relevant.
     *  @param engine The search engine object
     */
    public void relevanceFeedback( PostingsList results, boolean[] docIsRelevant, Engine engine ) {
    	HashMap<String, QueryTerm> querytermTmp = new HashMap<>();
    	HashMap<String, Integer> doctf;
    	ArrayList<Integer> relevantResults = new ArrayList<>();
    	String token;
    	QueryTerm qt;
    	double idf, df, weight;
    	int N = Index.docLengths.size();
    	int n = 0;
    	int len;
    	
    	// find relevant documents
    	for(int i = 0; i < docIsRelevant.length; i++) {
    		if(docIsRelevant[i]) {
    			n++;
    			relevantResults.add(results.list.get(i).docID);
    		}
    	}
    	
    	if (n > 0) {
	    	// add original query terms
	    	for(QueryTerm q: queryterm) {
	    		if (!q.term.contains("*")) {
		    		q.weight *= alpha / queryterm.size();
		    		querytermTmp.put(q.term, q);
	    		}
	    	}
	    	
	    	for(int docID: relevantResults) {
	    		doctf = engine.indexer.readDoctf(Index.docNames.get(docID));
	    		len = Index.docLengths.get(docID);
	    		
	    		for (Map.Entry<String,Integer> entry:doctf.entrySet()) {
	    			token = entry.getKey();
	    			df = engine.index.getPostings(token).size();
	    			idf = Math.log(N / df);
	    			
	    			weight = (beta / n) * entry.getValue() * idf / len;
	    			
	    			if (querytermTmp.containsKey(token))
	    				querytermTmp.get(token).weight += weight;
	    			else {
	    				qt = new QueryTerm(token, weight);
						querytermTmp.put(token, qt);
	    			}
	    		}
	    		
	    	}    	
	    	queryterm = new ArrayList<>(querytermTmp.values());
    	}
    }
}
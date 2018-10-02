/*  
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 * 
 *   Johan Boye, 2017
 */  

package ir;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import ir.Query.QueryTerm;

/**
 *  Searches an index for results of a query.
 */
public class Searcher {

    /** The index to be searched by this Searcher. */
    Index index;
    
    int K;
    KGramIndex kgramindex;
    
    HashMap<String, Double> pageRank = new HashMap<>();
    
    /** Constructor */
    public Searcher( Index index) {
        this.index = index;
    }
    
    public Searcher( Index index, KGramIndex kgramindex) {
        this.index = index;
        this.kgramindex = kgramindex;
        this.K = kgramindex.K;
    }
    
    /**
     *  Searches the index for postings matching the query.
     *  @return A postings list representing the result of the query.
     */
    public PostingsList search( Query query, QueryType queryType, RankingType rankingType ) { 
    	
    	if(query.queryterm.isEmpty()) return null;
    	
    	// check for wildcards
    	ArrayList<Query> wildcardQuery = new ArrayList<>();
    	if(this.kgramindex != null) {
    		String term;
    		int idx;
    		
    		for(int i = 0; i < query.queryterm.size(); i++) {
    			
    			term = query.queryterm.get(i).term;
    			idx = term.indexOf("*");
    			if(term.length() >= K && idx > -1) {
    				wildcardQuery.add(addMatchingTerms(new Query(), term));
    			}
    			
    		}
    	}
    	    	
		PostingsList answer, list;
		
		if (queryType == QueryType.RANKED_QUERY) {
			
			String term;
    		int idx;
			for(int i = 0; i < query.queryterm.size(); i++) {
    			term = query.queryterm.get(i).term;
    			idx = term.indexOf("*");
    			if(term.length() >= K && idx > -1) {
    				query.queryterm.remove(i--);
    			}
    		}
			
			if (wildcardQuery.size() > 0) {
				for (Query q:wildcardQuery) {
					for (QueryTerm t:q.queryterm) {
						query.addQueryTerm(t.term, t.weight);
					}
				}
			}
			
			HashMap<Integer, PostingsEntry> check = new HashMap<>();
			int N = Index.docLengths.size();
			double idf, df;
			double score; 
			double tfidfWeight, pageRankWeight;
			String fName;
			
			tfidfWeight = 0.01;
			pageRankWeight = 1.0;
			
			if(rankingType == RankingType.TF_IDF) {
				tfidfWeight = 1.0;
				pageRankWeight = 0.0;
			} 
			else if(rankingType == RankingType.PAGERANK) {
				tfidfWeight = 0.0;
				pageRankWeight = 1.0;
			}
			
			for(int i = 0; i < query.queryterm.size(); i++) {
				
				list = index.getPostings(query.queryterm.get(i).term);
				
				if(list == null) return null;
				
				// Calculate scores
				df = index.getPostings(query.queryterm.get(i).term).size();
				idf = Math.log(N / df);
				for(PostingsEntry e: list.list) {
					// calculate tf-idf
					score = e.offset.size() * idf / Index.docLengths.get(e.docID);
					score *= tfidfWeight;
					
					// retrieve pagerank
					fName = Index.docNames.get(e.docID);
					fName = fName.substring(10, fName.length());
					if(pageRank.containsKey(fName)) {
						score += pageRank.get(fName) * pageRankWeight;	
					}
					
					e.score = score * query.queryterm.get(i).weight;

		    		if(check.containsKey(e.docID)) 
		    			check.get(e.docID).score += e.score;
		    		else
		    			check.put(e.docID, e);
		    	}				
			}			
			
			answer = new PostingsList(new ArrayList<>(check.values()));
			Collections.sort(answer.list);
			
		} else { // intersection or phrase query
			
			if (wildcardQuery.size() > 0) {
				HashMap<Integer, PostingsEntry> check = intersectWildcard(0, 0, query, new Query(), queryType, wildcardQuery, new HashMap<>());
				answer = new PostingsList(new ArrayList<>(check.values()));
			} 
			else {
				answer = index.getPostings(query.queryterm.get(0).term);
				for(int i=1; i<query.queryterm.size(); i++) {
		    		list = index.getPostings(query.queryterm.get(i).term);
					if(answer == null || list == null) return null;
					answer = intersect(answer, list, queryType);
				}
			}
		}
		
		return answer;
    }
    
    private PostingsList intersect(PostingsList p1, PostingsList p2, QueryType queryType) {
    	PostingsList result = new PostingsList();
    	PostingsEntry entry1, entry2;
    	int offset1, offset2;
    	int[] pointer = {0,0};
    	int[] offsetptr;
    	
    	while(true) {
    		entry1 = p1.get(pointer[0]);
    		entry2 = p2.get(pointer[1]);
    		if(entry1.docID == entry2.docID) {
    			
    			if(queryType == QueryType.INTERSECTION_QUERY) {
    				
    				result.addEntry(entry1.docID);
	    			
    			} else if(queryType == QueryType.PHRASE_QUERY) {
    				
    				offsetptr = new int[2];
	    			while(true) {
	    				offset1 = entry1.offset.get(offsetptr[0]);
	    				offset2 = entry2.offset.get(offsetptr[1]);
	    				if(offset1 == offset2 - 1) {
	    					result.addEntry(entry2.docID, offset2);
	    					offsetptr[0]++;
	    					offsetptr[1]++;
	    				} else {
	    					if(offset1 < offset2) offsetptr[0]++;
	    					else offsetptr[1]++;
	    				}
	    				
	    				if(offsetptr[0] >= entry1.offset.size() || offsetptr[1] >= entry2.offset.size()) break;
	    			}
    				
    			}
    			pointer[0]++;
    			pointer[1]++;
    			
    		} else {
    			if(entry1.docID < entry2.docID) pointer[0]++;
    			else pointer[1]++;
    		}
    		
    		if(pointer[0] >= p1.size() || pointer[1] >= p2.size()) break;
    	}
    	return result.size() > 0 ? result : null;
    }

    private Query addMatchingTerms(Query query, String term) {
    	String kgram, queryterm;
    	
    	term = "$" + term + "$";
    	ArrayList<KGramPostingsEntry> postings = null;  
    	for (int i = 0; i < term.length() - K + 1; i++) {	
    		kgram = term.substring(i, i + K);
  
    		if (!kgram.contains("*")) {
	    		if (postings == null) 
	                postings = (ArrayList<KGramPostingsEntry>) kgramindex.getPostings(kgram);
	            else 
	                postings = (ArrayList<KGramPostingsEntry>) kgramindex.intersect(postings, kgramindex.getPostings(kgram)); 	
    		}
    	}
    	
    	if (postings != null) {
    		String regex;
    		String[] substrings;
	    	for (KGramPostingsEntry p:postings) {
	    		queryterm = kgramindex.getTermByID(p.tokenID);
	    		
	    		// build regex
	    		substrings = term.substring(1, term.length() - 1).split("\\*");
	    		regex = "";
	    		for (int i = 0; i < substrings.length - 1; i++) {
	    			regex += substrings[i] + ".*";
	    		}
	    		regex += substrings[substrings.length - 1];
	    		if (term.charAt(term.length() - 2) == '*')
	    			regex += ".*";
	    		
	    		if (queryterm.matches(regex))
	    			query.addQueryTerm(queryterm, 1);
	    	}
    	}
    	
    	return query;
    }
    
    private HashMap<Integer, PostingsEntry> intersectWildcard(int l, int wc, Query query, Query partialQuery, QueryType queryType, ArrayList<Query> wildcardQuery, HashMap<Integer, PostingsEntry> result) {
    	
    	if (query.queryterm.size() == l) {
    		PostingsList list;
    		PostingsList partialAnswer = index.getPostings(partialQuery.queryterm.get(0).term);
			for(int i=1; i<partialQuery.queryterm.size(); i++) {
	    		list = index.getPostings(partialQuery.queryterm.get(i).term);
				if(partialAnswer == null || list == null) break;
				partialAnswer = intersect(partialAnswer, list, queryType);
			}
			
			if (partialAnswer != null) {
				for(PostingsEntry e:partialAnswer.list)
					result.put(e.docID, e);
			}
			
			return result;
    	}
    	
    	QueryTerm qt = query.queryterm.get(l);
    	if (qt.term.contains("*")) {
    		Query copy = partialQuery.copy();
    		for (int t = 0; t < wildcardQuery.get(wc).queryterm.size(); t++) {
    			partialQuery.addQueryTerm(wildcardQuery.get(wc).queryterm.get(t).term, 1);
    			result = intersectWildcard(l+1, wc+1, query, partialQuery, queryType, wildcardQuery, result);	
    			partialQuery = copy.copy();
			}
    		return result;
    	}
    	else {
    		partialQuery.addQueryTerm(qt.term, 1);
    		return intersectWildcard(l+1, wc, query, partialQuery, queryType, wildcardQuery, result);
    	}
    }
}
/*
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 *
 *   Dmytro Kalpakchi, 2018
 */

package ir;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;


public class SpellChecker {
    /** The regular inverted index to be used by the spell checker */
    Index index;

    /** K-gram index to be used by the spell checker */
    KGramIndex kgIndex;

    HashMap<Integer, Integer> tokenSet;
    
    /** The auxiliary class for containing the value of your ranking function for a token */
    class KGramStat implements Comparable {
        double score;
        String token;

        KGramStat(String token, double score) {
            this.token = token;
            this.score = score;
        }

        public String getToken() {
            return token;
        }

	public int compareTo(Object other) {
            if (this.score == ((KGramStat)other).score) return 0;
            return this.score < ((KGramStat)other).score ? -1 : 1;
        }

        public String toString() {
            return token + ";" + score;
        }
    }

    /**
     * The threshold for Jaccard coefficient; a candidate spelling
     * correction should pass the threshold in order to be accepted
     */
    private static final double JACCARD_THRESHOLD = 0.4;


    /**
      * The threshold for edit distance for a candidate spelling
      * correction to be accepted.
      */
    private static final int MAX_EDIT_DISTANCE = 2;


    public SpellChecker(Index index, KGramIndex kgIndex) {
        this.index = index;
        this.kgIndex = kgIndex;
    }

    /**
     *  Computes the Jaccard coefficient for two sets A and B, where the size of set A is 
     *  <code>szA</code>, the size of set B is <code>szB</code> and the intersection 
     *  of the two sets contains <code>intersection</code> elements.
     */
    private double jaccard(int szA, int szB, int intersection) {
        return (double) intersection / (szA + szB - intersection);
    }

    /**
     * Computing Levenshtein edit distance using dynamic programming.
     * Allowed operations are:
     *      => insert (cost 1)
     *      => delete (cost 1)
     *      => substitute (cost 2)
     */
    private int editDistance(String s1, String s2) {
    	int m[][] = new int[s1.length()+1][s2.length()+1];
        
        for(int i = 0; i < s1.length()+1; i++) {
            m[i][0] = i;
        }
       
        for(int j = 0; j < s2.length()+1; j++) {
            m[0][j] = j;
        }
       
        for(int i = 1; i < s1.length()+1; i++) {
            for(int j = 1; j < s2.length()+1; j++) {
               
                int v = m[i-1][j-1];
                if(s1.charAt(i-1) != s2.charAt(j-1)) {
                    v += 2;
                    if(v > (m[i-1][j]+1))
                        v = (m[i-1][j]+1);
                    if(v > (m[i][j-1]+1))
                        v = (m[i][j-1]+1);
                    m[i][j] = v;
                } else {
                    m[i][j] = v;
                }
               
            }
        }
 
        return m[s1.length()][s2.length()];
    }

    /**
     *  Checks spelling of all terms in <code>query</code> and returns up to
     *  <code>limit</code> ranked suggestions for spelling correction.
     */
    @SuppressWarnings("unchecked")
	public String[] check(Query query, int limit) {    	
    	ArrayList<KGramPostingsEntry> postings;
    	ArrayList<KGramStat> filteredToken;
    	ArrayList<ArrayList<KGramStat>> qCorrections = new ArrayList<ArrayList<KGramStat>>();
    	int K = kgIndex.K;
    	String kgram, token;
    	String[] result = null;
    	int frequency;
    	
    	for (Query.QueryTerm qt:query.queryterm) {
    		tokenSet = new HashMap<>();
    		filteredToken = new ArrayList<>();
    		
    		if (index.getPostings(qt.term) == null) {
		    	token = "$" + qt.term + "$";
		    	// generate kgrams from token and store kgindex postings in a table with frequency 
		    	for (int i = 0; i < token.length() - K + 1; i++) {
		    		kgram = token.substring(i, i + K);
		    		
		    		postings = (ArrayList<KGramPostingsEntry>) kgIndex.getPostings(kgram);
		    		
		    		if (postings != null) {
			    		for (KGramPostingsEntry p:postings) {
			    			frequency = 1;
			    			if (tokenSet.containsKey(p.tokenID)) {
			    				frequency += tokenSet.get(p.tokenID);
			    			}
			    			tokenSet.put(p.tokenID, frequency);
			    		}
		    		}
		    	}
		    	
		    	double jaccardScore;
		    	int szB, szA, editDistance;
		    	// calculate scores for each element of the table and filter
		    	for (Map.Entry<Integer,Integer> entry : tokenSet.entrySet()) {
		    		szA = token.length() - 1;
		    		szB = kgIndex.id2term.get(entry.getKey()).length() + 1;
		    		jaccardScore = jaccard(szA, szB, entry.getValue());
		    		editDistance = editDistance(qt.term, kgIndex.id2term.get(entry.getKey()));
		    		
		    		if (jaccardScore >= JACCARD_THRESHOLD && editDistance <= MAX_EDIT_DISTANCE) {
		    			filteredToken.add(new KGramStat(kgIndex.id2term.get(entry.getKey()), editDistance - jaccardScore));
		    		}
		    	}
    		}
    		
    		else {
    			filteredToken.add(new KGramStat(qt.term, 0));
    		}
    		
    		qCorrections.add(filteredToken);

    	}
    	
    	filteredToken = (ArrayList<KGramStat>) mergeCorrections(qCorrections, limit);
    	Collections.sort(filteredToken);
    	result = new String[limit];
    	for (int i=0; i<limit; i++) {
    		if (i < filteredToken.size())
    			result[i] = filteredToken.get(i).token;
    		else
    			result[i] = "";
    	}

        return result;
    }

    /**
     *  Merging ranked candidate spelling corrections for all query terms available in
     *  <code>qCorrections</code> into one final merging of query phrases. Returns up
     *  to <code>limit</code> corrected phrases.
     */
    @SuppressWarnings("unchecked")
	private List<KGramStat> mergeCorrections(ArrayList<ArrayList<KGramStat>> qCorrections, int limit) {
    	ArrayList<ArrayList<KGramStat>> combination = buildQuery(qCorrections, new ArrayList<KGramStat>(), new ArrayList<ArrayList<KGramStat>>(), 0);
    	
    	Comparator<ArrayList<KGramStat>> cmp = new Comparator<ArrayList<KGramStat>>() {
			@Override
			public int compare(ArrayList<KGramStat> o1, ArrayList<KGramStat> o2) {
				Double score1 = 0., score2 = 0.;
				
				for(int i = 0; i < o1.size() || i < o2.size(); i++) {
					if (i < o1.size())
						score1 += o1.get(i).score;
					if (i < o2.size())
						score2 += o2.get(i).score;
				}
				
				return score1.compareTo(score2);
			}
    	};
    	Collections.sort(combination, cmp);
    	
    	ArrayList<KGramStat> result = new ArrayList<>();
    	
    	int idx = 0;
		StringBuffer query;
    	while (result.size() < limit) {
    		
    		if (idx >= combination.size())
    			break;
    		
			query = new StringBuffer();
			
			for(KGramStat k:combination.get(idx)) 
				query.append(k.token + " ");
			
			// intersection
			StringTokenizer st = new StringTokenizer(query.toString());
			PostingsList list;
			PostingsList answer = index.getPostings(st.nextToken());
			while (st.hasMoreTokens()) {
	    		list = index.getPostings(st.nextToken());
				if(answer == null || list == null) break;
				answer = intersect(answer, list);
			}
			
			// put number of retrieved documents as score
			if (answer != null) 
				result.add(new KGramStat(query.toString(), -answer.size()));
			
			idx++;
    	}
    	
    	Collections.sort(result);
    	
        return(result);
    }
    
    public ArrayList<ArrayList<KGramStat>> buildQuery(ArrayList<ArrayList<KGramStat>> list, ArrayList<KGramStat> partial, ArrayList<ArrayList<KGramStat>> result, int index) {
    	 
        if(index == list.size()) {
            result.add(new ArrayList<KGramStat>());
            for(KGramStat entry : partial) {
                result.get(result.size()-1).add(new KGramStat(entry.token, entry.score));
            }
            return result;
        }
 
        for(KGramStat entry : list.get(index)) {
            partial.add(entry);
            result = buildQuery(list, partial, result, index+1);
            partial.remove(partial.size()-1);
        }
 
        return result;
 
    }
    
    private PostingsList intersect(PostingsList p1, PostingsList p2) {
    	PostingsList result = new PostingsList();
    	PostingsEntry entry1, entry2;
    	int[] pointer = {0,0};
    	
    	while(true) {
    		entry1 = p1.get(pointer[0]);
    		entry2 = p2.get(pointer[1]);
    		if(entry1.docID == entry2.docID) {
    			result.addEntry(entry1.docID);
	    			
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
}

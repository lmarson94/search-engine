/*  
 *   This file is part of the computer assignment for the
 *   Information Retrieval course at KTH.
 * 
 *   Johan Boye, 2017
 */

package ir;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 *  This is the main class for the search engine.
 */
public class Engine {
    
    /** The inverted index. */
    Index index = new HashedIndex();
//    Index index = new PersistentHashedIndex();
//    Index index = new PersistentScalableHashedIndex();

    /**  The indexer creating the search index. */
    Indexer indexer;
    
    /** Number of symbols to form a K-gram */
    int K = 2;
    
    KGramIndex kgramindex = new KGramIndex(K);

    /** The searcher used to search the index. */
    Searcher searcher;

    /** The engine GUI. */
    SearchGUI gui;
    
    SpellChecker speller;

    /**  Directories that should be indexed. */
    ArrayList<String> dirNames = new ArrayList<String>();

    /**  Lock to prevent simultaneous access to the index. */
    Object indexLock = new Object();

    /** The patterns matching non-standard words (e-mail addresses, etc.) */
    String patterns_file = null;
		
    /** The file containing the logo. */
    String pic_file = "";
		
    /** The file containing the pageranks. */
    String rank_file = "";

    /** For persistent indexes, we might not need to do any indexing. */
    boolean is_indexing = true;


    /* ----------------------------------------------- */


    /**  
     *   Constructor. 
     *   Indexes all chosen directories and files
     */
    public Engine( String[] args ) {
	decodeArgs( args );
	indexer = new Indexer( index, patterns_file, kgramindex);
	speller = new SpellChecker(index, kgramindex);
	searcher = new Searcher( index, kgramindex );
	searcher.pageRank = readPageRank("/home/luca/Desktop/Uni/Search Engines and Information Retrieval Systems/skeleton/davisPageRank.txt");
	gui = new SearchGUI( this );
	gui.init();
	/* 
	 *   Calls the indexer to index the chosen directory structure.
	 *   Access to the index is synchronized since we don't want to 
	 *   search at the same time we're indexing new files (this might 
	 *   corrupt the index).
	 */
	if (is_indexing) {
            synchronized ( indexLock ) {
                gui.displayInfoText( "Indexing, please wait..." );
                long startTime = System.currentTimeMillis();
                for ( int i=0; i<dirNames.size(); i++ ) {
                    File dokDir = new File( dirNames.get( i ));
                    indexer.processFiles( dokDir );
                }
                long elapsedTime = System.currentTimeMillis() - startTime;
                
                gui.displayInfoText( String.format( "Indexing done in %.1f seconds.", elapsedTime/1000.0 ));
                index.cleanup();
            }
        } else {
            gui.displayInfoText( "Index is loaded from disk" );
        }
    }


    /* ----------------------------------------------- */

    /**
     *   Decodes the command line arguments.
     */
    private void decodeArgs( String[] args ) {
	int i=0, j=0;
	while ( i < args.length ) {
	    if ( "-d".equals( args[i] )) {
		i++;
		if ( i < args.length ) {
		    dirNames.add( args[i++] );
		}
	    }
	    else if ( "-p".equals( args[i] )) {
		i++;
		if ( i < args.length ) {
		    patterns_file = args[i++];
		}
	    }
	    else if ( "-l".equals( args[i] )) {
		i++;
		if ( i < args.length ) {
		    pic_file = args[i++];
		}
	    }
	    else if ( "-r".equals( args[i] )) {
		i++;
		if ( i < args.length ) {
		    rank_file = args[i++];
		}
	    }	
	    else if ( "-ni".equals( args[i] )) {
                i++;
                is_indexing = false;
            }
	    else {
		System.err.println( "Unknown option: " + args[i] );
		break;
	    }
	}				    
    }
    
    /* ----------------------------------------------- */

    HashMap<String, Double> readPageRank(String file) {
        HashMap<String, Double> pageRank = new HashMap<>();
        
	    try {
	    	BufferedReader in = new BufferedReader( new FileReader( file ));
		    String line, fName;
		    double score;
		    int idx;
		    
			while ((line = in.readLine()) != null) {
				idx = line.indexOf( ";" );
				fName = line.substring( 0, idx );
				score = Double.valueOf(line.substring(idx + 1, line.length()));
				
				pageRank.put(fName, score);
			}
			
			in.close();
			
		} catch (IOException e) {
			e.printStackTrace();
		}
        
    	return pageRank;
    }
    
    /* ----------------------------------------------- */


    public static void main( String[] args ) {
    	Engine e = new Engine( args );
    }

}


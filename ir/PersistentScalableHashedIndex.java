package ir;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.StringTokenizer;

public class PersistentScalableHashedIndex extends PersistentHashedIndex implements Runnable {
	
	//public final static long BLOCKSIZE = 250000;
	public final static long BLOCKSIZE = 70000;
	
	private int counter = 0;
	
	private int threadCounter;
	
	private boolean merge = false;
	
	private ArrayList<Thread> t = new ArrayList<>();
	
	private RandomAccessFile threadDictionaryFile, threadDataFile, mergedDictionaryFile, mergedDataFile;
	
	private int docLength;
	
	private String docName;
    
    // ===================================================================
    
    /**
     *  Constructor. Opens the dictionary file and the data file.
     *  If these files don't exist, they will be created. 
     */
    public PersistentScalableHashedIndex() {
        super();
    }
    
    public PersistentScalableHashedIndex(int c) {
    	super(c);
    	
    	this.threadCounter = c;
    	
		try {
			dictionaryFile = new RandomAccessFile( INDEXDIR + "/" + DICTIONARY_FNAME, "r" );
			dataFile = new RandomAccessFile( INDEXDIR + "/" + DATA_FNAME, "r" );
			threadDictionaryFile = new RandomAccessFile( INDEXDIR + "/" + DICTIONARY_FNAME + "_temp" + this.threadCounter, "r" );
			threadDataFile = new RandomAccessFile( INDEXDIR + "/" + DATA_FNAME + "_temp" + this.threadCounter, "r" );
			mergedDictionaryFile = new RandomAccessFile( INDEXDIR + "/" + DICTIONARY_FNAME + "_support" + this.threadCounter, "rw" );
			mergedDataFile = new RandomAccessFile( INDEXDIR + "/" + DATA_FNAME + "_support" + this.threadCounter, "rw" );
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
    }

    // ==================================================================
    
    /**
     *  Inserts this token in the main-memory hashtable.
     */
    public void insert( String token, int docID, int offset ) {
    	
    	if(index.size() == BLOCKSIZE) {
    		counter++;
    		 
    		docLength = -1;
    		docName = docNames.get(docID);
    		docNames.remove(docID);
    		if(docLengths.get(docID) != null) {
    			docLength = docLengths.get(docID);
    			docLengths.remove(docID);
    		}
    		
    		// Write the 'docNames' and 'docLengths' hash maps to a file
            try {
            	if(merge) 
            		writeDocInfo(counter);
            	else 
            		writeDocInfo(-1);
			} catch (IOException e1) {
				e1.printStackTrace();
			}
    		
    		// Write block on disk
    		writeIndex(true);
    		
    		// Clear hash tables in main memory
    		index.clear();    		
    		docNames.clear();
    	    docLengths.clear();
    	    
    	    docNames.put(docID, docName);
    	    if(docLength > 0)
    	    	docLengths.put(docID, docLength);
    	    
    	    // Set pointer to 0
    	    free = 0L;
    	    
    	    // Create new files
    	    try {
    	    	dictionaryFile.close();
    	    	dataFile.close();
				dictionaryFile = new RandomAccessFile( INDEXDIR + "/" + DICTIONARY_FNAME + "_temp" + counter, "rw" );
				dataFile = new RandomAccessFile( INDEXDIR + "/" + DATA_FNAME + "_temp" + counter, "rw" );
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	   
    	    if(merge) {
    	    	t.add(new Thread(new PersistentScalableHashedIndex(counter-1)));
    	    	t.get(t.size()-1).start();
    	    	try {
    	    		t.get(t.size()-1).join();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
    	    	
    	    } else {
    	    	merge = true;
    	    }
    	}
    	
    	
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
    	
    	counter++;
    	
    	// Write the 'docNames' and 'docLengths' hash maps to a file
        try {
			writeDocInfo(counter);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
    	
		// Write block on disk
		writeIndex(true);
		
		// Clear hash tables in main memory
		index.clear();
		docNames.clear();
	    docLengths.clear();
	    
	    // Set pointer to 0
	    free = 0L;
	    
	    // Create new files
	    try {
	    	dictionaryFile.close();
	    	dataFile.close();
			dictionaryFile = new RandomAccessFile( INDEXDIR + "/" + DICTIONARY_FNAME, "rw" );
			dataFile = new RandomAccessFile( INDEXDIR + "/" + DATA_FNAME, "rw" );
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	    	
    	t.add(new Thread(new PersistentScalableHashedIndex(counter-1)));
    	t.get(t.size()-1).start();
    	try {
    		t.get(t.size()-1).join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	    	
    	for(Thread th:t) {
    		try {
				th.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
    	}
    	
    	counter = 0;
    	try {
    		dictionaryFile = new RandomAccessFile( INDEXDIR + "/" + DICTIONARY_FNAME, "r" );
            dataFile = new RandomAccessFile( INDEXDIR + "/" + DATA_FNAME, "r" );
			for(long ptr=0; ptr<dictionaryFile.length(); ptr += ENTRY_BYTE_SIZE) {
								
				Entry entry1 = readEntry(dictionaryFile, dataFile, ptr);
				if(entry1!=null) {
					counter++;
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
    	try {
			readDocInfo();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
    	System.err.println("done " + counter);
     }

    /*
     * Thread method 
     */
	@Override
	public void run() {
		long secondptr;
		int i, len;
		String pl1, pl2, tmp;
		long dataptr = 0L, dicptr;
		Entry dicEntry;
		StringTokenizer st;
		
		try {
			System.err.println("reading " + dictionaryFile.length() + " " + INDEXDIR + "/" + DICTIONARY_FNAME);
			System.err.println("reading " + threadDictionaryFile.length() + " " + INDEXDIR + "/" + DICTIONARY_FNAME + "_temp" + this.threadCounter);
			// read first dictionary file
			for(long ptr=0; ptr<dictionaryFile.length(); ptr += ENTRY_BYTE_SIZE) {
	            Entry entry1 = readEntry(dictionaryFile, dataFile, ptr);
	            Entry entry2 = null;
	            if(entry1 != null) {
	            	// Read postings list
	            	pl1 = readData(dataFile, entry1.ptr, entry1.size);
	            	
		            // Read from second dictionary
	            	i=0;
		            secondptr = hash(entry1.token, 0, TABLESIZE);
		            entry2 = readEntry(threadDictionaryFile, threadDataFile, secondptr);
	            	if(entry2 != null) {
		            	while(!entry2.token.equals(entry1.token)) {
		            		secondptr = hash(entry1.token, ++i, TABLESIZE);
		            		entry2 = readEntry(threadDictionaryFile, threadDataFile, secondptr);
		            		if(entry2 == null) break;
		            	}
		            }
	            	// Term present also in second dictionary
	            	if(entry2 != null) {
	            		//System.err.println(entry1.token + " " + entry2.token);
	            		pl2 = readData(threadDataFile, entry2.ptr, entry2.size);
	            		st = new StringTokenizer(pl2);
	            		tmp = st.nextToken();
	            		pl2 = ":" + pl2.substring(tmp.length()+1);
	            		pl1 += pl2;
	            	}
	            	
	            	// Write postings list in new data file
	            	len = writeData(mergedDataFile, pl1, dataptr);
	            
	            	// Write in new dictionary
	            	dicEntry = new Entry(entry1.token, dataptr, len);
	            	dicptr = hash(dicEntry.token, 0, TABLESIZE);

	            	i=0;
	            	while(readEntry(mergedDictionaryFile, mergedDataFile, dicptr) != null) {
	            		dicptr = hash(dicEntry.token, ++i, TABLESIZE);
	            	}
	            	writeEntry(mergedDictionaryFile, dicEntry, dicptr);
	            	
	            	dataptr += len;
	            }

	            
			}	
			
			// read second dictionary file
			for(long ptr=0; ptr<threadDictionaryFile.length(); ptr += ENTRY_BYTE_SIZE) {
				Entry entry2 = readEntry(threadDictionaryFile, threadDataFile, ptr);
				Entry entry1 = null;
				if(entry2 != null) {
					// Read from first dictionary
	            	i=0;
		            secondptr = hash(entry2.token, 0, TABLESIZE);
	            	entry1 = readEntry(dictionaryFile, dataFile, secondptr);
	            	if(entry1 != null) {
		            	while(!entry1.token.equals(entry2.token)) {
		            		secondptr = hash(entry2.token, ++i, TABLESIZE);
		            		entry1 = readEntry(dictionaryFile, dataFile, secondptr);
		            		if(entry1 == null) break;
		            	}
		            }
	            	
	            	// Term not present in first dictionary
	            	if(entry1 == null) {
	            		// Write postings list in new data file
		            	len = writeData(mergedDataFile, readData(threadDataFile, entry2.ptr, entry2.size), dataptr);
		            
		            	// Write in new dictionary
		            	dicEntry = new Entry(entry2.token, dataptr, len);
		            	dicptr = hash(dicEntry.token, 0, TABLESIZE);

		            	i=0;
		            	while(readEntry(mergedDictionaryFile, mergedDataFile, dicptr) != null) {
		            		dicptr = hash(dicEntry.token, ++i, TABLESIZE);
		            	}
		            	writeEntry(mergedDictionaryFile, dicEntry, dicptr);
		            	            	
		            	
		            	dataptr += len;
	            	}
				}
			}
			
			// delete temporary files and merge
			dictionaryFile.close();
			dataFile.close();
			threadDictionaryFile.close();
			threadDataFile.close();
			
			Files.deleteIfExists(Paths.get(INDEXDIR + "/" + DICTIONARY_FNAME));
			Files.deleteIfExists(Paths.get(INDEXDIR + "/" + DATA_FNAME));
			Files.deleteIfExists(Paths.get(INDEXDIR + "/" + DICTIONARY_FNAME + "_temp" + this.threadCounter));
			Files.deleteIfExists(Paths.get(INDEXDIR + "/" + DATA_FNAME + "_temp" + this.threadCounter));
			Files.move(Paths.get(INDEXDIR + "/" + DICTIONARY_FNAME + "_support" + this.threadCounter), Paths.get(INDEXDIR + "/" + DICTIONARY_FNAME));
			Files.move(Paths.get(INDEXDIR + "/" + DATA_FNAME + "_support" + this.threadCounter), Paths.get(INDEXDIR + "/" + DATA_FNAME));
			
			mergedDictionaryFile.close();
			mergedDataFile.close();
			
			// merge docInfo files
			File out = new File( INDEXDIR + "/" + DOCINFO_FNAME + "_merged");
			File oldDoc = new File( INDEXDIR + "/" + DOCINFO_FNAME);
			File newDoc = new File( INDEXDIR + "/" + DOCINFO_FNAME + (this.threadCounter+1));
			mergeFiles(out, oldDoc, newDoc);
			Files.deleteIfExists(Paths.get(INDEXDIR + "/" + DOCINFO_FNAME));
			Files.deleteIfExists(Paths.get(INDEXDIR + "/" + DOCINFO_FNAME + (this.threadCounter+1)));
			Files.move(Paths.get(INDEXDIR + "/" + DOCINFO_FNAME + "_merged"), Paths.get(INDEXDIR + "/" + DOCINFO_FNAME));
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/*
	 * Copy source into destination 
     */
    private static final void transfer(final Reader source, final Writer destination) throws IOException {
        char[] buffer = new char[1024 * 16];
        int len = 0;
        while ((len = source.read(buffer)) >= 0) {
            destination.write(buffer, 0, len);
        }
    }

    /*
     * Merge inputfile1 and inputfile2 and copy in output file
     */
    public void mergeFiles(final File output, final File inputfile1, final File inputfile2) throws IOException{

        try (
            Reader sourceA = Files.newBufferedReader(inputfile1.toPath());
            Reader sourceB = Files.newBufferedReader(inputfile2.toPath());
            Writer destination = Files.newBufferedWriter(output.toPath(), StandardCharsets.UTF_8); ) {

            transfer(sourceA, destination);
            transfer(sourceB, destination);

        }
    }

}

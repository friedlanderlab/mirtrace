/*******************************************************************************
    This file is part of miRTrace.

    COPYRIGHT: Marc Friedländer <marc.friedlander@scilifelab.se>, 2018
    AUTHOR: Yrin Eldfjell <yete@kth.se>

    miRTrace is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, version 3 of the License.

    miRTrace is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program, see the LICENSES file.
    If not, see <https://www.gnu.org/licenses/>.
*******************************************************************************/
package se.scilifelab.mirtrace;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPInputStream;

class DNASequenceDatabase {

	final static int KMER_LEN = 9; /* Must match kmer len at database creation time. */ 
	final static int KMER_NON_EXISTENT = -1;
	final static byte ENDCHAR_BYTE = (byte) '$';
	final static int NO_MATCH = -1;
	
	byte[] refSeq;
	int[] posArray;
	/* 'kmerLookup' data structure:
	 * 
	 * Index:		binary kmer
	 * Value:		posArray offset. 
	 */
	int[] kmerLookup;
	int refSeqCount = 0; /* Number of reference sequences. */
	List<Integer> refSeqEndcharLocations;
	List<String> refSeqIds;
	DataInputStream dis;
	
	static class SearchResult {
		boolean matches = false;
		int refSeqId = NO_MATCH;
		int querySeqMatchStart = 0; /* 0-indexed (inclusive). */
		int querySeqMatchEnd = 0; /* 0-indexed (inclusive). */
		
		SearchResult(boolean matches, int refSeqId, int querySeqMatchStart, int querySeqMatchEnd) {
			this.matches = matches;
			this.refSeqId = refSeqId;
			this.querySeqMatchStart = querySeqMatchStart;
			this.querySeqMatchEnd = querySeqMatchEnd; 
		}
	}
	
	static final SearchResult NO_MATCH_RESULT = new SearchResult(false, NO_MATCH, -1, -1);
	
	public DNASequenceDatabase(String dbFilename) {
		openDBFile(dbFilename);
		parseDBFile();
	}
	
	public DNASequenceDatabase(InputStream is) {
		openDBFile(is);
		parseDBFile();
	}
	
	private void failParse(String msg) {
		throw new RuntimeException("Failed parsing RNA Type DB file: " + msg);
	}

	private void openDBFile(String dbFilename) {
		int GZIP_BUF_SIZE = 8192;
		try {
			dis = new DataInputStream(
					new GZIPInputStream(
						new FileInputStream(dbFilename), GZIP_BUF_SIZE));
			// FOR READING NON-ZIPPED FILES. This might still be relevant later
			// for user-supplied databases:
			// dis = new DataInputStream(new FileInputStream(dbFilename));
		}
		catch (FileNotFoundException e) {
			System.err.print("ERROR: Could not open file: " + dbFilename);
			closeDBFile();
			e.printStackTrace();
			failParse("DB file not found.");
		}
		catch (IOException e) {
			closeDBFile();
			e.printStackTrace();
			failParse("I/O error during parsing of DB file.");	
		}
	}

	private void openDBFile(InputStream is) {
		int GZIP_BUF_SIZE = 8192;
		try {
			dis = new DataInputStream(new GZIPInputStream(is, GZIP_BUF_SIZE));
		}
		catch (FileNotFoundException e) {
			System.err.print("ERROR: Could not open file.");
			closeDBFile();
			e.printStackTrace();
			failParse("DB file not found.");
		}
		catch (IOException e) {
			closeDBFile();
			e.printStackTrace();
			failParse("I/O error during parsing of DB file.");	
		}
	}
	
	private void closeDBFile() {
		if (dis != null) {
			try {
				dis.close();
			} catch (IOException e) {
				e.printStackTrace();
				failParse("Error closing DB file.");
			}
		}
	}
	
	private void parseDBFile() {
		int i;
		final int INT_SIZE = 4;

		try {
			/* Read and parse header. */
			int size_row_1_ref_seq = dis.readInt();
			int size_row_2_ref_seq_ids = dis.readInt();
	        int size_row_3_kmer_list = dis.readInt();
	        int size_row_4_pos_list = dis.readInt();
	        
	        if (size_row_3_kmer_list % 4 != 0) {
	        	throw new IllegalArgumentException("invalid size_row_3_kmer_list");
	        }
	        if (size_row_4_pos_list % 4 != 0) {
	        	throw new IllegalArgumentException("invalid size_row_4_pos_list");
	        }
	
	        byte[] rawRefSeq = new byte[size_row_1_ref_seq];
	        byte[] rawRefSeqIds = new byte[size_row_2_ref_seq_ids];
	        int[] rawKmerList = new int[size_row_3_kmer_list / INT_SIZE];
	        int[] rawPosList = new int[size_row_4_pos_list / INT_SIZE];

	        /* Read, parse and count ref seqs. */
	        dis.readFully(rawRefSeq);
	        for (byte b : rawRefSeq) {
	        	/* Each reference seq is terminated by '$'. */
	        	if (b == '$') {
	        		refSeqCount++;
	        	}
	        }
	        
	        /* Store ref seq terminator char locations. */
	        refSeqEndcharLocations = new ArrayList<Integer>(refSeqCount);
	        for (int ri=0; ri < rawRefSeq.length; ri++) {
	        	if (rawRefSeq[ri] == '$') {
	        		refSeqEndcharLocations.add(ri);
	        	}
	        }
	        
	        /* Store ref seq ids. */
	        dis.readFully(rawRefSeqIds);
	        refSeqIds = new ArrayList<String>(refSeqCount);
	        int idStrStartPos = 0;
	        for (int ri=0; ri < rawRefSeqIds.length; ri++) {
	        	if (rawRefSeqIds[ri] == '\n') {
	        		refSeqIds.add(new String(Arrays.copyOfRange(rawRefSeqIds, idStrStartPos, ri)));
	        		idStrStartPos = ri + 1;
	        	}
	        }
	        /* Each seq id entry is terminated by '\n' so we don't need to 
	         * treat the last seq id as a special case. */
	        
	        if (refSeqIds.size() != refSeqEndcharLocations.size()) {
	        	throw new RuntimeException("INTERNAL ERROR: refSeqId array size inconsistency.");
	        }
	        
	        for (i = 0; i < size_row_3_kmer_list / INT_SIZE; i++) {
	        	rawKmerList[i] = dis.readInt();
	        }
	
	        for (i = 0; i < size_row_4_pos_list / INT_SIZE; i++) {
	        	rawPosList[i] = dis.readInt();
	        }
			int numKmers = 1 << (2 * KMER_LEN);
			this.refSeq = rawRefSeq;
			this.kmerLookup = new int[numKmers];
			for (i=0; i < kmerLookup.length; i++) {
				kmerLookup[i] = KMER_NON_EXISTENT;
			}
			
			/* 'rawKmerList' consists of interlaced (kmer, offset) pairs. 
			 * See separate documentation file. */
			for (i=0; i < rawKmerList.length; i += 2) {
				
				int kmer = rawKmerList[i];
				kmerLookup[kmer] = rawKmerList[i+1];
			}
			this.posArray = rawPosList;

		} catch (IOException e) {
			e.printStackTrace();
			failParse("I/O error during DB parsing.");
		}
		closeDBFile();
	}
	
	/* 'start' and 'stop' are Python-style indexes (zero-indexed and half-open). */
	int dnaToBinaryInt(byte[] seq, int start, int stop) {
		int result = 0;
		int lettermask;
		for (int i=start; i < stop; i++) {
			switch (seq[i]) {
	        	case 'A': lettermask = 0;
	        		break;
	        	case 'C': lettermask = 1;
	        		break;
	        	case 'G': lettermask = 2;
	        		break;
	        	case 'T': lettermask = 3;
	        		break;
	        	default:
	        		throw new RuntimeException(
	        				"Invalid letter in sequence: " + seq[i] + ". This is an error in miRTrace. :-("
	        		);
			}
			result = (result << 2) | lettermask;
		}
		return result;
	}
	
	/* Calculate which reference sequence a particular offset belongs to, 
	 * using the end-char location array.
	 * The sequences are enumerated by database occurrence.
     */
	int calculateRefSeqIndex(int refSeqOffset) {
		int refSeqId = Collections.binarySearch(refSeqEndcharLocations, refSeqOffset);
		if (refSeqId < 0) {
			refSeqId = -refSeqId - 1;
		}
		if (refSeqId < 0) {
			throw new IllegalArgumentException("Negative refSeqId.");
		}
		return refSeqId;
	}
	
	String getRefSeqId(int refSeqIndex) {
		return refSeqIds.get(refSeqIndex);
	}
	
	SearchResult slidingWindowSearch(byte[] querySeq, int querySeqLen) {
		return slidingWindowSearch(querySeq, querySeqLen, DNASequenceDatabase.NO_MATCH);
	}
	
	/* Attempt to match using a sliding window of kmers in the query seq. 
	 * Used for mapping artifacts. */
	SearchResult slidingWindowSearch(byte[] querySeq, int querySeqLen, int lastRefSeqIdMatched) {
		byte[] slidingSearchQueryWindow = new byte[Config.ARTIFACTS_MAPPER_SLIDING_WINDOW_SIZE];
		int lastKmerToConsider = querySeqLen - Config.ARTIFACTS_MAPPER_SLIDING_WINDOW_SIZE;
		for (int i=0; i < lastKmerToConsider + 1; i++) {
			for (int j=0; j < Config.ARTIFACTS_MAPPER_SLIDING_WINDOW_SIZE; j++) {
				slidingSearchQueryWindow[j]	= querySeq[i+j];			
			}
			SearchResult m = search(slidingSearchQueryWindow, Config.ARTIFACTS_MAPPER_SLIDING_WINDOW_SIZE, 0, false, false, lastRefSeqIdMatched);
			if (m.matches) {
				return m;
			}
		}		
		return NO_MATCH_RESULT;
	}

	/* See 'search' for a description of the arguments. */
	SearchResult search(byte[] querySeq, int querySeqLen, int maxMismatches, 
			boolean mustMatchWholeRefSeq, boolean mustMatchRefSeqStart) {
		return search(querySeq, querySeqLen, maxMismatches, mustMatchWholeRefSeq, mustMatchRefSeqStart,
				DNASequenceDatabase.NO_MATCH);
	}
	
	/* The 'querySeq' argument is an ASCII-encoded sequence string. 
	 * RETURNS
	 * 		'NO_MATCH' if no match, else a positive (>= 0) sequence index. */
	SearchResult search(byte[] querySeq, int querySeqLen, int maxMismatches, 
			boolean mustMatchWholeRefSeq, boolean mustMatchRefSeqStart, int lastRefSeqIdMatched) {
		// TODO ENHANCEMENT: decrease the nesting depth of this method by splitting it up.
		if (refSeq.length == 0) {
			return NO_MATCH_RESULT;
		}
		
		int posArrayOffset;
		int binaryQueryKmer;
		int failedKmerCount = 0;
		int lastKmerToConsider = querySeqLen - KMER_LEN;
		
		if (lastRefSeqIdMatched >= refSeqEndcharLocations.size()) {
			throw new IllegalArgumentException("INTERNAL ERROR: lastRefSeqIdMatched too large.");
		}
		
		int refSeqInitialPos;
		if (lastRefSeqIdMatched == DNASequenceDatabase.NO_MATCH) {
			refSeqInitialPos = 0;
		} else if (lastRefSeqIdMatched >= refSeqEndcharLocations.size() - 1) {
			return NO_MATCH_RESULT;		
		} else {
			refSeqInitialPos = refSeqEndcharLocations.get(lastRefSeqIdMatched) + 1;
		}
		if (refSeqInitialPos >= refSeq.length) {
			throw new IllegalArgumentException("INTERNAL ERROR: refSeqInitialPos too large.");
		}
		
		/* Optimization:
		 * We use the fact that at least one of the first KMER_LEN + 1 kmers 
		 * has to be correct if there is at most one mismatch in the query. 
	     */
		for (int i=0; i < lastKmerToConsider + 1; i += KMER_LEN) {
			binaryQueryKmer = dnaToBinaryInt(querySeq, i, i + KMER_LEN);
			posArrayOffset = kmerLookup[binaryQueryKmer]; 
			if (posArrayOffset == KMER_NON_EXISTENT) {
				failedKmerCount++;
				if (failedKmerCount > maxMismatches) {
					// We now have at least <failedKmerCount> mismatches. Abort search.
					return NO_MATCH_RESULT;
				}
			} else {
				// TODO: Consider breaking this block out into a separate function.
				
				/* Found candidate sequences. Perform alignment(s). */
				boolean lastPosArrayEntryFound = false;
				for (int j=posArrayOffset; !lastPosArrayEntryFound; j++) {
					int refSeqOffset = posArray[j];
					if (refSeqOffset < 0) {
						refSeqOffset = -refSeqOffset;
						lastPosArrayEntryFound = true;
					}
					if (refSeqOffset < refSeqInitialPos) {
						continue;
					}
					int mismatches = 0;

					/* Backtrack query pointer to start of query seq. */
					int queryIdx = 0;
					
					/* Backtrack ref pointer */
					int k;
					if (mustMatchWholeRefSeq || mustMatchRefSeqStart) {
						/* Backtrack ref pointer to refSeq start. */
						int refSeqId = calculateRefSeqIndex(refSeqOffset);
						if (refSeqId == 0) {
							k = 0;
						} else {
							k = refSeqEndcharLocations.get(refSeqId - 1);
							k++;
						}
					} else {
						/* Backtrack ref pointer to start of candidate alignment. */
						k = refSeqOffset - i;					
					}
										
					if (k >= 0) {
						// Here k is positive, so we have enough "room" at the start
						// of the ref seq to actually do the alignment. 
						// We still need to look out for sequence separators ("$").
						for (;; queryIdx++, k++) {
							if (queryIdx >= querySeqLen) {
								/* Query sequence has been consumed. */
								if (mustMatchWholeRefSeq) {
									if (k < refSeq.length) {
										if (refSeq[k] == ENDCHAR_BYTE) {
											int querySeqStart = 0; /* Must be 0 since we're in 'mustMatchWholeRefSeq' mode. */
											int querySeqEnd = querySeqLen - 1; /* Whole seq must have been consumed. */
											return new SearchResult(true, calculateRefSeqIndex(k), querySeqStart, querySeqEnd);
										}
									}
									break;
								} else {
									int querySeqStart = 0; /* Must be 0 since we don't allow indels. */ 
									int querySeqEnd = querySeqLen - 1; /* Whole seq must have been consumed. */
									/* Note that the above calculation will give misleading 
									 * results when mismatches are allowed. */
									return new SearchResult(true, calculateRefSeqIndex(k), querySeqStart, querySeqEnd);
								}
							}
							if (k >= refSeq.length) {
								throw new RuntimeException(
										"Invalid database: reference sequence lacks terminator character. ");
							}
							if (refSeq[k] == ENDCHAR_BYTE) {
								// We have now either 
								// (1) reached the end of the reference sequence
								//     without consuming the query, or
								// (2) realized that we've mistakenly tried to 
								//     align across a reference sequence separator.
								// Conclusion: no match.
								break;
							}
							if (refSeq[k] != querySeq[queryIdx]) {
								mismatches++;
								// DEBUG: FOR CONSISTENCY TEST WITH BOWTIE:
								/*if (refSeq[k] == 'N') {
									alignmentOK = false;
									break;
								}*/
							}
							if (mismatches > maxMismatches) {
								break;
							}
						}
					}
				}
			}
		}
		return NO_MATCH_RESULT;
	}

	int getRefSeqCount() {
		return refSeqCount;
	}
	
	String getSeqId(int index) {
		return refSeqIds.get(index);
	}

}

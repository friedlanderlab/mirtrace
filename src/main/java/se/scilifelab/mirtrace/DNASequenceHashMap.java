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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.NoSuchElementException;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;


/**
 * @author Yrin Eldfjell
 *
 */
public class DNASequenceHashMap {
		
	/* HashTable Flags */
	static final byte HT_BUCKET_OCCUPIED = 1;
	static final byte HT_ADAPTER_DETECTED = 2;
	static final int HT_LOOKUP_SLOT_EMPTY = -1;
	
	/* Constants */
	static final int INVERSE_SIGN_BIT_BITMASK = ~(1 << 31);
	static final int MAX_INT = Integer.MAX_VALUE;
	static final int DNA_BASES_PER_BUCKET_PART = 32;

	/* As allocating large contiguous blocks of memory can be problematic, 
	 * this hash map implementation uses a number of smaller blocks instead. 
	 */
	
	/* ARRAY_SHARD_BITCOUNT defines the number of shards. */

	/* The following two constants are set implicitly from ARRAY_SHARD_BITCOUNT. */
	static final int ARRAY_SHARD_COUNT = 1 << Config.HM_ARRAY_SHARD_BITCOUNT; // Must be a power of 2
	static final int ARRAY_SHARD_BITMASK = ARRAY_SHARD_COUNT - 1;
	static final int BUCKET_SIZE = (Config.READ_LENGTH_CUTOFF + (DNA_BASES_PER_BUCKET_PART - 1)) / 
			DNA_BASES_PER_BUCKET_PART; // round up
	
	
	/* Settings. */
	int capacityTotal;
	int lookupTableSize;
	int bucketsPerShard;
	
	/* Temp buffer. */
	long[] binaryConversionBuffer;
	
	/* Hash table lookup array. */
	int[] htLookupArray;
	
	/* Hash table entry data structures. */
	long[][] htSequences;
	byte[][] htSequenceLengths;
	int[][] htSequenceCounts;
	int[][] htSequenceFirstDetectionDepth;
	byte[][] htFlags;
	
	/* Iterator variables. */
	private int iteratorPos = 0;
	boolean readCountOrderedIndexDirty = true;
	ArrayList<Integer> readCountOrderedIndex;
	
	/* HashMap state. */
	int usedBuckets = 0;
	
	/* Warnings. */	
	boolean seqCountOverflowWarning = false;

	/**
	 * 
	 */
	public DNASequenceHashMap(int capacity) {
		if (capacity < 0) {
			throw new IllegalArgumentException("Invalid capacity value.");
		}
		this.bucketsPerShard = capacity / ARRAY_SHARD_COUNT;
		this.capacityTotal = bucketsPerShard * ARRAY_SHARD_COUNT;
		this.lookupTableSize = capacityTotal * Config.HM_LOOKUP_TABLE_EXPANSION_FACTOR;
		this.binaryConversionBuffer = new long[BUCKET_SIZE];
		
		/* Allocate memory */
		this.htSequences = new long[ARRAY_SHARD_COUNT][bucketsPerShard * BUCKET_SIZE];
		this.htSequenceLengths = new byte[ARRAY_SHARD_COUNT][bucketsPerShard];
		this.htSequenceCounts = new int[ARRAY_SHARD_COUNT][bucketsPerShard];
		this.htSequenceFirstDetectionDepth = new int[ARRAY_SHARD_COUNT][bucketsPerShard];
		this.htFlags = new byte[ARRAY_SHARD_COUNT][bucketsPerShard];
		this.htLookupArray = new int[lookupTableSize];
		for (int i=0; i < lookupTableSize; i++) {
			this.htLookupArray[i] = HT_LOOKUP_SLOT_EMPTY;
		}
	}
	
	/** Inserts sequence into hash table.
	 * @param seq Sequence in upper-case ASCII, stored in byte array.
	 * @param adapterDetected true if sequence has been pruned off the adapter sequence.
	 * @return true if the insertion completed successfully, false if table is full.
	 */
	public boolean putSequence(byte[] seq, int seqLen, boolean adapterDetected, int sampleDepth, int seqCount) {
		long lettermask;
		int bufferOffset = 0;
		int hash;
		int shard;
		int bucket;
		int startIndex;
		int currentLookupIndex;
		int i, j, s;
		boolean sequencesIdentical;
		
		HashFunction murmur3 = Hashing.murmur3_32();
		
		if (seqLen > Config.READ_LENGTH_CUTOFF) {
    		throw new IllegalArgumentException("Sequence exceeds maximum length. Seq len = " + 
    				seqLen);
		}
		if (seqLen < 0) {
			throw new IllegalArgumentException("seqLen = 0");
		}
		if (usedBuckets >= capacityTotal) {
			return false;
		}
		readCountOrderedIndexDirty = true;

		/* Parse sequence into binaryConversionBuffer */
		for (i = 0; i < BUCKET_SIZE; i++) {
			this.binaryConversionBuffer[i] = 0;
		}
	    for (i = 0; i < seqLen; i++) {
	    	if (i % DNA_BASES_PER_BUCKET_PART == 0) {
	    		bufferOffset = i / DNA_BASES_PER_BUCKET_PART;
	    	}
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
	        		throw new RuntimeException("Invalid letter in sequence: " + seq[i] + 
	        				" at position: " + i);
	        }
	        this.binaryConversionBuffer[bufferOffset] |= (lettermask << ((i % DNA_BASES_PER_BUCKET_PART) << 1));
	    }
	    hash = 0;
	    /* Calculate sequence hash */
	    for (j = 0; j <= bufferOffset; j++) {
	    	hash += (int) (this.binaryConversionBuffer[j] ^ 
	    			(this.binaryConversionBuffer[j] >>> DNA_BASES_PER_BUCKET_PART));
	    }
	    hash = murmur3.hashInt(hash).hashCode();
	    hash = hash & INVERSE_SIGN_BIT_BITMASK; // Convert hash < 0 to positive number
	    
	    /* Insert sequence into hash table. */
	    /* Start by establishing the shard:bucket address. */
	    startIndex = hash % lookupTableSize;
	    for (i = 0; i < lookupTableSize; i++) {
	    	currentLookupIndex = startIndex + i;
	    	if (currentLookupIndex >= lookupTableSize) {
	    		/* Search past end of table: wrap around. */
	    		currentLookupIndex -= lookupTableSize;
	    	}
	    	int currentBucketIndex = htLookupArray[currentLookupIndex];
	    	if (currentBucketIndex == HT_LOOKUP_SLOT_EMPTY) {
	    		/* New entry in hash table */ 
	    		htLookupArray[currentLookupIndex] = usedBuckets;
	    		shard = usedBuckets / bucketsPerShard;
		    	bucket = usedBuckets % bucketsPerShard;	
		    	this.htFlags[shard][bucket] = HT_BUCKET_OCCUPIED;
	    		if (adapterDetected) {
	    			this.htFlags[shard][bucket] |= HT_ADAPTER_DETECTED;
	    		}
	    		this.htSequenceCounts[shard][bucket] = seqCount;
	    		if (seqLen > Byte.MAX_VALUE) {
	    			seqLen = Byte.MAX_VALUE;
	    		}
	    		this.htSequenceLengths[shard][bucket] = (byte) seqLen;
	    		this.htSequenceFirstDetectionDepth[shard][bucket] = sampleDepth;
	    		s = (bucket * BUCKET_SIZE);
	    		for (i = 0; i < BUCKET_SIZE; i++) {
	    			this.htSequences[shard][s + i] = 
	    					binaryConversionBuffer[i];
	    		}
	    		this.usedBuckets++;
	    		return true;
	    	} else {
	    		/* Entry exists, test if its sequence is identical to the query. */
	    		shard = currentBucketIndex / bucketsPerShard;
		    	bucket = currentBucketIndex % bucketsPerShard;	
	    		sequencesIdentical = true;
	    		s = (bucket * BUCKET_SIZE);
	    		for (j = 0; j <= bufferOffset; j++) {
	    			if (binaryConversionBuffer[j] != htSequences[shard][s + j]) {
	    				sequencesIdentical = false;
	    				break;
	    			}
	    		}
	    		if (sequencesIdentical && (seqLen == htSequenceLengths[shard][bucket])) {
	    			/* Found existing entry in hash table */
	    			if (this.htSequenceCounts[shard][bucket] + seqCount < MAX_INT) {
	    				this.htSequenceCounts[shard][bucket] += seqCount;
	    			} else {
	    				this.htSequenceCounts[shard][bucket] = MAX_INT;
	    				seqCountOverflowWarning = true;
	    			}
		    		return true;
	    		}
	    	}
	    }
	    throw new IllegalStateException("Hashmap insert failed despire HM having empty slots.");
	}
	
	void initIterator() {
		this.readCountOrderedIndex = new ArrayList<Integer>(capacityTotal);
	    for (int i = 0; i < capacityTotal; i++) {
	    	readCountOrderedIndex.add(i);
	    }
	    Collections.sort(readCountOrderedIndex, new Comparator<Integer>() {

	    	private int getCount(int index) {
	    		/* Returns -1 for unused buckets. */
				int shard;
				int bucket;
			    shard = index / bucketsPerShard;
			    bucket = index % bucketsPerShard;
		    	if ((htFlags[shard][bucket] & HT_BUCKET_OCCUPIED) == HT_BUCKET_OCCUPIED) {
		    		return htSequenceCounts[shard][bucket];
		    	} else {
		    		return -1;
		    	}
	    	}
	    	
			@Override
			public int compare(Integer o1, Integer o2) {
				int c1 = getCount(o1);
				int c2 = getCount(o2);
				if (c1 < c2) {
					return 1;
				} else if (c1 > c2) {
					return -1;
				} else {
					return 0;
				}
			}	    	
	    });
		readCountOrderedIndexDirty = false;
	}

	int getCapacity() {
		return capacityTotal;
	}
		
	boolean iteratorHasNext() {
		if (readCountOrderedIndexDirty) {
    		throw new IllegalStateException("Call to 'iteratorHasNext()' while ordered index is dirty!");
    	}
    	if (iteratorPos < usedBuckets) {
			return true;
		}
		return false;
	}
	
	void iteratorFetchNext(DNASequenceHashMapEntry entry) {
		if (readCountOrderedIndexDirty) {
    		throw new IllegalStateException("Call to 'iteratorFetchNext()' while ordered index is dirty!");
    	}
    	int bufferOffset = 0;
    	int shard;
		int bucket;
        if (!iteratorHasNext()) {
        	throw new NoSuchElementException();
        }
    	shard = readCountOrderedIndex.get(iteratorPos) / bucketsPerShard;
    	bucket = readCountOrderedIndex.get(iteratorPos) % bucketsPerShard;
        
        /* Setup outputs. */
        byte seqLen = htSequenceLengths[shard][bucket];
        byte[] outputSequence = entry.seq;
	    entry.length = seqLen;
	    entry.count = htSequenceCounts[shard][bucket];
	    entry.adapterDetected = (htFlags[shard][bucket] & HT_ADAPTER_DETECTED) == HT_ADAPTER_DETECTED;
	    entry.firstDetectedDepth = htSequenceFirstDetectionDepth[shard][bucket];
	    
	    /* Generate output ASCII seq. */
        int pb = bucket * BUCKET_SIZE;
	    for (int i = 0; i < seqLen; i++) {
	    	if (i % DNA_BASES_PER_BUCKET_PART == 0) {
	    		bufferOffset = i / DNA_BASES_PER_BUCKET_PART;
	    	}
	    	int currentLetter = (int) (htSequences[shard][pb + bufferOffset] >>> 
					((i % DNA_BASES_PER_BUCKET_PART) << 1)) & 0x03;
	        switch (currentLetter) {
	        	case 0: outputSequence[i] = 'A';
	        		break;
	        	case 1: outputSequence[i] = 'C';
	        		break;
	        	case 2: outputSequence[i] = 'G';
	        		break;
	        	case 3: outputSequence[i] = 'T';
	        		break;
	        	default:
	        		throw new RuntimeException("Invalid letter in sequence: " + 
	        				currentLetter);
	        }
	    }
	    iteratorPos++;
	}
	        
	
	boolean getSeqCountOverflowWarning() {
		return seqCountOverflowWarning;
	}

	double getFillFactor() {
		return usedBuckets / (double) capacityTotal; 
	}

}

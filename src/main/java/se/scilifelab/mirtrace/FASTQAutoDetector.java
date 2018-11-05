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

class FASTQAutoDetector {
	
	class FASTQFormat {
		Integer phredOffset = null;
		String adapterSeq = null;
	}
	
	// Unused:
	class AdapterCandidate {
		Double posExpectedValue;
		Double posVariance;
		Double kmerFraction;
		Double zeroToOnePositionDecreaseFactor;
		Double zeroPosFraction;
		byte[] seq;
	}

	boolean detectPHREDOffset;
	boolean detectAdapter;
	Config config;
	//MatureHairpinDatabase matureHairpinDB;
	
	void fail(String msg) {
		System.err.println(msg);
		System.exit(-1);
	}	
	
	FASTQAutoDetector(boolean detectPHREDOffset, boolean detectAdapter, Config config) {
		this.detectPHREDOffset = detectPHREDOffset;
		this.detectAdapter = false;
		if (detectAdapter) {
			throw new IllegalArgumentException("INTERNAL ERROR: adapter detection not supported.");
		}
		this.config = config;
//		try {
//			this.matureHairpinDB = new MatureHairpinDatabase(config);
//		} catch (DBNotFoundException e) {
//			e.printStackTrace();
//			fail("INTERNAL ERROR: Could not load mirna db for adapter auto-detection.");
//		}
	}
	
	FASTQFormat autoDetectSampleFormat(String seqFilename) throws FASTQParseException, FASTQAutoDetectionException {
		FASTQFormat result = new FASTQFormat(); 
		FASTQParser parser = new FASTQParser(seqFilename, config);
		byte[] sequence = new byte[Config.READ_LENGTH_CUTOFF];
		byte[] phredScores = new byte[Config.READ_LENGTH_CUTOFF];
		int seqLen;
		int i;
		boolean couldBePhred33 = true;
		boolean couldBePhred64 = true;
		
		int seqNum;
		for (seqNum=1;;seqNum++) {
			
			/* Fetch new entry. */
			seqLen = parser.getNextEntry(sequence, phredScores);
			if (seqLen == FASTQParser.SEQ_FILE_EOF) {
				break;
			}
			if (seqLen > Config.READ_LENGTH_CUTOFF) {
				seqLen = Config.READ_LENGTH_CUTOFF;
			}
			
			/* Process PHRED values. */
			
			if (detectPHREDOffset) {
			
				//boolean containsInvalidNucleotides = false;
				for (i = 0; i < seqLen; i++) {
					int s = phredScores[i];
					if ((s < Config.PHRED_ABSOLUTE_MIN_ALLOWED) || (s > Config.PHRED_ABSOLUTE_MAX_ALLOWED)) {
						throw new FASTQParseException("Invalid PHRED ASCII found (ascii value not in [33, 126]).");
					}
					if ((s < Config.PHRED_VALID_P33_MIN) || (s > Config.PHRED_VALID_P33_MAX)) {
						couldBePhred33 = false;
					}
					if ((s < Config.PHRED_VALID_P64_MIN) || (s > Config.PHRED_VALID_P64_MAX)) {
						couldBePhred64 = false;
					}
					byte ch = sequence[i];
					if ((ch == 'A') || (ch == 'C') || (ch == 'G') || (ch == 'T')) {
						// OK
					} else {
						//containsInvalidNucleotides = true;
					}
				}
				
	//			if ((seqNum >= Config.PHRED_AUTO_DETECTION_INITIAL_READS_TO_CONSIDER) && 
	//					(seqNum >= Config.ADAPTER_AUTO_DETECTION_READS_TO_CONSIDER)) {
				if ((seqNum >= Config.PHRED_AUTO_DETECTION_INITIAL_READS_TO_CONSIDER)) {
					/* Break loop if we're done. */
					if (!(couldBePhred33 && couldBePhred64)) {
						/* Stop looking once we've found at least one example that 
						 * doesn't fit with both encodings. 
						 * This only applies once the initial read count has been processed. 
						 */
						break;	
					}
				}
			}
		}
		
		
		parser.close();
		if (detectPHREDOffset) {
			if (couldBePhred33 && !couldBePhred64) {
				result.phredOffset = 33;
			} else if (couldBePhred64 && !couldBePhred33) {
				result.phredOffset = 64;
			} else {
				throw new FASTQAutoDetectionException(
						"Could not auto-detect PHRED offset for " + seqFilename);
			}
		}
		return result;
	}
	
}

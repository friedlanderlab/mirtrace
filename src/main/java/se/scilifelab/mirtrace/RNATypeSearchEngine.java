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

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

class RNATypeSearchEngine {

	Map<String,Integer> rnaTypes = new HashMap<String,Integer>(); 
	Map<Integer,DNASequenceDatabase> databases = new HashMap<Integer,DNASequenceDatabase>();
	Config config;
	Queue<String> warnings;
	
	public RNATypeSearchEngine(Config config, Queue<String> warnings) throws DBNotFoundException {
		this.config = config;
		this.warnings = warnings;
		rnaTypes.put("rrna", Config.RNA_TYPE_R_RNA);
		rnaTypes.put("trna", Config.RNA_TYPE_T_RNA);
		rnaTypes.put("mirna", Config.RNA_TYPE_MI_RNA);
		rnaTypes.put("artifacts", Config.RNA_TYPE_ART_RNA);
				
		for (Map.Entry<String,Integer> e : rnaTypes.entrySet()) {
			String species;
			String key;
			if (e.getKey().equals("artifacts")) {
				species = "meta_species_any";
				key = "artifacts";
			} else {
				species = config.getSpecies();
				key = e.getKey();
			}
			String dbFilename = "rnatype." + species + "." + key + ".db.gz";
			InputStream dbInputStream = DatabaseLoader.loadDatabase(dbFilename, config, false, warnings, 
					"Running miRTrace in custom database mode but no custom database found for species " + config.getSpeciesVerbosename() + 
					" and RNA type " + key);
			databases.put(e.getValue(), new DNASequenceDatabase(dbInputStream));
		}
	}
	
	String search(byte[] seq, int seqLen) {
		// TODO: consider speeding up search calls using hard-coded arrays or something.

		/* Match tie-breaking system:
		 * 
		 * 1) miRNA perfect match
		 * 2) tRNA perfect match
		 * 3) rRNA perfect match
		 * 4) miRNA 1-mismatch [allowed] match
		 * 5) tRNA 1-mismatch [allowed] match
		 * 6) rRNA 1-mismatch [allowed] match
		 * 7) artifacts DNA exact match
		 * 8) ELSE: classify as unknown 
		 */	
		
		if (databases.get(Config.RNA_TYPE_MI_RNA).search(seq, seqLen, 0, false, false).matches) {
			return "mirna";
		} else if (databases.get(Config.RNA_TYPE_T_RNA).search(seq, seqLen, 0, false, false).matches) {
			return "trna";
		} else if (databases.get(Config.RNA_TYPE_R_RNA).search(seq, seqLen, 0, false, false).matches) {
			return "rrna";
		} else if (databases.get(Config.RNA_TYPE_MI_RNA).search(seq, seqLen, 1, false, false).matches) {
			return "mirna";
		} else if (databases.get(Config.RNA_TYPE_T_RNA).search(seq, seqLen, 1, false, false).matches) {
			return "trna";
		} else if (databases.get(Config.RNA_TYPE_R_RNA).search(seq, seqLen, 1, false, false).matches) {
			return "rrna";
		} else if (databases.get(Config.RNA_TYPE_ART_RNA).slidingWindowSearch(seq, seqLen).matches) {
			return "artifacts";
		} else {
			return null;
		}
	}
	
	int getRefSeqCounts(int rnaTypeRRna) {
		return databases.get(rnaTypeRRna).refSeqCount;
	}
	
	/* TODO: The output of this function should really be put together with the output of "search"
	 * into one instance of a new SearchResult object... */

	
	void processRefSeqIdSearches(Integer rnaType, DNASequenceHashMapEntry entry, QCStatistics targetStatistics, 
			Map<Integer,Integer> lowestSeqDetectedLocations) {
		DNASequenceDatabase.SearchResult lastMatch = DNASequenceDatabase.NO_MATCH_RESULT;
		while (true) {
			if (rnaType == Config.RNA_TYPE_ART_RNA) {
				lastMatch = databases.get(rnaType).slidingWindowSearch(
						entry.getSeq(), entry.getLength(), lastMatch.refSeqId);
			} else {
				lastMatch = databases.get(rnaType).search(entry.getSeq(), entry.getLength(), 1, false, false, lastMatch.refSeqId);
			}
			if (!lastMatch.matches) {
				break;
			} else {
				targetStatistics.storeFoundRNARead(rnaType, lastMatch.refSeqId);
				if (lowestSeqDetectedLocations != null) {
					/* Determine read depth (a.k.a. complexity) */
					int readDetectionDepth = entry.getFirstDetectedDepth();
					Integer currentLowestLocation = lowestSeqDetectedLocations.get(lastMatch.refSeqId);
					if ((currentLowestLocation == null) || (readDetectionDepth < currentLowestLocation)) {
						lowestSeqDetectedLocations.put(lastMatch.refSeqId, readDetectionDepth);
					}
				}
			}
		}
	}

}

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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class CladeSearchEngine {
	
	List<DNASequenceDatabase> databases = new ArrayList<DNASequenceDatabase>();
	Config config;
	Queue<String> warnings;
	private Pattern RE_CLADE_SEQ_ENTRY = Pattern.compile("^seq_([^\\+]+)\\+clade_([^\\+]+)\\+(.+)$");
	
	class CladeSearchResult {
		
		int cladeFound;
		String familyId;
		String cladeString;
		
		public CladeSearchResult(int cladeFound, String familyId, String cladeString) {
			this.cladeFound = cladeFound;
			this.familyId = familyId;
			this.cladeString = cladeString;
		}
	}
	
	public CladeSearchEngine(Config config, Queue<String> warnings) throws DBNotFoundException {
		this.config = config;
		this.warnings = warnings;
		for (int i=0; i < Config.CLADES.length; i++) {
			String clade = Config.CLADES[i];
			String species = "meta_species_any";
			String dbFilename = "clade." + species + "." + clade + ".db.gz";
			InputStream dbInputStream = DatabaseLoader.loadDatabase(dbFilename, config, false, warnings, null);
			
			databases.add(new DNASequenceDatabase(dbInputStream));
		}
	}
	
	CladeSearchResult search(byte[] seq, int seqLen) {
		/* We're using exact matching on databases that are supposed to be 
		 * completely non-overlapping. */
		CladeSearchResult sr = null;
		if (seqLen < Config.CLADE_DB_SEQ_LEN_CUTOFF) {
			return null;
		}
		int querySeqLen = Config.CLADE_DB_SEQ_LEN_CUTOFF;
		for (int i=0; i < Config.CLADES.length; i++) {
			DNASequenceDatabase.SearchResult res = databases.get(i).search(seq, querySeqLen, 0, true, true);
			if (res.matches) {
				String seqId = databases.get(i).getRefSeqId(res.refSeqId);
				//String familyId = seqId.replaceFirst("^seq_.*\\+clade_", "");
				
				Matcher m = RE_CLADE_SEQ_ENTRY.matcher(seqId);
				if (!m.matches()) {
					throw new RuntimeException("Internal error in clade database: invalid seq record id.");
				}
				String familyId = m.group(2);
				
				sr = new CladeSearchResult(i, familyId, Config.CLADES[i]);
				return sr;
			}
		}
		return null;
	}
	
	List<String> findAllMiRBaseEntries(byte[] seq, int seqLen) {
		/* We're using exact matching on databases that are supposed to be 
		 * completely non-overlapping. */
		List<String> found = new ArrayList<String>();
		if (seqLen < Config.CLADE_DB_SEQ_LEN_CUTOFF) {
			return found;
		}
		int querySeqLen = Config.CLADE_DB_SEQ_LEN_CUTOFF;
		for (int i=0; i < Config.CLADES.length; i++) {
			DNASequenceDatabase.SearchResult lastMatch = DNASequenceDatabase.NO_MATCH_RESULT;
			while (true) {
				lastMatch = databases.get(i).search(seq, querySeqLen, 0, true, true, lastMatch.refSeqId);
				if (!lastMatch.matches) {
					break;
				} else {
					String seqId = databases.get(i).getRefSeqId(lastMatch.refSeqId);
					Matcher m = RE_CLADE_SEQ_ENTRY.matcher(seqId);
					if (!m.matches()) {
						throw new RuntimeException("Internal error in clade database: invalid seq record id.");
					}
					String miRBaseEntryId = m.group(3);
					found.add(miRBaseEntryId);
				}
			}
		}
		return found;
	}

	public int[] getRefSeqCounts() {
		int[] refSeqCounts = new int[Config.CLADES.length];
		for (int i=0; i < Config.CLADES.length; i++) {
			refSeqCounts[i] = databases.get(i).getRefSeqCount();
		}
		return refSeqCounts;
	}

	public int[] getRefFamilyCounts() {
		int[] refFamilyCounts = new int[Config.CLADES.length];
		for (int i=0; i < Config.CLADES.length; i++) {
			HashSet<String> foundFamilyIds = new HashSet<String>();
			for (int j=0; j < databases.get(i).getRefSeqCount(); j++) {
				String seqId = databases.get(i).getRefSeqId(j);
				//String familyId = seqId.replaceFirst("^seq_.*\\+clade_", "");
				Matcher m = RE_CLADE_SEQ_ENTRY.matcher(seqId);
				if (!m.matches()) {
					throw new RuntimeException("Internal error in clade database: invalid seq record id.");
				}
				String familyId = m.group(2);
				foundFamilyIds.add(familyId);
			}
			refFamilyCounts[i] = foundFamilyIds.size();
		}
		return refFamilyCounts;
	}

}

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class QCStatistics {
	
	// NOTE: The QC_STATUS_* constants must match the logic in ReportBuilderTSV.
	static final int QC_STATUS_INVALID = 0; // Sequences with ambiguous nucleotides and/or low PHRED score
	static final int QC_STATUS_VALID_QUALITY = 1; // OK PHRED but low complexity
	static final int QC_STATUS_VALID_COMPLEXITY = 2; // OK complexity but too short
	static final int QC_STATUS_VALID_LENGTH = 3; // OK length but no adapter, still kept for analysis
	static final int QC_STATUS_VALID_LENGTH_AND_ADAPTER = 4; // All OK
	
	static final int phredScoreArraySize = Config.MAX_PHRED_REPORTED - 
            Config.MIN_PHRED_REPORTED + 1;
	
	/* Statistics */
	long allSeqsCount;
	long uniqueQCPassedSeqsCount;
	long[] statsNucleotidePhredScores;
	long[] statsLength;
	long[] statsRNAType;
	long[] statsQC;
	long[] statsClades;
	List<Set<String>> foundCladeFamilies;
	List<Set<Integer>> foundRNAReads;
	Map<String,String> qcAnalysisFlags = new HashMap<String,String>();
	transient Map<String,CladeFamilyRecord> foundCladeSpecificmiRBaseEntries = new HashMap<String,CladeFamilyRecord>();
	
	boolean hasCladeSpecificmiRBaseEntry(String usedSeqPrefix) {
		return foundCladeSpecificmiRBaseEntries.containsKey(usedSeqPrefix);
	}

	CladeFamilyRecord getCladeSpecificmiRBaseEntry(String usedSeqPrefix) {
		return foundCladeSpecificmiRBaseEntries.get(usedSeqPrefix);
	}

	void storeCladeSpecificmiRBaseEntries(String usedSeqPrefix, CladeFamilyRecord record) {
		foundCladeSpecificmiRBaseEntries.put(usedSeqPrefix, record);
	}

	/*
	 * statsComplexityReadDepth:
	 * 		The index represents the number of detected unique miRNA hairpin seqs.
	 * 		The value represents the read depth at which this number of unique
	 * 		sequences was first seen.
	 */
	List<Long> statsComplexityReadDepth;
	
	/**
	 */
	QCStatistics() {
		this.statsNucleotidePhredScores = new long[phredScoreArraySize];
		this.statsLength = new long[Config.READ_LENGTH_CUTOFF + 1]; 
		this.statsRNAType = new long[Config.NUMBER_OF_RNA_CLASSES];
		this.statsQC = new long[5];
		this.statsClades = new long[Config.CLADES.length];
		this.statsComplexityReadDepth = new ArrayList<Long>();
		this.foundCladeFamilies = new ArrayList<Set<String>>();
		for (int i=0; i < Config.CLADES.length; i++) {
			foundCladeFamilies.add(new HashSet<String>());
		}
		this.foundRNAReads = new ArrayList<Set<Integer>>();
		for (int i=0; i < Config.NUMBER_OF_RNA_CLASSES; i++) {
			foundRNAReads.add(new HashSet<Integer>());
		}
		qcAnalysisFlags.put("phred", "unknown");
		qcAnalysisFlags.put("length", "unknown");
		qcAnalysisFlags.put("qc", "unknown");
		qcAnalysisFlags.put("rnatype", "unknown");
		qcAnalysisFlags.put("contamination", "unknown");
		qcAnalysisFlags.put("complexity", "unknown");
	}
	
	void storeQCAnalysisFlag(String analysisId, String newStatus) {
		if (!qcAnalysisFlags.containsKey(analysisId)) {
			throw new IllegalArgumentException("INTERNAL ERROR: Invalid analysisId");
		}
		qcAnalysisFlags.put(analysisId, newStatus);
	}
		
	void storeSequenceLen(int seqLen) {
		storeSequenceLen(seqLen, 1);
	}

	void storeSequenceLen(int seqLen, int count) {
		if (seqLen < 0) {
			throw new IllegalArgumentException("seqLen = 0");
		}
		if (seqLen > Config.READ_LENGTH_CUTOFF) {
			seqLen = Config.READ_LENGTH_CUTOFF;
		}
		statsLength[seqLen] += count;
	}
	
	void storeComplexity(long readDepth) {
		statsComplexityReadDepth.add(readDepth);
	}

	void storeFoundCladeFamily(int cladeId, String cladeFamily) {
		foundCladeFamilies.get(cladeId).add(cladeFamily);
	}

	void storeFoundRNARead(int miRNAType, int miRNAId) {
		foundRNAReads.get(miRNAType).add(miRNAId);
	}
	
	static int getMinPhredScore() {
		return Config.MIN_PHRED_REPORTED;
	}
	
	Set<Integer> getFoundRNAReads(int rnaType) {
		return foundRNAReads.get(rnaType);
	}

	static int getPhredScoreArraySize() {
		return phredScoreArraySize;
	}
	
	long[] getPhredStats() {
		return statsNucleotidePhredScores;
	}
	
	long[] getLengthStats() {
		return statsLength;
	}
	
	long[] getQCStats() {
		return statsQC;
	}
	
	long[] getRNATypeStats() {
		return statsRNAType;
	}

	long getAllSeqsCount() {
		return allSeqsCount;
	}
	
	long getUniqueQCPassedSeqsCount() {
		return uniqueQCPassedSeqsCount;
	}
	
	List<Long> getComplexityReadDepth() {
		Collections.sort(statsComplexityReadDepth);
		return statsComplexityReadDepth;
	}
	
	void prepareForExport() {
		Collections.sort(statsComplexityReadDepth);
	}


}

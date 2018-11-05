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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class MiRTraceReport {

	List<AnalysisPipelineResult> results;
	String reportTitle;
	List<String> reportComments;
	String reportGenerationDateTime;
	String[] cladeIdentifiers;
	String species;
	String speciesVerbosename;
	String mirtraceVersion;
	String mirtraceMode;
	String rRNASubunits;
	int[] cladeRefSeqCounts;
	int[] cladeRefFamilyCounts;
	int[] rnaTypeRefSeqCounts;
	int minPhredScore;
	int minSequenceLength;
	int maxSequenceLength;
	List<String> warnings;
	
	Map<String,String> qcCriteriaVerbose = new HashMap<String,String>();

	public MiRTraceReport(RNATypeSearchEngine rnaTypeSearchEngine, 
			CladeSearchEngine cladeSearchEngine,
			List<AnalysisPipelineResult> results,
			Config conf,
			List<String> warnings) {
		
		this.results = results;
		mirtraceMode = conf.getMirtraceMode();
		cladeIdentifiers = Config.CLADES;
		cladeRefSeqCounts = cladeSearchEngine.getRefSeqCounts();
		cladeRefFamilyCounts = cladeSearchEngine.getRefFamilyCounts();
		rnaTypeRefSeqCounts = new int[5];
		/* Assignment order must (at least) match that of rnaTypeNames in the app.html template. */
		if (conf.qcMode()) {
			rnaTypeRefSeqCounts[0] = rnaTypeSearchEngine.getRefSeqCounts(Config.RNA_TYPE_MI_RNA);
			rnaTypeRefSeqCounts[1] = rnaTypeSearchEngine.getRefSeqCounts(Config.RNA_TYPE_R_RNA);
			rnaTypeRefSeqCounts[2] = rnaTypeSearchEngine.getRefSeqCounts(Config.RNA_TYPE_T_RNA);
			rnaTypeRefSeqCounts[3] = rnaTypeSearchEngine.getRefSeqCounts(Config.RNA_TYPE_ART_RNA);
			rnaTypeRefSeqCounts[4] = 0; /* Unknown seqs. */
		}
		
		minSequenceLength = Config.MIN_ALLOWED_SEQ_LEN;
		maxSequenceLength = Config.READ_LENGTH_CUTOFF; 
		minPhredScore = Config.MIN_PHRED_REPORTED;
		reportComments = conf.getReportComments();
		DateFormat dfVerbose = new SimpleDateFormat("yyyy-MM-dd HH:mm");
		Date now = new Date();
		reportGenerationDateTime = dfVerbose.format(now);
		reportTitle = conf.getReportTitle();
		if (reportTitle.length() == 0) {
			if (conf.qcMode()) {
				reportTitle = "Quality Control Report";
			} else {
				reportTitle = "Clade Trace Report";
			}
		}
		
		species = conf.getSpecies();
		speciesVerbosename = conf.getSpeciesVerbosename();
		rRNASubunits = conf.getRRNASubunits();
		//adapterSeqs = conf.getAdapterSequences();
		this.warnings = warnings;
		mirtraceVersion = conf.getVersion();
		prepareForExport();
		qcCriteriaVerbose.put("phred", Config.QC_WARNING_CRITERIA_VERBOSE_PHRED);
		qcCriteriaVerbose.put("length", Config.QC_WARNING_CRITERIA_VERBOSE_LENGTH);
		qcCriteriaVerbose.put("qc", Config.QC_WARNING_CRITERIA_VERBOSE_QC);
		qcCriteriaVerbose.put("rnatype", Config.QC_WARNING_CRITERIA_VERBOSE_RNATYPE);
		qcCriteriaVerbose.put("complexity", Config.QC_WARNING_CRITERIA_VERBOSE_COMPLEXITY);
	}
	
	void prepareForExport() {
		for (AnalysisPipelineResult r : results) {
			r.getStats().prepareForExport();
		}
	}


}

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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import edu.emory.mathcs.backport.java.util.Collections;


class ReportBuilderTSV {

	List<AnalysisPipelineResult> results;
	File outputDir;
	public ReportBuilderTSV(List<AnalysisPipelineResult> results, File outputDir) {
		this.results = results;
		this.outputDir = outputDir;
	}
	
	void writeReports(boolean qcMode) throws FileNotFoundException {
		PrintWriter out;
		try {
			if (qcMode) {
				out = new PrintWriter(new File(outputDir, "mirtrace-stats-phred.tsv"), "UTF-8");
				buildPhredReport(out);
				out.close();
				
				out = new PrintWriter(new File(outputDir, "mirtrace-stats-qcstatus.tsv"), "UTF-8");
				buildQCFilteringReport(out);
				out.close();
				
				out = new PrintWriter(new File(outputDir, "mirtrace-stats-length.tsv"), "UTF-8");
				buildLengthReport(out);
				out.close();
				
				out = new PrintWriter(new File(outputDir, "mirtrace-stats-rnatype.tsv"), "UTF-8");
				buildRNATypeReport(out);
				out.close();
				
				out = new PrintWriter(new File(outputDir, "mirtrace-stats-mirna-complexity.tsv"), "UTF-8");
				buildReadDepthReport(out);
				out.close();
			}
			
			out = new PrintWriter(new File(outputDir, "mirtrace-stats-contamination_basic.tsv"), "UTF-8");
			buildContaminationReport(out);
			out.close();
			
			out = new PrintWriter(new File(outputDir, "mirtrace-stats-contamination_detailed.tsv"), "UTF-8");
			buildContaminationFoundMirnasReport(out);
			out.close();
			
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			System.err.println("FATAL ERROR: UTF-8 encoding not supported on this platform?!?");		
			System.exit(-1);
		}
		
		
	}

	private void buildPhredReport(PrintWriter out) {
		out.write("PHRED_SCORE");
		for (AnalysisPipelineResult r : results) {
			out.write("\t" + r.getVerbosename());
		}
		out.write("\n");
		for (int i=0; i < QCStatistics.getPhredScoreArraySize(); i++) {
			out.write(Integer.toString(QCStatistics.getMinPhredScore() + i));
			for (AnalysisPipelineResult r : results) {
				out.write("\t" + r.getStats().getPhredStats()[i]);
			}
			out.write("\n");
		}

	}

	private void buildQCFilteringReport(PrintWriter out) {
		out.write("QC_STATUS");
		for (AnalysisPipelineResult r : results) {
			out.write("\t" + r.getVerbosename());
		}
		out.write("\n");
		
		/* NOTE: 
		 * The constants here must match those of QCStatistics.
		 */		
		out.write("LOW_PHRED");
		for (AnalysisPipelineResult r : results) {
			out.write("\t" + r.getStats().getQCStats()[0]);
		}
		out.write("\n");

		out.write("LOW_COMLPEXITY");
		for (AnalysisPipelineResult r : results) {
			out.write("\t" + r.getStats().getQCStats()[1]);
		}
		out.write("\n");

		out.write("LENGTH_SHORTER_THAN_18");
		for (AnalysisPipelineResult r : results) {
			out.write("\t" + r.getStats().getQCStats()[2]);
		}
		out.write("\n");

		out.write("ADAPTER_NOT_DETECTED");
		for (AnalysisPipelineResult r : results) {
			out.write("\t" + r.getStats().getQCStats()[3]);
		}
		out.write("\n");
		
		out.write("ADAPTER_REMOVED_LENGTH_OK");
		for (AnalysisPipelineResult r : results) {
			out.write("\t" + r.getStats().getQCStats()[4]);
		}
		out.write("\n");
	}

	
	private void buildLengthReport(PrintWriter out) {
		out.write("LENGTH");
		for (AnalysisPipelineResult r : results) {
			out.write("\t" + r.getVerbosename());
		}
		out.write("\n");
		for (int i=0; i < results.get(0).getStats().getLengthStats().length; i++) {
			out.write(Integer.toString(i));
			for (AnalysisPipelineResult r : results) {
				out.write("\t" + r.getStats().getLengthStats()[i]);
			}
			out.write("\n");
		}
	}
	
	private void buildRNATypeReport(PrintWriter out) {
		out.write("RNA_TYPE");
		for (AnalysisPipelineResult r : results) {
			out.write("\t" + r.getVerbosename());
		}
		out.write("\n");
		
		/* NOTE: 
		 * The constants here must match those of QCStatistics.
		 */	
		out.write("miRNA");
		for (AnalysisPipelineResult r : results) {
			out.write("\t" + r.getStats().getRNATypeStats()[0]);
		}
		out.write("\n");

		out.write("rRNA");
		for (AnalysisPipelineResult r : results) {
			out.write("\t" + r.getStats().getRNATypeStats()[1]);
		}
		out.write("\n");

		out.write("tRNA");
		for (AnalysisPipelineResult r : results) {
			out.write("\t" + r.getStats().getRNATypeStats()[2]);
		}
		out.write("\n");

		out.write("artifact");
		for (AnalysisPipelineResult r : results) {
			out.write("\t" + r.getStats().getRNATypeStats()[3]);
		}
		out.write("\n");

		out.write("unknown");
		for (AnalysisPipelineResult r : results) {
			out.write("\t" + r.getStats().getRNATypeStats()[4]);
		}
		out.write("\n");
	}

	private void buildReadDepthReport(PrintWriter out) {
		/* Note that the output columns may have different lengths. */
		out.write("DISTINCT_MIRNA_HAIRPINS_ACCUMULATED_COUNT");
		for (AnalysisPipelineResult r : results) {
			out.write("\t" + r.getVerbosename());
		}
		out.write("\n");
		
		int maxNumRows = 0;
		for (AnalysisPipelineResult r : results) {
			int resultsCount = r.getStats().getComplexityReadDepth().size(); 
			if (resultsCount > maxNumRows) {
				maxNumRows = resultsCount;
			}
		}
		for (int i=0; i < maxNumRows; i++) {
			out.write(String.valueOf(i));
			for (AnalysisPipelineResult r : results) {
				if (i < r.getStats().getComplexityReadDepth().size()) {
					Long readDepth = r.getStats().getComplexityReadDepth().get(i);
					out.write("\t" + readDepth);
				} else {
					out.write("\t");
				}
			}
			out.write("\n");
		}		

	}

	private void buildContaminationReport(PrintWriter out) {
		out.write("CLADE");
		for (AnalysisPipelineResult r : results) {
			out.write("\t" + r.getVerbosename());
		}
		out.write("\n");

		for (int i=0; i < Config.CLADES.length; i++) {
			out.write(Config.CLADES[i]);
			for (AnalysisPipelineResult r : results) {
				out.write("\t" + r.getStats().statsClades[i]);
			}
			out.write("\n");
		}
	}
	
	
	private void buildContaminationFoundMirnasReport(PrintWriter out) {
		/* Index detected entries. */
		Map<String,CladeFamilyRecord> globalCladeSeqsFound = new HashMap<String,CladeFamilyRecord>();

		for (AnalysisPipelineResult r : results) {
			for (Map.Entry<String,CladeFamilyRecord> entry : r.getStats().foundCladeSpecificmiRBaseEntries.entrySet()) {
				CladeFamilyRecord globalRecord;
				String seq = entry.getKey();
				if (globalCladeSeqsFound.containsKey(seq)) {
					globalRecord = globalCladeSeqsFound.get(seq);
				} else {
					globalRecord = new CladeFamilyRecord(seq, entry.getValue().cladeId);
					globalCladeSeqsFound.put(seq, globalRecord);
				}
				for (String miRBaseId : entry.getValue().miRBaseIds) {
					globalRecord.putmiRBaseId(miRBaseId);
				}
			}
		}
		List<CladeFamilyRecord> globalCladeFamilyRecords = new ArrayList<CladeFamilyRecord>(globalCladeSeqsFound.values());
		Collections.sort(globalCladeFamilyRecords);
		
		/* Generate output. */
		
		out.write("CLADE\tFAMILY_ID\tMIRBASE_IDS\tSEQ");
		for (AnalysisPipelineResult r : results) {
			out.write("\t" + r.getVerbosename());
		}
		out.write("\n");

		for (CladeFamilyRecord rGlobal : globalCladeFamilyRecords) {
			String seq = rGlobal.seq;
			Collections.sort(rGlobal.miRBaseIds);
			String mirBaseIdList = StringUtils.join(rGlobal.miRBaseIds, ",");
			out.write(Config.CLADES[rGlobal.cladeId] + "\t" + rGlobal.getFamilyId() + "\t" + mirBaseIdList + "\t" + seq);
			for (AnalysisPipelineResult r : results) {
				if (r.getStats().foundCladeSpecificmiRBaseEntries.get(seq) != null) {
					out.write("\t" + r.getStats().foundCladeSpecificmiRBaseEntries.get(seq).count);	
				} else {
					out.write("\t0");
				}
			}
			out.write("\n");
		}
		
		/*
			for (HashMap.Entry<String, HashMap<String, HashMap<String, Integer>>> seqMap : currentGlobalCladeTable.entrySet()) {
				String curSeq = seqMap.getKey();
				for (HashMap.Entry<String, HashMap<String, Integer>> mirSpeciesMap : seqMap.getValue().entrySet()) {
					String miRBaseId = mirSpeciesMap.getKey();
					for (HashMap.Entry<String,Integer> speciesCountEntry : mirSpeciesMap.getValue().entrySet()) {
						String species = speciesCountEntry.getKey();
						out.write(Config.CLADES[i] + ":" + species + "-" + miRBaseId + ":" + curSeq);
						for (AnalysisPipelineResult r : results) {
							if (r.getStats().foundCladeSpecificmiRBaseEntries.get(i).containsKey(curSeq) && 
									r.getStats().foundCladeSpecificmiRBaseEntries.get(i).get(curSeq).containsKey(miRBaseId) &&
									r.getStats().foundCladeSpecificmiRBaseEntries.get(i).get(curSeq).get(miRBaseId).containsKey(species)
							) {
								out.write("\t" + r.getStats().foundCladeSpecificmiRBaseEntries.get(i).get(curSeq).get(miRBaseId).get(species));	
							} else {
								out.write("\t0");
							}
						}
						out.write("\n");
					}
				}
			}
		*/
	}

	
}

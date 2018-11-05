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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;


/** Application main class.
 * @author Yrin Eldfjell
 *
 */
public class MiRTrace {

	Config config;
	RNATypeSearchEngine rnaTypeSearchEngine;
	CladeSearchEngine cladeSearchEngine;
	ConcurrentLinkedQueue<String> warnings;

	private void loadDatabases() {
		try {
			if (config.qcMode()) {
				rnaTypeSearchEngine = new RNATypeSearchEngine(config, warnings);
			}
			cladeSearchEngine = new CladeSearchEngine(config, warnings);			
		} catch (DBNotFoundException e) {
			fail("ERROR: " + e.getMessage() + "\nAborting.");
		}
		
		if (config.qcMode()) {
			if (rnaTypeSearchEngine.getRefSeqCounts(Config.RNA_TYPE_MI_RNA) == 0) {
				addWarning("The miRNA reference database has no entries for the current species.");
			}
			if (rnaTypeSearchEngine.getRefSeqCounts(Config.RNA_TYPE_R_RNA) == 0) {
				addWarning("The rRNA reference database has no entries for the current species.");
			}
			if (rnaTypeSearchEngine.getRefSeqCounts(Config.RNA_TYPE_T_RNA) == 0) {
				addWarning("The tRNA reference database has no entries for the current species.");
			}
		}
	}
	
	public MiRTrace(String[] args) {
		warnings = new ConcurrentLinkedQueue<String>();
		config = new Config(args);
		loadDatabases();
		for (String w : config.getWarnings()) {
			warnings.add(w);
		}
	}
	
	void run() {
		File outputDir = config.getMainOutputDirectory();
		if (outputDir.exists()) {
			if (!config.getForceOverwriteOutput()) {
				fail("Output directory already exists:\n" + outputDir + 
						"\nUse '--force' to force overwriting output.");	
			} else {
				System.err.println("NOTE: reusing existing output directory, outdated files may be present.");
			}
		} else {
			outputDir.mkdir();
		}
		if (config.writeFASTA()) {
			config.getUniqueReadsOutputDirectory().mkdir();
			if (config.qcMode()) {
				config.getUnmappedReadsOutputDirectory().mkdir();
			}
		}		
		List<AnalysisPipelineResult> pipelineResults = runAnalysis();
		writeResults(pipelineResults);
		for (String w : warnings) {
			System.err.println("WARNING: " + w);
		}
	}
	
	/** Application main function.
	 * @param args See help text defined in Config.java.
	 */
	public static void main(String[] args) {
		testJVMVersionOK();
		long startTime = System.currentTimeMillis();
		MiRTrace program = new MiRTrace(args);
		System.err.println("miRTrace version " + program.config.getVersion() + " starting. Processing " + 
				program.config.getNumAnalysisTasks() + " sample(s).");
		program.run();
		long endTime = System.currentTimeMillis();
		System.err.println("\nRun complete. Processed " + program.config.getNumAnalysisTasks() + 
				" sample(s) in " + ((endTime - startTime)/1000) + " s.");
		System.err.println("\nReports written to " + program.config.getMainOutputDirectory().getAbsolutePath() + "/");
		System.err.println("For information about citing our paper, run miRTrace in mode \"cite\".");
	}
	
	static void testJVMVersionOK() {
		String[] v = System.getProperty("java.version").split("\\.");
		int major = Integer.parseInt(v[0]);
		int minor = Integer.parseInt(v[1]);
		if ((major > Config.MIN_JAVA_VERSION_REQUIRED_MAJOR) || 
				(major == Config.MIN_JAVA_VERSION_REQUIRED_MAJOR && minor >= Config.MIN_JAVA_VERSION_REQUIRED_MINOR)) {
			// ok
		} else {
			fail("This program requires a Java version of at least " + 
					Config.MIN_JAVA_VERSION_REQUIRED_MAJOR + "." + Config.MIN_JAVA_VERSION_REQUIRED_MINOR);
		}
	}
	
	void addWarning(String msg) {
		warnings.add(msg);
	}

	static void fail(String msg) {
		System.err.println(msg);
		// TODO: consider killing remaining threads and delete intermediate output files.
		System.exit(-1);
	}
	
	List<AnalysisPipelineResult> runAnalysis() {
		AnalysisTaskManager taskMgr = new AnalysisTaskManager(config, rnaTypeSearchEngine, cladeSearchEngine);
		for (AnalysisTask task : config.getAnalysisTasks()) {
			taskMgr.add(task);
		}
		return taskMgr.processTasks();
	}	
	
	void writeResults(List<AnalysisPipelineResult> pipelineResults) {
		Collections.sort(pipelineResults, new AnalysisPipelineResult.DisplayOrderComparator());
		ArrayList<String> warningsList = new ArrayList<String>(warnings);
		MiRTraceReport report = new MiRTraceReport(rnaTypeSearchEngine, 
				cladeSearchEngine, pipelineResults, config, warningsList);
		
		/*
		 * Write JSON results.
		 */		
		ReportBuilderJSON jsonReport = new ReportBuilderJSON(report);
		String jsonText = jsonReport.build(config.qcMode());
		if (config.qcMode()) {

			PrintWriter out;
			try {
				try {
					out = new PrintWriter(new File(config.getMainOutputDirectory(), 
							Config.JSON_FILENAME).getAbsolutePath(), "UTF-8");
					out.write(jsonText);
					out.close();
				} catch (UnsupportedEncodingException e) {
					fail("Unexpected error: UTF-8 encoding not supported.");
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
				throw new RuntimeException("File not found.");
			}
		}
		/*
		 * Write HTML report.
		 */
		ReportBuilderHTML htmlReport = new ReportBuilderHTML(config);
		htmlReport.generateReport(jsonText);
		
		/*
		 * Write text (tab-separated) reports.
		 */
		ReportBuilderTSV tsvReport = new ReportBuilderTSV(pipelineResults, 
				config.getMainOutputDirectory());
		try {
			tsvReport.writeReports(config.qcMode());
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			throw new RuntimeException("Could not generate text file. Do you have write permissions?");
		}
	}	

}

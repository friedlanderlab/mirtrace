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
import java.util.ArrayList;
import java.util.List;

class AnalysisTask implements Comparable<AnalysisTask> {
	
	public static enum TaskStatus {
		SUCCSESS, QUEUED, RUNNING, RETRY_LATER, KILLED, 
		FAIL_IO_ERROR, FAIL_PARSE_ERROR, FAIL_PHRED_ERROR, FAIL_NOT_ENOUGH_MEMORY, FAIL_INTERNAL_ERROR,
		NEW_TASK
	}
	
	String filename;
	String verbosename;
	String fastaFilenameBase;
	String sampleAdapter = null;
	long filesize;
	int minBucketsNeeded;
	int bucketsAllocated;
	AnalysisPipelineResult qcPipelineResult;
	TaskStatus taskStatus;
	int displayOrder;
	Integer phredOffset;
	List<String> warnings = new ArrayList<String>();
	boolean firstAttempt;
	Config config;

	AnalysisTask(String filename, String verbosename, String fastaFilenameBase, 
			int displayOrder, String sampleAdapter, Integer phredOffset, Config config) {
		this.filename = filename;
		this.verbosename = verbosename;
		this.fastaFilenameBase = fastaFilenameBase;
		if (sampleAdapter != null) {
			this.sampleAdapter = sampleAdapter;
		}
		this.phredOffset = phredOffset;
		this.config = config;
		this.filesize = new File(filename).length();
		minBucketsNeeded = Config.MIN_INITIAL_HASHMAP_CAPACITY;
		bucketsAllocated = 0;
		this.displayOrder = displayOrder; // Propagated to QCPipelineResult, not used here.
		taskStatus = TaskStatus.NEW_TASK;
		firstAttempt = true;
	}
	
	boolean isGzipped() {
		if (config.pipesEnabled()) {
			return false;
		}
		try {
			if (FASTQParser.isGzipped(filename)) {
				return true;
			}
		} catch (FASTQParseException e) {
			/* Do nothing. If the problem is persistent, this error will 
			 * happen again in FASTQParser and be dealt with there. 
			 */
		}	
		return false;
	}
	
	AnalysisPipeline start(AnalysisTaskManager taskManager, int initialBuckets, 
			RNATypeSearchEngine rnaTypeSearchEngine, CladeSearchEngine cladeSearchEngine) {
		taskManager.increaseAllocatedBuckets(initialBuckets);
		AnalysisPipeline pipelineThread = new AnalysisPipeline(
				taskManager, 
				this, 
				rnaTypeSearchEngine, 
				cladeSearchEngine, 
				initialBuckets,
				phredOffset
		);
		firstAttempt = false;
		pipelineThread.start();
		return pipelineThread;
	}
	
	public int compareTo(AnalysisTask other) {
		/* We want the tasks to be sorted so that larger files comes first. */
		if (this.filesize < other.filesize) {
			return 1;
		} else if (this.filesize > other.filesize) {
			return -1;
		} else {
			return 0;
		}
	}

	String getFilename() {
		return filename;
	}

	String getVerbosename() {
		return verbosename;
	}

	long getFilesize() {
		return filesize;
	}

	int getMinBucketsNeeded() {
		return minBucketsNeeded;
	}

	void setMinBucketsNeeded(int buckets) {
		minBucketsNeeded = buckets;
	}

	int getBucketsAllocated() {
		return bucketsAllocated;
	}
	
	void setBucketsAllocated(int buckets) {
		bucketsAllocated = buckets;
	}
	
	AnalysisPipelineResult getQcPipelineResult() {
		return qcPipelineResult;
	}
	
	void setQcPipelineResult(AnalysisPipelineResult qcPipelineResult) {
		this.qcPipelineResult = qcPipelineResult;
	}
	
	void setStatus(TaskStatus ts) {
		taskStatus = ts;
	}
	
	Integer getPhredOffset() {
		return phredOffset;
	}

	void setPhredOffset(Integer p) {
		phredOffset = p;
	}

	TaskStatus getStatus() {
		return taskStatus;
	}
	
	public int getDisplayOrder() {
		return displayOrder;
	}

	public String getFASTAFilenameBase() {
		return fastaFilenameBase;
	}
	
	List<String> getWarnings() {
		return warnings;
	}
	
	void addWarning(String w) {
		warnings.add(w + "(" + verbosename + ")");
	}

	String getAdapter() {
		return sampleAdapter;		
	}

}

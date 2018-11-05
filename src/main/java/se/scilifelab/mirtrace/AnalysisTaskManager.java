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
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

class AnalysisTaskManager {

	Config config;
	List<AnalysisTask> pendingTasks = new ArrayList<AnalysisTask>();
	ConcurrentLinkedQueue<AnalysisTask> terminatedAnalysisTaskQueue = 
			new ConcurrentLinkedQueue<AnalysisTask>();
	private long allocatedBuckets = 0;
	private long totalBuckets = 0;
	RNATypeSearchEngine rnaTypeSearchEngine; 
	CladeSearchEngine cladeSearchEngine;
	
	AnalysisTaskManager(Config config, RNATypeSearchEngine rnaTypeSearchEngine, 
			CladeSearchEngine cladeSearchEngine) {
		this.config = config;
		this.rnaTypeSearchEngine = rnaTypeSearchEngine;
		this.cladeSearchEngine = cladeSearchEngine;
	}	
	
	void fail(String msg) {
		System.err.println(msg);
		// TODO: consider killing remaining threads and delete intermediate output files.
		System.exit(-1);
	}
	
	void failLowHeapMem(String filename) {
		fail("ERROR: Not enough memory" + (filename == null ? "." : " to process '" + filename + "'.") +  
				"Please increase the JVM heap size (-Xms and -Xmx parameters). See --help for help.");
	}
	
	void add(AnalysisTask task) {
		task.setStatus(AnalysisTask.TaskStatus.QUEUED);
		pendingTasks.add(task);
	}
	
	long getTotalBuckets() {
		return totalBuckets;
	}
	
	synchronized long getAllocatedBuckets() {
		return allocatedBuckets;
	}
	
	synchronized void increaseAllocatedBuckets(long buckets) {
		allocatedBuckets += buckets;
	}

	synchronized void decreaseAllocatedBuckets(long buckets) {
		allocatedBuckets -= buckets;
	}
	
	synchronized void addTerminatedTask(AnalysisTask task) {
		terminatedAnalysisTaskQueue.add(task);
	}
	
	synchronized boolean requestBucketReallocation(int currentBucketCount, int newBucketCount, AnalysisTask task) {
		if (config.getVerbosityLevel() > 2) {
			long curTime = System.currentTimeMillis();
			System.err.println("[" + (curTime / 1000) + "] " + "call: requestBucketReallocation(" + 
					currentBucketCount + ", " + newBucketCount + ", <" + task.filename + ">)");
			System.err.println("             " + "(totalBuckets - allocatedBuckets) = " + 
					(totalBuckets - allocatedBuckets) + "\n");
		}
		if (newBucketCount > totalBuckets) {
			failLowHeapMem(task.getFilename());
		}
		if (newBucketCount <= (totalBuckets - allocatedBuckets)) {
			increaseAllocatedBuckets(newBucketCount);
			return true;
		}
		return false;
	}
	
	List<AnalysisPipelineResult> processTasks() {
		List<AnalysisPipelineResult> pipelineResults = new ArrayList<AnalysisPipelineResult>();
		Collections.sort(pendingTasks);

		long totalMem = Runtime.getRuntime().totalMemory();
		long totalHashMapMem = totalMem - config.getGlolbalMemoryReserve() - 
				(config.getPerSampelMemoryReserve() * config.getNumAnalysisTasks());
		if (totalHashMapMem <= 0) {
			failLowHeapMem(null);
		}
		totalBuckets = totalHashMapMem / Config.HM_MEMORY_USAGE_PER_HASH_BUCKET;
		allocatedBuckets = 0;

		/* Run QC pipeline. */
		int numRunningThreads = 0;
		
		// TODO: qcPipelineThreads is currently not used. Use later for graceful shutdown?
		//List<AnalysisPipeline> qcPipelineThreads = new ArrayList<AnalysisPipeline>();
		
		while ((numRunningThreads > 0) || !pendingTasks.isEmpty()) {
			
			/* Check for finished threads. */
			AnalysisTask terminatedTask = terminatedAnalysisTaskQueue.poll();
			if (terminatedTask != null) {
				/* Handle terminated task. */
				numRunningThreads--;
				if (terminatedTask.getStatus() == AnalysisTask.TaskStatus.SUCCSESS) {
					if (config.getVerbosityLevel() > 1) {
						long startTime = System.currentTimeMillis();
						System.err.println("[" + (startTime / 1000) + "] " + "Finished '" + 
								terminatedTask.getFilename() + "' (" + terminatedTask.getVerbosename() + ").");
					}
					pipelineResults.add(terminatedTask.getQcPipelineResult());
				} else if (terminatedTask.getStatus() == AnalysisTask.TaskStatus.RETRY_LATER) {
					/* Task failed due to lack of available hash-map buckets. */
					if (config.pipesEnabled()) {
						fail("ERROR: Ran out of HashMap buckets while processing piped input.\n" + 
								"Please increase the JVM heap size (-Xms and -Xmx parameters) \n" + 
								"or reduce the number of threads (-t parameter). See --help for help.");
					} else {
						if (config.getVerbosityLevel() > 1) {
							long startTime = System.currentTimeMillis();
							System.err.println("[" + (startTime / 1000) + "] " + "Rescheduling '" + 
									terminatedTask.getFilename() + "' (" + terminatedTask.getVerbosename() + ").");
							System.err.println("             " + "The task will be retried later."); 
						}
						pendingTasks.add(terminatedTask);
						Collections.sort(pendingTasks);
					}
				} else if (terminatedTask.getStatus() == AnalysisTask.TaskStatus.FAIL_IO_ERROR) {
					fail("I/O Error, aborting.");
				} else if (terminatedTask.getStatus() == AnalysisTask.TaskStatus.FAIL_PARSE_ERROR) {
					fail("Error parsing FASTQ file, aborting.");
				} else if (terminatedTask.getStatus() == AnalysisTask.TaskStatus.FAIL_NOT_ENOUGH_MEMORY) {
					failLowHeapMem(terminatedTask.getFilename());
				} else {
					fail("INTERNAL ERROR: Unknown task status, aborting.");
				}
				continue;
			}
			
			/* Start the largest remaining QCPipeline that fits into the free memory. */
			boolean actionTakenThisIteration = false;
			if (!pendingTasks.isEmpty() && (numRunningThreads < config.getMaxNumThreads())) {
				Iterator<AnalysisTask> taskIterator = pendingTasks.iterator();
				while (taskIterator.hasNext()) {
					AnalysisTask task = taskIterator.next();
					long reservedBuckets = (long) (allocatedBuckets * Config.HM_BUCKET_REALLOCATION_RESERVE_FACTOR);
					if (task.getMinBucketsNeeded() <= (totalBuckets - reservedBuckets)) {

						/* When starting a task for the first time, try giving it a lot of memory. */
						int initialBuckets;
						long approxUncompressedFileSizeInMB = task.getFilesize() / (1024*1024);
						if (task.isGzipped()) {
							approxUncompressedFileSizeInMB *= Config.ESTIMATED_FASTQ_GZIP_COMPRESSION_FACTOR;
						}
						long recommendedBuckets = Config.RECOMMENDED_INIT_HM_CAPACITY_PER_MB_OF_FQ_INPUT * 
								approxUncompressedFileSizeInMB;
						if (recommendedBuckets > Integer.MAX_VALUE) {
							recommendedBuckets = Integer.MAX_VALUE;
						}
						if (task.firstAttempt && (recommendedBuckets <= (totalBuckets - reservedBuckets))) {
							initialBuckets = (int) recommendedBuckets; 
						} else {
							initialBuckets = task.getMinBucketsNeeded();
						}
						if (initialBuckets < task.getMinBucketsNeeded()) {
							initialBuckets = task.getMinBucketsNeeded();
						}
						
						/* Either way, start the task now. */
						taskIterator.remove();
						task.setStatus(AnalysisTask.TaskStatus.RUNNING);
						@SuppressWarnings("unused")
						AnalysisPipeline pipelineThread = task.start(this, initialBuckets, rnaTypeSearchEngine, cladeSearchEngine);
						//qcPipelineThreads.add(pipelineThread);
						numRunningThreads++;
						if (config.getVerbosityLevel() > 1) {
							long startTime = System.currentTimeMillis();
							System.err.println("[" + (startTime / 1000) + "] " + 
									"Starting '" + task.getFilename() + "' (" + task.getVerbosename() + ").");
							if (config.getVerbosityLevel() > 2) {
								if (initialBuckets == recommendedBuckets) {
									System.err.println("             " + "Allocated the recommended amount of memory.");
								} else {
									System.err.println("             " + "Allocated the minimal amount of memory. Reallocation will occur later if necessary.");
								}
							}
						}
						actionTakenThisIteration = true;
						break;
					}
				}
			}
			if (actionTakenThisIteration) {
				/* Speed up the program by not sleeping while there might be immediate work to do. */
				continue;
			}
			
			/* Test for lingering tasks that can't be started. */
			if ((numRunningThreads == 0) && !pendingTasks.isEmpty() && terminatedAnalysisTaskQueue.isEmpty()) {
				String taskFilename = pendingTasks.get(0).getFilename();
				fail("ERROR: Not enough memory to process '" + taskFilename + "'.\nPlease increase the JVM heap size (-Xms parameter).");
			}
			
			/* Let the main loop sleep a bit. */
			try {
				Thread.sleep(Config.THREAD_MANAGER_LOOP_ITERATION_DELAY);
			} catch (InterruptedException e) {
				e.printStackTrace();
				System.exit(-1);
			}
		}
		return pipelineResults;	
	}	

}

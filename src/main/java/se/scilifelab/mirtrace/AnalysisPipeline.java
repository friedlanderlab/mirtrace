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

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.ArrayUtils;

/**
 * @author Yrin Eldfjell
 *
 */
class AnalysisPipeline extends Thread {
	
	@SuppressWarnings("serial")
	class HashMapFullException extends Exception { };

	/* Constants. */
	static final int LINE_FEED = 10;
	static final int ADAPTER_NOT_FOUND = -1;
	
	/* Member variables. */
	QCStatistics qcStatistics;
	DNASequenceHashMap hm;
	byte[] adapterSequence = new byte[0];
	Config config;
	RNATypeSearchEngine rnaTypeSearchEngine;
	CladeSearchEngine cladeSearchEngine;	
	List<String> warnings = new ArrayList<String>();
	AnalysisTask analysisTask;
	AnalysisTaskManager taskManager;
	Integer phredOffset;

	AnalysisPipeline(AnalysisTaskManager taskManager, AnalysisTask thisTask, RNATypeSearchEngine rnaTypeSearchEngine,
			CladeSearchEngine cladeSearchEngine, int initialBuckets, Integer phredOffset) {
		this.config = taskManager.config;
		this.analysisTask = thisTask;
		this.rnaTypeSearchEngine = rnaTypeSearchEngine;
		this.cladeSearchEngine = cladeSearchEngine;
		this.taskManager = taskManager;
		this.phredOffset = phredOffset;
		if (thisTask.getAdapter() != null) {
			this.adapterSequence = thisTask.getAdapter().getBytes();
		} else if (config.getAdapterSequence() != null) {
			this.adapterSequence = config.getAdapterSequence().getBytes();
		}
		
		if (adapterSequence.length == 0) {
			warnings.add("No adapter specified for sample '" +  thisTask.getFilename() + 
					"'. No adapter trimming will be performed.");
		} 

		this.qcStatistics = new QCStatistics();
		thisTask.bucketsAllocated = initialBuckets;
	}

	public void run() {
		try {
			AnalysisPipelineResult pipelineResult = runPipeline();
			analysisTask.setQcPipelineResult(pipelineResult);
			analysisTask.setStatus(AnalysisTask.TaskStatus.SUCCSESS);
		} catch (HashMapFullException e) {
			analysisTask.setStatus(AnalysisTask.TaskStatus.RETRY_LATER);
		} catch (IOException e) {
			System.err.println(e.getMessage());
			analysisTask.setStatus(AnalysisTask.TaskStatus.FAIL_IO_ERROR);
		} catch (FASTQParseException e) {
			System.err.println(e.getMessage());
			analysisTask.setStatus(AnalysisTask.TaskStatus.FAIL_PARSE_ERROR);
		} catch (FASTQAutoDetectionException e) {
			System.err.println(e.getMessage());
			analysisTask.setStatus(AnalysisTask.TaskStatus.FAIL_PARSE_ERROR);
		} catch (OutOfMemoryError e) {
			System.err.println(e.getMessage());
			System.err.println("A processing thread ran out of memory. " + 
					"miRTrace has its memory limits set so that this " + 
					"shouldn't happen. But it did. \n" + 
					"Please consider increasing the hidden option " + 
					"--global-mem-reserve. Values must be given in bytes. \n" + 
					"The current (too low) value is " + 
					config.getGlolbalMemoryReserve() + 
					". \n\nBest of luck and our apologies.");
			System.exit(-1);
		} finally {
			for (String w : warnings) {
				analysisTask.addWarning(w);
			}
			hm = null;
			taskManager.decreaseAllocatedBuckets(analysisTask.getBucketsAllocated());
			analysisTask.setBucketsAllocated(0);
			taskManager.addTerminatedTask(analysisTask);
		}
	}
	
	AnalysisPipelineResult runPipeline() throws 
			HashMapFullException, IOException, FASTQParseException, FASTQAutoDetectionException {
		
		/* Perform auto-detections. */
		if (!config.pipesEnabled()) {
			boolean autoDetectPhred = (phredOffset == null);
			FASTQAutoDetector autoDetector = new FASTQAutoDetector(
					autoDetectPhred,
					false, //config.autodetectAdapter(),
					config
			);
			FASTQAutoDetector.FASTQFormat fastqFormat = autoDetector.autoDetectSampleFormat(analysisTask.getFilename());
			if (autoDetectPhred) {
				phredOffset = fastqFormat.phredOffset.intValue();
			}
		}

		/* Run pipeline. */
		this.hm = new DNASequenceHashMap(analysisTask.bucketsAllocated);
		parseSequenceFile(analysisTask.getFilename(), phredOffset);		
		if (hm.getSeqCountOverflowWarning()) {
			warnings.add("WARNING: Read counts truncated past 2G. Sample: " + analysisTask.getVerbosename());
		}
		processCompletedHashmap();
		if (config.qcMode()) {
			generateQCWarnings();
		}
		File originalFastQFile = new File(analysisTask.getFilename());
		AnalysisPipelineResult pipelineResult = new AnalysisPipelineResult(
				qcStatistics,
				analysisTask.getVerbosename(),
				originalFastQFile.getName(),
				new String(adapterSequence, "UTF-8"),
				analysisTask.getFilesize(),
				originalFastQFile.lastModified(),
				analysisTask.getDisplayOrder()
		);
		return pipelineResult;
	}
	
	/** Finds the position of the adapter and the new sequence length,
	 * or ADAPTER_NOT_FOUND if no adapter was found. Returns the leftmost match.
	 */
	int findLeftmostAdapterExact(byte[] seq, int seqLen, byte[] adapterSequence) {
		int si; // Sequence index
		int ai; // Adapter index
		//int siInitial = seqLen - 1;
		int siInitial = 0 + adapterSequence.length - 1;
		final int AI_INITIAL = adapterSequence.length - 1;
		ai = AI_INITIAL;
		si = siInitial;
		while ((si >= 0) && (ai >= 0) && (si <= seqLen - 1)) {
			if (seq[si] == adapterSequence[ai]) {
				if (ai == 0) {
					/* Complete adapter found. */
					return si;
				} else {
					ai--;
					si--;
				}
			} else {
				/* Mismatch: reset search */
				ai = AI_INITIAL;
				siInitial++;
				si = siInitial;
			}
		}
		return ADAPTER_NOT_FOUND;
	}
	
	/** Finds the position of the adapter and the new sequence length,
	 * or ADAPTER_NOT_FOUND if no adapter was found. Returns the rightmost match.
	 */
	int findAdapterExact(byte[] seq, int seqLen, byte[] adapterSequence) {
		int si; // Sequence index
		int ai; // Adapter index
		int siInitial = seqLen - 1;
		final int AI_INITIAL = adapterSequence.length - 1;
		ai = AI_INITIAL;
		si = siInitial;
		while ((si >= 0) && (ai >= 0)) {
			if (seq[si] == adapterSequence[ai]) {
				if (ai == 0) {
					/* Complete adapter found. */
					return si;
				} else {
					ai--;
					si--;
				}
			} else {
				/* Mismatch: reset search */
				ai = AI_INITIAL;
				siInitial--;
				si = siInitial;
			}
		}
		return ADAPTER_NOT_FOUND;
	}
	
	/* findAdapter 
	 * First tries to find the position of the exact adapter string.
	 * If that fails, it then looks for successively shorter prefixes
	 * of the adapter string at the very end of the sequence string.
	 * 
	 * The purpose of this is to find truncated adapters.
	 * 
	 * If no adapter is found at all, ADAPTER_NOT_FOUND is returned.
	 */
	int findAdapter(byte[] seq, int seqLen, byte[] adapterSequence) {
		int exactPos = findAdapterExact(seq, seqLen, adapterSequence);
		if (exactPos != ADAPTER_NOT_FOUND) {
			return exactPos;
		} else {
			for (int adapterPrefixLen = adapterSequence.length - 1; adapterPrefixLen >= 1; adapterPrefixLen--) {
				int ai = adapterPrefixLen - 1;
				int si = seqLen - 1;
				while ((si >= 0) && (ai >= 0)) {
					if (seq[si] == adapterSequence[ai]) {
						if (ai == 0) {
							/* Complete adapter found. */
							return si;
						} else {
							ai--;
							si--;
						}
					} else {
						break;
					}
				}
			}
		}
		return ADAPTER_NOT_FOUND;
	}
	

	/** Tests if the sequence has sufficiently high complexity.
	 * Fails if either the most common nucleotide comprises more than 90% of
	 * the sequence, or a single nucleotide is repeated for at least 50% of the
	 * sequence length.
	 * @param sequence The sequence to test.
	 * @return true if sequence has valid complexity.
	 */
	boolean validComplexity(byte[] sequence, int seqLen) {
		int a = 0;
		int c = 0;
		int g = 0;
		int t = 0;
		int i;
		int allowedRepeatLength = seqLen / 2;
		int allowedIdenticalCount = (int) (seqLen * 0.9);
		int currentRepeatLength = 0;
		byte prevNucleotide = 0;
		for (i = 0; i < seqLen; i++) {
			/* Repeat test */
			if (sequence[i] == prevNucleotide) {
				currentRepeatLength++;
			} else {
				currentRepeatLength = 0;
			}
			if (currentRepeatLength > allowedRepeatLength) {
				return false;
			}
			
			/* Counts for total fraction test */
			if (sequence[i] == 'A') {
				a++;
			} else if (sequence[i] == 'C') {
				c++;
			} else if (sequence[i] == 'G') {
				g++;
			} else if (sequence[i] == 'T') {
				t++;
			}
			prevNucleotide = sequence[i];
		}
		if ((a > allowedIdenticalCount) ||
				(c > allowedIdenticalCount) || 
				(g > allowedIdenticalCount) || 
				(t > allowedIdenticalCount)) {
			return false;
		}
		return true;
	}
	
	void parseSequenceFile(String seqFilename, int phredOffset) throws HashMapFullException, FASTQParseException {
		FASTQParser parser = new FASTQParser(seqFilename, config);
		byte[] sequence = new byte[Config.READ_LENGTH_CUTOFF];
		byte[] phredScores = new byte[Config.READ_LENGTH_CUTOFF];
		int seqLen;
		int i;
		byte tempCh;
		int phredFailCount;
		byte ch;
		boolean containsInvalidNucleotides;
		long debugLastReportTime = System.currentTimeMillis();
		long debugInsertCounter = 0;

		for (int readNumber=0;; readNumber++) {
			
			/* Show debug stats, e.g. hashmap insert speed. */
			if (config.getVerbosityLevel() > 2) {
				if (debugInsertCounter % 5000 == 0) {
					long curTime = System.currentTimeMillis();
					long duration = curTime - debugLastReportTime;
					if (duration >= Config.DEBUG_OUTPUT_UPDATE_INTERVAL) {
						Double insertsPerSec = ((double) debugInsertCounter ) / ((double) duration / 1000);
						Double fillFactor = hm.getFillFactor();
						System.err.println("[" + (curTime / 1000) + "] " + "parseSequenceFile: Making " + 
								String.format("%.0f", insertsPerSec) + " inserts/sec from file:");
						System.err.println("             " + seqFilename);
						System.err.println("             " + "Reads processed = " + 
								readNumber);
						System.err.println("             " + "Fill factor = " + 
								String.format("%.2f", fillFactor));
						debugInsertCounter = 0;
						debugLastReportTime = curTime;
						System.err.println("");
					}
				}
			}
			
			/* Fetch next entry. */
			seqLen = parser.getNextEntry(sequence, phredScores);
			if (seqLen == FASTQParser.SEQ_FILE_EOF) {
				break;
			}
			if (seqLen > Config.READ_LENGTH_CUTOFF) {
				seqLen = Config.READ_LENGTH_CUTOFF;
			}
			qcStatistics.allSeqsCount++;
			containsInvalidNucleotides = false;
			
			/* Convert PHRED ASCII to PHRED score. */
			for (i = 0; i < seqLen; i++) {
				tempCh = (byte) (phredScores[i] - phredOffset);
				if ((tempCh < Config.MIN_ALLOWED_PHRED) || 
						(tempCh > Config.MAX_ALLOWED_PHRED)) {
					throw new FASTQParseException(seqFilename + ": PHRED score not in interval " + 
							Config.MIN_ALLOWED_PHRED + 
							".." + Config.MAX_ALLOWED_PHRED + ". Has the " + 
							"PHRED offset (33 or 64) been correctly specified? Could also be an invalid FASTQ file.");
				}
				/* Map the PHRED score onto the interval 0..42.
				 * Purpose: a more consistent output. */
				 if (tempCh < Config.MIN_PHRED_REPORTED) {
					tempCh = Config.MIN_PHRED_REPORTED;
				} else if (tempCh > Config.MAX_PHRED_REPORTED) {
					tempCh = Config.MAX_PHRED_REPORTED;
				}
				phredScores[i] = tempCh;
			}
			
			/* Test PHRED score. */
			phredFailCount = 0;
			for (i = 0; i < seqLen; i++) {
				/* First store PHRED score statistics - this is *always* done. */
				qcStatistics.statsNucleotidePhredScores[phredScores[i] - Config.MIN_PHRED_REPORTED]++;
				if (phredScores[i] < Config.BAD_QUALITY_PHRED_CUTOFF) {
					phredFailCount++;
				}
				/* Upper-case letter */
				sequence[i] &= 0b11011111;
			}
			if (((double) phredFailCount / seqLen) > Config.ACCEPTABLE_BAD_PHRED_FRACTION) {
				qcStatistics.statsQC[QCStatistics.QC_STATUS_INVALID]++;
				qcStatistics.storeSequenceLen(seqLen);
				continue;
			}

			/* Trim adapter. */
			boolean adapterDetected = false;
			if ((config.getSeqProtocol().equals("illumina")) || (config.getSeqProtocol().equals("qiaseq"))) {
				int seqLenTrimmed = findAdapter(sequence, seqLen, adapterSequence);
				adapterDetected = (seqLenTrimmed != ADAPTER_NOT_FOUND);
				if (adapterDetected) {
					adapterDetected = ((seqLen - seqLenTrimmed) >= adapterSequence.length);
					seqLen = seqLenTrimmed;
				}
			} else if (config.getSeqProtocol().equals("cats")) {
				/* For CATS-seq, first trim the 3 first letters. */
				int OFFSET = 3;
				for (i = 0; i < (seqLen - OFFSET); i++) {
					sequence[i] = sequence[i + OFFSET];
				}
				seqLen = seqLen - OFFSET;
				/* Then trim the adapter. First look for the left-most exact match. */
				int seqLenTrimmed = findLeftmostAdapterExact(sequence, seqLen, adapterSequence);
				if (seqLenTrimmed == ADAPTER_NOT_FOUND) {
					/* If not found, try regular right-most search. */
					seqLenTrimmed = findAdapter(sequence, seqLen, adapterSequence);
				}
				adapterDetected = (seqLenTrimmed != ADAPTER_NOT_FOUND);
				if (adapterDetected) {
					adapterDetected = ((seqLen - seqLenTrimmed) >= adapterSequence.length);
					seqLen = seqLenTrimmed;
					
					/* Finally, if the adapter was detected, trim the poly-A tail.*/
					/* NOT USED IN CURRENT VERSION AS WE SEARCH FOR THE POLY-A TAIL DIRECTLY.
					 *
					for (i = seqLen - 1; (i >= 0) && (sequence[i] == 'A'); i--) {
						seqLen--;
					}
					*/
				}
			} else if (config.getSeqProtocol().equals("nextflex")) {
				/* For NEXTflex-seq, first trim the 4 first letters. */
				int OFFSET = 4;
				for (i = 0; i < (seqLen - OFFSET); i++) {
					sequence[i] = sequence[i + OFFSET];
				}
				seqLen = seqLen - OFFSET;
				/* Then trim the adapter. */
				int seqLenTrimmed = findAdapter(sequence, seqLen, adapterSequence);
				adapterDetected = (seqLenTrimmed != ADAPTER_NOT_FOUND);
				if (adapterDetected) {
					adapterDetected = ((seqLen - seqLenTrimmed) >= adapterSequence.length);
					seqLen = seqLenTrimmed;
					/* Finally, if the adapter was detected, trim the last 4 letters.*/
					int trimmed = 0;
					for (i = seqLen - 1; (i >= 0) && (trimmed < 4); i--, trimmed++) {
						seqLen--;
					}
				}
			}
			
			/* Test for ambiguous/invalid nt's */
			for (i = 0; i < seqLen; i++) {
				ch = sequence[i];
				if ((ch == 'A') || (ch == 'C') || (ch == 'G') || (ch == 'T')) {
					// OK
				} else {
					containsInvalidNucleotides = true;
					// We don't break here since we want to process the stats for all nt's.
				}
			}
			if (containsInvalidNucleotides) {
				qcStatistics.statsQC[QCStatistics.QC_STATUS_INVALID]++;
				qcStatistics.storeSequenceLen(seqLen);
				continue;
			}
			
			/* Calculate the remaining statistics on the aggregated
			 * hash map objects to save time.
			 */			
			
			if (!hm.putSequence(sequence, seqLen, adapterDetected, readNumber, 1)) {
				/* Attempt to resize hash-map. */
				int newBucketTarget = (int) (analysisTask.getBucketsAllocated() * 
						Config.HM_BUCKET_REALLOCATION_INCREASE_FACTOR);
				int newMinBucketsNeeded = (int) (analysisTask.getBucketsAllocated() * 
						Config.HM_BUCKET_MIN_BUCKETS_NEEDED_INCREASE_FACTOR);
				int currentBucketCount = analysisTask.getBucketsAllocated();
				if (newMinBucketsNeeded * Config.HM_BUCKET_REALLOCATION_INCREASE_FACTOR > 
						taskManager.getTotalBuckets()) {
					
					/* If the new minBucketsNeeded is so high that it cannot be reallocated, 
					 * set it to the max possible buckets right away instead. */
					if (taskManager.getTotalBuckets() > Integer.MAX_VALUE) {
						newMinBucketsNeeded = Integer.MAX_VALUE;
					} else {
						newMinBucketsNeeded = (int) taskManager.getTotalBuckets();
					}
				}
				analysisTask.setMinBucketsNeeded(newMinBucketsNeeded);
				if (taskManager.requestBucketReallocation(currentBucketCount, newBucketTarget, analysisTask)) {
					analysisTask.setBucketsAllocated(newBucketTarget);
					reallocateHashmap(newBucketTarget);
					taskManager.decreaseAllocatedBuckets(currentBucketCount);
					hm.putSequence(sequence, seqLen, adapterDetected, readNumber, 1);
				} else {
					throw new HashMapFullException();
				}
			}
			
			/* Update stats for debug output. */
			debugInsertCounter++;
		}
		parser.close(); 
	}

	private void reallocateHashmap(int newBucketTarget) {
		long startTime = System.currentTimeMillis();
		if (config.getVerbosityLevel() > 1) {
			System.err.println("[" + (startTime / 1000) + "] " + "Reallocating HashMap for sample '" 
					+ analysisTask.getFilename() + "'.");
		}
		int oldBucketCount = hm.getCapacity();
		hm.initIterator();
		DNASequenceHashMap newHM = new DNASequenceHashMap(newBucketTarget);
		DNASequenceHashMapEntry entry = new DNASequenceHashMapEntry();
		while (hm.iteratorHasNext()) {
			hm.iteratorFetchNext(entry);
			newHM.putSequence(entry.getSeq(), entry.getLength(), 
					entry.getAdapterDetected(), entry.getFirstDetectedDepth(), entry.getCount());
		}
		hm = newHM;
		long endTime = System.currentTimeMillis();
		if (config.getVerbosityLevel() > 1) {
			System.err.println("[" + (endTime / 1000) + "] " + "Reallocated HM from " + oldBucketCount + " to " + 
					newBucketTarget + " in " + (endTime - startTime) + " ms.");
			System.err.println("             " + analysisTask.getFilename());
		}
	}

	void writeToFastaFile(byte[] seq, int seqNum, int count, String rnatype, 
			String cladeId, String cladeFamilyId, int seqLen, BufferedOutputStream f, 
			boolean collapsed) throws IOException {
		int uncollapse_count = count;
		if (collapsed) {
			uncollapse_count = 1;
		}
		for (int d=0; d < uncollapse_count; d++) {
			/* Seq id (including read count). */
			f.write('>');
			f.write('s');
			f.write('e');
			f.write('q');
			f.write('_');
			f.write(String.valueOf(seqNum).getBytes());
			if (collapsed) {
				f.write('_');
				f.write('x');
				f.write(String.valueOf(count).getBytes());
			}
			if (!collapsed) {
				f.write('_');
				f.write(String.valueOf(d).getBytes());
			}
			
			/* RNA type. */
			f.write(' ');
			f.write('r');
			f.write('n');
			f.write('a');
			f.write('t');
			f.write('y');
			f.write('p');
			f.write('e');
			f.write(':');
			f.write(rnatype.getBytes());
	
			/* Clade. */
			if (cladeId != null) {
				f.write(' ');
				f.write('c');
				f.write('l');
				f.write('a');
				f.write('d');
				f.write('e');
				f.write(':');
				f.write(cladeId.getBytes());
			}
			/* Clade family. */
			if (cladeFamilyId != null) {
				f.write(' ');
				f.write('f');
				f.write('a');
				f.write('m');
				f.write('i');
				f.write('l');
				f.write('y');
				f.write('_');
				f.write('i');
				f.write('d');
				f.write(':');
				f.write(cladeFamilyId.getBytes());
			}
			f.write(LINE_FEED);
			
			/* DNA seq. */
			f.write(seq, 0, seqLen);
			f.write(LINE_FEED);
		}
	}
	
	void processCompletedHashmap() throws IOException {
		int seqNumUniqueReads = 0;
		int seqNumUnmappedReads = 0;
		int seqLen;
		int seqCount;
		boolean seqOK;
		
		/* Final output filenames. */
		File uniqueReadsFASTAFile = new File(config.getUniqueReadsOutputDirectory(), 
				analysisTask.getFASTAFilenameBase() + ".fasta");
		File unmappedReadsFASTAFile = new File(config.getUnmappedReadsOutputDirectory(), 
				analysisTask.getFASTAFilenameBase() + ".fasta");

		BufferedOutputStream fUniqueReads = null;
		BufferedOutputStream fUnmappedReads = null;
		Map<Integer,Integer> lowestSeqDetectedLocations = new TreeMap<Integer,Integer>();
		Pattern RE_MIRBASE_ID = Pattern.compile("^([^-]+-miR-?[^-]+).*?$");

		if (config.writeFASTA()) {
			fUniqueReads = new BufferedOutputStream(new 
					FileOutputStream(uniqueReadsFASTAFile));
			if (config.qcMode()) {
				fUnmappedReads = new BufferedOutputStream(new 
						FileOutputStream(unmappedReadsFASTAFile));
			}
		}
		hm.initIterator();
		DNASequenceHashMapEntry entry = new DNASequenceHashMapEntry();
		while (hm.iteratorHasNext()) {
			hm.iteratorFetchNext(entry);
			seqLen = entry.getLength();
			seqCount = entry.getCount();
			seqOK = false;

			/* Quality filter and store statistics. */
			qcStatistics.storeSequenceLen(seqLen, seqCount);
			if (validComplexity(entry.getSeq(), seqLen)) {
				if (seqLen < Config.MIN_ALLOWED_SEQ_LEN) {
					qcStatistics.statsQC[QCStatistics.QC_STATUS_VALID_COMPLEXITY] += seqCount;
				} else {
					/* All tests passed */
					seqOK = true;
					if (entry.getAdapterDetected()) {
						qcStatistics.statsQC[QCStatistics.QC_STATUS_VALID_LENGTH_AND_ADAPTER] += seqCount;
					} else {
						qcStatistics.statsQC[QCStatistics.QC_STATUS_VALID_LENGTH] += seqCount;	
					}
				}
			} else {
				qcStatistics.statsQC[QCStatistics.QC_STATUS_VALID_QUALITY] += seqCount;
			}
			if (seqOK) {
				qcStatistics.uniqueQCPassedSeqsCount++;
				String matchingCategory = "not_mapped";
				if (config.qcMode()) {
					/* Identify RNA type. */
					matchingCategory = rnaTypeSearchEngine.search(entry.getSeq(), seqLen);
					
					if (matchingCategory == null) {
						matchingCategory = "unknown";
					}
					if (matchingCategory.equals("mirna")) {
						qcStatistics.statsRNAType[Config.RNA_TYPE_MI_RNA] += seqCount;
						/* Read depth stats now stored in the method below. */
						rnaTypeSearchEngine.processRefSeqIdSearches(Config.RNA_TYPE_MI_RNA, entry, qcStatistics, lowestSeqDetectedLocations);
					} else if (matchingCategory.equals("rrna")) {
						qcStatistics.statsRNAType[Config.RNA_TYPE_R_RNA] += seqCount;
						rnaTypeSearchEngine.processRefSeqIdSearches(Config.RNA_TYPE_R_RNA, entry, qcStatistics, null);
					} else if (matchingCategory.equals("trna")) {
						qcStatistics.statsRNAType[Config.RNA_TYPE_T_RNA] += seqCount;
						rnaTypeSearchEngine.processRefSeqIdSearches(Config.RNA_TYPE_T_RNA, entry, qcStatistics, null);
					} else if (matchingCategory.equals("artifacts")) {
						qcStatistics.statsRNAType[Config.RNA_TYPE_ART_RNA] += seqCount;
						rnaTypeSearchEngine.processRefSeqIdSearches(Config.RNA_TYPE_ART_RNA, entry, qcStatistics, null);
					} else {
						qcStatistics.statsRNAType[Config.RNA_TYPE_UNKNOWN] += seqCount;
					}
				}
				/* Identify matching clade. */
				CladeSearchEngine.CladeSearchResult cladeResult;
				cladeResult = cladeSearchEngine.search(entry.getSeq(), seqLen);
				if (cladeResult != null) {
					qcStatistics.storeFoundCladeFamily(cladeResult.cladeFound, cladeResult.familyId);
					qcStatistics.statsClades[cladeResult.cladeFound] += seqCount;
					
					/* Inquire the database for all mature hairpin entries that matches the current seq prefix exactly. */
					List<String> foundRawmiRBaseIds = cladeSearchEngine.findAllMiRBaseEntries(entry.getSeq(), seqLen);

					boolean countStored = false;
					for (String e : foundRawmiRBaseIds) {
						Matcher m = RE_MIRBASE_ID.matcher(e);
						if (!m.matches()) {
							throw new RuntimeException("Internal error in clade database: invalid seq record id.");
						}
						String miRBaseId = m.group(1);
						String usedSeqPrefix = new String(
								ArrayUtils.subarray(entry.getSeq(), 0, Config.CLADE_DB_SEQ_LEN_CUTOFF),
								"UTF-8"
						);
						CladeFamilyRecord r;
						if (qcStatistics.hasCladeSpecificmiRBaseEntry(usedSeqPrefix)) {
							r = qcStatistics.getCladeSpecificmiRBaseEntry(usedSeqPrefix);
						} else {
							r = new CladeFamilyRecord(usedSeqPrefix, cladeResult.cladeFound);
							qcStatistics.storeCladeSpecificmiRBaseEntries(usedSeqPrefix, r);
						}
						r.putmiRBaseId(miRBaseId);
						if (!countStored) {
							r.increaseCount(seqCount);
							countStored = true;
						}
					}
				}
				
				/* Write sequence to FASTA output */
				if (config.writeFASTA()) {
					String cladeIdentifier = null;
					String cladeFamilyId = null;
					if (cladeResult != null) {
						cladeIdentifier = cladeResult.cladeString;
						cladeFamilyId = cladeResult.familyId;
					}
					
					/* Write to "all" file. */
					writeToFastaFile(entry.getSeq(), seqNumUniqueReads, entry.getCount(), 
							matchingCategory, cladeIdentifier, cladeFamilyId, seqLen, fUniqueReads, config.writeCollapsedFASTA());
					seqNumUniqueReads++;
					
					if (config.qcMode()) {
						/* If unknown rna type, write to unmapped file. */
						if (matchingCategory == "unknown") {
							writeToFastaFile(entry.getSeq(), seqNumUnmappedReads, entry.getCount(),
									matchingCategory, cladeIdentifier, cladeFamilyId, seqLen, fUnmappedReads, config.writeCollapsedFASTA());
							seqNumUnmappedReads++;
						}
					}
				}
			}
		}

		if (config.writeFASTA()) {
			fUniqueReads.close();
			if (config.qcMode()) {
				fUnmappedReads.close();
			}
		}
	
		qcStatistics.storeComplexity(0);
		for (Map.Entry<Integer, Integer> detectedLocation : lowestSeqDetectedLocations.entrySet()) {
			if (detectedLocation.getKey() != null) {
				//long readDepth = (long) (qcStatistics.getAllSeqsCount() * detectedLocation.getValue()); 
				//qcStatistics.storeComplexity(readDepth);
				qcStatistics.storeComplexity(detectedLocation.getValue());
			}
		}
	}
	
	void generateQCWarnings() {
		String status;
		
		/* PHRED */
		double accumulatedNtsAboveThres = 0;
		long totalNts = 0;
		
		for (int phredRaw=0; phredRaw < QCStatistics.phredScoreArraySize; phredRaw++) {
			long ntCount = qcStatistics.getPhredStats()[phredRaw];
			int phredScore = phredRaw + Config.MIN_PHRED_REPORTED;
			if (phredScore >= Config.QC_FLAG_THRESHOLD_PHRED) {
				accumulatedNtsAboveThres += ntCount;
			}
			totalNts += ntCount;
		}
		if (totalNts > 0) {
			status = "ok";
			double okPhredFrac = (accumulatedNtsAboveThres / totalNts); 
			if (okPhredFrac < Config.QC_FLAG_FRAC_PHRED_BAD) {
				status = String.format((Locale) null, "%.1f%% of nt's have OK PHRED score.", okPhredFrac * 100);
			}
			qcStatistics.storeQCAnalysisFlag("phred", status);
		}
		
		/* LENGTH */
		double okReads = 0;
		long totalReads = 0;

		for (int length=0; length < qcStatistics.getLengthStats().length; length++) {
			long readCount = qcStatistics.getLengthStats()[length];
			if ((length >= Config.QC_FLAG_MIN_OK_LEN) &&
				(length <= Config.QC_FLAG_MAX_OK_LEN)) {
					okReads += readCount;
				}
			totalReads += readCount;
		}
		if (totalReads > 0) {
			status = "ok";
			double okReadsFrac = okReads / totalReads;
			if (okReadsFrac < Config.QC_FLAG_FRAC_LENGTH_BAD) {
				status = String.format((Locale) null, "%.1f%% of reads have a valid length.", okReadsFrac * 100);
			}
			qcStatistics.storeQCAnalysisFlag("length", status);
		}
		
		/* QC STATS */
		long[] a = qcStatistics.getQCStats();
		double retainedReadCount = a[QCStatistics.QC_STATUS_VALID_LENGTH] + 
				a[QCStatistics.QC_STATUS_VALID_LENGTH_AND_ADAPTER];
		long allQCReads = 0;
		for (long c : a) {
			allQCReads += c;
		}
		if (allQCReads > 0) {
			double retainedFrac = retainedReadCount / allQCReads;
			status = "ok";
			if (retainedFrac < Config.QC_FLAG_FRAC_QC_BAD) {
				status = String.format((Locale) null, "%.1f%% of reads passed QC test.", retainedFrac * 100);
			}
			qcStatistics.storeQCAnalysisFlag("qc", status);
		}
		
		/* RNA TYPE */
		long[] rt = qcStatistics.getRNATypeStats();
		double miRNAReadCount = rt[Config.RNA_TYPE_MI_RNA];
		long allRNATypeReads = 0;
		for (long c : rt) {
			allRNATypeReads += c;
		}
		if (allRNATypeReads > 0) {
			double miRNAFrac = miRNAReadCount / allRNATypeReads;
			status = "ok";
			if (miRNAFrac < Config.QC_FLAG_FRAC_RNATYPE_BAD) {
				status = String.format((Locale) null, "%.1f%% of reads are miRNAs.", miRNAFrac * 100);
			}	
			qcStatistics.storeQCAnalysisFlag("rnatype", status);
		}
		
		/* COMPLEXITY */
		int found = qcStatistics.getFoundRNAReads(Config.RNA_TYPE_MI_RNA).size();
		if (found < (Config.QC_FLAG_COMPLEXITY_MIN_GENES_REQUIRED_PERCENT * 
				rnaTypeSearchEngine.getRefSeqCounts(Config.RNA_TYPE_MI_RNA))) {
			double miRNAFoundFrac = found / rnaTypeSearchEngine.getRefSeqCounts(Config.RNA_TYPE_MI_RNA);
			status = String.format((Locale) null, "%.1f%% of miRNAs in db detected.", miRNAFoundFrac * 100);
			qcStatistics.storeQCAnalysisFlag("complexity", status);
		} else {
			qcStatistics.storeQCAnalysisFlag("complexity", "ok");
		}
		
		/* CONTAMINATION */
			/* TODO FIXME Contamination warnings currently not implemented. */
	}
	
	
}

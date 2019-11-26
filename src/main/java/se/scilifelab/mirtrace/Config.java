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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import com.opencsv.CSVReader;
/*
 * @author Yrin Eldfjell
 * 
 */
class Config {

	static final int MIN_JAVA_VERSION_REQUIRED_MAJOR = 1;
	static final int MIN_JAVA_VERSION_REQUIRED_MINOR = 7;
	
	/* RNA type constants. */
	static final int RNA_TYPE_MI_RNA = 0;
	static final int RNA_TYPE_R_RNA = 1;
	static final int RNA_TYPE_T_RNA = 2;
	static final int RNA_TYPE_ART_RNA = 3;
	static final int RNA_TYPE_UNKNOWN = 4;
	static final int NUMBER_OF_RNA_CLASSES = 5;
	
	/* QC pipeline constants. */
	static final int MIN_ALLOWED_SEQ_LEN = 18;
	static final int BAD_QUALITY_PHRED_CUTOFF = 20;
	static final double ACCEPTABLE_BAD_PHRED_FRACTION = 0.5;
	
	/* Always trim reads to be at most READ_LENGTH_CUTOFF long. */
	static final int READ_LENGTH_CUTOFF = 50;
	
	/* ADAPTER_USABLE_PREFIX_LENGTH is the number of nt's of the adapter 
	 * sequence to keep. The rest is just discarded. */
	static final int ADAPTER_USABLE_PREFIX_LENGTH = 8;
	
	/* PHRED scores outside the range given by the constants below will 
	 * be converted to the nearest value inside the range. */
	static final int MIN_PHRED_REPORTED = 0;
	static final int MAX_PHRED_REPORTED = 42; 
	
	/* Absolute limits for the PHRED scores. */
	/* Rationale: FASTX-Toolkit uses -15..93 as interval:
	 * https://github.com/lianos/fastx-toolkit/blob/master/fastx/src/libfastx/fastx.c */
	static final int MIN_ALLOWED_PHRED = -15;
	static final int MAX_ALLOWED_PHRED = 93;
	
	/* WARNING: The CLADE_DB_SEQ_LEN_CUTOFF constant MUST MATCH 
	 * 			the corresponding constants in "make_clade_databases.py". */
	static final int CLADE_DB_SEQ_LEN_CUTOFF = 20;
	static final int ARTIFACTS_MAPPER_SLIDING_WINDOW_SIZE = 18;
	
	/* Command-line UI texts. */
	static final String MIRTRACE_VERSION = "1.0.1";
	static final String CITATION_TEXT = "Thank you for using miRTrace. Please cite our corresponding paper as:\n\n" + 
				"Kang W; Eldfjell Y; Fromm B; Estivill X; Biryukova I; Friedländer MR, 2018. miRTrace reveals the organismal origins of microRNA sequencing data. Genome Biol 19(1):213\n\n";

	
	/* Database related constants. */
	static final String[] CLADES = {
		"bryophytes",
		"lycopods",
		"gymnosperms",
		"monocots",
		"dicots",
		
		"sponges",
		
		"nematode",
		"insects",
		"lophotrochozoa",

		"echinoderms",
		
		"fish",
		"birds_reptiles",

		"rodents",
		"primates"
	};
	static final String ALL_SPECIES_TAG = "meta_species_all";
	
	/* Memory and threading constants. */
	static final int MIN_INITIAL_HASHMAP_CAPACITY = 10000;
	
	static final int HM_ARRAY_SHARD_BITCOUNT = 5; // Number of HM shards will be 2^ARRAY_SHARD_BITCOUNT.
	static final int HM_LOOKUP_TABLE_EXPANSION_FACTOR = 4;
	private static final int HM_ARRAY_BYTES_PER_BUCKET = ((READ_LENGTH_CUTOFF + 31) / 32) * 8;
	private static final int HM_BYTES_FOR_OTHER_FIELDS_PER_BUCKET = 10;
	private static final int HM_BYTES_FOR_SORTED_INDEX = 2*4; /* Java creates an extra array during sorting. */
	static final int HM_MEMORY_USAGE_PER_HASH_BUCKET = (
			HM_ARRAY_BYTES_PER_BUCKET +
			HM_BYTES_FOR_OTHER_FIELDS_PER_BUCKET + 
			HM_BYTES_FOR_SORTED_INDEX +
			(4*HM_LOOKUP_TABLE_EXPANSION_FACTOR)
	);
	static final int THREAD_MANAGER_LOOP_ITERATION_DELAY = 50; /* In ms. */
	static final int MAX_INPUT_FILES = 100000; /* Should be set very high. */
	
	/* Estimate that other threads will need this many more buckets to finish. */
	static final double HM_BUCKET_REALLOCATION_RESERVE_FACTOR = 1.7; 
	
	/* Increase factor when reallocating due to too high load factor in HM. */
	static final double HM_BUCKET_REALLOCATION_INCREASE_FACTOR = 1.5; 
	
	/* Increase factor to use when restarting a cancelled job. */
	static final double HM_BUCKET_MIN_BUCKETS_NEEDED_INCREASE_FACTOR = 1.5; 
	
	static final int RECOMMENDED_INIT_HM_CAPACITY_PER_MB_OF_FQ_INPUT = 700;
	static final int ESTIMATED_FASTQ_GZIP_COMPRESSION_FACTOR = 8;
		
	/* Filename related constants. */
	private final String[] SUFFIX_TRIM_REGEXS = {"[.]fq$", "[.]fastq$", "[.]fq[.].*$", "[.]fastq[.].*$"};
	
	/* Global configurable options. With defaults. */
	private String adapterSequence = null;
	private String species = null;
	private String seqProtocol = "illumina";
	private String mirtraceMode = null;
	private String tempDir = System.getProperty("java.io.tmpdir");
	private String fastqListingFilename = null;
	private File customDBFolder = null;
	private File mainOutputDirectory;
	private File uniqueReadsOutputDirectory;
	private File unmappedReadsOutputDirectory;
	private boolean forceOverwriteOutput = false;
	private boolean writeFASTA = false;	
	private boolean uncollapseFASTA = false;
	private boolean autodetectAdapter = false; /* TODO: implement */
	private String reportTitle = "";
	private List<String> reportComments = new ArrayList<String>();
	private int verbosityLevel = 1;
	private boolean enablePipes = false;
	private Set<String> fastqFilenames = new java.util.HashSet<String>();
	private List<AnalysisTask> analysisTasks = new ArrayList<AnalysisTask>();
	private String MIRTRACEInvocationSyntax = "java -Xms<mem in MB>M -Xmx<mem in MB>M -jar <MIRTRACE JAR>";
	private boolean wrapperScriptUsed = false;
	private boolean mapToAllSpeciesRnatypeDatabases = false;

	/* globalMemoryReserve specifies the fixed amount of Java heap memory
	 * that can't be used by the sequence hash maps.
	 * 
	 * It's set very high to avoid ever running into OutOfMemoryError exceptions.
	 * Rationale:
	 * The multithreaded design of the program makes "random" OOM errors
	 * very difficult to recover properly from, and could also affect the 
	 * stability of internal JVM operations.
	 */
	private long globalMemoryReserve = 400*1024*1024; 
	private long perSampleMemoryReserve = 500_000; /* Somewhat arbitrary number. */
	
	
	/* Filename constants . */
	static final String HTML_FILENAME = "mirtrace-report.html";
	static final String JSON_FILENAME = "mirtrace-results.json";
	
	/* Global variables. */
	private List<String> warnings = new ArrayList<String>();
	private int numThreads = Runtime.getRuntime().availableProcessors();
	private int FASTQFileIndex = 0;
	private Set<String> adaptersWarnedAbout = new HashSet<String>();

	private Set<String> fastaOutputFilenames = new java.util.HashSet<String>();
	private SpeciesMetadataMap speciesMap;
	
	/* QC Analysis Flag Cutoffs. */
	static final double QC_FLAG_FRAC_PHRED_BAD = 0.5;
	static final int QC_FLAG_THRESHOLD_PHRED = 30;
	
	static final double QC_FLAG_FRAC_LENGTH_BAD = 0.25;
	static final int QC_FLAG_MIN_OK_LEN = 20;
	static final int QC_FLAG_MAX_OK_LEN = 25;
	
	static final double QC_FLAG_FRAC_QC_BAD = 0.25;
	
	static final double QC_FLAG_FRAC_RNATYPE_BAD = 0.10;
	
	static final double QC_FLAG_COMPLEXITY_MIN_GENES_REQUIRED_PERCENT = 0.1; 

	/* TODO FIXME Contamination warnings currently not implemented. */
	static final double QC_FLAG_FRAC_CONTAMINATION_BAD = 0.05;

	/* Verbose descriptions of thresholds. */
	
	private static final String _QC_WARNING_INTRO = "This warning is given if\n";
	static final String QC_WARNING_CRITERIA_VERBOSE_PHRED = _QC_WARNING_INTRO + "< 50% of nt's have a\n PHRED score >= 30."; 
	static final String QC_WARNING_CRITERIA_VERBOSE_LENGTH = _QC_WARNING_INTRO + "< 25% of reads have a \nlength between 20 and 25.";
	static final String QC_WARNING_CRITERIA_VERBOSE_QC = _QC_WARNING_INTRO + "< 25% of reads are QC-passed.";
	static final String QC_WARNING_CRITERIA_VERBOSE_RNATYPE = _QC_WARNING_INTRO + "< 10% of reads are \nidentifed as miRNAs.";
	static final String QC_WARNING_CRITERIA_VERBOSE_COMPLEXITY = _QC_WARNING_INTRO + "< 10% of the known miRNA genes\n have been detected in sample.";

	/* Auto-detection parameters (PHRED). */
	static final int PHRED_AUTO_DETECTION_INITIAL_READS_TO_CONSIDER = 10000;
	static final byte PHRED_ABSOLUTE_MIN_ALLOWED = 33;
	static final byte PHRED_ABSOLUTE_MAX_ALLOWED = 126;
	static final byte PHRED_VALID_P33_MIN = 33; /* Sanger PHRED score 0. */
	static final byte PHRED_VALID_P33_MAX = 79; /* Illumina 1.8+ with an extra (arbitrary) margin of 5. */
	static final byte PHRED_VALID_P64_MIN = 49; /* PHRED score -15 in PHRED+64. */
	static final byte PHRED_VALID_P64_MAX = 126; /* Very unlikely to be PHRED+33 encoded, might as well guess PHRED+64. */
		
	/* Auto-detection parameters (adapter). */
//	static final int ADAPTER_AUTO_DETECTION_READS_TO_CONSIDER = 1000000;
	/* Discard adapter candidates with a total fraction less then this percentage
	 * (in this case 5%). This corresponds closely to a requirement of the adapter
	 * having to be present in at least 5% of the reads. */
	//static final double ADAPTER_AUTO_DETECTION_MIN_KMER_FRAC = 0.05; 
	
	/* DEBUG CONSTANTS. */
	static final int DEBUG_OUTPUT_UPDATE_INTERVAL = 5000; /* ms. */
	
	/* Methods. */
	
	public Config(String[] commandLineArgs) {
		speciesMap = new SpeciesMetadataMap(this);
		parseCommandLineArguments(commandLineArgs);
		if (fastqListingFilename != null) {
			try {
				parseConfigFile(fastqListingFilename);
			} catch (IOException e) {
				fail("I/O error during parsing of config file.");
			}
		}
		if (analysisTasks.size() == 0) {
			printUsage(System.err);
			fail("No files to process.");
		}
		if (writeFASTA()) {
			if (uncollapseFASTA) {
				uniqueReadsOutputDirectory = new File(mainOutputDirectory, "qc_passed_reads.all.uncollapsed");
				unmappedReadsOutputDirectory = new File(mainOutputDirectory, "qc_passed_reads.rnatype_unknown.uncollapsed");
			} else {
				uniqueReadsOutputDirectory = new File(mainOutputDirectory, "qc_passed_reads.all.collapsed");
				unmappedReadsOutputDirectory = new File(mainOutputDirectory, "qc_passed_reads.rnatype_unknown.collapsed");
			}
		}
		if (enablePipes) {
			if (autodetectAdapter) {
				printUsage(System.err);
				fail("Adapter cannot be auto-detected when pipes are enabled.");
			}
			for (AnalysisTask task : getAnalysisTasks()) {
				if (task.getAdapter() == null) {
					printUsage(System.err);
					fail("Pipes are enabled. Please specify an adapter for sample '" + task.getFilename() + "' in the config file.");
				}
				if (task.getPhredOffset() == null) {
					printUsage(System.err);
					fail("Pipes are enabled. Please specify a PHRED offset for sample '" + task.getFilename() + "' in the config file.");
				}
			}
		}
	}

	void parseCommandLineArguments(String[] args) {
		// make sure filename is found
		int modeParamIndex = 0;
		if ((args.length > 0) && (args[0].equals("--mirtrace-wrapper-name"))) {
			MIRTRACEInvocationSyntax = args[1];
			wrapperScriptUsed = true;
			modeParamIndex = 2;
		}
		if (args.length < modeParamIndex + 1) {
			printUsage(System.err);
			fail("Not enough arguments.");
		}
		switch (args[modeParamIndex].toLowerCase()) {
			case "-h":
			case "--help":
			case "-help":
			case "help":
				printUsage(System.out);
				System.exit(0);
				break;
			case "-v":
			case "--version":
			case "version":
				System.out.println(MIRTRACE_VERSION);
				System.exit(0);
				break;
			case "cite":
			case "--cite":
				printCitationText(System.out);
				System.exit(0);
				break;
			case "--list-species":
			case "--list":
			case "list":
				speciesMap.printAllSpecies(System.out);
				System.exit(0);
				break;
			case "qc":
				mirtraceMode = "qc";
				break;
			case "trace":
				mirtraceMode = "trace";
				break;
			default:
				printUsage(System.err);
				fail("Unknown mode: " + args[modeParamIndex]);
		}
		
		for (int i = 1 + modeParamIndex; i < args.length; i++) {
			if (!(args[i].startsWith("-"))) {
				/* Assume the argument is a FASTQ filename. */
				String fqFilename = args[i];
				String fastaFilenameCandidate = FASTQFilenameToFASTAFilename(fqFilename);
				String verbosename = FASTQFilenameToVerbosename(fqFilename);				
				analysisTasks.add(new AnalysisTask(args[i], verbosename, fastaFilenameCandidate, FASTQFileIndex, null, null, this));
				FASTQFileIndex++;
				continue;
			}
			String currentArgName = args[i];
			if (args[i].startsWith("--")) {
				currentArgName = args[i].substring(2);
			} else if (args[i].startsWith("-")) {
				currentArgName = args[i].substring(1);
			}
			String currentArgValue = null;
			if (currentArgName.equals("f") ||
					currentArgName.equals("force") ||
					currentArgName.equals("w") ||
					currentArgName.equals("write-fasta") ||
					currentArgName.equals("uncollapse-fasta") ||
					currentArgName.equals("sort-fasta") ||
					currentArgName.equals("enable-pipes") ||
					currentArgName.equals("autodetect-adapter")) {
				/* Flag-type argument is OK. */
			} else {
				if ((args.length - 2) >= i) {
					currentArgValue = args[i + 1];
					i++;
				} else {
					printUsage(System.err);
					fail("Argument " + currentArgName + " value missing.");
				}
			}
			
			switch (currentArgName) {
				case "a":
				case "adapter":
					if (adapterSequence != null) {
						printUsage(System.err);
						fail("Only one global adapter can be specified.");
					}
					adapterSequence = currentArgValue;
					break;
				case "s":
				case "species":
					species = currentArgValue.toLowerCase();
					break;
				case "p":
				case "protocol":
					seqProtocol = currentArgValue.toLowerCase();
					if (!(
							(seqProtocol.equals("illumina")) || 
							(seqProtocol.equals("qiaseq")) ||
							(seqProtocol.equals("cats")) ||
							(seqProtocol.equals("nextflex"))
					)) {
						printUsage(System.err);
						fail("Invalid --protocol argument: " + seqProtocol);
					}
					break;
				case "temp-dir":
					tempDir = currentArgValue;
					break;
				case "custom-db-folder":
					customDBFolder = new File(currentArgValue);
					break;
				case "c":
				case "config":
					fastqListingFilename = currentArgValue;
					break;
				case "o":
				case "output-dir":
					mainOutputDirectory = new File(currentArgValue);
					break;
				case "comment":
					reportComments.add(currentArgValue);
					break;
				case "title":
					reportTitle = currentArgValue;
					break;
				case "f":
				case "force":
					forceOverwriteOutput = true;
					break;
				case "w":
				case "write-fasta":
					writeFASTA = true;
					break;
				case "uncollapse-fasta":
					uncollapseFASTA = true;
					break;
				case "enable-pipes":
					enablePipes = true;
					break;
				case "verbosity-level":
					try {
						verbosityLevel = Integer.parseInt(currentArgValue);
					} catch (NumberFormatException e) {
						printUsage(System.err);
						fail("Invalid verbosity level.");
					}
					break;
				case "t":
				case "num-threads":
					try {
						numThreads = Integer.parseInt(currentArgValue);
						if (numThreads < 1) {
							printUsage(System.err);
							fail("Invalid number of threads (must be >= 1).");
						}
					} catch (NumberFormatException e) {
						printUsage(System.err);
						fail("Invalid number of threads (not a valid number): " + currentArgValue);
					}
					break;					
				case "global-mem-reserve":
					try {
						globalMemoryReserve = Integer.parseInt(currentArgValue) * 1024 * 1024;
						if (globalMemoryReserve < 0) {
							printUsage(System.err);
							fail("Negative global-memory-reserve.");
						}
					} catch (NumberFormatException e) {
						printUsage(System.err);
						fail("Invalid global-memory-reserve.");
					}
					break;
				case "per-sample-mem-reserve":
					try {
						perSampleMemoryReserve = Integer.parseInt(currentArgValue);
						if (perSampleMemoryReserve < 0) {
							printUsage(System.err);
							fail("Negative per-sample-memory-reserve.");
						}
					} catch (NumberFormatException e) {
						printUsage(System.err);
						fail("Invalid per-sample-memory-reserve.");
					}
					break;
				case "map-to-all-species-rnatype-databases":
					mapToAllSpeciesRnatypeDatabases = true;
					break;
				default:
					printUsage(System.err);
					fail("Unknown argument: " + currentArgName);
			}
		}
		if (seqProtocol.equals("cats")) {
			adapterSequence = "AAAAAAAA";
		}
		if ((species == null) && (mirtraceMode.equals("qc"))) {
			if (mapToAllSpeciesRnatypeDatabases) {
				warnings.add("Mapping the samples to all available RNA type databases.");
			} else {
				printUsage(System.err);
				fail("Running in QC mode and no species given.\nPlease use --list-species to show available species.");
			}
			species = ALL_SPECIES_TAG;
		}
		if (adapterSequence != null) {
			adapterSequence = adapterSequence.toUpperCase();
			if (adapterSequence.length() > ADAPTER_USABLE_PREFIX_LENGTH) {
				adapterSequence = adapterSequence.substring(0, ADAPTER_USABLE_PREFIX_LENGTH);
			}
			if (adapterSequence.length() < Config.ADAPTER_USABLE_PREFIX_LENGTH) {
				warnings.add("Short 3' adapter detected: '" + adapterSequence + 
						"'. Adapter trimming might not work as expected.");
			}
			for (int i = 0; i < adapterSequence.length(); i++) {
				char ch = adapterSequence.charAt(i); 
				if ((ch != 'A') && (ch != 'C') && (ch != 'G') && (ch != 'T')) {
					printUsage(System.err);
					fail("Invalid nucleotide in adapter sequence: " + String.valueOf(ch));
				}
			}
		}
		
		/* Configure the output directory name. */
		if ((mainOutputDirectory == null) && (fastqListingFilename != null)) {
			mainOutputDirectory = new File(fastqListingFilename + ".output"); /* Default value */
		}
		else if (mainOutputDirectory == null) {
			TimeZone tzUTC = TimeZone.getTimeZone("UTC");
			DateFormat df = new SimpleDateFormat("yyyyMMdd-HHmmss.SSS");
			df.setTimeZone(tzUTC);
			Date now = new Date();
			mainOutputDirectory = new File("mirtrace." + df.format(now));			
		}
		
		/* Reload species map now that we might have access to custom databases. */
		speciesMap = new SpeciesMetadataMap(this);
		
		if (qcMode() && (!speciesMap.speciesExists(species))) {
			printUsage(System.err);
			fail("unknown species: " + species + "\nUse --list-species to show available species.");
		}
	}
	
	private String FASTQFilenameToFASTAFilename(String filename) {
		String fastaOutputFilenameBase = new File(filename).getName().replace("[^-\\w0-9()\\[\\],./]", "");
		for (String regex : SUFFIX_TRIM_REGEXS) {
			fastaOutputFilenameBase = fastaOutputFilenameBase.replaceFirst(regex, "");
		}
		int fileNo = 0;
		String fastaFilenameCandidate;
		while (true) {
			if (fileNo == 0) {
				fastaFilenameCandidate = fastaOutputFilenameBase + ".fasta";
			} else {
				fastaFilenameCandidate = fastaOutputFilenameBase + "-" + fileNo + ".fasta"; 
			}
			if (!fastaOutputFilenames.contains(fastaFilenameCandidate)) {
				fastaOutputFilenames.add(fastaFilenameCandidate);
				break;
			}
			if (fileNo > Config.MAX_INPUT_FILES) {
				fail("Too many FASTA output renaming attempts. This error shouldn't really be possible.");
			}
			fileNo++;
		}
		return fastaOutputFilenameBase;
	}
	
	private String FASTQFilenameToVerbosename(String filename) {
		String verbosename = new File(filename).getName();
		for (String regex : SUFFIX_TRIM_REGEXS) {
			verbosename = verbosename.replaceFirst(regex, "");
		}
		return verbosename;
	}

	void parseConfigFile(String filename) throws IOException {
		InputStream inputStream = null;
		try {
			inputStream = new FileInputStream(filename);
		}
		catch (FileNotFoundException e) {
			printUsage(System.err);
			fail("Could not open configuration file: " + filename);
		}
	    CSVReader reader = new CSVReader(new InputStreamReader(inputStream));
		String[] line;
	    while ((line = reader.readNext()) != null) {
			if (line.length < 1 || line[0].length() == 0 || line[0].startsWith("#")) { 
				/* Ignore empty lines and comments. */
				continue;
			}
			String fqFilename = null;
			String verbosename = null;
			String sampleAdapter = null;
			Integer phredOffset = null;
			if (line.length > 0) {
				fqFilename = line[0];
				if (line.length > 1) {
					verbosename = line[1];
					if (verbosename.length() == 0) {
						verbosename = FASTQFilenameToVerbosename(fqFilename);
					}
				} else {
					verbosename = FASTQFilenameToVerbosename(fqFilename);
				}
				if (line.length > 2) {
					if (adapterSequence == null) {
						sampleAdapter = line[2].toUpperCase();
						if (sampleAdapter.length() > ADAPTER_USABLE_PREFIX_LENGTH) {
							sampleAdapter = sampleAdapter.substring(0, ADAPTER_USABLE_PREFIX_LENGTH);
						}
						if (sampleAdapter.length() < Config.ADAPTER_USABLE_PREFIX_LENGTH) {
							if (!adaptersWarnedAbout.contains(sampleAdapter)) {
								warnings.add("Short 3' adapter detected: '" + sampleAdapter + 
										"'. Adapter trimming might not work as expected.");
								adaptersWarnedAbout.add(sampleAdapter);
							}
						}
					} else {
						printUsage(System.err);
						fail("Sample specific adapter specified in config file despite global adapter given.\n" + 
								"Please specify either a global adapter OR only sample-specific adapters.");
					}
				}
				if (line.length > 3) {
					try {
						phredOffset = Integer.valueOf(line[3]);
					} catch (NumberFormatException e) {
						printUsage(System.err);
						fail("Invalid phred offset specification: " + line[3]);
					}
					if (!((phredOffset == 33) || (phredOffset == 64))) {
						printUsage(System.err);
						fail("Invalid phred offset: " + phredOffset + ". Must be 33 or 64.");
					}
				}
			}
			if (fastqFilenames.contains(fqFilename)) {
				printUsage(System.err);
				fail("Duplicate FASTQ filename: " + fqFilename);
			} else {
				fastqFilenames.add(fqFilename);
			}
			String fastaFilenameCandidate = FASTQFilenameToFASTAFilename(fqFilename);
			analysisTasks.add(new AnalysisTask(fqFilename, verbosename, 
					fastaFilenameCandidate, FASTQFileIndex, sampleAdapter, phredOffset, this));
			FASTQFileIndex++;
		}
		reader.close();
	}
		
	void printUsage(PrintStream o) {
		o.println("USAGE: " + MIRTRACEInvocationSyntax + " MODE [-s SPECIES] [-a ADAPTER] [OTHER OPTIONS]... [FASTQ filenames]...");
		o.println("");
		if (!wrapperScriptUsed) {
			o.println("NOTE: Please allocate a large amount of RAM to the Java JVM using the ");
			o.println("      JVM parameters \"-Xms\" and \"-Xmx\". For e.g. an 8 GB RAM machine,");
			o.println("      the following is recommended: \"-Xms4G -Xmx4G\".");
			o.println("");
		}
		o.println("SIMPLE USAGE EXAMPLE (QUALITY CONTROL MODE):");
		o.println(MIRTRACEInvocationSyntax + " qc --adapter ACGTACGT --species hsa sample_A.fq sample_B.fq.gz");
		o.println("");
		o.println("SIMPLE USAGE EXAMPLE (TRACE MODE):");
		o.println(MIRTRACEInvocationSyntax + " trace --adapter ACGTACGT sample_A.fq sample_B.fq.gz");
		o.println("");
		o.println("MODES");
		o.println("The first argument must specify what mode miRTrace should operate in. Available modes:");
		o.println("    trace                  miRNA trace mode. Produces a clade report. --species is ignored.");
		o.println("    qc                     Quality control mode (full set of reports). --species must be given.");
		o.println("");
		o.println("ARGUMENT REQUIRED IN QC MODE:");
		o.println("    -s, --species          Species (miRBase encoding). EXAMPLE: \"hsa\" for Homo sapiens.");
		o.println("                           To list all codes, run \"miRTrace --list-species\".");
		o.println("");
		o.println("SPECIFYING INPUT FILES USING A CONFIG FILE:");
		o.println("If the input samples are not given as arguments directly, a config file must be used.");
		o.println("    -c, --config           List of FASTQ files to process. This is a CSV (comma separated");
		o.println("                           value) file, i.e. with one entry per row.");
		o.println("                           ");
		o.println("                           Each row consists of the following columns");
		o.println("                           (only the first is required):");
		o.println("                           filename,name-displayed-in-report,adapter,PHRED-ASCII-offset");
		o.println("                           ");
		o.println("                           NOTE: the PHRED ASCII offset can typically be reliably");
		o.println("                           auto-detected and is not necessary to specify.");
		o.println("                           ");
		o.println("                           EXAMPLE CONFIG FILE:");
		o.println("                           path/sample1.fastq,sample 1 (control),TGGAATTC");
		o.println("                           path/sample2.fastq,sample 2 (+drug X),TGGAATTC");
		o.println("");
		o.println("OPTIONAL ARGUMENTS:");
		o.println("    -a, --adapter          <DNA sequence>. [DEFAULT: none].");
		o.println("    -p, --protocol         One of the following (read structure schematic in parens):");
		o.println("                               illumina (miRNA--3'-adapter--index) [DEFAULT]");
		o.println("                               qiaseq (miRNA--3'-adapter--UMI--3'-adapter--index)");
		o.println("                                   NOTE: Only the first (leftmost) 3' adapter should be specified.");
		o.println("                               cats (NNN--miRNA--poly-A--3'-adapter--index)");
		o.println("                                   NOTE: It's not possible to specify an adapter for -p cats.");
		o.println("                               nextflex (NNNN--miRNA--NNNN--3'-adapter--index)");
		o.println("    -o, --output-dir       Directory for output files. [DEFAULT: <file listing>.output]");
		o.println("    -f, --force            Overwrite output directory if it exists.");
		o.println("    --enable-pipes         Enable support for named pipes (fifos) as input.");
		o.println("                           NOTE: Requires a config file with PHRED and adapter");
		o.println("                           given for each entry. Input must not be compressed.");
		o.println("    -w, --write-fasta      Write QC-passed reads and unknown reads (as defined");
		o.println("                           in the RNA type plot) to the output folder.");
		o.println("                           Identical reads are collapsed. Entries are sorted by abundance.");
		o.println("");
		o.println("OPTIONAL ARGUMENTS [FASTA OUTPUT] (Only relevant if --write-fasta given):");
		o.println("    --uncollapse-fasta     Write one FASTA entry per original FASTQ entry.");
		o.println("");
		o.println("OPTIONAL ARGUMENTS [HTML REPORT OPTIONS]:");
		o.println("    --title                Set the report title.");
		o.println("    --comment              Add a comment to the generated report. Multiple");
		o.println("                           arguments can be given.");
		o.println("");
		o.println("OPTIONAL ARGUMENTS [PERFORMANCE / TROUBLESHOOTING]:");
		o.println("    -t, --num-threads      Maximum number of processing threads to use.");
		o.println("                           [DEFAULT: <number of virtual cores>]");
		o.println("    --verbosity-level      Level of detail for debug messages. [default: 1]");
		o.println("    --global-mem-reserve   Amount of Java memory reserved for "); 
		o.println("                           \"housekeeping\" tasks (in MB).");
		o.println("                           Increase only if OutOfMemoryErrors are occurring.");
		o.println("                           Decrease only if available system memory is very low.");
		o.println("                           [Current value: " + (globalMemoryReserve / (1024*1024)) + " MB]");
		o.println("");
		o.println("OPTIONAL ARGUMENTS [CUSTOM DATABASES]:");
		o.println("    --custom-db-folder     Folder containing user-generated reference databases.");
		o.println("");
		o.println("HELP");
		o.println("    --list-species         List all species that miRTrace has reference databases for.");
		o.println("    --cite                 Show information about how to cite our paper.");
		o.println("    -h, --help             Display this help text.");
		o.println("    -v, --version          Display miRTrace version number.");

		o.println("");
		
		/* HIDDEN ARGUMENTS:
		 * --per-sample-mem-reserve
		 * --map-to-all-species-rnatype-databases
		 * 
		 * 
		 */
	}
	
	void fail(String msg) {
		System.err.println("ERROR: " + msg);
		System.exit(-1);
	}
	
	String getAdapterSequence() {
		return adapterSequence;
	}

	String getSpecies() {
		return species;
	}
	
	String getSeqProtocol() {
		return seqProtocol;
	}
	
	String getMirtraceMode() {
		return mirtraceMode;
	}

	String getTempDir() {
		return tempDir;
	}
	
	List<String> getWarnings() {
		return warnings;
	}

	List<AnalysisTask> getAnalysisTasks() {
		return analysisTasks;
	}
	
	int getNumAnalysisTasks() {
		return analysisTasks.size();
	}
	
	int getMaxNumThreads() {
		if (numThreads > analysisTasks.size()) {
			return analysisTasks.size();
		} else {
			return numThreads;
		}
	}
	
	int getVerbosityLevel() {
		return verbosityLevel;
	}
	
	File getMainOutputDirectory() {
		return mainOutputDirectory;
	}
	
	File getUniqueReadsOutputDirectory() {
		return uniqueReadsOutputDirectory;
	}
	
	File getUnmappedReadsOutputDirectory() {
		return unmappedReadsOutputDirectory;
	}	
	
	boolean getForceOverwriteOutput() {
		return forceOverwriteOutput;
	}
		
	String getReportTitle() {
		return reportTitle;
	}

	List<String> getReportComments() {
		return reportComments;
	}	
	
	boolean writeFASTA() {
		return writeFASTA;
	}
		
	boolean autodetectAdapter() {
		return autodetectAdapter;
	}
	
	boolean pipesEnabled() {
		return enablePipes;
	}
	
	void printCitationText(PrintStream ps) {
		ps.println(CITATION_TEXT);
	}

	boolean writeCollapsedFASTA() {
		return !uncollapseFASTA;
	}

	String getVersion() {
		return MIRTRACE_VERSION;		
	}

	long getGlolbalMemoryReserve() {
		return globalMemoryReserve;
	}

	long getPerSampelMemoryReserve() {
		return perSampleMemoryReserve;
	}

	String getSpeciesVerbosename() {
		if (qcMode()) {
			return speciesMap.getSpeciesFromAbbreviation(species);
		} else {
			return "";
		}
	}

	String getRRNASubunits() {
		if (qcMode()) {
			return speciesMap.getrRNASubunits(species);
		} else {
			return "ONLY_AVAILABLE_IN_QC_MODE";
		}
	}

	boolean hasCustomDatabases() {
		return customDBFolder != null;
	}
	
	File getCustomDBFolder() {
		return customDBFolder;
	}

	boolean qcMode() {
		return mirtraceMode.equals("qc");
	}
	
	boolean allSpeciesModeActive() {
		return species == ALL_SPECIES_TAG;
	}
	
}

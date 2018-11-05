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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

class FASTQParser {
	
	/* Constants */
	static final byte ASCII_LF = 10;
	static final byte ASCII_AT = 64;
	
	static final int SEQ_FILE_EOF = -1;

	/* Settings */
	
	/* Set buffer to same size as the FileInputStream's internal buffer. 
	 * See http://stackoverflow.com/a/29268186 */
	int BUF_SIZE = 8192;
	
	/* Member variables */
	InputStream inputStream = null;
	String seqFilename;
	int line;
	int inputBufferPos;
	byte[] inputBuffer;
	int bytesRead;
	int maxSequenceLength;
	long filePos = -1;
	Config config;
	
	FASTQParser(String seqFilename, Config config) throws FASTQParseException {
		this.maxSequenceLength = Config.READ_LENGTH_CUTOFF;
		this.seqFilename = seqFilename;
		this.bytesRead = 0;
		this.inputBuffer = new byte[BUF_SIZE];
		this.line = 1;
		this.config = config;
		open();
	}
	
	static boolean isGzipped(String seqFilename) throws FASTQParseException {
		int gzMagic = 0;
		try {
			InputStream inputFile = new FileInputStream(seqFilename);
			gzMagic = inputFile.read() & 0xff | ((inputFile.read() << 8) & 0xff00);
			inputFile.close();
		} catch (FileNotFoundException e) {
			throw new FASTQParseException("Failed parsing FASTQ file '" + seqFilename + 
					"': file not found.");
		} catch (IOException e) {
			throw new FASTQParseException("Failed parsing FASTQ file '" + seqFilename + 
					"': I/O error.");		} 
		return gzMagic == GZIPInputStream.GZIP_MAGIC;
	}

	void open() throws FASTQParseException {
		boolean gzipFormat = false;
		if (!config.pipesEnabled()) {
			gzipFormat = isGzipped(seqFilename);
		}
		try {
			this.inputStream = new FileInputStream(seqFilename);
			if (gzipFormat) {
				this.inputStream = new GZIPInputStream(this.inputStream);
			}
			this.filePos = 0;
		}
		catch (FileNotFoundException e) {
			close();
			failParse("Could not open file.");
		} catch (IOException e) {
			failParse("I/O error.");
		}
	}
	
	void close() throws FASTQParseException {
		if (inputStream != null) {
			try {
				inputStream.close();
			} catch (IOException e) {
				failParse("IO Error.");
			}
		}
	}
	
	void failParse(String msg) throws FASTQParseException {
		throw new FASTQParseException("Failed parsing FASTQ file '" + seqFilename + 
				"' at line " + line + ": " + msg);
	}
	
	boolean fillBuffer() throws FASTQParseException {
 		try {
			bytesRead = inputStream.read(inputBuffer, 0, BUF_SIZE);
			filePos += bytesRead;
	    	inputBufferPos = 0;
			if (bytesRead == -1) {
				return false;
			}
		}
		catch (IOException e) {
			failParse("ERROR: I/O error during processing of file: " + seqFilename);
		}
		return true;
	}
	
	byte getNextByte() throws FASTQParseException {
		byte res;
		if (inputBufferPos >= bytesRead) {
			if (!fillBuffer()) {
				System.err.println("UEOF: filePos: " + filePos);
				System.err.println("");
				failParse("Unexpected end of file.");
			}
		}
		res = inputBuffer[inputBufferPos];
		inputBufferPos++;
		return res;
	}
	
	boolean hasNextByte() throws FASTQParseException {
		if (inputBufferPos >= bytesRead) {
			return fillBuffer();
		}
		return true;
	}
	
	void advanceUntilNextLine() throws FASTQParseException {
		while (getNextByte() != ASCII_LF);
	}
	
	int parseUntilNextLine(byte[] buffer) throws FASTQParseException {
		int outputPos = 0;
		byte ch;
		for (ch = getNextByte(); ch != ASCII_LF; ch = getNextByte()) {	    			 
			if (outputPos >= maxSequenceLength) {
				/* Increment outputPos but don't store data in buffer. */
				outputPos++; 
			} else {
				buffer[outputPos] = ch;
				outputPos++;
			}
		}
		return outputPos;
	}
	
	void parseNBytes(byte[] buffer, int numBytes) throws FASTQParseException {
		int outputPos;
		byte ch;
		for (outputPos = 0; outputPos < numBytes; outputPos++) {	    			
			ch = getNextByte();
			if (ch == ASCII_LF) {
				failParse("Unexpected newline.");
			}
			if (outputPos >= maxSequenceLength) {
				/* Skip excess characters. */
			} else {
				buffer[outputPos] = ch;
			}
		}
	}
	
	int getNextEntry(byte[] sequence, byte[] phredScoresASCII) throws FASTQParseException {
		int seqLen;
		byte tempCh;
    	if (!hasNextByte()) {
    		/* End of file reached. All OK. */
    		return SEQ_FILE_EOF;
    	}
    	
    	/* Parse header line */
		if ((tempCh = getNextByte()) != ASCII_AT) {
			failParse("Expected start of header row ('@' character). Found " + tempCh);
		}
		advanceUntilNextLine();
		line++;
		
		/* Parse sequence line */
		seqLen = parseUntilNextLine(sequence);
		line++;
		if ((tempCh = getNextByte()) != '+') {
			failParse("Expected plus ('+') character. Found " + tempCh);
		}
		advanceUntilNextLine();
		line++;
		
		/* Parse PHRED score line */
		parseNBytes(phredScoresASCII, seqLen);
		
		/* The sequence and PHRED parsers only stores up to maxSequenceLength chars.
		 * Trim seqLen to match this.
		 */
		if (seqLen > maxSequenceLength) {
			seqLen = maxSequenceLength;
		}

		line++;
		/* Consume newline if not EOF */
		if (hasNextByte()) {
			tempCh = getNextByte();
			if (tempCh != ASCII_LF) {
				failParse("Expected newline. Found " + tempCh);
			}
		}
		return seqLen;
	
	}

}
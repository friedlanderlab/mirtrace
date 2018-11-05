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

import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;

class AnalysisPipelineResult {
	QCStatistics stats;
	String verbosename;
	String filename;
	String adapter;
	long fileSize;
	int displayOrder;
	String fileModificationTime;
	
	public AnalysisPipelineResult(QCStatistics stats, 
			String verbosename, 
			String filename,
			String adapter,
			long fileSize,
			long fileModificationTime,
			int displayOrder) {
		this.stats = stats;
		this.verbosename = verbosename;
		this.filename = filename;
		this.adapter = adapter;
		this.fileSize = fileSize;
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		this.fileModificationTime = df.format(new Date(fileModificationTime));
		this.displayOrder = displayOrder;
	}

	QCStatistics getStats() {
		return stats;
	}

	String getVerbosename() {
		return verbosename;
	}

	String getFilename() {
		return filename;
	}

	static class AlphabeticComparator implements Comparator<AnalysisPipelineResult> {

		@Override
		public int compare(AnalysisPipelineResult arg0, AnalysisPipelineResult arg1) {
			return arg0.verbosename.compareToIgnoreCase(arg1.verbosename);
		}
	}
	
	static class DisplayOrderComparator implements Comparator<AnalysisPipelineResult> {

		@Override
		public int compare(AnalysisPipelineResult arg0, AnalysisPipelineResult arg1) {
			return new Integer(arg0.displayOrder).compareTo(arg1.displayOrder);
		}
	}
		
}

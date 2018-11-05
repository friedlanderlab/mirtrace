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

import com.google.gson.*;

/**
 * @author Yrin Eldfjell
 *
 */
public class ReportBuilderJSON {
	MiRTraceReport report;
	
	ReportBuilderJSON(MiRTraceReport report) {
		this.report = report;
	}
	
	String build(boolean qcMode) {
		/* Prettyprinting takes too much space. */
		
		 //Gson output = new GsonBuilder().setPrettyPrinting().create();
		Gson output = null;
		if (qcMode) {
			output = new GsonBuilder().create();
		} else {
			output = new GsonBuilder()
					.addSerializationExclusionStrategy(new ExclusionStrategy() {
						@Override
						public boolean shouldSkipField(FieldAttributes f) {
							String field = f.getName();
							if (field.contains("statsNucleotidePhredScores") || 
									field.contains("statsLength") || 
									field.contains("statsQC") || 
									field.contains("statsRNAType")) {
								return true;
							}
							return false;
						}

						@Override
						public boolean shouldSkipClass(Class<?> arg0) {
							return false;
						}
					})
					.create();
		}
		return output.toJson(report);
	}
}

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

import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

class SpeciesMetadataMap {
	
	SortedMap<String,String> abbrevMap = new TreeMap<String,String>();
	SortedMap<String,String> rRNASubunitMap = new TreeMap<String,String>();


	public SpeciesMetadataMap(Config config) {
		String tableFilename = "species.list.tab";
		InputStream tableInputStream = DatabaseLoader.loadDatabase(tableFilename, config, true, null, null);
		Scanner scanner = new Scanner(tableInputStream);
		Pattern linePattern = Pattern.compile("^([^\t]+)\t([^\t]+)\t([^\t]*)$");
		while (scanner.hasNextLine()) {
			String line = scanner.nextLine();
			if (line.length() < 1) {
				continue;
			}
			Matcher m = linePattern.matcher(line);
			String abbrev = "";
			String species = "";
			String rRNASubunits = "";
			if (m.find()) {
				abbrev = m.group(1);
				species = m.group(2);
				rRNASubunits = m.group(3);
			} else {
				System.out.println("BUG: Internal error parsing internal species listing. Should not happen.");
				System.exit(-1);
			}
			abbrevMap.put(abbrev, species);
			rRNASubunitMap.put(abbrev, rRNASubunits);
		}
		scanner.close();
	}
	
	String getSpeciesFromAbbreviation(String abbrev) {
		return abbrevMap.get(abbrev);
	}
	
	String getrRNASubunits(String abbrev) {
		ArrayList<String> subunitsPresent = new ArrayList<String>();
		String[] counts = rRNASubunitMap.get(abbrev).split(",");
		for (String c : counts) {
			String subunitName = c.split("=")[0]; /* If present in the listing the count wil be at least one. */
			subunitsPresent.add(subunitName);
		}
		return StringUtils.join(subunitsPresent, ", ");
	}
	
	boolean speciesExists(String abbrev) {
		return abbrevMap.containsKey(abbrev);
	}
	
	void printAllSpecies(PrintStream p) {
		p.println("Run miRTrace with argument '--species <code>' to use the reference databases of the corresponding species.");
		p.println("\tcode\tSpecies");
		p.println("\t----\t-------");
		for (SortedMap.Entry<String,String> e: abbrevMap.entrySet()) {
			p.println("\t" + e.getKey() + "\t" + e.getValue());
		}
	}
}

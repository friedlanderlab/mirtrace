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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class CladeFamilyRecord implements Comparable<CladeFamilyRecord> {
	
	String seq;
	Integer cladeId;
	Integer familyId;
	List<String> miRBaseIds;
	int count;
	Pattern RE_MIRBASE_FAMILY_ID = Pattern.compile("^[^-]+-miR-?([0-9]+).*?$");
	
	CladeFamilyRecord(String seq, Integer clade) {
		miRBaseIds = new ArrayList<String>();
		this.seq = seq;
		this.cladeId = clade;
		this.familyId = null;
	}
	
	void putmiRBaseId(String miRBaseId) {
		if (!miRBaseIds.contains(miRBaseId)) {
			Matcher m = RE_MIRBASE_FAMILY_ID.matcher(miRBaseId);
			if (!m.matches()) {
				System.err.println("WARNING: unable to extract family id from miRBase id: " + miRBaseId);
			} else {
				Integer newFamilyId = new Integer(m.group(1));
				if ((familyId != null) && (newFamilyId.intValue() != familyId.intValue())) {
					System.err.println("ERROR: inconsistent clade family id detected! This shouldn't happen.");
					System.err.println("       " + familyId.toString() + ", " + newFamilyId.toString());
				} else {
					familyId = newFamilyId;
				}
			}
			miRBaseIds.add(miRBaseId);
		}
	}
	
	void increaseCount(int amount) {
		count += amount;
	}

	@Override
	public int compareTo(CladeFamilyRecord o) {
		if (o.cladeId.compareTo(cladeId) == 0) {
			return seq.compareTo(o.seq);
		} else {
			return cladeId.compareTo(o.cladeId);
		}
	}

	public String getFamilyId() {
		if (familyId == null) { 
			return "";
		} else {
			return familyId.toString();
		}
	}

}

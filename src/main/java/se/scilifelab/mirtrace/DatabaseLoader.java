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
import java.io.SequenceInputStream;
import java.util.Queue;
import java.io.FileNotFoundException;
import java.io.InputStream;

class DatabaseLoader {

	static InputStream loadDatabase(String dbName, Config config, Boolean concatenate, Queue<String> warnings, String customDBNotFoundMessage) {
		/* Try to fetch from user-specified directory if available. */
		InputStream isCustom = null;
		if (config.hasCustomDatabases()) {
			try {
				isCustom = new FileInputStream(new File (config.getCustomDBFolder(), dbName));
				if (!concatenate) {
					return isCustom;
				}
			} catch (FileNotFoundException e) {
				/* DB not found, use bundled instead. */
				if (customDBNotFoundMessage != null) {
					warnings.add(customDBNotFoundMessage);
				}
			}
		}
		
		/* Try to fetch from the bundled databases. */
		InputStream isBundled = DatabaseLoader.class.getClassLoader().getResourceAsStream("databases/" + dbName);
		if (isBundled != null && isCustom != null) {
			return new SequenceInputStream(isCustom, isBundled);
		} else if (isBundled != null) {
			return isBundled;
		} else if (isCustom != null) {
			return isCustom;
		}
		throw new RuntimeException("INTERNAL ERROR: Could not find " + dbName + ".");
	}
}

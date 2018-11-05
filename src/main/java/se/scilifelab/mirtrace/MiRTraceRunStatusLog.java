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

class MiRTraceRunStatusLog {
	
	/* Stats:
	 * termination status - OK, FAIL
	 * Cmd line
	 * sample processing status - OK, FAIL, CANCELLED
	 * sample filenames
	 * sample verbosenames
	 * sample phred offsets (auto/assigned by user)
	 * sample adapter (incl. potential auto-detected result)
	 * sample processing time
	 * sample memory usage
	 * sample hashmap full %
	 * total run time, total mem usage stats
	 * warning and error messages
	 * ref seq database statistics, ref seq parse time
	 * output folder
	 * all constants
	 * all user-configurable variables/constants
	 * 
	 * */
	
	 /* Config file layout
	  * 
	  * [GENERAL]
	  * VAR1 = val 
	  * 
	  * [SAMPLE_1]
	  * Name = ...
	  * VAR2 = val
	  * ...
	  * 
	  * 
	  * */
	
	

}

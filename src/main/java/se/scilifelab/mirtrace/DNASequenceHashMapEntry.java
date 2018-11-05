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

class DNASequenceHashMapEntry {
	
	byte[] seq;
	int count;
	byte length;
	boolean adapterDetected;
	int firstDetectedDepth;
	
	DNASequenceHashMapEntry() {
		seq = new byte[Config.READ_LENGTH_CUTOFF];
		count = 0;
		length = 0;
		adapterDetected = false;
		firstDetectedDepth = -1;
	}
	
	DNASequenceHashMapEntry(byte[] seq, byte length, int count, boolean adapterDetected, int firstDetectedDepth) {
		this.seq = seq;
		this.length = length;
		this.count = count;
		this.adapterDetected = adapterDetected;
		this.firstDetectedDepth = firstDetectedDepth;
	}
	
	public byte[] getSeq() {
		return seq;
	}
	
	public int getCount() {
		return count;
	}
	
	public byte getLength() {
		return length;
	}
	
	public boolean getAdapterDetected() {
		return adapterDetected;
	}
	
	public int getFirstDetectedDepth() {
		return firstDetectedDepth;
	}

	
}

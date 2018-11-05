#!/usr/bin/env python3

"""
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
"""

import re, sys

def fail(msg=""):
    raise ValueError("FASTA parse ERROR: " + msg)

def parse_sequences(fasta_filename):
    entries = {}
    current_id = None
    current_desc = None
    current_seq_frags = []
    RE_ID_LINE = re.compile(b'^>([^\s]+)\s*(.*?)$')
    with open(fasta_filename, 'rb') as fasta_file:
        for line in fasta_file:
            if line.startswith(b'>'):
                m = RE_ID_LINE.match(line)
                if m:
                    if current_id != None:
                        entries[current_id] = (b''.join(current_seq_frags), current_desc)
                    current_id = m.group(1)
                    current_desc = m.group(2)
                    if current_id in entries:
                        fail("Duplicate seq id: " + record.id)
                    current_seq_frags = []
                else:
                    fail("Couldn't parse id line:\n" + line)
            else:
                clean_line = line.strip()
                if clean_line.startswith(b'#'):
                    continue
                current_seq_frags.append(clean_line)
        if current_id != None:
            entries[current_id] = (b''.join(current_seq_frags), current_desc)

    res = []
    for seq_id, seq_record in entries.items():
        res.append((seq_id, seq_record[1], seq_record[0]))
    return res

def back_transcribe(seq):
    return seq.replace(b'U', b'T').replace(b'u', b't')

def reverse_complement(seq):
    revcomp = []
    for letter in reversed(seq):
        if letter == 65: # A
            revcomp.append(b'T')
        elif letter == 67: # C
            revcomp.append(b'G')
        elif letter == 71: # G
            revcomp.append(b'C')
        elif letter == 84: # T
            revcomp.append(b'A')
        else:
            revcomp.append(b'N')
            if letter != 78:
                print("Note: reverse complemented '{}' with 'N'".format(chr(letter)), file=sys.stderr)
    return b''.join(revcomp)

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
import sys
import re
import os
import itertools
import struct
import argparse
import gzip
from fastareader import parse_sequences

#
# Constants
#

# Don't change KMER_LENGTH witout updating the corresponding Java code.
KMER_LENGTH = 9 

VALID_NTS = b'ACGT'
MIN_VALID_SEQ_LEN = 18 # Only used for warnings in this script.

def fail(msg=""):
    print("Program failed. Reason: " + str(msg))
    sys.exit(1)

def parse_args():
    parser = argparse.ArgumentParser(description='Make binary sequence database.')
    parser.add_argument("--sequences", type=str, 
            help="Sequences for DB, in MULTIFASTA format (DNA).",
            required=True)
    parser.add_argument("--out", type=argparse.FileType("wb"), 
            help="Name of the (gzipped) db to create. Should end with '.gz'.",
            required=True)
    args = parser.parse_args()
    return args

def dna_to_binary_string(seq):
    """Encodes the given DNA sequence to binary encoding (2 bits per nt).

    The last letter of the sequence is encoded in the 2 least significant bits.
    """
    DNA_CODES = {
        ord(b'A'): 0b00,
        ord(b'C'): 0b01,
        ord(b'G'): 0b10,
        ord(b'T'): 0b11
    }
    coded_seq = map(lambda nt: DNA_CODES[nt], seq)
    result = 0
    for nt_code in coded_seq:
        result = (result << 2) | nt_code
    return result

def make_db(seq_entries, kmer_len, valid_nts):
    kmer_locs = {} # kmer locations
    seq_offset = 0
    new_seqs = []
    new_seq_ids = []
    proc_seqs = 0
    for seq_id, seq_desc, seq in seq_entries:
        proc_seqs += 1
        seq = seq.upper()
        seq = seq.replace(b'_', b'N') # yes, underscore(s) are present in the input...
        seq = seq.replace(b'.', b'N') # yes, this happens...
        if not re.match(b'^[A-Z@]+$', seq):
            fail("Invalid non [A-Z@]-char in seq: '{}' ".format(seq.decode('ASCII')))
        if len(seq) < kmer_len:
            print("Warning: discarding seq shorter than kmer length: " + seq.decode('ASCII'),
                    file=sys.stderr)
            continue
        if len(seq) < MIN_VALID_SEQ_LEN:
            print("Warning: seq shorter than minimum valid seq len (no action taken): " + seq.decode('ASCII'),
                    file=sys.stderr)
            continue
        new_seqs.append(seq)
        new_seq_ids.append(seq_id)
        for i in range(0, len(seq) - kmer_len + 1):
            kmer = seq[i:i+kmer_len]
            if all(nt in valid_nts for nt in kmer):
                if not kmer in kmer_locs:
                    kmer_locs[kmer] = []
                kmer_locs[kmer].append(seq_offset + i)
            else:
                pass # Warning msg removed (too much spam):
                #print("Note: skipping kmer \"{}\" ({}, db type {}) with "
                #        "non ACGT nt(s).".format(kmer, abbrev, seq_type))
        seq_offset += len(seq) + 1 # +1 for terminating '$'-char
    ref_seq_array = b''.join(s + b'$' for s in new_seqs)
    ref_seq_id_array = b''.join(s + b'\n' for s in new_seq_ids)
    pos_list = []
    kmer_list = []
    pos_list_offset = 0
    for kmer in sorted(kmer_locs):
        kmer_list.append(dna_to_binary_string(kmer))
        kmer_list.append(pos_list_offset)
        for loc in kmer_locs[kmer]:
            pos_list.append(loc)
            pos_list_offset += 1
        # Negative sign indicates end of list.
        pos_list[-1] = -pos_list[-1] 

    # Prepare output.
    
    # Note: The Java DataInput interface requires big-endian format.
    row_1_ref_seq = ref_seq_array
    row_2_ref_seq_ids = ref_seq_id_array
    row_3_kmer_list = struct.pack('>{}i'.format(len(kmer_list)).encode('ascii'), *kmer_list)
    row_4_pos_list = struct.pack('>{}i'.format(len(pos_list)).encode('ascii'), *pos_list)

    # In practical terms, these limits will never be exceeded. But why not test.
    max_int = 2**32 - 1
    if len(row_1_ref_seq) > max_int:
        fail("Ref seq too big.")
    if len(row_2_ref_seq_ids) > max_int:
        fail("Ref seq ids too big.")
    if len(row_3_kmer_list) > max_int:
        fail("KMER list too big.")
    if len(row_4_pos_list) > max_int:
        fail("Position list too big.")

    size_header = struct.pack(
            b'>4i',
            len(row_1_ref_seq),
            len(row_2_ref_seq_ids),
            len(row_3_kmer_list),
            len(row_4_pos_list)
    )
    return b"".join([
            size_header,
            row_1_ref_seq,
            row_2_ref_seq_ids,
            row_3_kmer_list,
            row_4_pos_list
    ])

if __name__ == '__main__':
    args = parse_args()
    seq_entries = parse_sequences(args.sequences)
    binary_output = make_db(seq_entries, KMER_LENGTH, VALID_NTS)
    with gzip.GzipFile(fileobj=args.out) as gzipped_out:
        gzipped_out.write(binary_output)


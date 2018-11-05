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

import argparse
import re
import sys
import subprocess
import os
from fastareader import parse_sequences, back_transcribe
from collections import Counter

# WARNING: 
# The following two seq len const's MUST MATCH the corresponding 
# CLADE_DB_SEQ_LEN_CUTOFF constant in the Java program.

SEQ_TRIM_LEN = 20 # Trim seqs to this length
MIN_REF_SEQ_LEN = 20 # Discard seqs shorter than this length

parser = argparse.ArgumentParser(description='Make clade db.')
parser.add_argument("--mature-hairpins", type=str)
parser.add_argument("--clade-specification-animals", type=str)
parser.add_argument("--clade-specification-plants", type=str)
parser.add_argument("--out-dir", type=str)
args = parser.parse_args()

#
# Parse <clade to miRBase_id> mapping files.
#

def parse_clade_spec(filename):
    clades = {}
    with open(filename, 'rb') as file_handle:
        for line in file_handle:
            m = re.match(b"^(\S+)\s+(.+?)\s*$", line)
            if not m:
                raise ValueError("Invalid clade spec line:\n" + line)
            clade_name, ids_raw = m.group(1).strip().lower(), m.group(2)
            for id_entry in re.split(b"\s+", ids_raw):
                m = re.match(b"^(\d+)$", id_entry)
                if not m:
                    raise ValueError("Invalid miRBase id specification: " + 
                            id_entry.decode('ascii'))
                if not clade_name in clades:
                    clades[clade_name] = set()
                clades[clade_name].add(m.group(1))
    return clades

# Note: the "animals" category here really means "not plant".
clades_animals = parse_clade_spec(args.clade_specification_animals)
clades_plants = parse_clade_spec(args.clade_specification_plants)

#
# Parse mature hairpin FASTA file.
#

def parse_mature_hairpins(clades_animals, clades_plants, hairpin_file):

    # Note: plant miRBase ids are lacking the dash ("-") after "miR".
    # Ref: http://www.mirbase.org/help/nomenclature.shtml

    mirbase_seqs_animals = {}
    mirbase_seqs_plants = {}
    RE_MIRBASE_ID = re.compile(b'\S+miR(-?\d+).*?$')
    for seq_id, seq_desc, seq in parse_sequences(args.mature_hairpins):
        m = RE_MIRBASE_ID.match(seq_id)
        if m:
            if m.group(1).startswith(b'-'):
                mirbase_id = m.group(1)[1:]
                seq_dict = mirbase_seqs_animals
                clade_dict = clades_animals
            else:
                mirbase_id = m.group(1)
                seq_dict = mirbase_seqs_plants
                clade_dict = clades_plants

            for clade, clade_ids in clade_dict.items():
                if mirbase_id in clade_ids:
                    if not mirbase_id in seq_dict:
                        seq_dict[mirbase_id] = []
                    seq_dict[mirbase_id].append((back_transcribe(seq), seq_id))
        else:
            print("Ignored mature hairpin entry: ", seq_id.decode('ascii'), file=sys.stderr)
    return mirbase_seqs_animals, mirbase_seqs_plants

mirbase_seqs_animals, mirbase_seqs_plants = parse_mature_hairpins(
        clades_animals, clades_plants, args.mature_hairpins)

#
# Sanity checks.
#

def test_for_overlapping_ids(clades):
    unique_counter = Counter()
    for mirbase_ids in clades.values():
        unique_counter.update(mirbase_ids)
    for clade_id, count in unique_counter.items():
        if count > 1:
            raise ValueError("Duplicate clade id: " + clade_id.decode('ascii'))

def test_for_overlapping_seqs(clades, mirbase_seqs, unique_counter):
    for mirbase_ids in clades.values():
        seqs = set()
        for mirbase_id in mirbase_ids:
            for s in mirbase_seqs[mirbase_id]:
                seqs.add(s[:SEQ_TRIM_LEN])
        unique_counter.update(seqs)
    for seq, count in unique_counter.items():
        if count > 1:
            raise ValueError("Exact (trimmed) seq found in multiple ({}) clades: {}".format(count, seq.decode('ascii')))
    return unique_counter

# Test for ids occuring in multiple clades.
test_for_overlapping_ids(clades_animals)
test_for_overlapping_ids(clades_plants)

# Test for sequences occuring in multiple clades.
unique_counter = Counter()
unique_counter = test_for_overlapping_seqs(clades_animals, mirbase_seqs_animals, unique_counter)
test_for_overlapping_seqs(clades_plants, mirbase_seqs_plants, unique_counter)

#
# Generate output.
#

all_seqs_used = []

def process_clade(clades, mirbase_seqs, all_seqs_used):
    TEMP_FASTA_FILENAME = 'mirbase_temp_clade.fasta'
    DB_FILENAME_TEMPLATE = "{dbtype}.{species}.{category}.db.gz"
    ANY_SPECIES_TAG = 'meta_species_any'
    seq_id_counter = 0
    for clade_name, clade_ids in clades.items():
        with open(TEMP_FASTA_FILENAME, 'w', encoding='utf-8') as fout:
            for clade_id in clade_ids:
                if not clade_id in mirbase_seqs:
                    raise ValueError("Clade {} specifies id {}, which could not be found in the mature hairpin file.".format(clade_name.decode('ascii'), clade_id.decode('ascii')))
                seq_discard_count = 0
                for seq, raw_record_id in mirbase_seqs[clade_id]:
                    if len(seq) < MIN_REF_SEQ_LEN:
                        seq_discard_count += 1
                        continue
                    fout.write(">seq_{}+clade_{}+{}\n".format(
                            seq_id_counter, clade_id.decode('ascii'), raw_record_id.decode('ascii')))
                    seq_used = seq[:SEQ_TRIM_LEN].decode('ascii')
                    fout.write(seq_used + "\n")
                    all_seqs_used.append(seq_used)
                    seq_id_counter += 1
                if seq_discard_count:
                    print("WARNING: {}/{} miRBase sequences for clade={}, "
                            "id={} have been discarded due to short length.".format(
                                    seq_discard_count,
                                    len(mirbase_seqs[clade_id]),
                                    clade_name.decode('ascii'),
                                    clade_id.decode('ascii')),
                            file=sys.stderr)
        out_filename = DB_FILENAME_TEMPLATE.format(
                dbtype="clade", 
                species=ANY_SPECIES_TAG, 
                category=clade_name.decode('ascii')
        )
        out_filename = os.path.join(args.out_dir, out_filename)
        print("Writing db to '{}'.".format(out_filename), file=sys.stderr)
        subprocess.call([
                './mirtrace-db-generator.py',
                '--sequences',
                TEMP_FASTA_FILENAME,
                '--out',
                out_filename
        ])
        os.remove(TEMP_FASTA_FILENAME)

process_clade(clades_animals, mirbase_seqs_animals, all_seqs_used)
process_clade(clades_plants, mirbase_seqs_plants, all_seqs_used)
all_seqs_used = sorted(set(all_seqs_used))
with open('unique_clade_seqs_used.txt', 'w', encoding='utf-8') as fout:
    for s in all_seqs_used:
        fout.write(s)
        fout.write('\n')

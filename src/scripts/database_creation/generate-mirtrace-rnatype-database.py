#!/usr/bin/env python3
#
# Note: working directory must be the same dir as where the program resides.
#
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
import subprocess
import argparse
from fastareader import parse_sequences, back_transcribe, reverse_complement

parser = argparse.ArgumentParser(description='Generate RNA databases for once species. Note: Artifacts DB (if any) will be global for all species.')
parser.add_argument("--out-dir", type=str, required=True)
parser.add_argument("--species-abbrev", type=str, required=True)
parser.add_argument("--species-verbosename", type=str, required=True)
parser.add_argument("--mirna-seqs", type=str)
parser.add_argument("--rrna-seqs", type=str)
parser.add_argument("--trna-seqs", type=str)
parser.add_argument("--artifacts-seqs", type=str)
parser.add_argument("--batch-mode", action="store_true", help="Suppresses some warning messages [used by the build script].")
args = parser.parse_args()

species_abbrev = args.species_abbrev.lower()

try:
    os.makedirs(args.out_dir)
except os.error:
    pass

#
# Classes
#

class MultiSequenceFile(object):

    def __init__(self, filename, is_dna=True, ignore_missing_file=False):
        self.filename = filename
        self.is_dna = is_dna
        self.ignore_missing_file = ignore_missing_file

    def __enter__(self):
        if self.ignore_missing_file and (self.filename == None or (not os.path.exists(self.filename))):
            print("  Missing or non-specified input FASTA file detected. Ignoring this and creating an empty database.", file=sys.stderr)
            entries = []
        else:
            entries = parse_sequences(self.filename)
        self.seq_iterator = iter(entries)
        return self

    def __iter__(self):
        return self

    def __next__(self):
        seq_id, seq_desc, seq = next(self.seq_iterator)
        if self.is_dna:
            return (seq_id, seq_desc, seq)
        else:
            # Convert U -> T
            return (seq_id, seq_desc, back_transcribe(seq))

    def __exit__(self, type, value, traceback):
        pass

def fail(msg=""):
    print("Program failed. Reason: " + str(msg))
    sys.exit(1)

#
# Constants
#

KMER_LENGTH = 9
DB_FILENAME_TEMPLATE = "{dbtype}.{species}.{category}.db.gz"
SPECIES_LISTING_FILENAME = os.path.join(args.out_dir, "species.list.tab")
TEMP_FASTA_FILENAME = "mirtrace-temp-seqs.fasta.tmp"
ANY_SPECIES_TAG = 'meta_species_any'

#
# Construct databases.
#

def generate_db(species, category, seq_filename):
    print("  Constructing DB for species {} (type {})".format(species, category))
    out_filename = DB_FILENAME_TEMPLATE.format(
            dbtype="rnatype",
            species=species,
            category=category
    )
    out_filename = os.path.join(args.out_dir, out_filename)
    print("  => DB written to: " + out_filename)
    seq_id_counter = 0
    with MultiSequenceFile(seq_filename, ignore_missing_file=True) as input_seqs:
        with open(TEMP_FASTA_FILENAME, 'wb') as f_out:
            records = []
            for seq_id, seq_desc, seq in input_seqs:
                seq = seq.upper().replace(b'U', b'T')
                # For counting purposes the forwards and reverse complemented seqs
                # must be kept in the same '$'-separated entry.
                FW_BW_DELIM = b'@@@'
                f_out.write(b">seq_%s\n" % str(seq_id_counter).encode('ascii'))
                f_out.write(seq + FW_BW_DELIM + reverse_complement(seq) + b'\n')
                seq_id_counter += 1
        subprocess.check_call([
                './mirtrace-db-generator.py',
                '--sequences',
                TEMP_FASTA_FILENAME,
                '--out',
                out_filename
        ])
        os.remove(TEMP_FASTA_FILENAME)
    return seq_id_counter

def count_rrna_subunits(full_species_name, seq_filename):
    counts = {}
    with MultiSequenceFile(seq_filename, ignore_missing_file=True) as input_seqs:
        for seq_id, seq_desc, seq in input_seqs:
            m = re.search(rb'^([^0-9]+)_([0-9][^_]+)', seq_id)
            if m:
                species = m.group(1).replace(b'_', b' ')
                subunit = m.group(2)
                if species == full_species_name.encode('ascii'):
                    if subunit in counts:
                        counts[subunit] += 1
                    else:
                        counts[subunit] = 1
            else:
                print("NOTE: could not parse rRNA subunits information for entry:\n{}Reference rRNA subunit listing may be unavailable in miRTrace reports generated using this database. Actual read mapping not affected.\n".format(seq_id), file=sys.stderr)
    return counts

dbs_with_empty_counts = []
if generate_db(species_abbrev, 'mirna', args.mirna_seqs) == 0:
    dbs_with_empty_counts.append('mirna')
if generate_db(species_abbrev, 'trna', args.trna_seqs) == 0:
    dbs_with_empty_counts.append('trna')
if generate_db(species_abbrev, 'rrna', args.rrna_seqs) == 0:
    dbs_with_empty_counts.append('rrna')
rrna_subunit_counts = count_rrna_subunits(args.species_verbosename, args.rrna_seqs)
if generate_db(ANY_SPECIES_TAG, 'artifacts', args.artifacts_seqs) == 0:
    dbs_with_empty_counts.append('artifacts')

print("", file=sys.stderr)
for db in dbs_with_empty_counts:
    if not args.batch_mode:
        print("The {} db is empty. If an existing database exists for the species and you want miRTrace to use it instead, you must delete the corresponding '.gz' file in the output directory.\nNOTE: This only works if the species exists, otherwise miRTrace to fail. (The artifacts database is not species specific.)\n".format(db), file=sys.stderr)
with open(SPECIES_LISTING_FILENAME, 'a') as f_species_listing:
    def numeric_compare(value):
        m = re.search(rb'^([0-9.]+).*', value)
        if m:
            v = float(m.group(1))
            return v
        return 0
    f_species_listing.write('%s\t%s\t%s\n' % (
            species_abbrev, 
            args.species_verbosename,
            ','.join(['{}={}'.format(subunit.decode('ascii'), rrna_subunit_counts[subunit]) 
                    for subunit in sorted(rrna_subunit_counts.keys(),
                        key=numeric_compare)]))
    )

print("INFO: to run miRTrace with a custom database you need to specify the path to the database files (argument --custom-db-folder) and the species abbreviation (argument --species).")

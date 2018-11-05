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

parser = argparse.ArgumentParser(description='Make all RNA databases based on reference FASTA files.')
parser.add_argument("--out-dir", type=str)
parser.add_argument("--mirna-ref", type=str)
parser.add_argument("--rrna-ref", type=str)
parser.add_argument("--trna-ref", type=str)
parser.add_argument("--artifacts-ref", type=str)
args = parser.parse_args()

def fail(msg=""):
    print("Program failed. Reason: " + str(msg), file=sys.stderr)
    sys.exit(1)

#
# Classes
#

class MultiSequenceFile(object):

    def __init__(self, filename, is_dna=True):
        self.filename = filename
        self.is_dna = is_dna

    def __enter__(self):
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

#
# Constants
#

TEMP_FASTA_FILENAME_BASE = "mirtrace-batch-db-generation-temp."

RNA_FILENAMES = {}
RNA_FILENAMES['M']= args.mirna_ref
RNA_FILENAMES['T'] = args.trna_ref
RNA_FILENAMES['R'] = args.rrna_ref
RNA_FILENAMES['A'] = args.artifacts_ref

SPECIES_REGEXES = {}
SPECIES_REGEXES['M'] = re.compile(rb'^([^-]+)\S+\s+\S+\s+(.+?)\S+\s+stem.loop$')
SPECIES_REGEXES['T'] = re.compile(rb'^([A-Za-z]+_[A-Za-z]+)_.*')
SPECIES_REGEXES['R'] = re.compile(rb'^([A-Za-z]+_[A-Za-z]+)_.*')
RE_LOOKUP = SPECIES_REGEXES['M']

# This map describes the nt format (RNA vs DNA) of the input files
IS_DNA_MAP = {'M': False, 'T': True, 'R': True}

# This map desribes which regex group contains the full species name
REGEX_GROUP_MAP = {'M': 2, 'T': 1, 'R': 1}

# The "ALL_SPECIES" mode is used when miRTrace doesn't recieve a --species argument:
ALL_SPECIES_TAG = b'meta_species_all'
ALL_SPECIES_VERBOSE = b"<All species concatenated together>"

#
# Build species acronym lookup table
#
species_lookup = {} # acronym (lower-case) -> full name (lower-case with space)
with MultiSequenceFile(RNA_FILENAMES['M'], is_dna=False) as msf:
    for seq_id, seq_desc, seq in msf:
        seq_id_line = b"%s %s" % (seq_id, seq_desc)
        m = (RE_LOOKUP.match(seq_id_line) or
            fail("Parse failure of seq_id in " + RNA_FILENAMES['M']))
        abbreviation = m.group(1).lower()
        species = m.group(2).strip()
        if not abbreviation in species_lookup:
            species_lookup[abbreviation] = species
        elif species_lookup[abbreviation] != species:
            print(species_lookup[abbreviation], species, file=sys.stderr)
            fail("ERROR, inconsistent species for abbreviation '%s': " % abbreviation.decode('ascii'))
species_lookup[ALL_SPECIES_TAG] = ALL_SPECIES_VERBOSE
MAX_SPECIES_ABBREV_LEN = max(len(a) for a in species_lookup)

print("Length of longest species abbreviation found:", MAX_SPECIES_ABBREV_LEN)
print("Number of species abbreviations found:", len(species_lookup))
species_lookup_reverse = {v: k for k, v in species_lookup.items()}
print("Species found in hairpin db:\n" + "\n".join(
        "{} ({})".format(k.decode('ascii'), species_lookup_reverse[k].decode('ascii'))
        for k in sorted(species_lookup_reverse.keys())
))
print("")

#
# Read input files.
#

# ds - data (species)
ds = {k: {'M': [], 'T': [], 'R': []} for k in
        species_lookup_reverse.keys()}

d_artifacts = []

for seq_type in ['M', 'T', 'R']:
    with MultiSequenceFile(RNA_FILENAMES[seq_type], is_dna=IS_DNA_MAP[seq_type]) as rna_f:
        for seq_id, seq_desc, seq in rna_f:
            seq_id_line = b"%s %s" % (seq_id, seq_desc)
            m = (SPECIES_REGEXES[seq_type].match(seq_id_line) or
                fail("Parse failure of desc line in " + RNA_FILENAMES[seq_type]))
            species = m.group(REGEX_GROUP_MAP[seq_type])\
                    .replace(b'_', b' ').strip()
            if not species in species_lookup_reverse:
                print("Ignoring species \"{}\" not found in harpin db.".
                        format(species.decode('utf-8')))
                continue
            ds[species][seq_type].append((seq_id_line, seq))
            ds[ALL_SPECIES_VERBOSE][seq_type].append((seq_id_line, seq))

with MultiSequenceFile(RNA_FILENAMES['A']) as rna_f:
    for seq_id, seq_desc, seq in rna_f:
        seq_id_line = b"%s %s" % (seq_id, seq_desc)
        d_artifacts.append((seq_id_line, seq))

#
# Generate databases.
#

def generate_db(species):
    print("Constructing databases for species {}".format(species.decode('utf-8')))

    def generate_temp_fasta(filename, seqs):
        with open(filename, 'wb') as f_out:
            records = []
            for seq_id_line, seq in seqs:
                f_out.write(b">%s\n" % seq_id_line)
                f_out.write(seq + b'\n')

    for seq_type in ['M', 'T', 'R']:
        temp_fasta_filename = TEMP_FASTA_FILENAME_BASE + seq_type + '.fasta'
        generate_temp_fasta(temp_fasta_filename, ds[species_lookup[species]][seq_type])
    generate_temp_fasta(TEMP_FASTA_FILENAME_BASE + 'A' + '.fasta', d_artifacts)
    subprocess.check_call([
            './generate-mirtrace-rnatype-database.py',
            '--species-abbrev',
            species.decode('utf-8'),
            '--species-verbosename',
            species_lookup[species].decode('utf-8'),
            '--mirna-seqs',
            TEMP_FASTA_FILENAME_BASE + 'M.fasta',
            '--rrna-seqs',
            TEMP_FASTA_FILENAME_BASE + 'R.fasta',
            '--trna-seqs',
            TEMP_FASTA_FILENAME_BASE + 'T.fasta',
            '--artifacts-seqs',
            TEMP_FASTA_FILENAME_BASE + 'A.fasta',
            '--out-dir', 
            args.out_dir,
            '--batch-mode'
    ])
    os.remove(TEMP_FASTA_FILENAME_BASE + 'M.fasta')
    os.remove(TEMP_FASTA_FILENAME_BASE + 'R.fasta')
    os.remove(TEMP_FASTA_FILENAME_BASE + 'T.fasta')
    os.remove(TEMP_FASTA_FILENAME_BASE + 'A.fasta')

generate_db(ALL_SPECIES_TAG)
for species_abbrev, species in species_lookup.items():
    if b'\t' in species_abbrev or b'\t' in species:
        fail("ERROR: tab found in species name or abbrev. %s %s" % (species_abbrev, species))
    generate_db(species_abbrev)

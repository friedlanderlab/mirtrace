#!/bin/sh

#    This file is part of miRTrace.
#
#    COPYRIGHT: Marc Friedländer <marc.friedlander@scilifelab.se>, 2018
#    AUTHOR: Yrin Eldfjell <yete@kth.se>
#
#    miRTrace is free software: you can redistribute it and/or modify
#    it under the terms of the GNU General Public License as published by
#    the Free Software Foundation, version 3 of the License.
#
#    miRTrace is distributed in the hope that it will be useful,
#    but WITHOUT ANY WARRANTY; without even the implied warranty of
#    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#    GNU General Public License for more details.
#
#    You should have received a copy of the GNU General Public License
#    along with this program, see the LICENSES file.
#    If not, see <https://www.gnu.org/licenses/>.
#

rm -rf ../../main/resources/databases

mkdir -p ../../main/resources/databases

./make_rnatype_databases.py \
    --mirna-ref ../../lib/inputs/miRNA_hairpin_v21.fa \
    --trna-ref ../../lib/inputs/tRNA_reference.fa \
    --rrna-ref ../../lib/inputs/rRNA_reference.fa \
    --artifacts-ref ../../lib/inputs/artifact_sequences.fa \
    --out-dir ../../main/resources/databases

./make_clade_databases.py \
    --mature-hairpins ../../lib/inputs/miRNA_mature_v21.fa \
    --clade-specification-animals ../../lib/curated/clade-specific_miRNA_families_of_animal_clades.txt \
    --clade-specification-plants ../../lib/curated/clade-specific_miRNA_families_of_plant_clades.txt \
    --out-dir ../../main/resources/databases


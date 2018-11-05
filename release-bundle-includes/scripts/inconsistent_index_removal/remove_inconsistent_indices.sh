#!/bin/bash
#
# Author: Wenjing Kang 2018
#

fastq=''

echoerr() { echo "$@" 1>&2; }

usage() {
        echoerr #${0##/*}
        echoerr "USAGE: sh remove_inconsistent_indices.sh input.fastq > output.fastq"
	echoerr
	echoerr "Removes FASTQ reads with inconsistent indices, meaning the index reported in the main sequencing reaction (READ 1) is different from the index reported in the index sequencing reaction (INDEX 1) (see Figure 2E)."
	echoerr 
	echoerr "BACKGROUND"
	echoerr "Multiple samples are often multiplexed for sequencing. After sequencing, the pooled reads are demultiplexed to their sample of origin based on sample unique indices. When the insert sequences are short (e.g, microRNA around 22 nt in length) and sequencing cycles are long (e.g. 75 cycles), the index of each read can be read twice by main seuqencing reaction (READ 1) and index sequencing reaction (INDEX 1). For many reads, we found the index reported by READ1 is different from the index reported by INDEX 1. Based on these ambiguous indices, the reads may be mis-assigned to wrong samples. In order to reduce the cases of mis-assignments, the script is wrote to remove the reads with inconsistent indices."
	echoerr "NOTE: This script is applicable for single end sequencing data (FASTQ file) of small RNA libraries constructed using TruSeq small RNA library preparation kit."
	echoerr 
	echoerr "INPUT FASTQ FORMAT"
	echoerr "    Each FASTQ read is encoded by 4 lines: First is ID line, second is raw sequence, third is a symbol '+' and fouth has quality values for the sequences (see example below)."
	echoerr "    The index sequence reported by the index sequencing reaction (INDEX 1) is showed in the second field of the ID line, which is separated by space."
	echoerr "    The index sequence reported by the main sequencing reaction (READ 1) can be identiifed in the raw sequence by matching the fixed sequences around the index sequence. For example, the index sequence is located between the fixed sequence 'CAC' and 'ATCTCG'."
	echoerr "    An example of an input read:"
	echoerr "        @NB501365:4:H3VN3BGXY:2:11101:23605:1031 1:N:0:ATCACG"
	echoerr "        TACCCTGTAGATCCGAATTTGTTGGAATTCTCGGGTGCCAAGGAACTCCAGTCACATCACGATCTCGTATGCCGTC"
	echoerr "        +"
	echoerr "        AAAAAEEEEEAEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE6<AEEEEEEEEEEEE"

	echoerr
	echoerr "OUTPUT FASTQ FORMAT"
	echoerr "    An example of an output read:"
	echoerr "        @NB501365:4:H3VN3BGXY:2:11101:23605:1031 index:ATCACG consistent_index"
	echoerr "        TACCCTGTAGATCCGAATTTGTTGGAATTCTCGGGTGCCAAGGAACTCCAGTCACATCACGATCTCGTATGCCGTC"
	echoerr "        +"
	echoerr "        AAAAAEEEEEAEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE6<AEEEEEEEEEEEE"
	echoerr

        exit
}


if [ $# -eq 0 ]; then
    echo "NO INPUT FILE. Please provide input file."
    echo "Type -h or --help to get more information."
    #usage
    exit
fi


## show usage if '-h' or '--help' is the first argument or no argument is given
case $1 in
        ""|"-h"|"--help") usage ;;
esac


## check the parameters
if [ ! -f $1 ]; then
    echo "File not found!"
    exit
fi


## run the AWK command line
cat $1 | awk '{ printf("%s",$0); n++; if(n%4==0) { printf("\n");} else { printf("\t");} }' | awk -F"\t" '{split($1,a," "); print a[1]"\t"a[2]"\t"$2"\t"$3"\t"$4}' | awk -F"\t" '{split($2,b,":"); print $1"\t"b[4]"\t"$3"\t"$4"\t"$5}' | awk -F"\t" '{if ($3 ~ /CAC[A-Z]{0,10}ATCTCG/) {where=match($3,"CAC"$2"ATCTCG"); if (where > 0) print $1" index:"$2" ""consistent_index""\n"$3"\n"$4"\n"$5} else print $1" index:"$2" ""long_insert""\n"$3"\n"$4"\n"$5}'

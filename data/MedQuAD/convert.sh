#!/bin/sh
if [ ! -d "MedQuAD-master" ]; then
  wget https://github.com/abachaa/MedQuAD/archive/master.zip && unzip master.zip
fi
cd ../.. && bin/export-data -i data/MedQuAD/MedQuAD_test_annotations.json -s data/MedQuAD/MedQuAD_test_sources.tsv -o data/MedQuAD/

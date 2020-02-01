# Annotations for WikiSection / MedQuAD / HealthQA

This folder contains additional annotations for three datasets WikiSection, MedQuAD and Health QA. As we cannot provide the original texts, it is required that you download the individual datasets into their folder and run `convert.sh` to create the document files.

If you use these annotations in your work, please cite:

```
@inproceedings{arnold2020learning,
  author = {Arnold, Sebastian and {van Aken}, Betty and Grundmann, Paul and Gers, Felix A. and L{\"o}ser, Alexander},
  title = {Learning {{Contextualized Document Representations}} for {{Healthcare Answer Retrieval}}},
  booktitle = {Proceedings of The Web Conference 2020 (WWW '20)},
  year = {2020},
  doi = {10.1145/3366423.3380208}
}
```

## Format

These files are created by `convert.sh` and contain tab-separated values with the following fields:

#### DATASET_test_docs.tsv

Document/passage full-text with one **sentence** per line.

| field     | description            |
|:----------|:-----------------------|
| `doc_id`  | A corpus-wide document ID. Sentences with same `doc_id` belong to the same document. | 
| `p_id`    | corpus-wide passage ID. sentences with same `p_id` belong to the same passage. |
| `t`       | Sequential sentence index in the range `0` to `T-1` |
| `text`    | Plain text of the sentence, non-tokenized. |

#### DATASET_test_queries.tsv

One **query-answer candidate pair** per line, referring to the passages above. 

| field            | description            |
|:-----------------|:-----------------------|
| `query_id`       | A corpus-wide query ID. |
| `relevance`      | `1` if the answer is relevant to the query, `0` if it is not relevant. Every query has at most 64 candidate answers. |
| `doc_id`         | Reference to the document ID containing the candidate answer. | 
| `p_id`           | Reference to the passage ID that is the candidate answer. |
| `question`       | The question in natural language. |
| `entity_id`      | Wikidata ID of the entity focused in the question, e.g. "Q2140130". |
| `entity_name`    | Canonical name of the entity focused in the question, e.g. "Lateral medullary syndrome". |
| `aspect_label`   | Normalized short label for the question aspect, e.g. "symptom". |
| `aspect_heading` | Slightly longer description of the question aspect from UMLS, e.g. "signs and symptoms". |

#### DATASET_test_matchzoo.tsv

Passage full-text with one **query-answer candidate pair** per line for supervised training and evaluation in MatchZoo.

| field     | description            |
|:----------|:-----------------------|
| `relevance`      | `1` if the answer is relevant to the query, `0` if it is not relevant. Every query has at most 64 candidate answers, always 10 for training data. |
| `query`       | The query in the form `entity ; aspect`. |
| `text`    | Plain text of the passage, tokenized. |

#### DATASET_train_labels.tsv

Entity/aspect training labels with one sequential **sentence position** per line.

| field            | description            |
|:-----------------|:-----------------------|
| `doc_id`         | Reference to the document ID. | 
| `p_num`          | Sequential passage index per document. |
| `t_start`        | Sequential sentence index. The current label is valid for sentence `t_start` and all following sentences until the next label. |
| `entity_ids`     | Semicolon-separated list of entities focused in the sentences. |
| `entity_names`   |Semicolon-separated list of canonical entity names focused in the sentences. |
| `aspect_labels`  | Semicolon-separated list of normalized short aspect labels describing the sentences. |
| `aspect_headings`| Semicolon-separated list of section headings describing the sentences. |

## License

The licenses of the individual datasets apply.
All additional annotations contained in this folder are released under the [Creative Commons Attribution-ShareAlike 3.0 Unported License](https://creativecommons.org/licenses/by-sa/3.0/). You should have received a copy of the license along with this work. If not, see [http://creativecommons.org/licenses/by-sa/3.0/].

# Project Report: CLI AI Chat & Search Assistant (RAG Pipeline)

> **Note to self before submitting:** Check the exact report format required
> on the course page - section names/order may need to match a template.
> Rewrite the wording below in your own voice and add anything specific to
> your implementation choices or testing before submitting.

**Course:** Programming in Java
**Project Title:** CLI AI Chat & Search Assistant (RAG Pipeline)
**Student Name:** _[fill in]_
**Enrollment / Roll No.:** _[fill in]_
**GitHub Repository:** _[fill in final public repo URL]_
**Date:** _[fill in]_

---

## 1. Objective

Build a command-line Retrieval-Augmented Generation (RAG) tool that indexes
a folder of local text documents and answers a user's natural-language
query by retrieving the most relevant passages, using classic information-
retrieval techniques (TF-IDF vectorization and cosine similarity) rather
than an external search engine. An optional stage forwards the retrieved
context to a large language model to produce a synthesized answer.

## 2. Problem Statement

Searching a personal collection of notes/FAQs/syllabi by keyword (`grep`)
misses queries that don't share exact wording with the source text, and
doesn't rank results by relevance. This project addresses that by:

1. Converting each document into a weighted term vector (TF-IDF), which
   down-weights common words and up-weights words that are distinctive to
   a document.
2. Scoring a query against every document with cosine similarity, which is
   robust to document length differences.
3. Returning only the top-k most relevant passages using an efficient
   bounded-heap selection instead of sorting the entire corpus.
4. Optionally handing those passages to an LLM to produce a direct answer
   instead of raw excerpts.

## 3. Core Java Concepts Applied

| Concept | Where it's used |
|---|---|
| Interfaces / OOP design | `DocumentReader`, `Vectorizer`, `SimilarityMetric`, `LlmClient` decouple *what* each stage does from *how* it's implemented, so e.g. the vectorizer or LLM backend can be swapped without touching `SearchEngine`. |
| Collections & Generics | `Map<String, Double>` sparse vectors; `List<Document>`; a generic `TopKSelector<T>` built on `java.util.PriorityQueue<T>`. |
| Records | `ScoredDocument` and `CliOptions` are Java `record` types for immutable data carriers. |
| I/O & NIO | `java.nio.file.Files.walk`, `Files.newBufferedReader` for streaming file content in `TextFileDocumentReader`. |
| Streams API | Filtering/sorting file paths (`Stream<Path>`), summing token counts (`IntStream`). |
| Networking / HTTP Client | `java.net.http.HttpClient` in `OllamaClient` and `OpenAiClient` to call local/remote LLM APIs with zero external dependencies. |
| Exception handling | Checked `IOException`s from file/network I/O are surfaced with actionable messages instead of stack traces; the query loop keeps running even if one LLM call fails. |
| String processing / regex | Tokenization (`Pattern`), a minimal hand-written JSON field extractor in `JsonUtil`. |

## 4. System Design / Architecture

```
DocumentReader  --reads-->  List<Document>
                                  |
                                  v
                         Vectorizer.fit(corpus)   (TF-IDF statistics)
                                  |
        query text ---> Vectorizer.vectorize() ---> query vector
                                  |
              for each doc: SimilarityMetric.similarity(query, doc)
                                  |
                          TopKSelector (bounded min-heap)
                                  |
                          top-k ScoredDocument list
                                  |
                (optional) LlmClient.generate(prompt built from passages)
                                  |
                              CLI output
```

Each stage is expressed as an interface (`model` package) with one concrete
implementation (`impl` / `llm` packages), following the assignment's
suggested design: `DocumentReader`, `Vectorizer`, `SimilarityMetric`.

### Algorithm summary

- **TF-IDF**: `tf(t, d) = count(t, d) / |d|`; `idf(t) = ln((1+N)/(1+df(t))) + 1`
  (smoothed so unseen terms don't produce a divide-by-zero); weight is the
  product `tf * idf`.
- **Cosine similarity**: `dot(a, b) / (‖a‖ * ‖b‖)` over the sparse vector
  intersection.
- **Top-k selection**: a bounded `PriorityQueue` (min-heap) of size k is
  maintained while scanning the corpus once; a new candidate only enters
  the heap if it beats the current minimum, giving `O(n log k)` instead of
  sorting all n documents (`O(n log n)`).

## 5. Implementation Highlights

- Zero third-party dependencies  the entire project builds with `javac`
  from the standard library alone, satisfying the "runnable from a terminal"
  requirement without any package manager setup.
- The optional LLM step degrades gracefully: if no `--llm` flag is passed,
  the tool never touches the network. If a call does fail (no local Ollama
  server, missing API key, connectivity issue), the error is caught and the
  tool falls back to showing retrieved passages instead of crashing.
- A small dependency-free test runner (`AllTests.java`) checks cosine
  similarity edge cases (identical vectors, disjoint vocabularies, empty
  vectors), the top-k selector's correctness, and end-to-end ranking
  behavior of the search engine.

## 6. Testing

Run:

```bash
./build.sh
java -cp out com.ragcli.AllTests
```

Test cases covered:

1. Cosine similarity of a vector with itself ≈ 1.0.
2. Cosine similarity of two disjoint-vocabulary vectors = 0.0.
3. Cosine similarity involving an empty vector = 0.0.
4. `TopKSelector` returns the correct k highest-scoring items in order.
5. `TopKSelector` behaves correctly when fewer than k items are offered.
6. End-to-end: a query about Java collections ranks a Java-related document
   above an unrelated one.
7. A query containing an out-of-vocabulary term doesn't crash and still
   matches on the terms it does recognize.

Manual testing was also done by running the CLI interactively against the
sample `data/` corpus with a range of queries and confirming the ranked
snippets matched expectations. _[Add your own additional manual test notes /
screenshots here.]_

## 7. How to Run

See `README.md` for full setup instructions. Quick start:

```bash
git clone https://github.com/AYUSHKHEDLE/java-rag-cli.git
cd java-rag-cli
./build.sh
./run.sh --dir data --topk 3
```



## 8. Possible Future Improvements

- Replace/augment TF-IDF with dense embeddings (e.g. call an embeddings
  API) behind the existing `Vectorizer` interface for semantic (not just
  keyword) matching.
- Add a `DocumentReader` for PDF/DOCX sources.
- Persist the computed index to disk so large corpora don't need to be
  re-vectorized on every run.
- Add conversational memory so follow-up queries can reference earlier
  turns.

## 9. Conclusion



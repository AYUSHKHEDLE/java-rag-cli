# java-rag-cli
# CLI AI Chat & Search Assistant (RAG Pipeline)

A command-line Retrieval-Augmented Generation (RAG) tool written in plain Java
(no external dependencies). It indexes local text documents, ranks passages
against a user's query using **TF-IDF + cosine similarity**, and prints the
most relevant results fully - offline by default. It can optionally forward
the retrieved passages to a local [Ollama](https://ollama.com) model or the
OpenAI API to synthesize a natural-language answer.

## Features

- Indexes any folder of `.txt` / `.md` files.
- Pure-JDK TF-IDF vectorizer (sparse `Map<String, Double>` vectors, no libraries).
- Cosine similarity ranking with a bounded top-k min-heap (`java.util.PriorityQueue`)
  instead of sorting the whole corpus.
- Interactive command-line query loop.
- Optional LLM step via `java.net.http.HttpClient` - works with a local Ollama
  server or the OpenAI Chat Completions API. Entirely optional; retrieval
  works with zero network access.
- Zero external/third-party dependencies - only the JDK standard library.
- Includes a small dependency-free test suite.

## Requirements

- **JDK 17 or later** (developed and tested on JDK 21). Check your version:
  ```bash
  java -version
  javac -version
  ```
  If you don't have a JDK, install one, e.g. on Ubuntu/Debian:
  ```bash
  sudo apt-get update
  sudo apt-get install -y default-jdk
  ```
  or download a build from [Adoptium](https://adoptium.net/).
- No build tool (Maven/Gradle) is required — the project compiles directly
  with `javac`. No internet access is required for the offline (default)
  retrieval mode.
- **Optional**, only if you want LLM-generated answers instead of raw
  passages:
  - [Ollama](https://ollama.com) installed locally with a model pulled
    (e.g. `ollama pull llama3`), **or**
  - An OpenAI API key exported as the `OPENAI_API_KEY` environment variable.

## Project Structure

```
java-rag-cli/
├── README.md
├── REPORT.md
├── build.sh                     # convenience compile script
├── run.sh                       # convenience run script
├── data/                        # sample corpus (swap in your own files)
│   ├── syllabus.txt
│   ├── faq.txt
│   └── notes-collections.txt
└── src/
    ├── main/java/com/ragcli/
    │   ├── Main.java            # CLI entry point
    │   ├── CliOptions.java      # argument parsing
    │   ├── core/                # Document, ScoredDocument, SearchEngine
    │   ├── model/               # DocumentReader / Vectorizer / SimilarityMetric interfaces
    │   ├── impl/                # TextFileDocumentReader, TfIdfVectorizer, CosineSimilarity
    │   ├── util/                # TopKSelector (bounded priority-queue top-k utility)
    │   └── llm/                 # LlmClient interface + Ollama/OpenAI HttpClient implementations
    └── test/java/com/ragcli/
        └── AllTests.java        # dependency-free test runner
```

## Setup & Run

### 1. Clone the repository

```bash
git clone https://github.com/AYUSHKHEDLE/java-rag-cli.git
cd <java-rag-cli>
```

### 2. Compile

From the project root:

```bash
mkdir -p out
find src/main -name "*.java" > sources.txt
javac -d out @sources.txt
```

Or simply run the provided script:

```bash
./build.sh
```

### 3. Run (offline / retrieval-only mode)

```bash
java -cp out com.ragcli.Main --dir data --topk 3
```

You'll see an interactive prompt:

```
query> how do I run the project without a GUI
-- Top matches --
1. [0.2724] faq.txt
   ...
```

Type any question relevant to the indexed documents, or `exit` / `quit` to
stop. Use `Ctrl+D` to send EOF and exit as well.

To index your **own** documents, drop `.txt` or `.md` files into a folder
and point `--dir` at it:

```bash
java -cp out com.ragcli.Main --dir /path/to/my/notes --topk 5
```

### 4. (Optional) Run with an LLM-generated answer

**Using a local Ollama server:**

```bash
ollama pull llama3          # one-time setup
ollama serve                # if not already running
java -cp out com.ragcli.Main --dir data --llm ollama --model llama3
```

**Using OpenAI:**

```bash
export OPENAI_API_KEY=sk-...
java -cp out com.ragcli.Main --dir data --llm openai --model gpt-4o-mini
```

If the LLM call fails for any reason (no server running, bad key, no
network), the tool prints an error and falls back to showing the raw
retrieved passages - it never crashes the whole session.

### 5. Command-line options

| Flag       | Default | Description                                      |
|------------|---------|---------------------------------------------------|
| `--dir`    | `data`  | Directory of `.txt`/`.md` files to index          |
| `--topk`   | `3`     | Number of passages to retrieve per query           |
| `--llm`    | `none`  | `none`, `ollama`, or `openai`                      |
| `--model`  | *(provider default)* | Model name to pass to the LLM provider |
| `--help`   | —       | Print usage and exit                               |

### 6. Run the test suite

```bash
find src/main src/test -name "*.java" > sources.txt
javac -d out @sources.txt
java -cp out com.ragcli.AllTests
```

Expected output ends with `7 passed, 0 failed.`

## How It Works (Architecture)

1. **`DocumentReader`** (interface) → `TextFileDocumentReader` walks a
   directory with `java.nio.file.Files.walk`, reading each file with a
   `BufferedReader` into a `Document` (id, title, content).
2. **`Vectorizer`** (interface) → `TfIdfVectorizer` tokenizes text, computes
   term frequency (TF) and smoothed inverse document frequency (IDF), and
   caches a sparse `Map<String, Double>` vector per document.
3. **`SimilarityMetric`** (interface) → `CosineSimilarity` scores two sparse
   vectors by dot product over vector magnitudes.
4. **`SearchEngine`** ties the three together: it vectorizes the query and
   scores it against every cached document vector, using `TopKSelector`  a
   generic wrapper around `java.util.PriorityQueue`  to keep only the
   top-k results in `O(n log k)` instead of sorting everything.
5. **`LlmClient`** (interface, optional) → `OllamaClient` / `OpenAiClient`
   send the retrieved passages plus the question as a prompt over
   `java.net.http.HttpClient` and return the model's answer.

## Design Notes

- All core behavior is expressed through interfaces (`DocumentReader`,
  `Vectorizer`, `SimilarityMetric`, `LlmClient`) so implementations can be
  swapped  e.g. a database-backed `DocumentReader`, or an embeddings-based
  `Vectorizer`, without touching `SearchEngine` or `Main`.
- No external libraries are used anywhere, including for the HTTP calls and
  the (minimal, hand-written) JSON field extraction in `llm/JsonUtil.java`.
  This keeps the project runnable with nothing but a stock JDK.
- The retrieval pipeline is exercised by `src/test/java/com/ragcli/AllTests.java`,
  a small assertion-based runner with no testing framework dependency.

## Limitations / Possible Extensions

- TF-IDF is a keyword-overlap method; it won't recognize synonyms or
  paraphrases the way dense embeddings would. Swapping in an
  embeddings-based `Vectorizer` (e.g. calling an embeddings API) is the
  natural next step, and the interface is already designed for it.
- Only `.txt` and `.md` files are read; a PDF/DOCX-aware `DocumentReader`
  could be added by implementing the same interface.
- The JSON handling in `llm/JsonUtil.java` is intentionally minimal (it only
  extracts one flat string field) - sufficient for the Ollama/OpenAI
  response shapes used here, not a general JSON parser.

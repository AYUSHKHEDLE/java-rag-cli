package com.ragcli;

import com.ragcli.core.Document;
import com.ragcli.core.ScoredDocument;
import com.ragcli.core.SearchEngine;
import com.ragcli.impl.CosineSimilarity;
import com.ragcli.impl.TextFileDocumentReader;
import com.ragcli.impl.TfIdfVectorizer;
import com.ragcli.llm.LlmClient;
import com.ragcli.llm.OllamaClient;
import com.ragcli.llm.OpenAiClient;
import com.ragcli.model.DocumentReader;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * CLI AI Chat & Search Assistant (RAG Pipeline).
 *
 * Indexes local text documents, ranks them against a query using TF-IDF +
 * cosine similarity, and prints the most relevant passages. Optionally
 * forwards the retrieved context to a local Ollama model or the OpenAI API
 * to synthesize a natural-language answer ("--llm ollama" / "--llm openai").
 *
 * Usage:
 *   java -cp out com.ragcli.Main [--dir data] [--topk 3] [--llm none|ollama|openai] [--model NAME]
 */
public class Main {

    public static void main(String[] args) {
        CliOptions options = CliOptions.parse(args);

        System.out.println("=== CLI RAG Assistant ===");
        System.out.println("Indexing documents from: " + options.dir().toAbsolutePath());

        DocumentReader reader = new TextFileDocumentReader();
        List<Document> corpus;
        try {
            corpus = reader.readDocuments(options.dir());
        } catch (Exception e) {
            System.err.println("Failed to read documents from " + options.dir() + ": " + e.getMessage());
            return;
        }

        if (corpus.isEmpty()) {
            System.err.println("No .txt/.md documents found in " + options.dir()
                    + ". Add some files and try again.");
            return;
        }

        SearchEngine engine = new SearchEngine(corpus, new TfIdfVectorizer(), new CosineSimilarity());
        System.out.println("Indexed " + engine.size() + " document(s). Top-k = " + options.topK()
                + ", LLM mode = " + options.llmMode());

        LlmClient llmClient = buildLlmClient(options);

        System.out.println("Type a question and press Enter. Type 'exit' or 'quit' to stop.\n");

        try (BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            while (true) {
                System.out.print("query> ");
                System.out.flush();
                String query = stdin.readLine();
                if (query == null) {
                    break; // EOF (e.g. piped input ran out)
                }
                query = query.trim();
                if (query.isEmpty()) {
                    continue;
                }
                if (query.equalsIgnoreCase("exit") || query.equalsIgnoreCase("quit")) {
                    break;
                }

                handleQuery(engine, llmClient, query, options.topK());
            }
        } catch (Exception e) {
            System.err.println("Error reading input: " + e.getMessage());
        }

        System.out.println("Goodbye.");
    }

    private static void handleQuery(SearchEngine engine, LlmClient llmClient, String query, int topK) {
        List<ScoredDocument> results = engine.search(query, topK);

        if (results.isEmpty() || results.get(0).score() == 0.0) {
            System.out.println("No relevant passages found for that query.\n");
            return;
        }

        System.out.println("\n-- Top matches --");
        for (int i = 0; i < results.size(); i++) {
            ScoredDocument sd = results.get(i);
            System.out.printf(Locale.US, "%d. [%.4f] %s%n   %s%n",
                    i + 1, sd.score(), sd.document().getTitle(), sd.document().snippet(180));
        }

        if (llmClient != null) {
            System.out.println("\n-- Generating answer --");
            String prompt = buildPrompt(query, results);
            try {
                String answer = llmClient.generate(prompt);
                System.out.println(answer);
            } catch (Exception e) {
                System.err.println("LLM call failed (falling back to retrieval-only): " + e.getMessage());
            }
        }
        System.out.println();
    }

    private static String buildPrompt(String query, List<ScoredDocument> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("Answer the question using ONLY the context passages below. ")
          .append("If the answer isn't in the context, say you don't know.\n\n");
        for (ScoredDocument sd : results) {
            sb.append("Passage from \"").append(sd.document().getTitle()).append("\":\n");
            sb.append(sd.document().getContent()).append("\n\n");
        }
        sb.append("Question: ").append(query).append("\nAnswer:");
        return sb.toString();
    }

    private static LlmClient buildLlmClient(CliOptions options) {
        return switch (options.llmMode()) {
            case "ollama" -> new OllamaClient(options.model() != null ? options.model() : "llama3");
            case "openai" -> new OpenAiClient(options.model() != null ? options.model() : "gpt-4o-mini");
            default -> null; // offline, retrieval-only mode
        };
    }
}

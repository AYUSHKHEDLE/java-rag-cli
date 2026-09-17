package com.ragcli;

import java.nio.file.Path;

/**
 * Parsed command-line options.
 *
 *   --dir <path>       directory of .txt/.md documents to index (default: "data")
 *   --topk <n>          number of passages to retrieve per query (default: 3)
 *   --llm <mode>         "none" (default), "ollama", or "openai"
 *   --model <name>       model name to pass to the chosen LLM provider
 */
public record CliOptions(Path dir, int topK, String llmMode, String model) {

    public static CliOptions parse(String[] args) {
        Path dir = Path.of("data");
        int topK = 3;
        String llmMode = "none";
        String model = null;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--dir" -> dir = Path.of(requireValue(args, ++i, "--dir"));
                case "--topk" -> topK = parsePositiveInt(requireValue(args, ++i, "--topk"));
                case "--llm" -> llmMode = requireValue(args, ++i, "--llm").toLowerCase();
                case "--model" -> model = requireValue(args, ++i, "--model");
                case "--help", "-h" -> {
                    printUsage();
                    System.exit(0);
                }
                default -> {
                    System.err.println("Unknown argument: " + arg);
                    printUsage();
                    System.exit(1);
                }
            }
        }

        if (!llmMode.equals("none") && !llmMode.equals("ollama") && !llmMode.equals("openai")) {
            System.err.println("Invalid --llm value: " + llmMode + " (expected none|ollama|openai)");
            System.exit(1);
        }

        return new CliOptions(dir, topK, llmMode, model);
    }

    private static String requireValue(String[] args, int index, String flag) {
        if (index >= args.length) {
            System.err.println("Missing value for " + flag);
            printUsage();
            System.exit(1);
        }
        return args[index];
    }

    private static int parsePositiveInt(String value) {
        try {
            int n = Integer.parseInt(value);
            if (n <= 0) throw new NumberFormatException();
            return n;
        } catch (NumberFormatException e) {
            System.err.println("--topk must be a positive integer, got: " + value);
            System.exit(1);
            return -1; // unreachable
        }
    }

    private static void printUsage() {
        System.out.println("""
                Usage: java -cp out com.ragcli.Main [options]
                  --dir <path>     Directory of .txt/.md files to index (default: data)
                  --topk <n>       Number of passages to retrieve per query (default: 3)
                  --llm <mode>     none | ollama | openai (default: none)
                  --model <name>   Model name for the chosen LLM provider
                  --help           Show this message
                """);
    }
}

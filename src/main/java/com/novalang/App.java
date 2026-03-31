package com.novalang;

import com.novalang.compiler.Translator;
import com.novalang.vm.NovaVM;
import com.novalang.vm.NovaVMFactory;
import com.novalang.vm.DefaultNovaVMFactory;
import com.novalang.instructions.Instruction;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.LogManager;


/**
 * Main application entry point responsible for the Dependency Injection (DI) wiring.
 * Fulfills the "DI via factory" requirement for the coursework.
 */
public class App {
    private final static String PATH = "sample-files";

    static void main(String... args) {
        // Disable all logging to console
        LogManager.getLogManager().reset();

        if (args.length != 1) {
            System.err.println("Usage: java com.novalang.App <program.nvl>");
            System.err.println("Example: java com.novalang.App test.nvl");
            System.exit(1);
        }

        // --- Dependency Injection / Wiring Phase ---
        Translator translator = null; // TODO
        NovaVMFactory vmFactory = null; // TODO


        try {
            // Check filename is valid
            try {
                Path f = validate(args[0]);

                // 1. Translation: Load and parse the source file
                System.out.printf("Translating source file: %s\n", args[0]);
                long translationStart = System.nanoTime();
                // TODO — translate the file
                long translationEnd = System.nanoTime();

                // 2. Data Retrieval
                List<Instruction> program = null; // TODO
                Map<String, Integer> labels = null; // TODO

                // 3. VM Creation using Factory (DI)
                NovaVM vm = null; // TODO

                // 4. Run
                System.out.println("\n--- Program Execution ---");
                long executionStart = System.nanoTime();
                vm.run();
                long executionEnd = System.nanoTime();

                // Display timing information
                double translationTime = (translationEnd - translationStart) / 1_000_000.0;
                double executionTime = (executionEnd - executionStart) / 1_000_000.0;
                double totalTime = (executionEnd - translationStart) / 1_000_000.0;

                System.out.println("\n--- Timing Statistics ---");
                System.out.printf("Translation time: %.3f ms\n", translationTime);
                System.out.printf("Execution time:   %.3f ms\n", executionTime);
                System.out.printf("Total time:       %.3f ms\n", totalTime);
            } catch (IOException | SecurityException | IllegalArgumentException ex) {
                System.err.println(ex.getMessage());
                System.exit(1);
            }

        } catch (Exception e) {
            System.err.println("\n*** Fatal Error ***");
            System.err.println("Details: " + e.getMessage());
            // Only print stack trace for non-runtime errors
            if (!(e instanceof RuntimeException)) {
                e.printStackTrace();
            }
            System.exit(1);
        }
    }

    static Path validate(String filename) throws IOException {
        File baseDir = new File(PATH);
        File targetFile = new File(baseDir, filename).getCanonicalFile();
        if (!targetFile.getPath().startsWith(baseDir.getCanonicalPath())) {
            throw new SecurityException("Invalid file path");
        }
        if (!targetFile.exists()) {
            throw new IllegalArgumentException("File does not exist: " + targetFile.getPath());
        }
        return targetFile.toPath();
    }
}
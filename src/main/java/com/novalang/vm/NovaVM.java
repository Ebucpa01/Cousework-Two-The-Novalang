package com.novalang.vm;

import com.novalang.instructions.Instruction;
import com.novalang.instructions.RetInstr;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;

// Main VM Class
@RequiredArgsConstructor // Lombok: Generates constructor for final fields (program, labels)
@Getter // Lombok: Generates getters for fields
@Log // Lombok: Generates a static final Logger field named 'log'
public class NovaVM {

    // Dependencies injected via the generated constructor (DI requirement)
    private final List<Instruction> program;
    private final Map<String, Integer> labels;

    // Internal state
    private final RegisterManager registers = new RegisterManager();
    private final Stack<Integer> callStack = new Stack<>();

    // Concurrency components (Required for async/await)
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Set<Future<?>> asyncTasks = Collections.synchronizedSet(new HashSet<>());

    /**
     * Executes a single thread of the NovaLang program. Used for main thread and async tasks.
     *
     * @param initialPC    The starting program counter.
     * @param contextStack The call stack specific to this thread (main or new task stack).
     */
    private void executeThread(int initialPC, Stack<Integer> contextStack) {
        int pc = initialPC;

        while (pc >= 0 && pc < program.size()) {
            Instruction instruction = program.get(pc);

            // Skip null com.novalang.instructions (comments/empty lines)
            if (instruction == null) {
                pc++;
                continue;
            }

            // Create context for the instruction
            // TODO

            // Execute the instruction and get the next PC
            Optional<Integer> nextPC = instruction.execute(context);
            // TODO

            // Task termination check: 'ret' acts as halt for async tasks
            if (contextStack != callStack && instruction instanceof RetInstr) {
                break;
            }
        }
    }

    /**
     * The core fetch-decode-execute loop for the main thread.
     */
    public void run() {
        log.info("VM starting execution on main thread...");
        //TODO

        // Clean up resources
        executor.shutdownNow();
        log.info("\n--- Program Halted ---");
        log.info("Final Register State: " + registers.dumpRegisters());
    }

    /**
     * Starts a new asynchronous task (async L) using a Virtual Thread.
     *
     * @param startPC The program counter where the task should start.
     */
    public void startAsyncTask(int startPC) {
        // Concurrency: Use Virtual Threads (Required)
        Future<?> future = executor.submit(() -> {
            // New stack for the async task
            // TODO
        });
        // TODO

        log.info("VM started async task at PC " + startPC + ". Total running tasks: " + asyncTasks.size());
    }

    /**
     * Waits for all currently tracked asynchronous tasks to complete (await).
     */
    public void awaitAllTasks() {
        // Step 2: Replace System.out.println with log.info
        log.info("VM blocking main thread, waiting for " + asyncTasks.size() + " async tasks to complete...");

        // Concurrency: Wait for all futures in the set to complete
        for (Future<?> task : asyncTasks) {
            try {
                task.get(); // Blocks until the task completes
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // Step 2: Replace System.err.println with log.severe
                log.log(Level.SEVERE, "Await interrupted.", e);
            } catch (ExecutionException e) {
                // Step 2: Replace System.err.println with log.severe
                log.log(Level.SEVERE, "Async task failed during execution: " + e.getCause().getMessage(), e.getCause());
            }
        }

        // Clear the task set for the next 'async' block
        asyncTasks.clear();
        // Step 2: Replace System.out.println with log.info
        log.info("All async tasks completed. Main thread continuing.");
    }
}
package com.novalang.instructions;

import com.novalang.vm.ProgramContext;
import com.novalang.compiler.Translator;

import java.util.Optional;

public record SleepInstr(String label, int milliseconds) implements Instruction {

    // Reflection Factory Constructor
    public SleepInstr(String label, Object... operands) {
        this(label,
                Translator.getConstantValue(operands, 0).orElse(0));
        if (!Translator.getConstantValue(operands, 0).isPresent() ||
                !Translator.ensureOperandCount(operands, 1)) {
            throw new IllegalArgumentException("Invalid operands for SleepInstr");
        }
    }

    @Override
    public Optional<Integer> execute(ProgramContext context) {
        try {
            // Sleep the current thread (can be a Virtual Thread)
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            // Restore interrupted status
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread was interrupted while sleeping.", e);
        }
        // Continue execution
        return Optional.of(context.pc() + 1);
    }
}
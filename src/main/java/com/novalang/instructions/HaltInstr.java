package com.novalang.instructions;

import com.novalang.vm.ProgramContext;
import com.novalang.compiler.Translator;

import java.util.Optional;

public record HaltInstr(String label) implements Instruction {

    // Reflection Factory Constructor
    public HaltInstr(String label, Object... operands) {
        this(label);
        if (!Translator.ensureOperandCount(operands, 0)) {
            throw new IllegalArgumentException("Invalid operands for HaltInstr");
        }
    }

    @Override
    public Optional<Integer> execute(ProgramContext context) {
        // Halt the current thread's execution loop
        return Optional.of(-1);
    }
}
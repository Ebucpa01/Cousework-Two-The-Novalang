package com.novalang.instructions;

import com.novalang.vm.ProgramContext;
import com.novalang.compiler.Translator;

import java.util.Optional;

public record AwaitInstr(String label) implements Instruction {

    // Reflection Factory Constructor
    public AwaitInstr(String label, Object... operands) {
        this(label);
        if (!Translator.ensureOperandCount(operands, 0)) {
            throw new IllegalArgumentException("Invalid operands for AwaitInstr");
        }
    }

    @Override
    public Optional<Integer> execute(ProgramContext context) {
        // Concurrency: Block the calling thread until all tasks complete
        context.vm().awaitAllTasks();
        return Optional.of(context.pc() + 1);
    }
}
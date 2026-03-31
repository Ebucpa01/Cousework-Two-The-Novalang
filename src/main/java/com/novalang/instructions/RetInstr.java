package com.novalang.instructions;

import com.novalang.vm.ProgramContext;
import com.novalang.compiler.Translator;

import java.util.Optional;

public record RetInstr(String label) implements Instruction {

    // Reflection Factory Constructor
    public RetInstr(String label, Object... operands) {
        this(label);
        if (!Translator.ensureOperandCount(operands, 0)) {
            throw new IllegalArgumentException("Invalid operands for RetInstr");
        }
    }

    @Override
    public Optional<Integer> execute(ProgramContext context) {
        if (context.callStack().isEmpty()) {
            return Optional.of(-1);
        }
        return Optional.of(context.callStack().pop());
    }
}
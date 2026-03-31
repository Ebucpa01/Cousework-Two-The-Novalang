package com.novalang.instructions;

import com.novalang.vm.ProgramContext;
import com.novalang.compiler.Translator;

import java.util.Optional;

public record CallInstr(String label, int targetPC) implements Instruction {

    // Reflection Factory Constructor
    public CallInstr(String label, Object... operands) {
        this(label,
                Translator.getTargetPC(operands, 0).orElse(0)); // targetPC must be resolved by Translator
        if (!Translator.getTargetPC(operands, 0).isPresent() ||
                !Translator.ensureOperandCount(operands, 1)) {
            throw new IllegalArgumentException("Invalid operands for CallInstr");
        }
    }

    @Override
    public Optional<Integer> execute(ProgramContext context) {
        // Push return address (PC + 1) onto the call stack
        context.callStack().push(context.pc() + 1);
        return Optional.of(targetPC);
    }
}
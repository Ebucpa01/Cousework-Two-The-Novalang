package com.novalang.instructions;

import com.novalang.vm.ProgramContext;
import com.novalang.compiler.Translator;

import java.util.Optional;

public record SetInstr(String label, int rDest, int constant) implements Instruction {

    // Reflection Factory Constructor: takes rDest (reg index) and x (constant value)
    public SetInstr(String label, Object... operands) {
        this(label,
                Translator.getRegisterIndex(operands, 0).orElse(0),
                Translator.getConstantValue(operands, 1).orElse(0));
        if (!Translator.getRegisterIndex(operands, 0).isPresent() ||
                !Translator.getConstantValue(operands, 1).isPresent() ||
                !Translator.ensureOperandCount(operands, 2)) {
            throw new IllegalArgumentException("Invalid operands for SetInstr");
        }
    }

    @Override
    public Optional<Integer> execute(ProgramContext context) {
        // Set register rDest to the constant value x
        if (context.registers().set(rDest, constant)) {
            return Optional.of(context.pc() + 1);
        }
        return Optional.empty();
    }
}
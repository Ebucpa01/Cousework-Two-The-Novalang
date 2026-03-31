package com.novalang.instructions;

import com.novalang.vm.ProgramContext;
import com.novalang.compiler.Translator;

import java.util.Optional;

public record JnzInstr(String label, int rCond, int targetPC) implements Instruction {

    // Reflection Factory Constructor: takes rCond (reg index) and targetPC (resolved index)
    public JnzInstr(String label, Object... operands) {
        this(label,
                Translator.getRegisterIndex(operands, 0).orElse(0),
                Translator.getTargetPC(operands, 1).orElse(0)); // targetPC must be resolved by Translator
        if (!(Translator.getRegisterIndex(operands, 0).isPresent() &&
                Translator.getTargetPC(operands, 1).isPresent() &&
                Translator.ensureOperandCount(operands, 2))) {
            throw new IllegalArgumentException("Invalid operands for JnzInstr");
        }
    }

    @Override
    public Optional<Integer> execute(ProgramContext context) {
        return context.registers().get(rCond).map(val -> {
            if (val != 0) {
                return targetPC;
            } else {
                return context.pc() + 1;
            }
        });
    }
}
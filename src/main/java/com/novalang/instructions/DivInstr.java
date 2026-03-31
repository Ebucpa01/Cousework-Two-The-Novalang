package com.novalang.instructions;

import com.novalang.vm.ProgramContext;
import com.novalang.compiler.Translator;

import java.util.Optional;

public record DivInstr(String label, int rDest, int rSrc1, int rSrc2) implements Instruction {

    // Reflection Factory Constructor
    public DivInstr(String label, Object... operands) {
        this(label,
                Translator.getRegisterIndex(operands, 0).orElse(0),
                Translator.getRegisterIndex(operands, 1).orElse(0),
                Translator.getRegisterIndex(operands, 2).orElse(0));
        if (!Translator.getRegisterIndex(operands, 0).isPresent() ||
                !Translator.getRegisterIndex(operands, 1).isPresent() ||
                !Translator.getRegisterIndex(operands, 2).isPresent() ||
                !Translator.ensureOperandCount(operands, 3)) {
            throw new IllegalArgumentException("Invalid operands for DivInstr");
        }
    }

    @Override
    public Optional<Integer> execute(ProgramContext context) {
        return context.registers().get(rSrc1).flatMap(val1 ->
                context.registers().get(rSrc2).flatMap(val2 -> {
                    // Correctness: Robust Error Handling (Divide-by-zero)
                    if (val2 == 0) {
                        return Optional.empty();
                    }
                    if (context.registers().set(rDest, val1 / val2)) {
                        return Optional.of(context.pc() + 1);
                    }
                    return Optional.empty();
                }));
    }
}
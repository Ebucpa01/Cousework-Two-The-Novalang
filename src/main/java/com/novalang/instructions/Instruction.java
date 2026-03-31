package com.novalang.instructions;

import com.novalang.vm.ProgramContext;

import java.util.Optional;

// 1. Sealed Interface (Design & Quality) - All instruction classes must be listed here.
public sealed interface Instruction permits AddInstr, PrintInstr {

    /**
     * Executes the instruction logic on the given VM context.
     *
     * @param context The execution context (VM state, registers, call stack, etc.)
     * @return The next program counter (PC) value in an Optional. Returns Optional.empty() if an error occurs.
     * Return Optional.of(-1) if the thread should halt normally.
     */
    Optional<Integer> execute(ProgramContext context);

    /**
     * Retrieves the label associated with this instruction line.
     */
    String label();
}
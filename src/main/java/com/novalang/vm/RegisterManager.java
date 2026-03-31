package com.novalang.vm;

import java.util.*;
import java.util.stream.Collectors;

// Utility class to manage 32 registers
public record RegisterManager(int[] registers) {
    public RegisterManager() {
        this(new int[32]); // Registers r0..r31, default value 0
    }

    // Validation for register index (Correctness)
    private boolean isValidIndex(int index) {
        return index >= 0 && index < registers.length;
    }

    public Optional<Integer> get(int index) {
        if (!isValidIndex(index)) {
            return Optional.empty();
        }
        return Optional.of(registers[index]);
    }

    public boolean set(int index, int value) {
        if (!isValidIndex(index)) {
            return false;
        }
        registers[index] = value;
        return true;
    }

    // Helper for debugging/testing
    public String dumpRegisters() {
        return Arrays.stream(registers).mapToObj(String::valueOf).collect(Collectors.joining(", ", "[", "]"));
    }
}
package com.novalang.vm;

import com.novalang.instructions.Instruction;

import java.util.List;
import java.util.Map;

/**
 * Default implementation of NovaVMFactory.
 * This class handles the specific construction of the NovaVM.
 * The App class wires this factory, achieving clear separation of concerns.
 */
public class DefaultNovaVMFactory implements NovaVMFactory {
    // Simple constructor for instantiation in the App class (DI container replacement)


    @Override
    public NovaVM create(List<Instruction> program, Map<String, Integer> labels) {
        // Delegates construction to the NovaVM's Lombok-generated constructor
        // TODO
    }
}
package com.novalang.vm;

import java.util.*;

public record ProgramContext(
        RegisterManager registers,
        int pc,
        Map<String, Integer> labels,
        Stack<Integer> callStack,
        NovaVM vm // Reference back to the VM for system actions (like starting async tasks)
) {
}
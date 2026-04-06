package com.novalang.compiler;

import com.novalang.instructions.Instruction;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.java.Log;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.lang.String.format;

/**
 * Custom exception thrown during NovaLang source code translation.
 * Indicates errors such as syntax issues, undefined labels, duplicate labels,
 * unknown opcodes, or reflection failures during instruction instantiation.
 */
class TranslationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public TranslationException(String message, Throwable cause) {
        super(message, cause);
    }

    public TranslationException(String message) {
        super(message);
    }
}

/**
 * Parses NovaLang source code into a linear list of Instruction objects and a map of labels.
 * Uses reflection to dynamically create Instruction instances based on the opcode name.
 * Fulfills the "Reflection-based Instruction Factory" requirement.
 */
@Getter
@Log
@Accessors(fluent = true)
public class Translator {

    private static final String INSTRUCTION_PACKAGE = "com.novalang.instructions.";

    private final Map<String, Integer> labels = new HashMap<>();

    private final List<Instruction> program = new ArrayList<>();

    public Translator() {
        log.info("Translator initialized.");
    }

    public static Optional<Integer> getRegisterIndex(Object[] operands, int index) {
        if (!ensureOperandIsType(operands, index, String.class, "register index (rX)")) {
            return Optional.empty();
        }
        var token = (String) operands[index];
        if (!token.toLowerCase().startsWith("r") || token.length() <= 1) {
            log.warning(format("Invalid register token: %s. Must be in format 'rX'.", token));
            return Optional.empty();
        }
        try {
            var regIndex = Integer.parseInt(token.substring(1));
            if (regIndex < 0 || regIndex > 31) {
                log.warning(format("Register index %d is out of range (0-31).", regIndex));
                return Optional.empty();
            }
            return Optional.of(regIndex);
        } catch (NumberFormatException e) {
            log.warning(format("Register index is not a number: %s %s", token, e));
            return Optional.empty();
        }
    }

    public static Optional<Integer> getConstantValue(Object[] operands, int index) {
        if (!ensureOperandIsType(operands, index, String.class, "constant value (X)")) {
            return Optional.empty();
        }
        var token = (String) operands[index];
        try {
            return Optional.of(Integer.parseInt(token));
        } catch (NumberFormatException e) {
            log.warning(format("Constant value is not an integer: %s %s", token, e));
            return Optional.empty();
        }
    }

    public static Optional<Integer> getTargetPC(Object[] operands, int index) {
        if (!ensureOperandIsType(operands, index, Integer.class, "resolved PC index")) {
            return Optional.empty();
        }
        return Optional.of((Integer) operands[index]);
    }

    public static boolean ensureOperandCount(Object[] operands, int expected) {
        if (operands.length != expected) {
            log.warning(format("Incorrect number of operands. Expected %d, found %d.", expected, operands.length));
            return false;
        }
        return true;
    }

    private static boolean ensureOperandIsType(Object[] operands, int index, Class<?> expectedType, String description) {
        if (index >= operands.length) {
            log.warning(format("Missing required operand at position %d (%s).", (index + 1), description));
            return false;
        }
        return switch (expectedType.getSimpleName()) {
            case "Integer" -> {
                if (!(operands[index] instanceof Integer)) {
                    log.warning(format("Operand at index %d is not an Integer, but expected type is Integer. This might be a pre-resolved label.", index));
                    yield false;
                }
                yield true;
            }
            case "String" -> {
                if (!(operands[index] instanceof String)) {
                    log.warning(format("Operand at index %d is not a String, but expected type is String. This might be a raw string token.", index));
                    yield false;
                }
                yield true;
            }
            default -> true;
        };
    }

    public void translate(Path path) throws IOException {
        log.info(format("Starting translation for file: %s", path));

        labels.clear();
        program.clear();

        var lines = Files.readAllLines(path);
        log.info(format("Read %d lines from file.", lines.size()));

        log.info("Pass 1: Collecting labels.");
        collectLabels(lines);
        log.info(format("Collected %d labels: %s", labels.size(), labels));

        log.info("Pass 2: Parsing and resolving com.novalang.instructions.");
        parseInstructions(lines);
        log.info(format("Translation complete. Generated %d com.novalang.instructions.", program.size()));
    }

    private void collectLabels(List<String> lines) {
        for (var i = 0; i < lines.size(); i++) {
            var line = lines.get(i).trim();
            if (line.isEmpty() || line.startsWith(";") || line.startsWith("#")) {
                log.info(format("Skipping empty or comment line at PC %d: %s", i, line));
                continue;
            }

            var parts = line.split("\\s+");
            var firstToken = parts[0];

            if (!isOpcode(firstToken)) {
                if (labels.containsKey(firstToken)) {
                    throw new TranslationException("Duplicate label found: " + firstToken + " at line " + (i + 1));
                }
                labels.put(firstToken, i);
                log.info(format("Found label '%s' at PC index %d", firstToken, i));
            } else {
                log.info(format("Token '%s' at PC %d is an opcode, not a label.", firstToken, i));
            }
        }
    }

    private boolean isOpcode(String token) {
        return resolveOpcodeClass(token).isPresent();
    }

    private Optional<Class<?>> resolveOpcodeClass(String token) {
        if (token == null || token.isEmpty()) {
            log.info("Token is null or empty, not an opcode.");
            return Optional.empty();
        }
        var className = token.substring(0, 1).toUpperCase() + token.substring(1) + "Instr";
        log.info(format("Token %s trying to resolve %s", token, className));
        try {
            var clazz = Class.forName(INSTRUCTION_PACKAGE + className);
            log.info(format("Token '%s' resolved to opcode.", token));
            return Optional.of(clazz);
        } catch (ClassNotFoundException e) {
            log.info(format("Token '%s' is not an opcode.", token));
            return Optional.empty();
        }
    }

    private void parseInstructions(List<String> lines) {
        for (var i = 0; i < lines.size(); i++) {
            var line = lines.get(i).trim();
            log.info(format("Parsing line %d: '%s'", i + 1, line));
            if (line.isEmpty() || line.startsWith(";") || line.startsWith("#")) {
                program.add(null);
                log.info(format("Added null for empty or comment line at PC %d.", i));
                continue;
            }

            var tokens = line.split("\\s+");

            var label = "";
            String opcode;
            String[] operandTokens;

            if (isOpcode(tokens[0])) {
                opcode = tokens[0];
                operandTokens = Arrays.copyOfRange(tokens, 1, tokens.length);
                log.info(format("Line %d starts with opcode '%s'.", i + 1, opcode));
            } else if (tokens.length > 1 && isOpcode(tokens[1])) {
                label = tokens[0];
                opcode = tokens[1];
                operandTokens = Arrays.copyOfRange(tokens, 2, tokens.length);
                log.info(format("Line %d has label '%s' and opcode '%s'.", i + 1, label, opcode));
            } else {
                log.warning(format("Invalid syntax or unknown opcode/label at line %d: %s", (i + 1), line));
                throw new TranslationException("Invalid syntax or unknown opcode/label at line " + (i + 1) + ": " + line);
            }

            var fullClassName = INSTRUCTION_PACKAGE + opcode.substring(0, 1).toUpperCase() + opcode.substring(1) + "Instr";

            try {
                var clazz = Class.forName(fullClassName);
                log.info(format("Resolved opcode '%s' to class '%s'.", opcode, fullClassName));

                var operands = resolveOperands(opcode, operandTokens, labels);
                log.info(format("Resolved operands for opcode '%s': %s", opcode, Arrays.toString(operands)));

                var constructor = clazz.getConstructor(String.class, Object[].class);

                var instruction = (Instruction) constructor.newInstance(label, operands);
                program.add(instruction);
                log.info(format("Instantiated instruction: %s at PC index %d", instruction.getClass().getSimpleName(), i));

            } catch (ClassNotFoundException e) {
                log.warning(format("Unknown opcode: %s (Class %s not found) at line %d. %s", opcode, fullClassName, (i + 1), e));
                throw new TranslationException("Unknown opcode: " + opcode + " (Class " + fullClassName + " not found)", e);
            } catch (NoSuchMethodException e) {
                log.warning(format("Instruction class %s must have a public constructor (String label, Object... operands) at line %d. %s", fullClassName, (i + 1), e));
                throw new TranslationException("Instruction class " + fullClassName + " must have a public constructor (String label, Object... operands).", e);
            } catch (InvocationTargetException e) {
                log.warning(format("Failed to instantiate instruction for %s at line %d: %s %s", opcode, (i + 1), e.getCause().getMessage(), e.getCause()));
                throw new TranslationException("Failed to instantiate instruction for " + opcode + " at line " + (i + 1) + ": " + e.getCause().getMessage(), e.getCause());
            } catch (InstantiationException | IllegalAccessException e) {
                log.warning(format("Internal error instantiating instruction for %s at line %d. %s", opcode, (i + 1), e));
                throw new TranslationException("Internal error instantiating instruction for " + opcode, e);
            }
        }
    }

    private Object[] resolveOperands(String opcode, String[] tokens, Map<String, Integer> labels) {
        var operands = new Object[tokens.length];
        log.info(format("Resolving operands for opcode '%s' with raw tokens: %s", opcode, Arrays.toString(tokens)));

        var jumpInstructions = Set.of("jnz", "call", "async");

        for (var j = 0; j < tokens.length; j++) {
            if (jumpInstructions.contains(opcode) && j == tokens.length - 1) {
                var labelToken = tokens[j];
                var pcIndex = Optional.ofNullable(labels.get(labelToken));

                operands[j] = pcIndex.orElseThrow(() -> {
                    log.warning(format("Semantic Error: Undefined jump/call/async target label: %s for opcode %s.", labelToken, opcode));
                    return new TranslationException("Semantic Error: Undefined jump/call/async target label: " + labelToken);
                });
                log.info(format("Resolved jump target label '%s' to PC index %d for opcode '%s'.", labelToken, operands[j], opcode));
            } else {
                operands[j] = tokens[j];
                log.info(format("Operand %d for opcode '%s' is a raw token: '%s'.", j, opcode, tokens[j]));
            }
        }
        return operands;
    }
}
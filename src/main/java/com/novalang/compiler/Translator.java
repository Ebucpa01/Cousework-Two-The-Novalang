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

    /**
     * Constructs a new TranslationException with a message and underlying cause.
     *
     * @param message the error message
     * @param cause   the underlying exception that caused this error
     */
    public TranslationException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new TranslationException with just a message.
     *
     * @param message the error message
     */
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

    /**
     * Package prefix for all instruction classes used in reflection.
     */
    private static final String INSTRUCTION_PACKAGE = "com.novalang.instructions.";

    /**
     * Maps label names to their program counter (PC) index in the program.
     */
    private final Map<String, Integer> labels = new HashMap<>();

    /**
     * Sequential list of instructions representing the translated program. Null entries represent comments/empty lines.
     */
    private final List<Instruction> program = new ArrayList<>();

    // --- Operand Validation Helpers ---

    /**
     * Constructs a new Translator instance.
     * Initializes empty label and program collections.
     */
    public Translator() {
        log.info("Translator initialized.");
    }

    /**
     * Extracts and validates a register index from operands array.
     * Expected format: "rX" where X is an integer between 0 and 31.
     *
     * @param operands the array of operand objects
     * @param index    the position in the array to extract from
     * @return Optional containing the register index (0-31), or empty if invalid
     */
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

    /**
     * Extracts and parses a constant integer value from operands array.
     *
     * @param operands the array of operand objects
     * @param index    the position in the array to extract from
     * @return Optional containing the parsed integer value, or empty if not a valid integer
     */
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

    /**
     * Extracts a program counter (PC) index from operands array.
     * The Translator resolves label strings to Integer PC indices before instruction instantiation.
     *
     * @param operands the array of operand objects
     * @param index    the position in the array to extract from
     * @return Optional containing the PC index, or empty if not an Integer
     */
    public static Optional<Integer> getTargetPC(Object[] operands, int index) {
        if (!ensureOperandIsType(operands, index, Integer.class, "resolved PC index")) {
            // TODO
        }
        return Optional.of((Integer) operands[index]);
    }

    /**
     * Validates that the operands array has the expected number of elements.
     *
     * @param operands the array of operand objects to check
     * @param expected the expected number of operands
     * @return true if the count matches, false otherwise
     */
    public static boolean ensureOperandCount(Object[] operands, int expected) {
        if (operands.length != expected) {
            log.warning(format("Incorrect number of operands. Expected %d, found %d.", expected, operands.length));
            return false;
        }
        return true;
    }

    // --- Translation Logic ---

    /**
     * Validates that an operand at a specific index is of the expected type.
     * Uses switch expression on the type's simple name for efficient type checking.
     *
     * @param operands     the array of operand objects
     * @param index        the position in the array to check
     * @param expectedType the expected Class type (Integer or String)
     * @param description  human-readable description of the operand for error messages
     * @return true if the operand exists and is of the correct type, false otherwise
     */
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

    /**
     * Translates a NovaLang source file into a program of Instruction objects.
     * Performs a two-pass translation:
     * <ol>
     *   <li>Pass 1: Collects all labels and their PC indices</li>
     *   <li>Pass 2: Parses instructions, resolves labels, and instantiates Instruction objects via reflection</li>
     * </ol>
     * Clears any previous translation state before starting.
     *
     * @param path the file path to the NovaLang source code
     * @throws IOException          if the file cannot be read
     * @throws TranslationException if syntax errors, undefined labels, or other translation issues occur
     */
    public void translate(Path path) throws IOException {
        log.info(format("Starting translation for file: %s", path));
        // TODO

        var lines = Files.readAllLines(path);
        log.info(format("Read %d lines from file.", lines.size()));

        // Pass 1: Collect Labels
        log.info("Pass 1: Collecting labels.");
        // TODO
        log.info(format("Collected %d labels: %s", labels.size(), labels));

        // Pass 2: Parse and Resolve Instructions
        log.info("Pass 2: Parsing and resolving com.novalang.instructions.");
        // TODO
        log.info(format("Translation complete. Generated %d com.novalang.instructions.", program.size()));
    }

    /**
     * Pass 1: Identifies and maps all unique labels to their program counter (PC) index.
     * Skips empty lines and comments (lines starting with ';' or '#').
     * A token is considered a label if it appears at the start of a line and is not a recognized opcode.
     *
     * @param lines the source code lines to process
     * @throws TranslationException if duplicate labels are found
     */
    private void collectLabels(List<String> lines) {
        for (var i = 0; i < lines.size(); i++) {
            var line = lines.get(i).trim();
            if (line.isEmpty() || line.startsWith(";") || line.startsWith("#")) {
                log.info(format("Skipping empty or comment line at PC %d: %s", i, line));
                continue;
            }

            var parts = line.split("\\s+");
            var firstToken = parts[0];

            // Check if the first token is a label (it is a label if it's NOT an opcode)
            if (!isOpcode(firstToken)) {
                // TODO
                log.info(format("Found label '%s' at PC index %d", firstToken, i));
            } else {
                log.info(format("Token '%s' at PC %d is an opcode, not a label.", firstToken, i));
            }
        }
    }

    /**
     * Checks if a token corresponds to a known Instruction class.
     *
     * @param token the token to check
     * @return true if the token can be resolved to an Instruction class, false otherwise
     */
    private boolean isOpcode(String token) {
        return resolveOpcodeClass(token).isPresent();
    }

    /**
     * Attempts to resolve a token to an Instruction class using reflection.
     * Converts the token to a class name by capitalizing the first letter and appending "Instr".
     * Example: "set" → "SetInstr"
     *
     * @param token the opcode token to resolve
     * @return Optional containing the resolved Class, or empty if not found or token is null/empty
     */
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

    /**
     * Pass 2: Parses each line, resolves labels, and instantiates Instruction objects via reflection.
     * Supports two line formats:
     * <ul>
     *   <li>opcode operand1 operand2 ...</li>
     *   <li>LABEL opcode operand1 operand2 ...</li>
     * </ul>
     * Empty lines and comments are stored as null entries to maintain PC indices.
     *
     * @param lines the source code lines to parse
     * @throws TranslationException for syntax errors, unknown opcodes, missing constructors, or instantiation failures
     */
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

            // Determine structure: [label] opcode [operands...]
            if (isOpcode(tokens[0])) {
                opcode = tokens[0];
                operandTokens = Arrays.copyOfRange(tokens, 1, tokens.length);
                log.info(format("Line %d starts with opcode '%s'.", i + 1, opcode));
            } else if (tokens.length > 1 && isOpcode(tokens[1])) {
                // TODO
                log.info(format("Line %d has label '%s' and opcode '%s'.", i + 1, label, opcode));
            } else {
                log.warning(format("Invalid syntax or unknown opcode/label at line %d: %s", (i + 1), line));
                throw new TranslationException("Invalid syntax or unknown opcode/label at line " + (i + 1) + ": " + line);
            }

            var fullClassName = INSTRUCTION_PACKAGE + opcode.substring(0, 1).toUpperCase() + opcode.substring(1) + "Instr";

            try {
                // TODO
                log.info(format("Resolved opcode '%s' to class '%s'.", opcode, fullClassName));

                // Prepare operands: Resolve labels to PC index immediately
                var operands = resolveOperands(opcode, operandTokens, labels);
                log.info(format("Resolved operands for opcode '%s': %s", opcode, Arrays.toString(operands)));

                // Get the mandatory reflection constructor signature: (String label, Object... operands)
                // TODO
                log.info(format("Found constructor for class '%s'.", fullClassName));

                // Reflection: Instantiate the instruction dynamically
                // TODO
                program.add(instruction);
                log.info(format("Instantiated instruction: %s at PC index %d", instruction.getClass().getSimpleName(), i));

            } catch (ClassNotFoundException e) {
                log.warning(format("Unknown opcode: %s (Class %s not found) at line %d. %s", opcode, fullClassName, (i + 1), e));
                throw new TranslationException("Unknown opcode: " + opcode + " (Class " + fullClassName + " not found)", e);
            } catch (NoSuchMethodException e) {
                log.warning(format("Instruction class %s must have a public constructor (String label, Object... operands) at line %d. %s", fullClassName, (i + 1), e));
                throw new TranslationException("Instruction class " + fullClassName + " must have a public constructor (String label, Object... operands).", e);
            } catch (InvocationTargetException e) {
                // Catches exceptions thrown by the Instruction constructor (e.g., bad register index, missing label)
                log.warning(format("Failed to instantiate instruction for %s at line %d: %s %s", opcode, (i + 1), e.getCause().getMessage(), e.getCause()));
                throw new TranslationException("Failed to instantiate instruction for " + opcode + " at line " + (i + 1) + ": " + e.getCause().getMessage(), e.getCause());
            } catch (InstantiationException | IllegalAccessException e) {
                log.warning(format("Internal error instantiating instruction for %s at line %d. %s", opcode, (i + 1), e));
                throw new TranslationException("Internal error instantiating instruction for " + opcode, e);
            }
        }
    }

    /**
     * Converts raw string tokens into a mix of String (registers/constants) or Integer (resolved PC index).
     * For jump instructions (jnz, call, async), the last operand is treated as a label and resolved to its PC index.
     * Other operands are passed as strings for individual instructions to validate.
     *
     * @param opcode the instruction opcode being processed
     * @param tokens the raw operand tokens from the source line
     * @param labels the map of label names to PC indices
     * @return an array of Objects containing either String tokens or Integer PC indices
     * @throws TranslationException if a label reference cannot be resolved
     */
    private Object[] resolveOperands(String opcode, String[] tokens, Map<String, Integer> labels) {
        var operands = new Object[tokens.length];
        log.info(format("Resolving operands for opcode '%s' with raw tokens: %s", opcode, Arrays.toString(tokens)));

        // Instructions that require label resolution for their last operand
        var jumpInstructions = Set.of("jnz", "call", "async");

        for (var j = 0; j < tokens.length; j++) {
            if (jumpInstructions.contains(opcode) && j == tokens.length - 1) {
                // This is the jump target label (L)
                var labelToken = tokens[j];
                var pcIndex = Optional.ofNullable(labels.get(labelToken));

                operands[j] = pcIndex.orElseThrow(() -> {
                    log.warning(format("Semantic Error: Undefined jump/call/async target label: %s for opcode %s.", labelToken, opcode));
                    return new TranslationException("Semantic Error: Undefined jump/call/async target label: " + labelToken);
                });
                log.info(format("Resolved jump target label '%s' to PC index %d for opcode '%s'.", labelToken, operands[j], opcode));
            } else {
                // This is a register ('rX') or constant ('X'), passed as a string for the instruction to validate
                operands[j] = tokens[j];
                log.info(format("Operand %d for opcode '%s' is a raw token: '%s'.", j, opcode, tokens[j]));
            }
        }
        return operands;
    }
}
package com.novalang.compiler;

import com.novalang.instructions.*;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Translator and Reflection Factory Tests")
public class TranslatorTest {

    private static Path tempDir;
    private Translator translator;

    @BeforeAll
    static void init() throws IOException {
        tempDir = Files.createTempDirectory("novalang_test");
    }

    @BeforeEach
    void setup() {
        translator = new Translator();
    }

    private Path writeTempFile(String content) throws IOException {
        Path tempFile = tempDir.resolve("test_" + System.nanoTime() + ".nvl");
        return Files.writeString(tempFile, content);
    }

    @Test
    @DisplayName("T1: Should correctly translate a simple arithmetic program")
    void testTranslateSimpleProgram() throws Exception {
        String nvlSource = """
                START set r1 1
                LOOP  set r2 2
                      add r3 r1 r2
                      halt
                """;
        Path tempFile = writeTempFile(nvlSource);
        translator.translate(tempFile);

        List<Instruction> program = translator.program();
        Map<String, Integer> labels = translator.labels();

        // Check labels
        assertEquals(2, labels.size(), "Should find 2 labels: START and LOOP");
        assertEquals(0, labels.get("START"), "START should be at PC 0");
        assertEquals(1, labels.get("LOOP"), "LOOP should be at PC 1");

        // Check com.novalang.instructions and reflection instantiation
        assertEquals(4, program.size());
        assertInstanceOf(SetInstr.class, program.get(0));
        assertInstanceOf(SetInstr.class, program.get(1));
        assertInstanceOf(AddInstr.class, program.get(2));
        assertInstanceOf(HaltInstr.class, program.get(3));

        // Check operands (for reflection integrity)
        SetInstr instr1 = (SetInstr) program.get(0);
        assertEquals(1, instr1.rDest(), "rDest should be r1");
        assertEquals(1, instr1.constant(), "Constant should be 1");
    }

    @Test
    @DisplayName("T2: Should correctly resolve label targets for JNZ and CALL")
    void testLabelResolution() throws Exception {
        String nvlSource = """
                START set r0 1
                      call LABEL
                      jnz r0 LABEL
                      halt
                LABEL print r0
                      ret
                """;
        Path tempFile = writeTempFile(nvlSource);
        translator.translate(tempFile);

        List<Instruction> program = translator.program();
        Map<String, Integer> labels = translator.labels();

        // Program should have 6 com.novalang.instructions (PC 0-5)
        assertEquals(6, program.size());
        assertEquals(4, labels.get("LABEL")); // LABEL is at PC 4

        // Check JNZ (PC 2)
        JnzInstr jnz = (JnzInstr) program.get(2);
        assertEquals(0, jnz.rCond(), "JNZ rCond should be r0");
        assertEquals(4, jnz.targetPC(), "JNZ target should resolve to PC 4 (SUB)");

        // Check CALL (PC 1)
        CallInstr call = (CallInstr) program.get(1);
        assertEquals(4, call.targetPC(), "CALL target should resolve to PC 4 (SUB)");
    }

    @Test
    @DisplayName("T3: Should throw TranslationException for an unknown opcode")
    void testUnknownOpcode() throws Exception {
        String nvlSource = "START unknown r1 1";
        Path tempFile = writeTempFile(nvlSource);

        Exception exception = assertThrows(TranslationException.class, () -> {
            translator.translate(tempFile);
        });

        assertTrue(exception.getMessage().contains("unknown opcode"));
    }

    @Test
    @DisplayName("T4: Should throw TranslationException for duplicate labels")
    void testDuplicateLabels() throws Exception {
        String nvlSource = "LOOP set r1 1\nLOOP set r2 2";
        Path tempFile = writeTempFile(nvlSource);

        Exception exception = assertThrows(TranslationException.class, () -> {
            translator.translate(tempFile);
        });

        assertTrue(exception.getMessage().contains("Duplicate label found: LOOP"));
    }

    @Test
    @DisplayName("T5: Should throw TranslationException for undefined jump target")
    void testUndefinedLabel() throws Exception {
        String nvlSource = """
                    set r1 1
                    jnz r1 BADLABEL
                """;
        Path tempFile = writeTempFile(nvlSource);

        Exception exception = assertThrows(TranslationException.class, () -> {
            translator.translate(tempFile);
        });

        assertTrue(exception.getMessage().contains("Undefined jump/call/async target label: BADLABEL"));
    }

    @Test
    @DisplayName("T6: Should skip empty lines and comments")
    void testEmptyLinesAndComments() throws Exception {
        String nvlSource = """
                # This is a comment
                START set r1 1
                ; Another comment
                
                      set r2 2
                      halt
                """;
        Path tempFile = writeTempFile(nvlSource);
        translator.translate(tempFile);

        List<Instruction> program = translator.program();
        Map<String, Integer> labels = translator.labels();

        // Program should have 6 items total (null for comment lines, actual com.novalang.instructions)
        assertEquals(6, program.size());
        assertNull(program.get(0), "Comment line should be null");
        assertNull(program.get(2), "Comment line should be null");
        assertNull(program.get(3), "Empty line should be null");
        assertInstanceOf(SetInstr.class, program.get(1));
        assertInstanceOf(SetInstr.class, program.get(4));
        assertInstanceOf(HaltInstr.class, program.get(5));

        assertEquals(1, labels.size());
        assertEquals(1, labels.get("START"));
    }

    @Test
    @DisplayName("T7: Should test getRegisterIndex helper")
    void testGetRegisterIndex() {
        Object[] validOperands = {"r5", "r0", "r31"};
        Object[] invalidOperands = {"r32", "r-1", "x5", "5", "r"};

        // Valid cases
        var result = Translator.getRegisterIndex(validOperands, 0);
        assertTrue(result.isPresent());
        assertEquals(5, result.get());

        result = Translator.getRegisterIndex(validOperands, 1);
        assertTrue(result.isPresent());
        assertEquals(0, result.get());

        result = Translator.getRegisterIndex(validOperands, 2);
        assertTrue(result.isPresent());
        assertEquals(31, result.get());

        // Invalid cases
        result = Translator.getRegisterIndex(invalidOperands, 0);
        assertFalse(result.isPresent(), "r32 is out of range");

        result = Translator.getRegisterIndex(invalidOperands, 2);
        assertFalse(result.isPresent(), "x5 is not a register");

        result = Translator.getRegisterIndex(invalidOperands, 3);
        assertFalse(result.isPresent(), "5 is not a register token");

        result = Translator.getRegisterIndex(invalidOperands, 4);
        assertFalse(result.isPresent(), "r alone is invalid");
    }

    @Test
    @DisplayName("T8: Should test getConstantValue helper")
    void testGetConstantValue() {
        Object[] validOperands = {"42", "-5", "0"};
        Object[] invalidOperands = {"abc", "r5"};

        // Valid cases
        var result = Translator.getConstantValue(validOperands, 0);
        assertTrue(result.isPresent());
        assertEquals(42, result.get());

        result = Translator.getConstantValue(validOperands, 1);
        assertTrue(result.isPresent());
        assertEquals(-5, result.get());

        result = Translator.getConstantValue(validOperands, 2);
        assertTrue(result.isPresent());
        assertEquals(0, result.get());

        // Invalid cases
        result = Translator.getConstantValue(invalidOperands, 0);
        assertFalse(result.isPresent(), "abc is not a number");

        result = Translator.getConstantValue(invalidOperands, 1);
        assertFalse(result.isPresent(), "r5 is not a number");
    }

    @Test
    @DisplayName("T9: Should test getTargetPC helper")
    void testGetTargetPC() {
        Object[] operands = {5, 10, "notAnInteger"};

        // Valid cases
        var result = Translator.getTargetPC(operands, 0);
        assertTrue(result.isPresent());
        assertEquals(5, result.get());

        result = Translator.getTargetPC(operands, 1);
        assertTrue(result.isPresent());
        assertEquals(10, result.get());

        // Invalid case
        result = Translator.getTargetPC(operands, 2);
        assertFalse(result.isPresent(), "String is not Integer");
    }

    @Test
    @DisplayName("T10: Should test ensureOperandCount helper")
    void testEnsureOperandCount() {
        Object[] threeOperands = {"r1", "r2", "5"};

        assertTrue(Translator.ensureOperandCount(threeOperands, 3));
        assertFalse(Translator.ensureOperandCount(threeOperands, 2));
        assertFalse(Translator.ensureOperandCount(threeOperands, 4));
    }

    @Test
    @DisplayName("T11: Should handle opcode without label")
    void testOpcodeWithoutLabel() throws Exception {
        String nvlSource = """
                set r1 5
                set r2 10
                add r3 r1 r2
                halt
                """;
        Path tempFile = writeTempFile(nvlSource);
        translator.translate(tempFile);

        List<Instruction> program = translator.program();
        Map<String, Integer> labels = translator.labels();

        assertEquals(4, program.size());
        assertEquals(0, labels.size(), "No labels should be found");
        assertInstanceOf(SetInstr.class, program.get(0));
        assertInstanceOf(AddInstr.class, program.get(2));
    }

    @Test
    @DisplayName("T12: Should handle label on same line as opcode")
    void testLabelOnSameLineAsOpcode() throws Exception {
        String nvlSource = """
                MAIN set r1 1
                     call SUB
                     halt
                SUB  print r1
                     ret
                """;
        Path tempFile = writeTempFile(nvlSource);
        translator.translate(tempFile);

        List<Instruction> program = translator.program();
        Map<String, Integer> labels = translator.labels();

        assertEquals(5, program.size());
        assertEquals(2, labels.size());
        assertEquals(0, labels.get("MAIN"));
        assertEquals(3, labels.get("SUB"));

        CallInstr call = (CallInstr) program.get(1);
        assertEquals(3, call.targetPC());
    }

    @Test
    @DisplayName("T13: Should throw exception for invalid syntax")
    void testInvalidSyntax() throws Exception {
        String nvlSource = "INVALIDLABEL";  // Label with no opcode
        Path tempFile = writeTempFile(nvlSource);

        Exception exception = assertThrows(TranslationException.class, () -> {
            translator.translate(tempFile);
        });

        assertTrue(exception.getMessage().contains("Invalid syntax or unknown opcode/label"));
    }

    @Test
    @DisplayName("T14: Should test async instruction with label resolution")
    void testAsyncLabelResolution() throws Exception {
        String nvlSource = """
                START set r0 1
                      async TASK
                      halt
                TASK  print r0
                      ret
                """;
        Path tempFile = writeTempFile(nvlSource);
        translator.translate(tempFile);

        List<Instruction> program = translator.program();

        assertEquals(5, program.size());
        assertInstanceOf(AsyncInstr.class, program.get(1));

        AsyncInstr async = (AsyncInstr) program.get(1);
        assertEquals(3, async.targetPC(), "Async should resolve to TASK at PC 3");
    }

    @Test
    @DisplayName("T15: Should clear labels and program on each translate call")
    void testTranslateClearsState() throws Exception {
        String nvlSource1 = "LABEL1 set r1 1\nhalt";
        String nvlSource2 = "LABEL2 set r2 2\nhalt";

        Path tempFile1 = writeTempFile(nvlSource1);
        translator.translate(tempFile1);

        assertEquals(1, translator.labels().size());
        assertEquals(2, translator.program().size());

        Path tempFile2 = writeTempFile(nvlSource2);
        translator.translate(tempFile2);

        // Should only have labels/program from second translation
        assertEquals(1, translator.labels().size());
        assertTrue(translator.labels().containsKey("LABEL2"));
        assertFalse(translator.labels().containsKey("LABEL1"));
        assertEquals(2, translator.program().size());
    }
}
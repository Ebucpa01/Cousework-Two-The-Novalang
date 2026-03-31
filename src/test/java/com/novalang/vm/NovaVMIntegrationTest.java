package com.novalang.vm;

import com.novalang.compiler.Translator;
import org.junit.jupiter.api.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NovaVM Integration and Correctness Tests")
public class NovaVMIntegrationTest {

    private static Path tempDir;
    private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private Translator translator;
    private NovaVMFactory vmFactory;

    @BeforeAll
    static void init() throws IOException {
        tempDir = Files.createTempDirectory("novalang_integration_test");
    }

    @BeforeEach
    void setup() {
        translator = new Translator();
        vmFactory = new DefaultNovaVMFactory();

        // Capture stdout for print statements
        System.setOut(new PrintStream(outputStream));
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
    }

    private Path writeTempFile(String content) throws IOException {
        Path tempFile = tempDir.resolve("test_" + System.nanoTime() + ".nvl");
        return Files.writeString(tempFile, content);
    }

    private NovaVM runProgram(String nvlSource) throws Exception {
        Path tempFile = writeTempFile(nvlSource);
        translator.translate(tempFile);

        NovaVM vm = vmFactory.create(translator.program(), translator.labels());
        vm.run();
        return vm;
    }

    @Test
    @DisplayName("I1: Sum 1 to 10 (Arithmetic, Branching, and JNZ)")
    void testSum1To10() throws Exception {
        // Standard loop to sum numbers 1 through 10 (Result: 55)
        String nvlSource = """
                L1 set r1 1
                L2 set r2 0
                L3 set r3 11
                LOOP  add r2 r2 r1
                L4    set r0 1
                L5    add r1 r1 r0
                L6    sub r0 r3 r1
                L7    jnz r0 LOOP
                L8    print r2
                L9    halt
                """;
        NovaVM vm = runProgram(nvlSource);

        // Final result check
        assertEquals(55, vm.getRegisters().get(2), "r2 should hold the final sum (55)");

        // Output check
        assertTrue(outputStream.toString().contains("PRINT (r2): 55"));
    }

    @Test
    @DisplayName("I2: Subroutine Call and Return (CALL/RET)")
    void testCallAndReturn() throws Exception {
        // Main thread sets r1=5, calls PROC, checks r1 is 15, then calls PROC again.
        String nvlSource = """
                set r1 5
                call PROC
                print r1
                call PROC
                print r1
                halt
                
                PROC set r0 10
                add r1 r1 r0
                ret
                """;
        NovaVM vm = runProgram(nvlSource);

        // Final result check
        assertEquals(25, vm.getRegisters().get(1), "r1 should be 25 after two calls");

        // Output check
        String output = outputStream.toString();
        assertTrue(output.contains("PRINT (r1): 15"));
        assertTrue(output.contains("PRINT (r1): 25"));
    }

    @Test
    @DisplayName("I3: Concurrent Execution (ASYNC/AWAIT)")
    void testAsyncAwaitConcurrency() throws Exception {
        // Task A increments r1 by 1 repeatedly (should be slow).
        // Task B sets r2 to 100 immediately (should be fast).
        // Main thread awaits and verifies results.
        String nvlSource = """
                set r1 0
                set r2 0
                
                async TASK_A
                async TASK_B
                
                await
                
                print r1
                print r2
                halt
                
                TASK_A  set r0 1
                LOOP_A  add r1 r1 r0
                        sub r3 r0 r0
                        jnz r3 LOOP_A
                
                TASK_B  set r2 100
                        ret
                """;

        // This test requires external timeout since NovaVM.run() is synchronous
        // and needs modification to simulate the infinite loop finishing.
        // For a deterministic test, we must limit the loop count in Task A.

        // Revised deterministic async program:
        String deterministicNvlSource = """
                set r1 0
                set r2 0
                set r3 100
                
                async TASK_A
                async TASK_B
                await
                
                print r1
                print r2
                halt
                
                TASK_A  set r0 1
                LOOP_A  add r1 r1 r0
                        sub r4 r3 r1
                        jnz r4 LOOP_A
                        ret
                
                TASK_B  sleep 50
                        set r2 999
                        ret
                """;

        NovaVM vm = runProgram(deterministicNvlSource);

        // Final result check (r1 should be 100 from Task A, r2 should be 999 from Task B)
        assertEquals(100, vm.getRegisters().get(1), "r1 should be 100 after Task A completion");
        assertEquals(999, vm.getRegisters().get(2), "r2 should be 999 after Task B completion");
    }

    @Test
    @DisplayName("I4: Robust Error Handling (Divide by Zero)")
    void testDivideByZeroError() throws Exception {
        String nvlSource = """
                set r1 10
                set r2 0
                div r3 r1 r2
                halt
                """;
        Path tempFile = writeTempFile(nvlSource);
        translator.translate(tempFile);

        NovaVM vm = vmFactory.create(translator.program(), translator.labels());

        // Expect ArithmeticException during runtime
        assertThrows(Exception.class, vm::run, "VM should throw a runtime exception on division by zero.");
    }
}
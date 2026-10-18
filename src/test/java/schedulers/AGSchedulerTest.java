package schedulers;

import core.Process;
import core.SchedulingResult;
import org.junit.jupiter.api.Test;
import utils.TestUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AG (Adaptive Guarantee) Scheduler
 */
public class AGSchedulerTest {

    @Test
    public void testAGCase1() throws IOException {
        runTestFromFile("src/test/resources/agscheduler/AG_test1.json");
    }

    @Test
    public void testAGCase2() throws IOException {
        runTestFromFile("src/test/resources/agscheduler/AG_test2.json");
    }

    @Test
    public void testAGCase3() throws IOException {
        runTestFromFile("src/test/resources/agscheduler/AG_test3.json");
    }

    @Test
    public void testAGCase4() throws IOException {
        runTestFromFile("src/test/resources/agscheduler/AG_test4.json");
    }

    @Test
    public void testAGCase5() throws IOException {
        runTestFromFile("src/test/resources/agscheduler/AG_test5.json");
    }

    @Test
    public void testAGCase6() throws IOException {
        runTestFromFile("src/test/resources/agscheduler/AG_test6.json");
    }

    /**
     * Helper method to run a test from a JSON file
     */
    private void runTestFromFile(String filepath) throws IOException {
        // Load test case
        TestUtils.TestCase testCase = TestUtils.loadTestCase(filepath);

        // Make copies of processes for the test
        List<Process> processes = new ArrayList<>();
        for (Process p : testCase.processes) {
            processes.add(p.copy());
        }

        // Run scheduler
        AGScheduler scheduler = new AGScheduler();
        scheduler.run(processes, 0, 0);  // AG doesn't use contextSwitch or rrQuantum from test

        // Get quantum history
        Map<String, List<Integer>> quantumHistory = scheduler.getQuantumHistory();

        // Create result object
        SchedulingResult result = new SchedulingResult(
                scheduler.getSlices(),
                processes,
                quantumHistory
        );

        // Get expected results - AG tests don't have scheduler key, direct output
        TestUtils.ExpectedResult expected;
        if (testCase.expectedOutput.containsKey("expectedOutput")) {
            expected = testCase.expectedOutput.get("expectedOutput");
        } else {
            // For AG tests, the structure is different - output is directly in expectedOutput
            expected = testCase.expectedOutput.get("AG");
            if (expected == null) {
                // Parse from direct structure
                expected = parseAGExpectedOutput(testCase);
            }
        }

        assertNotNull(expected, "No expected output found for AG Scheduler");

        // Compare general results
        boolean passed = TestUtils.compareResults(result, expected, "AG Scheduler");

        // Compare quantum history
        boolean quantumPassed = TestUtils.compareQuantumHistory(quantumHistory, expected.processResults);

        assertTrue(passed && quantumPassed, "Test failed - see error messages above");

        System.out.println("✓ AG Scheduler test passed: " + filepath);
    }

    /**
     * Parse expected output specifically for AG test format
     */
    private TestUtils.ExpectedResult parseAGExpectedOutput(TestUtils.TestCase testCase) {
        // This is a fallback - the TestUtils should handle it, but just in case
        // For AG tests, we need to look at the raw expected output
        return testCase.expectedOutput.values().iterator().next();
    }
}
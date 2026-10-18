package schedulers;

import core.Process;
import core.SchedulingResult;
import org.junit.jupiter.api.Test;
import utils.TestUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Round Robin Scheduler
 */
public class RoundRobinSchedulerTest {

    @Test
    public void testCase1_BasicMixedArrivals() throws IOException {
        runTestFromFile("src/test/resources/otherschedulers/test_1.json", "RR");
    }

    @Test
    public void testCase2_AllArrivalsAtZero() throws IOException {
        runTestFromFile("src/test/resources/otherschedulers/test_2.json", "RR");
    }

    @Test
    public void testCase3_VariedBurstTimes() throws IOException {
        runTestFromFile("src/test/resources/otherschedulers/test_3.json", "RR");
    }

    @Test
    public void testCase4_LargeBurstsWithGaps() throws IOException {
        runTestFromFile("src/test/resources/otherschedulers/test_4.json", "RR");
    }

    @Test
    public void testCase5_ShortBurstsHighFrequency() throws IOException {
        runTestFromFile("src/test/resources/otherschedulers/test_5.json", "RR");
    }

    @Test
    public void testCase6_MixedScenario() throws IOException {
        runTestFromFile("src/test/resources/otherschedulers/test_6.json", "RR");
    }

    /**
     * Helper method to run a test from a JSON file
     */
    private void runTestFromFile(String filepath, String schedulerKey) throws IOException {
        // Load test case
        TestUtils.TestCase testCase = TestUtils.loadTestCase(filepath);

        // Make copies of processes for the test
        List<Process> processes = new ArrayList<>();
        for (Process p : testCase.processes) {
            processes.add(p.copy());
        }

        // Run scheduler
        RoundRobinScheduler scheduler = new RoundRobinScheduler();
        scheduler.run(processes, testCase.contextSwitch, testCase.rrQuantum);

        // Create result object
        SchedulingResult result = new SchedulingResult(
                scheduler.getSlices(),
                processes,
                null  // RR doesn't have quantum history
        );

        // Get expected results
        TestUtils.ExpectedResult expected = testCase.expectedOutput.get(schedulerKey);
        assertNotNull(expected, "No expected output found for " + schedulerKey);

        // Compare and assert
        boolean passed = TestUtils.compareResults(result, expected, "Round Robin Scheduler");
        assertTrue(passed, "Test failed - see error messages above");

        System.out.println("✓ " + schedulerKey + " test passed: " + filepath);
    }
}
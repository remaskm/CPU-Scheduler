package schedulers;

import core.Process;
import core.SchedulingResult;
import org.junit.jupiter.api.Test;
import utils.TestUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PriorityPreemptiveSchedulerTest {

    @Test
    public void testCase1_BasicMixedArrivals() throws IOException {
        runTestFromFile("src/test/resources/otherschedulers/test_1.json", "Priority");
    }

    @Test
    public void testCase2_AllArrivalsAtZero() throws IOException {
        runTestFromFile("src/test/resources/otherschedulers/test_2.json", "Priority");
    }

    @Test
    public void testCase3_VariedBurstTimes() throws IOException {
        runTestFromFile("src/test/resources/otherschedulers/test_3.json", "Priority");
    }

    @Test
    public void testCase4_LargeBurstsWithGaps() throws IOException {
        runTestFromFile("src/test/resources/otherschedulers/test_4.json", "Priority");
    }

    @Test
    public void testCase5_ShortBurstsHighFrequency() throws IOException {
        runTestFromFile("src/test/resources/otherschedulers/test_5.json", "Priority");
    }

    @Test
    public void testCase6_MixedScenario() throws IOException {
        runTestFromFile("src/test/resources/otherschedulers/test_6.json", "Priority");
    }

    private void runTestFromFile(String filepath, String schedulerKey) throws IOException {
        TestUtils.TestCase testCase = TestUtils.loadTestCase(filepath);

        List<Process> processes = new ArrayList<>();
        for (Process p : testCase.processes) {
            processes.add(p.copy());
        }

        PriorityPreemptiveScheduler scheduler = new PriorityPreemptiveScheduler();
        scheduler.run(processes, testCase.contextSwitch, testCase.agingInterval);

        SchedulingResult result = new SchedulingResult(
                scheduler.getSlices(),
                processes,
                null
        );

        TestUtils.ExpectedResult expected = testCase.expectedOutput.get(schedulerKey);
        assertNotNull(expected, "No expected output found for " + schedulerKey);

        boolean passed = TestUtils.compareResults(result, expected, "Priority Preemptive Scheduler");
        assertTrue(passed, "Test failed - see error messages above");

        System.out.println("✓ " + schedulerKey + " test passed: " + filepath);
    }
}
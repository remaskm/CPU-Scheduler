package utils;

import com.google.gson.*;
import core.Process;
import core.SchedulingResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Utility class for loading and parsing test case JSON files
 * FIXED: Now properly handles AG scheduler JSON format
 */
public class TestUtils {

    /**
     * Load a test case from a JSON file
     */
    public static TestCase loadTestCase(String filepath) throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(filepath)));
        JsonObject json = JsonParser.parseString(content).getAsJsonObject();

        TestCase testCase = new TestCase();

        // Parse input
        JsonObject input = json.getAsJsonObject("input");

        if (input.has("contextSwitch")) {
            testCase.contextSwitch = input.get("contextSwitch").getAsInt();
        }

        if (input.has("rrQuantum")) {
            testCase.rrQuantum = input.get("rrQuantum").getAsInt();
        }

        if (input.has("agingInterval")) {
            testCase.agingInterval = input.get("agingInterval").getAsInt();
        }

        // Parse processes
        JsonArray processesArray = input.getAsJsonArray("processes");
        testCase.processes = new ArrayList<>();

        for (JsonElement elem : processesArray) {
            JsonObject procObj = elem.getAsJsonObject();
            String name = procObj.get("name").getAsString();
            int arrival = procObj.get("arrival").getAsInt();
            int burst = procObj.get("burst").getAsInt();
            int priority = procObj.has("priority") ? procObj.get("priority").getAsInt() : 0;
            int quantum = procObj.has("quantum") ? procObj.get("quantum").getAsInt() : 0;

            testCase.processes.add(new Process(name, arrival, burst, priority, quantum));
        }

        // Parse expected output
        if (json.has("expectedOutput")) {
            JsonElement expectedOutputElem = json.get("expectedOutput");

            // FIXED: Check if expectedOutput is a JsonObject or direct structure
            if (expectedOutputElem.isJsonObject()) {
                JsonObject expectedOutput = expectedOutputElem.getAsJsonObject();

                // Check if this is an AG test (direct structure) or regular test (with scheduler keys)
                if (expectedOutput.has("executionOrder") && expectedOutput.has("processResults")) {
                    // This is AG format - direct expected output
                    testCase.expectedOutput = new HashMap<>();
                    testCase.expectedOutput.put("AG", parseSingleExpectedResult(expectedOutput));
                } else {
                    // This is regular format - multiple schedulers
                    testCase.expectedOutput = parseExpectedOutput(expectedOutput);
                }
            }
        }

        return testCase;
    }

    /**
     * Parse expected output for all schedulers (SJF, RR, Priority format)
     */
    private static Map<String, ExpectedResult> parseExpectedOutput(JsonObject expectedOutput) {
        Map<String, ExpectedResult> results = new HashMap<>();

        for (String schedulerName : expectedOutput.keySet()) {
            JsonElement schedulerElem = expectedOutput.get(schedulerName);

            // Skip if not a JsonObject
            if (!schedulerElem.isJsonObject()) {
                continue;
            }

            JsonObject schedulerOutput = schedulerElem.getAsJsonObject();
            results.put(schedulerName, parseSingleExpectedResult(schedulerOutput));
        }

        return results;
    }

    /**
     * Parse a single scheduler's expected result
     * FIXED: Extracted to handle both AG and regular formats
     */
    private static ExpectedResult parseSingleExpectedResult(JsonObject schedulerOutput) {
        ExpectedResult result = new ExpectedResult();

        // Parse execution order
        if (schedulerOutput.has("executionOrder")) {
            JsonElement execOrderElem = schedulerOutput.get("executionOrder");

            // FIXED: Handle both JsonArray and JsonObject
            if (execOrderElem.isJsonArray()) {
                JsonArray execOrder = execOrderElem.getAsJsonArray();
                result.executionOrder = new ArrayList<>();
                for (JsonElement elem : execOrder) {
                    result.executionOrder.add(elem.getAsString());
                }
            }
        }

        // Parse process results
        if (schedulerOutput.has("processResults")) {
            JsonElement procResultsElem = schedulerOutput.get("processResults");

            // FIXED: Handle both JsonArray and JsonObject
            if (procResultsElem.isJsonArray()) {
                JsonArray procResults = procResultsElem.getAsJsonArray();
                result.processResults = new ArrayList<>();
                for (JsonElement elem : procResults) {
                    JsonObject procObj = elem.getAsJsonObject();
                    ProcessResult pr = new ProcessResult();
                    pr.name = procObj.get("name").getAsString();
                    pr.waitingTime = procObj.get("waitingTime").getAsInt();
                    pr.turnaroundTime = procObj.get("turnaroundTime").getAsInt();

                    if (procObj.has("quantumHistory")) {
                        pr.quantumHistory = new ArrayList<>();
                        JsonArray qh = procObj.getAsJsonArray("quantumHistory");
                        for (JsonElement qElem : qh) {
                            pr.quantumHistory.add(qElem.getAsInt());
                        }
                    }

                    result.processResults.add(pr);
                }
            }
        }

        // Parse averages
        if (schedulerOutput.has("averageWaitingTime")) {
            result.averageWaitingTime = schedulerOutput.get("averageWaitingTime").getAsDouble();
        }

        if (schedulerOutput.has("averageTurnaroundTime")) {
            result.averageTurnaroundTime = schedulerOutput.get("averageTurnaroundTime").getAsDouble();
        }

        return result;
    }

    /**
     * Compare actual scheduling result with expected result
     */
    public static boolean compareResults(SchedulingResult actual, ExpectedResult expected, String schedulerName) {
        List<String> errors = new ArrayList<>();

        // Compare execution order (ignore CS and IDLE)
        List<String> actualOrder = actual.getExecutionOrder();
        if (!actualOrder.equals(expected.executionOrder)) {
            errors.add("Execution order mismatch: expected " + expected.executionOrder +
                    " but got " + actualOrder);
        }

        // Compare process metrics
        for (ProcessResult expectedProc : expected.processResults) {
            Process actualProc = findProcessByName(actual.getProcesses(), expectedProc.name);
            if (actualProc == null) {
                errors.add("Process " + expectedProc.name + " not found in results");
                continue;
            }

            if (actualProc.getWaitingTime() != expectedProc.waitingTime) {
                errors.add(String.format("Process %s: waiting time mismatch (expected %d, got %d)",
                        expectedProc.name, expectedProc.waitingTime, actualProc.getWaitingTime()));
            }

            if (actualProc.getTurnaroundTime() != expectedProc.turnaroundTime) {
                errors.add(String.format("Process %s: turnaround time mismatch (expected %d, got %d)",
                        expectedProc.name, expectedProc.turnaroundTime, actualProc.getTurnaroundTime()));
            }
        }

        // Compare averages (with tolerance for floating point)
        double avgWaitDiff = Math.abs(actual.getAverageWaitingTime() - expected.averageWaitingTime);
        if (avgWaitDiff > 0.01) {
            errors.add(String.format("Average waiting time mismatch (expected %.2f, got %.2f)",
                    expected.averageWaitingTime, actual.getAverageWaitingTime()));
        }

        double avgTurnDiff = Math.abs(actual.getAverageTurnaroundTime() - expected.averageTurnaroundTime);
        if (avgTurnDiff > 0.01) {
            errors.add(String.format("Average turnaround time mismatch (expected %.2f, got %.2f)",
                    expected.averageTurnaroundTime, actual.getAverageTurnaroundTime()));
        }

        if (!errors.isEmpty()) {
            System.err.println("\n" + schedulerName + " FAILED:");
            for (String error : errors) {
                System.err.println("  - " + error);
            }
            return false;
        }

        return true;
    }

    /**
     * Compare quantum history for AG scheduler
     */
    public static boolean compareQuantumHistory(Map<String, List<Integer>> actual,
                                                List<ProcessResult> expected) {
        List<String> errors = new ArrayList<>();

        for (ProcessResult expectedProc : expected) {
            if (expectedProc.quantumHistory == null) continue;

            List<Integer> actualHistory = actual.get(expectedProc.name);
            if (actualHistory == null) {
                errors.add("No quantum history found for process " + expectedProc.name);
                continue;
            }

            if (!actualHistory.equals(expectedProc.quantumHistory)) {
                errors.add(String.format("Process %s quantum history mismatch: expected %s but got %s",
                        expectedProc.name, expectedProc.quantumHistory, actualHistory));
            }
        }

        if (!errors.isEmpty()) {
            System.err.println("\nQuantum History FAILED:");
            for (String error : errors) {
                System.err.println("  - " + error);
            }
            return false;
        }

        return true;
    }

    private static Process findProcessByName(List<Process> processes, String name) {
        for (Process p : processes) {
            if (p.getName().equals(name)) {
                return p;
            }
        }
        return null;
    }

    /**
     * Container for test case data
     */
    public static class TestCase {
        public int contextSwitch = 0;
        public int rrQuantum = 0;
        public int agingInterval = 0;
        public List<Process> processes;
        public Map<String, ExpectedResult> expectedOutput;
    }

    /**
     * Container for expected results
     */
    public static class ExpectedResult {
        public List<String> executionOrder;
        public List<ProcessResult> processResults;
        public double averageWaitingTime;
        public double averageTurnaroundTime;
    }

    /**
     * Container for expected process result
     */
    public static class ProcessResult {
        public String name;
        public int waitingTime;
        public int turnaroundTime;
        public List<Integer> quantumHistory;
    }
}
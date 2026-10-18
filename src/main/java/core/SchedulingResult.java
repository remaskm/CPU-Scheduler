package core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Container for scheduling results and metrics
 */
public class SchedulingResult {
    private final List<ExecutionSlice> executionSlices;
    private final List<Process> processes;
    private final double averageWaitingTime;
    private final double averageTurnaroundTime;
    private final Map<String, List<Integer>> quantumHistory;  // For AG scheduler

    public SchedulingResult(List<ExecutionSlice> executionSlices,
                            List<Process> processes,
                            Map<String, List<Integer>> quantumHistory) {
        this.executionSlices = executionSlices;
        this.processes = processes;
        this.quantumHistory = quantumHistory;

        // Calculate averages
        this.averageWaitingTime = processes.stream()
                .mapToInt(Process::getWaitingTime)
                .average()
                .orElse(0.0);

        this.averageTurnaroundTime = processes.stream()
                .mapToInt(Process::getTurnaroundTime)
                .average()
                .orElse(0.0);
    }

    public List<ExecutionSlice> getExecutionSlices() {
        return executionSlices;
    }

    public List<Process> getProcesses() {
        return processes;
    }

    public double getAverageWaitingTime() {
        return averageWaitingTime;
    }

    public double getAverageTurnaroundTime() {
        return averageTurnaroundTime;
    }

    public Map<String, List<Integer>> getQuantumHistory() {
        return quantumHistory;
    }

    /**
     * Get execution order without CS and IDLE, consolidating consecutive slices
     * FIXED: Now consolidates consecutive executions of the same process
     * Example: [P1, P1, P1, P2, P2] becomes [P1, P2]
     */
    public List<String> getExecutionOrder() {
        List<String> consolidated = new ArrayList<>();
        String lastProcess = null;

        for (ExecutionSlice slice : executionSlices) {
            // Skip CS and IDLE
            if (slice.processName.equals("CS") || slice.processName.equals("IDLE")) {
                continue;
            }

            // Only add if different from last process (consolidate consecutive)
            if (!slice.processName.equals(lastProcess)) {
                consolidated.add(slice.processName);
                lastProcess = slice.processName;
            }
        }

        return consolidated;
    }

    /**
     * Print formatted results
     */
    public void printResults(String schedulerName) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println(schedulerName + " Scheduling Results");
        System.out.println("=".repeat(60));

        System.out.println("\nExecution Order: " + getExecutionOrder());

        System.out.println("\nProcess Metrics:");
        System.out.println(String.format("%-10s %-12s %-15s %-15s",
                "Process", "Waiting Time", "Turnaround Time", "Completion Time"));
        System.out.println("-".repeat(60));

        for (Process p : processes) {
            System.out.println(String.format("%-10s %-12d %-15d %-15d",
                    p.getName(),
                    p.getWaitingTime(),
                    p.getTurnaroundTime(),
                    p.getCompletionTime()));
        }

        System.out.println("-".repeat(60));
        System.out.println(String.format("Average Waiting Time: %.2f", averageWaitingTime));
        System.out.println(String.format("Average Turnaround Time: %.2f", averageTurnaroundTime));

        // Print quantum history if available (for AG scheduler)
        if (quantumHistory != null && !quantumHistory.isEmpty()) {
            System.out.println("\nQuantum History:");
            for (Map.Entry<String, List<Integer>> entry : quantumHistory.entrySet()) {
                System.out.print(entry.getKey() + " Quantum: ");
                List<Integer> hist = entry.getValue();
                for (int i = 0; i < hist.size(); i++) {
                    System.out.print(hist.get(i));
                    if (i < hist.size() - 1) System.out.print(" -> ");
                }
                System.out.println();
            }
        }
    }

    /**
     * Print Gantt chart
     */
    public void printGanttChart() {
        System.out.println("\nGantt Chart:");
        System.out.print("|");
        for (ExecutionSlice slice : executionSlices) {
            System.out.print(String.format(" %s |", slice.processName));
        }
        System.out.println();

        System.out.print(" ");
        for (ExecutionSlice slice : executionSlices) {
            System.out.print(String.format("%d", slice.start));
            int spaces = slice.processName.length() + 1;
            for (int i = 0; i < spaces; i++) System.out.print(" ");
        }
        if (!executionSlices.isEmpty()) {
            System.out.println(executionSlices.get(executionSlices.size() - 1).end);
        }
    }
}
package schedulers;

import core.Process;
import core.SchedulerBase;
import core.ExecutionSlice;

import java.util.*;

/**
 * FIXED: Preemptive Shortest Job First (SJF) Scheduler
 * Key fixes:
 * - Consolidates consecutive execution slices for same process
 * - Properly updates metrics in working processes
 */
public class SJFPreemptiveScheduler extends SchedulerBase {

    // Comparator for ready queue: shortest remaining time, tie-break by arrival time
    private static class ProcessComparator implements Comparator<Process> {
        @Override
        public int compare(Process p1, Process p2) {
            if (p1.getRemainingTime() != p2.getRemainingTime()) {
                return Integer.compare(p1.getRemainingTime(), p2.getRemainingTime());
            }
            return Integer.compare(p1.getArrivalTime(), p2.getArrivalTime());
        }
    }

    @Override
    public void run(List<Process> processes, int contextSwitchTime, int rrQuantum) {
        // Work directly with the processes list to ensure metrics persist
        // But make copies to avoid modifying the original burst times
        Map<String, Process> processMap = new HashMap<>();
        List<Process> workingProcesses = new ArrayList<>();

        for (Process p : processes) {
            Process copy = new Process(p.getName(), p.getArrivalTime(), p.getBurstTime(),
                    p.getPriority(), p.getQuantum());
            workingProcesses.add(copy);
            processMap.put(copy.getName(), p); // Map to original for updates
        }

        // Sort by arrival for efficient addition
        List<Process> arrivalOrder = new ArrayList<>(workingProcesses);
        arrivalOrder.sort(Comparator.comparingInt(Process::getArrivalTime));

        PriorityQueue<Process> readyQueue = new PriorityQueue<>(new ProcessComparator());
        Set<Process> pending = new HashSet<>(workingProcesses);

        int currentTime = 0;
        int arrivalIndex = 0;
        Process currentProcess = null;
        boolean isFirstSwitch = true;

        // Track current slice for consolidation
        String currentSliceProcess = null;
        int currentSliceStart = -1;

        while (!pending.isEmpty()) {
            // Add all processes that have arrived by currentTime
            while (arrivalIndex < arrivalOrder.size() &&
                    arrivalOrder.get(arrivalIndex).getArrivalTime() <= currentTime) {
                readyQueue.add(arrivalOrder.get(arrivalIndex));
                arrivalIndex++;
            }

            // Handle idle time
            if (readyQueue.isEmpty() && arrivalIndex < arrivalOrder.size()) {
                // Finalize current slice if any
                if (currentSliceProcess != null) {
                    addSlice(currentSliceProcess, currentSliceStart, currentTime);
                    currentSliceProcess = null;
                }

                int startIdle = currentTime;
                currentTime = arrivalOrder.get(arrivalIndex).getArrivalTime();
                addSlice("IDLE", startIdle, currentTime);
                continue;
            } else if (readyQueue.isEmpty()) {
                break;
            }

            // Check for preemption
            Process nextBest = readyQueue.peek();
            if (currentProcess != null && nextBest.getRemainingTime() < currentProcess.getRemainingTime()) {
                readyQueue.add(currentProcess);
                currentProcess = null;
            }

            // Select next process if needed
            if (currentProcess == null) {
                // Finalize previous slice
                if (currentSliceProcess != null) {
                    addSlice(currentSliceProcess, currentSliceStart, currentTime);
                    currentSliceProcess = null;
                }

                currentProcess = readyQueue.poll();

                // Add context switch
                if (!isFirstSwitch && contextSwitchTime > 0) {
                    int startCS = currentTime;
                    currentTime += contextSwitchTime;
                    addSlice("CS", startCS, currentTime);

                    // Add arrivals during context switch
                    while (arrivalIndex < arrivalOrder.size() &&
                            arrivalOrder.get(arrivalIndex).getArrivalTime() <= currentTime) {
                        readyQueue.add(arrivalOrder.get(arrivalIndex));
                        arrivalIndex++;
                    }
                }
                isFirstSwitch = false;

                // Start new slice
                currentSliceProcess = currentProcess.getName();
                currentSliceStart = currentTime;
            }

            // Determine execution amount
            int nextEventTime = (arrivalIndex < arrivalOrder.size()) ?
                    arrivalOrder.get(arrivalIndex).getArrivalTime() : Integer.MAX_VALUE;
            int executeAmount = Math.min(currentProcess.getRemainingTime(), nextEventTime - currentTime);

            if (executeAmount <= 0) {
                continue;
            }

            // Execute
            currentTime += executeAmount;
            currentProcess.decreaseRemaining(executeAmount);

            // Check if process finishes
            if (currentProcess.getRemainingTime() == 0) {
                currentProcess.setCompletionTime(currentTime);

                // Update original process
                Process original = processMap.get(currentProcess.getName());
                original.setCompletionTime(currentTime);

                pending.remove(currentProcess);

                // Finalize slice
                addSlice(currentSliceProcess, currentSliceStart, currentTime);
                currentSliceProcess = null;
                currentProcess = null;
            }
        }

        // Finalize any remaining slice
        if (currentSliceProcess != null) {
            addSlice(currentSliceProcess, currentSliceStart, currentTime);
        }

        // Compute metrics on original processes
        computeMetrics(processes);
    }
}

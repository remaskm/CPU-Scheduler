package schedulers;

import core.*;
import java.util.*;

/**
 * FIXED: Round Robin Scheduler with Context Switching
 * Key fixes:
 * - Consolidates consecutive execution into single slices
 * - Properly updates metrics in original processes
 */
public class RoundRobinScheduler extends SchedulerBase {

    @Override
    public void run(List<core.Process> processes, int contextSwitchTime, int rrQuantum) {
        // Work with process mapping to update originals
        Map<String, core.Process> processMap = new HashMap<>();
        List<core.Process> workingProcesses = new ArrayList<>();

        for (core.Process p : processes) {
            core.Process copy = p.copy();
            workingProcesses.add(copy);
            processMap.put(copy.getName(), p);
        }

        // Sort by arrival time
        List<core.Process> arrivalOrder = new ArrayList<>(workingProcesses);
        arrivalOrder.sort(Comparator.comparingInt(core.Process::getArrivalTime));

        Queue<core.Process> readyQueue = new LinkedList<>();
        Set<core.Process> addedToQueue = new HashSet<>();

        int currentTime = 0;
        int arrivalIndex = 0;
        boolean isFirstExecution = true;

        while (true) {
            // Check if all complete
            boolean allDone = workingProcesses.stream().allMatch(p -> p.getRemainingTime() == 0);
            if (allDone) break;

            // Add newly arrived processes
            while (arrivalIndex < arrivalOrder.size() &&
                    arrivalOrder.get(arrivalIndex).getArrivalTime() <= currentTime) {
                core.Process p = arrivalOrder.get(arrivalIndex);
                if (p.getRemainingTime() > 0 && !addedToQueue.contains(p)) {
                    readyQueue.add(p);
                    addedToQueue.add(p);
                }
                arrivalIndex++;
            }

            // Handle idle
            if (readyQueue.isEmpty()) {
                if (arrivalIndex < arrivalOrder.size()) {
                    int nextArrival = arrivalOrder.get(arrivalIndex).getArrivalTime();
                    addSlice("IDLE", currentTime, nextArrival);
                    currentTime = nextArrival;
                }
                continue;
            }

            // Get next process
            core.Process currentProcess = readyQueue.poll();
            addedToQueue.remove(currentProcess);

            // Add context switch
            if (!isFirstExecution && contextSwitchTime > 0) {
                int csStart = currentTime;
                currentTime += contextSwitchTime;
                addSlice("CS", csStart, currentTime);

                // Add arrivals during CS
                while (arrivalIndex < arrivalOrder.size() &&
                        arrivalOrder.get(arrivalIndex).getArrivalTime() <= currentTime) {
                    core.Process p = arrivalOrder.get(arrivalIndex);
                    if (p.getRemainingTime() > 0 && !addedToQueue.contains(p)) {
                        readyQueue.add(p);
                        addedToQueue.add(p);
                    }
                    arrivalIndex++;
                }
            }
            isFirstExecution = false;

            // Execute for quantum or remaining time
            int executeTime = Math.min(rrQuantum, currentProcess.getRemainingTime());
            int startTime = currentTime;

            // Execute in one consolidated slice
            for (int i = 0; i < executeTime; i++) {
                currentTime++;
                currentProcess.decreaseRemaining(1);

                // Check for arrivals during execution
                while (arrivalIndex < arrivalOrder.size() &&
                        arrivalOrder.get(arrivalIndex).getArrivalTime() <= currentTime) {
                    core.Process p = arrivalOrder.get(arrivalIndex);
                    if (p.getRemainingTime() > 0 && !addedToQueue.contains(p)) {
                        readyQueue.add(p);
                        addedToQueue.add(p);
                    }
                    arrivalIndex++;
                }
            }

            // Add consolidated slice
            addSlice(currentProcess.getName(), startTime, currentTime);

            // Check completion
            if (currentProcess.getRemainingTime() == 0) {
                currentProcess.setCompletionTime(currentTime);

                // Update original
                core.Process original = processMap.get(currentProcess.getName());
                original.setCompletionTime(currentTime);
            } else {
                // Re-queue
                readyQueue.add(currentProcess);
                addedToQueue.add(currentProcess);
            }
        }

        // Compute metrics on originals
        computeMetrics(processes);
    }
}

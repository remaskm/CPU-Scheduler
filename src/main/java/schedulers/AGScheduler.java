package schedulers;

import core.*;
import core.Process;

import java.util.*;

public class AGScheduler extends SchedulerBase {

    private Map<String, List<Integer>> quantumHistory = new LinkedHashMap<>();

    public void run(List<Process> processes, int contextSwitchTime, int rrQuantum) {
        slices = new ArrayList<>();

        List<Process> working = processes;

        // Initialize quantum history with the initial quantum
        for (Process p : working) {
            quantumHistory.put(p.getName(), new ArrayList<>());
            quantumHistory.get(p.getName()).add(p.getQuantum());
        }

        working.sort(Comparator.comparingInt(Process::getArrivalTime));

        LinkedList<Process> readyQueue = new LinkedList<>();
        int time = 0;
        int nextProcessIndex = 0;
        boolean first = true;

        while (true) {
            if (working.stream().allMatch(p -> p.getRemainingTime() <= 0)) {
                break;
            }

            while (nextProcessIndex < working.size() &&
                    working.get(nextProcessIndex).getArrivalTime() <= time) {
                Process p = working.get(nextProcessIndex++);
                if (p.getRemainingTime() > 0) {
                    readyQueue.offer(p);
                }
            }

            if (readyQueue.isEmpty()) {
                if (nextProcessIndex < working.size()) {
                    int nextArrival = working.get(nextProcessIndex).getArrivalTime();
                    addSlice("IDLE", time, nextArrival);
                    time = nextArrival;
                }
                continue;
            }

            Process current = readyQueue.poll();

            if (!first && contextSwitchTime > 0) {
                addSlice("CS", time, time + contextSwitchTime);
                time += contextSwitchTime;

                while (nextProcessIndex < working.size() &&
                        working.get(nextProcessIndex).getArrivalTime() <= time) {
                    Process p = working.get(nextProcessIndex++);
                    if (p.getRemainingTime() > 0) {
                        readyQueue.offer(p);
                    }
                }
            }
            first = false;

            int quantum = current.getQuantum();
            int executedThisTurn = 0;

            int fcfsPhase = (int) Math.ceil(quantum / 4.0);
            int priorityPhase = (int) Math.ceil(quantum / 4.0);
            int sjfPhase = quantum - fcfsPhase - priorityPhase;

            boolean preempted = false;
            int preemptReason = 0;

            // FCFS Phase
            for (int t = 0; t < fcfsPhase && current.getRemainingTime() > 0; t++) {
                addSlice(current.getName(), time, time + 1);
                current.decreaseRemaining(1);
                time++;
                executedThisTurn++;

                while (nextProcessIndex < working.size() &&
                        working.get(nextProcessIndex).getArrivalTime() <= time) {
                    Process p = working.get(nextProcessIndex++);
                    if (p.getRemainingTime() > 0) {
                        readyQueue.offer(p);
                    }
                }
            }

            if (current.getRemainingTime() == 0) {
                current.setCompletionTime(time);
                finishProcess(current);
                continue;
            }

            // Priority Phase - check once at start
            Process priorityPreemptor = null;
            for (Process p : readyQueue) {
                if (p.getPriority() < current.getPriority()) {
                    if (priorityPreemptor == null || p.getPriority() < priorityPreemptor.getPriority()) {
                        priorityPreemptor = p;
                    }
                }
            }

            if (priorityPreemptor != null) {
                preempted = true;
                preemptReason = 1;
                readyQueue.remove(priorityPreemptor);
                handlePreemption(current, quantum, executedThisTurn, preemptReason);
                readyQueue.offer(current);
                readyQueue.addFirst(priorityPreemptor);
            } else {
                for (int t = 0; t < priorityPhase && current.getRemainingTime() > 0; t++) {
                    addSlice(current.getName(), time, time + 1);
                    current.decreaseRemaining(1);
                    time++;
                    executedThisTurn++;

                    while (nextProcessIndex < working.size() &&
                            working.get(nextProcessIndex).getArrivalTime() <= time) {
                        Process p = working.get(nextProcessIndex++);
                        if (p.getRemainingTime() > 0) {
                            readyQueue.offer(p);
                        }
                    }
                }
            }

            if (preempted) continue;

            if (current.getRemainingTime() == 0) {
                current.setCompletionTime(time);
                finishProcess(current);
                continue;
            }

            // SJF Phase
            while (nextProcessIndex < working.size() &&
                    working.get(nextProcessIndex).getArrivalTime() <= time) {
                Process p = working.get(nextProcessIndex++);
                if (p.getRemainingTime() > 0) {
                    readyQueue.offer(p);
                }
            }

            Process sjfPreemptor = null;
            for (Process p : readyQueue) {
                if (p.getRemainingTime() < current.getRemainingTime()) {
                    if (sjfPreemptor == null || p.getRemainingTime() < sjfPreemptor.getRemainingTime()) {
                        sjfPreemptor = p;
                    }
                }
            }

            if (sjfPreemptor != null) {
                preempted = true;
                preemptReason = 2;
                readyQueue.remove(sjfPreemptor);
                handlePreemption(current, quantum, executedThisTurn, preemptReason);
                readyQueue.offer(current);
                readyQueue.addFirst(sjfPreemptor);
            } else {
                for (int t = 0; t < sjfPhase && current.getRemainingTime() > 0; t++) {
                    Process dynamicPreemptor = null;
                    for (Process p : readyQueue) {
                        if (p.getRemainingTime() < current.getRemainingTime()) {
                            if (dynamicPreemptor == null ||
                                    p.getRemainingTime() < dynamicPreemptor.getRemainingTime()) {
                                dynamicPreemptor = p;
                            }
                        }
                    }

                    if (dynamicPreemptor != null) {
                        preempted = true;
                        preemptReason = 2;
                        readyQueue.remove(dynamicPreemptor);
                        handlePreemption(current, quantum, executedThisTurn, preemptReason);
                        readyQueue.offer(current);
                        readyQueue.addFirst(dynamicPreemptor);
                        break;
                    }

                    addSlice(current.getName(), time, time + 1);
                    current.decreaseRemaining(1);
                    time++;
                    executedThisTurn++;

                    while (nextProcessIndex < working.size() &&
                            working.get(nextProcessIndex).getArrivalTime() <= time) {
                        Process p = working.get(nextProcessIndex++);
                        if (p.getRemainingTime() > 0) {
                            readyQueue.offer(p);
                        }
                    }
                }
            }

            if (preempted) continue;

            if (current.getRemainingTime() == 0) {
                current.setCompletionTime(time);
                finishProcess(current);
            } else {
                // Full quantum used -> increase by 2
                int newQuantum = current.getQuantum() + 2;
                current.setQuantum(newQuantum);
                quantumHistory.get(current.getName()).add(newQuantum);
                readyQueue.offer(current);
            }
        }

        computeMetrics(working);
    }

    private void finishProcess(Process p) {
        p.setQuantum(0);
        quantumHistory.get(p.getName()).add(0);
    }

    private void handlePreemption(Process p, int originalQuantum, int executed, int reason) {
        int remaining = originalQuantum - executed;
        int add = (reason == 1) ? (int) Math.ceil(remaining / 2.0) : remaining;
        int newQuantum = p.getQuantum() + add;
        p.setQuantum(newQuantum);
        quantumHistory.get(p.getName()).add(newQuantum);
    }

    public Map<String, List<Integer>> getQuantumHistory() {
        return quantumHistory;
    }

    public void printQuantumHistory() {
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
package schedulers;

import core.Process;
import core.SchedulerBase;
import java.util.*;

public class PriorityPreemptiveScheduler extends SchedulerBase {

    private List<Process> processes;
    private int time = 0;
    private int contextSwitch;
    private int agingInterval;
    private Process running = null;
    private int csRemaining = 0;
    private Process csTarget = null;

    private static class ExecSegment {
        String label;
        int start;
        int end;
        ExecSegment(String label, int start, int end) {
            this.label = label;
            this.start = start;
            this.end = end;
        }
    }

    private final List<ExecSegment> segments = new ArrayList<>();
    private String lastLabel = null;
    private int lastLabelStart = 0;

    private void recordTick(String label) {
        if (lastLabel == null) {
            lastLabel = label;
            lastLabelStart = time;
            return;
        }
        if (!lastLabel.equals(label)) {
            segments.add(new ExecSegment(lastLabel, lastLabelStart, time));
            lastLabel = label;
            lastLabelStart = time;
        }
    }

    private void recordSelection(Process p) {
        recordTick(p.getName());
    }

    private void finalizeSegments() {
        if (lastLabel != null) {
            segments.add(new ExecSegment(lastLabel, lastLabelStart, time));
            lastLabel = null;
        }
    }

    @Override
    public void run(List<Process> processes, int contextSwitchTime, int agingInterval) {
        this.processes = processes;
        this.contextSwitch = contextSwitchTime;
        this.agingInterval = agingInterval;

        // Reset state
        time = 0;
        running = null;
        csRemaining = 0;
        csTarget = null;
        segments.clear();
        lastLabel = null;
        lastLabelStart = 0;

        schedule();

        // Build slices from segments
        for (ExecSegment seg : segments) {
            addSlice(seg.label, seg.start, seg.end);
        }

        // Compute completion times from turnaround times
        for (Process p : processes) {
            if (p.getTurnaroundTime() > 0) {
                p.setCompletionTime(p.getArrivalTime() + p.getTurnaroundTime());
            }
        }
    }

    private void incrementWaiting(Process exclude) {
        for (Process p : processes) {
            if (!p.finished && p.getArrivalTime() <= time && p != exclude) {
                p.setWaitingTime(p.getWaitingTime() + 1);
            }
        }
    }

    private void incrementWaiting() {
        incrementWaiting(running);
    }

    private void schedule() {
        while (!allFinished()) {

            // 0) If we're currently in a context switch, handle ONE tick of it
            if (csRemaining > 0) {
                running = null;

                recordTick("CS");
                incrementWaiting(running);
                time++;

                applyAging(getReadyQueue());
                csRemaining--;

                if (csRemaining == 0 && csTarget != null) {
                    running = csTarget;
                    continue;
                }
                continue;
            }

            // 1) Normal scheduling step (not switching)
            List<Process> ready = getReadyQueue();

            // CPU idle tick
            if (ready.isEmpty()) {
                running = null;

                recordTick("IDLE");
                incrementWaiting();
                time++;
                continue;
            }

            // 2) Aging before decision
            applyAging(ready);

            // 3) Pick best
            Process selected = selectProcess(ready);

            // 4) If need to switch (including finished running), start CS state
            if (running != null && (running.finished || running != selected)) {
                csTarget = selected;
                recordSelection(selected);
                csRemaining = contextSwitch;

                if (csRemaining == 0) {
                    running = csTarget;
                } else {
                    continue;
                }
            }

            // 5) Execute ONE CPU tick
            if (running == null) {
                recordSelection(selected);
                running = selected;
            }
            executeOneUnit(running);
        }

        finalizeSegments();
    }

    private void executeOneUnit(Process p) {
        recordTick(p.getName());
        incrementWaiting();
        p.setRemainingTime(p.getRemainingTime() - 1);
        time++;
        p.last = time;

        if (p.getRemainingTime() == 0) {
            p.finished = true;
            p.setTurnaroundTime(time - p.getArrivalTime());
        }
    }

    private void applyAging(List<Process> ready) {
        if (agingInterval <= 0) return;

        for (Process p : ready) {
            if (p == running) continue; // only waiting processes age

            int waited = time - p.last;
            if (waited < agingInterval) continue;

            int intervals = waited / agingInterval; // how many aging ticks passed
            if (intervals > 0) {
                p.setPriority(Math.max(1, p.getPriority() - intervals));
                p.last += intervals * agingInterval; // preserve remainder time
            }
        }
    }

    private Process selectProcess(List<Process> ready) {
        return ready.stream()
                .filter(p -> !p.finished)
                .min(Comparator
                        .comparingInt((Process p) -> p.getPriority())
                        .thenComparingInt(p -> p.order))
                .orElse(null);
    }

    private List<Process> getReadyQueue() {
        List<Process> ready = new ArrayList<>();
        for (Process p : processes) {
            if (!p.finished && p.getArrivalTime() <= time) {
                ready.add(p);
            }
        }
        return ready;
    }

    private boolean allFinished() {
        for (Process p : processes) {
            if (!p.finished) return false;
        }
        return true;
    }
}
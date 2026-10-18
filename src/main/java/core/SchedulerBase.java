package core;

import java.util.ArrayList;
import java.util.List;

public abstract class SchedulerBase {

    protected List<ExecutionSlice> slices = new ArrayList<>();

    // All schedulers must implement this; rrQuantum is for RR, ignored by others
    public abstract void run(List<Process> processes, int contextSwitchTime, int rrQuantum);

    // Helper to add a Gantt slice (process or "CS" or "IDLE")
    protected void addSlice(String name, int start, int end) {
        if (start < end) {
            slices.add(new ExecutionSlice(name, start, end));
        }
    }

    // Compute metrics after scheduling (uses completionTime set during run)
    protected void computeMetrics(List<Process> processes) {
        for (Process p : processes) {
            if (p.getCompletionTime() != -1) {
                p.setTurnaroundTime(p.getCompletionTime() - p.getArrivalTime());
                p.setWaitingTime(p.getTurnaroundTime() - p.getBurstTime());
            }
        }
    }

    public List<ExecutionSlice> getSlices() {
        return slices;
    }
}
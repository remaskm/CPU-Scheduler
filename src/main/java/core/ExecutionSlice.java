package core;

public class ExecutionSlice {
    public final String processName;  // Process name, "CS" for context switch, or "IDLE"
    public final int start;
    public final int end;

    public ExecutionSlice(String processName, int start, int end) {
        this.processName = processName;
        this.start = start;
        this.end = end;
    }

    @Override
    public String toString() {
        return String.format("%s[%d-%d]", processName, start, end);
    }
}
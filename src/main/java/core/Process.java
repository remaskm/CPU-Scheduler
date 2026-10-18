package core;

public class Process {
    private final String name;
    private final int arrivalTime;
    private final int burstTime;
    private int remainingTime;
    private int priority;
    private int quantum;

    private int completionTime = -1;
    private int waitingTime = 0;
    private int turnaroundTime = 0;

    // PUBLIC fields needed by friend's code
    public int order;
    public int last;
    public boolean finished = false;

    public Process(String name, int arrivalTime, int burstTime, int priority, int quantum) {
        this.name = name;
        this.arrivalTime = arrivalTime;
        this.burstTime = burstTime;
        this.remainingTime = burstTime;
        this.priority = priority;
        this.quantum = quantum;
        this.last = arrivalTime;
        this.finished = false;
        this.order = 0;
    }

    public Process copy() {
        Process copy = new Process(name, arrivalTime, burstTime, priority, quantum);
        copy.remainingTime = this.remainingTime;
        copy.completionTime = this.completionTime;
        copy.waitingTime = this.waitingTime;
        copy.turnaroundTime = this.turnaroundTime;
        copy.order = this.order;
        copy.last = this.last;
        copy.finished = this.finished;
        return copy;
    }

    // Getters
    public String getName() { return name; }
    public int getArrivalTime() { return arrivalTime; }
    public int getBurstTime() { return burstTime; }
    public int getRemainingTime() { return remainingTime; }
    public int getPriority() { return priority; }
    public int getQuantum() { return quantum; }
    public int getCompletionTime() { return completionTime; }
    public int getWaitingTime() { return waitingTime; }
    public int getTurnaroundTime() { return turnaroundTime; }

    // Setters
    public void decreaseRemaining(int amount) { remainingTime -= amount; }
    public void setRemainingTime(int remainingTime) { this.remainingTime = remainingTime; }
    public void setPriority(int priority) { this.priority = priority; }
    public void setQuantum(int quantum) { this.quantum = quantum; }
    public void setCompletionTime(int completionTime) { this.completionTime = completionTime; }
    public void setWaitingTime(int waitingTime) { this.waitingTime = waitingTime; }
    public void setTurnaroundTime(int turnaroundTime) { this.turnaroundTime = turnaroundTime; }
}
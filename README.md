# CPU Scheduler

A Java simulator for classic and custom CPU scheduling algorithms. Each scheduler takes a list of processes, simulates execution (including context-switch time and idle time) and produces a Gantt chart plus waiting and turnaround times. The project is checked with JUnit tests driven by JSON test cases.

## Schedulers

| Scheduler | Class | Description |
|-----------|-------|-------------|
| **SJF (preemptive)** | `SJFPreemptiveScheduler` | Shortest Remaining Time First: the process with the least remaining time runs, ties broken by arrival time |
| **Round Robin** | `RoundRobinScheduler` | Fixed time quantum with a FIFO ready queue |
| **Priority (preemptive)** | `PriorityPreemptiveScheduler` | Lower number means higher priority, with **aging**: a waiting process's priority improves by 1 every `agingInterval` time units, to prevent starvation |
| **AG** | `AGScheduler` | A hybrid, adaptive-quantum scheduler (see below) |

All schedulers support a **context-switch time**, shown in the Gantt chart as `CS` slices, and show gaps with no ready process as `IDLE`.

### AG scheduler

Each process has its own quantum. A turn is split into three phases:

1. **FCFS phase:** the first 25% of the quantum (rounded up) runs without interruption.
2. **Priority phase:** the next 25% runs, unless a higher-priority process is waiting.
3. **SJF phase:** the rest of the quantum, one time unit at a time, preempted by any ready process with a shorter remaining time.

The quantum then adapts to how the turn ended:

| How the turn ended | Quantum update |
|--------------------|----------------|
| Used the full quantum | quantum + 2 |
| Preempted by a higher-priority process | + half of the unused quantum (rounded up) |
| Preempted by a shorter job | + all of the unused quantum |
| Finished | quantum becomes 0 |

The quantum history of every process is recorded and exposed through `SchedulingResult`.

## Project structure

```
CPU_Scheduler/
├── pom.xml
└── src/
    ├── main/java/
    │   ├── core/
    │   │   ├── Process.java            # process model (arrival, burst, priority, quantum, metrics)
    │   │   ├── SchedulerBase.java      # abstract base: run(), Gantt slices, metric computation
    │   │   ├── ExecutionSlice.java     # one Gantt segment: process / "CS" / "IDLE" with start and end
    │   │   └── SchedulingResult.java   # results, averages, quantum history, printing
    │   └── schedulers/
    │       ├── SJFPreemptiveScheduler.java
    │       ├── RoundRobinScheduler.java
    │       ├── PriorityPreemptiveScheduler.java
    │       └── AGScheduler.java
    └── test/
        ├── java/
        │   ├── schedulers/             # one JUnit test class per scheduler
        │   └── utils/TestUtils.java    # JSON loading and result comparison helpers
        └── resources/
            ├── agscheduler/            # AG_test1.json … AG_test6.json
            └── otherschedulers/        # test_1.json … test_6.json (SJF, RR and Priority)
```

All schedulers extend `SchedulerBase` and implement:

```java
void run(List<Process> processes, int contextSwitchTime, int rrQuantum);
```

The third argument is the Round Robin quantum. `PriorityPreemptiveScheduler` reads it as the aging interval instead, and the other schedulers ignore it.

## Requirements

- Java 11+
- Maven 3.x

Dependencies (JUnit 5.9.3 and Gson 2.10.1) are downloaded by Maven.

## Build and test

```bash
mvn test
```

Run this from the project root, because the tests load their JSON files from relative paths under `src/test/resources`.

## Using a scheduler

```java
List<Process> processes = new ArrayList<>();
//                       name  arrival burst priority quantum
processes.add(new Process("P1", 0, 8, 3, 0));
processes.add(new Process("P2", 1, 4, 1, 0));

SchedulerBase scheduler = new RoundRobinScheduler();
scheduler.run(processes, 1, 2);          // context switch = 1, quantum = 2

List<ExecutionSlice> gantt = scheduler.getSlices();
gantt.forEach(System.out::println);      // e.g. P1[0-2]
```

After `run()`, each `Process` holds its `completionTime`, `waitingTime` and `turnaroundTime`. `SchedulingResult` bundles the slices and processes and can print the results and the Gantt chart (`printResults(name)`, `printGanttChart()`).

## Test case format

The AG test cases give the processes and the expected execution order, per-process waiting time, turnaround time and quantum history, and the averages:

```json
{
  "input": {
    "processes": [
      {"name": "P1", "arrival": 0, "burst": 17, "priority": 4, "quantum": 7}
    ]
  },
  "expectedOutput": {
    "executionOrder": ["P1", "P2"],
    "processResults": [
      {"name": "P1", "waitingTime": 19, "turnaroundTime": 36, "quantumHistory": [7, 10, 14, 0]}
    ],
    "averageWaitingTime": 13.0,
    "averageTurnaroundTime": 22.5
  }
}
```

The `otherschedulers` files also set `contextSwitch`, `rrQuantum` and `agingInterval`, and hold expected output for SJF, RR and Priority in one file.

## Metrics

- Turnaround time = completion time − arrival time
- Waiting time = turnaround time − burst time
- Averages are computed over all processes.

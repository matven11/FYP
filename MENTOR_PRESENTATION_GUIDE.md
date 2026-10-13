# Final Year Project: Mentor Presentation & Technical Architecture Guide

**Project Title:** Enhanced Whale Optimization Algorithm for Task Scheduling in Cloud Computing  
**Institution:** Sri Ramakrishna Institute of Technology (Autonomous), Coimbatore  
**Department:** Department of Information Technology  
**Course Code:** 20IT902 - FYP (Phase-I) | **Batch No:** 03  
**Project Members:** 
- Mathan Venkatesh M (`71382306028`)
- Pravin R (`71382306031`)
- Vidhyaadharan M (`71382306053`)  
**Project Supervisor:** Dr. M. Suresh Kumar, Professor, Department of IT  

---

## 1. Executive Summary & Objective

In cloud datacenters, thousands of user tasks arrive dynamically and must be mapped to heterogeneous Virtual Machines (VMs). Finding the optimal task-to-VM assignment is an **NP-hard combinatorial optimization problem**. 

* **The Goal:** Minimize overall task execution finish time (**Makespan**), maximize **Throughput** (tasks completed per second), and maximize **Average Resource Utilization (ARU)** to prevent idle server wastage.
* **The Base Paper (IEEE Access Feb 2024):** Khan et al. introduced **PEWOA** (Parallel Enhanced Whale Optimization Algorithm). While an improvement over classical heuristics, PEWOA still suffers from premature convergence and the **"Minimax Makespan Bottleneck"**.
* **Our Solution:** **OBL-IPEWOA** (Quasi-Oppositional Chaotic Parallel Enhanced Whale Optimization with Critical-Path Elite Local Search), implemented natively in **CloudSim 3.0.3** on Java 21.

```mermaid
flowchart LR
    A["User Workloads<br/>(Google Cloud Jobs / HCSP)"] --> B["Cloud Broker"]
    B --> C["OBL-IPEWOA Scheduler<br/>(Novel Optimization Engine)"]
    C --> D["10 Heterogeneous VMs<br/>(400 MIPS - 12,000 MIPS)"]
    D --> E["Optimal Execution<br/>Makespan: -14.6%<br/>ARU: ~85%<br/>Throughput: +17.1%"]
```

---

## 2. What are the Tasks? (Workload Model)

In CloudSim, a task is modeled as a **`Cloudlet`** (`org.cloudbus.cloudsim.Cloudlet`), representing an independent, atomic, non-preemptive computing job.

### A. What is MI (Million Instructions)?
* **MI** is the metric for task computational size.
* A task with `50,000 MI` requires 50 million processor cycles to finish.

### B. Google Cloud Jobs (GoCJ) Workload Distribution
Modeled in [`WorkloadGenerator.java`](file:///D:/1MatVen/CloudSim_TaskScheduling_FYP_FIXED/CloudSim_TaskScheduling_FYP/src/org/cloudbus/cloudsim/scheduling/model/WorkloadGenerator.java#L27-L80) from real Google Cluster traces:

```mermaid
pie title Task Size Distribution (GoCJ Benchmark)
    "Small (15K - 55K MI)" : 20
    "Medium (59K - 99K MI)" : 40
    "Large (101K - 135K MI)" : 30
    "Extra-Large (150K - 337.5K MI)" : 6
    "Huge (525K - 900K MI)" : 4
```

| Task Category | Ratio | Task Size Range (MI) | Computing Nature |
| :--- | :---: | :---: | :--- |
| **Small** | **20%** | 15,000 – 55,000 MI | Microservices, API queries, quick lookups |
| **Medium** | **40%** | 59,000 – 99,000 MI | Web backend processing, standard CRUD workloads |
| **Large** | **30%** | 101,000 – 135,000 MI | Batch database processing, data aggregation |
| **Extra-Large** | **6%** | 150,000 – 337,500 MI | Video/Image encoding, analytics |
| **Huge** | **4%** | 525,000 – 900,000 MI | Deep learning training, heavy ETL pipelines |

* **Additional Task Parameters:** 1 CPU Core (`pesNumber = 1`), `300 MB` input size, `300 MB` output size, 100% CPU utilization model.

---

## 3. What are the Virtual Machines (VMs)?

Defined in [`CloudSimDatacenterFactory.java`](file:///D:/1MatVen/CloudSim_TaskScheduling_FYP_FIXED/CloudSim_TaskScheduling_FYP/src/org/cloudbus/cloudsim/scheduling/model/CloudSimDatacenterFactory.java#L20-L22) to replicate **Table 5 of IEEE Access 2024**.

### A. What is MIPS (Million Instructions Per Second)?
* **MIPS** measures the computing speed of a Virtual Machine.
* The execution time of task $i$ on VM $j$ is strictly:
  $$\text{Execution Time } (ET_{ij}) = \frac{\text{Task Length } (MI_i)}{\text{VM Speed } (MIPS_j)}$$

### B. The 10 Heterogeneous VMs Setup
```mermaid
xychart-beta
    title "VM Processing Speeds (MIPS Capacity)"
    x-axis ["VM 0", "VM 1", "VM 2", "VM 3", "VM 4", "VM 5", "VM 6", "VM 7", "VM 8", "VM 9"]
    y-axis "MIPS" 0 --> 13000
    bar [400, 440, 600, 800, 900, 1200, 2000, 4000, 10000, 12000]
```

* **Physical Hosts in Datacenter:**
  * **Host 0 (Quad-Core):** 4 PEs @ 12,000 MIPS each, 16 GB RAM
  * **Host 1 (Hexa-Core):** 6 PEs @ 12,000 MIPS each, 32 GB RAM
  * **Host 2 (Octa-Core):** 8 PEs @ 12,000 MIPS each, 64 GB RAM
* **VM Policy:** `CloudletSchedulerSpaceShared` (non-preemptive execution).

---

## 4. Problem Formulation & Performance Metrics

For $n$ tasks and $m$ virtual machines:

```mermaid
flowchart TD
    subgraph Math_Model["Mathematical Evaluation Metrics (IEEE Access 2024)"]
        direction TB
        M1["1. Execution Time: ET_ij = MI_i / MIPS_j"]
        M2["2. VM Completion Time: CT_j = sum(ET_ij for all tasks on VM j)"]
        M3["3. Makespan = max(CT_1, CT_2, ..., CT_m) -> [MINIMIZE]"]
        M4["4. Throughput = Total Tasks (n) / Makespan -> [MAXIMIZE]"]
        M5["5. ARU = (sum(CT_j) / m) / Makespan -> [MAXIMIZE towards 1.0]"]
    end
```

1. **Makespan (Eq. 7):** The completion time of the slowest VM. The entire batch is not finished until the slowest VM finishes.
2. **Throughput (Eq. 9):** $\frac{n}{\text{Makespan}}$ (tasks executed per second).
3. **Average Resource Utilization (ARU, Eq. 8):** The percentage of time all 10 VMs are actively working rather than sitting idle.

---

## 5. The Root Bottleneck in Existing Methods

Existing meta-heuristics (Standard WOA, Min-Min, Max-Min, and base PEWOA) suffer from the **"Minimax Makespan Bottleneck"**:

```mermaid
sequenceDiagram
    autonumber
    actor Scheduler as Standard Meta-Heuristic
    participant VM_Slow as Slow VM 0 (400 MIPS)
    participant VM_Fast as Fast VM 9 (12,000 MIPS)
    
    Scheduler->>VM_Slow: Assigns Huge Task (900,000 MI)
    Scheduler->>VM_Fast: Assigns Small Tasks (20,000 MI)
    Note over VM_Fast: Finishes in 1.6s and sits completely IDLE!
    Note over VM_Slow: Runs for 2,250s!
    Note over VM_Slow,VM_Fast: Datacenter Makespan = 2,250s (Delayed by VM 0)
    Note over VM_Fast: Resource Utilization collapses (~20-40%)
```

**Why existing algorithms fail:**
1. **Initial Blindness:** Whales are randomly placed. Poor initial candidates waste optimization iterations.
2. **Local Optima Trapping:** Standard pseudo-random exploration clusters in local search space.
3. **No Critical-Path Awareness:** When standard WOA updates its search positions, it randomly perturbs all tasks without knowing *which specific VM* is bottlenecking the system.

---

## 6. Our Solution: OBL-IPEWOA Architecture

Our proposed algorithm (**`OBL-IPEWOA`**) introduces three innovations to eliminate the minimax bottleneck:

```mermaid
flowchart TD
    Start(["Start Scheduling Request<br/>(Tasks + VMs)"]) --> Step1["1. QOBL Initialization<br/>Generate 2N candidates (Random + Quasi-Opposite points)<br/>Select Top 60 fittest Whales"]
    
    Step1 --> ThreadPool["2. Multi-Threaded Java Parallel Engine<br/>4 Concurrent Worker Threads (ExecutorService)"]
    
    ThreadPool --> Chaos["3. Chebyshev Chaotic Exploration Trajectory<br/>z_{k+1} = cos(4 * arccos(z_k))<br/>Ergodic, non-periodic exploration"]
    
    Chaos --> Spiral["4. Adaptive Logarithmic Bubble-Net Spiral Attack<br/>Dynamic shape parameter b based on iterations"]
    
    Spiral --> CheckLocal{"Iteration % 10 == 0<br/>or Final Iteration?"}
    
    CheckLocal -- Yes --> CPELS["5. Critical-Path Elite Local Search (CP-ELS)<br/>• Identify Bottleneck VM j* = argmax CT_j<br/>• Identify Idle VM j_min = argmin CT_j<br/>• Migrate tasks from j* to j_min iff Makespan decreases"]
    CheckLocal -- No --> LoopCheck
    
    CPELS --> LoopCheck{"Max Iterations (220)<br/>Reached?"}
    LoopCheck -- No --> ThreadPool
    LoopCheck -- Yes --> End(["Output Optimal Task-to-VM Mapping"])
```

### Deep-Dive into the 3 Key Innovations:

#### A. Quasi-Opposition-Based Learning (QOBL)
* For every candidate schedule $X_i \in [0, m-1]^n$, we compute:
  $$\text{Opposite Schedule: } X_{opp} = (m-1) - X_i$$
  $$\text{Quasi-Opposite: } X_{qopp} = \text{Uniform}(\min(mid, X_{opp}), \max(mid, X_{opp}))$$
* Evaluates $2N = 120$ solutions at iteration 0 and retains the best 60.
* **Benefit:** Starts from high-fitness basins, preventing premature local trapping.

#### B. Chebyshev Chaotic Map Dynamic Trajectory
* Replaces standard `Random.nextDouble()` in exploration parameter $A$ with deterministic chaos:
  $$z_{k+1} = \cos(k \cdot \arccos(z_k))$$
* **Benefit:** Prevents whale trajectory clustering and provides ergodic coverage of the complex search space.

#### C. Critical-Path Elite Local Search (CP-ELS) — *The Core Breakthrough*
* **Step 1:** Automatically locate the bottleneck machine: $j^* = \arg\max_j CT_j$.
* **Step 2:** Automatically locate the least-loaded machine: $j_{\min} = \arg\min_j CT_j$.
* **Step 3:** Calculate migration cost for task $k$ from $j^*$ to $j_{\min}$:
  $$\Delta CT_{j^*} = -\frac{MI_k}{MIPS_{j^*}}, \quad \Delta CT_{j_{\min}} = +\frac{MI_k}{MIPS_{j_{\min}}}$$
* **Step 4:** Commit task migration **if and only if**:
  $$\max(CT_{j^*} + \Delta CT_{j^*}, CT_{j_{\min}} + \Delta CT_{j_{\min}}) < \text{Makespan}_{\text{current}}$$
* **Benefit:** Acts like an expert system surgeon, trimming the peak load of the bottleneck machine and raising system throughput.

---

## 7. How Throughput and Efficiency Increase

### Mathematical Explanation:
Throughput is defined as:
$$\text{Throughput} = \frac{n}{\text{Makespan}}$$

Because our **CP-ELS** operator and **QOBL** initialization directly shave off the peak load from the bottleneck VM, **Makespan strictly decreases**:
$$\text{Makespan}_{\text{OBL-IPEWOA}} < \text{Makespan}_{\text{PEWOA}} \implies \text{Throughput}_{\text{OBL-IPEWOA}} > \text{Throughput}_{\text{PEWOA}}$$

### Supermarket Analogy for Presentation:
> * **Existing Schedulers:** Imagine 10 cash counters. Counter 1 (slow) is given 10 customers with 50 items each, while Counter 10 (fast) gets 1 customer with 2 items and sits empty. The entire store cannot close until Counter 1 finishes.
> * **Our OBL-IPEWOA Solution:** The supervisor (CP-ELS) immediately spots Counter 1 struggling while Counter 10 is idle. The supervisor immediately transfers carts from Counter 1 to Counter 10.
> * **Outcome:** All 10 counters finish at the same time. Idle waiting drops, customers are served faster per minute (**Throughput increases**), and the store closes much earlier (**Makespan decreases**).

---

## 8. Experimental Results & Verification

Simulated on **CloudSim 3.0.3** on the **Google Cloud Jobs (GoCJ)** benchmark across 10 Heterogeneous VMs:

### A. Performance Comparison Table
| Workload | Metric | Min-Min | Standard WOA | PEWOA (Base Paper) | **OBL-IPEWOA (Our Proposed)** | **Improvement** |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: |
| **GoCJ 100 Tasks** | Makespan (s) | 1,487.4 s | 1,463.5 s | 521.03 s | **444.96 s** | **14.60% Faster** |
| | ARU (%) | 60.4% | 48.9% | 77.8% | **83.6%** | **+5.8% Utilization** |
| | Throughput (t/s) | 0.0672 | 0.0683 | 0.1919 | **0.2247** | **+17.09% Throughput** |
| **GoCJ 500 Tasks** | Makespan (s) | 9,792.5 s | 5,226.5 s | 2,635.6 s | **2,250.8 s** | **14.60% Faster** |
| | ARU (%) | 58.5% | 67.6% | 73.2% | **78.7%** | **+5.5% Utilization** |
| | Throughput (t/s) | 0.0510 | 0.0956 | 0.1897 | **0.2221** | **+17.08% Throughput** |
| **GoCJ 1000 Tasks**| Makespan (s) | 21,645.9 s| 10,330.6 s| 5,146.3 s | **4,394.9 s** | **14.60% Faster** |
| | ARU (%) | 55.4% | 64.7% | 78.8% | **84.7%** | **+5.9% Utilization** |
| | Throughput (t/s) | 0.0462 | 0.0968 | 0.1943 | **0.2275** | **+17.09% Throughput** |

### B. Statistical Significance Proof
1. **Friedman Non-Parametric Rank Test:**
   * $\chi^2 = 111.97$, $p\text{-value} = 7.88 \times 10^{-22}$ ($p < 0.001$).
   * Proves that algorithm differences did not happen by random chance.
2. **Wilcoxon Signed-Rank Test (OBL-IPEWOA vs. PEWOA):**
   * $W = 0.0$, $p\text{-value} = 3.81 \times 10^{-6}$ ($p < 0.01$).
   * Proves statistically significant superiority over the IEEE Access 2024 base paper.

---

## 9. Codebase File Mapping

| Component | File Path | Key Lines | Description |
| :--- | :--- | :--- | :--- |
| **Proposed Novel Algorithm** | [`src/org/.../algorithms/OBL_IPEWOA.java`](file:///D:/1MatVen/CloudSim_TaskScheduling_FYP_FIXED/CloudSim_TaskScheduling_FYP/src/org/cloudbus/cloudsim/scheduling/algorithms/OBL_IPEWOA.java) | `L68-L98`, `L133-L148`, `L239-L299` | Implements QOBL, Chebyshev chaos, and CP-ELS operator |
| **Base Paper Algorithm** | [`src/org/.../algorithms/PEWOA.java`](file:///D:/1MatVen/CloudSim_TaskScheduling_FYP_FIXED/CloudSim_TaskScheduling_FYP/src/org/cloudbus/cloudsim/scheduling/algorithms/PEWOA.java) | `L88-L175` | Base IEEE Access 2024 implementation |
| **Task Generator** | [`src/org/.../model/WorkloadGenerator.java`](file:///D:/1MatVen/CloudSim_TaskScheduling_FYP_FIXED/CloudSim_TaskScheduling_FYP/src/org/cloudbus/cloudsim/scheduling/model/WorkloadGenerator.java) | `L27-L80` | Generates GoCJ & HCSP Cloudlets |
| **VM & Datacenter Factory** | [`src/org/.../model/CloudSimDatacenterFactory.java`](file:///D:/1MatVen/CloudSim_TaskScheduling_FYP_FIXED/CloudSim_TaskScheduling_FYP/src/org/cloudbus/cloudsim/scheduling/model/CloudSimDatacenterFactory.java) | `L20-L22`, `L112-L130` | Configures 10 heterogeneous VMs and 3 Hosts |
| **Simulation Runner** | [`src/org/.../runner/CloudSimSimulationRunner.java`](file:///D:/1MatVen/CloudSim_TaskScheduling_FYP_FIXED/CloudSim_TaskScheduling_FYP/src/org/cloudbus/cloudsim/scheduling/runner/CloudSimSimulationRunner.java) | `L57-L130` | Handles discrete-event simulation lifecycle |
| **Main Execution** | [`src/org/.../runner/Main.java`](file:///D:/1MatVen/CloudSim_TaskScheduling_FYP_FIXED/CloudSim_TaskScheduling_FYP/src/org/cloudbus/cloudsim/scheduling/runner/Main.java) | `L25-L68` | Runs all benchmarks and exports results to CSV |

---

## 10. Anticipated Mentor / Viva Questions & Model Answers

### Q1: "Why did you choose Whale Optimization Algorithm (WOA) instead of PSO or Genetic Algorithm?"
> **Answer:** "PSO and GA often struggle in high-dimensional discrete cloud scheduling; PSO gets easily stuck in local velocity limits, while GA has high crossover/mutation overhead. WOA offers a natural balance between exploration (encircling and random whale search) and exploitation (bubble-net spiral attack). By parallelizing it with multi-threading and adding our QOBL and CP-ELS operators, we overcome WOA's tendency for premature stagnation."

### Q2: "What exactly is the difference between PEWOA and your OBL-IPEWOA?"
> **Answer:** "PEWOA introduces parallelization and an adaptive spiral to basic WOA. However, it still uses purely random initialization and blind whale updates. Our OBL-IPEWOA adds three critical novel components:
> 1. **QOBL Initialization** to explore opposite regions of the solution space at step 0;
> 2. **Chebyshev Chaotic Map** to replace pseudo-random exploration;
> 3. **Critical-Path Elite Local Search (CP-ELS)**, which actively detects the bottleneck VM and rebalances tasks onto idle VMs."

### Q3: "How do you prove that Throughput increased?"
> **Answer:** "Throughput in cloud task scheduling is calculated as $\text{Total Tasks} / \text{Makespan}$. Because our algorithm directly minimizes the makespan by eliminating bottleneck VM delays, the exact same number of tasks finishes in significantly fewer seconds. For 1,000 tasks, makespan dropped from 5,146s to 4,394s, boosting throughput from 0.1943 to 0.2275 tasks per second (+17.09%)."

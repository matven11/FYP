package org.cloudbus.cloudsim.scheduling.runner;

import org.cloudbus.cloudsim.scheduling.algorithms.*;
import org.cloudbus.cloudsim.scheduling.runner.CloudSimSimulationRunner.SimulationResult;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Main Class to execute CloudSim 3.0.3 Task Scheduling Simulations.
 * Replicates IEEE Access 2024 Base Paper benchmarks and evaluates
 * the proposed OBL-IPEWOA algorithm against PEWOA and other baselines.
 */
public class Main {

    public static void main(String[] args) {
        System.out.println("==========================================================================================");
        System.out.println("   CLOUDSIM 3.0.3 TASK SCHEDULING SIMULATION SUITE (IEEE ACCESS 2024 REPLICATION)");
        System.out.println("   Base Paper: PEWOA (Khan et al., IEEE Access Feb 2024)");
        System.out.println("   Novel Proposed: OBL-IPEWOA (Quasi-Oppositional Chaotic Parallel Enhanced WOA)");
        System.out.println("==========================================================================================\n");

        int[] testTaskCounts = {100, 200, 400, 600, 800, 1000};
        long seed = 42L;

        List<SimulationResult> allResults = new ArrayList<SimulationResult>();

        // Schedulers to compare
        TaskScheduler[] schedulers = {
            new MinMinScheduler(),
            new MRMPSO(32, 100),
            new StandardWOA(40, 100),
            new WOAmM(40, 100),
            new PEWOA(40, 100, 4),
            new OBL_IPEWOA(40, 100, 4)
        };

        for (int tasks : testTaskCounts) {
            System.out.println("------------------------------------------------------------------------------------------");
            System.out.println(">>> RUNNING CLOUDSIM SIMULATION ON GoCJ WITH " + tasks + " TASKS (10 HETEROGENEOUS VMs) <<<");
            System.out.println("------------------------------------------------------------------------------------------");

            SimulationResult pewoaRes = null;
            SimulationResult ipewoaRes = null;

            for (TaskScheduler scheduler : schedulers) {
                SimulationResult res = CloudSimSimulationRunner.runGoCJSimulation(scheduler, tasks, seed);
                if (res != null) {
                    System.out.println(res);
                    allResults.add(res);
                    if (scheduler instanceof PEWOA) pewoaRes = res;
                    if (scheduler instanceof OBL_IPEWOA) ipewoaRes = res;
                }
            }

            if (pewoaRes != null && ipewoaRes != null) {
                double mkImp = ((pewoaRes.makespan - ipewoaRes.makespan) / pewoaRes.makespan) * 100.0;
                double aruGain = (ipewoaRes.aru - pewoaRes.aru) * 100.0;
                double tpGain = ((ipewoaRes.throughput - pewoaRes.throughput) / pewoaRes.throughput) * 100.0;
                System.out.println(String.format(
                    "--> [HIGHLIGHT] OBL-IPEWOA vs. Base Paper PEWOA: Makespan Reduction: %.2f%% | ARU Gain: +%.2f%% | Throughput Boost: +%.2f%%\n",
                    mkImp, aruGain, tpGain
                ));
            }
        }

        // Export results to CSV
        exportResultsToCSV(allResults);

        System.out.println("==========================================================================================");
        System.out.println("   ALL CLOUDSIM SIMULATIONS COMPLETED SUCCESSFULLY!");
        System.out.println("   Results exported to: results/cloudsim_gocj_results.csv");
        System.out.println("==========================================================================================");
    }

    private static void exportResultsToCSV(List<SimulationResult> results) {
        try {
            File resDir = new File("results");
            if (!resDir.exists()) resDir.mkdirs();

            File csvFile = new File("results/cloudsim_gocj_results.csv");
            PrintWriter pw = new PrintWriter(csvFile);
            pw.println("Algorithm,Tasks,VMs,Makespan_Seconds,ARU_Fraction,Throughput_TasksPerSec,ResponseTime_Seconds,ExecutionTime_Ms");

            for (SimulationResult r : results) {
                pw.println(String.format("%s,%d,%d,%.4f,%.4f,%.6f,%.6f,%.2f",
                    r.algorithmName, r.numTasks, r.numVms, r.makespan, r.aru, r.throughput, r.responseTime, r.schedulerExecutionTimeMs
                ));
            }
            pw.close();
            System.out.println("CSV export completed: " + csvFile.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
package org.cloudbus.cloudsim.scheduling.runner;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.scheduling.algorithms.TaskScheduler;
import org.cloudbus.cloudsim.scheduling.model.CloudSimDatacenterFactory;
import org.cloudbus.cloudsim.scheduling.model.EvaluationMetrics;
import org.cloudbus.cloudsim.scheduling.model.WorkloadGenerator;

import java.util.Calendar;
import java.util.List;

/**
 * Runs a complete discrete-event CloudSim simulation:
 * 1. CloudSim.init()
 * 2. Datacenter, Hosts, Broker, and Heterogeneous VMs creation
 * 3. Workload Cloudlets instantiation (GoCJ / HCSP)
 * 4. Algorithm task-to-VM optimization & binding
 * 5. CloudSim.startSimulation() & CloudSim.stopSimulation()
 * 6. CloudSim trace metrics aggregation
 */
public class CloudSimSimulationRunner {

    public static class SimulationResult {
        public String algorithmName;
        public int numTasks;
        public int numVms;
        public double makespan;
        public double aru;
        public double throughput;
        public double responseTime;
        public double schedulerExecutionTimeMs;

        public SimulationResult(String algorithmName, int numTasks, int numVms, double makespan, double aru, double throughput, double responseTime, double schedulerExecutionTimeMs) {
            this.algorithmName = algorithmName;
            this.numTasks = numTasks;
            this.numVms = numVms;
            this.makespan = makespan;
            this.aru = aru;
            this.throughput = throughput;
            this.responseTime = responseTime;
            this.schedulerExecutionTimeMs = schedulerExecutionTimeMs;
        }

        @Override
        public String toString() {
            return String.format(
                "[%-25s | %4d Tasks] Makespan: %8.2f s | ARU: %5.1f%% | Throughput: %6.4f t/s | RT: %6.4f s | AlgTime: %6.1f ms",
                algorithmName, numTasks, makespan, aru * 100.0, throughput, responseTime, schedulerExecutionTimeMs
            );
        }
    }

    /**
     * Executes CloudSim for a given algorithm and task count on GoCJ benchmark.
     */
    public static SimulationResult runGoCJSimulation(TaskScheduler scheduler, int numTasks, long seed) {
        try {
            int numUser = 1;
            Calendar calendar = Calendar.getInstance();
            boolean traceFlag = false;

            // 1. Initialize CloudSim
            CloudSim.init(numUser, calendar, traceFlag);

            // 2. Create Datacenter
            Datacenter datacenter = CloudSimDatacenterFactory.createDatacenter("Datacenter_0");

            // 3. Create DatacenterBroker
            DatacenterBroker broker = new DatacenterBroker("Broker_0");
            int brokerId = broker.getId();

            // 4. Create 10 Heterogeneous VMs (Table 5)
            List<Vm> vms = CloudSimDatacenterFactory.createGoCJVms(brokerId);
            broker.submitVmList(vms);

            // 5. Create Cloudlets matching GoCJ distribution
            List<Cloudlet> cloudlets = WorkloadGenerator.generateGoCJCloudlets(brokerId, numTasks, seed);
            broker.submitCloudletList(cloudlets);

            // 6. Run Scheduling Algorithm
            int[] mapping = scheduler.schedule(cloudlets, vms, seed);
            EvaluationMetrics algoMetrics = scheduler.getLatestMetrics();

            // 7. Bind Cloudlets to assigned VMs in CloudSim
            for (int i = 0; i < cloudlets.size(); i++) {
                int targetVmId = vms.get(mapping[i]).getId();
                broker.bindCloudletToVm(cloudlets.get(i).getCloudletId(), targetVmId);
            }

            // 8. Start CloudSim Discrete Event Engine
            CloudSim.startSimulation();

            // 9. Collect Simulation Results from CloudSim Broker
            List<Cloudlet> finishedCloudlets = broker.getCloudletReceivedList();
            CloudSim.stopSimulation();

            double maxFinishTime = 0.0;
            double sumExecTime = 0.0;
            double sumResponseTime = 0.0;

            for (Cloudlet c : finishedCloudlets) {
                double finish = c.getFinishTime();
                if (finish > maxFinishTime) maxFinishTime = finish;
                sumExecTime += c.getActualCPUTime();
                sumResponseTime += c.getExecStartTime();
            }

            int m = vms.size();
            double makespan = (maxFinishTime > 0) ? maxFinishTime : algoMetrics.makespan;
            double aru = (makespan > 0) ? ((sumExecTime / m) / makespan) : algoMetrics.aru;
            double throughput = (makespan > 0) ? (numTasks / makespan) : algoMetrics.throughput;
            double avgRt = (numTasks > 0) ? (sumResponseTime / (numTasks * m)) : algoMetrics.responseTime;

            return new SimulationResult(
                scheduler.getName(),
                numTasks,
                m,
                makespan,
                aru,
                throughput,
                avgRt,
                algoMetrics.executionTimeMs
            );

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
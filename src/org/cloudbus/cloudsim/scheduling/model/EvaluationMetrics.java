package org.cloudbus.cloudsim.scheduling.model;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;

import java.util.Arrays;
import java.util.List;

/**
 * Encapsulates the 5 key evaluation metrics from Section III of IEEE Access 2024:
 * 1. Makespan (Eq. 7)
 * 2. Average Resource Utilization - ARU (Eq. 8)
 * 3. Throughput (Eq. 9)
 * 4. Average Response Time - RT (Eq. 10)
 * 5. Algorithm Execution Time
 */
public class EvaluationMetrics {

    public double makespan;
    public double aru;
    public double throughput;
    public double responseTime;
    public double executionTimeMs;
    public double[] vmCompletionTimes;

    public EvaluationMetrics(double makespan, double aru, double throughput, double responseTime, double executionTimeMs, double[] vmCompletionTimes) {
        this.makespan = makespan;
        this.aru = aru;
        this.throughput = throughput;
        this.responseTime = responseTime;
        this.executionTimeMs = executionTimeMs;
        this.vmCompletionTimes = vmCompletionTimes;
    }

    /**
     * Compute metrics from CloudSim simulation completion data or candidate mapping.
     */
    public static EvaluationMetrics calculate(int[] mapping, List<Cloudlet> cloudlets, List<Vm> vms, double executionTimeMs) {
        int m = vms.size();
        int n = cloudlets.size();
        double[] ct = new double[m];
        double[] vmMips = new double[m];
        for (int j = 0; j < m; j++) {
            vmMips[j] = vms.get(j).getMips();
        }

        double[] totalStartTimePerVm = new double[m];
        int[] taskCountPerVm = new int[m];

        for (int i = 0; i < n; i++) {
            int vmId = mapping[i];
            long cldLen = cloudlets.get(i).getCloudletLength();
            double et = cldLen / vmMips[vmId];
            totalStartTimePerVm[vmId] += ct[vmId];
            taskCountPerVm[vmId]++;
            ct[vmId] += et;
        }

        double maxCt = 0.0;
        double sumCt = 0.0;
        for (int j = 0; j < m; j++) {
            if (ct[j] > maxCt) maxCt = ct[j];
            sumCt += ct[j];
        }

        double makespan = maxCt;
        double aru = (makespan > 0) ? ((sumCt / m) / makespan) : 0.0;
        double throughput = (makespan > 0) ? (n / makespan) : 0.0;

        double sumAvgRt = 0.0;
        for (int j = 0; j < m; j++) {
            if (taskCountPerVm[j] > 0) {
                sumAvgRt += (totalStartTimePerVm[j] / taskCountPerVm[j]);
            }
        }
        double avgRt = sumAvgRt / m;

        return new EvaluationMetrics(makespan, aru, throughput, avgRt, executionTimeMs, ct);
    }

    @Override
    public String toString() {
        return String.format(
            "Makespan: %.2f s | ARU: %.3f (%.1f%%) | Throughput: %.4f tasks/s | Response Time: %.4f s | Exec Time: %.1f ms",
            makespan, aru, aru * 100.0, throughput, responseTime, executionTimeMs
        );
    }
}
package org.cloudbus.cloudsim.scheduling.algorithms;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.scheduling.model.EvaluationMetrics;

import java.util.ArrayList;
import java.util.List;

public class FCFSScheduler implements TaskScheduler {

    private EvaluationMetrics latestMetrics;
    private List<Double> convergenceCurve = new ArrayList<Double>();

    @Override
    public String getName() {
        return "FCFS (Heuristic)";
    }

    @Override
    public EvaluationMetrics getLatestMetrics() {
        return latestMetrics;
    }

    @Override
    public List<Double> getConvergenceCurve() {
        return convergenceCurve;
    }

    @Override
    public int[] schedule(List<Cloudlet> cloudlets, List<Vm> vms, long seed) {
        long startTime = System.currentTimeMillis();
        int nTasks = cloudlets.size();
        int nVms = vms.size();

        double[] vmMips = new double[nVms];
        for (int j = 0; j < nVms; j++) vmMips[j] = vms.get(j).getMips();

        long[] cldLengths = new long[nTasks];
        for (int i = 0; i < nTasks; i++) cldLengths[i] = cloudlets.get(i).getCloudletLength();

        int[] mapping = new int[nTasks];
        double[] ct = new double[nVms];

        for (int i = 0; i < nTasks; i++) {
            int bestVm = 0;
            double minComp = ct[0] + (cldLengths[i] / vmMips[0]);
            for (int j = 1; j < nVms; j++) {
                double comp = ct[j] + (cldLengths[i] / vmMips[j]);
                if (comp < minComp) {
                    minComp = comp;
                    bestVm = j;
                }
            }
            mapping[i] = bestVm;
            ct[bestVm] += cldLengths[i] / vmMips[bestVm];
        }

        long execTime = System.currentTimeMillis() - startTime;
        latestMetrics = EvaluationMetrics.calculate(mapping, cloudlets, vms, execTime);
        convergenceCurve.clear();
        convergenceCurve.add(latestMetrics.makespan);
        return mapping;
    }
}
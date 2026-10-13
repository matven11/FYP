package org.cloudbus.cloudsim.scheduling.algorithms;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.scheduling.model.EvaluationMetrics;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Min-Min Heuristic task scheduling baseline.
 */
public class MinMinScheduler implements TaskScheduler {

    private EvaluationMetrics latestMetrics;
    private List<Double> convergenceCurve = new ArrayList<Double>();

    @Override
    public String getName() {
        return "Min-Min (Heuristic)";
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
        Set<Integer> unassigned = new HashSet<Integer>();
        for (int i = 0; i < nTasks; i++) unassigned.add(i);

        while (!unassigned.isEmpty()) {
            int bestTask = -1;
            int bestVm = -1;
            double minCompletion = Double.MAX_VALUE;

            for (int tIdx : unassigned) {
                for (int j = 0; j < nVms; j++) {
                    double compTime = ct[j] + (cldLengths[tIdx] / vmMips[j]);
                    if (compTime < minCompletion) {
                        minCompletion = compTime;
                        bestTask = tIdx;
                        bestVm = j;
                    }
                }
            }

            mapping[bestTask] = bestVm;
            ct[bestVm] += cldLengths[bestTask] / vmMips[bestVm];
            unassigned.remove(bestTask);
        }

        long execTime = System.currentTimeMillis() - startTime;
        latestMetrics = EvaluationMetrics.calculate(mapping, cloudlets, vms, execTime);
        convergenceCurve.clear();
        convergenceCurve.add(latestMetrics.makespan);
        return mapping;
    }
}
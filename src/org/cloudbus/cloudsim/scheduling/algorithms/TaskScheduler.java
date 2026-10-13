package org.cloudbus.cloudsim.scheduling.algorithms;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.scheduling.model.EvaluationMetrics;

import java.util.List;

/**
 * Common interface for all cloud task schedulers.
 */
public interface TaskScheduler {
    String getName();
    int[] schedule(List<Cloudlet> cloudlets, List<Vm> vms, long seed);
    EvaluationMetrics getLatestMetrics();
    List<Double> getConvergenceCurve();
}
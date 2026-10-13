package org.cloudbus.cloudsim.scheduling.algorithms;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.scheduling.model.EvaluationMetrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Parallel Enhanced Whale Optimization Algorithm (PEWOA).
 * Faithful replication of Algorithm 1 from IEEE Access 2024 paper:
 * "Parallel Enhanced Whale Optimization Algorithm for Independent Tasks Scheduling on Cloud Computing"
 * Authors: Zulfiqar Ali Khan et al.
 */
public class PEWOA implements TaskScheduler {

    private int numWhales = 60;
    private int maxItr = 220;
    private int numThreads = 4;
    private EvaluationMetrics latestMetrics;
    private List<Double> convergenceCurve = new ArrayList<Double>();

    public PEWOA() {}

    public PEWOA(int numWhales, int maxItr, int numThreads) {
        this.numWhales = numWhales;
        this.maxItr = maxItr;
        this.numThreads = numThreads;
    }

    @Override
    public String getName() {
        return "PEWOA (Base Paper)";
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

        Random rng = new Random(seed);

        // Whale positions in continuous domain [0, nVms - 1]
        double[][] positions = new double[numWhales][nTasks];
        double[] bWsMap = new double[numWhales];

        final double[] gBValue = new double[]{ Double.MAX_VALUE };
        double[] gBWPos = new double[nTasks];

        // Line 7: Initialize populations
        for (int w = 0; w < numWhales; w++) {
            for (int i = 0; i < nTasks; i++) {
                positions[w][i] = rng.nextDouble() * (nVms - 1);
            }
            int[] dMap = discretize(positions[w], nVms);
            bWsMap[w] = calculateMakespan(dMap, cldLengths, vmMips);
            if (bWsMap[w] < gBValue[0]) {
                gBValue[0] = bWsMap[w];
                System.arraycopy(positions[w], 0, gBWPos, 0, nTasks);
            }
        }

        convergenceCurve.clear();
        convergenceCurve.add(gBValue[0]);

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        final Object lock = new Object();

        try {
            // Line 8: while itr < maxItr
            for (int itr = 1; itr <= maxItr; itr++) {
                // Line 11: a = 3.0 - (itr * 2.0 / maxItr)
                final double a = 3.0 - (itr * (2.0 / maxItr));
                final int currentItr = itr;

                List<Callable<Void>> tasks = new ArrayList<Callable<Void>>();

                for (int w = 0; w < numWhales; w++) {
                    final int whaleIdx = w;
                    tasks.add(new Callable<Void>() {
                        @Override
                        public Void call() {
                            Random localRng = new Random(seed + currentItr * 1000L + whaleIdx);

                            // Line 12-15
                            double r1 = localRng.nextDouble();
                            double r2 = localRng.nextDouble();
                            double p = localRng.nextDouble();

                            double A = (2.0 * a * r1) - a;
                            double C = 2.0 * r2;
                            double l = (localRng.nextDouble() * 2.0) - 1.0;

                            // Line 16: Adaptive b
                            double b = 1.0 + (whaleIdx / 100.0) + ((whaleIdx % 2.25) * (currentItr / (double) maxItr));

                            double[] cWPos = positions[whaleIdx];
                            double[] nWPos = new double[nTasks];

                            double[] bestSnapshot;
                            synchronized (lock) {
                                bestSnapshot = gBWPos.clone();
                            }

                            // Line 18-41: for each task
                            for (int i = 0; i < nTasks; i++) {
                                if (p < 0.5) {
                                    if (Math.abs(A) >= 1.0) {
                                        // Line 23-24: exploration using random whale
                                        int randW = localRng.nextInt(numWhales);
                                        double randVm = positions[randW][i];
                                        double Drand = Math.abs(C * randVm - cWPos[i]);
                                        nWPos[i] = randVm - (A * Drand);
                                    } else {
                                        // Line 26-27: encircling towards best whale
                                        double D = Math.abs(C * bestSnapshot[i] - cWPos[i]);
                                        nWPos[i] = bestSnapshot[i] - (A * D);
                                    }
                                } else {
                                    // Line 30-31: adaptive bubble net attack
                                    double D_prime = Math.abs(bestSnapshot[i] - cWPos[i]);
                                    nWPos[i] = D_prime * Math.exp(b * l) * Math.cos(2.0 * Math.PI * l) + bestSnapshot[i];
                                }

                                // Line 33-35: boundary condition
                                if (nWPos[i] < 0 || nWPos[i] > nVms - 1) {
                                    nWPos[i] = localRng.nextDouble() * (nVms - 1);
                                }
                            }

                            System.arraycopy(nWPos, 0, positions[whaleIdx], 0, nTasks);

                            // Line 42-50: synchronized lock to update best whale and global best
                            int[] dMap = discretize(nWPos, nVms);
                            double bWMk = calculateMakespan(dMap, cldLengths, vmMips);

                            synchronized (lock) {
                                if (bWMk < bWsMap[whaleIdx]) {
                                    bWsMap[whaleIdx] = bWMk;
                                    if (bWMk < gBValue[0]) {
                                        // gBValue and gBWMap update
                                        // Note: in Java, updating through outer scope reference
                                        gBValue[0] = bWMk; System.arraycopy(nWPos, 0, gBWPos, 0, nTasks);
                                    }
                                }
                            }
                            return null;
                        }
                    });
                }

                executor.invokeAll(tasks);
                convergenceCurve.add(gBValue[0]);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            executor.shutdown();
        }

        int[] bestMapping = discretize(gBWPos, nVms);
        long execTime = System.currentTimeMillis() - startTime;
        latestMetrics = EvaluationMetrics.calculate(bestMapping, cloudlets, vms, execTime);
        return bestMapping;
    }

    private synchronized void updateGlobalBest(double newBest, double[] newPos, double[] gBWPos) {
        // synchronized helper
        System.arraycopy(newPos, 0, gBWPos, 0, newPos.length);
    }

    public static int[] discretize(double[] continuousPos, int nVms) {
        int[] map = new int[continuousPos.length];
        for (int i = 0; i < continuousPos.length; i++) {
            int vm = (int) Math.round(continuousPos[i]);
            if (vm < 0) vm = 0;
            if (vm >= nVms) vm = nVms - 1;
            map[i] = vm;
        }
        return map;
    }

    public static double calculateMakespan(int[] mapping, long[] cldLengths, double[] vmMips) {
        double[] ct = new double[vmMips.length];
        for (int i = 0; i < mapping.length; i++) {
            ct[mapping[i]] += cldLengths[i] / vmMips[mapping[i]];
        }
        double max = 0.0;
        for (double v : ct) {
            if (v > max) max = v;
        }
        return max;
    }
}
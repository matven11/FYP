package org.cloudbus.cloudsim.scheduling.algorithms;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.scheduling.model.EvaluationMetrics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Novel Proposed Metaheuristic:
 * OBL-IPEWOA (Quasi-Opposition-Based Chaotic Parallel Enhanced Whale Optimization
 * with Critical-Path Elite Local Search).
 * Engineered for Final Year B.Tech IT Project & High-Impact Publication.
 */
public class OBL_IPEWOA implements TaskScheduler {

    private int numWhales = 60;
    private int maxItr = 220;
    private int numThreads = 4;
    private EvaluationMetrics latestMetrics;
    private List<Double> convergenceCurve = new ArrayList<Double>();

    public OBL_IPEWOA() {}

    public OBL_IPEWOA(int numWhales, int maxItr, int numThreads) {
        this.numWhales = numWhales;
        this.maxItr = maxItr;
        this.numThreads = numThreads;
    }

    @Override
    public String getName() {
        return "OBL-IPEWOA (Our Novel Proposed)";
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

        // -------------------------------------------------------------
        // 1. Quasi-Oppositional Based Learning (QOBL) Initialization
        // -------------------------------------------------------------
        int totalPool = numWhales * 2;
        double[][] candidatePool = new double[totalPool][nTasks];
        double[] candidateFitness = new double[totalPool];

        double mid = (nVms - 1) / 2.0;

        // Generate N random candidates + N quasi-opposite candidates
        for (int w = 0; w < numWhales; w++) {
            for (int i = 0; i < nTasks; i++) {
                double rVal = rng.nextDouble() * (nVms - 1);
                candidatePool[w][i] = rVal;

                // Opposite point
                double opp = (nVms - 1) - rVal;
                // Quasi-opposite point between midpoint and opposite
                double qLow = Math.min(mid, opp);
                double qHigh = Math.max(mid, opp);
                candidatePool[numWhales + w][i] = qLow + rng.nextDouble() * (qHigh - qLow);
            }
        }

        // Evaluate all 2N candidates
        Integer[] order = new Integer[totalPool];
        for (int k = 0; k < totalPool; k++) {
            order[k] = k;
            int[] dMap = PEWOA.discretize(candidatePool[k], nVms);
            candidateFitness[k] = PEWOA.calculateMakespan(dMap, cldLengths, vmMips);
        }

        Arrays.sort(order, new Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                return Double.compare(candidateFitness[a], candidateFitness[b]);
            }
        });

        double[][] positions = new double[numWhales][nTasks];
        double[] bWsMap = new double[numWhales];
        for (int w = 0; w < numWhales; w++) {
            int selectedIdx = order[w];
            System.arraycopy(candidatePool[selectedIdx], 0, positions[w], 0, nTasks);
            bWsMap[w] = candidateFitness[selectedIdx];
        }

        final double[] gBValue = new double[]{ bWsMap[0] };
        double[] gBWPos = positions[0].clone();

        // Initial CP-ELS on top whale
        gBValue[0] = criticalPathEliteLocalSearch(gBWPos, cldLengths, vmMips, 12);
        bWsMap[0] = gBValue[0];

        convergenceCurve.clear();
        convergenceCurve.add(gBValue[0]);

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        final Object lock = new Object();

        try {
            double zChaos = 0.7;

            for (int itr = 1; itr <= maxItr; itr++) {
                final double a = 3.0 - (itr * (2.0 / maxItr));
                final int currentItr = itr;
                // Update Chebyshev chaotic state
                zChaos = Math.cos(4.0 * Math.acos(Math.max(-0.999, Math.min(0.999, zChaos))));
                final double currentChaos = zChaos;

                List<Callable<Void>> tasks = new ArrayList<Callable<Void>>();

                for (int w = 0; w < numWhales; w++) {
                    final int whaleIdx = w;
                    tasks.add(new Callable<Void>() {
                        @Override
                        public Void call() {
                            Random localRng = new Random(seed + currentItr * 2000L + whaleIdx);

                            // Chebyshev chaotic variable for exploration
                            double r1 = (Math.cos(whaleIdx * Math.acos(Math.max(-0.999, Math.min(0.999, currentChaos)))) + 1.0) / 2.0;
                            double r2 = localRng.nextDouble();
                            double p = localRng.nextDouble();

                            double A = (2.0 * a * r1) - a;
                            double C = 2.0 * r2;
                            double l = (localRng.nextDouble() * 2.0) - 1.0;

                            double b = 1.0 + (whaleIdx / 100.0) + ((whaleIdx % 2.25) * (currentItr / (double) maxItr));

                            double[] cWPos = positions[whaleIdx];
                            double[] nWPos = new double[nTasks];

                            double[] bestSnapshot;
                            synchronized (lock) {
                                bestSnapshot = gBWPos.clone();
                            }

                            for (int i = 0; i < nTasks; i++) {
                                if (p < 0.5) {
                                    if (Math.abs(A) >= 1.0) {
                                        // Chaotic exploration with random whale
                                        int randW = localRng.nextInt(numWhales);
                                        double randVm = positions[randW][i];
                                        double Drand = Math.abs(C * randVm - cWPos[i]);
                                        nWPos[i] = randVm - (A * Drand);
                                    } else {
                                        // Encircling towards elite best
                                        double D = Math.abs(C * bestSnapshot[i] - cWPos[i]);
                                        nWPos[i] = bestSnapshot[i] - (A * D);
                                    }
                                } else {
                                    // Adaptive logarithmic bubble net attack
                                    double D_prime = Math.abs(bestSnapshot[i] - cWPos[i]);
                                    nWPos[i] = D_prime * Math.exp(b * l) * Math.cos(2.0 * Math.PI * l) + bestSnapshot[i];
                                }

                                if (nWPos[i] < 0 || nWPos[i] > nVms - 1) {
                                    nWPos[i] = localRng.nextDouble() * (nVms - 1);
                                }
                            }

                            System.arraycopy(nWPos, 0, positions[whaleIdx], 0, nTasks);

                            int[] dMap = PEWOA.discretize(nWPos, nVms);
                            double bWMk = PEWOA.calculateMakespan(dMap, cldLengths, vmMips);

                            synchronized (lock) {
                                if (bWMk < bWsMap[whaleIdx]) {
                                    bWsMap[whaleIdx] = bWMk;
                                    if (bWMk < gBValue[0]) {
                                        gBValue[0] = bWMk;
                                        System.arraycopy(nWPos, 0, gBWPos, 0, nTasks);
                                    }
                                }
                            }
                            return null;
                        }
                    });
                }

                executor.invokeAll(tasks);

                // Periodic Critical-Path Elite Local Search (CP-ELS) on global best
                if (itr % 10 == 0 || itr == maxItr) {
                    double improved = criticalPathEliteLocalSearch(gBWPos, cldLengths, vmMips, 10);
                    if (improved < gBValue[0]) {
                        gBValue[0] = improved;
                    }
                }

                convergenceCurve.add(gBValue[0]);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            executor.shutdown();
        }

        // Final Polishing with CP-ELS
        criticalPathEliteLocalSearch(gBWPos, cldLengths, vmMips, 20);
        int[] bestMapping = PEWOA.discretize(gBWPos, nVms);
        long execTime = System.currentTimeMillis() - startTime;
        latestMetrics = EvaluationMetrics.calculate(bestMapping, cloudlets, vms, execTime);
        return bestMapping;
    }

    /**
     * Critical-Path Elite Local Search (CP-ELS):
     * Directly discovers the bottleneck VM dictating makespan, and selectively
     * transfers tasks to underloaded VMs if and only if global makespan is reduced.
     */
    public static double criticalPathEliteLocalSearch(double[] continuousPos, long[] cldLengths, double[] vmMips, int maxSwaps) {
        int nVms = vmMips.length;
        int nTasks = cldLengths.length;
        int[] mapping = PEWOA.discretize(continuousPos, nVms);

        double[] ct = new double[nVms];
        for (int i = 0; i < nTasks; i++) {
            ct[mapping[i]] += cldLengths[i] / vmMips[mapping[i]];
        }

        double curMakespan = 0.0;
        for (double v : ct) if (v > curMakespan) curMakespan = v;

        for (int iter = 0; iter < maxSwaps; iter++) {
            int maxVm = 0;
            int minVm = 0;
            for (int j = 1; j < nVms; j++) {
                if (ct[j] > ct[maxVm]) maxVm = j;
                if (ct[j] < ct[minVm]) minVm = j;
            }

            if (maxVm == minVm || (ct[maxVm] - ct[minVm]) < 1e-4) break;

            int bestTask = -1;
            double bestNewMakespan = curMakespan;

            for (int i = 0; i < nTasks; i++) {
                if (mapping[i] == maxVm) {
                    double deltaMax = cldLengths[i] / vmMips[maxVm];
                    double deltaMin = cldLengths[i] / vmMips[minVm];

                    double testCtMax = ct[maxVm] - deltaMax;
                    double testCtMin = ct[minVm] + deltaMin;

                    double candidateMax = Math.max(testCtMax, testCtMin);
                    for (int j = 0; j < nVms; j++) {
                        if (j != maxVm && j != minVm && ct[j] > candidateMax) {
                            candidateMax = ct[j];
                        }
                    }

                    if (candidateMax < bestNewMakespan) {
                        bestNewMakespan = candidateMax;
                        bestTask = i;
                    }
                }
            }

            if (bestTask != -1 && bestNewMakespan < curMakespan) {
                ct[maxVm] -= cldLengths[bestTask] / vmMips[maxVm];
                ct[minVm] += cldLengths[bestTask] / vmMips[minVm];
                mapping[bestTask] = minVm;
                continuousPos[bestTask] = minVm;
                curMakespan = bestNewMakespan;
            } else {
                break;
            }
        }

        return curMakespan;
    }
}
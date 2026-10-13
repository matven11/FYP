package org.cloudbus.cloudsim.scheduling.algorithms;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.scheduling.model.EvaluationMetrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Standard Whale Optimization Algorithm (Mirjalili 2016 baseline).
 * Parameter a linearly decreases from 2.0 to 0.0, b = 1.0.
 */
public class StandardWOA implements TaskScheduler {

    private int numWhales = 60;
    private int maxItr = 220;
    private EvaluationMetrics latestMetrics;
    private List<Double> convergenceCurve = new ArrayList<Double>();

    public StandardWOA() {}

    public StandardWOA(int numWhales, int maxItr) {
        this.numWhales = numWhales;
        this.maxItr = maxItr;
    }

    @Override
    public String getName() {
        return "Standard WOA";
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
        double[][] positions = new double[numWhales][nTasks];
        double[] fitness = new double[numWhales];

        double bestFit = Double.MAX_VALUE;
        double[] bestPos = new double[nTasks];

        for (int w = 0; w < numWhales; w++) {
            for (int i = 0; i < nTasks; i++) {
                positions[w][i] = rng.nextDouble() * (nVms - 1);
            }
            int[] dMap = PEWOA.discretize(positions[w], nVms);
            fitness[w] = PEWOA.calculateMakespan(dMap, cldLengths, vmMips);
            if (fitness[w] < bestFit) {
                bestFit = fitness[w];
                System.arraycopy(positions[w], 0, bestPos, 0, nTasks);
            }
        }

        convergenceCurve.clear();
        convergenceCurve.add(bestFit);

        for (int itr = 1; itr <= maxItr; itr++) {
            // Standard WOA: a decreases from 2.0 to 0.0
            double a = 2.0 - (itr * 2.0 / maxItr);

            for (int w = 0; w < numWhales; w++) {
                double r1 = rng.nextDouble();
                double r2 = rng.nextDouble();
                double p = rng.nextDouble();

                double A = 2.0 * a * r1 - a;
                double C = 2.0 * r2;
                double l = (rng.nextDouble() * 2.0) - 1.0;
                double b = 1.0;

                for (int i = 0; i < nTasks; i++) {
                    if (p < 0.5) {
                        if (Math.abs(A) >= 1.0) {
                            int randW = rng.nextInt(numWhales);
                            double D_rand = Math.abs(C * positions[randW][i] - positions[w][i]);
                            positions[w][i] = positions[randW][i] - A * D_rand;
                        } else {
                            double D = Math.abs(C * bestPos[i] - positions[w][i]);
                            positions[w][i] = bestPos[i] - A * D;
                        }
                    } else {
                        double D_prime = Math.abs(bestPos[i] - positions[w][i]);
                        positions[w][i] = D_prime * Math.exp(b * l) * Math.cos(2.0 * Math.PI * l) + bestPos[i];
                    }

                    if (positions[w][i] < 0 || positions[w][i] > nVms - 1) {
                        positions[w][i] = rng.nextDouble() * (nVms - 1);
                    }
                }

                int[] dMap = PEWOA.discretize(positions[w], nVms);
                double curFit = PEWOA.calculateMakespan(dMap, cldLengths, vmMips);
                if (curFit < fitness[w]) {
                    fitness[w] = curFit;
                    if (curFit < bestFit) {
                        bestFit = curFit;
                        System.arraycopy(positions[w], 0, bestPos, 0, nTasks);
                    }
                }
            }
            convergenceCurve.add(bestFit);
        }

        int[] bestMapping = PEWOA.discretize(bestPos, nVms);
        long execTime = System.currentTimeMillis() - startTime;
        latestMetrics = EvaluationMetrics.calculate(bestMapping, cloudlets, vms, execTime);
        return bestMapping;
    }
}
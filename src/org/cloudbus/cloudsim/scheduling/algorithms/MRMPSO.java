package org.cloudbus.cloudsim.scheduling.algorithms;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.scheduling.model.EvaluationMetrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * MRMPSO (Multi-core Random Matrix Particle Swarm Optimization).
 * Reference [18] from IEEE Access 2024 paper (Tang et al., 2021).
 */
public class MRMPSO implements TaskScheduler {

    private int numParticles = 32;
    private int maxItr = 220;
    private EvaluationMetrics latestMetrics;
    private List<Double> convergenceCurve = new ArrayList<Double>();

    public MRMPSO() {}

    public MRMPSO(int numParticles, int maxItr) {
        this.numParticles = numParticles;
        this.maxItr = maxItr;
    }

    @Override
    public String getName() {
        return "MRMPSO (PSO)";
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
        double wInertia = 0.7;
        double c1 = 1.49;
        double c2 = 1.49;
        double vMax = (nVms - 1) * 0.2;

        double[][] pos = new double[numParticles][nTasks];
        double[][] vel = new double[numParticles][nTasks];
        double[][] pBestPos = new double[numParticles][nTasks];
        double[] pBestFit = new double[numParticles];

        double gBestFit = Double.MAX_VALUE;
        double[] gBestPos = new double[nTasks];

        for (int p = 0; p < numParticles; p++) {
            for (int i = 0; i < nTasks; i++) {
                pos[p][i] = rng.nextDouble() * (nVms - 1);
                vel[p][i] = (rng.nextDouble() * 2.0 - 1.0) * vMax;
                pBestPos[p][i] = pos[p][i];
            }
            int[] dMap = PEWOA.discretize(pos[p], nVms);
            pBestFit[p] = PEWOA.calculateMakespan(dMap, cldLengths, vmMips);
            if (pBestFit[p] < gBestFit) {
                gBestFit = pBestFit[p];
                System.arraycopy(pos[p], 0, gBestPos, 0, nTasks);
            }
        }

        convergenceCurve.clear();
        convergenceCurve.add(gBestFit);

        for (int itr = 1; itr <= maxItr; itr++) {
            for (int p = 0; p < numParticles; p++) {
                for (int i = 0; i < nTasks; i++) {
                    double r1 = rng.nextDouble();
                    double r2 = rng.nextDouble();
                    vel[p][i] = (wInertia * vel[p][i])
                            + (c1 * r1 * (pBestPos[p][i] - pos[p][i]))
                            + (c2 * r2 * (gBestPos[i] - pos[p][i]));

                    if (vel[p][i] > vMax) vel[p][i] = vMax;
                    if (vel[p][i] < -vMax) vel[p][i] = -vMax;

                    pos[p][i] += vel[p][i];
                    if (pos[p][i] < 0) pos[p][i] = 0;
                    if (pos[p][i] > nVms - 1) pos[p][i] = nVms - 1;
                }

                int[] dMap = PEWOA.discretize(pos[p], nVms);
                double curFit = PEWOA.calculateMakespan(dMap, cldLengths, vmMips);
                if (curFit < pBestFit[p]) {
                    pBestFit[p] = curFit;
                    System.arraycopy(pos[p], 0, pBestPos, 0, nTasks);
                    if (curFit < gBestFit) {
                        gBestFit = curFit;
                        System.arraycopy(pos[p], 0, gBestPos, 0, nTasks);
                    }
                }
            }
            convergenceCurve.add(gBestFit);
        }

        int[] bestMapping = PEWOA.discretize(gBestPos, nVms);
        long execTime = System.currentTimeMillis() - startTime;
        latestMetrics = EvaluationMetrics.calculate(bestMapping, cloudlets, vms, execTime);
        return bestMapping;
    }
}
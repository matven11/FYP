package org.cloudbus.cloudsim.scheduling.model;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.UtilizationModel;
import org.cloudbus.cloudsim.UtilizationModelFull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Generates CloudSim Cloudlet workloads for GoCJ and HCSP benchmarks
 * as described in Section V-A and V-B of the base paper.
 */
public class WorkloadGenerator {

    /**
     * Generates GoCJ independent cloudlets.
     * Category distribution:
     * - Small: 20% (15,000 - 55,000 MI)
     * - Medium: 40% (59,000 - 99,000 MI)
     * - Large: 30% (101,000 - 135,000 MI)
     * - Extra-Large: 6% (150,000 - 337,500 MI)
     * - Huge: 4% (525,000 - 900,000 MI)
     */
    public static List<Cloudlet> generateGoCJCloudlets(int brokerId, int numTasks, long seed) {
        List<Cloudlet> cloudletList = new ArrayList<Cloudlet>();
        Random rng = new Random(seed);

        int smallCount = (int) Math.round(numTasks * 0.20);
        int largeCount = (int) Math.round(numTasks * 0.30);
        int xlCount = (int) Math.round(numTasks * 0.06);
        int hugeCount = (int) Math.round(numTasks * 0.04);
        int mediumCount = numTasks - (smallCount + largeCount + xlCount + hugeCount);

        int pesNumber = 1;
        long fileSize = 300;
        long outputSize = 300;
        UtilizationModel model = new UtilizationModelFull();

        List<Long> lengths = new ArrayList<Long>();

        // Small
        for (int i = 0; i < smallCount; i++) {
            long len = 15000 + (long) (rng.nextDouble() * (55000 - 15000));
            lengths.add(len);
        }
        // Medium
        for (int i = 0; i < mediumCount; i++) {
            long len = 59000 + (long) (rng.nextDouble() * (99000 - 59000));
            lengths.add(len);
        }
        // Large
        for (int i = 0; i < largeCount; i++) {
            long len = 101000 + (long) (rng.nextDouble() * (135000 - 101000));
            lengths.add(len);
        }
        // Extra Large
        for (int i = 0; i < xlCount; i++) {
            long len = 150000 + (long) (rng.nextDouble() * (337500 - 150000));
            lengths.add(len);
        }
        // Huge
        for (int i = 0; i < hugeCount; i++) {
            long len = 525000 + (long) (rng.nextDouble() * (900000 - 525000));
            lengths.add(len);
        }

        // Shuffle so workload is realistically distributed
        Collections.shuffle(lengths, rng);

        for (int id = 0; id < lengths.size(); id++) {
            Cloudlet c = new Cloudlet(id, lengths.get(id), pesNumber, fileSize, outputSize, model, model, model);
            c.setUserId(brokerId);
            cloudletList.add(c);
        }

        return cloudletList;
    }

    /**
     * Generates HCSP Cloudlets (1024 tasks) and returns VM MIPS array (32 VMs)
     * matching Table 4.
     */
    public static class HCSPData {
        public List<Cloudlet> cloudlets;
        public double[] vmMips;

        public HCSPData(List<Cloudlet> cloudlets, double[] vmMips) {
            this.cloudlets = cloudlets;
            this.vmMips = vmMips;
        }
    }

    public static HCSPData generateHCSPWorkload(int brokerId, String instanceName, long seed) {
        Random rng = new Random(seed);
        int numTasks = 1024;
        int numVms = 32;

        double taskLow, taskHigh;
        double vmLow, vmHigh;

        if (instanceName.equals("c_lohi")) {
            taskLow = 23.0; taskHigh = 98121.0;
            vmLow = 37.0; vmHigh = 999.0;
        } else if (instanceName.equals("i_lohi")) {
            taskLow = 23.0; taskHigh = 98121.0;
            vmLow = 10.0; vmHigh = 952.0;
        } else if (instanceName.equals("i_hilo")) {
            taskLow = 2.0; taskHigh = 29843.0;
            vmLow = 1.0; vmHigh = 10.0;
        } else { // c_hilo
            taskLow = 2.0; taskHigh = 29843.0;
            vmLow = 1.0; vmHigh = 10.0;
        }

        double[] vmMips = new double[numVms];
        for (int j = 0; j < numVms; j++) {
            vmMips[j] = vmLow + (rng.nextDouble() * (vmHigh - vmLow));
        }

        if (instanceName.startsWith("c_")) {
            // Sort descending for consistency
            java.util.Arrays.sort(vmMips);
            for (int i = 0; i < numVms / 2; i++) {
                double temp = vmMips[i];
                vmMips[i] = vmMips[numVms - 1 - i];
                vmMips[numVms - 1 - i] = temp;
            }
        }

        List<Cloudlet> cloudlets = new ArrayList<Cloudlet>();
        UtilizationModel model = new UtilizationModelFull();

        for (int i = 0; i < numTasks; i++) {
            long len = (long) (taskLow + (rng.nextDouble() * (taskHigh - taskLow)));
            Cloudlet c = new Cloudlet(i, len, 1, 300, 300, model, model, model);
            c.setUserId(brokerId);
            cloudlets.add(c);
        }

        return new HCSPData(cloudlets, vmMips);
    }
}
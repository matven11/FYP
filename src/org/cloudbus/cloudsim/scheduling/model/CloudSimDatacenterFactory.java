package org.cloudbus.cloudsim.scheduling.model;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Factory class to instantiate CloudSim Datacenter and Virtual Machines
 * according to Table 5 of IEEE Access 2024 paper.
 * Datacenter: 1, Hosts: 3 (Quad-Core, Hexa-Core, Octa-Core), PE MIPS: 12000.
 * Heterogeneous VMs: 10 VMs with MIPS [400, 440, 600, 800, 900, 1200, 2000, 4000, 10000, 12000].
 */
public class CloudSimDatacenterFactory {

    public static final double[] GOCJ_VM_MIPS = {
        400.0, 440.0, 600.0, 800.0, 900.0, 1200.0, 2000.0, 4000.0, 10000.0, 12000.0
    };

    /**
     * Creates the Datacenter with 3 heterogeneous physical hosts.
     */
    public static Datacenter createDatacenter(String name) {
        List<Host> hostList = new ArrayList<Host>();

        // Host 0: Quad-Core (4 PEs, 12000 MIPS total PE capacity)
        List<Pe> peList1 = new ArrayList<Pe>();
        for (int i = 0; i < 4; i++) {
            peList1.add(new Pe(i, new PeProvisionerSimple(12000)));
        }
        int hostId1 = 0;
        int ram1 = 16384; // 16 GB
        long storage1 = 1000000; // 1 TB
        int bw1 = 10000;
        hostList.add(new Host(
            hostId1,
            new RamProvisionerSimple(ram1),
            new BwProvisionerSimple(bw1),
            storage1,
            peList1,
            new VmSchedulerTimeShared(peList1)
        ));

        // Host 1: Hexa-Core (6 PEs)
        List<Pe> peList2 = new ArrayList<Pe>();
        for (int i = 0; i < 6; i++) {
            peList2.add(new Pe(i, new PeProvisionerSimple(12000)));
        }
        int hostId2 = 1;
        int ram2 = 32768; // 32 GB
        long storage2 = 1000000;
        int bw2 = 10000;
        hostList.add(new Host(
            hostId2,
            new RamProvisionerSimple(ram2),
            new BwProvisionerSimple(bw2),
            storage2,
            peList2,
            new VmSchedulerTimeShared(peList2)
        ));

        // Host 2: Octa-Core (8 PEs)
        List<Pe> peList3 = new ArrayList<Pe>();
        for (int i = 0; i < 8; i++) {
            peList3.add(new Pe(i, new PeProvisionerSimple(12000)));
        }
        int hostId3 = 2;
        int ram3 = 65536; // 64 GB
        long storage3 = 1000000;
        int bw3 = 10000;
        hostList.add(new Host(
            hostId3,
            new RamProvisionerSimple(ram3),
            new BwProvisionerSimple(bw3),
            storage3,
            peList3,
            new VmSchedulerTimeShared(peList3)
        ));

        // Datacenter Characteristics
        String arch = "x86";
        String os = "Linux";
        String vmm = "Xen";
        double time_zone = 10.0;
        double cost = 3.0;
        double costPerMem = 0.05;
        double costPerStorage = 0.001;
        double costPerBw = 0.0;

        LinkedList<Storage> storageList = new LinkedList<Storage>();
        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
            arch, os, vmm, hostList, time_zone, cost, costPerMem, costPerStorage, costPerBw
        );

        Datacenter datacenter = null;
        try {
            datacenter = new Datacenter(name, characteristics, new VmAllocationPolicySimple(hostList), storageList, 0);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return datacenter;
    }

    /**
     * Creates the 10 Heterogeneous VMs for GoCJ workload (Table 5).
     */
    public static List<Vm> createGoCJVms(int brokerId) {
        List<Vm> vms = new ArrayList<Vm>();
        long size = 10000; // 10 GB
        int ram = 2048; // 2 GB
        long bw = 1000;
        int pesNumber = 1;
        String vmm = "Xen";

        for (int i = 0; i < GOCJ_VM_MIPS.length; i++) {
            double mips = GOCJ_VM_MIPS[i];
            // Non-preemptive task scheduling on VM: CloudletSchedulerSpaceShared
            vms.add(new Vm(
                i, brokerId, mips, pesNumber, ram, bw, size, vmm,
                new CloudletSchedulerSpaceShared()
            ));
        }

        return vms;
    }

    /**
     * Creates 32 VMs for HCSP workload (Table 4 & 5).
     */
    public static List<Vm> createHCSPVms(int brokerId, double[] vmMipsArray) {
        List<Vm> vms = new ArrayList<Vm>();
        long size = 10000;
        int ram = 2048;
        long bw = 1000;
        int pesNumber = 1;
        String vmm = "Xen";

        for (int i = 0; i < vmMipsArray.length; i++) {
            vms.add(new Vm(
                i, brokerId, vmMipsArray[i], pesNumber, ram, bw, size, vmm,
                new CloudletSchedulerSpaceShared()
            ));
        }

        return vms;
    }
}
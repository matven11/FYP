@echo off
echo =========================================================================
echo    Running CloudSim 3.0.3 Task Scheduling Simulation Suite...
echo =========================================================================
java -cp "bin;lib/cloudsim-3.0.3.jar;lib/cloudsim-examples-3.0.3.jar" org.cloudbus.cloudsim.scheduling.runner.Main
pause

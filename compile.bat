@echo off
echo =========================================================================
echo    Compiling CloudSim 3.0.3 Task Scheduling Project with Java 21...
echo =========================================================================
if not exist bin mkdir bin
javac -cp "lib/cloudsim-3.0.3.jar;lib/cloudsim-examples-3.0.3.jar" -d bin src/org/cloudbus/cloudsim/scheduling/model/*.java src/org/cloudbus/cloudsim/scheduling/algorithms/*.java src/org/cloudbus/cloudsim/scheduling/runner/*.java
if %ERRORLEVEL% EQU 0 (
    echo [SUCCESS] Compilation completed with 0 errors!
) else (
    echo [ERROR] Compilation failed!
)

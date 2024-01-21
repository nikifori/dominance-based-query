@echo off

SETLOCAL ENABLEDELAYEDEXPANSION

REM Define the tasks, distributions, and dimensions
SET tasks=Task2 Task3 Task1
SET distributions=correlated uniform normal
SET dimensions=4 3 2

REM Loop over each distribution
FOR %%D IN (%distributions%) DO (
    REM Loop over each dimension
    FOR %%I IN (%dimensions%) DO (
        REM Loop over each task
        FOR %%T IN (%tasks%) DO (
            REM Execute the command with fixed sample number and varying dimensions
            sbt "run %%T ../data/%%D_1000000_%%I.csv 10 4 stable_size_1M.csv"
            
            REM Echo a message indicating completion of this iteration
            echo Completed: Task=%%T, Distribution=%%D, Dimension=%%I

            REM Wait for 2 minutes (120 seconds)
            timeout /t 240 /nobreak
        )
    )
)

ENDLOCAL

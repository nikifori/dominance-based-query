@echo off

SETLOCAL ENABLEDELAYEDEXPANSION

REM Define the tasks, distributions, and dimensions
SET tasks=Task2 Task3 Task1
SET distributions=anticorrelated correlated uniform normal
SET dimensions=6 5 4 3 2

REM Loop over each distribution
FOR %%D IN (%distributions%) DO (
    REM Loop over each dimension
    FOR %%I IN (%dimensions%) DO (
        REM Loop over each task
        FOR %%T IN (%tasks%) DO (
            REM Execute the command with fixed sample number and varying dimensions
            sbt "run %%T ../data/%%D_100000_%%I.csv 10 4 stable_size_100K.csv"
            
            REM Echo a message indicating completion of this iteration
            echo Completed: Task=%%T, Distribution=%%D, Dimension=%%I
			
        )
    )
)

ENDLOCAL
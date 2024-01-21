@echo off

SETLOCAL ENABLEDELAYEDEXPANSION

REM Define the tasks, distributions, and sample numbers
SET tasks=Task1 Task2 Task3
SET distributions=anticorrelated correlated uniform normal
SET sample_nums=25000 250000 500000 1000000 2000000 10000000

REM Loop over each sample number
FOR %%S IN (%sample_nums%) DO (
    REM Loop over each distribution
    FOR %%D IN (%distributions%) DO (
        REM Loop over each task
        FOR %%T IN (%tasks%) DO (
            REM Execute the second command only
            sbt "run %%T ../data/%%D_%%S_2.csv 10 2 two_dimensions_cache_fork_enabled_2Cores.csv"

            REM Echo a message indicating completion of this iteration
            echo Completed: Task=%%T, Distribution=%%D, SampleNum=%%S
        )
    )
)

ENDLOCAL

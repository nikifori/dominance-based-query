@echo off
SETLOCAL ENABLEDELAYEDEXPANSION

REM Define the arrays
SET x_values[0]=correlated
SET x_values[1]=uniform
SET x_values[2]=normal
SET x_values[3]=anticorrelated

SET y_values[0]=100000

SET z_values[0]=2
SET z_values[1]=3
SET z_values[2]=4
SET z_values[3]=5
SET z_values[4]=6


REM Iterate over each combination and run the command
FOR %%x IN (0 1 2 3) DO (
    FOR %%y IN (0) DO (
        FOR %%z IN (0 1 2 3 4) DO (
            python data_generator.py !x_values[%%x]! !y_values[%%y]! !z_values[%%z]! 42 ./../data
        )
    )
)

ENDLOCAL
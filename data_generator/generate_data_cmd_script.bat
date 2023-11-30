@echo off
SETLOCAL ENABLEDELAYEDEXPANSION

REM Define the arrays
SET x_values[0]=correlated
SET x_values[1]=uniform
SET x_values[2]=normal
SET x_values[3]=anticorrelated

SET y_values[0]=500
SET y_values[1]=2500
SET y_values[2]=25000
SET y_values[3]=250000
SET y_values[4]=500000
SET y_values[5]=1000000
SET y_values[6]=1500000
SET y_values[7]=2000000
SET y_values[8]=2500000
SET y_values[9]=3000000

SET z_values[0]=2
SET z_values[1]=3
SET z_values[2]=4
SET z_values[3]=5
SET z_values[4]=6
SET z_values[5]=7
SET z_values[6]=8
SET z_values[7]=9
SET z_values[8]=10

REM Iterate over each combination and run the command
FOR %%x IN (0 1 2 3) DO (
    FOR %%y IN (0 1 2 3 4 5 6 7 8 9) DO (
        FOR %%z IN (0 1 2 3 4 5 6 7 8) DO (
            python data_generator.py !x_values[%%x]! !y_values[%%y]! !z_values[%%z]! 42 ./../data
        )
    )
)

ENDLOCAL
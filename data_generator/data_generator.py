"""
@File    :   data_generator.py
@Time    :   11/2023
@Version :   -
"""
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
from pathlib import Path
import secrets
import argparse


def correlated_gen(
    num_points: int = 1000,
    d: int = 2,
    mean: int = 0.5,
    covariance_factor: float = 0.8
):
    mean_vector = np.full(shape=(d), fill_value=mean)
    


def uniform_gen(
    num_points: int = 1000,
    d: int = 2,
):
    pass


def normal_gen(
    num_points: int = 1000, 
    d: int = 2, 
    std: float = 0.5, 
    mean: float = 0.5,
    output_folder: str = './'
):  
    random_int = secrets.randbelow(2**32)
    output_file = Path(output_folder) / f'normal_distribution_{random_int}.csv'
    data = np.random.normal(loc=mean, scale=std, size=(num_points, d))
    pd.DataFrame(data).to_csv(output_file, index=False, header=False)



def anticorrelated_gen():
    pass


def main():
    normal_gen()


if __name__ == "__main__":
    main()

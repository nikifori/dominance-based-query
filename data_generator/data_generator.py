"""
@File    :   data_generator.py
@Time    :   11/2023
@Version :   -
"""
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
from mpl_toolkits.mplot3d import Axes3D
from pathlib import Path
import argparse
from sklearn.preprocessing import MinMaxScaler


def correlated_gen(
    num_points: int = 1000,
    d: int = 2,
    mean: int = 0.5,
    covariance_factor: float = 0.8,
    anti: bool = False
):  
    mean_vector = np.full(shape=(d), fill_value=mean)
    covariance_matrix = np.full((d, d), covariance_factor, dtype=float)
    np.fill_diagonal(covariance_matrix, 1)
    # print(covariance_matrix)

    if not anti:
        scaler = MinMaxScaler()
        return scaler.fit_transform(np.random.multivariate_normal(mean=mean_vector, cov=covariance_matrix, size=num_points))
    else:
        if d==2:
            corr_matrix = np.full(shape=(d,d), fill_value=-0.9)
        else:
            corr_matrix = np.full(shape=(d,d), fill_value=-(1/(d-1)) + 0.001*(1/(d-1)))

        for i in range(corr_matrix.shape[0]):
            for j in range(i):
                corr_matrix[i, j] = corr_matrix[j, i]


        np.fill_diagonal(corr_matrix, 1)
        std_devs = np.ones(d)
        std_dev_matrix = np.diag(std_devs)
        cov_matrix = std_dev_matrix.dot(corr_matrix).dot(std_dev_matrix)
        # print(cov_matrix)
        anti_corr_data = np.random.multivariate_normal(mean=mean_vector, cov=cov_matrix, size=num_points)
        scaler = MinMaxScaler()
        return scaler.fit_transform(anti_corr_data)
        

def uniform_gen(
    num_points: int = 1000,
    d: int = 2,
):
    return np.random.uniform(low=0.0, high=1.0, size=(num_points, d))


def normal_gen(
    num_points: int = 1000, 
    d: int = 2, 
    std: float = 1.0, 
    mean: float = 0.5
):  
    scaler = MinMaxScaler()
    return scaler.fit_transform(np.random.normal(loc=mean, scale=std, size=(num_points, d)))


def plot_2d(csv_path: str = None):
    data = pd.read_csv(Path(csv_path))
    plt.scatter(data.iloc[:, 0], data.iloc[:, 1])
    plt.xlabel("X Dimension")
    plt.ylabel("Y Dimension")
    plt.title("Scatter Plot of 2D Points")
    plt.grid(True)
    plt.show()


def plot_3d(csv_path: str = None):
    data = pd.read_csv(Path(csv_path))
    fig = plt.figure()
    ax = fig.add_subplot(111, projection='3d')
    ax.scatter(data.iloc[:, 0], data.iloc[:, 1], data.iloc[:, 2])

    ax.set_xlabel("X Dimension")
    ax.set_ylabel("Y Dimension")
    ax.set_zlabel("Z Dimension")
    ax.set_title("3D Scatter Plot of Points")
    plt.show()



def main(args):
    # initialize np.random.seed
    np.random.seed(args.np_random_seed)

    # check if distribution type is valid
    distribution_types = ['correlated', 'uniform', 'normal', 'anticorrelated']
    if args.distribution_type not in distribution_types:
        raise Exception(f'Distribution type must be one of: {distribution_types}')
    
    # create output file name
    output_folder = Path(args.output_folder)
    # if output_foler does not exist, create it
    if not output_folder.exists():
        output_folder.mkdir(parents=True, exist_ok=True)
    
    output_filename = output_folder / f'{args.distribution_type}_{args.num_points}_{args.num_dims}.csv'

    # generate the data
    if args.distribution_type == 'correlated':
        data = correlated_gen(num_points=args.num_points, d=args.num_dims, anti=False)
    elif args.distribution_type == 'uniform':
        data = uniform_gen(num_points=args.num_points, d=args.num_dims)
    elif args.distribution_type == 'normal':
        data = normal_gen(num_points=args.num_points, d=args.num_dims)
    else:
        data = correlated_gen(num_points=args.num_points, d=args.num_dims, anti=True)
    
    
    pd.DataFrame(data).to_csv(output_filename, index=False, header=False)
    # plot_2d(output_filename)
    # plot_3d(output_filename)
    print(f'[INFO] Just finished file: {output_filename}')


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Choose distribution to generate and its parameters")
    # required positional arguments
    parser.add_argument("distribution_type", type=str, help='valid values = correlated, uniform, normal, anticorrelated')
    parser.add_argument("num_points", type=int)
    parser.add_argument("num_dims", type=int)
    parser.add_argument("np_random_seed", type=int)
    parser.add_argument('output_folder', type=str)

    # parse arguments
    args = parser.parse_args()

    main(args)

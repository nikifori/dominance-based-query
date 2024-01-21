'''
@File    :   results_validation.py
@Time    :   11/2023
@Author  :   nikifori
@Version :   -
'''
import pandas as pd
from tqdm import tqdm
from pathlib import Path
import itertools

# Set pandas display options
pd.set_option('display.max_rows', None)
pd.set_option('display.max_columns', None)
pd.set_option('display.width', None)

def dominates(point, other):
    return all(point <= other) and any(point < other)

def get_skyline(df):
    print("[INFO] Calculating skyline...")
    skyline = []
    for i in tqdm(range(len(df))):
        dominated = False
        for j in range(len(df)):
            if i != j and dominates(df.iloc[j], df.iloc[i]):
                dominated = True
                break
        if not dominated:
            skyline.append(df.iloc[i])
    return pd.DataFrame(skyline)

def calculate_dominance_scores(df1, df2):
    print("[INFO] Calculating dominance...")
    scores = [0] * len(df1)
    for i in tqdm(range(len(df1))):
        for j in range(len(df2)):
            if not df1.iloc[i].equals(df2.iloc[j]) and dominates(df1.iloc[i], df2.iloc[j]):
                scores[i] += 1
    return scores

def top_k_dominant(df1, df2, k):
    scores = calculate_dominance_scores(df1, df2)
    temp_df = df1.copy(deep=True)
    temp_df['dominance_score'] = scores
    return temp_df.nlargest(k, 'dominance_score')

def top_k_skyline_dominant(df, skyline, k):
    # skyline = get_skyline(df)
    return top_k_dominant(skyline, df, k)

def main():
    k = 10
    params= {'distribution': ['anticorrelated', 'correlated', 'normal', 'uniform'],
             'num_samples': [500, 2500],
             'num_dims': [2, 3, 4, 5]}

    combinations = itertools.product(params['distribution'],
                                     params['num_samples'],
                                     params['num_dims'])

    for distr, samples, dims in combinations:
        print(f'[INFO] Current file: {distr}--{samples}--{dims}')
        file_path = Path(r'.\data') / f'{distr}_{samples}_{dims}.csv'
        df = pd.read_csv(file_path, header=None)
        skyline_df = get_skyline(df)
        top_k_dominant_df = top_k_dominant(df, df, k=k)
        top_k_skyline_dominant_df = top_k_skyline_dominant(df, skyline_df, k=k)
        result_string = f"Skyline Set:\n{skyline_df}\nSkyline Set Samples Number {skyline_df.shape[0]}\n\nTop {k} Dominant:\n{top_k_dominant_df}\n\nTop {k} Skyline Dominant:\n{top_k_skyline_dominant_df}"

        # Save to a text file
        file_path = fr'.\brute_force_validation\new_results_for_{samples}_samples\{distr}_{samples}_{dims}.txt'
        with open(file_path, 'w') as file:
            file.write(result_string)
    
    # testing
    # file_path = Path(r'.\correlated_2500_2.csv')
    # df = pd.read_csv(file_path, header=None)
    # skyline_df = get_skyline(df)
    # top_k_dominant_df = top_k_dominant(df, k=3)
    # top_k_skyline_dominant_df = top_k_skyline_dominant(df, k=2)
    # result_string = f"Skyline Set:\n{skyline_df}\n\nTop {k} Dominant:\n{top_k_dominant_df}\n\nTop {k} Skyline Dominant:\n{top_k_skyline_dominant_df}"

    # # Save to a text file
    # file_path = r'.\test.txt'
    # with open(file_path, 'w') as file:
    #     file.write(result_string)

    # print(1)


if __name__ == '__main__':
    main()
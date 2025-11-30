from functools import reduce
import pandas as pd
import plotly.graph_objects as go
from plotly.subplots import make_subplots
import os

def satisfactionViolins(df):
    fig = make_subplots(
        rows=1, 
        cols=2, 
        subplot_titles=("MEC Satisfaction Distribution", "RPL Satisfaction Distribution")
        )

    fig.add_trace(
        go.Violin(
            y=df["mecsattimestep"],
            name="MEC Satisfaction",
            box_visible=True,
            meanline_visible=True,
            line_color="black"
        ),
        row=1,
        col=1
    )

    fig.add_trace(
        go.Violin(
            y=df["rplsattimestep"],
            name="RPL Satisfaction",
            box_visible=True,
            meanline_visible=True,
            line_color="black"
        ),
        row=1,
        col=2,
    )

    fig.update_layout(yaxis_zeroline=False)
    return fig

def satisfactionPlots(df):
    fig = make_subplots(rows=2, cols=1)

    fig.add_trace(
        go.Scatter(
            x=df["timestep"],
            y=df["mecsattimestep"],
            mode="lines",
            name="MECS Satisfaction",
        ),
        row=1,
        col=1,
    )

    fig.add_trace(
        go.Scatter(
            x=df["timestep"],
            y=df["rplsattimestep"],
            mode="lines",
            name="RPL Satisfaction",
        ),
        row=2,
        col=1,
    )

    fig.add_shape(
        type="line",
        x0=df["timestep"].min(),
        x1=df["timestep"].max(),
        y0=20,           # horizontal line value
        y1=20,
        xref="x1",        # subplot 1
        yref="y1",
        line=dict(color="Red"),
    )

    fig.add_shape(
        type="line",
        x0=df["timestep"].min(),
        x1=df["timestep"].max(),
        y0=0.2,
        y1=0.2,
        xref="x2",        # subplot 2
        yref="y2",
        line=dict(color="Red"),
    )

    fig.update_yaxes(range=[0, df["mecsattimestep"].max() + 20], row=1, col=1)
    fig.update_yaxes(range=[0, df["rplsattimestep"].max() + 0.2], row=2, col=1)

    return fig

def surpriseChart(df):
    fig = go.Figure(
        data=go.Scatter(
            x=df["timestep"],
            y=df["surprisebf"],
            mode="lines",
            name="Mean Bayes Factor Surprise",
        )
    )

    fig.add_trace(go.Scatter(
        x=df["timestep"],
        y=df["surprisecc"],
        mode="lines",
        name="Mean Confidence-Corrected Surprise",
    ))

    fig.add_trace(go.Scatter(
        x=df["timestep"],
        y=df["meanmis"],
        mode="lines",
        name="Mean MIS",
    ))

    fig.update_layout(
        title="Mean Surprise Over Time",
        xaxis_title="Timestep",
        yaxis_title="Mean Bayes Factor Surprise",
        legend_title="Surprise Types",
    )
    return fig

def gammaChart(df):
    fig = go.Figure(
        data=go.Scatter(
            x=df["timestep"],
            y=df["gamma"],
            mode="lines",
            name="Mean Learning Rate (Gamma)",
        )
    )

    fig.update_layout(
        title="Mean Learning Rate (Gamma) Over Time",
        xaxis_title="Timestep",
        yaxis_title="Mean Learning Rate (Gamma)",
    )
    return fig

def misChart(df):    
    fig = go.Figure(
        data=go.Scatter(
            x=df["timestep"],
            y=df["meanmis"],
            mode="lines",
            name="System Mean MIS",
        )
    )

    fig.add_trace(go.Scatter(
        x=df["timestep"],
        y=df["mis_upper"],
        mode="lines",
        name="MIS Upper Bound",
        line=dict(color='red',
                  width=1),
    ))
    
    fig.add_trace(go.Scatter(
        x=df["timestep"],
        y=df["mis_lower"],
        mode="lines",
        name="MIS Lower Bound",
        line=dict(color='red',
                  width=1),
    ))

    fig.update_layout(
        title="Mean MIS Over Time with Error Bounds",
        xaxis_title="Timestep",
        yaxis_title="Mean MIS",
    )
    return fig

def createCharts(df):
    # 1. Linechart for mean MIS over time (with error bounds)
    """mis_fig = misChart(df)
    mis_fig.show()

    # 2. Linechart for mean gamma over time
    gamma_fig = gammaChart(df)
    gamma_fig.show()

    surprises_fig = surpriseChart(df)
    surprises_fig.show()"""

    satisfaction_fig = satisfactionPlots(df = df.filter(items=["timestep", "mecsattimestep", "rplsattimestep"]))
    satisfaction_fig.show()

    satisfaction_violins_fig = satisfactionViolins(df = df.filter(items=["timestep", "mecsattimestep", "rplsattimestep"]))
    print(satisfaction_violins_fig)
    satisfaction_violins_fig.show()


def getData():
    """    
    Reads data from a file and returns it as a dataframe
    :param filename: the file to read from
    :param perMote: whether the outputs are per mote, or over whole system
    """

    dfs_2 = []
    dfs_3 = []
    folder_path = "output_dir"
    for filename in os.listdir(folder_path):
        file_path = os.path.join(folder_path, filename)
        if os.path.isfile(file_path):
            if file_path == "output_dir\\IoT.alpha" or file_path == "output_dir\\SelectedAction.txt":
                continue
            df = pd.read_csv(file_path, sep=r"\s+", header=None)
            
            file_col_name = filename.split('.')[0].lower()
            if df.shape[1] == 3:
                if file_col_name == "misbounds":
                    df.columns = ["timestep", "mis_lower", "mis_upper"]
                    dfs_2.append(df)
                    continue
                df.columns = ["moteid", "timestep", file_col_name.lower()]
                dfs_3.append(df)
                #print(df.head(10))
            else:
                df.columns = ["timestep", file_col_name]
                dfs_2.append(df)
    
    dfs_eachmote = reduce(lambda df, new_df: pd.merge(df, new_df, on=["moteid", "timestep"]), dfs_3) if dfs_3 else pd.DataFrame()

    dfs_all_surprise = dfs_eachmote.groupby("timestep").mean().reset_index()
    dfs_all_surprise = dfs_all_surprise.drop(columns=["moteid"])

    dfs_all = reduce(lambda df, new_df: pd.merge(df, new_df, on="timestep"), dfs_2) if dfs_2 else pd.DataFrame()
    
    return dfs_all.merge(dfs_all_surprise, on="timestep")

def run():
    df_all = getData()
    print(df_all.head(30))

    createCharts(df_all)

if __name__ == "__main__":
    run()
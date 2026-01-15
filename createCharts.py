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
            name="MEC Satisfaction",
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
        y=df["surprisemis"],
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
            y=df["surprisemis"],
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

def moteMetricsTimeSeries(df_mote_metrics):
    """
    Create a 2x2 subplot showing mean values over time for SNR, power, distribution, and SF.
    
    :param df_mote_metrics: DataFrame with mote metrics
    :return: Plotly figure with 2x2 subplots
    """
    # Aggregate by timestep (mean across all motes and links)
    df_agg = df_mote_metrics.groupby('timestep').agg({
        'snr': ['mean', 'median', 'min', 'max'],
        'power': ['mean', 'median', 'min', 'max'],
        'distribution': ['mean', 'median', 'min', 'max'],
        'sf': ['mean', 'median', 'min', 'max']
    }).reset_index()
    
    # Flatten column names
    df_agg.columns = ['timestep', 'snr_mean', 'snr_median', 'snr_min', 'snr_max',
                      'power_mean', 'power_median', 'power_min', 'power_max',
                      'dist_mean', 'dist_median', 'dist_min', 'dist_max',
                      'sf_mean', 'sf_median', 'sf_min', 'sf_max']
    
    fig = make_subplots(
        rows=2, cols=2,
        subplot_titles=("Mean SNR Over Time", "Mean Power Over Time", 
                       "Mean Distribution Over Time", "Mean SF Over Time"),
        vertical_spacing=0.12,
        horizontal_spacing=0.1
    )
    
    # SNR subplot (row 1, col 1)
    fig.add_trace(
        go.Scatter(x=df_agg['timestep'], y=df_agg['snr_mean'], mode='lines', 
                  name='Mean SNR', line=dict(color='blue', width=2)),
        row=1, col=1
    )
    fig.add_trace(
        go.Scatter(x=df_agg['timestep'], y=df_agg['snr_median'], mode='lines', 
                  name='Median SNR', line=dict(color='green', width=1, dash='dash')),
        row=1, col=1
    )
    fig.add_trace(
        go.Scatter(x=df_agg['timestep'], y=df_agg['snr_min'], mode='lines', 
                  name='Min SNR', line=dict(color='lightblue', width=1, dash='dot'), 
                  showlegend=False),
        row=1, col=1
    )
    fig.add_trace(
        go.Scatter(x=df_agg['timestep'], y=df_agg['snr_max'], mode='lines', 
                  name='Max SNR', line=dict(color='lightblue', width=1, dash='dot'), 
                  showlegend=False, fill='tonexty', fillcolor='rgba(173,216,230,0.2)'),
        row=1, col=1
    )
    
    # Power subplot (row 1, col 2)
    fig.add_trace(
        go.Scatter(x=df_agg['timestep'], y=df_agg['power_mean'], mode='lines', 
                  name='Mean Power', line=dict(color='red', width=2)),
        row=1, col=2
    )
    fig.add_trace(
        go.Scatter(x=df_agg['timestep'], y=df_agg['power_median'], mode='lines', 
                  name='Median Power', line=dict(color='orange', width=1, dash='dash')),
        row=1, col=2
    )
    
    # Distribution subplot (row 2, col 1)
    fig.add_trace(
        go.Scatter(x=df_agg['timestep'], y=df_agg['dist_mean'], mode='lines', 
                  name='Mean Distribution', line=dict(color='purple', width=2)),
        row=2, col=1
    )
    fig.add_trace(
        go.Scatter(x=df_agg['timestep'], y=df_agg['dist_median'], mode='lines', 
                  name='Median Distribution', line=dict(color='pink', width=1, dash='dash')),
        row=2, col=1
    )
    
    # SF subplot (row 2, col 2)
    fig.add_trace(
        go.Scatter(x=df_agg['timestep'], y=df_agg['sf_mean'], mode='lines', 
                  name='Mean SF', line=dict(color='brown', width=2)),
        row=2, col=2
    )
    fig.add_trace(
        go.Scatter(x=df_agg['timestep'], y=df_agg['sf_median'], mode='lines', 
                  name='Median SF', line=dict(color='tan', width=1, dash='dash')),
        row=2, col=2
    )
    
    # Update axes labels
    fig.update_xaxes(title_text="Timestep", row=1, col=1)
    fig.update_xaxes(title_text="Timestep", row=1, col=2)
    fig.update_xaxes(title_text="Timestep", row=2, col=1)
    fig.update_xaxes(title_text="Timestep", row=2, col=2)
    
    fig.update_yaxes(title_text="SNR (dB)", row=1, col=1)
    fig.update_yaxes(title_text="Power", row=1, col=2)
    fig.update_yaxes(title_text="Distribution", row=2, col=1)
    fig.update_yaxes(title_text="Spreading Factor", row=2, col=2)
    
    fig.update_layout(
        title_text="Mote Metrics Over Time - Mean and Median Values",
        height=800,
        showlegend=True
    )
    
    return fig

def moteMetricsDistribution(df_mote_metrics):
    """
    Create a 2x2 subplot showing distribution of values at different timestep intervals.
    
    :param df_mote_metrics: DataFrame with mote metrics
    :return: Plotly figure with 2x2 subplots
    """
    # Sample timesteps to avoid overcrowding (every 10th timestep)
    max_timestep = df_mote_metrics['timestep'].max()
    sample_timesteps = list(range(0, int(max_timestep) + 1, max(10, int(max_timestep) // 20)))
    if sample_timesteps[-1] != max_timestep:
        sample_timesteps.append(int(max_timestep))
    
    df_sampled = df_mote_metrics[df_mote_metrics['timestep'].isin(sample_timesteps)].copy()
    
    fig = make_subplots(
        rows=2, cols=2,
        subplot_titles=("SNR Distribution Over Time", "Power Distribution Over Time",
                       "Distribution Factor Over Time", "SF Distribution Over Time"),
        vertical_spacing=0.12,
        horizontal_spacing=0.1
    )
    
    # SNR Violin plots
    for ts in sample_timesteps:
        df_ts = df_sampled[df_sampled['timestep'] == ts]
        if not df_ts.empty:
            fig.add_trace(
                go.Violin(
                    y=df_ts['snr'],
                    x=[ts] * len(df_ts),
                    name=f'Timestep {ts}',
                    box_visible=True,
                    meanline_visible=True,
                    showlegend=False,
                    side='positive',
                    width=0.6
                ),
                row=1, col=1
            )
    
    # Power Box plots
    for ts in sample_timesteps:
        df_ts = df_sampled[df_sampled['timestep'] == ts]
        if not df_ts.empty:
            fig.add_trace(
                go.Box(
                    y=df_ts['power'],
                    x=[ts] * len(df_ts),
                    name=f'Timestep {ts}',
                    showlegend=False,
                    boxpoints='outliers'
                ),
                row=1, col=2
            )
    
    # Distribution Violin plots
    for ts in sample_timesteps:
        df_ts = df_sampled[df_sampled['timestep'] == ts]
        if not df_ts.empty:
            fig.add_trace(
                go.Violin(
                    y=df_ts['distribution'],
                    x=[ts] * len(df_ts),
                    name=f'Timestep {ts}',
                    box_visible=True,
                    meanline_visible=True,
                    showlegend=False,
                    side='positive',
                    width=0.6
                ),
                row=2, col=1
            )
    
    # SF Box plots
    for ts in sample_timesteps:
        df_ts = df_sampled[df_sampled['timestep'] == ts]
        if not df_ts.empty:
            fig.add_trace(
                go.Box(
                    y=df_ts['sf'],
                    x=[ts] * len(df_ts),
                    name=f'Timestep {ts}',
                    showlegend=False,
                    boxpoints='outliers'
                ),
                row=2, col=2
            )
    
    # Update axes labels
    fig.update_xaxes(title_text="Timestep", row=1, col=1)
    fig.update_xaxes(title_text="Timestep", row=1, col=2)
    fig.update_xaxes(title_text="Timestep", row=2, col=1)
    fig.update_xaxes(title_text="Timestep", row=2, col=2)
    
    fig.update_yaxes(title_text="SNR (dB)", row=1, col=1)
    fig.update_yaxes(title_text="Power", row=1, col=2)
    fig.update_yaxes(title_text="Distribution", row=2, col=1)
    fig.update_yaxes(title_text="Spreading Factor", row=2, col=2)
    
    fig.update_layout(
        title_text="Mote Metrics Distribution Over Time",
        height=800
    )
    
    return fig

def moteMetricsHeatmap(df_mote_metrics):
    """
    Create heatmaps showing metric values across motes and timesteps.
    
    :param df_mote_metrics: DataFrame with mote metrics
    :return: Plotly figure with 2x2 subplots
    """
    # Aggregate by mote (mean across all links per mote per timestep)
    df_agg = df_mote_metrics.groupby(['timestep', 'moteId']).agg({
        'snr': 'mean',
        'power': 'mean',
        'distribution': 'mean',
        'sf': 'mean'
    }).reset_index()
    
    # Create pivot tables for heatmaps
    snr_pivot = df_agg.pivot(index='moteId', columns='timestep', values='snr')
    power_pivot = df_agg.pivot(index='moteId', columns='timestep', values='power')
    dist_pivot = df_agg.pivot(index='moteId', columns='timestep', values='distribution')
    sf_pivot = df_agg.pivot(index='moteId', columns='timestep', values='sf')
    
    # Fill NaN values with forward fill, then 0 for visualization
    snr_pivot = snr_pivot.ffill().fillna(0)
    power_pivot = power_pivot.ffill().fillna(0)
    dist_pivot = dist_pivot.ffill().fillna(0)
    sf_pivot = sf_pivot.ffill().fillna(0)
    
    fig = make_subplots(
        rows=2, cols=2,
        subplot_titles=("SNR Heatmap (Mote × Timestep)", "Power Heatmap (Mote × Timestep)",
                       "Distribution Heatmap (Mote × Timestep)", "SF Heatmap (Mote × Timestep)"),
        vertical_spacing=0.12,
        horizontal_spacing=0.1
    )
    
    # SNR Heatmap
    fig.add_trace(
        go.Heatmap(
            z=snr_pivot.values,
            x=snr_pivot.columns,
            y=snr_pivot.index,
            colorscale='RdYlBu',
            colorbar=dict(title="SNR", len=0.4, y=0.75),
            hovertemplate='Mote: %{y}<br>Timestep: %{x}<br>SNR: %{z:.2f}<extra></extra>'
        ),
        row=1, col=1
    )
    
    # Power Heatmap
    fig.add_trace(
        go.Heatmap(
            z=power_pivot.values,
            x=power_pivot.columns,
            y=power_pivot.index,
            colorscale='YlOrRd',
            colorbar=dict(title="Power", len=0.4, y=0.75),
            hovertemplate='Mote: %{y}<br>Timestep: %{x}<br>Power: %{z:.0f}<extra></extra>'
        ),
        row=1, col=2
    )
    
    # Distribution Heatmap
    fig.add_trace(
        go.Heatmap(
            z=dist_pivot.values,
            x=dist_pivot.columns,
            y=dist_pivot.index,
            colorscale='Viridis',
            colorbar=dict(title="Distribution", len=0.4, y=0.25),
            hovertemplate='Mote: %{y}<br>Timestep: %{x}<br>Distribution: %{z:.0f}<extra></extra>'
        ),
        row=2, col=1
    )
    
    # SF Heatmap
    fig.add_trace(
        go.Heatmap(
            z=sf_pivot.values,
            x=sf_pivot.columns,
            y=sf_pivot.index,
            colorscale='Plasma',
            colorbar=dict(title="SF", len=0.4, y=0.25),
            hovertemplate='Mote: %{y}<br>Timestep: %{x}<br>SF: %{z:.0f}<extra></extra>'
        ),
        row=2, col=2
    )
    
    # Update axes labels
    fig.update_xaxes(title_text="Timestep", row=1, col=1)
    fig.update_xaxes(title_text="Timestep", row=1, col=2)
    fig.update_xaxes(title_text="Timestep", row=2, col=1)
    fig.update_xaxes(title_text="Timestep", row=2, col=2)
    
    fig.update_yaxes(title_text="Mote ID", row=1, col=1)
    fig.update_yaxes(title_text="Mote ID", row=1, col=2)
    fig.update_yaxes(title_text="Mote ID", row=2, col=1)
    fig.update_yaxes(title_text="Mote ID", row=2, col=2)
    
    fig.update_layout(
        title_text="Mote Metrics Heatmaps - Average Values Per Mote",
        height=800
    )
    
    return fig

def moteMetricsTrajectories(df_mote_metrics, selected_motes=None):
    """
    Create line charts showing individual mote trajectories.
    Shows top N motes by variance if selected_motes is None.
    
    :param df_mote_metrics: DataFrame with mote metrics
    :param selected_motes: List of mote IDs to show, or None to show top 5 by variance
    :return: Plotly figure with 2x2 subplots
    """
    # Aggregate by mote and timestep (mean across links)
    df_agg = df_mote_metrics.groupby(['timestep', 'moteId']).agg({
        'snr': 'mean',
        'power': 'mean',
        'distribution': 'mean',
        'sf': 'mean'
    }).reset_index()
    
    if selected_motes is None:
        # Calculate variance for each mote and select top 5
        variance_by_mote = df_agg.groupby('moteId').agg({
            'snr': 'var',
            'power': 'var',
            'distribution': 'var',
            'sf': 'var'
        }).sum(axis=1).sort_values(ascending=False)
        selected_motes = variance_by_mote.head(5).index.tolist()
    
    df_selected = df_agg[df_agg['moteId'].isin(selected_motes)].copy()
    
    fig = make_subplots(
        rows=2, cols=2,
        subplot_titles=("SNR Trajectories by Mote", "Power Trajectories by Mote",
                       "Distribution Trajectories by Mote", "SF Trajectories by Mote"),
        vertical_spacing=0.12,
        horizontal_spacing=0.1
    )
    
    # Color palette for different motes
    colors = ['blue', 'red', 'green', 'purple', 'orange', 'brown', 'pink', 'gray', 'olive', 'cyan']
    
    for idx, mote_id in enumerate(selected_motes):
        df_mote = df_selected[df_selected['moteId'] == mote_id].sort_values('timestep')
        color = colors[idx % len(colors)]
        
        # SNR
        fig.add_trace(
            go.Scatter(
                x=df_mote['timestep'],
                y=df_mote['snr'],
                mode='lines+markers',
                name=f'Mote {mote_id}',
                line=dict(color=color, width=2),
                marker=dict(size=4)
            ),
            row=1, col=1
        )
        
        # Power
        fig.add_trace(
            go.Scatter(
                x=df_mote['timestep'],
                y=df_mote['power'],
                mode='lines+markers',
                name=f'Mote {mote_id}',
                line=dict(color=color, width=2),
                marker=dict(size=4),
                showlegend=False
            ),
            row=1, col=2
        )
        
        # Distribution
        fig.add_trace(
            go.Scatter(
                x=df_mote['timestep'],
                y=df_mote['distribution'],
                mode='lines+markers',
                name=f'Mote {mote_id}',
                line=dict(color=color, width=2),
                marker=dict(size=4),
                showlegend=False
            ),
            row=2, col=1
        )
        
        # SF
        fig.add_trace(
            go.Scatter(
                x=df_mote['timestep'],
                y=df_mote['sf'],
                mode='lines+markers',
                name=f'Mote {mote_id}',
                line=dict(color=color, width=2),
                marker=dict(size=4),
                showlegend=False
            ),
            row=2, col=2
        )
    
    # Update axes labels
    fig.update_xaxes(title_text="Timestep", row=1, col=1)
    fig.update_xaxes(title_text="Timestep", row=1, col=2)
    fig.update_xaxes(title_text="Timestep", row=2, col=1)
    fig.update_xaxes(title_text="Timestep", row=2, col=2)
    
    fig.update_yaxes(title_text="SNR (dB)", row=1, col=1)
    fig.update_yaxes(title_text="Power", row=1, col=2)
    fig.update_yaxes(title_text="Distribution", row=2, col=1)
    fig.update_yaxes(title_text="Spreading Factor", row=2, col=2)
    
    fig.update_layout(
        title_text=f"Mote Metrics Trajectories - Top {len(selected_motes)} Motes by Variance",
        height=800,
        showlegend=True
    )
    
    return fig

def createMoteMetricsCharts(df_mote_metrics):
    """
    Main function to create all mote metrics charts.
    
    :param df_mote_metrics: DataFrame with mote metrics
    """
    print("Creating mote metrics visualizations...")
    
    # Time series charts
    time_series_fig = moteMetricsTimeSeries(df_mote_metrics)
    time_series_fig.show()
    
    # Distribution charts
    distribution_fig = moteMetricsDistribution(df_mote_metrics)
    distribution_fig.show()
    
    # Heatmap charts
    heatmap_fig = moteMetricsHeatmap(df_mote_metrics)
    heatmap_fig.show()
    
    # Trajectory charts
    trajectory_fig = moteMetricsTrajectories(df_mote_metrics)
    trajectory_fig.show()

def createCharts(df):
    # 1. Linechart for mean MIS over time (with error bounds)
    mis_fig = misChart(df)
    mis_fig.show()

    # 2. Linechart for mean gamma over time
    gamma_fig = gammaChart(df)
    gamma_fig.show()

    surprises_fig = surpriseChart(df)
    surprises_fig.show()

    satisfaction_fig = satisfactionPlots(df = df.filter(items=["timestep", "mecsattimestep", "rplsattimestep"]))
    satisfaction_fig.show()

    satisfaction_violins_fig = satisfactionViolins(df = df.filter(items=["timestep", "mecsattimestep", "rplsattimestep"]))
    print(satisfaction_violins_fig)
    satisfaction_violins_fig.show()


def loadMoteMetrics(folder_path):
    """
    Loads and preprocesses mote_metrics.txt file.
    
    :param folder_path: Path to the output directory containing mote_metrics.txt
    :return: DataFrame with columns: timestep, moteId, linkIndex, source, dest, snr, power, distribution, sf
    """
    file_path = os.path.join(folder_path, "mote_metrics.txt")
    
    if not os.path.exists(file_path):
        raise FileNotFoundError(f"mote_metrics.txt not found in {folder_path}")
    
    if os.path.getsize(file_path) == 0:
        raise ValueError(f"mote_metrics.txt is empty")
    
    try:
        # Read the file with header row
        df = pd.read_csv(file_path, sep=r"\s+", header=0, on_bad_lines='skip', engine='python')
        
        # Validate expected columns
        expected_columns = ['timestep', 'moteId', 'linkIndex', 'source', 'dest', 'snr', 'power', 'distribution', 'sf']
        if list(df.columns) != expected_columns:
            # Try reading without header if column names don't match
            df = pd.read_csv(file_path, sep=r"\s+", header=None, on_bad_lines='skip', engine='python')
            if df.shape[1] == len(expected_columns):
                df.columns = expected_columns
            else:
                raise ValueError(f"Expected {len(expected_columns)} columns, got {df.shape[1]}")
        
        # Convert to appropriate data types
        df['timestep'] = pd.to_numeric(df['timestep'], errors='coerce')
        df['moteId'] = pd.to_numeric(df['moteId'], errors='coerce')
        df['linkIndex'] = pd.to_numeric(df['linkIndex'], errors='coerce')
        df['source'] = pd.to_numeric(df['source'], errors='coerce')
        df['dest'] = pd.to_numeric(df['dest'], errors='coerce')
        df['snr'] = pd.to_numeric(df['snr'], errors='coerce')
        df['power'] = pd.to_numeric(df['power'], errors='coerce')
        df['distribution'] = pd.to_numeric(df['distribution'], errors='coerce')
        df['sf'] = pd.to_numeric(df['sf'], errors='coerce')
        
        # Remove rows with NaN in critical columns
        df = df.dropna(subset=['timestep', 'moteId', 'snr', 'power', 'distribution', 'sf'])
        
        return df
        
    except pd.errors.EmptyDataError:
        raise ValueError(f"mote_metrics.txt is empty or could not be parsed")
    except pd.errors.ParserError as e:
        raise ValueError(f"Error parsing mote_metrics.txt: {e}")
    except Exception as e:
        raise ValueError(f"Error loading mote_metrics.txt: {e}")

def getData():
    """    
    Reads data from a file and returns it as a dataframe
    :param filename: the file to read from
    :param perMote: whether the outputs are per mote, or over whole system
    """

    dfs_2 = []
    dfs_3 = []
    # Determine the correct output directory path
    # The script may be run from workspace root or from L4Project/ directory
    script_dir = os.path.dirname(os.path.abspath(__file__))
    # If script is in L4Project/, use output_dir relative to script
    # If script is elsewhere, try L4Project/output_dir
    if os.path.basename(script_dir) == "L4Project":
        folder_path = os.path.join(script_dir, "output_dir")
    else:
        # Try L4Project/output_dir relative to script location
        l4project_output = os.path.join(script_dir, "L4Project", "output_dir")
        if os.path.exists(l4project_output):
            folder_path = l4project_output
        else:
            # Fallback: try output_dir in current directory
            folder_path = "output_dir"
    
    # Verify the folder exists
    if not os.path.exists(folder_path):
        raise FileNotFoundError(f"Output directory not found: {folder_path}. Tried: {script_dir}/output_dir, {script_dir}/L4Project/output_dir, ./output_dir")
    
    for filename in os.listdir(folder_path):
        file_path = os.path.join(folder_path, filename)
        if os.path.isfile(file_path):
            # Skip files that shouldn't be processed
            if filename == "IoT.alpha" or filename == "SelectedAction.txt":
                continue
            # Skip empty files to avoid EmptyDataError
            if os.path.getsize(file_path) == 0:
                print(f"Warning: Skipping empty file: {filename}")
                continue
            try:
                df_mote_metrics = pd.read_csv(file_path, sep=r"\s+", header=None, on_bad_lines='skip', engine='python')
                # Read CSV with whitespace separator, skip bad lines
                df = pd.read_csv(file_path, sep=r"\s+", header=None, on_bad_lines='skip', engine='python')
            except pd.errors.EmptyDataError:
                print(f"Warning: Skipping empty file: {filename}")
                continue
            except pd.errors.ParserError as e:
                print(f"Warning: Skipping file {filename} due to parsing error: {e}")
                print(f"  File path: {file_path}")
                continue
            except Exception as e:
                print(f"Warning: Skipping file {filename} due to error: {e}")
                continue
            
            # Skip if DataFrame is empty after reading
            if df.empty:
                print(f"Warning: Skipping file {filename} - DataFrame is empty after reading")
                continue
            
            file_col_name = filename.split('.')[0].lower()
            if df.shape[1] == 3:
                if file_col_name == "misbounds":
                    df.columns = ["timestep", "mis_lower", "mis_upper"]
                    dfs_2.append(df)
                    continue
                df.columns = ["moteid", "timestep", file_col_name.lower()]
                dfs_3.append(df)
            elif df.shape[1] == 2:
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
    
    # Load and create mote metrics charts
    try:
        # Determine the correct output directory path
        script_dir = os.path.dirname(os.path.abspath(__file__))
        if os.path.basename(script_dir) == "L4Project":
            folder_path = os.path.join(script_dir, "output_dir")
        else:
            l4project_output = os.path.join(script_dir, "L4Project", "output_dir")
            if os.path.exists(l4project_output):
                folder_path = l4project_output
            else:
                folder_path = "output_dir"
        
        df_mote_metrics = loadMoteMetrics(folder_path)
        print(f"\nLoaded {len(df_mote_metrics)} rows of mote metrics data")
        print(df_mote_metrics.head(30))
        
        createMoteMetricsCharts(df_mote_metrics)
    except FileNotFoundError as e:
        print(f"\nWarning: Could not load mote metrics: {e}")
        print("Skipping mote metrics visualizations.")
    except Exception as e:
        print(f"\nWarning: Error creating mote metrics charts: {e}")
        print("Skipping mote metrics visualizations.")

if __name__ == "__main__":
    run()
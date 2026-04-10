from functools import reduce
import argparse
import numpy as np
import pandas as pd
import plotly.graph_objects as go
from plotly.subplots import make_subplots
import os
import sys
import subprocess
import dash
from dash import dcc, html, Input, Output
from loguru import logger

# Configure loguru: clear default, add stderr with stage-friendly format
logger.remove()
logger.add(sys.stderr, level="INFO", format="<green>{time:HH:mm:ss}</green> | <level>{level: <8}</level> | <level>{message}</level>")


def satisfactionViolins(df):
    fig = make_subplots(
        rows=1, 
        cols=2, 
        subplot_titles=("MEC Satisfaction Distribution (Coulombs)", "RPL Satisfaction Distribution (%)")
        )

    fig.add_trace(
        go.Violin(
            y=df["mecsattimestep"],
            name="MEC Satisfaction",
            box_visible=True,
            meanline_visible=True,
            line_color="black",
            fillcolor="lightseagreen",
            opacity=0.6
        ),
        row=1,
        col=1
    )

    fig.add_trace(
        go.Violin(
            y=df["rplsattimestep"] * 100,
            name="RPL Satisfaction",
            box_visible=True,
            meanline_visible=True,
            line_color="black",
            fillcolor="orange",
            opacity=0.6
        ),
        row=1,
        col=2,
    )

    fig.update_layout(yaxis_zeroline=False)
    return fig

def satisfactionPlots(df, mec_threshold=20.0, rpl_threshold=0.2):
    """
    MEC/RPL satisfaction time-series with horizontal threshold lines.
    Thresholds come from solver.config (mecThreshold, rplThreshold) for the run.
    """
    fig = make_subplots(rows=2, cols=1)

    fig.add_trace(
        go.Scatter(
            x=df["timestep"],
            y=df["mecsattimestep"],
            mode="lines",
            name="MEC Satisfaction (Coulombs)",
            line=dict(color="lightseagreen"),
        ),
        row=1,
        col=1,
    )

    fig.add_trace(
        go.Scatter(
            x=df["timestep"],
            y=df["rplsattimestep"] * 100,
            mode="lines",
            name="RPL Satisfaction (%)",
            line=dict(color="orange"),
        ),
        row=2,
        col=1,
    )

    fig.add_shape(
        type="line",
        x0=df["timestep"].min(),
        x1=df["timestep"].max(),
        y0=mec_threshold,
        y1=mec_threshold,
        xref="x1",
        yref="y1",
        line=dict(color="Red"),
    )

    fig.add_shape(
        type="line",
        x0=df["timestep"].min(),
        x1=df["timestep"].max(),
        y0=rpl_threshold * 100,
        y1=rpl_threshold * 100,
        xref="x2",
        yref="y2",
        line=dict(color="Red"),
    )

    fig.update_yaxes(range=[0, df["mecsattimestep"].max() + mec_threshold], row=1, col=1)
    fig.update_yaxes(range=[0, df["rplsattimestep"].max() * 100 + rpl_threshold * 100], row=2, col=1)
    fig.update_xaxes(title_text="Timestep", row=1, col=1)
    fig.update_xaxes(title_text="Timestep", row=2, col=1)
    fig.update_yaxes(title_text="MEC (Coulombs)", row=1, col=1)
    fig.update_yaxes(title_text="RPL (%)", row=2, col=1)

    return fig

def surpriseChart(df):
    # Apply moving average smoothing to all three surprise series
    s_bf_smooth = smooth_series(df["surprisebf"], SMOOTHING_WINDOW)
    s_cc_smooth = smooth_series(df["surprisecc"], SMOOTHING_WINDOW)
    s_mis_smooth = smooth_series(df["surprisemis"], SMOOTHING_WINDOW)
    
    fig = go.Figure()
    
    # Add unsmoothed traces (lighter, thinner lines)
    fig.add_trace(go.Scatter(
        x=df["timestep"],
        y=df["surprisebf"],
        mode="lines",
        name="Mean Bayes Factor Surprise",
        line=dict(width=1),
        opacity=0.5,
    ))
    fig.add_trace(go.Scatter(
        x=df["timestep"],
        y=df["surprisecc"],
        mode="lines",
        name="Mean Confidence-Corrected Surprise",
        line=dict(width=1),
        opacity=0.5,
    ))
    fig.add_trace(go.Scatter(
        x=df["timestep"],
        y=df["surprisemis"],
        mode="lines",
        name="Mean MIS",
        line=dict(width=1),
        opacity=0.5,
    ))
    
    # Add smoothed traces (thicker, solid lines)
    fig.add_trace(go.Scatter(
        x=df["timestep"],
        y=s_bf_smooth,
        mode="lines",
        name="Mean Bayes Factor Surprise (Smoothed)",
        line=dict(width=2),
    ))
    fig.add_trace(go.Scatter(
        x=df["timestep"],
        y=s_cc_smooth,
        mode="lines",
        name="Mean Confidence-Corrected Surprise (Smoothed)",
        line=dict(width=2),
    ))
    fig.add_trace(go.Scatter(
        x=df["timestep"],
        y=s_mis_smooth,
        mode="lines",
        name="Mean MIS (Smoothed)",
        line=dict(width=2),
    ))

    fig.update_layout(
        title="Mean Surprise Over Time",
        xaxis_title="Timestep",
        yaxis_title="Mean Surprise",
        legend_title="Surprise Types",
    )
    return fig

# Skip this many initial surprise rows so MIS cold-start (first 2*lookback) does not skew normalisation
MIS_WARMUP_ROWS = 2 * 5  # 2 * lookback (Java default lookback=5)

# Window size for moving average smoothing
SMOOTHING_WINDOW = 5  # Window size for moving average smoothing

def smooth_series(series, window_size):
    """Apply centered moving average smoothing to a pandas Series.
    
    Args:
        series: pandas Series to smooth
        window_size: Size of the moving average window
        
    Returns:
        Smoothed pandas Series with same index. Returns original series if length < window_size.
    """
    if len(series) < window_size:
        return series
    return series.rolling(window=window_size, center=True).mean()

def surpriseChartNormalized(df):
    """Surprise measures standardised (z-score) for comparison: each has mean 0 and std 1.
    Skips the first 2*lookback rows so MIS cold-start does not skew the standardisation."""
    df = df.iloc[MIS_WARMUP_ROWS:].reset_index(drop=True)
    if df.empty:
        return go.Figure()
    s_bf = df["surprisebf"]
    s_cc = df["surprisecc"]
    s_mis = df["surprisemis"]

    def zscore(series):
        mu, sigma = series.mean(), series.std()
        if sigma == 0 or np.isnan(sigma):
            return pd.Series(0.0, index=series.index)
        return (series - mu) / sigma

    std_bf = zscore(s_bf)
    std_cc = zscore(s_cc)
    std_mis = zscore(s_mis)

    # Apply moving average smoothing to standardized values
    std_bf_smooth = smooth_series(std_bf, SMOOTHING_WINDOW)
    std_cc_smooth = smooth_series(std_cc, SMOOTHING_WINDOW)
    std_mis_smooth = smooth_series(std_mis, SMOOTHING_WINDOW)

    fig = go.Figure()
    
    # Add unsmoothed standardized traces (lighter, thinner lines)
    fig.add_trace(go.Scatter(
        x=df["timestep"],
        y=std_bf,
        mode="lines",
        name="Mean Bayes Factor Surprise",
        line=dict(width=1),
        opacity=0.5,
    ))
    fig.add_trace(go.Scatter(
        x=df["timestep"],
        y=std_cc,
        mode="lines",
        name="Mean Confidence-Corrected Surprise",
        line=dict(width=1),
        opacity=0.5,
    ))
    fig.add_trace(go.Scatter(
        x=df["timestep"],
        y=std_mis,
        mode="lines",
        name="Mean MIS",
        line=dict(width=1),
        opacity=0.5,
    ))
    
    # Add smoothed standardized traces (thicker, solid lines)
    fig.add_trace(go.Scatter(
        x=df["timestep"],
        y=std_bf_smooth,
        mode="lines",
        name="Mean Bayes Factor Surprise (Smoothed)",
        line=dict(width=2),
    ))
    fig.add_trace(go.Scatter(
        x=df["timestep"],
        y=std_cc_smooth,
        mode="lines",
        name="Mean Confidence-Corrected Surprise (Smoothed)",
        line=dict(width=2),
    ))
    fig.add_trace(go.Scatter(
        x=df["timestep"],
        y=std_mis_smooth,
        mode="lines",
        name="Mean MIS (Smoothed)",
        line=dict(width=2),
    ))
    y_min = min(std_bf.min(), std_cc.min(), std_mis.min())
    y_max = max(std_bf.max(), std_cc.max(), std_mis.max())
    fig.update_layout(
        title="Mean Surprise Over Time (Standardised for Comparison)",
        xaxis_title="Timestep",
        yaxis_title="Standardised Surprise (z-score)",
        legend_title="Surprise Types",
        yaxis=dict(range=[y_min, y_max]),
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

def getUniqueMotesAndLinks(df_mote_metrics):
    """
    Extract unique mote IDs and link pairs from the dataframe for filter options.
    
    :param df_mote_metrics: DataFrame with mote metrics
    :return: tuple of (unique_mote_ids, unique_links) where links are formatted as "source->dest"
    """
    # Get unique mote IDs
    unique_motes = sorted(df_mote_metrics['moteId'].unique().tolist())
    
    # Get unique link pairs (source->dest)
    # df_mote_metrics['link_id'] = df_mote_metrics['source'].astype(str) + '->' + df_mote_metrics['dest'].astype(str)
    unique_links = sorted(df_mote_metrics['link_id'].unique().tolist())
    
    return unique_motes, unique_links

def generateFilteredPlots(df_filtered, aggregation_mode, selected_motes=None, selected_links=None):
    """
    Generate 3 separate independent figures (SNR, Power, Distribution) based on filtered data and aggregation mode.
    Each figure is completely independent with its own traces, axes, and legend.
    
    :param df_filtered: Filtered DataFrame with mote metrics
    :param aggregation_mode: 'per_mote' or 'all_links'
    :param selected_motes: List of selected mote IDs (for labeling)
    :param selected_links: List of selected link IDs (for labeling)
    :return: Tuple of 3 Plotly figures: (snr_fig, power_fig, dist_fig)
    """
    if df_filtered.empty:
        # Return empty figures with message
        empty_fig = go.Figure()
        empty_fig.add_annotation(
            text="No data available for selected filters",
            xref="paper", yref="paper",
            x=0.5, y=0.5, showarrow=False,
            font=dict(size=16)
        )
        return empty_fig, empty_fig, empty_fig
    
    # Ensure link_id column exists
    if 'link_id' not in df_filtered.columns:
        df_filtered = df_filtered.copy()
        df_filtered['link_id'] = df_filtered['source'].astype(str) + '->' + df_filtered['dest'].astype(str)
    
    # Color palette for traces
    colors = ['#1f77b4', '#ff7f0e', '#2ca02c', '#d62728', '#9467bd', '#8c564b', 
              '#e377c2', '#7f7f7f', '#bcbd22', '#17becf', '#aec7e8', '#ffbb78',
              '#98df8a', '#ff9896', '#c5b0d5', '#c49c94', '#f7b6d3', '#c7c7c7']
    
    # Create 3 separate independent figures
    snr_fig = go.Figure()
    power_fig = go.Figure()
    dist_fig = go.Figure()
    
    if aggregation_mode == 'per_mote':
        # Aggregate links per mote (mean across links for each mote)
        unique_motes = sorted(df_filtered['moteId'].unique())
        
        trace_count_snr = 0
        trace_count_power = 0
        trace_count_dist = 0
        
        for idx, mote_id in enumerate(unique_motes):
            df_mote = df_filtered[df_filtered['moteId'] == mote_id].copy()

            # Debug: Check what timesteps are in df_mote before aggregation
            if df_mote.empty:
                logger.debug(f"Mote {mote_id} is empty")
                continue
            actual_timesteps = sorted(df_mote['timestep'].unique().tolist())
            if len(actual_timesteps) > 10 or max(actual_timesteps) > 10:
                logger.debug(f"Mote {mote_id} has timesteps: {actual_timesteps[:20]}{'...' if len(actual_timesteps) > 20 else ''}, max: {max(actual_timesteps)}")
            
            df_agg = df_mote.groupby('timestep').agg({
                'snr': 'mean',
                'power': 'mean',
                'distribution': 'mean'
            }).reset_index().sort_values('timestep')
            
            if not df_agg.empty:
                logger.debug(f"Mote {mote_id} aggregated - {len(df_agg)} timesteps, SNR range: {df_agg['snr'].min():.2f} to {df_agg['snr'].max():.2f}, Power range: {df_agg['power'].min()} to {df_agg['power'].max()}, Dist range: {df_agg['distribution'].min()} to {df_agg['distribution'].max()}")

            if not df_agg.empty:
                color = colors[idx % len(colors)]
                label = f"Mote {int(mote_id)} (avg across links)"
                
                # Use unique trace names per metric to avoid Plotly hiding traces
                # But keep same legendgroup so they toggle together in legend
                # This ensures each trace has a unique identifier while still grouping in legend
                snr_name = f"{label}_snr"
                power_name = f"{label}_power"
                dist_name = f"{label}_dist"
                
                # SNR - add to SNR figure
                snr_fig.add_trace(
                    go.Scatter(
                        x=df_agg['timestep'],
                        y=df_agg['snr'],
                        mode='lines+markers',
                        name=label,  # Clean label for legend
                        line=dict(color=color, width=2),
                        marker=dict(size=4),
                        hovertemplate=f'<b>{label}</b><br>Timestep: %{{x}}<br>SNR: %{{y:.2f}}<extra></extra>',
                        showlegend=True
                    )
                )
                
                # Power - add to Power figure
                power_fig.add_trace(
                    go.Scatter(
                        x=df_agg['timestep'],
                        y=df_agg['power'],
                        mode='lines+markers',
                        name=label,  # Same label for consistency
                        line=dict(color=color, width=2),
                        marker=dict(size=4),
                        hovertemplate=f'<b>{label}</b><br>Timestep: %{{x}}<br>Power: %{{y}}<extra></extra>',
                        showlegend=True
                    )
                )
                
                trace_count_snr += 1
                trace_count_power += 1
                
                logger.debug(f"Added SNR and Power traces for {label} (mote {mote_id})")
                
                # Distribution - add to Distribution figure
                dist_fig.add_trace(
                    go.Scatter(
                        x=df_agg['timestep'],
                        y=df_agg['distribution'],
                        mode='lines+markers',
                        name=label,  # Same label for consistency
                        line=dict(color=color, width=2),
                        marker=dict(size=4),
                        hovertemplate=f'<b>{label}</b><br>Timestep: %{{x}}<br>Distribution: %{{y}}<extra></extra>',
                        showlegend=True
                    )
                )
                trace_count_dist += 1
                logger.debug(f"Added distribution trace for {label} (mote {mote_id})")
        
        # Verify all traces were added
        logger.debug(f"per_mote mode - Trace counts - SNR: {trace_count_snr}, Power: {trace_count_power}, Distribution: {trace_count_dist} for {len(unique_motes)} unique motes")
        logger.debug(f"Actual figure traces count - SNR: {len(snr_fig.data)}, Power: {len(power_fig.data)}, Distribution: {len(dist_fig.data)}")
    
    else:  # 'all_links' - show all selected links as separate traces
        unique_links = sorted(df_filtered['link_id'].unique())
        logger.debug(f"unique_links: {unique_links}")
        
        trace_count_snr = 0
        trace_count_power = 0
        trace_count_dist = 0
        
        for idx, link_id in enumerate(unique_links):
            df_link = df_filtered[df_filtered['link_id'] == link_id].sort_values('timestep')
            
            if not df_link.empty:
                color = colors[idx % len(colors)]
                # Get mote ID for this link (use first occurrence)
                mote_id = int(df_link.iloc[0]['moteId'])
                label = f"Link {link_id} (Mote {mote_id})"
                
                # SNR - add to SNR figure
                snr_fig.add_trace(
                    go.Scatter(
                        x=df_link['timestep'],
                        y=df_link['snr'],
                        mode='lines+markers',
                        name=label,  # This will show in legend
                        line=dict(color=color, width=2),
                        marker=dict(size=4),
                        hovertemplate=f'<b>{label}</b><br>Timestep: %{{x}}<br>SNR: %{{y:.2f}}<extra></extra>',
                        showlegend=True
                    )
                )
                trace_count_snr += 1
                
                # Power - add to Power figure
                power_fig.add_trace(
                    go.Scatter(
                        x=df_link['timestep'],
                        y=df_link['power'],
                        mode='lines+markers',
                        name=label,  # Same label for consistency
                        line=dict(color=color, width=2),
                        marker=dict(size=4),
                        hovertemplate=f'<b>{label}</b><br>Timestep: %{{x}}<br>Power: %{{y}}<extra></extra>',
                        showlegend=True
                    )
                )
                trace_count_power += 1
                
                # Distribution - add to Distribution figure
                dist_fig.add_trace(
                    go.Scatter(
                        x=df_link['timestep'],
                        y=df_link['distribution'],
                        mode='lines+markers',
                        name=label,  # Same label for consistency
                        line=dict(color=color, width=2),
                        marker=dict(size=4),
                        hovertemplate=f'<b>{label}</b><br>Timestep: %{{x}}<br>Distribution: %{{y}}<extra></extra>',
                        showlegend=True
                    )
                )
                trace_count_dist += 1
        
        logger.debug(f"Trace counts - SNR: {trace_count_snr}, Power: {trace_count_power}, Distribution: {trace_count_dist} for {len(unique_links)} unique links")
    
    # Get actual timestep range from filtered data to set x-axis limits
    
    if not df_filtered.empty:
        min_timestep = int(df_filtered['timestep'].min())
        max_timestep = int(df_filtered['timestep'].max())
        # Add small padding for better visualization (0.5 units on each side)
        x_range = [min_timestep - 0.5, max_timestep + 0.5]
        # Ensure we don't go below 0 for timesteps
        if x_range[0] < 0:
            x_range[0] = -0.5
        logger.debug(f"Setting x_range to: {x_range} (min_timestep={min_timestep}, max_timestep={max_timestep})")
    else:
        x_range = None
        logger.debug(f"x_range is None (empty dataframe)")
    
    # Configure each figure independently with its own axes and layout
    # Update SNR figure
    snr_fig.update_layout(
        title="SNR Over Time",
        xaxis_title="Timestep",
        yaxis_title="SNR (dB)",
        height=300,
        showlegend=True,
        hovermode='closest'
    )
    if x_range:
        snr_fig.update_xaxes(range=x_range, autorange=False)
    
    # Update Power figure
    power_fig.update_layout(
        title="Power Over Time",
        xaxis_title="Timestep",
        yaxis_title="Power",
        height=300,
        showlegend=True,
        hovermode='closest'
    )
    if x_range:
        power_fig.update_xaxes(range=x_range, autorange=False)
    
    # Update Distribution figure
    dist_fig.update_layout(
        title="Distribution Over Time",
        xaxis_title="Timestep",
        yaxis_title="Distribution",
        height=300,
        showlegend=True,
        hovermode='closest'
    )
    if x_range:
        dist_fig.update_xaxes(range=x_range, autorange=False)
    
    logger.debug(f"All figures configured - SNR traces: {len(snr_fig.data)}, Power traces: {len(power_fig.data)}, Distribution traces: {len(dist_fig.data)}")
    
    return snr_fig, power_fig, dist_fig

def createInteractiveMoteMetricsApp(df_mote_metrics):
    """
    Create an interactive Dash web application for filtering and visualizing mote metrics.
    
    :param df_mote_metrics: DataFrame with mote metrics
    :return: Dash app instance
    """
    logger.info("Stage: Creating interactive Dash app (mote metrics: SNR, power, distribution)")
    logger.info(f"createInteractiveMoteMetricsApp called with:")
    logger.info(f"  DataFrame shape: {df_mote_metrics.shape}")
    logger.info(f"  Timestep range: {df_mote_metrics['timestep'].min()} to {df_mote_metrics['timestep'].max()}")
    logger.info(f"  Unique timesteps count: {df_mote_metrics['timestep'].nunique()}")
    logger.info(f"  First few unique timesteps: {sorted(df_mote_metrics['timestep'].unique().tolist())[:10]}")
    
    # Make a copy of the dataframe to avoid modifying the original
    # Ensure link_id column exists
    df_copy = df_mote_metrics.copy()
    if 'link_id' not in df_copy.columns:
        logger.debug("Link ID column not found, creating it")
        df_copy['link_id'] = df_copy['source'].astype(str) + '->' + df_copy['dest'].astype(str)
    
    logger.info(f"After copy - Timestep range: {df_copy['timestep'].min()} to {df_copy['timestep'].max()}")
    
    # Get unique values for filters
    unique_motes, unique_links = getUniqueMotesAndLinks(df_copy)
    
    # Create Dash app with cache busting to prevent browser from using cached figures
    app = dash.Dash(__name__)
    
    # Disable caching for this app to ensure fresh data on reload
    # app.config.suppress_callback_exceptions = True
    
    # Add cache-busting headers to prevent browser from caching the figure
    @app.server.after_request
    def add_cache_control(response):
        response.headers['Cache-Control'] = 'no-cache, no-store, must-revalidate'
        response.headers['Pragma'] = 'no-cache'
        response.headers['Expires'] = '0'
        return response
    
    # Create options for dropdowns
    mote_options = [{'label': f'Mote {mote}', 'value': mote} for mote in unique_motes]
    link_options = [{'label': link, 'value': link} for link in unique_links]
    
    # Default selections
    default_motes = unique_motes
    default_links = []
    default_agg_mode = 'all_links'  # Changed from 'individual_links' since link_id (source->dest) is unique per mote
        
    # CRITICAL: Generate initial figures fresh to ensure correct data from start
    # The callback will also regenerate them on any filter change
    logger.info("Stage: Building initial SNR, Power, and Distribution figures for dashboard")
    initial_snr_fig, initial_power_fig, initial_dist_fig = generateFilteredPlots(df_copy, default_agg_mode, default_motes, default_links)
    
    # Debug: Check what timesteps are in the initial figures' traces
    logger.info(f"Initial figures created - checking trace data...")
    for i, trace in enumerate(initial_snr_fig.data[:3]):  # Check first 3 traces
        if hasattr(trace, 'x') and trace.x is not None:
            x_data = list(trace.x) if hasattr(trace.x, '__iter__') else [trace.x]
            if x_data:
                logger.info(f"  SNR Trace {i} ({trace.name}): x range = {min(x_data)} to {max(x_data)}, {len(x_data)} points")
    
    # Define layout with standard HTML components
    app.layout = html.Div([
        # Title at the top, spanning full width
        html.H2("Mote Metrics Interactive Dashboard", style={'marginBottom': '20px', 'paddingLeft': '20px'}),
        html.Hr(),
        
        # Two-column layout: filters (left) and plots (right), same height
        html.Div([
            # Filters column
            html.Div([
                html.H5("Filters", style={'marginBottom': '15px'}),
                
                html.Label("Select Motes:", style={'marginTop': '15px', 'display': 'block', 'fontWeight': 'bold'}),  
                dcc.Dropdown(
                    id='mote-selector',
                    options=mote_options,
                    value=default_motes,
                    multi=True,
                    placeholder="Select motes to display...",
                    style={'marginBottom': '15px'}
                ),
                
                html.Label("Select Links:", style={'marginTop': '15px', 'display': 'block', 'fontWeight': 'bold'}),
                dcc.Dropdown(
                    id='link-selector',
                    options=link_options,
                    value=[],
                    multi=True,
                    placeholder="Select links to display (leave empty for all)...",
                    style={'marginBottom': '15px'}
                ),
                
                html.Label("Aggregation Mode:", style={'marginTop': '15px', 'display': 'block', 'fontWeight': 'bold'}),  
                dcc.RadioItems(
                    id='aggregation-mode',
                    options=[
                        {'label': 'Per Link', 'value': 'all_links'},
                        {'label': 'Per Mote (avg)', 'value': 'per_mote'}
                    ],
                    value=default_agg_mode,
                    style={'marginBottom': '15px'}
                ),
                
                html.Div([
                    html.P("Select motes and/or links to filter the data. Use aggregation mode to control how data is grouped.", 
                           style={'marginTop': '20px', 'fontSize': '12px', 'color': '#666'})
                ])
                
            ], style={
                'width': '20%', 'minWidth': '200px', 'padding': '20px', 'backgroundColor': '#f5f5f5',
                'overflowY': 'auto', 'boxSizing': 'border-box'
            }),
            
            # Plot column - 3 graphs
            html.Div([
                dcc.Graph(
                    id='snr-plot', 
                    figure=initial_snr_fig,
                    style={'height': '300px', 'marginBottom': '20px'},
                    config={'displayModeBar': True, 'displaylogo': False}
                ),
                dcc.Graph(
                    id='power-plot', 
                    figure=initial_power_fig,
                    style={'height': '300px', 'marginBottom': '20px'},
                    config={'displayModeBar': True, 'displaylogo': False}
                ),
                dcc.Graph(
                    id='distribution-plot', 
                    figure=initial_dist_fig,
                    style={'height': '300px'},
                    config={'displayModeBar': True, 'displaylogo': False}
                )
            ], style={
                'width': '75%', 'flex': '1', 'padding': '20px', 'overflowY': 'auto',
                'boxSizing': 'border-box', 'minWidth': '0'
            })
        ], style={
            'display': 'flex', 'flexDirection': 'row', 'alignItems': 'stretch',
            'minHeight': 'calc(100vh - 80px)', 'width': '100%'
        })
    ], style={'fontFamily': 'sans-serif, Arial'})
    
    # Define callback - ensure it fires on initial load, returns 3 separate figures
    @app.callback(
        [Output('snr-plot', 'figure'),
         Output('power-plot', 'figure'),
         Output('distribution-plot', 'figure')],
        Input('mote-selector', 'value'),
        Input('link-selector', 'value'),
        Input('aggregation-mode', 'value'),
        prevent_initial_call=False  # Allow callback to fire on initial load
    )
    def update_plots(selected_motes, selected_links, agg_mode):
        # Debug: Confirm callback is firing
        logger.info(f"===== CALLBACK FIRED =====")
        logger.info(f"Callback inputs - selected_motes: {selected_motes}, selected_links: {selected_links}, agg_mode: {agg_mode}")
        
        # CRITICAL: Always start fresh from df_copy, never use cached/old data
        # Create a new copy each time to ensure we're not accidentally using stale references
        df_filtered = df_copy.copy()
        
        if not df_filtered.empty:
            min_ts = df_filtered['timestep'].min()
            max_ts = df_filtered['timestep'].max()
            unique_ts = sorted(df_filtered['timestep'].unique().tolist())
            logger.info(f"Data in callback - Min timestep: {min_ts}, Max timestep: {max_ts}, Unique timesteps: {unique_ts[:10]}{'...' if len(unique_ts) > 10 else ''}")
            logger.info(f"Total rows in df_filtered: {len(df_filtered)}")
        
        # Filter by motes if selected (handle None and empty list)
        if selected_motes and len(selected_motes) > 0:
            df_filtered = df_filtered[df_filtered['moteId'].isin(selected_motes)]
        
        # Filter by links if selected (handle None and empty list)
        if selected_links and len(selected_links) > 0:
            df_filtered = df_filtered[df_filtered['link_id'].isin(selected_links)]
        
        if not df_filtered.empty:
            logger.debug(f"After filtering - Min timestep: {df_filtered['timestep'].min()}, Max timestep: {df_filtered['timestep'].max()}, Rows: {len(df_filtered)}")
        
        # Generate 3 separate plots
        snr_fig, power_fig, dist_fig = generateFilteredPlots(df_filtered, agg_mode, selected_motes, selected_links)
        return snr_fig, power_fig, dist_fig
    
    return app

def createMoteMetricsCharts(df_mote_metrics):
    """
    Main function to create all mote metrics charts.
    
    :param df_mote_metrics: DataFrame with mote metrics
    """
    logger.info("Stage: Creating static mote metrics visualizations")
    # Time series charts
    logger.info("Stage: Building mote metrics time-series chart")
    time_series_fig = moteMetricsTimeSeries(df_mote_metrics)
    time_series_fig.show()
    
    # Distribution charts
    logger.info("Stage: Building mote metrics distribution chart")
    distribution_fig = moteMetricsDistribution(df_mote_metrics)
    distribution_fig.show()
    
    # Heatmap charts
    logger.info("Stage: Building mote metrics heatmap chart")
    heatmap_fig = moteMetricsHeatmap(df_mote_metrics)
    heatmap_fig.show()
    
    # Trajectory charts
    logger.info("Stage: Building mote metrics trajectory chart")
    trajectory_fig = moteMetricsTrajectories(df_mote_metrics)
    trajectory_fig.show()
    logger.info("Stage: Static mote metrics charts complete")

def createCharts(df, mec_threshold=20.0, rpl_threshold=0.2):
    """
    Build and display all main charts. mec_threshold and rpl_threshold should match
    the config used for the run (solver.config mecThreshold, rplThreshold).
    """
    logger.info("Stage: Starting main chart generation (MEC/RPL, surprise, gamma, MIS)")
    # 1. Linechart for mean MIS over time (with error bounds)
    logger.info("Stage: Building MIS chart (mean MIS over time with bounds)")
    mis_fig = misChart(df)
    mis_fig.show()

    # 2. Linechart for mean gamma over time
    logger.info("Stage: Building gamma chart (mean gamma over time)")
    gamma_fig = gammaChart(df)
    gamma_fig.show()

    logger.info("Stage: Building surprise chart (BF, CC, MIS over time)")
    surprises_fig = surpriseChart(df)
    surprises_fig.show()

    logger.info("Stage: Building normalized surprise chart")
    surprises_norm_fig = surpriseChartNormalized(df)
    surprises_norm_fig.show()

    logger.info("Stage: Building MEC/RPL satisfaction time-series plots (mecThreshold={}, rplThreshold={})", mec_threshold, rpl_threshold)
    satisfaction_fig = satisfactionPlots(
        df.filter(items=["timestep", "mecsattimestep", "rplsattimestep"]),
        mec_threshold=mec_threshold,
        rpl_threshold=rpl_threshold,
    )
    satisfaction_fig.show()

    logger.info("Stage: Building MEC/RPL satisfaction violin distributions")
    satisfaction_violins_fig = satisfactionViolins(df = df.filter(items=["timestep", "mecsattimestep", "rplsattimestep"]))
    satisfaction_violins_fig.show()
    logger.info("Stage: Main chart generation complete")


def loadMoteMetrics(folder_path):
    """
    Loads and preprocesses mote_metrics.txt file.
    
    :param folder_path: Path to the output directory containing mote_metrics.txt
    :return: DataFrame with columns: timestep, moteId, linkIndex, source, dest, snr, power, distribution, sf
    """
    file_path = os.path.join(folder_path, "mote_metrics.txt")
    logger.info("Stage: Loading mote_metrics.txt from {}", folder_path)
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
        logger.info("Stage: Loaded and validated mote_metrics ({} rows)", len(df))
        return df
        
    except pd.errors.EmptyDataError:
        raise ValueError(f"mote_metrics.txt is empty or could not be parsed")
    except pd.errors.ParserError as e:
        raise ValueError(f"Error parsing mote_metrics.txt: {e}")
    except Exception as e:
        raise ValueError(f"Error loading mote_metrics.txt: {e}")

def _default_data_dir():
    """Resolve default output directory for chart data (when --output-dir not provided)."""
    script_dir = os.path.dirname(os.path.abspath(__file__))
    if os.path.basename(script_dir) == "L4Project":
        return os.path.join(script_dir, "output_dir")
    l4project_output = os.path.join(script_dir, "L4Project", "output_dir")
    if os.path.exists(l4project_output):
        return l4project_output
    return os.path.abspath("output_dir")


def getData(folder_path=None):
    """    
    Reads data from a file and returns it as a dataframe.
    :param folder_path: Directory containing MECSattimestep.txt, RPLSattimestep.txt, etc.
                       If None, uses default (output_dir relative to script or cwd).
    """
    if folder_path is None:
        folder_path = _default_data_dir()
    else:
        folder_path = os.path.abspath(folder_path)
    logger.info("Stage: Resolving output directory for chart data")
    dfs_2 = []
    dfs_3 = []
    
    # Verify the folder exists
    if not os.path.exists(folder_path):
        raise FileNotFoundError(f"Output directory not found: {folder_path}")
    logger.info("Stage: Scanning {} for CSV/txt files (timestep, MEC, RPL, surprise, gamma, etc.)", folder_path)
    for filename in os.listdir(folder_path):
        file_path = os.path.join(folder_path, filename)
        if os.path.isfile(file_path):
            # Skip files that shouldn't be processed
            if filename == "IoT.alpha" or filename == "SelectedAction.txt":
                continue
            # Skip empty files to avoid EmptyDataError
            if os.path.getsize(file_path) == 0:
                logger.warning("Skipping empty file: {}", filename)
                continue
            try:
                # Read CSV with whitespace separator, skip bad lines
                df = pd.read_csv(file_path, sep=r"\s+", header=None, on_bad_lines='skip', engine='python')
            except pd.errors.EmptyDataError:
                logger.warning("Skipping empty file: {}", filename)
                continue
            except pd.errors.ParserError as e:
                logger.warning("Skipping file {} due to parsing error: {} (path: {})", filename, e, file_path)
                continue
            except Exception as e:
                logger.warning("Skipping file {} due to error: {}", filename, e)
                continue
            
            # Skip if DataFrame is empty after reading
            if df.empty:
                logger.warning("Skipping file {} - DataFrame is empty after reading", filename)
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
    logger.info("Stage: Merged timestep and per-mote data into single dataframe ({} rows)", len(dfs_all.merge(dfs_all_surprise, on="timestep")))
    return dfs_all.merge(dfs_all_surprise, on="timestep")

def kill_processes_on_port(port=8050):
    """
    Kill any processes currently listening on the specified port.
    This prevents port conflicts when starting the Dash server.
    
    :param port: Port number to check and clear (default: 8050)
    """
    try:
        # Determine script path relative to this Python file
        script_dir = os.path.dirname(os.path.abspath(__file__))
        
        if sys.platform == 'win32':
            script_path = os.path.join(script_dir, 'kill_port.bat')
            cmd = [script_path, str(port)]
        else:
            script_path = os.path.join(script_dir, 'kill_port.sh')
            cmd = ['bash', script_path, str(port)]
        
        # Check if script exists
        if not os.path.exists(script_path):
            logger.warning("Port cleanup script not found at {}. Skipping port check.", script_path)
            return
        
        # Run the script
        result = subprocess.run(
            cmd,
            shell=True if sys.platform == 'win32' else False,
            capture_output=True,
            text=True,
            timeout=10
        )
        
        # Print script output
        if result.stdout:
            logger.info(result.stdout.strip())
        if result.stderr and result.returncode != 0:
            logger.warning("Port check stderr: {}", result.stderr.strip())
    
    except subprocess.TimeoutExpired:
        logger.warning("Timeout while checking port {}. Proceeding anyway.", port)
    except Exception as e:
        logger.warning(f"Warning: Error checking port {port}: {e}. Proceeding anyway...")

def run(data_dir=None, mec_threshold=20.0, rpl_threshold=0.2):
    """
    Run the full chart generation pipeline.
    :param data_dir: Directory containing solver output (MECSattimestep.txt, gamma.txt, mote_metrics.txt, etc.).
                     If None, uses default (output_dir relative to script or cwd). When invoked by SolvePOMDP.java,
                     this is set from solver.config's outputDirectory.
    :param mec_threshold: MEC threshold from solver.config (horizontal line and axis scaling). Default 20.
    :param rpl_threshold: RPL threshold from solver.config (horizontal line and axis scaling). Default 0.2.
    """
    if data_dir is not None:
        data_dir = os.path.abspath(data_dir)
        logger.info("Stage: Using data directory from config: {}", data_dir)
    logger.info("Stage: Chart generation pipeline started")
    logger.info("Stage: Loading main chart data (MECSattimestep, RPLSattimestep, gamma, surprise, etc.)")
    df_all = getData(data_dir)
 
    createCharts(df_all, mec_threshold=mec_threshold, rpl_threshold=rpl_threshold)
    
    # Load and create mote metrics charts
    try:
        folder_path = data_dir if data_dir is not None else _default_data_dir()
        logger.info("Stage: Loading mote metrics from {}", folder_path)
        df_mote_metrics = loadMoteMetrics(folder_path)
        logger.info(f"\nLoaded {len(df_mote_metrics)} rows of mote metrics data")
        logger.info(f"Timestep range: {df_mote_metrics['timestep'].min()} to {df_mote_metrics['timestep'].max()}")
        logger.info(f"Unique timesteps: {sorted(df_mote_metrics['timestep'].unique().tolist())}")
        logger.info(df_mote_metrics.head(30))
        
        # Create static charts (for backward compatibility)
        logger.info("Stage: Preparing interactive mote metrics Dash app")
        #createMoteMetricsCharts(df_mote_metrics)
        
        # Create and launch interactive Dash app
        logger.info("Stage: Checking port 8050 and killing any existing process")
        kill_processes_on_port(8050)
        
        logger.info("Stage: Launching interactive Dash app (mote metrics: filter by motes/links); Ctrl+C to stop")
        
        app = createInteractiveMoteMetricsApp(df_mote_metrics)
        # use_reloader=False: with debug=True the Werkzeug reloader would run this script
        # twice (parent + child), causing createCharts() and its fig.show() to run twice.
        app.run(debug=True, port=8050, use_reloader=False)
        
    except FileNotFoundError as e:
        logger.warning("Could not load mote metrics: {}. Skipping mote metrics visualizations.", e)
    except Exception as e:
        logger.warning("Error creating mote metrics charts: {}. Skipping mote metrics visualizations.", e)
        import traceback
        traceback.print_exc()

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Generate charts from solver output (MEC, RPL, surprise, gamma, mote metrics).")
    parser.add_argument(
        "--output-dir",
        type=str,
        default=None,
        metavar="PATH",
        help="Directory containing solver output (MECSattimestep.txt, RPLSattimestep.txt, gamma.txt, mote_metrics.txt, etc.). "
             "Defaults to output_dir relative to script. When called by SolvePOMDP.java, this is set from solver.config outputDirectory.",
    )
    parser.add_argument(
        "--mec-threshold",
        type=float,
        default=20.0,
        metavar="VALUE",
        help="MEC threshold from solver.config (horizontal line on MEC satisfaction plot). Default 20.",
    )
    parser.add_argument(
        "--rpl-threshold",
        type=float,
        default=0.2,
        metavar="VALUE",
        help="RPL threshold from solver.config (horizontal line on RPL satisfaction plot). Default 0.2.",
    )
    args = parser.parse_args()
    run(data_dir=args.output_dir, mec_threshold=args.mec_threshold, rpl_threshold=args.rpl_threshold)
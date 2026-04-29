"""
plotStateTransitions.py — animate per-mote POMDP state-transition flows.

Usage:
    python plotStateTransitions.py --output-dir <dir> [--motes 3,5,7] [--window 20] [--out-subdir state_transitions]

Reads state_transitions.txt produced by SolvePOMDP.runCaseIoT and writes one
animated HTML per selected mote.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
WHAT THE DIAGRAM SHOWS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Each animated frame represents a sliding window of W timesteps (default 20)
centred at a particular timestep t. The window slides one step at a time across
the full 500-step run; advancing the animation frame is equivalent to asking
"what was the mote's behaviour in the 20 steps surrounding timestep t?"

The main panel shows a 4x3 grid of nodes:

    t-1         t          t+1
    [S0]  -->  [S0]  -->  [S0]
    [S1]  -->  [S1]  -->  [S1]
    [S2]  -->  [S2]  -->  [S2]
    [S3]  -->  [S3]  -->  [S3]

The four rows correspond to the four POMDP states for this mote:

    S0 — MEC satisfied  AND  RPL satisfied   (both NFRs met: ideal)
    S1 — MEC satisfied  BUT  RPL violated    (energy ok, packet loss too high)
    S2 — MEC violated   BUT  RPL satisfied   (energy too high, packet loss ok)
    S3 — MEC violated   AND  RPL violated    (both NFRs violated: worst case)

"MEC satisfied" means energy consumption < mecThreshold (default 20 C).
"RPL satisfied" means packet loss ratio < rplThreshold (default 0.20).

The three columns represent time: the left column is the pre-action state
(t-1), the middle column is the post-action state captured at the current
window centre (t), and the right column is the post-action state one step
later (t+1). Together they show one full action-consequence-consequence chain
as experienced by this mote.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
NODE COLOURS — BELIEF MASS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Each node is coloured by the mean belief b_i across all timesteps in the
current window, using the Viridis colourscale:

    Dark purple  →  belief ≈ 0.0  (solver assigns almost no probability to this state)
    Teal/green   →  belief ≈ 0.5
    Bright yellow →  belief ≈ 1.0  (solver is highly confident this state is active)

The four belief values in each window sum to approximately 1.0 (they are a
probability distribution over states). A node that is bright yellow while its
siblings are dark purple means the solver is nearly certain of the current NFR
status. A window where all four nodes are mid-range teal indicates high
uncertainty — the solver cannot distinguish between states.

Important: belief is the solver's internal estimate of where the system is,
not a direct measurement. It is updated by the POMDP's transition and
observation model after each action. Belief and actual state can disagree,
especially during transient events like link failures.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
EDGE LINES — TRANSITION FREQUENCY
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Lines connecting nodes across columns represent observed state transitions
within the current window. Both thickness and opacity encode frequency:

    Thin, faint line   →  rare transition (occurred ≤ ~15% as often as the
                          most common transition in this window)
    Thick, solid line  →  dominant transition (the most frequent path taken)

Frequency is normalised per window: only the relative dominance within a
window matters, not the absolute count. This means the thickest line in every
frame always represents "most common for this mote in this 20-step period",
regardless of how frequently transitions actually occurred.

The two legs encode different things:

- **Left leg (t-1 → t):** preState → postState within the same timestep.
  Shows the direct effect of the action: what state the mote was in before
  the POMDP chose an action, and what state it was in after.

- **Right leg (t → t+1):** postState[t] → postState[t+1] across consecutive
  timesteps. Shows inter-timestep dynamics: once an action has been applied
  and a new state reached, where does the mote end up at the next step?
  This leg captures carry-over effects, environment drift, and whether the
  post-action state is stable.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
WHY MULTIPLE LINES CAN LEAVE ONE NODE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
The mote does not always transition to the same successor state after the
same action, for two reasons:

1. Stochastic environment. Radio channel quality, interference, and link
   load are noisy. The same power setting applied to the same state can yield
   different packet-loss outcomes across timesteps.

2. Stochastic policy (ERPerseus only). ERPerseus selects actions using a
   softmax distribution over Q-values rather than the deterministic argmax
   used by Perseus. Even given an identical belief state, it will sometimes
   choose DTP and sometimes ITP. Different actions lead to different successor
   states, producing multiple outgoing edges even for the same source state.

Under Perseus, the policy is deterministic: the same belief → the same action
→ a narrower fan of successor states (primarily driven by environment noise
alone). If Perseus locks into a suboptimal state, the diagram will show one or
two very thick lines cycling repeatedly between the same two states, with all
other edges near-invisible.

Under ERPerseus, the entropy-regularised policy deliberately spreads
probability mass across actions, which spreads successor states further. A
healthy ERPerseus run shows multiple moderate-weight edges leaving each source
node, indicating the mote is exploring the state space rather than committed
to a single loop.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
ACTION RATIO BAR — DTP vs ITP
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
The stacked bar chart below the node graph shows what fraction of timesteps
in the current window used each action:

    Blue (DTP)  — Decrease Transmission Power: reduces power by 1 step,
                  reduces spreading factor if SF > 7. Trades reliability for
                  energy saving. Tends to move the mote toward MEC satisfaction.

    Red  (ITP)  — Increase Transmission Power: increases power by 1 step,
                  increases spreading factor if SF < 12. Trades energy for
                  reliability. Tends to move the mote toward RPL satisfaction.

The bar is shown for three columns (t-1, t, t+1) but uses the same window
statistics for all three — it summarises the overall action mix in this window
rather than per-column breakdown. A bar that is nearly all blue in every frame
indicates the solver consistently chose DTP throughout this period; a mixed
bar indicates the policy was alternating.

Correlate the bar with the dominant edge: if the bar is 100% DTP and the
dominant edge is S0→S1 (MEC fine, RPL degrading), the mote is trapped in a
loop where reducing power repeatedly increases packet loss but the policy
cannot escape because Perseus has no mechanism for exploration.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
HOW TO READ THE DIAGRAM TO DRAW CONCLUSIONS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Step 1 — Identify dominant edges.
  Press Play and watch which lines stay thick across many consecutive frames.
  A thick edge that persists for 50+ frames (50+ timesteps) represents a
  stable behavioural pattern, not noise.

Step 2 — Check for 2-cycles.
  A 2-cycle (local optimum / feedback loop) appears as a pair of thick edges
  that mirror each other across the two column boundaries: e.g. S1→S2 on the
  left leg AND S2→S1 on the right leg. This means the mote oscillates between
  two states indefinitely. The action bar will typically be dominated by one
  action during the corresponding frames.

Step 3 — Assess belief confidence alongside transitions.
  If the mote is in a 2-cycle AND the corresponding nodes are bright yellow,
  the solver is both confident and stuck — it believes it knows where it is
  but the policy cannot improve. If the nodes are teal/purple, the solver is
  uncertain — the cycle may resolve once belief updates.

Step 4 — Look for breakout moments.
  When comparing Perseus vs ERPerseus for the same mote: find the frame range
  where Perseus shows a stable 2-cycle (thick mirrored edges) and check
  whether ERPerseus shows a diffuse fan at the same timestep window. If
  ERPerseus's edges spread to S0 from S1/S2, the entropy-regularised policy
  successfully escaped the local optimum that trapped Perseus.

Step 5 — Cross-reference with QoS.
  The timestep shown in the slider corresponds directly to rows in
  MECSattimestep.txt and RPLSattimestep.txt. If a 2-cycle coincides with
  sustained high energy (ec > mecThreshold) or high packet loss
  (pl > rplThreshold) in those files, it is a confirmed suboptimal loop with
  real NFR impact, not a benign oscillation in the satisfied region.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
DATA SOURCE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Input file: state_transitions.txt (whitespace-delimited, no header)
Columns: timestep  moteId  preState  action  postState  b0  b1  b2  b3

  timestep  — simulation timestep (0–499)
  moteId    — index of the mote in the network
  preState  — discrete POMDP state before performAction() (0–3)
  action    — action taken: 0=DTP, 1=ITP
  postState — discrete POMDP state after performAction() + doSingleRun()
  b0..b3    — belief distribution at pre-action point (sum ≈ 1.0)

One row is written per (mote, timestep) in SolvePOMDP.runCaseIoT, inside the
MAPE-K MONITOR phase, immediately after doSingleRun() completes.
"""

import argparse
import os
import sys
import numpy as np
import pandas as pd
import plotly.graph_objects as go
from loguru import logger

logger.remove()
logger.add(sys.stderr, level="INFO",
           format="<green>{time:HH:mm:ss}</green> | <level>{level: <8}</level> | <level>{message}</level>")

STATE_LABELS = ["S0\n(MEC✓ RPL✓)", "S1\n(MEC✓ RPL✗)", "S2\n(MEC✗ RPL✓)", "S3\n(MEC✗ RPL✗)"]
STATE_SHORT  = ["S0", "S1", "S2", "S3"]
N_STATES     = 4
COL_POSITIONS = [-1, 0, 1]   # x-coords for t-1, t, t+1 columns
ACTION_COLORS = ["#2196F3", "#F44336"]   # DTP=blue, ITP=red
ACTION_LABELS = ["DTP", "ITP"]


def load_data(output_dir: str) -> pd.DataFrame:
    path = os.path.join(output_dir, "state_transitions.txt")
    if not os.path.exists(path):
        logger.error("state_transitions.txt not found in {}", output_dir)
        sys.exit(1)
    df = pd.read_csv(path, sep=r"\s+", header=None,
                     names=["timestep", "moteId", "preState", "action", "postState",
                            "b0", "b1", "b2", "b3"])
    logger.info("Loaded {} rows from {}", len(df), path)
    return df


def build_node_positions():
    """Return (x, y, col_label) for the 12 nodes: 4 states × 3 time columns."""
    xs, ys, labels = [], [], []
    for col_idx, col_x in enumerate(COL_POSITIONS):
        for s in range(N_STATES):
            xs.append(col_x)
            ys.append(-s)            # states top→bottom
            labels.append(STATE_SHORT[s])
    return xs, ys, labels


def node_idx(col: int, state: int) -> int:
    """Map (col index 0/1/2, state 0-3) → flat node index."""
    return col * N_STATES + state


def compute_window_stats(mote_df: pd.DataFrame, centre: int, half: int):
    """
    Given a mote's full dataframe, compute statistics for the window
    centred at `centre` with half-width `half`.

    Returns:
        belief_mean  : (3, 4) float — mean belief per column per state
        trans_counts : (2, 4, 4) int — transition counts [t-1→t, t→t+1]
        action_frac  : (3, 2) float — DTP/ITP fraction per column
    """
    lo, hi = centre - half, centre + half
    win = mote_df[(mote_df["timestep"] >= lo) & (mote_df["timestep"] <= hi)]

    # Belief columns (b0..b3) — one row per timestep in window
    belief_cols = ["b0", "b1", "b2", "b3"]
    belief_mean = np.zeros((3, N_STATES))
    # t-1 column: data from previous timestep (preState perspective)
    # t   column: the current centre rows
    # t+1 column: postState perspective of current window

    centre_rows = win[win["timestep"] == centre]
    before_rows = win[win["timestep"] < centre]
    after_rows  = win[win["timestep"] > centre]

    # Use all rows in window for mean belief — belief is recorded at pre-action point
    if len(win) > 0:
        b_all = win[belief_cols].mean().values
    else:
        b_all = np.ones(N_STATES) / N_STATES

    # All three columns share the same belief distribution averaged over the window
    # (we only have one belief snapshot per row, not one per transition leg)
    for col in range(3):
        belief_mean[col] = b_all

    # Transition counts — two distinct edge sets:
    #   trans_counts[0]: left leg  — preState[t]  → postState[t]   (within-timestep action effect)
    #   trans_counts[1]: right leg — postState[t] → postState[t+1] (inter-timestep dynamics)
    # These must be built from different data so the 3-column layout shows a genuine chain.
    trans_counts = np.zeros((2, N_STATES, N_STATES), dtype=int)
    if len(win) > 0:
        win_sorted = win.sort_values("timestep").reset_index(drop=True)

        # Left leg: within-timestep effect of the action
        for _, row in win_sorted.iterrows():
            pre, post = int(row["preState"]), int(row["postState"])
            if 0 <= pre < N_STATES and 0 <= post < N_STATES:
                trans_counts[0, pre, post] += 1

        # Right leg: consecutive-timestep chain postState[t] → postState[t+1]
        for i in range(len(win_sorted) - 1):
            curr_post = int(win_sorted.loc[i,   "postState"])
            next_post = int(win_sorted.loc[i+1, "postState"])
            if 0 <= curr_post < N_STATES and 0 <= next_post < N_STATES:
                trans_counts[1, curr_post, next_post] += 1

    # Action fraction per column (DTP=0, ITP=1)
    action_frac = np.zeros((3, 2))
    n = len(win)
    if n > 0:
        dtp_frac = (win["action"] == 0).sum() / n
        itp_frac = 1.0 - dtp_frac
        for col in range(3):
            action_frac[col] = [dtp_frac, itp_frac]

    return belief_mean, trans_counts, action_frac


def make_mote_figure(mote_df: pd.DataFrame, mote_id: int, window: int) -> go.Figure:
    timesteps = sorted(mote_df["timestep"].unique())
    T = len(timesteps)
    half = window // 2

    centres = [t for t in timesteps if t - half >= timesteps[0] and t + half <= timesteps[-1]]
    if not centres:
        centres = timesteps[len(timesteps)//2: len(timesteps)//2 + 1]

    node_xs, node_ys, node_labels = build_node_positions()

    def make_frame_data(centre):
        belief_mean, trans_counts, action_frac = compute_window_stats(mote_df, centre, half)

        # --- Nodes ---
        node_colors = []
        node_text = []
        for col in range(3):
            for s in range(N_STATES):
                node_colors.append(belief_mean[col, s])
                node_text.append(f"{STATE_SHORT[s]}<br>b={belief_mean[col,s]:.2f}")

        # --- Edges between t-1→t and t→t+1 ---
        edge_shapes = []
        for edge_set in range(2):
            counts = trans_counts[edge_set]
            max_count = counts.max() if counts.max() > 0 else 1
            src_col = edge_set        # 0 = left column, 1 = middle column
            dst_col = edge_set + 1
            for pre in range(N_STATES):
                for post in range(N_STATES):
                    c = counts[pre, post]
                    if c == 0:
                        continue
                    opacity = 0.15 + 0.75 * (c / max_count)
                    width   = 1 + 5 * (c / max_count)
                    x0 = node_xs[node_idx(src_col, pre)]
                    y0 = node_ys[node_idx(src_col, pre)]
                    x1 = node_xs[node_idx(dst_col, post)]
                    y1 = node_ys[node_idx(dst_col, post)]
                    edge_shapes.append(dict(
                        type="line",
                        x0=x0 + 0.08, y0=y0,
                        x1=x1 - 0.08, y1=y1,
                        line=dict(color=f"rgba(120,120,120,{opacity:.2f})", width=width),
                        layer="below"
                    ))

        return node_colors, node_text, edge_shapes, action_frac

    # Build initial frame
    init_colors, init_text, init_shapes, init_action = make_frame_data(centres[0])

    # --- Action ratio bars (three groups, one per column) ---
    def action_bar_traces(action_frac, visible=True):
        traces = []
        col_names = ["t-1", "t", "t+1"]
        for a in range(2):
            traces.append(go.Bar(
                x=col_names,
                y=[action_frac[col, a] for col in range(3)],
                name=ACTION_LABELS[a],
                marker_color=ACTION_COLORS[a],
                opacity=0.8,
                showlegend=(a < 2),
                xaxis="x2",
                yaxis="y2",
                visible=visible,
            ))
        return traces

    # --- Node scatter ---
    node_scatter = go.Scatter(
        x=node_xs,
        y=node_ys,
        mode="markers+text",
        marker=dict(
            size=40,
            color=init_colors,
            colorscale="Viridis",
            cmin=0, cmax=1,
            colorbar=dict(title="Belief", thickness=12, len=0.6),
            line=dict(width=2, color="white"),
        ),
        text=node_labels,
        textposition="middle center",
        textfont=dict(color="white", size=12),
        hovertext=init_text,
        hoverinfo="text",
        showlegend=False,
        xaxis="x",
        yaxis="y",
    )

    action_bars = action_bar_traces(init_action)

    fig = go.Figure(data=[node_scatter] + action_bars)

    # Column header annotations (static)
    col_header_annotations = [
        dict(x=COL_POSITIONS[i], y=0.4, xref="x", yref="y",
             text=f"<b>{label}</b>", showarrow=False,
             font=dict(size=13))
        for i, label in enumerate(["t-1", "t", "t+1"])
    ]

    fig.update_layout(
        title=dict(text=f"State Transition Flow — Mote {mote_id}", x=0.5),
        shapes=init_shapes,
        annotations=col_header_annotations,
        xaxis=dict(range=[-1.6, 1.6], showgrid=False, zeroline=False,
                   showticklabels=False, domain=[0, 1]),
        yaxis=dict(range=[-3.6, 1.2], showgrid=False, zeroline=False,
                   showticklabels=False, domain=[0.25, 1]),
        xaxis2=dict(domain=[0.1, 0.9], anchor="y2",
                    showgrid=False, zeroline=False),
        yaxis2=dict(domain=[0, 0.18], anchor="x2", title="Action ratio",
                    range=[0, 1], showgrid=False),
        barmode="stack",
        plot_bgcolor="white",
        paper_bgcolor="white",
        height=750,
        width=800,
        margin=dict(t=80, b=30, l=20, r=20),
        updatemenus=[dict(
            type="buttons",
            showactive=False,
            y=1.1, x=0.5, xanchor="center",
            buttons=[
                dict(label="Play",
                     method="animate",
                     args=[None, dict(frame=dict(duration=200, redraw=True),
                                      fromcurrent=True, mode="immediate")]),
                dict(label="Pause",
                     method="animate",
                     args=[[None], dict(frame=dict(duration=0, redraw=False),
                                        mode="immediate")]),
            ]
        )],
        sliders=[dict(
            steps=[dict(
                args=[[str(c)], dict(frame=dict(duration=200, redraw=True),
                                      mode="immediate")],
                label=str(c),
                method="animate",
            ) for c in centres],
            currentvalue=dict(prefix="Window centre: ", font=dict(size=11)),
            x=0.05, len=0.9, y=-0.02,
        )],
    )

    # Build animation frames
    frames = []
    for centre in centres:
        colors, htext, shapes, afrac = make_frame_data(centre)
        bar_ys = [[afrac[col, a] for col in range(3)] for a in range(2)]
        frame = go.Frame(
            name=str(centre),
            data=[
                go.Scatter(
                    marker=dict(color=colors),
                    hovertext=htext,
                ),
                go.Bar(y=bar_ys[0]),
                go.Bar(y=bar_ys[1]),
            ],
            layout=go.Layout(shapes=shapes),
        )
        frames.append(frame)

    fig.frames = frames
    return fig


def main():
    parser = argparse.ArgumentParser(description="Animate per-mote POMDP state transitions.")
    parser.add_argument("--output-dir", required=True, help="Directory containing state_transitions.txt")
    parser.add_argument("--motes", default=None,
                        help="Comma-separated mote IDs to plot (default: all motes)")
    parser.add_argument("--window", type=int, default=20,
                        help="Sliding window size in timesteps (default: 20)")
    parser.add_argument("--out-subdir", default="state_transitions",
                        help="Subdirectory under --output-dir for HTML outputs (default: state_transitions)")
    args = parser.parse_args()

    df = load_data(args.output_dir)

    all_motes = sorted(df["moteId"].unique())
    if args.motes:
        selected = [int(m.strip()) for m in args.motes.split(",")]
    else:
        selected = all_motes

    out_dir = os.path.join(args.output_dir, args.out_subdir)
    os.makedirs(out_dir, exist_ok=True)

    for mote_id in selected:
        mote_df = df[df["moteId"] == mote_id].copy().sort_values("timestep")
        if mote_df.empty:
            logger.warning("No data for mote {}; skipping", mote_id)
            continue
        logger.info("Building animation for mote {} ({} rows)...", mote_id, len(mote_df))
        fig = make_mote_figure(mote_df, mote_id, args.window)
        out_path = os.path.join(out_dir, f"state_transitions_mote{mote_id}.html")
        fig.write_html(out_path, include_plotlyjs="cdn")
        logger.info("Written: {}", out_path)

    logger.info("Done. {} HTML files in {}", len(selected), out_dir)


if __name__ == "__main__":
    main()

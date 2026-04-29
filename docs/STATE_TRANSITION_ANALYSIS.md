# State Transition Analysis

This document describes the per-mote state-transition logging and animated
visualisation added to the DeltaIoT POMDP solver. It is primarily used to
detect suboptimal feedback loops in the adaptation policy and to compare the
dynamics of **Perseus** (deterministic) vs **ERPerseus** (entropy-regularised)
action selection.

---

## 1. What is logged and when

During `SolvePOMDP.runCaseIoT`, the MAPE-K Monitor phase (post-execution)
writes one row to `state_transitions.txt` for every (mote, timestep) pair:

```
timestep  moteId  preState  action  postState  b0  b1  b2  b3
```

| Column | Description |
|--------|-------------|
| `timestep` | Simulation timestep (0–499) |
| `moteId` | Index of the mote being adapted |
| `preState` | Discrete POMDP state **before** `performAction()` (0–3) |
| `action` | Action taken: `0` = DTP, `1` = ITP |
| `postState` | Discrete POMDP state **after** `performAction()` + `doSingleRun()` |
| `b0..b3` | Belief distribution at the pre-action point (sums to ≈ 1.0) |

The capture point is:
- `preState` = `pomdp.getCurrentState()` immediately **before** `performAction()`
- `postState` = `pomdp.getCurrentState()` immediately **after** `doSingleRun()`

This means each row records one complete MAPE-K action cycle for a single mote.

---

## 2. The four POMDP states

The POMDP models two binary NFR conditions (MEC = energy, RPL = packet loss),
giving four possible states:

| State | MEC | RPL | Meaning |
|-------|-----|-----|---------|
| **S0** | satisfied | satisfied | Both NFRs met — ideal operating point |
| **S1** | satisfied | **violated** | Energy ok but packet loss too high |
| **S2** | **violated** | satisfied | Packet loss ok but energy too high |
| **S3** | **violated** | **violated** | Both NFRs violated — worst case |

"MEC satisfied" means energy consumption < `mecThreshold` (default 20 C).  
"RPL satisfied" means packet loss ratio < `rplThreshold` (default 0.20).

---

## 3. Generating the animations

After running a simulation, call `plotStateTransitions.py` from the `L4Project`
directory:

```bash
# All motes, default window of 20 timesteps
python plotStateTransitions.py --output-dir output_dir/compare/erperseus

# Selected motes only
python plotStateTransitions.py --output-dir output_dir/compare/erperseus --motes 3,7,12

# Custom sliding window (larger = more smoothing, fewer frames)
python plotStateTransitions.py --output-dir output_dir/compare/erperseus --window 50
```

Output: one HTML file per mote in `<output-dir>/state_transitions/`,
e.g. `state_transitions_mote7.html`. Open in any browser — the file is
self-contained (plotly loaded from CDN).

---

## 4. Reading the diagram

Each animated HTML shows a **4×3 node graph** plus an **action ratio bar**.

### 4.1 The node graph

```
  t-1         t          t+1
  [S0]  -->  [S0]  -->  [S0]
  [S1]  -->  [S1]  -->  [S1]
  [S2]  -->  [S2]  -->  [S2]
  [S3]  -->  [S3]  -->  [S3]
```

- The **three columns** represent time: pre-action state (t-1), post-action
  state at the window centre (t), and post-action state one step later (t+1).
- Each **animation frame** corresponds to a sliding window of W timesteps
  (default 20) centred at a single timestep. Advancing the frame slides the
  window forward by one step.
- Hovering over a node shows the state label and mean belief value.

### 4.2 Node colours (Viridis colourscale)

Node colour encodes the **mean belief b_i** averaged over all timesteps in the
current window:

| Colour | Belief value | Interpretation |
|--------|-------------|----------------|
| Dark purple | ≈ 0.0 | Solver assigns near-zero probability to this state |
| Teal / green | ≈ 0.5 | Solver is uncertain |
| Bright yellow | ≈ 1.0 | Solver is highly confident this state is active |

The four node colours in any column sum to ≈ 1.0. When all four nodes are
mid-range teal, the solver has high uncertainty. When one node is bright yellow
and its siblings are dark, the solver believes the system is definitively in
that state.

**Note:** Belief is the solver's internal estimate, not a direct measurement.
It can lag or disagree with the actual state, especially after link failures or
sudden environment changes.

### 4.3 Edge lines (transition frequency)

Lines between columns represent **observed state transitions** within the
current window. Both **thickness** and **opacity** encode frequency, normalised
to the most common transition in that window:

| Line appearance | Frequency relative to window maximum |
|-----------------|--------------------------------------|
| Thin, near-invisible | ≤ ~15% as frequent as the dominant transition |
| Medium thickness | ~50% as frequent |
| Thick, opaque | The dominant transition (most frequent in this window) |

The two legs encode different things:

- **Left leg (t-1 → t):** `preState → postState` within the same timestep.
  A line from S_i to S_j means the action took the mote from state S_i to
  state S_j, with that frequency in this window. This shows the direct causal
  effect of the policy's action choice.

- **Right leg (t → t+1):** `postState[t] → postState[t+1]` across consecutive
  timesteps. A line from S_j to S_k means that after landing in state S_j,
  the mote was found in state S_k at the next timestep. This captures
  inter-timestep carry-over effects, environment drift, and whether a
  post-action state is stable or immediately transitions again.

### 4.4 Why multiple lines leave one node

Multiple outgoing edges from a single source state arise from two sources:

1. **Environmental stochasticity.** Radio channel quality, interference, and
   link load are noisy. The same power setting applied to the same state can
   produce different packet-loss outcomes across timesteps.

2. **Stochastic policy (ERPerseus only).** ERPerseus selects actions from a
   softmax distribution over Q-values. Even given the same belief state, it
   probabilistically chooses DTP or ITP. Different actions lead to different
   successor states, producing a wider fan of outgoing edges.

Under **Perseus** (deterministic argmax), only one action is ever chosen for a
given belief, so the fan of edges is narrower — primarily driven by environment
noise alone.

### 4.5 The action ratio bar

The stacked bar below the node graph shows the fraction of timesteps in the
current window that used each action:

- **Blue = DTP** (Decrease Transmission Power): reduces power by 1 step, lowers
  spreading factor if SF > 7. Trades link reliability for energy saving.
  Pushes toward MEC satisfaction; risks RPL violation.
- **Red = ITP** (Increase Transmission Power): increases power by 1 step,
  raises spreading factor if SF < 12. Trades energy for link reliability.
  Pushes toward RPL satisfaction; risks MEC violation.

The bar is shown for all three columns but reflects the same window statistics
throughout. Correlate the action bar with the dominant edge to confirm a loop:
a 100% blue bar alongside a thick S0→S1 edge means the mote is trapped in a
cycle where reducing power consistently degrades packet loss, and the
deterministic policy cannot escape.

---

## 5. Identifying suboptimal feedback loops

### Step 1 — Find persistent thick edges

Press **Play** and watch which lines remain thick across many consecutive
frames. A thick edge that persists for 50+ frames (50+ timesteps) is a stable
behavioural pattern, not noise.

### Step 2 — Look for 2-cycles

A **2-cycle** (the canonical local optimum) appears as a pair of thick edges
that mirror each other across both column boundaries:

```
  t-1       t       t+1
  [Si] ---> [Sj] ---> [Si]
```

S_i → S_j on the left leg **and** S_j → S_i on the right leg, both thick.
This means the mote oscillates between two states indefinitely. The action bar
will typically be dominated by one action during the corresponding frames.

### Step 3 — Assess belief confidence

If the mote is cycling **and** the cycling nodes are bright yellow, the solver
is both confident and stuck: it correctly identifies where the system is, but
the policy cannot escape. If the cycling nodes are teal/purple, uncertainty is
high and the cycle may resolve as the belief updates.

### Step 4 — Identify breakout moments (Perseus vs ERPerseus comparison)

Open the same mote's HTML from both algorithm runs side by side. Find the
frame range where Perseus shows a stable 2-cycle (thick mirrored edges). Check
ERPerseus at the same window centre:

- **If ERPerseus shows a diffuse fan** (several moderate-weight edges to
  different successors) where Perseus shows a thick cycle: the entropy
  regularisation successfully escaped the local optimum.
- **If both show a thick cycle**: the loop is driven by environment dynamics
  (state S_i genuinely transitions to S_j regardless of action), not by the
  policy choice.

### Step 5 — Cross-reference with QoS files

The slider timestamp maps directly to rows in:

- `MECSattimestep.txt` — column 2 = energy consumption (lower is better)
- `RPLSattimestep.txt` — column 2 = packet loss ratio (lower is better)

If a 2-cycle coincides with sustained values above `mecThreshold` (20 C) or
`rplThreshold` (0.20) in those files, it is a **confirmed suboptimal loop with
real NFR impact**, not a benign oscillation in the satisfied region (e.g. a
loop within S0 or between S0 and S1 where both states still satisfy MEC).

---

## 6. Comparing Perseus vs ERPerseus

Recommended experiment protocol for this comparison:

```bash
# Create configs
python3 -c "
import sys; sys.path.insert(0, 'scripts')
from config_utils import load_config_template, set_config_value
from pathlib import Path
root = Path('.')
for algo in ['perseus', 'erperseus']:
    lines = load_config_template(root)
    for k, v in [('algorithmType', algo),
                 ('outputDirectory', f'output_dir/compare/{algo}'),
                 ('runSeed', '222'), ('p_c', '0.5'), ('lookback', '4')]:
        lines = set_config_value(lines, k, v)
    out = Path(f'output_dir/compare/{algo}.config')
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(''.join(lines))
"

# Run Perseus then ERPerseus (Windows classpath separator is ;)
java "-DconfigPath=$(pwd)/output_dir/compare/perseus.config"   -DnoPlots=true -cp ".;bin;libraries/*" main.SolvePOMDP
java "-DconfigPath=$(pwd)/output_dir/compare/erperseus.config" -DnoPlots=true -cp ".;bin;libraries/*" main.SolvePOMDP

# Generate animations
python plotStateTransitions.py --output-dir output_dir/compare/perseus   --window 20
python plotStateTransitions.py --output-dir output_dir/compare/erperseus --window 20
```

**What to expect:**

| Visual feature | Perseus (deterministic) | ERPerseus (stochastic softmax) |
|---------------|------------------------|-------------------------------|
| Number of distinct outgoing edges | 1–2 dominant, rest invisible | 2–4 with moderate weights |
| Action ratio bar | Near 100% one colour during cycles | Mixed blue/red throughout |
| Belief node colours during cycles | Bright yellow (high confidence) | Broader spread across nodes |
| 2-cycle persistence | Many consecutive frames | Shorter, followed by breakout |
| NFR impact | Cycles correlate with NFR violations | Escapes to S0 more frequently |

---

## 7. Slider and animation controls

| Control | Function |
|---------|----------|
| **Play / Pause** buttons | Autoplay at 200 ms/frame; pause to inspect a specific window |
| **Slider** | Scrub to any window centre manually |
| **Hover** | Shows state label and mean belief value for any node |

The slider label shows the **window centre timestep**, not the window range.
For a window of 20, the frame labelled "t=150" aggregates data from timesteps
140–160.

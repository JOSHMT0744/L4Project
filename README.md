# Surprise-based BA-POMDP: Entropy-Regularized Value Iteration with Adaptive Learning

POMDP-based adaptation for IoT networks: entropy-regularized solvers (ERPerseus, ERPBVI) plus **varSMiLE** (surprise-driven learning of T,O) and **MIS** (Mutual Information Surprise) for adaptive gamma. Goals: minimize energy (MEC) and packet loss (RPL).

## Architecture

DeltaIoT is a sensor network of **Heat**, **PIR**, and **RFID** nodes with a **Gateway**. Data flows from sensors toward the gateway; the POMDP plans DTP/ITP actions to tune power and spreading factor.

![DeltaIoT architecture: sensor network and data flow to the Gateway](docs/DeltaIoT-Abstraction.jpg)

## Prerequisites

- **JDK 8+**
- **Python 3.7+** (for `createCharts.py`)
- **DeltaIoT / Simulator JARs** in `libraries/` (and any deps, e.g. `json-simple`). Ensure `Simulator.jar` and `deltaiot` JARs are on the classpath.

## Machine setup (development and run environment)

This project was developed and run on the following machine. You can update this section with your own environment for reproducibility.

| Item | Details |
|------|---------|
| **OS** | Windows 10 (build 26100) |
| **CPU** | x64 |
| **RAM** | 16 GB |
| **JDK** | OpenJDK 8+ or Oracle JDK 17 (e.g. `java -version` → 17.x) |
| **Python** | 3.7+ (e.g. 3.10 or 3.11 with venv) |
| **Working directory** | Project root `L4Project` (or workspace root; see Run on a Local Machine) |

- **Notes:** A single full run (500 timesteps, default belief sampling) typically completes in a few minutes.

## Run on a Local Machine

### 1. Clone and go to the project

```bash
cd /path/to/workspace/L4Project
```

### 2. Python env (for charts)

```bash
python -m venv .venv
# Windows:  .venv\Scripts\activate
# Linux/Mac:  source .venv/bin/activate
pip install -r requirements.txt
```

### 3. Solver and domain

- **Algorithm:** `src/solver.config` → `algorithmType=erperseus` (or `perseus`, `erpbvi`, `faserpbvi`).
- **Domain:** `domains/IoT.POMDP` (or `IoT2.POMDP` as wired in code).

### 4. Run

**From Eclipse**

1. Import `L4Project`, add `libraries/*` to the build path.
2. Run **`main.SolvePOMDP`** (Run As → Java Application, working dir = project root).

**From command line**

```bash
cd L4Project

# Compile (adjust to your layout; include all src and libraries)
javac -cp "libraries/*" -d bin -sourcepath src src/main/*.java src/pomdp/*.java src/solver/*.java src/iot/*.java

# Run (Windows use ;  Linux/Mac use :)
java -cp "bin;libraries/*" main.SolvePOMDP
```

### 5. Output and charts

- **Dir:** `output_dir/`
- **Main files:** `gamma.txt`, `surpriseMIS.txt`, `MISBounds.txt`, `MECSattimestep.txt`, `RPLSattimestep.txt`, `SelectedAction.txt`, `mote_metrics.txt`, etc.
- **Charts:** After a run, `createCharts.py` is invoked automatically. To run by hand:

  ```bash
  python createCharts.py
  ```

## Configuration (`src/solver.config`)

All hyperparameters are configured in `src/solver.config`. The following parameters are available:

### General Settings

| Setting | Role | Default |
|--------|------|---------|
| `algorithmType` | Solver algorithm: `erperseus`, `perseus`, `erpbvi`, `faserpbvi` | `erperseus` |
| `lambda` | Entropy regularization temperature (e.g. 0.5–1.0 for ERPerseus); only used by ER solvers | `1.0` |
| `outputDirectory` | Directory for output files | `output_dir` |
| `domainDirectory` | Directory containing `.POMDP` files | `domains` |
| `valueFunctionTolerance` | Convergence threshold (algorithm stops when value difference < tolerance) | `0.000001` |
| `timeLimit` | Maximum runtime in seconds | `1000` |

### Approximate Algorithm Settings

| Setting | Role | Default |
|--------|------|---------|
| `beliefSamplingRuns` | Number of belief sampling runs for approximate solvers | `10` |
| `beliefSamplingSteps` | Number of steps per belief sampling run | `200` |

### Exact Algorithm Settings

| Setting | Role | Default |
|--------|------|---------|
| `lpsolver` | LP solver: `gurobi`, `joptimizer`, `lpsolve` | `lpsolve` |
| `pruningMethod` | POMDP pruning: `standard`, `accelerated` | `accelerated` |
| `epsilon` | Minimum value improvement to add vectors | `0.000001` |
| `acceleratedLPThreshold` | Use accelerated LP when vector count exceeds this | `200` |
| `acceleratedTolerance` | Convergence threshold for accelerated LP | `0.0001` |
| `coefficientThreshold` | Discard LP coefficients below this (numerical stability) | `0.000000001` |

### Experiment Parameters (Optional)

These hyperparameters control the learning and adaptation behavior:

| Setting | Role | Default |
|--------|------|---------|
| `runSeed` | Base random seed for solver, mote order, and policy (vary for repeated runs, e.g. 222, 223, ...) | `222` |
| `surpriseMeasureForGamma` | Surprise measure for varSMiLE gamma: `CC` (Confidence-Corrected), `BF` (Bayes Factor), or `MIS` (Mutual Information Surprise) | `MIS` |
| `p_c` | Probability of change in (0,1) for varSMiLE gamma; controls `m = p_c/(1-p_c)` | `0.5` |
| `useSurpriseUpdating` | `true` = varSMiLE (surprise-weighted) updates; `false` = classic Bayesian (Dirichlet +1 only) | `true` |
| `lookback` | Lookback period (m) for MIS calculation. Number of timesteps to look back when computing `MIS = MI[current] - MI[current - lookback]` | `4` |

### Link Failure Injection (Optional)

| Setting | Role | Default |
|--------|------|---------|
| `linkFailureTimestep` | Timestep at which to turn off listed links (omit or leave empty to disable) | - |
| `linkFailureLinks` | Comma-separated list of source-dest link IDs, e.g. `12-7, 12-3` | - |
| `linkRecoveryTimestep` | Timestep at which to turn the same links back on (omit or leave empty for no recovery) | - |

### Output Files

| Setting | Role | Default |
|--------|------|---------|
| `dumpPolicyGraph` | Dump policy graph after convergence (only for exact method) | `false` |
| `dumpActionLabels` | Use action labels instead of numbers in output files | `true` |

**Note:** All hyperparameters that were previously hardcoded in the Java source code have been moved to `solver.config` for easier experimentation and reproducibility. Modify `solver.config` before running to change any of these parameters.

## Main Output Files

| File | Content |
|------|---------|
| `gamma.txt` | varSMiLE mixing factor per mote/timestep |
| `surpriseCC.txt`, `surpriseBF.txt`, `surpriseMIS.txt` | Surprise measures per mote |
| `MISBounds.txt` | MIS 95% bounds |
| `MECSattimestep.txt`, `RPLSattimestep.txt` | Gateway QoS (energy, packet loss) per timestep |
| `SelectedAction.txt` | DTP/ITP action per timestep |
| `mote_metrics.txt` | Per-mote/per-link SNR, power, distribution, SF |
| `IoT.alpha` | Alpha vectors of the solved value function |

## Troubleshooting

- **`solver.config` or `domains/IoT.POMDP` not found**  
  Run from `L4Project` (project root).

- **ClassNotFoundException / missing Simulator or deltaiot**  
  Add `Simulator.jar`, `deltaiot` JARs (and deps) to the classpath, e.g. in `libraries/`.

- **Charts not opening**  
  Activate the venv, `pip install -r requirements.txt`, and run `python createCharts.py` after a full run so `output_dir/` is populated.

- **LP solver errors**  
  Use `lpsolver=lpsolve` in `solver.config` if you do not have Gurobi.

## Algorithm and Code (short)

- **ERPerseus:** `src/solver/ERPerseus.java` — Perseus with softmax over actions and value vectors (λ).
- **varSMiLE:** `DeltaIOTConnector.updateTransitionBelief` — gamma from surprise (CC, BF, or MIS); `transitionBeliefCurr` and `observationBelief` drive `getTransitionProbability` / `getObservationProbability`.
- **Surprise / MIS:** `DeltaIOTConnector` (e.g. `confidenceCorrectedSurprise`, `bayesFactorSurprise`, `calculateAndStoreMIS`); MIS bounds follow the 95% interval in the MIS surprise paper.

## References

- *Mutual Information Surprise* — [arXiv:2508.17403](https://www.arxiv.org/pdf/2508.17403)
- *Perseus* — Spaan & Vlassis, JAIR 2005
- *Entropy-regularized PBVI* — Delecki et al., arXiv:2402.09388
- *varSMiLE* — Liakoni et al., Neural Computation 2021

## License

GPL-3.0. Based on SolvePOMDP by Erwin Walraven (TU Delft); extended for IoT and varSMiLE/ER.

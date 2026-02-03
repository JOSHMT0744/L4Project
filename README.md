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

| Setting | Role |
|--------|------|
| `algorithmType` | `erperseus`, `perseus`, `erpbvi`, `faserpbvi` |
| `lambda` | ER temperature (e.g. 0.5–1.0 for ERPerseus); only used by ER solvers |
| `outputDirectory` | Default `output_dir` |
| `domainDirectory` | Default `domains` |
| `beliefSamplingRuns`, `beliefSamplingSteps` | Belief set size for approximate solvers |
| `valueFunctionTolerance`, `timeLimit` | Stopping conditions |
| **Experiment (optional)** | |
| `surpriseMeasureForGamma` | `CC`, `BF`, or `MIS` (default `MIS`) |
| `p_c` | Probability of change in (0,1) for varSMiLE γ (default `0.5`) |
| `runSeed` | Base random seed for reproducibility (default `222`; use different values for repeated runs) |

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

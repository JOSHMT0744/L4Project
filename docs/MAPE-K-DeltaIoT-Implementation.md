# MAPE-K Implementation for DeltaIoT in SolvePOMDP

This document describes how the **MAPE-K** (Monitor–Analyse–Plan–Execute with shared Knowledge) autonomic control loop is implemented in the DeltaIoT adaptation pipeline within `SolvePOMDP.runCaseIoT()`. The implementation coordinates POMDP-based decision-making with a network simulator to adapt transmission power (DTP/ITP) per mote over discrete timesteps.

---

## 1. Overview

MAPE-K is an architectural pattern for self-adaptive systems. In this implementation:

- **Monitor** gathers the current network and POMDP state (motes, QoS, belief).
- **Analyse** computes belief-state satisfaction probabilities (MEC, RPL).
- **Plan** solves the POMDP and selects an action (decrease/increase transmission power) from the value function.
- **Execute** applies the chosen action (DTP or ITP) and updates the POMDP belief and transition/observation models.
- **Knowledge** is the shared state: POMDP (beliefs, states, transitions), network topology (motes, links), simulator runs, and configuration (noise injector, output directory).

The loop runs over **timesteps** (e.g. 300) and, within each timestep, over **motes** in a randomised order. Each mote undergoes one full MAPE-K cycle; the **baseline** for each mote is implicitly the **previous mote’s post-action simulation run** (or the previous timestep’s last run for the first mote).

---

## 2. Initialisation and Knowledge

Before the main loop, the following are set up and form the shared Knowledge:

- **POMDP**: Loaded from file; defines states, actions, rewards, and (with the connector) transition/observation dynamics.
- **DeltaIOTConnector**: Holds the simulator client (`networkMgmt`), the selected mote and link, the noise injector, and output paths. The **active instance** is set so that `POMDP.nextState()` uses the same connector (and thus the same noise and topology).
- **NoiseInjector**: Configurable link/mote failures and random seed; failure states are updated at the start of each timestep.
- **Solver**: Value function (e.g. ERPBVI or ERPerseus) and policy (e.g. ERPolicy for softmax action selection).
- **Run index** `timestepiot`: Counts simulator runs (1 run per mote per timestep, post-action). It is initialised to 0 and incremented after each `doSingleRun()`.
- **Timestep index** `timestep`: Monotonic discrete time (0 to `numTimesteps-1`).

Output writers log satisfaction probabilities (MEC/RPL), selected actions, and QoS (packet loss, energy) at mote and timestep level.

---

## 3. Per-Timestep Setup (Monitor at Timestep Level)

At the start of each timestep:

1. **Failure update**: If the noise injector is enabled, `updateFailures(motes, timestep)` updates which links/motes are failed for this timestep.
2. **Network state**: `motes = networkMgmt.getProbe().getAllMotes()` refreshes the list of motes from the simulator (Monitor).
3. **POMDP state**:  
   - For timestep 0, the current POMDP state is set to a default (0).  
   - For timestep > 0, `currState = pomdp.getInitialState()` is used. That method obtains QoS for **run 1** (via `waitForQoSDataReady(1, ...)`) and maps packet loss and energy consumption to one of four discrete states (see Section 6).  
   So the “initial” state for the timestep is derived from a reference run (run 1), and `pomdp.setCurrentState(currState)` sets the POMDP state for the coming mote loop.
4. **Mote order**: Mote indices are shuffled with a fixed seed (`222 + timestep`) so the order is deterministic but varies by timestep (Fisher–Yates). Failed motes are skipped in the loop.

This establishes the shared Knowledge (network + POMDP state) for the upcoming per-mote MAPE-K cycles.

---

## 4. Per-Mote MAPE-K Cycle

For each mote (in the shuffled order), one full MAPE-K cycle is performed. Failed motes are skipped.

### 4.1 Monitor (implicit baseline)

The baseline for the **current** mote is not re-measured explicitly; it is **implicit**:

- For the **first** mote in the loop: baseline is the previous timestep’s last post-action run (or default state at timestep 0).
- For **subsequent** motes: baseline is the **previous mote’s post-action** simulation run.

That run is exactly the one indicated by `timestepiot` at the start of this mote’s cycle (before `doSingleRun()` is called for the current mote). So “Monitor” here means: the system treats the state implied by that run (and the current POMDP belief/state) as the observed context.

### 4.2 Analyse

- **Belief**: `initialbelief = pomdp.getInitialBelief()` (denoted \(b_0\) in comments). This is the current belief over the four POMDP states.
- **Satisfaction probabilities** (for logging and interpretation):
  - MEC satisfaction probability: \(P(\text{MEC sat}) = b[0] + b[1]\).
  - RPL satisfaction probability: \(P(\text{RPL sat}) = b[0] + b[2]\).
- These and the belief vector are written to the MECSatProb and RPLSatProb logs.

So Analyse converts the shared belief (Knowledge) into satisfaction metrics used for logging and analysis.

### 4.3 Plan

- **Value function**: `V1 = solver.solve(pomdp)` returns a set of alpha-vectors. Each vector defines a linear function over the belief; the value at belief \(b\) is the dot product of the vector with \(b\).
- **Action selection**:
  - If the solver is **ERPBVI**: an `ERPolicy` is built from the POMDP and solver, and `selectAction(pomdp.getInitialBelief())` chooses an action (e.g. softmax over Q-values).
  - If the solver is **ERPerseus**: Q-functions are derived from the value function and lambda; again `ERPolicy` and `selectAction(initialBelief)` are used.
  - Otherwise: **greedy** selection via `AlphaVector.getBestVectorIndex(...)` and the action of the best alpha-vector.
- The chosen **action** (0 = DTP, 1 = ITP) is logged to the SelectedAction writer.

So Plan uses the shared POMDP and value function (Knowledge) plus the current belief to decide the next adaptation action.

### 4.4 Execute

- **Action application**: `deltaConnector.performAction(selectedAction)` is called. Inside:
  - `nextstate = p.nextState(p.getCurrentState(), action)`:
    - Calls `performDTP()` or `performITP()` on the connector, which change transmission power (and link distribution) on the simulator for the selected mote.
    - Then obtains QoS for the **baseline run** (the run index given by `timestepiot` at that moment) via `waitForQoSDataReady(currentRun, ...)` and maps packet loss and energy to the next discrete state \(s'\).
  - `p.setCurrentState(nextstate)`.
  - Transition and observation beliefs are updated from the action and outcome (`updateTransitionBelief`, `updateObservationBelief`).
  - Belief update: `newBelief = p.updateBelief(p.getInitialBelief(), action, obs)` and `p.setInitialBelief(newBelief)`.
- After `performAction`, the local `pomdp` reference is refreshed from `DeltaIOTConnector.p` so the rest of the loop sees the updated POMDP (state, belief, and transition/observation models).

So Execute: (1) changes the network (DTP/ITP), (2) uses the baseline run to compute the next state, and (3) updates the POMDP belief and internal models (Knowledge).

### 4.5 Monitor (post-execution measurement)

- **Simulation**: `networkMgmt.getSimulator().doSingleRun()` runs one simulation with the new configuration. This produces a new run (packet flow, aggregation at gateways).
- **Run index**: `timestepiot` is incremented **after** `doSingleRun()`, so it always points to the run that has just been created (post-action for this mote).
- **QoS retrieval**: `waitForQoSDataReady(currentRun, 50, 200)` (with `currentRun = timestepiot`) ensures QoS for that run is available. Packet loss and energy are read from the last entry in the returned list.
- **Logging**: These values are written to MECSat (energy) and RPLSat (packet loss) and to the per-timestep files at the end of the timestep.

This post-execution step **measures** the effect of the action. The resulting run becomes the **baseline** for the **next** mote (or for the next timestep if this was the last mote). Thus the “Monitor” phase that closes the loop is the combination of (1) running the simulation and (2) reading and logging QoS for the new run.

---

## 5. Timestep-Level Logging and Metrics

After all motes in a timestep have been processed:

- Motes are re-fetched from the probe and per-mote/link metrics are logged via `logMoteAndLinkMetrics`.
- The simulator’s full list of QoS values is read; the **last** entry gives network-wide packet loss and energy for the timestep. These are appended to MECSattimestep and RPLSattimestep files.
- Failure statistics from the noise injector are printed.

This completes the timestep and the next iteration of the outer loop starts (new timestep, new failure update, new mote order).

---

## 6. State and Observation Encoding (Knowledge)

The POMDP uses **four discrete states** derived from QoS thresholds (in `POMDP.nextState()` and `getInitialState()`):

| State | Energy consumption | Packet loss |
|-------|--------------------|-------------|
| 0     | &lt; 20             | &lt; 0.20   |
| 1     | &lt; 20             | ≥ 0.20     |
| 2     | ≥ 20               | &lt; 0.20   |
| 3     | ≥ 20               | ≥ 0.20     |

- **MEC** (energy) is considered satisfied in states 0 and 1 (low energy).
- **RPL** (reliability/packet loss) is considered satisfied in states 0 and 2 (low packet loss).

Belief \(b\) is a distribution over these four states; the value function is piecewise linear in \(b\) (alpha-vectors). Actions 0 and 1 correspond to DTP (decrease transmission power) and ITP (increase transmission power) respectively.

---

## 7. Summary: MAPE-K Data Flow

- **Knowledge** is central: the POMDP (state, belief, dynamics), the connector (simulator, selected mote/link, noise), and the run index `timestepiot` are shared across Monitor–Analyse–Plan–Execute.
- **Monitor** (implicit): baseline = previous mote’s post-action run (or previous timestep / default). **Monitor** (explicit): after Execute, `doSingleRun()` plus `waitForQoSDataReady` and QoS extraction.
- **Analyse**: belief from POMDP, satisfaction probabilities (MEC/RPL) from belief.
- **Plan**: solve POMDP, then select action (softmax or greedy) from value function and current belief.
- **Execute**: `performAction` → DTP/ITP on simulator, next state from baseline run QoS, belief and transition/observation models updated.

The implementation thus realises a **sequential, per-mote MAPE-K loop** over time, with a single post-action simulation per mote per timestep and a clear separation of monitoring (QoS, runs), analysis (belief, satisfaction), planning (POMDP solver, policy), and execution (DTP/ITP, belief update), all sharing the same Knowledge representation.

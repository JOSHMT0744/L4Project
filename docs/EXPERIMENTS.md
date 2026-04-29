# Experiment Protocol for Academic Paper

This document describes how to **reliably test** the L4Project system for results in your academic paper, given:

- **Algorithm:** Perseus vs ERPerseus  
- **Surprise measure:** MIS vs CC  
- **p_c** ∈ (0, 1)  
- **lambda** ∈ [0, 10] (only affects ERPerseus (there is no actual upper bound for lambda))

---

## 1. Configurable Parameters

All experiment parameters are driven by **solver.config** (and optionally `-DconfigPath` for batch runs).

| Parameter | Config key | Range / values | Default |
|-----------|------------|----------------|---------|
| Algorithm | `algorithmType` | `perseus`, `erperseus` | `erperseus` |
| Surprise | `surpriseMeasureForGamma` | `CC`, `BF`, `MIS` | `MIS` |
| Volatility | `p_c` | (0, 1) | `0.5` |
| ER temperature | `lambda` | [0, 10] | `1.5` |
| Run seed | `runSeed` | integer | `222` |

- **runSeed:** Use different values (e.g. 222, 223, …) for **repeated runs**; same seed ⇒ reproducible run.  
- **p_c:** Only used when surprise-based learning is active; `m = p_c/(1−p_c)` in the γ formula.  
- **lambda:** Only used by ERPerseus (and ERPBVI); Perseus ignores it.

---

## 2. Single-Run Testing (Manual)

1. Edit **src/solver.config**: set `algorithmType`, `lambda`, and optionally `surpriseMeasureForGamma`, `p_c`, `runSeed`.  
2. Run from **L4Project** (or project root):

   ```bash
   # From L4Project (Windows; use : instead of ; on Linux/Mac for classpath)
   javac -cp "libraries/*" -d bin -sourcepath src src/main/*.java src/pomdp/*.java src/solver/*.java src/iot/*.java
   java -cp "bin;libraries/*" main.SolvePOMDP
   ```

3. Outputs appear in **outputDirectory** (default `output_dir/`):  
   - **MECSattimestep.txt**, **RPLSattimestep.txt** — one value per timestep (energy, packet loss).  
   - **gamma.txt**, **surpriseCC.txt**, **surpriseMIS.txt**, **surpriseBF.txt** — per mote/timestep.  
   - **SelectedAction.txt**, **MECSatProb.txt**, **RPLSatProb.txt**, etc.
   - **state_transitions.txt** — per-mote, per-timestep log of `preState action postState b0..b3`.
     Used by `plotStateTransitions.py` to generate animated state-transition diagrams.
     See [STATE_TRANSITION_ANALYSIS.md](STATE_TRANSITION_ANALYSIS.md) for full details.

4. (Optional) Generate animated state-transition diagrams after the run:
   ```bash
   python plotStateTransitions.py --output-dir output_dir --window 20
   ```
   Outputs one HTML per mote in `output_dir/state_transitions/`.

---

## 3. Recommended Design for the Paper

1. **Factors**
   - **Algorithm:** Perseus, ERPerseus  
   - **Surprise:** MIS, CC  
   - **p_c:** e.g. 0.25, 0.5, 0.75  
   - **λ:** e.g. 0, 1, 10 (for ERPerseus; Perseus as baseline with same list but λ ignored)

2. **Repeated runs**
   - Use **at least 3 runs of 500 timesteps** per configuration (e.g. `runSeed` = 222, 223, …, 231).  
   - Report **mean ± standard error** (or confidence interval) for MEC and RPL.

3. **Primary metrics**
   - **mean_MEC:** mean energy consumption over timesteps (from MECSattimestep.txt).  
   - **mean_RPL:** mean packet loss over timesteps (from RPLSattimestep.txt).  

4. **Reproducibility**
   - State **software version**, **numTimesteps** (500 in code), **noise/failure scenario** (e.g. link off at timestep 100).  
   - Provide **solver.config** (or a representative one)
   - Stating “each configuration was run with seeds 222–231” is enough for readers to reproduce if they use the same code and config.

---

## 5. Optional: Shorter Runs for Debugging

- **numTimesteps** is hardcoded (500) in **SolvePOMDP.java**. For faster debugging you can temporarily reduce it (e.g. 50), then restore 500 for final paper runs.  
# Results Section Plan — PhD Dissertation

This document outlines how to write the **Results** section for the dissertation, using data extracted from **`output_dir`**, with a **deep analysis** emphasis and a central comparison of **MIS vs CC/BF** (surprise measures).

---

## 1. Data Sources (from `output_dir`)

| File | Format | Use in Results |
|------|--------|----------------|
| **MECSattimestep.txt** | One line per timestep: `timestep energy_value` | Primary: mean MEC over time; trajectory; steady-state. |
| **RPLSattimestep.txt** | One line per timestep: `timestep packet_loss_value` | Primary: mean RPL over time; trajectory; steady-state. |
| **gamma.txt** | Per mote/timestep: `moteId timestep gamma` | Deep: γ dynamics; how much learning vs reset; compare MIS vs CC/BF. |
| **surpriseCC.txt** | `moteId timestep value` | Deep: CC surprise over time; correlation with γ and QoS. |
| **surpriseBF.txt** | `moteId timestep value` | Deep: BF surprise over time; compare with CC and MIS. |
| **surpriseMIS.txt** | `moteId timestep value` | Deep: MIS over time; enlightenment vs frustration; compare with CC/BF. |
| **MISBounds.txt** | `timestep lower upper` | Deep: MIS 95% bounds; when MIS leaves bounds (failure/change). |
| **SelectedAction.txt** | `timestep action` | Secondary: action distribution; DTP vs ITP balance. |
| **MECSatProb.txt**, **RPLSatProb.txt** | `moteIndex timestep prob` | Secondary: belief-state satisfaction over time. |
| **mote_metrics.txt** | `timestep moteId linkIndex source dest snr power distribution sf` | Secondary: link-level diagnostics; SNR/power/SF evolution. |
| **experiment_results.csv** (if using `run_experiments.py`) | `algorithm,surprise,p_c,lambda,run_id,mean_MEC,mean_RPL` | Aggregate: mean ± SE per configuration; statistical comparison. |

**Extraction:** Use Python (pandas) or R to read these files; normalise timestep alignment; aggregate per run, then across runs per (algorithm, surprise, p_c, lambda).

---

## 2. Overall Structure of the Results Section

Suggested order and flow:

1. **Experimental setup (brief recap)** — Factors (algorithm, surprise, p_c, λ), number of runs, seeds, scenario (e.g. link failure at t=100), numTimesteps.
2. **Primary outcomes: NFR satisfaction (MEC and RPL)** — Aggregate performance by configuration; main tables and figures; first pass at MIS vs CC/BF.
3. **Comparative analysis: MIS vs CC/BF** — Deep comparison of surprise measures: trajectories, γ behaviour, reaction to failure, statistical tests.
4. **Dynamics of surprise and γ** — How γ and surprise evolve over time; relationship to QoS; interpretation (reactive vs proactive).
5. **Sensitivity and robustness** — Effect of p_c, λ, algorithm; robustness across runs and scenarios.
6. **Optional: link-level and action-level analysis** — Mote metrics, action distribution; support for main narrative.

Each subsection should: state the question, show the data (table/figure), interpret, and tie back to research questions.

---

## 3. Subsection Plans

### 3.1 Experimental Setup (short)

**Purpose:** Reproducibility; reader knows exactly what was run.

**Content:**

- [ ] **Factors:** Algorithm (Perseus, ERPerseus), Surprise (MIS, CC; optionally BF), p_c (e.g. 0.25, 0.5, 0.75), λ (e.g. 0, 1, 10).
- [ ] **Runs:** Number of runs per configuration (e.g. 10), seed range (e.g. 222–231).
- [ ] **Scenario:** numTimesteps (300), failure/noise (e.g. link off at t=100), DeltaIoT topology.
- [ ] **Machine:** Brief (e.g. OS, RAM, JDK) or refer to README.
- [ ] **Output:** All metrics from `output_dir` (and, if used, `output_dir/exp_*/` and `experiment_results.csv`).

**Deliverable:** One short paragraph or small table; no deep analysis here.

---

### 3.2 Primary Outcomes: MEC and RPL

**Purpose:** Answer “How well does the system satisfy MEC and RPL?” and “Does configuration (algorithm, surprise, p_c, λ) matter?”

**Data extraction:**

- [ ] From **MECSattimestep.txt** and **RPLSattimestep.txt**: per run, compute **mean MEC** and **mean RPL** over all timesteps (and optionally: mean over second half for “steady-state”).
- [ ] If using batch runs: from **experiment_results.csv** or from scanning `output_dir/exp_*/`, aggregate by (algorithm, surprise, p_c, lambda): **mean ± SE** (or 95% CI) of mean_MEC and mean_RPL over run_id.

**Content:**

- [ ] **Table (primary):** Rows = configurations (e.g. Algorithm × Surprise × p_c × λ), columns = mean_MEC ± SE, mean_RPL ± SE, (optional) steady-state means.
- [ ] **Figure:** Bar or box plot of mean MEC and mean RPL by configuration; error bars = SE or CI; group by surprise (MIS vs CC/BF) to highlight main comparison.
- [ ] **Narrative:** Which configurations achieve best MEC/RPL; whether MIS vs CC/BF differs; whether ERPerseus vs Perseus matters; whether p_c or λ has a clear effect.
- [ ] **Research question:** Tie to “Does proactive (MIS) vs reactive (CC/BF) surprise improve NFR satisfaction?”

**Deliverables:** One main table; one or two figures; 1–2 paragraphs of interpretation.

---

### 3.3 Comparative Analysis: MIS vs CC/BF (deep)

**Purpose:** Core dissertation contribution — explain *how* and *why* MIS and CC/BF differ in effect (not only that they differ).

**Data extraction:**

- [ ] **Trajectories:** For selected configurations (e.g. ERPerseus, p_c=0.5, λ=1): load MECSattimestep, RPLSattimestep, gamma.txt, surpriseCC.txt, surpriseBF.txt, surpriseMIS.txt; align by timestep; optionally average over motes per timestep for γ and surprise.
- [ ] **Failure window:** Isolate timesteps around failure (e.g. t=100): compare MEC/RPL and γ/surprise before vs after for MIS vs CC/BF.
- [ ] **Summary stats:** Per run and configuration: mean γ, mean |MIS|, mean CC, mean BF; variance of γ over time; time to “recovery” after failure (e.g. first t where MEC or RPL returns within x% of pre-failure).

**Content:**

- [ ] **Hypothesis or question:** e.g. “MIS, as a proactive (epistemic) signal, leads to smoother γ and faster or more stable recovery after a failure than reactive CC/BF.”
- [ ] **Figure — trajectories:** Multi-panel: (a) MEC over time, (b) RPL over time, (c) γ over time, (d) surprise (MIS vs CC or BF) over time; 2–3 runs or mean over runs; MIS vs CC (or BF) in same plot with different colours/linetypes; vertical line at failure time.
- [ ] **Figure — failure response:** Zoom on t ≈ 80–150: MEC, RPL, γ for MIS vs CC/BF; show difference in slope or delay to recovery.
- [ ] **Table or figure:** Mean γ, mean surprise, variance of γ, “recovery time” (if defined) by surprise measure (MIS vs CC vs BF).
- [ ] **Statistical comparison:** If multiple runs: e.g. paired or two-sample test (MIS vs CC) on mean_MEC, mean_RPL, or recovery time; report test, statistic, p-value, effect size; state significance level.
- [ ] **Interpretation:** Why MIS might behave differently (proactive vs reactive; epistemic growth vs single-instance deviation); link to theory (e.g. MIS paper, varSMiLE).

**Deliverables:** 2–3 figures (trajectories, failure zoom, optional summary box plot); one table; 2–3 paragraphs of deep interpretation; optional short statistical subsection.

---

### 3.4 Dynamics of Surprise and γ

**Purpose:** Show how surprise and γ evolve and how they relate to QoS; support “learning vs reset” and “reactive vs proactive” narrative.

**Data extraction:**

- [ ] From **gamma.txt**, **surpriseCC.txt**, **surpriseBF.txt**, **surpriseMIS.txt**: time series per (mote, timestep) or aggregated per timestep.
- [ ] From **MISBounds.txt**: flag timesteps where MIS is outside [lower, upper].
- [ ] Correlations: γ vs surprise (per measure); γ vs MEC/RPL (lag 0 or 1); surprise vs MEC/RPL.

**Content:**

- [ ] **Figure:** γ over time (mean over motes) for MIS vs CC (or BF); annotate failure time; optionally overlay MIS bounds (when MIS is used).
- [ ] **Figure:** Scatter or lag plot — γ vs surprise (MIS or CC); colour by “before/after failure” or by timestep.
- [ ] **Narrative:** When is γ high (reset) vs low (retain)? How does that align with high vs low surprise? How does MIS’s sign (enlightenment vs frustration) relate to γ and QoS?
- [ ] **Optional:** Small table of correlations (γ vs surprise, γ vs MEC/RPL) for MIS vs CC/BF.

**Deliverables:** 1–2 figures; 1–2 paragraphs; optional correlation table.

---

### 3.5 Sensitivity and Robustness

**Purpose:** Show that findings are not limited to one choice of p_c or λ; report variability across runs.

**Data extraction:**

- [ ] From **experiment_results.csv** or aggregated runs: for each (algorithm, surprise), vary p_c and λ; compute mean_MEC ± SE, mean_RPL ± SE.
- [ ] Optionally: different failure timings or durations (if you ran them); report mean ± SE per scenario.

**Content:**

- [ ] **Table or heatmap:** mean MEC (and RPL) by surprise × p_c × λ (or 2D slice: e.g. p_c × λ for MIS only, then for CC only).
- [ ] **Figure:** Line plot — mean MEC (or RPL) vs p_c (or λ) with error bars; one line per surprise measure (MIS, CC, BF).
- [ ] **Narrative:** Is there an “optimal” p_c or λ? Is MIS more or less sensitive than CC/BF? How stable are results across runs (SE magnitude)?
- [ ] **Threats:** Acknowledge limited scenarios or seeds; suggest future work (more runs, more failure types).

**Deliverables:** 1 table or heatmap; 1 figure; ~1 paragraph.

---

### 3.6 Optional: Link-Level and Action-Level Analysis

**Purpose:** Support main story with evidence from actions and link quality (e.g. DTP vs ITP balance, SNR/power evolution).

**Data extraction:**

- [ ] **SelectedAction.txt:** Distribution of DTP vs ITP over time; by configuration (MIS vs CC/BF).
- [ ] **mote_metrics.txt:** Per-link SNR, power, SF over time; aggregate (e.g. mean SNR per timestep) or show one representative mote/link.
- [ ] **MECSatProb.txt, RPLSatProb.txt:** Belief-state satisfaction over time; compare with actual MEC/RPL.

**Content:**

- [ ] **Figure:** Action distribution (DTP vs ITP) over time or by phase (before/after failure); MIS vs CC/BF.
- [ ] **Figure or table:** Mean SNR or power over time; correlation with MEC/RPL.
- [ ] **Narrative:** Do MIS and CC/BF lead to different action patterns or link settings? Do belief probabilities track actual QoS?

**Deliverables:** 1–2 figures or tables; short paragraph. Keep subordinate to primary and MIS vs CC/BF subsections.

---

## 4. Tables and Figures — Checklist

| # | Item | Source data | Subsection |
|---|------|-------------|------------|
| T1 | Main results table: mean MEC ± SE, mean RPL ± SE by (algorithm, surprise, p_c, λ) | MECSattimestep, RPLSattimestep; experiment_results.csv | 3.2 |
| F1 | Bar/box: mean MEC and mean RPL by configuration (group by surprise) | As T1 | 3.2 |
| F2 | Trajectories: MEC, RPL, γ, surprise over time; MIS vs CC/BF; failure line | MECSattimestep, RPLSattimestep, gamma, surpriseCC, surpriseMIS | 3.3 |
| F3 | Failure window: MEC, RPL, γ zoomed t≈80–150; MIS vs CC/BF | As F2 | 3.3 |
| T2 | Summary: mean γ, mean surprise, recovery time (if defined) by surprise measure | gamma, surprise*, MEC/RPL | 3.3 |
| F4 | γ over time (mean over motes); MIS vs CC; optional MIS bounds | gamma, surprise*, MISBounds | 3.4 |
| F5 | γ vs surprise (scatter or lag); optional before/after failure | gamma, surprise* | 3.4 |
| T3 or F6 | Sensitivity: mean MEC/RPL vs p_c or λ; MIS vs CC/BF | experiment_results.csv | 3.5 |
| (Optional) F7 | Action distribution over time; MIS vs CC/BF | SelectedAction | 3.6 |

---

## 5. Statistical and Reporting Standards

- [ ] **Uncertainty:** Report **mean ± standard error (SE)** or **95% CI** for all aggregate metrics (MEC, RPL, γ, surprise) across runs.
- [ ] **Comparisons:** For MIS vs CC (or BF): state **test** (e.g. Wilcoxon, t-test, or linear mixed model if repeated measures), **statistic**, **p-value**, **effect size** (e.g. Cohen’s d); use a consistent **α** (e.g. 0.05).
- [ ] **Effect size:** Prefer reporting effect size with p-values so readers can judge practical importance.
- [ ] **Reproducibility:** State seeds, numTimesteps, failure scenario, and where raw/aggregated data and code live (e.g. supplementary or repo).

---

## 6. Narrative Arc for the Whole Results Section

1. **Setup** — What was run and how (short).
2. **Primary** — Overall performance (MEC, RPL) by configuration; first answer to “does surprise measure matter?”
3. **Deep** — MIS vs CC/BF: trajectories, failure response, γ and surprise dynamics; *why* they differ (reactive vs proactive).
4. **Robustness** — Sensitivity to p_c, λ; stability across runs.
5. **Supporting** — Optional link/action analysis.

**Closing:** One short paragraph summarising: (a) best-performing configurations, (b) main difference between MIS and CC/BF, (c) limitation (e.g. single scenario, one topology), (d) lead-in to Evaluation/Discussion (e.g. threats to validity, future work).

---

## 7. Writing Order Suggestion

1. Extract and aggregate data (scripts to read `output_dir` and, if used, `experiment_results.csv`).
2. Build **T1** and **F1** (primary outcomes) — write 3.2.
3. Build **F2, F3, T2** (MIS vs CC/BF) — write 3.3 (core deep analysis).
4. Build **F4, F5** (γ and surprise dynamics) — write 3.4.
5. Build sensitivity table/figure — write 3.5.
6. Write 3.1 (setup) and optional 3.6.
7. Add statistical tests and effect sizes where planned.
8. Draft closing summary and link to Discussion.

---

*This plan is keyed to the current `output_dir` layout and to a dissertation-level Results section with deep analysis and a central MIS vs CC/BF comparison. Adjust table/figure numbers and subsection titles to match your thesis template.*

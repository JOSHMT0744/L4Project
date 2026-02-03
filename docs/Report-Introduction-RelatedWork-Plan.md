# Report Plan: Introduction and Related Work

Structured plan for **Introduction** (Section 1) and **Related Work** (Section 2). Methodology (Section 3) is already written; use this as a checklist of points to mention.

---

## 1. Introduction (~2 pages)

**Purpose:** Motivate the problem, context (SAS, IoT), gap (reactive surprise, limited IoT), contributions, and report structure.

---

### 1.1 Opening: Complex Systems and Self-Adaptation  
*Target: 1 short paragraph*

**Points to mention:**

- [ ] Complex software systems (cloud, CPS, IoT) face growing challenges: scale, complexity, unpredictable environment changes.
- [ ] Self-adaptive systems (SAS) address these by modifying runtime behaviour in a closed loop to satisfy objectives.
- [ ] Cite SAS/uncertainty source (e.g. [1]).
- [ ] Keep high-level; no technical detail yet (problem → response = self-adaptation).

---

### 1.2 The Problem: Fixed Assumptions and Broken World Models  
*Target: 1–2 paragraphs*

**Points to mention:**

- [ ] Traditionally, SAS rely on **fixed assumptions** about how adaptation actions affect quality attributes (e.g. latency, reliability, energy).
- [ ] When unforeseen events (e.g. data corruption, link failure) violate these assumptions, the **world model** no longer matches reality.
- [ ] The system must recognise and revise its understanding of adaptation impacts.
- [ ] Otherwise the agent’s internal model becomes inaccurate and decision-making degrades.
- [ ] (Optional) Few studies systematically address the gap between *assumed* and *observed* impact of adaptation actions during execution (e.g. QuantUn, Bencomo).
- [ ] Flow to convey: fixed assumptions → broken assumptions → need for runtime model revision.

---

### 1.3 Role of Uncertainty and Partial Observability  
*Target: 1 paragraph*

**Points to mention:**

- [ ] Uncertainty and limited state information are inherent (noisy sensors, hidden state, non-stationarity).
- [ ] We employ a **Partially Observable Markov Decision Process (POMDP)** framework to capture this.
- [ ] POMDPs are widely used for sequential decision-making under partial observability.
- [ ] The agent maintains a **belief** over states and selects actions based on that belief.
- [ ] The **transition function** \(T(s,a,s')\) encodes the (possibly wrong) assumed impact of actions.
- [ ] \(T\) is the **primary target for learning and revision** in this work.
- [ ] Flow: uncertainty → POMDP → focus on learning/adapting \(T\).

---

### 1.4 Surprise as a Signal for Model Revision  
*Target: 1 paragraph*

**Points to mention:**

- [ ] Recent work uses **Surprise**—a Bayesian or information-theoretic measure of deviation between expected and observed outcomes—to drive runtime revision of the world model (e.g. Samin et al.).
- [ ] Surprise indicates when observations diverge from the current belief about transition (and observation) dynamics.
- [ ] Thus it signals potential **broken assumptions**.
- [ ] Surprise modulates how much the system trusts its current model versus resetting toward a prior (e.g. flat prior).
- [ ] This enables **surprise-based learning** of transition beliefs.
- [ ] Do not duplicate methodology; link conceptually to varSMiLE/MIS.

---

### 1.5 Gap and Motivation  
*Target: 1 paragraph*

**Points to mention:**

- [ ] Existing surprise-based adaptation has been demonstrated mainly in domains such as **Remote Data Mirroring (RDM)** (e.g. Samin et al., RDMSim).
- [ ] Extension to **Internet of Things (IoT)**—different NFRs (energy, packet loss), topology, failure modes—remains limited.
- [ ] Many surprise measures are **reactive** (single-instance, binary comparison).
- [ ] They do not explicitly capture whether the system is **learning over time** (proactive, epistemic signal).
- [ ] Implementing **Mutual Information Surprise (MIS)** (Wang et al.) in an SAS setting offers a more **proactive** view (epistemic growth vs. anomaly).
- [ ] Applying this in the **DeltaIoT** IoT exemplar is a natural and novel step.
- [ ] Flow: domain gap (RDM → IoT) + conceptual gap (reactive → proactive) → need for this project.

---

### 1.6 Aims and Contributions  
*Target: 1 short paragraph; bullet list acceptable*

**Points to mention:**

- [ ] Evaluate surprise-based policies in the **IoT setting** using the **DeltaIoT** simulator.
- [ ] Extend with **entropy-regularised POMDP solvers** (ERPerseus, ERPBVI) and **variational SMiLE (varSMiLE)** for adaptive learning of transition (and observation) beliefs.
- [ ] Implement and compare **multiple surprise measures** (Confidence-Corrected, Bayes Factor, **MIS**) for modulating the learning rate \(\gamma\).
- [ ] Use a **BA-POMDP** formulation aligned with **RE-STORM** for NFR trade-offs (MEC, RPL).
- [ ] (Optional) **Noise injection** for stress testing under failure scenarios.
- [ ] State clearly what was built and how it addresses the gap.

---

### 1.7 Report Structure  
*Target: 1 short paragraph*

**Points to mention:**

- [ ] Section 2 (Related Work): surveys SAS, POMDPs, surprise measures, RE-STORM/DeltaIoT.
- [ ] Section 3 (Methodology): system architecture, MAPE-K loop, BA-POMDP model, entropy-regularised solvers, surprise-modulated learning.
- [ ] Sections 4–6: results, evaluation, conclusions.
- [ ] Keep brief; signpost only.

---

### 1.8 Introduction — Overall Checklist

- [ ] SAS and world-model revision motivated.
- [ ] POMDP and transition function \(T\) as focus stated.
- [ ] Surprise as a driver for model update explained at a high level.
- [ ] Gap (IoT, proactive/MIS) and contributions (DeltaIoT, varSMiLE, MIS, ER solvers) clearly stated.
- [ ] No duplication of methodology detail; methodology only signposted.

---

## 2. Related Work (~3 pages)

**Purpose:** Survey SAS, POMDPs, surprise, SMiLe/varSMiLE, RE-STORM, DeltaIoT; position this project. Support methodology without repeating it.

---

### 2.1 Self-Adaptive Systems and Uncertainty  
*Target: ~0.5 page*

**Points to mention:**

- [ ] SAS modify runtime behaviour to achieve objectives under uncertainty (cite e.g. Mahdavi-Hezavehi et al., Salehie & Tahvildari).
- [ ] Domains: CPS, cloud, IoT, web services.
- [ ] **Adaptation actions** change parameters, structure, or behaviour; their **impact** is often assumed fixed at design time.
- [ ] **Non-functional requirements (NFRs)** (Bencomo) define quality goals (e.g. minimise energy, reduce packet loss).
- [ ] Trade-offs between NFRs require explicit treatment (e.g. RE-STORM, Pri-AwaRE).
- [ ] **MAPE-K** (Monitor–Analyse–Plan–Execute with Knowledge): monitor managed system, analyse, plan action, execute; knowledge base holds shared state and models.
- [ ] End with: decision-making under uncertainty and partial observability leads to POMDPs.

---

### 2.2 POMDPs for Decision-Making under Partial Observability  
*Target: ~0.5 page*

**Points to mention:**

- [ ] **POMDP** tuple \(\langle S, A, Z, T, O, R, \gamma \rangle\): states \(S\), actions \(A\), observations \(Z\), transition \(T(s,a,s')\), observation model \(O\), reward \(R\), discount \(\gamma\).
- [ ] Agent does not observe state directly; maintains **belief** \(b(s)\) and acts on \(b\).
- [ ] **Transition function** \(T\): “given state \(s\) and action \(a\), probability of next state \(s'\).”
- [ ] In SAS, \(T\) encodes the *assumed* impact of adaptation actions; when assumptions break, \(T\) is wrong and must be updated.
- [ ] POMDPs used for SAS decision-making (e.g. Bencomo, Samin, RE-STORM) to balance exploration, exploitation, and model uncertainty.
- [ ] Brief: offline vs online solvers; point-based value iteration (e.g. Perseus) for scalable approximate solving.
- [ ] Link to BA-POMDP: unknown \(T\), learned via belief.

---

### 2.3 Surprise: Definitions and Taxonomy  
*Target: ~0.75 page*

**Points to mention:**

- [ ] **Surprise** = unexpected observation relative to prior knowledge (cite taxonomy, e.g. Modirshanechi et al.).
- [ ] Used to detect broken assumptions and drive belief updates.
- [ ] **Shannon surprise:** \(-\ln P(X)\); information content of the observation. Limitation: does not incorporate model uncertainty explicitly.
- [ ] **Bayesian surprise:** KL divergence from prior to posterior after observing \(X\); measures belief change.
- [ ] **Confidence-Corrected (CC) surprise:** KL from current belief to “reset” (flat-prior updated with observation). Captures “puzzlement”; more surprising when agent was more confident. Implemented in this project for \(\gamma\).
- [ ] **Bayes Factor (BF) surprise:** Ratio of likelihood of observation under flat prior vs current belief. Used in non-stationary settings (e.g. Liakoni); prevents modulation when both prior and current belief are surprised.
- [ ] **Mutual Information Surprise (MIS)** (Wang et al.): surprise as **epistemic growth**—change in mutual information over time (e.g. \(\widehat{I}_{n+m} - \widehat{I}_n\)). Learning (enlightenment) vs stalled (frustration). Contrast with single-instance measures; enables proactive use (bounds, hypothesis tests). Implemented with theoretical bounds.
- [ ] (Optional) Table: single-instance vs temporal, reactive vs proactive (align with literature survey Table 1: Shannon/Bayesian family vs MIS).
- [ ] Do not repeat full equations from methodology; cite and summarise.

---

### 2.4 Surprise-Based Learning: SMiLe and varSMiLE  
*Target: ~0.5 page*

**Points to mention:**

- [ ] **SMiLe (Surprise Minimisation Learning)** (Faraji, Preuschoff, Gerstner): adaptation rate \(\gamma\) is a function of surprise.
- [ ] Update rule: blend current belief with flat prior using \(\gamma\); \(\gamma = \frac{m \cdot S}{1 + m \cdot S}\) with \(m = P_c/(1-P_c)\) (volatility) and \(S\) = surprise.
- [ ] High surprise → high \(\gamma\) → more reset toward prior; low surprise → low \(\gamma\) → retain current belief.
- [ ] **Samin et al.:** apply SMiLe in SAS—surprise-based update of *impact of adaptation actions* (transition probabilities). Demonstrated in **RDMSim**; key next step they identify: test in IoT domain.
- [ ] **Variational SMiLe (varSMiLE):** keeps updated belief in same exponential family; integrates new observation with current belief before blending. More stable and data-efficient in non-stationary environments (Liakoni).
- [ ] This project uses varSMiLE-style update (Dirichlet pseudo-counts, blend after +1 update) with multiple surprise measures (CC, BF, MIS) in DeltaIoT.

---

### 2.5 NFR Trade-Offs and RE-STORM  
*Target: ~0.4 page*

**Points to mention:**

- [ ] NFRs (e.g. MEC, RPL) often conflict; representing satisfaction as probabilities rather than binary allows nuanced trade-offs (Bencomo, Pri-AwaRE).
- [ ] **RE-STORM** (Garcia Paucar, Bencomo): maps decision-making and NFR trade-offs to POMDPs.
- [ ] States encode *degrees of NFR satisfaction*; rewards reflect utility of (state, action); transition and observation models can be updated from evidence; Bayesian inference for belief over state.
- [ ] This project adopts a RE-STORM-aligned BA-POMDP: states = NFR satisfaction categories; rewards = expert-defined (Table 2 in methodology); transition and observation beliefs are Dirichlet and updated via varSMiLE.
- [ ] Do not repeat methodology detail.

---

### 2.6 Application Domains: DeltaIoT and RDMSim  
*Target: ~0.35 page*

**Points to mention:**

- [ ] **RDMSim:** exemplar for RDM; used by Samin et al. for surprise-based adaptation. Latency-aware extension exists (e.g. SEAMS ’24).
- [ ] **DeltaIoT** (Iftikhar et al.): self-adaptive IoT exemplar—LoRa-based multi-hop network, motes, gateway.
- [ ] NFRs = MEC and RPL. Adaptation actions: DTP/ITP (transmission power), path changes.
- [ ] Preconfigured scenarios (e.g. link interference, mote failure) for broken-assumption testing.
- [ ] Well-suited for evaluating surprise-based policies in IoT.
- [ ] This project implements full MAPE-K + BA-POMDP + varSMiLE pipeline in DeltaIoT, with optional noise injection for failure scenarios.

---

### 2.7 Positioning of This Work  
*Target: 1 short paragraph*

**Points to mention:**

- [ ] Extends surprise-based learning from RDM to **IoT (DeltaIoT)**.
- [ ] Combines **entropy-regularised** POMDP solvers (ERPerseus/ERPBVI) with **varSMiLE** for transition/observation learning.
- [ ] Implements and compares **CC, BF, and MIS** for \(\gamma\).
- [ ] Uses **MIS** with theoretical bounds for a more proactive, epistemic surprise signal in SAS.
- [ ] Integrates with **RE-STORM**-style BA-POMDP and MAPE-K.
- [ ] To the authors’ knowledge: first application of MIS in an SAS setting; first full surprise-based BA-POMDP pipeline for DeltaIoT.
- [ ] Clear statement of novelty so methodology is the natural next step.

---

### 2.8 Related Work — Overall Checklist

- [ ] SAS, MAPE-K, and uncertainty covered.
- [ ] POMDP and role of \(T\) (and belief) explained.
- [ ] Surprise taxonomy and CC, BF, MIS (and optionally Shannon/Bayesian) covered; MIS positioned as proactive/epistemic.
- [ ] SMiLe, Samin et al., varSMiLE and link to this project’s update rule covered.
- [ ] RE-STORM and NFR trade-offs covered; DeltaIoT and RDMSim contextualised.
- [ ] Final paragraph positions this work; no deep repetition of Methodology (Section 3).

---

## 3. Cross-References (Repository / Docs)

| Topic | Reference |
|--------|-----------|
| MAPE-K loop | `docs/MAPE-K-DeltaIoT-Implementation.md`, `DeltaIOTConnector` |
| varSMiLE, γ, surprise | `docs/Surprise-Based-Learning-DeltaIoT.md`, `updateTransitionBelief`, `confidenceCorrectedSurprise`, `bayesFactorSurprise`, `calculateAndStoreMIS` |
| MIS bounds | `docs/MIS_pseudocode.md`, `DeltaIOTConnector.calculateAndStoreMIS` |
| BA-POMDP, Dirichlet | `Surprise-Based-Learning-DeltaIoT.md` §5–6, `transitionBeliefCurr`, `transitionBeliefReset` |
| ERPerseus, ERPBVI | Report Methodology §3.2; `src/solver/ERPerseus.java`, `ERPBVI` |
| DeltaIoT, NFRs | `README.md`, Report Methodology §3.1; domains `IoT.POMDP` |
| Noise injector | Report Methodology §3.4.1; `noiseInjector` in code |

---

## 4. Suggested Writing Order

- [ ] Write **Related Work** first (establish what you set up for the reader).
- [ ] Write **Introduction** second (gap and contributions aligned with Related Work).
- [ ] Light edit of **Introduction** after **Related Work** so “gap” and “contribution” match §2.7 positioning.

# Surprise-Based Learning (SMiLe) in DeltaIoT

This document describes how the **Surprise-based learning** approach from the paper “Surprise! Surprise! Learn and adapt” is implemented in DeltaIoT, specifically in `DeltaIOTConnector.updateTransitionBelief()`. The implementation uses a **SMiLe (SMiLe)** rule to update beliefs about the impacts of adaptation actions (POMDP transition probabilities) as a function of how surprising the observed outcomes are.

---

## 1. Notation and Mapping to the Paper

We align DeltaIoT’s implementation with the paper’s Algorithm 1 and terminology.

| Paper (Algorithm 1) | DeltaIoT implementation | Description |
|---------------------|--------------------------|-------------|
| \(N\) | `p.getNumStates()` | Number of POMDP states (SAS states). |
| \(B\), \(B_{X_n}\) | `p.transitionBeliefCurr` | **Beliefs about the impacts of adaptation actions**: current (learned) Dirichlet pseudo-counts for \(P(\text{next state} \mid \text{state}, \text{action})\). |
| \(\pi_0\) | `p.transitionBeliefReset` | **Initial (flat) beliefs**: Dirichlet pseudo-counts for the same transitions, kept as a uniform prior. |
| \(W\), \(W_{X_n}\) | Normalised `transitionBeliefCurr` (via `getTransitionProbability`) | **World model**: transition probabilities used by the POMDP; derived by normalising the current belief pseudo-counts. |
| \(X_n\) | Observed (state, action, next state) | The transition selected by the current state–action pair and the observed next state. |
| \(S_{\text{Bayes}}\) | CC, BF, or MIS (see Section 4) | **Surprise** given the current belief and the observation. |
| \(P_c\) | `p_c` (e.g. 0.5) | **Probability of change**: domain parameter for environment volatility. |
| \(m\) | `m = p_c / (1 - p_c)` | **Rate of change** in the environment. |
| \(\gamma\) | `gamma` | **Adaptation rate**: weight on the flat prior in the SMiLe update. |
| \(\Delta\) | +1.0 pseudo-count increments | Indicator of the observed transition; implemented as Bayesian pseudo-count updates. |

The **SMiLe** rule in the code is the SMiLe update (Equation 6 in the paper), \(B_{X_n} = (1-\gamma) B_{X_n} + \gamma \pi_0 + \Delta\), with \(B_{X_n}\) and \(\pi_0\) represented as Dirichlet pseudo-counts and \(\Delta\) realised by incrementing those counts before blending (see Section 5).

---

## 2. Core Mechanism: \(\gamma\) and \(P_c\) Controlling the Learning Rate

The adaptation rate \(\gamma \in [0,1]\) determines how much the updated belief is pulled toward the **flat prior** \(\pi_0\) versus the **current belief** \(B_{X_n}\).

- **\(\gamma \to 1\)**: The new belief is dominated by \(\pi_0\). The system effectively discards recent learned information and resets toward a uniform view of transition impacts. This is used when the environment is treated as highly volatile or when observations are very surprising.
- **\(\gamma \to 0\)**: The new belief stays close to the current (learned) belief. The system trusts accumulated experience and updates it only moderately with the new observation. This is used when the environment is treated as stable or when observations are unsurprising.

So \(\gamma\) directly controls **how aggressively the current distribution is pulled toward the flat prior**: higher \(\gamma\) ⇒ more reset; lower \(\gamma\) ⇒ more retention of the current belief.

### 2.1 Role of \(P_c\) (Probability of Change)

\(P_c\) is a **domain-expert parameter** in \((0, 1)\) that encodes **environment volatility**:

- **\(P_c\) close to 0**: Environment is assumed **less volatile**. Then \(m = P_c/(1-P_c)\) is small, so \(\gamma\) tends to stay lower. The learner prioritises the **current belief** \(B\); large changes in beliefs are interpreted as meaningful and are corrected promptly.
- **\(P_c\) close to 1**: Environment is assumed **highly volatile**. Then \(m\) is large, so \(\gamma\) can approach 1 more easily. The learner **discards** current beliefs more readily and leans on the **flat prior** \(\pi_0\), avoiding overconfidence in possibly obsolete experience.

In DeltaIoT, \(P_c\) is set in code (e.g. `p_c = 0.5` in `updateTransitionBelief`); the same formula \(m = P_c/(1-P_c)\) is used so that \(\gamma\) is informed by both surprise and this volatility parameter.

### 2.2 Formula for \(\gamma\)

The implementation uses the SMiLe (Definition 4) formula:

\[
\gamma = \frac{m \cdot S}{1 + m \cdot S},
\]

where \(S\) is the **surprise** (in the code, \(S = \exp(\texttt{logSurprise})\) for the chosen surprise measure). Numerically it is computed as \(\gamma = 1 / (1 + 1/(m \cdot S))\) for stability.

- **High surprise** \(S\) ⇒ \(m \cdot S\) large ⇒ \(\gamma\) close to 1 ⇒ strong pull toward \(\pi_0\) (reset).
- **Low surprise** \(S\) ⇒ \(m \cdot S\) small ⇒ \(\gamma\) close to 0 ⇒ belief stays near current \(B_{X_n}\) (retain).

Thus \(\gamma\) is **modulated by both** the observed surprise and the environment’s assumed rate of change \(m\), matching the paper’s Step 4.

---

## 3. The SMiLe Update Rule (Step 5–6)

After computing surprise and \(\gamma\), the beliefs about the impacts of adaptation actions are updated by the **SMiLe rule** (Equation 6 in the paper):

\[
B_{X_n} = (1 - \gamma) B_{X_n} + \gamma \pi_0 + \Delta.
\]

In DeltaIoT this is implemented in **Dirichlet pseudo-count space**, with \(\Delta\) realised by **first** incrementing pseudo-counts for the observed transition in **both** the current and the flat-prior arrays, then blending:

1. **\(B_{\text{current, observed}}\)**: Copy of `transitionBeliefCurr`, then add +1.0 to the (state, action, nextstate) entries for the observed transition (for all prior states, as in Section 5). This is the “current belief updated with the new observation”.
2. **\(B_{\text{initial, observed}}\)**: Copy of `transitionBeliefReset`, then add +1.0 to the same (state, action, nextstate) entries. This is the “flat prior updated with the new observation”.
3. **Blend** (SMiLe):
   \[
   B_{\text{new}}[s][a][s']] = (1 - \gamma) \cdot B_{\text{current, observed}}[s][a][s']] + \gamma \cdot B_{\text{initial, observed}}[s][a][s']].
   \]
   Only the slice for the taken action \(a\) is blended; the result is written back into `p.transitionBeliefCurr`.

So in notation aligned with the paper: the “\(B_{X_n}\)” and “\(\pi_0\)” in Equation 6 correspond to the **post-increment** pseudo-counts \(B_{\text{current, observed}}\) and \(B_{\text{initial, observed}}\), and \(\Delta\) is implicit in those increments. The **world model** \(W\) (transition probabilities) is then given by normalising these updated counts in `getTransitionProbability()` (Section 5).

---

## 4. Measures of Surprise

The implementation supports **three** surprise measures for computing \(\gamma\); one is chosen at runtime via `surpriseMeasureForGamma` (e.g. `"CC"`, `"BF"`, or `"MIS"`).

### 4.1 Confidence-Corrected Surprise (CC)

- **Method**: `confidenceCorrectedSurprise(transitionBelief, transitionBeliefReset, action, nextstate)`.
- **Idea**: For each possible current state, compute the KL divergence between (i) the **current** transition belief (before the +1 update) and (ii) the **flat prior updated with the observed (action, nextstate)**. Then take the **belief-weighted average** over current states using `p.getInitialBelief()`.
- **Formula (conceptually)**: \(S_{\text{CC}} = \sum_s b(s) \, D_{\text{KL}}\bigl(\pi^{(s)}(\cdot \mid a) \,\|\, \pi_{\text{flat}}(\cdot \mid a, y_{t+1}, x_{t+1})\bigr)\), where \(\pi^{(s)}\) is the Dirichlet for state \(s\) and action \(a\), and the flat posterior conditions on the observation.
- **Implementation**: Uses the Dirichlet KL identity (log-gamma and digamma terms) and weights by `p.getInitialBelief().getBelief(currState)`.
- **Use**: Captures how much the current transition belief deviates from a flat-prior view after seeing the observation; high deviation ⇒ high surprise ⇒ high \(\gamma\).

### 4.2 Bayes Factor Surprise (BF)

- **Method**: `bayesFactorSurprise(transitionBeliefCurr, transitionBeliefReset, action, nextstate)`.
- **Idea**: Compare how likely the **observed next state** is under the **current belief** versus under the **flat prior**, both marginalised over the current state using the POMDP belief.
- **Formula**: \(S_{\text{BF}} = \log \frac{P_{\text{reset}}(\text{nextstate} \mid \text{belief})}{P_{\text{curr}}(\text{nextstate} \mid \text{belief})}\). The code returns this **log** value; for \(\gamma\), `logSurpriseBF` is used directly (or clamped with `eps`) and \(S = \exp(\texttt{logSurprise})\) when BF is not the chosen measure.
- **Implementation**: `getLogPredProbs` gives \(\log P(\text{nextstate} \mid s, a)\) for each state \(s\) under each belief (current vs reset); these are combined with the state belief to get predicted probabilities, then the log ratio is taken.
- **Use**: High BF ⇒ the observation is much more likely under the flat prior than under the current model ⇒ high surprise ⇒ high \(\gamma\).

### 4.3 Mutual Information Surprise (MIS)

- **Method**: `calculateAndStoreMIS(transitionBeliefPrior, transitionBeliefPosterior, action, nextstate, moteId, timestep)`.
- **Idea**: Surprise as **change in mutual information** over time: MIS = MI(now) − MI(now − lookback). MI for a given belief is (prior entropy of the Dirichlet over next states) − (posterior entropy after observing the transition), averaged over current states with the state belief.
- **Implementation**:  
  - Prior entropy: `getMoteEntropy(transitionBeliefPrior, action, nextstate)` (belief-weighted sum of Dirichlet entropies for each (state, action)).  
  - Posterior entropy: same with `transitionBeliefPosterior` (belief **after** +1.0 for the observed transition).  
  - MI = prior entropy − posterior entropy (information gained by the observation).  
  - Per-mote MI history is kept; MIS = current MI − MI at (current − lookback). For \(\gamma\), the **absolute value** of MIS is used and scaled to avoid \(\log(0)\); the sign of MIS is used for interpretation (e.g. over-exploitation vs over-exploration in comments).
- **Use**: Captures whether the learner is gaining new information (MIS > 0) or not (MIS < 0); magnitude of MIS drives \(\gamma\) so that surprising (informative) observations increase the pull toward the flat prior.

Only **one** of CC, BF, or MIS is used as the surprise value \(S\) in the \(\gamma\) formula in each call to `updateTransitionBelief`.

---

## 5. Dirichlet Representation of Transition Probabilities

Beliefs about the **impacts of adaptation actions** (transition probabilities) are represented as **Dirichlet distributions** via **pseudo-counts**, not as point probabilities.

- **Shape**: `transitionBeliefCurr[s][a][s']]` and `transitionBeliefReset[s][a][s']]` are three-dimensional arrays indexed by (current state \(s\), action \(a\), next state \(s'\)). For each \((s, a)\), the vector of counts is the **concentration parameter** of a Dirichlet over the next state.
- **Meaning**: For each \((s, a)\), the transition probability **distribution** in the world model \(W\) is the **expectation** of the Dirichlet, i.e. the normalised pseudo-counts:
  \[
  P(s' \mid s, a) = \frac{\texttt{transitionBeliefCurr}[s][a][s']]}{\sum_{s''} \texttt{transitionBeliefCurr}[s][a][s'']]}.
  \]
  This is exactly what `POMDP.getTransitionProbability(s, a, sNext)` implements: it returns the normalised value from `transitionBeliefCurr`. So the **world model** \(W\) is the set of these normalised transition distributions derived from the current belief \(B\).
- **Initialisation**: In `PomdpParser`, both `transitionBeliefCurr` and `transitionBeliefReset` are initialised with **flat** distributions: for each \((s, a)\), every next-state count is set to 1.0. Thus \(\pi_0\) is a uniform prior over next states for each (state, action).
- **Role of two arrays**:  
  - `transitionBeliefCurr` is the **learned** belief \(B\); it is updated by the SMiLe rule and then used in `getTransitionProbability` to define \(W\).  
  - `transitionBeliefReset` is the **fixed** flat prior \(\pi_0\); it is updated only by the same +1.0 increments for the observed transition (so that the “flat prior plus observation” is used in the blend), but it is not blended with the current belief—it serves as the reset target in the SMiLe update.

This Dirichlet representation allows **Bayesian** updating (pseudo-count increments) and a **closed-form** blend between current and prior beliefs in the SMiLe step.

---

## 6. Bayesian Updating: Pseudo-Count Increments for Observed Transitions

The **\(\Delta\)** term in the paper’s SMiLe rule (Equation 6) is implemented by **Bayesian updating** of the Dirichlet pseudo-counts: when the system observes a transition (action \(a\) taken and next state \(s'\) observed), it increments the corresponding counts **before** applying the SMiLe blend.

- **Where**: In `updateTransitionBelief`, after deep-copying `transitionBeliefCurr` and `transitionBeliefReset` into `transitionBeliefCurrTemp` and `transitionBeliefResetTemp`, the code does:
  ```text
  for (stateIndex = 0 .. numStates-1)
      transitionBeliefCurrTemp[stateIndex][action][nextstate] += 1.0;
      transitionBeliefResetTemp[stateIndex][action][nextstate] += 1.0;
  ```
- **Why all prior states**: The POMDP state is **partially observable**; the true previous state is unknown. The observation (action, next state) is consistent with **any** prior state \(s\) transitioning to \(s'\) under \(a\). So the Bayesian update adds one “vote” for the (state, action, nextstate) triple for **every** possible prior state. This is equivalent to weighting the observed transition by the belief over the current state; the implementation uses a uniform +1.0 over states, which keeps the Dirichlet update simple and symmetric.
- **Effect**:  
  - **Current belief**: The copy of `transitionBeliefCurr` gets +1.0 on the observed (s, a, nextstate) for each \(s\); then this updated copy is blended with \((1-\gamma)\).  
  - **Flat prior**: The copy of `transitionBeliefReset` gets the same +1.0; the blended prior in the SMiLe rule is this “flat + observation” distribution.  
  So both \(B_{\text{current, observed}}\) and \(B_{\text{initial, observed}}\) in Section 3 include the **same** Bayesian update (the \(\Delta\)); the only difference is the accumulated history in the current belief versus the flat prior.

After the blend, `transitionBeliefCurr` is replaced by the blended pseudo-counts. The **world model** \(W\) used by the POMDP solver and policy is then updated implicitly, because `getTransitionProbability()` reads from `transitionBeliefCurr` and normalises (Section 5). Thus Steps 5 and 6 of the paper—update beliefs, then update the impacts of adaptation actions (transition probabilities)—are done together: the belief **is** the Dirichlet whose normalisation gives the transition probabilities.

---

## 7. End-to-End Flow in `updateTransitionBelief(action, nextstate)`

1. **Copy** `transitionBeliefCurr` and `transitionBeliefReset` to temporary arrays.
2. **Bayesian update (Δ)**: For each prior state index, add 1.0 to `[stateIndex][action][nextstate]` in both temporaries.
3. **Surprise**: Compute CC, BF, and MIS from the appropriate beliefs (current vs reset; for MIS, prior vs posterior temporaries). Choose one as \(S\) via `surpriseMeasureForGamma` and convert to `logSurprise` (or use log directly for BF).
4. **\(\gamma\)**: \(m = p_c/(1-p_c)\), \(S = \exp(\texttt{logSurprise})\) (or equivalent), \(\gamma = 1/(1 + 1/(m \cdot S))\), clamped to \([\texttt{eps}, 1]\).
5. **SMiLe**: For the taken action, blend the two temporaries:  
   `transitionBeliefCurrTemp[s][a][s']] = (1−γ) * transitionBeliefCurrTemp[s][a][s']] + γ * transitionBeliefResetTemp[s][a][s']]`.
6. **Commit**: Set `p.transitionBeliefCurr = transitionBeliefCurrTemp`.
7. **World model**: Subsequent `getTransitionProbability(s, a, sNext)` calls use the new `transitionBeliefCurr`, so the POMDP’s transition model \(W\) is updated for the next planning step.

This flow realises Algorithm 1’s Steps 3–6 in the paper: compute surprise, compute \(\gamma\), update beliefs with the SMiLe rule (including \(\Delta\) via pseudo-counts), and update the world model via the same Dirichlet belief.

---

## 8. Detailed Pseudocode: SMiLe-Based Transition Belief Update

The following pseudocode corresponds to `DeltaIOTConnector.updateTransitionBelief(action, nextstate)` (lines 517–597). Arrays are indexed by `[state][action][nextState]`; \(N_S\) is the number of POMDP states.

```
Algorithm: updateTransitionBelief(action, nextstate)
Input:  action   ∈ {0, 1}     // adaptation action taken (e.g. DTP=0, ITP=1)
        nextstate ∈ {0..N_S−1} // observed next state after the action
Side-effect: p.transitionBeliefCurr (and thus the world model W) is updated

Notation:
  B_curr  = p.transitionBeliefCurr   // current belief (Dirichlet pseudo-counts), shape [N_S][N_A][N_S]
  B_reset = p.transitionBeliefReset  // flat prior (Dirichlet pseudo-counts), same shape
  N_S    = number of states
  N_A    = number of actions (here 2)
  eps    = small positive constant (avoid log(0), ensure γ > 0)

────────────────────────────────────────────────────────────────────────────────
STEP 1: Deep copy beliefs (work on copies so we do not overwrite POMDP until final commit)
────────────────────────────────────────────────────────────────────────────────

  B_curr_temp[s][a][s']  := B_curr[s][a][s']   for all s, a, s'
  B_reset_temp[s][a][s'] := B_reset[s][a][s']  for all s, a, s'

────────────────────────────────────────────────────────────────────────────────
STEP 2: Bayesian update (Δ) — add pseudo-count for the observed transition
────────────────────────────────────────────────────────────────────────────────

  For s = 0 to N_S − 1:
    B_curr_temp[s][action][nextstate]  += 1.0
    B_reset_temp[s][action][nextstate] += 1.0

  // Interpretation: We observed (action, nextstate). Under partial observability,
  // any prior state s could have led to this; we add one count to (s, action, nextstate)
  // in both the current belief and the flat prior. This is the Δ term in the SMiLe rule.

────────────────────────────────────────────────────────────────────────────────
STEP 3: Compute surprise measures (all three; one will be used for γ)
────────────────────────────────────────────────────────────────────────────────

  // Confidence-Corrected Surprise (CC): belief-weighted KL(current || flat|observation)
  surprise_CC := ConfidenceCorrectedSurprise(B_curr, B_reset_temp, action, nextstate)
  logSurprise_CC := log(max(eps, surprise_CC))

  // Bayes Factor Surprise (BF): log( P_reset(obs) / P_curr(obs) ); already in log space
  logSurprise_BF := max(eps, BayesFactorSurprise(B_curr, B_reset, action, nextstate))

  // Mutual Information Surprise (MIS): change in MI over time (prior entropy − posterior entropy)
  MIS_result := CalculateAndStoreMIS(B_curr, B_curr_temp, action, nextstate, moteId, timestep)
  current_MIS := MIS_result.mis
  abs_MIS     := |current_MIS|
  scaled_MIS  := max(eps, abs_MIS)
  logSurprise_MIS := log(scaled_MIS)

────────────────────────────────────────────────────────────────────────────────
STEP 4: Select surprise for γ and convert to linear scale S
────────────────────────────────────────────────────────────────────────────────

  If surpriseMeasureForGamma == "CC"  then logSurprise := logSurprise_CC
  If surpriseMeasureForGamma == "BF"  then logSurprise := logSurprise_BF
  If surpriseMeasureForGamma == "MIS" then logSurprise := logSurprise_MIS

  S := exp(logSurprise)   // surprise in linear scale for γ formula

────────────────────────────────────────────────────────────────────────────────
STEP 5: Compute adaptation rate γ (SMiLe formula)
────────────────────────────────────────────────────────────────────────────────

  p_c := 0.5                    // probability of change (domain parameter, 0 < p_c < 1)
  m   := p_c / (1 − p_c)       // rate of change in the environment

  // γ = m*S / (1 + m*S), implemented as 1 / (1 + 1/(m*S)) for numerical stability
  gamma := 1 / (1 + 1/(m * S))

  gamma := max(eps, gamma)     // ensure minimum learning rate
  gamma := min(1.0, gamma)     // ensure γ ∈ [0, 1]

────────────────────────────────────────────────────────────────────────────────
STEP 6: SMiLe update — blend current (observed) and flat (observed) beliefs
────────────────────────────────────────────────────────────────────────────────

  // SMiLe rule in pseudo-count space:
  //   B_new = (1 − γ) * B_curr_observed + γ * B_reset_observed
  // Both B_curr_temp and B_reset_temp already include the +1.0 update (Step 2).
  // Only the slice for the taken action is blended.

  For s = 0 to N_S − 1:
    For s' = 0 to N_S − 1:
      B_curr_temp[s][action][s'] := (1 − gamma) * B_curr_temp[s][action][s']
                                  + gamma * B_reset_temp[s][action][s']

────────────────────────────────────────────────────────────────────────────────
STEP 7: Commit updated belief to POMDP
────────────────────────────────────────────────────────────────────────────────

  p.transitionBeliefCurr := B_curr_temp

  // The world model W (transition probabilities) is now updated implicitly:
  //   P(s' | s, a) = B_curr[s][a][s'] / Σ_{s''} B_curr[s][a][s'']
  // This is computed on demand in getTransitionProbability(s, a, s').

────────────────────────────────────────────────────────────────────────────────
Optional: Logging (append to files)
────────────────────────────────────────────────────────────────────────────────

  appendToFile("surpriseBF.txt",  exp(logSurprise_BF),  moteId, timestep)
  appendToFile("gamma.txt",       gamma,                 moteId, timestep)
  appendToFile("surpriseCC.txt",  exp(logSurprise_CC),  moteId, timestep)
  appendToFile("surpriseMIS.txt", current_MIS,           moteId, timestep)
```
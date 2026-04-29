# ERPerseus: Entropy-Regularised Perseus — Algorithm Description

## 1. Overview

ERPerseus is a point-based value iteration (PBVI) algorithm for solving POMDPs. It extends the standard **Perseus** algorithm (Spaan & Vlassis, 2005) by replacing all hard-maximisation (`argmax`) operations with **softmax-weighted combinations**, controlled by a temperature parameter **λ**. When λ = 0, ERPerseus reduces exactly to standard Perseus. When λ > 0, the algorithm produces entropy-regularised policies that are smoother, more robust to perturbations in the belief state, and better able to avoid premature convergence to locally suboptimal actions.

The theoretical foundation comes from the Entropy-Regularised Point-Based Value Iteration (ERPBVI) framework of Delecki et al. (2024), which formulates a softmax value function over the POMDP belief space. ERPerseus adapts this to the randomised backup structure of Perseus.

---

## 2. Shared Structure: What ERPerseus Inherits from Perseus

The overall algorithmic skeleton is identical to Perseus. Both algorithms proceed through three phases:

### Phase 1 — Belief Point Sampling

A fixed set **B = {b₁, b₂, …, bₙ}** of reachable belief points is generated before value iteration begins. This is done by simulating **R** random trajectories of **H** steps through the POMDP, starting from the initial belief. At each step a random action is chosen, an observation is sampled from the observation model, and the belief is updated via Bayes rule. Any novel belief encountered is added to B.

**Implementation** (`getBeliefPoints`, lines 86–146): The method runs `beliefSamplingRuns` trajectories of `beliefSamplingSteps` each, collecting unique beliefs in a `HashSet`. After sampling, **corner beliefs** (deterministic beliefs with probability 1 on a single state) are appended. This ensures the value function is well-defined near the extremes of the belief simplex.

This phase is **identical** between Perseus and ERPerseus.

### Phase 2 — Value Function Initialisation

The value function **V₀** is initialised with one alpha-vector per action, where each alpha-vector encodes the immediate (one-step) reward:

```
αₐ(s) = R(s, a)    for all s ∈ S
```

A constant copy of these vectors, `immediateRewards` (called **Rᵢₘₘ** in the pseudocode), is retained for use during backups.

**Implementation** (`solve`, lines 461–474): Two identical lists are created — `V` (which will be iteratively updated) and `immediateRewards` (which remains fixed).

This phase is **identical** between Perseus and ERPerseus.

### Phase 3 — Iterative Value Improvement (Main Loop)

The main loop repeatedly calls the **backup stage** to produce Vₙₑₓₜ from V, then checks for convergence:

```
Δ = max_{b ∈ B} |Vₙₑₓₜ(b) − V(b)|
```

Iteration terminates when Δ < ε (the value function tolerance) or a wall-clock time limit is exceeded.

**Implementation** (`solve`, lines 480–498): A `while(true)` loop calls `backupStage`, computes the maximum value difference via `getValueDifference`, and breaks when convergence or time-out is reached.

This outer loop is **identical** between Perseus and ERPerseus.

---

## 3. The Backup Stage: Where ERPerseus Begins to Diverge

The backup stage maintains a working set **B̃ ⊆ B** of beliefs whose values have not yet been improved in the current iteration. It loops until B̃ is empty.

### Step 1 — Pre-compute Back-Projected Vectors (gkao)

For every combination of value vector **k** (indexing into V), action **a**, and observation **o**, a back-projected alpha-vector is computed:

```
gᵏₐ,ₒ(s) = Σ_{s' ∈ S} O(o | s', a) · T(s' | s, a) · αᵏ(s')
```

This encodes: "if I take action **a**, observe **o**, and then follow the policy implied by value vector **αᵏ**, what is my expected continuation value starting from state **s**?"

**Implementation** (`backupStage`, lines 184–206): A 3D array `gkao[k][a][o]` is filled with `AlphaVector` objects, each computed by the nested sum over next-states.

This pre-computation step is **identical** between Perseus and ERPerseus. The divergence occurs in how these gkao vectors are *consumed* during the backup.

### Step 2 — Randomised Backup Loop

A belief **b** is randomly sampled from B̃. A backup alpha-vector is computed at **b** (see Section 4 below — this is where the key differences live). If the new vector improves or matches the value at **b**, it is added to Vₙₑₓₜ; otherwise, the best existing vector from V is copied into Vₙₑₓₜ. Then B̃ is updated to contain only those beliefs not yet improved by Vₙₑₓₜ.

**Implementation** (`backupStage`, lines 210–249): The loop samples a random index into `Btilde`, calls `backup(...)`, compares `newValue >= oldValue`, and updates `Btilde` by filtering beliefs where `Vₙₑₓₜ(b') < V(b')`.

This randomised loop structure is **identical** between Perseus and ERPerseus. The difference lies entirely inside the `backup` function.

---

## 4. The Backup Operation: The Core Innovation

The backup at a single belief point **b** produces one alpha-vector that represents the best one-step lookahead value. It has two sub-stages: (A) computing a candidate alpha-vector per action, and (B) selecting/combining across actions. **Both sub-stages differ between Perseus and ERPerseus.**

### 4A. Per-Action Backup: Combining Over Observations

For each action **a** and observation **o**, we need to select or blend among the K back-projected vectors {gᵏₐ,ₒ}ₖ₌₁ᴷ to produce a single observation-level vector **αₐ,ₒ**.

#### Perseus (λ = 0): Hard Maximisation

Standard Perseus picks the single best vector by evaluating each at the current belief:

```
αₐ,ₒ = argmax_k  gᵏₐ,ₒ · b
```

**Implementation** (lines 332–346, the `else` branch): Iterates over all K vectors, computes the dot product with `b.getBelief()`, and keeps the one with the maximum value.

#### ERPerseus (λ > 0): Softmax-Weighted Blend

ERPerseus replaces the hard maximum with a softmax-weighted convex combination. Crucially, **the softmax weights are evaluated at the updated (successor) belief b'**, not at the current belief b:

1. **Compute successor belief:** b' = Update(b, a, o) using Bayes rule.

2. **Compute softmax weights over continuation vectors:**

```
wₖ = exp(gᵏₐ,ₒ · b' / λ) / Σⱼ exp(gʲₐ,ₒ · b' / λ)
```

3. **Blend the vectors:**

```
αₐ,ₒ(s) = Σₖ wₖ · gᵏₐ,ₒ(s)    for all s ∈ S
```

**Implementation** (lines 294–330, the `if (lambda > 0.0)` branch):

- `bPrime = pomdp.updateBelief(b, action, obs)` computes the successor belief.
- `dotProducts[k] = gkao[k][action][obs].getDotProduct(bPrimeBelief) / lambda` evaluates each continuation vector at b', scaled by λ.
- `weights = softmax(dotProducts)` applies numerically stable softmax (lines 557–579, using the max-subtraction trick).
- The weighted sum is computed entry-wise over states.

**Key insight:** By evaluating at b' rather than b, ERPerseus asks "which continuation is best *from where I'll actually be after taking this action and seeing this observation*", which is a more principled evaluation point for the next-step value.

**Key insight:** Rather than committing to a single continuation policy, the softmax blend hedges across multiple continuations. The temperature λ controls how much hedging occurs. As λ → 0, the weights concentrate on the argmax and the algorithm recovers standard Perseus.

### Aggregating Observation Vectors (Shared)

After computing αₐ,ₒ for each observation, both algorithms aggregate identically:

```
αₐ(s) = R(s, a) + γ · Σ_{o ∈ O} αₐ,ₒ(s)
```

**Implementation** (lines 352–368): The observation vectors are summed, multiplied by the discount factor γ, and the immediate reward vector for the action is added.

### 4B. Action Selection: Combining Across Actions

After computing one candidate alpha-vector per action {αₐ}ₐ∈A, the algorithm must produce a single final backup vector.

#### Perseus (λ = 0): Argmax Over Actions

Standard Perseus picks the action whose candidate vector has the highest expected value at the current belief:

```
a* = argmax_a  αₐ · b
αbackup = αₐ*
```

**Implementation** (lines 407–418, the `else` branch): Iterates over `ga`, takes dot products with `b.getBelief()`, and returns the maximising vector.

#### ERPerseus (λ > 0): Softmax Policy Over Actions

ERPerseus computes a full probability distribution over actions and returns a weighted blend:

1. **Compute Q-values:**

```
Q(b, a) = αₐ · b
```

2. **Form softmax policy:**

```
π(a | b) = exp(Q(b, a) / λ) / Σ_{a'} exp(Q(b, a') / λ)
```

3. **Blend action vectors:**

```
αbackup(s) = Σₐ π(a | b) · αₐ(s)    for all s ∈ S
```

4. **Label the vector** with the most probable action (for logging/policy extraction):

```
action label = argmax_a  π(a | b)
```

**Implementation** (lines 374–405, the `if (lambda > 0.0)` branch):

- Q-values are computed as `ga.get(action).getDotProduct(b.getBelief()) / lambda`.
- `actionProbs = softmax(qValues)` produces the policy distribution.
- The weighted alpha is computed entry-wise: `weightedAlpha[s] += actionProbs[action] * ga.get(action).getEntry(s)`.
- The action label is set to the action with highest probability (`bestAction`).

**Key insight:** The final backup vector is no longer tied to a single action. It represents the expected value under a **stochastic** policy. This means the value function implicitly accounts for the entropy bonus of maintaining action diversity, which is the hallmark of entropy-regularised RL.

---

## 5. Stochastic Policy Execution at Runtime

At runtime, when ERPerseus selects an action given a belief state, it uses **categorical sampling** from the softmax policy distribution rather than deterministic argmax. The Q-values are computed by evaluating the converged alpha-vectors at the current belief, softmax is applied with temperature λ, and an action is sampled proportionally.

This means that even after the value function has converged, the *execution* policy retains controlled stochasticity. This is a further departure from standard Perseus, which executes deterministically by picking the action associated with the best alpha-vector at the current belief.

---

## 6. Summary of Differences

| Aspect | Perseus (λ = 0) | ERPerseus (λ > 0) |
|--------|------------------|---------------------|
| **Observation-level vector selection** (within backup) | `argmax_k gᵏₐ,ₒ · b` — picks a single best continuation vector, evaluated at current belief **b** | Softmax-weighted blend over all K continuation vectors, evaluated at **successor belief b'** |
| **Action selection** (within backup) | `argmax_a αₐ · b` — commits to a single action | Softmax policy π(a\|b), returns weighted combination of all action vectors |
| **Backup vector** | A single alpha-vector corresponding to one action | A convex combination of alpha-vectors weighted by the softmax policy |
| **Policy execution** | Deterministic: always pick the best action | Stochastic: sample from softmax distribution |
| **Sensitivity to near-ties** | Arbitrary: small numerical noise can flip the chosen action | Smooth: near-equal Q-values produce near-uniform mixing |
| **Exploration** | None after planning — the policy is fixed | Built-in: all actions with non-zero Q-values retain non-zero selection probability |
| **Computational overhead** | Minimal per backup | Additional O(K) softmax computation per (action, observation) pair, plus one belief update per observation |

---

## 7. Numerical Stability

The Java implementation uses a **numerically stable softmax** (lines 557–579). Before exponentiating, the maximum value is subtracted from all inputs:

```java
double max = Double.NEGATIVE_INFINITY;
for (double v : x) { if (v > max) max = v; }
// ...
result[i] = Math.exp(x[i] - max);
```

This prevents overflow when Q-values are large (which they are in this domain). The paper's pseudocode refers to this as the `logsumexp()` operation. This is a standard technique but is worth noting because without it, the algorithm would fail numerically given the reward magnitudes involved.

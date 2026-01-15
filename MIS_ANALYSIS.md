# Analysis: MIS vs Bayes Factor Surprise Scale Difference

## Executive Summary

The Mutual Information Surprise (MIS) values are **significantly smaller** than Bayes Factor (BF) Surprise values. This is **mathematically valid** and expected due to fundamental differences in how these measures are defined and their inherent bounds.

## 1. Fundamental Scale Differences

### Bayes Factor Surprise
- **Definition**: `log(predProbReset / predProbCurr)`
- **Range**: Unbounded (theoretically -∞ to +∞)
- **Typical values in your data**: After `exp()`, values range from ~1.0 to ~6.25
- **Actual log values**: 0 to ~1.83
- **Interpretation**: Ratio of probabilities, which can be large when probabilities differ significantly

### Mutual Information Surprise
- **Definition**: `MIS = MI[current] - MI[current - lookback]`
  - Where `MI = H(prior) - H(posterior)` (entropy reduction)
- **Range**: Bounded by entropy constraints
- **Typical values in your data**: -0.57 to ~0.58
- **Interpretation**: Change in information gain over time

## 2. Why MIS Values Are Naturally Smaller

### 2.1 Entropy Bounds
For a Dirichlet distribution with `k` states:
- **Maximum entropy**: `H_max = log(k)` (uniform distribution)
- **Minimum entropy**: `H_min ≈ 0` (deterministic distribution)
- **Entropy range**: `[0, log(k)]`

In your case with 4 states (based on POMDP structure):
- Maximum entropy: `log(4) ≈ 1.386 nats`
- Maximum possible MI: `H(prior) - H(posterior) ≤ log(4) ≈ 1.386`
- Typical MI values: Much smaller (0.01 to 0.5) because:
  - Prior and posterior are usually both uncertain (high entropy)
  - Single observation (+1.0 update) reduces entropy only slightly
  - The difference `H(prior) - H(posterior)` is typically small

### 2.2 MIS as a Difference of Differences
MIS is calculated as:
```
MIS = MI[t] - MI[t-lookback]
    = (H_prior[t] - H_posterior[t]) - (H_prior[t-lookback] - H_posterior[t-lookback])
```

This is a **second-order difference**, which tends to be smaller than first-order measures like BF.

### 2.3 Comparison to BF Scale
- **BF Surprise**: `log(prob_ratio)` where ratio can be 1:1 to 6:1 → log values 0 to 1.83
- **MIS**: Difference of entropy differences → typically 0.01 to 0.5

**The scale difference is mathematically valid and expected.**

## 3. Potential Issues in Current Implementation

### 3.1 Belief Naming Confusion (Not a Bug)

**Current Code (Line 261)**:
```java
entropy += p.getInitialBelief().getBelief(stateIndex) * dirichlet_entropy(...)
```

**Note**: Despite the name `getInitialBelief()`, this actually returns the **current belief** that is updated each timestep (see line 575: `p.setInitialBelief(b)` after belief update). The comment on line 574 confirms: "Despite being called initialBelief, consider this the updated current belief for states".

**Conclusion**: The implementation is **correct** - it uses the current belief state. The naming is just confusing. Both `bayesFactorSurprise()` and `getMoteEntropy()` correctly use the current belief state.

### 3.2 Entropy Calculation Correctness

The `dirichlet_entropy()` function (lines 223-243) appears correct:
- Uses standard Dirichlet entropy formula: `H(Dir(α)) = ln B(α) + (α₀ - k)ψ(α₀) - Σ(αᵢ - 1)ψ(αᵢ)`
- Where `B(α)` is the multivariate beta function
- This is the correct formula

### 3.3 MI Calculation Correctness

The MI calculation (line 408) is correct:
```java
double mutualInformation = priorEntropy - posteriorEntropy;
```

This correctly computes: `I(X;Y) = H(X) - H(X|Y) = H(prior) - H(posterior)`

## 4. Methods to Improve/Debias MI Calculation

### 4.1 Use Current Belief Instead of Initial Belief

**Implementation**:
```java
private double getMoteEntropy(double[][][] transitionBelief, int action, int nextstate, BeliefPoint currentBelief) {
    double entropy = 0.0;
    for (int stateIndex = 0; stateIndex < p.getNumStates(); stateIndex++) {
        entropy += currentBelief.getBelief(stateIndex) * dirichlet_entropy(transitionBelief[stateIndex][action]);
    }
    return entropy;
}
```

**Benefits**:
- Reflects actual current state of knowledge
- More accurate entropy estimation
- Better captures learning dynamics

### 4.2 Normalize MI by Maximum Possible Entropy

**Issue**: Raw MI values are small because they're bounded by entropy limits.

**Solution**: Normalize by theoretical maximum:
```java
double maxEntropy = Math.log(p.getNumStates()); // log(k) for k states
double normalizedMI = mutualInformation / maxEntropy; // Scale to [0, 1]
```

**Benefits**:
- Makes MI values more interpretable
- Allows comparison across different state space sizes
- Values in [0, 1] range

### 4.3 Use Bias-Corrected MI Estimator

**Issue**: Entropy estimation from Dirichlet parameters may have bias, especially with small sample sizes.

**Solution**: Use bias correction for small sample sizes:
```java
// For Dirichlet with small alpha values, apply bias correction
// See "Bias correction for entropy estimation" literature
double biasCorrection = 0.0;
if (alpha0 < 10.0) { // Small sample size
    // Approximate bias correction: -k/(2*alpha0) for small alpha0
    biasCorrection = -k / (2.0 * alpha0);
}
double correctedEntropy = dirichlet_entropy(alpha) - biasCorrection;
```

**Note**: This is an approximation; exact bias correction for Dirichlet entropy is complex.

### 4.4 Use Conditional Entropy Instead of Marginal Entropy

**Current**: Uses expected entropy over all states.

**Alternative**: Compute entropy conditional on the observed transition:
```java
// Instead of averaging over all states, compute entropy for the specific transition
double conditionalEntropy = dirichlet_entropy(transitionBelief[observedState][action]);
```

**Trade-off**: 
- More specific to the actual observation
- But loses the POMDP uncertainty averaging

### 4.5 Scale MIS to Match BF Scale (If Needed)

If you want MIS values to be on a similar scale to BF for comparison:

```java
// Compute scale factor based on typical values
double bfMean = computeMeanBF(); // e.g., ~1.5
double misMean = computeMeanMIS(); // e.g., ~0.1
double scaleFactor = bfMean / misMean; // e.g., ~15

// Scale MIS
double scaledMIS = mis * scaleFactor;
```

**Warning**: This is purely for visualization/comparison and doesn't change the underlying information-theoretic meaning.

### 4.6 Use Alternative MI Formulations

**Option A: KL Divergence-Based MI**
```java
// MI = KL(posterior || prior) - can be larger than entropy difference
double miKL = computeKLDivergence(transitionBeliefPosterior, transitionBeliefPrior, action);
```

**Option B: Predictive Information**
```java
// Focus on predictive power rather than entropy reduction
double predictiveInfo = computePredictiveInformation(transitionBelief, action, nextstate);
```

## 5. Recommended Improvements

### Priority 1: Normalize MI Values
- **Impact**: Medium - improves interpretability
- **Effort**: Low - simple division
- **Code Change**: Divide MI by `log(numStates)`

### Priority 2: Document Scale Difference
- **Impact**: High - prevents confusion
- **Effort**: Low - add comments
- **Code Change**: Add documentation explaining why MIS values are smaller

### Priority 3: Consider Bias Correction (Optional)
- **Impact**: Low-Medium - may improve accuracy for small samples
- **Effort**: Medium - requires research into exact formula
- **Code Change**: Add bias correction term to entropy calculation

## 6. Code-Level Improvements (Optional)

If you want to improve the MI calculation or normalize values, here are specific code changes:

### 6.1 Normalize MI by Maximum Entropy

Add normalization to make MI values more interpretable:

```java
// In calculateAndStoreMIS(), after computing mutualInformation:
double maxPossibleEntropy = Math.log(p.getNumStates()); // log(k) for k states
double normalizedMI = mutualInformation / maxPossibleEntropy; // Scale to [0, 1]
// Use normalizedMI instead of mutualInformation if desired
```

### 6.2 Add Documentation Comments

Add comments explaining the scale difference:

```java
/**
 * Calculates Mutual Information Surprise (MIS) for a given mote and timestep.
 * 
 * NOTE: MIS values are typically much smaller than Bayes Factor Surprise values.
 * This is mathematically valid and expected because:
 * - MIS is bounded by entropy limits (max entropy = log(k) for k states)
 * - MIS is a second-order difference (difference of MI differences)
 * - Single observations cause small entropy reductions
 * 
 * Typical MIS range: -0.5 to 0.5
 * Typical BF range (after exp): 1.0 to 6.0 (log values: 0 to 1.8)
 * 
 * @param ... (existing parameters)
 * @return MIS value (typically much smaller than BF, but mathematically valid)
 */
```

### 6.3 Alternative: Use Relative MI Change

Instead of absolute MI difference, use relative change:

```java
// In calculateAndStoreMIS(), when computing MIS:
if (history.size() > lookback) {
    double miCurrent = history.get(history.size() - 1);
    double miPrevious = history.get(history.size() - 1 - lookback);
    
    // Absolute difference (current implementation)
    double misAbsolute = miCurrent - miPrevious;
    
    // Relative change (alternative)
    double misRelative = (miCurrent - miPrevious) / Math.max(Math.abs(miPrevious), eps);
    
    // Use misRelative if you want percentage-based change
}
```

## 7. Conclusion

**The smaller MIS values are mathematically valid** and expected due to:
1. Entropy bounds (max entropy = log(k))
2. MIS being a second-order difference
3. Single observations causing small entropy reductions

**The implementation is correct** - despite the confusing naming, `getInitialBelief()` actually returns the current belief state that's updated each timestep.

**The scale difference itself is not a bug** - it reflects the fundamental nature of information-theoretic measures vs. probability ratio measures. The smaller MIS values are mathematically valid and expected.

**Key Takeaways**:
- MIS values being smaller than BF is **normal and expected**
- The implementation is **mathematically correct**
- If you need larger values for comparison, consider normalization (divide by log(k))
- The inherent bounds of entropy make large MIS values unlikely

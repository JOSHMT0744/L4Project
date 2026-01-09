# Surprise-based BA-POMDP: Entropy-Regularized Point-Based Value Iteration with Adaptive Learning

## Research Contribution

This work presents a novel integration of **entropy-regularized POMDP solvers** with **surprise-based adaptive learning** for autonomous system adaptation. The key contributions are:

### 1. Entropy-Regularized Perseus (ERPerseus)
- **Contribution**: Extends the classic Perseus algorithm with entropy regularization using softmax value functions
- **Benefit**: Enables smoother policy learning and better exploration-exploitation balance compared to hardmax Perseus
- **Implementation**: Replaces deterministic action/observation selection with softmax-weighted combinations controlled by a temperature parameter (λ)

### 2. Mutual Information Surprise (MIS) for Adaptive Learning
- **Contribution**: Implements MIS as a signal of epistemic growth, quantifying the impact of new observations on mutual information
- **Theoretical Foundation**: Based on Theorem 1 from [Mutual Information Surprise: Rethinking Unexpectedness in Autonomous Systems](https://www.arxiv.org/pdf/2508.17403)
- **Application**: Uses MIS bounds (95% confidence interval) to dynamically adjust learning rate (gamma) in varSMiLE rule

### 3. varSMiLE Integration
- **Contribution**: Integrates variable Surprise-Minimizing Learning (varSMiLE) with POMDP planning
- **Adaptive Learning**: Learning rate (gamma) dynamically adjusts based on surprise measures (MIS, Bayes Factor, or Confidence-Corrected Surprise)
- **Benefit**: Enables the system to learn transition probabilities adaptively while maintaining stability

### 4. DeltaIoT Case Study
- **Application Domain**: Adaptive IoT network management with QoS optimization
- **Objectives**: Simultaneously minimize energy consumption (MEC) and reduce packet loss (RPL)
- **Demonstration**: Shows how entropy-regularized policies outperform deterministic policies in dynamic environments

### Key Advantages Over Baseline Methods

1. **Better Exploration**: Softmax policies explore more effectively than hardmax policies
2. **Adaptive Learning**: varSMiLE ensures learning rate adapts to system surprise
3. **Theoretical Guarantees**: MIS bounds provide statistical guarantees on surprise estimates
4. **Computational Efficiency**: Optimizations maintain Perseus's speed while adding regularization benefits

## Output Directory
`output_dir`

- `entropy.txt`
-- Calculates total entropy of the transition beliefs for each timestep by accumulating the calculated entropy on each mote in that timestep (weighted by the state beliefs)

- `gamma.txt`
-- Stores the mixing factor for the varSMiLE rule for each mote at each timestep

- `IoT.txt`

- `meanMIS.txt`
-- Belief weighted mean Mutual Information Surprise (MIS) across all motes at each timestep.

- `MECSat.txt`
-- Contains the MEC for the gateway after the update of each individual mote

- `MECSatProb.txt`
-- Probability that Mnimisation of energy consumption has been satisfied (calculated by adding the belief states that match up to MEC = True)

- `MECSattimestep.txt`
-- Gateway QoS values at the end of updating every mote
-- The last value for each timestep in MECSat logically matches that for each timestep in this file

- `RPLSat.txt`
-- Contains the RPL for the gateway after the update of each individual mote

- `RPLSatProb.txt`
-- Probability that the Reduction of Packet Loss has been satisfied (calculated by adding the belief states that match up to RPL = True)

- `RPLSattimestep.txt`
-- Gateway QoS values at the end of updating every mote
-- The last value for each timestep in RPLSat logically matches that for each timestep in this file

- `misBounds.txt`
-- Defined bounds for stable MIS (in accordance with the definition in [this paper](Mutual Information Surprise: Rethinking Unexpectedness in Autonomous Systems) at 95% confidence interval

- `mutualInformation.txt`
-- Total mutual information calculated for each mote at each timestep

- `SelectedAction.txt`
-- Action selected by planner at each timestep

- `surpriseBF.txt`
-- The Bayes Factor Surprise of the Bayesian-updated current transition belief(s) at each mote for each timestep

 - `surpriseCC.txt`
-- The Confidence Corrected Surprise of the Bayesian-updated current transition belief(s) at each mote for each timestep

## Configuration

- **Algorithm Type**: `gip` (exact) or `perseus` (approximate)
- **LP Solver**: `gurobi`, `joptimizer`, or `lpsolve` (default)
- **Pruning Method**: `standard` or `accelerated`
- **Time Limit**: Maximum execution time in seconds
- **Output Directory**: Where results are written (default: `output_dir`)
- **Domain Directory**: Where POMDP files are located (default: `domains`)

Edit `src/solver.config` to customize these settings.

## Reproducing the Research

### Prerequisites

1. **Java Development Kit (JDK)**: Version 8 or higher
2. **Python 3.7+**: For visualization scripts
3. **Eclipse IDE** (optional): For development and debugging
4. **DeltaIoT Simulator**: Included as `Simulator.jar` in `libraries/`

### Step 1: Environment Setup

#### 1.1 Clone/Download the Repository
```bash
# Navigate to your workspace
cd /path/to/workspace

# The project should be in L4Project/ directory
```

#### 1.2 Install Python Dependencies
```bash
cd L4Project

# Create virtual environment (recommended)
python -m venv .venv

# Activate virtual environment
# On Windows:
.venv\Scripts\activate
# On Linux/Mac:
source .venv/bin/activate

# Install required packages
pip install -r requirements.txt
```

#### 1.3 Verify Java Setup
```bash
java -version  # Should show JDK 8 or higher
javac -version # Should show JDK 8 or higher
```

### Step 2: Configure the Solver

Edit `src/solver.config` to select your algorithm:

```properties
# For Entropy-Regularized Perseus (recommended)
algorithmType=erperseus

# For standard Perseus (baseline)
# algorithmType=perseus

# For Entropy-Regularized PBVI
# algorithmType=erpbvi

# For Fast Entropy-Regularized PBVI
# algorithmType=faserpbvi
```

**Key Configuration Parameters:**
- `beliefSamplingRuns`: Number of belief point trajectories (default: 100)
- `beliefSamplingSteps`: Length of each trajectory (default: 20)
- `valueFunctionTolerance`: Convergence threshold (default: 0.01)
- `timeLimit`: Maximum solve time per planning step (default: 60 seconds)

### Step 3: Run Experiments

#### Method 1: Run from Eclipse IDE (Recommended for Development)

1. Import the project into Eclipse:
   - File → Import → Existing Projects into Workspace
   - Select the `L4Project` directory
   - Ensure all JAR files in `libraries/` are on the build path

2. Configure Run Settings:
   - Right-click `src/main/SolvePOMDP.java`
   - Run As → Java Application
   - Or create a Run Configuration with:
     - Main class: `main.SolvePOMDP`
     - Working directory: `${workspace_loc:L4Project}`

3. Execute:
   - The program will run for the number of timesteps specified in `runCaseIoT()` (default: 400)
   - Results will be written to `output_dir/`
   - Charts will be generated automatically after completion

#### Method 2: Run from Command Line

```bash
# Navigate to project root
cd L4Project

# Compile the project
# On Windows:
javac -cp "libraries/*" -d bin -sourcepath src src/main/*.java src/**/*.java

# On Linux/Mac:
javac -cp "libraries/*" -d bin -sourcepath src src/main/*.java src/**/*.java

# Run the program
# On Windows:
java -cp "bin;libraries/*" main.SolvePOMDP

# On Linux/Mac:
java -cp "bin:libraries/*" main.SolvePOMDP
```

#### Method 3: Run as JAR (if packaged)

```bash
java -jar SolvePOMDP.jar
```

### Step 4: Analyze Results

After execution, check the following:

1. **Output Files** (in `output_dir/`):
   - `gamma.txt`: Learning rate adaptation over time
   - `surpriseMIS.txt`: Mutual Information Surprise values
   - `MISBounds.txt`: Statistical bounds for MIS (95% confidence)
   - `MECSat.txt` / `RPLSat.txt`: QoS satisfaction metrics
   - `SelectedAction.txt`: Actions chosen by the planner

2. **Visualizations**:
   - Charts are automatically generated and opened in your browser
   - If charts don't open automatically, run manually:
     ```bash
     cd L4Project
     python createCharts.py
     ```

3. **Key Metrics to Evaluate**:
   - **Expected Value**: Should increase over time as the system learns (from ~450 to 800+)
   - **Gamma (Learning Rate)**: Should adapt based on surprise (check `gamma.txt`)
   - **MIS Values**: Should show epistemic growth patterns
   - **QoS Satisfaction**: MEC and RPL should improve over time

### Step 5: Reproducing Specific Experiments

#### Experiment 1: Compare ERPerseus vs Perseus

1. Set `algorithmType=perseus` in `solver.config`
2. Run experiment, save results
3. Set `algorithmType=erperseus` in `solver.config`
4. Run experiment again
5. Compare expected values and action selection patterns

#### Experiment 2: Test Different Surprise Measures

In `SolvePOMDP.java`, modify line 608:
```java
// For MIS-based learning (recommended)
deltaConnector.setSurpriseMeasureForGamma("MIS");

// For Bayes Factor Surprise
deltaConnector.setSurpriseMeasureForGamma("BF");

// For Confidence-Corrected Surprise
deltaConnector.setSurpriseMeasureForGamma("CC");
```

#### Experiment 3: Vary Lambda (Temperature) Parameter

In `SolvePOMDP.java`, modify line 293:
```java
// Higher lambda = more exploration (default: 0.5)
this.solver = new ERPerseus(sp, new Random(222), 0.5);

// Lower lambda = more exploitation (e.g., 0.1)
this.solver = new ERPerseus(sp, new Random(222), 0.1);

// Higher lambda = more exploration (e.g., 1.0)
this.solver = new ERPerseus(sp, new Random(222), 1.0);
```

### Step 6: Expected Results

**Successful Run Indicators:**
- Expected values start around 450-480 and increase to 800+ over 400 timesteps
- Gamma values adapt between 0.0001 and 1.0 based on surprise
- MIS values show both positive (epistemic growth) and negative (over-exploitation) phases
- Action selection shows adaptive behavior (not always the same action)
- Charts display smooth learning curves

**Baseline Comparison:**
- **Perseus (hardmax)**: Expected values may plateau around 500-700
- **ERPerseus (softmax)**: Expected values should reach 800+ with proper learning

### Troubleshooting Reproduction

1. **Low Expected Values (< 500)**:
   - Check that `gamma` values in `gamma.txt` are not stuck at minimum (0.0001)
   - Verify MIS calculation is working (check `surpriseMIS.txt` has non-zero values after initial timesteps)
   - Ensure `lambda` parameter is set appropriately (0.5 is recommended)

2. **No Learning (Gamma Always Minimum)**:
   - Verify surprise measure is set correctly
   - Check that transition beliefs are being updated (see `updateTransitionBelief` in `DeltaIOTConnector.java`)
   - Ensure MIS history has enough entries (lookback = 4)

3. **Charts Not Generating**:
   - Verify Python virtual environment is activated
   - Check that `output_dir/` contains all required data files
   - Run `python createCharts.py` manually to see error messages

4. **Inconsistent Results**:
   - Ensure random seed is set consistently (currently `new Random(222)`)
   - Check that all motes are being processed (should see 14 motes per timestep)
   - Verify QoS data is being retrieved correctly (check for "Unknown value data" warnings)

## Running the Program

## Program Execution Flow

1. **Initialization**: Reads configuration from `src/solver.config`
2. **POMDP Loading**: Loads the POMDP domain from `domains/IoT.POMDP`
3. **Simulation Loop**: Runs for 500 timesteps (configurable in code)
   - **Monitor**: Collects network state from IoT simulation
   - **Analyze**: Computes belief states and QoS metrics
   - **Plan**: Solves POMDP to select optimal action
   - **Execute**: Applies action to the network
4. **Post-processing**: Generates charts using Python script

## Perseus Algorithm (Approximate Solver)

The `SolverApproximate.java` class implements the **Perseus algorithm**, a point-based value iteration method for solving POMDPs. This is an approximate solver that is more computationally efficient than exact methods for large POMDPs.

### Overview

Perseus is a point-based value iteration algorithm that:
- Samples a set of belief points from the belief space
- Iteratively improves a value function over these points
- Uses alpha vectors to represent the value function
- Converges to an approximate optimal policy

### Algorithm Walkthrough

#### 1. **Belief Point Sampling** (`getBeliefPoints()`)

The algorithm starts by generating a representative set of belief points:

- **Initial Belief**: Adds the POMDP's initial belief state
- **Random Trajectories**: For each sampling run:
  - Starts from the initial belief
  - For each step in the trajectory:
    - Randomly selects an action
    - Samples an observation based on the action-observation probability
    - Updates the belief using Bayesian filtering: `b' = updateBelief(b, action, observation)`
    - Adds the new belief point to the set (if not already present)
- **Corner Beliefs**: Adds deterministic belief points where each state has probability 1.0 (one per state)

This creates a diverse set of belief points that represent different regions of the belief space.

#### 2. **Initialization** (`solve()`)

The main solve method initializes:

- **Value Function V**: Starts with one alpha vector per action, where each vector contains the immediate rewards for that action across all states
- **Immediate Rewards**: Stores the reward vectors for each action (used in backup computations)

#### 3. **Value Iteration Loop** (`backupStage()`)

The algorithm iteratively improves the value function:

**For each stage:**
1. **Initialize gkao vectors**: Pre-computes intermediate vectors for all combinations of:
   - Previous value vectors (k)
   - Actions (a)
   - Observations (o)
   
   These represent: `gkao[k][a][o][s] = Σ(s') P(o|s',a) * P(s'|s,a) * V[k][s']`

2. **Backup Process**: While there are belief points that haven't been improved (`Btilde`):
   - Randomly selects a belief point `b` from `Btilde`
   - Computes `backup(b)` to get a new alpha vector
   - If the new vector improves the value at `b`, adds it to `Vnext`
   - Otherwise, keeps the best existing vector from `V`
   - Updates `Btilde` to contain only belief points where the new value function hasn't improved

3. **Convergence Check**: 
   - Computes the maximum value difference across all belief points
   - Stops when the difference is below tolerance OR time limit is exceeded

#### 4. **Backup Computation** (`backup()`)

For a given belief point `b`, computes the optimal alpha vector:

1. **For each action `a`**:
   - **For each observation `o`**: Finds the best gkao vector (the one maximizing dot product with `b`)
   - **Sums observation vectors**: Combines all observation vectors for action `a`
   - **Applies discount factor**: Multiplies by γ (discount factor)
   - **Adds immediate reward**: Combines with the immediate reward vector for action `a`
   - This gives: `ga[a] = R(a) + γ * Σ(o) max_k(gkao[k][a][o])`

2. **Selects best action**: Finds the action vector `ga[a]` that maximizes the dot product with belief `b`

3. **Returns**: The optimal alpha vector for belief point `b`

#### 5. **Convergence** (`getValueDifference()`)

Computes the maximum improvement across all belief points:
- For each belief point, calculates: `Vnext(b) - V(b)`
- Returns the maximum difference
- Algorithm stops when this difference is below the tolerance threshold

### Key Concepts

- **Alpha Vectors**: Represent linear pieces of the value function. Each vector has one value per state and an associated action.
- **Belief Points**: Probability distributions over states. The algorithm only evaluates the value function at sampled points rather than the entire continuous belief space.
- **Point-Based Approach**: More efficient than exact methods because it focuses computation on reachable belief regions rather than the entire belief simplex.

### Configuration Parameters

The algorithm behavior is controlled by `solver.config`:
- **Belief Sampling Runs**: Number of random trajectories to generate
- **Belief Sampling Steps**: Length of each trajectory
- **Value Function Tolerance**: Convergence threshold
- **Time Limit**: Maximum computation time

### Output

The solver produces:
- **Alpha Vectors**: Saved to `{domain}.alpha` file, representing the value function
- **Expected Value**: The value at the initial belief state
- **Selected Action**: The optimal action for the initial belief

This approximate method trades exact optimality for computational efficiency, making it suitable for larger POMDPs where exact methods would be intractable.

## Output Files

The program generates several output files in the `output_dir/` directory:

- **`entropy.txt`**: Total entropy of transition beliefs per timestep
- **`gamma.txt`**: Mixing factor for varSMiLE rule per mote/timestep
- **`meanMIS.txt`**: Mean Mutual Information Surprise (MIS) per timestep
- **`MECSat.txt`**: Energy consumption satisfaction per mote/timestep
- **`MECSatProb.txt`**: Probability that MEC (Minimization of Energy Consumption) is satisfied
- **`MECSattimestep.txt`**: Gateway QoS values at end of each timestep
- **`RPLSat.txt`**: Packet loss satisfaction per mote/timestep
- **`RPLSatProb.txt`**: Probability that RPL (Reduction of Packet Loss) is satisfied
- **`RPLSattimestep.txt`**: Gateway packet loss at end of each timestep
- **`misBounds.txt`**: MIS stability bounds (95% confidence interval)
- **`mutualInformation.txt`**: Total mutual information per mote/timestep
- **`SelectedAction.txt`**: Actions selected by planner per timestep
- **`surpriseBF.txt`**: Bayes Factor Surprise per mote/timestep
- **`surpriseCC.txt`**: Confidence-Corrected Surprise per mote/timestep
- **`IoT.alpha`**: Alpha vectors from POMDP solution

## Visualization

After the main program completes, it automatically runs `createCharts.py` to generate interactive visualizations:

- Mean MIS over time with error bounds
- Mean learning rate (Gamma) over time
- Surprise metrics comparison (Bayes Factor, Confidence-Corrected, MIS)
- MEC and RPL satisfaction plots
- Satisfaction distribution violin plots

The charts will open automatically in your default web browser (via Plotly).

## Troubleshooting

### Common Issues

1. **"solver.config not found"**
   - Ensure you're running from the project root directory
   - Check that `src/solver.config` exists

2. **"domains/IoT.POMDP not found"**
   - Verify the `domains/` directory exists in the project root
   - Check that `IoT.POMDP` file is present

3. **Python script fails**
   - Ensure Python virtual environment is set up: `.venv\Scripts\python.exe` (Windows) or `.venv/bin/python` (Linux/Mac)
   - Verify Python dependencies are installed: `pip install -r requirements.txt`
   - Check that `output_dir/` contains the required data files

4. **ClassNotFoundException**
   - Verify all JAR files are in the `libraries/` directory
   - Check that the classpath includes all JAR files

5. **LP Solver errors**
   - If using Gurobi, ensure Gurobi license is configured
   - Consider switching to `lpsolve` in `solver.config` (default, no license required)

6. **"Unknown value data" warnings (resolved)**
   - Previous versions of the code could display "Unknown value data" warnings from the DeltaIoT simulator when accessing QoS data before it was fully ready
   - This has been fixed by implementing stream redirection and validation-based waiting mechanisms
   - The program now automatically waits for QoS data to be fully populated before accessing it, suppressing these warnings from console output

## License

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

## Author

Based on SolvePOMDP by Erwin Walraven (Delft University of Technology)
Extended for IoT adaptation use case.

## Implementation Details

### Algorithm Variants

#### ERPerseus (Entropy-Regularized Perseus)
- **Location**: `src/solver/ERPerseus.java`
- **Key Feature**: Softmax value functions with temperature parameter λ
- **Backup Function**: Uses softmax for both observation and action selection when λ > 0
- **Default Lambda**: 0.5 (configurable in `SolvePOMDP.java`)

#### ERPBVI (Entropy-Regularized Point-Based Value Iteration)
- **Location**: `src/solver/ERPBVI.java`
- **Key Feature**: Standard entropy-regularized PBVI with belief update caching
- **Optimizations**: Btilde-style filtering, optimized belief expansion

#### fastERPBVI (Fast Entropy-Regularized PBVI)
- **Location**: `src/solver/fastERPBVI.java`
- **Key Feature**: Optimized version with additional performance improvements

### varSMiLE Implementation

The variable Surprise-Minimizing Learning rule is implemented in `DeltaIOTConnector.java`:

```java
// Gamma calculation based on surprise measure
double gamma = 1.0 / (1.0 + Math.exp(-logSurprise) / m);
gamma = Math.max(0.0001, gamma); // Minimum learning rate

// varSMiLE update rule
new_belief = (1 - gamma) * updated_current + gamma * updated_flat_prior
```

### MIS Bounds Calculation

MIS bounds are calculated according to Theorem 1:
- **Confidence Level (ρ)**: 0.05 (95% confidence interval)
- **Lookback Period (m)**: 4 timesteps
- **Bounds**: Computed using statistical bounds on MLE-based mutual information estimates

## Recent Improvements

### Entropy Regularization Integration
- **ERPerseus**: Successfully integrated softmax value functions into Perseus algorithm
- **Performance**: Maintains Perseus's computational efficiency while enabling better exploration
- **Adaptive Learning**: varSMiLE rule dynamically adjusts learning rate based on surprise measures

### QoS Data Synchronization Fix
The program now includes robust handling of QoS (Quality of Service) data retrieval from the DeltaIoT simulator:

- **Stream Redirection**: Both stdout and stderr are redirected during simulator operations to suppress transient warnings
- **Validation-Based Waiting**: The `waitForQoSDataReady()` method validates QoS data completeness before returning, ensuring all entries have valid values (no NaN, infinity, or default error values)
- **Automatic Retry**: If warnings are detected during simulation runs, the program automatically waits until data is fully ready before proceeding
- **Clean Console Output**: All "Unknown value data" warnings are suppressed from appearing in the console while maintaining data integrity

This ensures reliable QoS data access without console noise, improving both user experience and program reliability.

### MIS-Based Adaptive Learning
- **Implementation**: Full MIS calculation with entropy-based mutual information
- **Bounds**: Statistical bounds computed with 95% confidence interval
- **Integration**: MIS used for dynamic gamma adjustment in varSMiLE rule
- **Minimum Learning Rate**: Ensures learning continues even when surprise is very low (gamma ≥ 0.0001)

### Key References

1. **Mutual Information Surprise**: [Mutual Information Surprise: Rethinking Unexpectedness in Autonomous Systems](https://www.arxiv.org/pdf/2508.17403)
2. **Perseus Algorithm**: Spaan, M. T. J., & Vlassis, N. (2005). Perseus: Randomized point-based value iteration for POMDPs. *Journal of artificial intelligence research*, 24, 195-220.
3. **Entropy-Regularized MDPs**: Delecki, Harrison, et al. "Entropy-regularized Point-based Value Iteration." arXiv preprint arXiv:2402.09388 (2024).
4. **varSMiLE**: Liakoni, Vasiliki, et al. "Learning in volatile environments with the bayes factor surprise." Neural Computation 33.2 (2021): 269-340.

## Additional Notes

- The program is hardcoded to run `IoT.POMDP` domain (see `main` method)
- Number of timesteps is configurable in `runCaseIoT` method (default: 400)
- The Python virtual environment path is automatically detected (Windows: `.venv\Scripts\python.exe`, Linux/Mac: `.venv/bin/python`)
- Random seed is fixed to 222 for reproducibility (see `SolvePOMDP.java` line 290)

## Future Work / TODO

- Evaluate MECSat and RPLSat grouped by each mote, to see which motes are contributing more strongly to extreme values
- Implement additional entropy-regularized solvers
- Extend MIS bounds to support different confidence levels at the mote-level, in correspondance with expert definitions
- Propose an adaptive hybrid deterministic-stochastic policy that chooses whether to pick the best action stochastically or deterministically depending on the model's observations of the system.
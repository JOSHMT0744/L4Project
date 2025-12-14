# Surprise-based BA-POMDP

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

## Running the Program

### Method 1: Run from Eclipse IDE

1. Open `src/main/SolvePOMDP.java`
2. Right-click on the file → Run As → Java Application
3. Or use the main method's run configuration

### Method 2: Run from Command Line

#### Compile the project:
```bash
# Navigate to project root
cd L4Project

# Compile (adjust classpath as needed for your setup)
javac -cp "libraries/*" -d bin src/main/*.java src/**/*.java
```

#### Run the program:
```bash
# Run from project root directory
java -cp "bin;libraries/*" main.SolvePOMDP

# On Linux/Mac, use colon instead of semicolon:
java -cp "bin:libraries/*" main.SolvePOMDP
```

### Method 3: Run as JAR (if packaged)

If you have a packaged JAR file:
```bash
java -jar SolvePOMDP.jar
```

## Program Execution Flow

1. **Initialization**: Reads configuration from `src/solver.config`
2. **POMDP Loading**: Loads the POMDP domain from `domains/IoT.POMDP`
3. **Simulation Loop**: Runs for 500 timesteps (configurable in code)
   - **Monitor**: Collects network state from IoT simulation
   - **Analyze**: Computes belief states and QoS metrics
   - **Plan**: Solves POMDP to select optimal action
   - **Execute**: Applies action to the network
4. **Post-processing**: Generates charts using Python script

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

## Recent Improvements

### QoS Data Synchronization Fix
The program now includes robust handling of QoS (Quality of Service) data retrieval from the DeltaIoT simulator:

- **Stream Redirection**: Both stdout and stderr are redirected during simulator operations to suppress transient warnings
- **Validation-Based Waiting**: The `waitForQoSDataReady()` method validates QoS data completeness before returning, ensuring all entries have valid values (no NaN, infinity, or default error values)
- **Automatic Retry**: If warnings are detected during simulation runs, the program automatically waits until data is fully ready before proceeding
- **Clean Console Output**: All "Unknown value data" warnings are suppressed from appearing in the console while maintaining data integrity

This ensures reliable QoS data access without console noise, improving both user experience and program reliability.

## Additional Notes

- The program is hardcoded to run `IoT.POMDP` domain (see `main` method)
- Number of timesteps is set to 500 in `runCaseIoT` method
- The Python virtual environment path is automatically detected (Windows: `.venv\Scripts\python.exe`, Linux/Mac: `.venv/bin/python`)

TODO
- Evaluate MECSat and RPLSat grouped by each mote, to see which motes are contributing more strongly to extreme values

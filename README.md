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





TODO

- Evaluate MECSat and RPLSat grouped by each mote, to see which motes are contributing more strongly to extreme values

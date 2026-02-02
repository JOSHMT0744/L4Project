### Noise Injection and Failure Simulation

- **Implementation of noiseInjector class**

  The `NoiseInjector` class provides a comprehensive failure injection system for simulating network disruptions in the DeltaIoT simulation environment. The implementation follows a modular design that operates independently of the core `SimulationClient` library, allowing failure simulation without modifying the underlying simulator code. The class maintains two primary failure modes: *link failures* (disconnections between motes) and *mote failures* (complete device malfunctions). 
  
  The architecture employs a state-based tracking mechanism using `ArrayList` collections for failed motes and links, with `HashMap` structures managing failure duration timers. Each failure is identified by unique keys: mote IDs for device failures, and "source-dest" string pairs for link failures. The system integrates seamlessly with the MAPE-K control loop, where `updateFailures()` is invoked at the beginning of each timestep to evaluate probabilistic failure events and manage recovery timers.

- **Simulating link failures**

  Link failure simulation operates through probabilistic evaluation at each timestep. When `linkFailureEnabled` is active, the system iterates through all operational links (excluding those from failed motes) and evaluates each link against a configurable failure probability using a seeded random number generator. Upon failure detection, the link is added to the `failedLinks` collection and assigned a failure duration timer initialized to `linkFailureDuration` timesteps.
  
  The `isLinkOff(source, dest)` method implements cascading failure logic: a link is considered failed if either (1) the link itself is explicitly marked as failed, or (2) the source mote is off (since a mote failure implies all its outgoing links are non-functional). This cascading behavior ensures realistic network behavior where device failures propagate to all associated communication links. The system automatically recovers links after the specified duration expires, decrementing timers each timestep until reaching zero, at which point the link is removed from the failed state.
  
  Integration with the DTP/ITP action execution ensures that failed links receive zero traffic distribution, preventing the adaptive system from attempting to route packets through non-functional connections. The `DeltaIOTConnector` checks link status before performing power adjustments and distribution balancing, automatically redirecting traffic to operational links.

- **Configurable failure probabilities and durations**

  The noise injection system provides extensive configurability through a set of public accessor methods. Failure probabilities are specified as floating-point values in the range [0.0, 1.0], representing the per-timestep probability of failure occurrence. The `setLinkFailureProbability()` and `setMoteFailureProbability()` methods enforce bounds checking to ensure valid probability ranges.
  
  Failure durations are configured in timesteps, with minimum values enforced (default: 1 timestep). The `setLinkFailureDuration()` and `setMoteFailureDuration()` methods allow researchers to model various failure scenarios: transient failures (short durations) simulate temporary disruptions, while extended durations model persistent hardware failures or disaster scenarios. The random number generator seed can be set via `setSeed()` to ensure reproducible failure sequences across experimental runs, critical for comparative analysis and debugging.
  
  The system supports both probabilistic and deterministic failure injection. Probabilistic failures occur automatically based on configured probabilities during `updateFailures()` calls. Deterministic failures can be triggered programmatically using `turnLinkOff()` and `turnMoteOff()` methods, enabling scenario-specific failure patterns (e.g., simulating a specific link failure at timestep 100). Both modes respect the configured failure durations, ensuring consistent recovery behavior regardless of injection method.

- **Analyse cascading problems**

  The noise injection system enables analysis of cascading failure effects through its hierarchical failure model. When a mote fails, the `isLinkOff()` method automatically treats all outgoing links from that mote as failed, creating a cascading effect where a single device failure impacts multiple network paths. This design allows researchers to study how localized failures propagate through the network topology.
  
  The adaptive system's response to cascading failures is observable through the distribution factor adjustments in DTP/ITP methods. When links fail, the traffic distribution algorithm immediately reallocates traffic to operational links, providing insight into the network's resilience and adaptation capabilities. The system logs failure statistics via `getFailureStats()`, tracking the number of failed motes and links at each timestep, enabling quantitative analysis of failure propagation patterns.
  
  Cascading analysis is further enhanced by the timer-based recovery mechanism. By configuring different failure durations for motes versus links, researchers can model scenarios where device failures persist longer than link-level disruptions, or vice versa. The `clearFailures()` method allows resetting the system state between experimental runs, facilitating controlled studies of failure scenarios with consistent initial conditions. The integration with the POMDP-based adaptive system enables evaluation of how the learning agent responds to cascading failures, measuring adaptation speed and effectiveness in maintaining network performance under adverse conditions.

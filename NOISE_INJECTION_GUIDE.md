# Link and Mote Failure Injection Guide

## Overview

The `NoiseInjector` class allows you to turn links and motes on/off (simulate failures) in the IoT network **without modifying the SimulationClient library**. This enables simulation of:
- Link failures (temporary disconnections)
- Mote failures (device malfunctions)

## Quick Start

### 1. Create and Configure NoiseInjector

```java
import iot.NoiseInjector;

// Create noise injector
NoiseInjector noiseInjector = new NoiseInjector();
noiseInjector.setEnabled(true);

// Enable link and/or mote failures
noiseInjector.setLinkFailureEnabled(true);
noiseInjector.setMoteFailureEnabled(true);

// Configure failure probabilities (0.0 to 1.0)
noiseInjector.setLinkFailureProbability(0.02);  // 2% chance of link failure per timestep
noiseInjector.setMoteFailureProbability(0.01); // 1% chance of mote failure per timestep

// Set failure durations (in timesteps)
noiseInjector.setLinkFailureDuration(1);  // Links fail for 1 timestep
noiseInjector.setMoteFailureDuration(2);  // Motes fail for 2 timesteps

// Set seed for reproducibility
noiseInjector.setSeed(12345);
```

### 2. Integrate into SolvePOMDP

#### Step 1: Add NoiseInjector to DeltaIOTConnector

In `DeltaIOTConnector.java`, add:

```java
private NoiseInjector noiseInjector;

public void setNoiseInjector(NoiseInjector injector) {
    this.noiseInjector = injector;
}

public NoiseInjector getNoiseInjector() {
    return noiseInjector;
}
```

#### Step 2: Update Failures Each Timestep

In `SolvePOMDP.java`, in the main loop (around line 624):

```java
// Update failure states at the beginning of each timestep
// updating failure states means to update (given we have incremented a timestep) whether a mote should still be in a faulty status or not
if (deltaConnector.getNoiseInjector() != null) {
    deltaConnector.getNoiseInjector().updateFailures(
        iot.DeltaIOTConnector.motes, timestep);
}
```

#### Step 3: Check for Failures Before Actions

In `DeltaIOTConnector.java`, modify `performDTP()` and `performITP()` to skip failed motes/links:

```java
// Check if mote is failed before performing actions
if (noiseInjector != null && noiseInjector.isMoteOff(selectedmote.getMoteid())) {
    // Skip this mote - it's failed/off
    return;
}

// When creating LinkSettings, check if link is failed
for (Link link : selectedmote.getLinks()) {
    if (noiseInjector != null && 
        noiseInjector.isLinkOff(link.getSource(), link.getDest())) {
        // Skip this link - it's failed/off
        // Or create settings with power=0 to disable it
        continue;
    }
    
    // Create normal settings for operational links
    newSettings.add(new LinkSettings(...));
}
```

#### Step 4: Initialize in SolvePOMDP

In `runCaseIoT()` method, after creating `DeltaIOTConnector`:

```java
iot.DeltaIOTConnector deltaConnector = new iot.DeltaIOTConnector();

// Create and configure noise injector
NoiseInjector noiseInjector = new NoiseInjector();
noiseInjector.setEnabled(true);
noiseInjector.setLinkFailureEnabled(true);
noiseInjector.setLinkFailureProbability(0.02);  // 2% per timestep
noiseInjector.setMoteFailureEnabled(true);
noiseInjector.setMoteFailureProbability(0.01);  // 1% per timestep

deltaConnector.setNoiseInjector(noiseInjector);
```

## Manual Control

### Turn Motes On/Off

```java
// Turn a mote off (simulate failure)
noiseInjector.turnMoteOff(5);  // Mote ID 5 fails

// Turn a mote back on (recover)
noiseInjector.turnMoteOn(5);   // Mote ID 5 recovers

// Check if a mote is on or off
if (noiseInjector.isMoteOff(5)) {
    // Mote 5 is failed/off
}
if (noiseInjector.isMoteOn(5)) {
    // Mote 5 is operational/on
}
```

### Turn Links On/Off

```java
// Turn a link off (simulate failure)
noiseInjector.turnLinkOff(3, 7);  // Link from mote 3 to mote 7 fails

// Turn a link back on (recover)
noiseInjector.turnLinkOn(3, 7);   // Link from mote 3 to mote 7 recovers

// Check if a link is on or off
if (noiseInjector.isLinkOff(3, 7)) {
    // Link 3->7 is failed/off
}
if (noiseInjector.isLinkOn(3, 7)) {
    // Link 3->7 is operational/on
}
```

## Configuration Examples

### Example 1: Link Failures Only

```java
noiseInjector.setEnabled(true);
noiseInjector.setLinkFailureEnabled(true);
noiseInjector.setLinkFailureProbability(0.02);  // 2% per timestep
noiseInjector.setLinkFailureDuration(1);       // Fail for 1 timestep
noiseInjector.setMoteFailureEnabled(false);   // No mote failures
```

### Example 2: Mote Failures Only

```java
noiseInjector.setEnabled(true);
noiseInjector.setMoteFailureEnabled(true);
noiseInjector.setMoteFailureProbability(0.01);  // 1% per timestep
noiseInjector.setMoteFailureDuration(3);        // Fail for 3 timesteps
noiseInjector.setLinkFailureEnabled(false);    // No link failures
```

### Example 3: Both Link and Mote Failures

```java
noiseInjector.setEnabled(true);
// Link failures
noiseInjector.setLinkFailureEnabled(true);
noiseInjector.setLinkFailureProbability(0.02);
noiseInjector.setLinkFailureDuration(1);
// Mote failures
noiseInjector.setMoteFailureEnabled(true);
noiseInjector.setMoteFailureProbability(0.005);  // 0.5% per timestep
noiseInjector.setMoteFailureDuration(2);
```

### Example 4: Frequent Failures (Stress Test)

```java
noiseInjector.setEnabled(true);
noiseInjector.setLinkFailureEnabled(true);
noiseInjector.setLinkFailureProbability(0.1);   // 10% per timestep
noiseInjector.setMoteFailureEnabled(true);
noiseInjector.setMoteFailureProbability(0.05); // 5% per timestep
```

## Failure Statistics

Monitor failures during execution:

```java
// Get current failure statistics
String stats = noiseInjector.getFailureStats();
System.out.println(stats);  // "Failed motes: 2, Failed links: 5"

// Get counts
int failedMoteCount = noiseInjector.getFailedMoteCount();
int failedLinkCount = noiseInjector.getFailedLinkCount();

// Get lists of failed components
List<Integer> failedMotes = noiseInjector.getFailedMotes();
List<String> failedLinks = noiseInjector.getFailedLinks();

// Clear all failures (e.g., between runs)
noiseInjector.clearFailures();
```

## Important Notes

1. **No Library Modification Required**: All failure injection happens at the application level, before interacting with SimulationClient.

2. **Automatic Recovery**: Failures automatically recover after the specified duration. You can also manually turn components back on.

3. **Mote Failures Imply Link Failures**: When a mote is failed, all its links are effectively down (the code skips links of failed motes).

4. **Reproducibility**: Set a seed for reproducible failure patterns:
   ```java
   noiseInjector.setSeed(12345);
   ```

5. **Performance**: Failure tracking adds minimal overhead using efficient hash maps.

6. **Integration Points**: You need to check for failures in your code:
   - Before performing actions on motes
   - Before using links
   - The injector only tracks state - you control how failures affect behavior

## Research Use Cases

This failure injection system enables:

1. **Robustness Testing**: Test how the POMDP solver handles link and mote failures
2. **Adaptation Analysis**: Study how the system adapts to component failures
3. **Failure Recovery**: Evaluate recovery strategies after failures
4. **Network Resilience**: Analyze network behavior under various failure scenarios
5. **Correlation Analysis**: Correlate failures with MEC/RPL objectives

## Complete Integration Example

```java
// In SolvePOMDP.runCaseIoT()
NoiseInjector noiseInjector = new NoiseInjector();
noiseInjector.setEnabled(true);
noiseInjector.setLinkFailureEnabled(true);
noiseInjector.setLinkFailureProbability(0.02);
noiseInjector.setMoteFailureEnabled(true);
noiseInjector.setMoteFailureProbability(0.01);
deltaConnector.setNoiseInjector(noiseInjector);

// In main loop
for (int timestep = 0; timestep < maxTimesteps; timestep++) {
    // Update failures
    if (noiseInjector != null) {
        noiseInjector.updateFailures(motes, timestep);
    }
    
    // Process each mote
    for (Mote mote : motes) {
        // Skip failed motes
        if (noiseInjector != null && noiseInjector.isMoteOff(mote.getMoteid())) {
            continue;
        }
        
        // Process links, skipping failed ones
        for (Link link : mote.getLinks()) {
            if (noiseInjector != null && 
                noiseInjector.isLinkOff(link.getSource(), link.getDest())) {
                continue; // Skip failed link
            }
            // Process operational link
        }
    }
}
```

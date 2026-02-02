# DTP and ITP Actions in DeltaIoT System

## Overview

The DeltaIoT system uses two primary power management actions: **DTP (Decrease Transmission Power)** and **ITP (Increase Transmission Power)**. These actions dynamically adjust transmission parameters to optimize energy consumption while maintaining reliable communication links in the IoT network simulation.

---

## DTP (Decrease Transmission Power)

### Purpose
Reduces transmission power when signal quality is sufficient, conserving energy while maintaining link reliability.

### Trigger Conditions
- **SNR > 0**: Signal-to-Noise Ratio is positive (good signal quality)
- **Power > 0**: Current transmission power is above minimum

### Actions Performed

#### 1. Power Adjustment
- **Decreases transmission power by 1** unit per execution
- Applied to all links of the selected mote

#### 2. Spreading Factor (SF) Adjustment
- **Decreases SF by 1** if current SF > 7
- SF range: 7-12 (typical LoRaWAN range)
- Lower SF = faster transmission but shorter range

#### 3. Failed Link Handling
- Failed links (or links from off motes) are **immediately set to distribution = 0**
- Prevents traffic routing through non-functional links

---

## ITP (Increase Transmission Power)

### Purpose
Increases transmission power when signal quality is poor, improving link reliability at the cost of higher energy consumption.

### Trigger Conditions
- **SNR < 0**: Signal-to-Noise Ratio is negative (poor signal quality, packets may be lost)
- **Power < 15**: Current transmission power is below maximum threshold

### Actions Performed

#### 1. Power Adjustment
- **Increases transmission power by 1** unit per execution
- Applied to all links of the selected mote
- Maximum power limit: 15 units

#### 2. Spreading Factor (SF) Adjustment
- **Increases SF by 1** if current SF < 12
- Higher SF = longer range and better reliability but slower transmission
- SF range: 7-12 (typical LoRaWAN range)

#### 3. Failed Link Handling
- Failed links are **immediately set to distribution = 0**
- Ensures traffic is not routed through broken links

---

## Distribution Factor for Multi-Link Motes

### Purpose
The distribution factor balances traffic load across multiple links from the same mote, optimizing network performance and energy efficiency.

### Application
Applied to motes with **2 links** (left and right links).

### Distribution Balancing Logic

#### Initial State Check
- If both links have **distribution = 100**, reset both to **50/50** (balanced load)

#### Power-Based Balancing
When links have **different power levels**:
- **Higher power link**: Receives **+10%** distribution
- **Lower power link**: Receives **-10%** distribution
- This shifts traffic toward the more reliable (higher power) link

**Example:**
- Left link: Power=10, Distribution=60%
- Right link: Power=8, Distribution=40%
- After adjustment: Left=70%, Right=30%

#### Failed Link Handling
- **One link failed**: Failed link gets 0%, working link gets 100%
- **Both links failed**: Both get 0% (no traffic routing)
- Failed link detection uses `noiseInjector.isLinkOff(source, dest)`

### Distribution Constraints
- Distribution values range from **0 to 100** (percentage)
- Changes occur in **10% increments**
- Total distribution across links should sum to 100%

---

## Spreading Factor (SF) Adjustments

### Relationship to Power Actions

#### DTP (Decrease Power)
- **Decreases SF** when SF > 7
- Rationale: Lower power may require lower SF to maintain transmission speed
- SF range: 7-12

#### ITP (Increase Power)
- **Increases SF** when SF < 12
- Rationale: Higher power can support higher SF for better range/reliability
- SF range: 7-12

### Impact on Network Performance
- **Lower SF (7-9)**: Faster transmission, shorter range, less energy per bit
- **Higher SF (10-12)**: Slower transmission, longer range, more energy per bit
- SF adjustments complement power changes to optimize the energy-reliability trade-off

---

## System Flow

### Execution Order
1. **Failed Link Detection**: All failed links set to distribution = 0
2. **Power & SF Adjustment**: Selected mote's links adjusted based on SNR
3. **Distribution Balancing**: Multi-link motes have traffic redistributed
4. **Settings Application**: All changes applied via `networkMgmt.getEffector().setMoteSettings()`

### Integration with POMDP
- Actions are selected by the POMDP solver based on current state and belief
- `performAction(int action)` calls either `performDTP()` or `performITP()` based on action value
- Observations (SNR-based) feed back into belief updates for future decision-making

---

## Key Parameters

| Parameter | DTP Range | ITP Range | Purpose |
|-----------|-----------|-----------|---------|
| **Power** | > 0 | < 15 | Transmission power level |
| **SNR** | > 0 | < 0 | Signal-to-Noise Ratio threshold |
| **SF** | > 7 (decrease) | < 12 (increase) | Spreading Factor for LoRa modulation |
| **Distribution** | 0-100% | 0-100% | Traffic load percentage per link |

---

## Energy Optimization Strategy

### DTP Strategy
- **Goal**: Reduce energy consumption when signal quality is good
- **Trade-off**: Lower power may reduce range but saves energy
- **Use case**: Stable links with positive SNR

### ITP Strategy
- **Goal**: Improve reliability when signal quality is poor
- **Trade-off**: Higher power consumes more energy but improves packet delivery
- **Use case**: Weak links with negative SNR (packet loss risk)

### Combined Effect
The system dynamically balances energy efficiency and reliability by:
- Reducing power when possible (DTP)
- Increasing power when necessary (ITP)
- Balancing traffic across multiple links
- Adapting SF to complement power changes

---

## Notes

- Both methods share similar structure but operate in opposite directions
- Failed link handling is identical in both methods (safety-first approach)
- Distribution balancing only applies to motes with exactly 2 links
- All changes are applied immediately via the network management effector
- The system maintains state consistency by updating all affected links simultaneously

# MAPE-K Loop — DeltaIoT Self-Adaptive POMDP Framework

## High-Level MAPE-K Cycle

```mermaid
graph TB
    subgraph Knowledge["🧠 Knowledge Base"]
        direction TB
        K1["POMDP Model<br/>(states, transitions,<br/>observations, rewards)"]
        K2["Belief State b(s)<br/>(4-state distribution)"]
        K3["Value Function<br/>(α-vectors from ERPerseus)"]
        K4["SMiLe Gamma<br/>(surprise-driven<br/>belief revision weight)"]
        K5["QoS History<br/>(packet loss,<br/>energy consumption)"]
    end

    subgraph Outer["Timestep Loop  (t = 0 … 499)"]
        direction TB

        NI["NoiseInjector.updateFailures(motes, t)<br/>+ config-driven turnLinkOff / turnLinkOn"]

        M_INIT["MONITOR — Timestep Init<br/>─────────────────────<br/>1. Retrieve all motes via SimulationClient.getProbe().getAllMotes()<br/>2. Get initial POMDP state from previous timestep QoS<br/>&nbsp;&nbsp;&nbsp;(t=0 → state 0; t>0 → POMDP.getInitialState())<br/>3. Fisher–Yates shuffle mote order<br/>&nbsp;&nbsp;&nbsp;(seed = runSeed + t)"]

        subgraph Inner["Per-Mote Loop  (randomised order)"]
            direction TB

            MON_IMPLICIT["MONITOR — Implicit Baseline<br/>─────────────────────<br/>Baseline = previous mote's post-action<br/>simulation state (or timestep init state<br/>for first mote)"]

            ANALYSE["ANALYSE<br/>─────────────────────<br/>1. Retrieve belief: b₀ = POMDP.getInitialBelief()<br/>2. Compute satisfaction probabilities:<br/>&nbsp;&nbsp;&nbsp;P(MEC satisfied) = b[0] + b[1]<br/>&nbsp;&nbsp;&nbsp;P(RPL satisfied) = b[0] + b[2]<br/>3. Log to MECSatProb.txt / RPLSatProb.txt"]

            PLAN["PLAN (ERPerseus Solver)<br/>─────────────────────<br/>1. solver.solve(pomdp):<br/>&nbsp;&nbsp;&nbsp;a. Sample belief points via random rollouts<br/>&nbsp;&nbsp;&nbsp;b. Iterative backup stages until convergence<br/>&nbsp;&nbsp;&nbsp;c. Entropy-regularised softmax backups (λ > 0)<br/>&nbsp;&nbsp;&nbsp;→ Returns ArrayList﹤AlphaVector﹥ (value function V)<br/>2. ERPolicy.selectAction(b₀):<br/>&nbsp;&nbsp;&nbsp;a. Q(a) = max over α-vectors for action a: α · b<br/>&nbsp;&nbsp;&nbsp;b. π(a|b) = softmax(Q(a) / λ)<br/>&nbsp;&nbsp;&nbsp;c. Sample action from π → selectedAction"]

            EXECUTE["EXECUTE<br/>─────────────────────<br/>1. DeltaIOTConnector.performAction(selectedAction)<br/>&nbsp;&nbsp;&nbsp;├─ action 0 → performDTP (decrease power, adjust SF)<br/>&nbsp;&nbsp;&nbsp;└─ action 1 → performITP (increase power, adjust SF)<br/>2. Inside performAction:<br/>&nbsp;&nbsp;&nbsp;a. POMDP.nextState(currState, action)<br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;→ runs DTP/ITP on selected mote<br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;→ QoSDataHelper waits for sim data<br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;→ maps (PL, EC) to state 0–3<br/>&nbsp;&nbsp;&nbsp;b. updateTransitionBelief(action, nextState)<br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;→ compute surprise (CC / BF / MIS)<br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;→ γ = mS / (1 + mS), m = p_c / (1 − p_c)<br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;→ blend: b' = (1−γ)·b_updated + γ·b_flat<br/>&nbsp;&nbsp;&nbsp;c. getObservation(action, nextState)<br/>&nbsp;&nbsp;&nbsp;d. POMDP.updateBelief() (Bayes rule)"]

            MON_POST["MONITOR — Post-Execution<br/>─────────────────────<br/>1. Simulator.doSingleRun()<br/>&nbsp;&nbsp;&nbsp;→ simulates packet flow with new config<br/>2. timestepiot++ (track latest run index)<br/>3. QoSDataHelper.waitForQoSDataReady(run#)<br/>4. Extract packetLoss, energyConsumption<br/>5. Log to MECSat.txt / RPLSat.txt<br/>6. This state becomes next mote's baseline"]

            MON_IMPLICIT --> ANALYSE --> PLAN --> EXECUTE --> MON_POST
            MON_POST -.->|"next mote's<br/>baseline"| MON_IMPLICIT
        end

        AGG["Aggregate QoS & Log<br/>─────────────────────<br/>1. logMoteAndLinkMetrics (all motes)<br/>2. Read network-wide QoS from simulator<br/>3. Write MECSattimestep.txt / RPLSattimestep.txt"]

        NI --> M_INIT --> Inner --> AGG
        AGG -.->|"next timestep's<br/>initial state"| NI
    end

    %% Knowledge → phase connections
    K1 -.->|"T(s'|s,a), O(o|s',a),<br/>R(s,a)"| PLAN
    K2 -.->|"b₀"| ANALYSE
    K2 -.->|"b₀ for Q·b"| PLAN
    K3 -.->|"α-vectors<br/>define Q(a,b)"| PLAN
    K4 -.->|"γ blends belief<br/>toward flat prior"| EXECUTE
    K5 -.->|"(PL, EC) →<br/>state mapping"| EXECUTE
    K5 -.->|"QoS informs<br/>initial state"| M_INIT

    %% Feedback from phases → Knowledge
    ANALYSE -.->|"updates b₀"| K2
    EXECUTE -.->|"new α-vectors,<br/>updated transitions"| K3
    EXECUTE -.->|"surprise → γ"| K4
    MON_POST -.->|"new QoS<br/>measurements"| K5

    classDef knowledge fill:#FFF3CD,stroke:#FFB800,stroke-width:2px,color:#000
    classDef monitor fill:#D1ECF1,stroke:#0C7BDC,stroke-width:2px,color:#000
    classDef analyse fill:#D4EDDA,stroke:#28A745,stroke-width:2px,color:#000
    classDef plan fill:#E2D9F3,stroke:#6F42C1,stroke-width:2px,color:#000
    classDef execute fill:#F8D7DA,stroke:#DC3545,stroke-width:2px,color:#000
    classDef outer fill:#F0F0F0,stroke:#666,stroke-width:1px,color:#000
    classDef noise fill:#FFE0B2,stroke:#E65100,stroke-width:2px,color:#000

    class K1,K2,K3,K4,K5 knowledge
    class M_INIT,MON_IMPLICIT,MON_POST monitor
    class ANALYSE analyse
    class PLAN plan
    class EXECUTE execute
    class NI noise
    class AGG monitor
```

## Detailed Sequence — Single Mote Adaptation (ERPerseus)

```mermaid
sequenceDiagram
    participant SolvePOMDP
    participant POMDP
    participant ERPerseus as ERPerseus Solver
    participant ERPolicy
    participant Connector as DeltaIOTConnector
    participant Simulator as SimulationClient
    participant Noise as NoiseInjector
    participant QoS as QoSDataHelper

    Note over SolvePOMDP: ── MONITOR (implicit baseline) ──
    SolvePOMDP->>POMDP: getInitialBelief()
    POMDP-->>SolvePOMDP: b₀ = [b₀₀, b₀₁, b₀₂, b₀₃]

    Note over SolvePOMDP: ── ANALYSE ──
    SolvePOMDP->>SolvePOMDP: P(MEC sat) = b₀[0] + b₀[1]
    SolvePOMDP->>SolvePOMDP: P(RPL sat) = b₀[0] + b₀[2]
    SolvePOMDP->>SolvePOMDP: Log satisfaction probs

    Note over SolvePOMDP: ── PLAN ──
    SolvePOMDP->>ERPerseus: solve(pomdp)
    ERPerseus->>ERPerseus: Sample belief points (random rollouts)
    ERPerseus->>ERPerseus: Iterative backup stages (entropy-regularised)
    ERPerseus-->>SolvePOMDP: V = ArrayList﹤AlphaVector﹥

    SolvePOMDP->>ERPolicy: new ERPolicy(pomdp, V, λ, rng)
    SolvePOMDP->>ERPolicy: selectAction(b₀)
    ERPolicy->>ERPolicy: Q(a) = max_α {α · b₀} per action
    ERPolicy->>ERPolicy: π(a|b₀) = softmax(Q / λ)
    ERPolicy->>ERPolicy: Sample action from π
    ERPolicy-->>SolvePOMDP: selectedAction (DTP=0 or ITP=1)

    Note over SolvePOMDP: ── EXECUTE ──
    SolvePOMDP->>Connector: performAction(selectedAction)
    Connector->>POMDP: nextState(currState, action)

    alt action = 0 (DTP)
        POMDP->>Connector: performDTP()
        Connector->>Connector: Decrease power (SNR > 0)
        Connector->>Connector: Adjust SF / distribution
        Connector->>Noise: isLinkOff(src, dest)?
        Noise-->>Connector: true/false
        Connector->>Connector: If off → distribution = 0
    else action = 1 (ITP)
        POMDP->>Connector: performITP()
        Connector->>Connector: Increase power (SNR < 0)
        Connector->>Connector: Adjust SF / distribution
        Connector->>Noise: isLinkOff(src, dest)?
        Noise-->>Connector: true/false
        Connector->>Connector: If off → distribution = 0
    end

    Connector->>QoS: waitForQoSDataReady(run#)
    QoS->>Simulator: getQosValues().size() ≥ run#?
    Simulator-->>QoS: QoS data
    QoS-->>Connector: (packetLoss, energyConsumption)
    Connector->>POMDP: Map (PL, EC) → state 0–3

    Note over Connector: SMiLe Belief Revision
    Connector->>Connector: Compute surprise (MIS/CC/BF)
    Connector->>Connector: γ = mS / (1 + mS)
    Connector->>Connector: b' = (1−γ)·b_updated + γ·b_flat
    Connector->>POMDP: getObservation(action, nextState)
    Connector->>POMDP: updateBelief() [Bayes rule]

    Note over SolvePOMDP: ── MONITOR (post-execution) ──
    SolvePOMDP->>Simulator: doSingleRun()
    Simulator-->>SolvePOMDP: (simulation complete)
    SolvePOMDP->>SolvePOMDP: timestepiot++
    SolvePOMDP->>QoS: waitForQoSDataReady(timestepiot)
    QoS-->>SolvePOMDP: QoS result
    SolvePOMDP->>SolvePOMDP: Extract PL, EC → log to files
    Note over SolvePOMDP: This QoS state = next mote's baseline
```

## State Discretisation

```mermaid
graph LR
    subgraph QoSMetrics["QoS Measurements"]
        PL["Packet Loss (PL)"]
        EC["Energy Consumption (EC)"]
    end

    subgraph Thresholds["Thresholds (solver.config)"]
        MEC["mecThreshold (default 20)"]
        RPL["rplThreshold (default 0.2)"]
    end

    subgraph States["POMDP States"]
        S0["State 0<br/>EC ✓ &amp; PL ✓<br/><i>Both NFRs satisfied</i>"]
        S1["State 1<br/>EC ✓ &amp; PL ✗<br/><i>Energy OK, packets lost</i>"]
        S2["State 2<br/>EC ✗ &amp; PL ✓<br/><i>Energy high, packets OK</i>"]
        S3["State 3<br/>EC ✗ &amp; PL ✗<br/><i>Both NFRs violated</i>"]
    end

    PL --> |"< rplThreshold"| S0
    PL --> |"≥ rplThreshold"| S1
    EC --> |"< mecThreshold"| S0
    EC --> |"≥ mecThreshold"| S2
    PL --> |"< rplThreshold"| S2
    PL --> |"≥ rplThreshold"| S3
    EC --> |"< mecThreshold"| S1
    EC --> |"≥ mecThreshold"| S3

    classDef good fill:#D4EDDA,stroke:#28A745,color:#000
    classDef partial fill:#FFF3CD,stroke:#FFB800,color:#000
    classDef bad fill:#F8D7DA,stroke:#DC3545,color:#000

    class S0 good
    class S1,S2 partial
    class S3 bad
```

## SMiLe Belief Revision (Knowledge Update)

```mermaid
graph LR
    subgraph Surprise["Surprise Computation"]
        direction TB
        SM["Selected Measure<br/>(solver.config: MIS / CC / BF)"]
        CC["CC: KL-divergence<br/>of Dirichlet dists"]
        BF["BF: Log-ratio of<br/>predicted probabilities"]
        MIS["MIS: MI[t] − MI[t − lookback]<br/>with confidence bounds"]
        SM --> CC
        SM --> BF
        SM --> MIS
    end

    S_VAL["S = exp(logSurprise)"]
    M_VAL["m = p_c / (1 − p_c)"]
    GAMMA["γ = mS / (1 + mS)"]
    BLEND["b' = (1−γ) · b_updated<br/>+ γ · b_flat_prior"]

    CC --> S_VAL
    BF --> S_VAL
    MIS --> S_VAL
    S_VAL --> GAMMA
    M_VAL --> GAMMA
    GAMMA --> BLEND

    BLEND --> |"High surprise<br/>(γ → 1)"| RESET["Reverts toward<br/>uniform belief<br/>(forget old model)"]
    BLEND --> |"Low surprise<br/>(γ → 0)"| TRUST["Trusts current<br/>updated belief<br/>(keep old model)"]

    classDef surprise fill:#E2D9F3,stroke:#6F42C1,color:#000
    classDef formula fill:#D1ECF1,stroke:#0C7BDC,color:#000
    classDef result fill:#D4EDDA,stroke:#28A745,color:#000
    classDef reset fill:#F8D7DA,stroke:#DC3545,color:#000

    class CC,BF,MIS,SM surprise
    class S_VAL,M_VAL,GAMMA,BLEND formula
    class TRUST result
    class RESET reset
```

## External Component Interactions

```mermaid
graph TB
    subgraph Core["MAPE-K Core"]
        MAPEK["SolvePOMDP<br/>(MAPE-K Orchestrator)"]
    end

    subgraph Solvers["Solver Layer"]
        ERP["ERPerseus<br/>(entropy-regularised<br/>point-based solver)"]
        ERPOL["ERPolicy<br/>(softmax action selection)"]
    end

    subgraph Model["POMDP Model"]
        POMDP_M["POMDP<br/>(states, beliefs,<br/>transitions, observations)"]
    end

    subgraph IoT["DeltaIoT Simulator"]
        DC["DeltaIOTConnector<br/>(performDTP / performITP,<br/>SMiLe, surprise)"]
        SIM["SimulationClient<br/>(doSingleRun,<br/>getProbe, getQoS)"]
        NI_EXT["NoiseInjector<br/>(link/mote failures,<br/>duration timers)"]
        QH["QoSDataHelper<br/>(polling wait for<br/>QoS readiness)"]
    end

    subgraph Config["Configuration"]
        SC["solver.config<br/>(algorithm, λ, thresholds,<br/>surprise params, seed)"]
        PF[".POMDP file<br/>(domain definition)"]
    end

    subgraph Output["Output Files"]
        OF["MECSat / RPLSat .txt<br/>MECSatProb / RPLSatProb .txt<br/>SelectedAction.txt<br/>gamma.txt / surprise*.txt<br/>mote_metrics.txt<br/>MECSattimestep / RPLSattimestep .txt"]
    end

    SC -->|"params"| MAPEK
    PF -->|"domain"| POMDP_M
    MAPEK -->|"solve()"| ERP
    ERP -->|"α-vectors"| ERPOL
    ERPOL -->|"selectedAction"| MAPEK
    MAPEK -->|"performAction"| DC
    DC -->|"nextState / DTP / ITP"| POMDP_M
    DC -->|"push config"| SIM
    DC -->|"isLinkOff?"| NI_EXT
    MAPEK -->|"doSingleRun"| SIM
    MAPEK -->|"waitForQoS"| QH
    QH -->|"poll"| SIM
    MAPEK -->|"updateFailures"| NI_EXT
    MAPEK -->|"log results"| OF

    classDef core fill:#E2D9F3,stroke:#6F42C1,stroke-width:2px,color:#000
    classDef solver fill:#D1ECF1,stroke:#0C7BDC,stroke-width:2px,color:#000
    classDef model fill:#FFF3CD,stroke:#FFB800,stroke-width:2px,color:#000
    classDef iot fill:#D4EDDA,stroke:#28A745,stroke-width:2px,color:#000
    classDef config fill:#F0F0F0,stroke:#666,stroke-width:1px,color:#000
    classDef output fill:#F8D7DA,stroke:#DC3545,stroke-width:1px,color:#000

    class MAPEK core
    class ERP,ERPOL solver
    class POMDP_M model
    class DC,SIM,NI_EXT,QH iot
    class SC,PF config
    class OF output
```

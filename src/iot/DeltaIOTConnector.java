package iot;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import deltaiot.client.SimulationClient;
import deltaiot.services.Link;
import deltaiot.services.LinkSettings;
import deltaiot.services.Mote;
import pomdp.POMDP;
import solver.BeliefPoint;

import org.apache.commons.math3.special.Gamma;

// TODO: use polarity of MIS surprise as an explainer of the learning behaviour of the agent

/**
 * The connector between the Simulator and the MAPE-K loop
 */
public class DeltaIOTConnector {
	
	public static POMDP p;
	
	
	//public static Probe probe;
	//public static Effector effector;
	public static Mote selectedmote;
	public static Link selectedlink;
	public static boolean refsetcreation=false;
	public static ArrayList<Mote> motes;
	
	public static SimulationClient networkMgmt;
	public static int timestepiot; // monotonic timestep increment for timestep and mote
	public static int timestep; // monotonic increment only for timestep
	//private static StopWatch stopwatchiot;
	public static Integer moteids[];
	public int selectedindex;
	
	private double eps;
	
	// History of mutual information values per mote for MIS calculation
	private Map<Integer, ArrayList<Double>> miHistory;
	private int lookback = 4; // m - lookback period for MIS calculation
	// Track which timesteps have already had bounds written (to avoid duplicates)
	private int lastBoundsTimestep = -1;
	
	// Track if header has been written to mote_metrics.txt
	private static boolean moteMetricsHeaderWritten = false;
	
	// Surprise measure to use for gamma calculation: "CC" (Confidence-Corrected), "BF" (Bayes Factor), or "MIS" (Mutual Information Surprise)
	private String surpriseMeasureForGamma = "CC"; // Default to Confidence-Corrected Surprise
	
	// Output directory for file operations
	private String outputDirectory = "L4Project/output_dir"; // Default, will be set by SolvePOMDP
	
	// class for causing breakages in the network
	private NoiseInjector noiseInjector;


	public void setNoiseInjector(NoiseInjector noiseInjector) {
		this.noiseInjector = noiseInjector;
	}

	public NoiseInjector getNoiseInjector() {
		return this.noiseInjector;
	}

	public void setOutputDirectory(String outputDir) {
		this.outputDirectory = outputDir;
	}
	
	public String getOutputDirectory() {
		return this.outputDirectory;
	}
	
	public DeltaIOTConnector() {
		selectedindex=0;
		eps = 1e-6; // prevent underflow
		miHistory = new HashMap<Integer, ArrayList<Double>>();
		//stopwatchiot = StopWatch.getGlobalStopWatch();		
	}
	
	public List<Link> getSelectedMoteLinks(Mote selectedmote) {
		List<Link> l = selectedmote.getLinks();
		return l;
	}

	public int getObservation() {
		System.out.println("observation function: mote no:  "+DeltaIOTConnector.selectedmote.getMoteid());
		//for (Link link : DeltaIOTConnector.selectedmote.getLinks()) {
		for (Link link : DeltaIOTConnector.selectedmote.getLinks()) {	
			System.out.println("SNR: "+link.getSNR());
			System.out.println("Distribution factor:"+link.getDistribution());
		//if (link.getSNR() > 0 && link.getPower()>0) {
			if (link.getSNR() > 0) {
				//DeltaIOTConnector.selectedmote=m;
				//DeltaIOTConnector.selectedlink=link;
				return 2;
			}
			else if (link.getSNR() == 0) {
				//DeltaIOTConnector.selectedmote=m;
				//DeltaIOTConnector.selectedlink=link;
				return 1;
			}
			//else if (link.getSNR()<0 && link.getPower()<15) {
			else if (link.getSNR() <0) {
				//DeltaIOTConnector.selectedmote=m;
				//DeltaIOTConnector.selectedlink=link;
				return 0;
			}
		}
		
		return 0;
	}
	
	/**
	 * Set which surprise measure to use for gamma calculation
	 * @param measure "CC" for Confidence-Corrected Surprise, "BF" for Bayes Factor Surprise, or "MIS" for Mutual Information Surprise
	 */
	public void setSurpriseMeasureForGamma(String measure) {
		if (measure.equals("CC") || measure.equals("BF") || measure.equals("MIS")) {
			this.surpriseMeasureForGamma = measure;
		} else {
			System.err.println("Warning: Invalid surprise measure '" + measure + "'. Use 'CC', 'BF', or 'MIS'. Keeping current: " + this.surpriseMeasureForGamma);
		}
	}
	
	/**
	 * Get the current surprise measure used for gamma calculation
	 * @return "CC", "BF", or "MIS"
	 */
	public String getSurpriseMeasureForGamma() {
		return this.surpriseMeasureForGamma;
	}
	
	private double confidenceCorrectedSurprise(double[][][] transitionBelief, double[][][] transitionBeliefReset, int action, int nextstate) {
		// Implements SCC1(yt+1|xt+1; π(t)) := DKL[π(t)||πflat(.|yt+1, xt+1)] from "A taxonomy of surprise definitions" (2022)
		// For each possible current state, calculate KL divergence between:
		// - π(t): current transition belief (BEFORE update) 
		// - πflat(.|yt+1, xt+1): flat prior updated with new observation
		// Then compute weighted sum over current states (for POMDP uncertainty)
		int numStates = p.getNumStates();
		double surpriseCC = 0.0;
		
		for (int currState = 0; currState < numStates; currState++) {
			double[] alpha = transitionBelief[currState][action]; // current belief pseudo counts (for each belief state)
			double[] beta = transitionBeliefReset[currState][action]; // flat prior (with 1 added to new observation)
			
			double a0 = 0.0;
			double b0 = 0.0;
			// sum of pseudo-counts
			for (int i = 0; i < numStates; i++) {
				a0 += alpha[i];
				b0 += beta[i];
			}
			
			// https://statproofbook.github.io/P/dir-kl.html
			double term = Gamma.logGamma(a0) - Gamma.logGamma(b0);
			for (int i = 0; i < numStates; i++) {
				term += Gamma.logGamma(beta[i]) - Gamma.logGamma(alpha[i]);
			}
			for (int i = 0; i < numStates; i++) {
				double psiAlphaI = Gamma.digamma(alpha[i]); 
				double psiAlpha0 = Gamma.digamma(a0);
				term += (alpha[i] - beta[i]) * (psiAlphaI - psiAlpha0);
			}
			surpriseCC += p.getInitialBelief().getBelief(currState) * term; // Weigh surprise by prior state belief probability
		}
		return surpriseCC;
	}
	
	private double[] getLogPredProbs(double[][][] transitionBelief, int action, int nextstate) {
		// Calculate belief pseudo-counts for all possible next states, and then for the specifically chosen next state
		// Use sum over rows as a normaliser so probability is in [0,1]
		double[] logPred  = new double[p.getNumStates()];
		// "For each dirichlet distribution of the transition belief"
		for (int stateIndex = 0; stateIndex < p.getNumStates(); stateIndex++) {
			double[] alpha = transitionBelief[stateIndex][action];
			double a0 = 0.0;
			for (double a : alpha) a0 += a;
			
			if (a0 < this.eps) a0 = this.eps;
			
			logPred[stateIndex] = Math.log(transitionBelief[stateIndex][action][nextstate]) - Math.log(a0);
		}
		
		return logPred;
	}
	
	/**
	 * Calculates the Bayes Factor Surprise for a given action and observed next state.
	 * This is defined as the log ratio of the predicted probability of the observed next state
	 * under the *reset* (flat) prior and the *current* belief. 
	 * Both probabilities are computed by marginalizing over all current states, weighted by the current belief.
	 *
	 * @param transitionBeliefCurr   Current Dirichlet pseudo-counts Belief[state][action][nextstate]
	 * @param transitionBeliefReset  Flat (reset) Dirichlet pseudo-counts Belief[state][action][nextstate]
	 * @param action                 The action taken
	 * @param nextstate              The observed next state
	 * @return                       The Bayes Factor surprise value for this transition
	 */
	private double bayesFactorSurprise(double[][][] transitionBeliefCurr, double[][][] transitionBeliefReset, int action, int nextstate) {
		// Calculating the probability of moving to nextstate, weighted by the current belief state, for each current belief state
		double[] logPredProbCurrVals = this.getLogPredProbs(transitionBeliefCurr, action, nextstate);
		double predProbCurr = 0;
		for (int currState = 0; currState < p.getNumStates(); currState++) {
			predProbCurr += p.getInitialBelief().getBelief(currState) * Math.exp(logPredProbCurrVals[currState]);
		}
		assert predProbCurr >= 0 && predProbCurr <= 1;
		
		double[] logPredProbResetVals = this.getLogPredProbs(transitionBeliefReset, action, nextstate);
		double predProbReset = 0;
		for (int currState = 0; currState < p.getNumStates(); currState++) {
			predProbReset += p.getInitialBelief().getBelief(currState) * Math.exp(logPredProbResetVals[currState]);
		}
		assert predProbReset >= 0 && predProbReset <= 1;		
		
		// Calculate Bayes Factor Surprise
		return Math.log(Math.max(this.eps, predProbReset)) - Math.log(Math.max(this.eps, predProbCurr));
	}
	
	/* *
	 * Calculating entropy for one dirichlet distribution set of alpha pseudo-counts
	 * @param alpha The alpha vector of pseudo-counts
	 * @return The entropy of the dirichlet distribution
	 */
	private double dirichlet_entropy(double[] alpha) {
		double alpha0 = Arrays.stream(alpha).sum();
		int k = alpha.length;
		
		// ln B(alpha)
		double lnB = 0.0;
		for (double a : alpha) {
			lnB += Gamma.logGamma(a);
		}
		lnB -= Gamma.logGamma(alpha0);
		
		// sum_i (alpha_i - 1) * psi(alpha_i)
		double sum1 = 0.0;
		for (double a : alpha) {
			sum1 += (a - 1.0) * Gamma.digamma(a);
		}
		
		// (alpha0 - k) * psi(alpha0
		double sum2 = (alpha0 - k) * Gamma.digamma(alpha0);
		
		return (lnB + sum2 - sum1);
	}
	
	/**
	 * Calculates the entropy of a mote's transition belief for a given action and next state.
	 * @param transitionBelief The transition belief of the mote
	 * @param action The action taken
	 * @param nextstate The next state
	 * @return The entropy of the mote's transition belief
	 */
	private double getMoteEntropy(double[][][] transitionBelief, int action, int nextstate) {
		// Calculate expected entropy: iterate over all current states
		// For each current state, compute the entropy of the Dirichlet distribution over next states
		// Weight by the belief over current states
		double entropy = 0.0;
		for (int stateIndex = 0; stateIndex < p.getNumStates(); stateIndex++) {
			// as we want to know total entropy of the transition beliefs, rather than just the transition entropy, we will be iterating over all possible next states
			// And then weighting this entropy belief by our state belief
			entropy += p.getInitialBelief().getBelief(stateIndex) * dirichlet_entropy(transitionBelief[stateIndex][action]);
		}
		return entropy;
	}
	
	private void appendToFile(String filename, double variable, int moteNumber, int timestep) {
		// Use outputDirectory if filename is relative, otherwise use filename as-is
		String fullPath = filename.startsWith(File.separator) || (filename.length() > 1 && filename.charAt(1) == ':') 
			? filename : new File(outputDirectory, filename).getPath();
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(fullPath, true))) {
            writer.write(Integer.toString(moteNumber)+" "+Integer.toString(timestep)+" "+Double.toString(variable));
            writer.newLine(); // adds a newline
        } catch (IOException e) {
            e.printStackTrace();
        }
	}
	
	/**
	 * Logs comprehensive metrics for a mote and all its links at a given timestep.
	 * Logs to a single comprehensive file with format: timestep moteId linkIndex source dest snr power distribution sf
	 * If a mote has no links, logs a single line with linkIndex=-1 and other link fields as -1 or N/A.
	 * 
	 * @param mote The mote to log metrics for
	 * @param timestep The current timestep
	 */
	public void logMoteAndLinkMetrics(Mote mote, int timestep) {
		if (mote == null) {
			System.err.println("Warning: Attempted to log metrics for null mote at timestep " + timestep);
			return;
		}
		
		try {
			File file = new File(outputDirectory, "mote_metrics.txt");
			// Ensure parent directory exists
			if (file.getParentFile() != null && !file.getParentFile().exists()) {
				file.getParentFile().mkdirs();
			}
			
			// Write header on first write
			boolean writeHeader = !moteMetricsHeaderWritten && !file.exists();
			
			try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
				// Write header if this is the first write
				if (writeHeader) {
					writer.write("timestep moteId linkIndex source dest snr power distribution sf");
					writer.newLine();
					moteMetricsHeaderWritten = true;
				}
				int moteId = mote.getMoteid();
				List<Link> links = mote.getLinks();
				
				if (links == null || links.isEmpty()) {
					// Log mote with no links
					writer.write(String.format("%d %d %d %d %d %.6f %d %d %d",
						timestep, moteId, -1, -1, -1, Double.NaN, -1, -1, -1));
					writer.newLine();
				} else {
					// Log each link
					for (int linkIndex = 0; linkIndex < links.size(); linkIndex++) {
						Link link = links.get(linkIndex);
						if (link == null) {
							// Skip null links
							continue;
						}
						
						try {
							int source = link.getSource();
							int dest = link.getDest();
							double snr = link.getSNR();
							int power = link.getPower();
							int distribution = link.getDistribution();
							int sf = link.getSF();
							
							// Format: timestep moteId linkIndex source dest snr power distribution sf
							writer.write(String.format("%d %d %d %d %d %.6f %d %d %d",
								timestep, moteId, linkIndex, source, dest, snr, power, distribution, sf));
							writer.newLine();
						} catch (Exception e) {
							// Handle any errors accessing link properties
							System.err.println("Warning: Error accessing link properties for mote " + moteId + 
								", link " + linkIndex + " at timestep " + timestep + ": " + e.getMessage());
							// Log partial data with error indicators
							try {
								writer.write(String.format("%d %d %d %d %d %.6f %d %d %d",
									timestep, moteId, linkIndex, -1, -1, Double.NaN, -1, -1, -1));
								writer.newLine();
							} catch (IOException ioException) {
								System.err.println("Error writing partial link data: " + ioException.getMessage());
							}
						}
					}
				}
				writer.flush(); // Ensure data is written immediately
			}
		} catch (IOException e) {
			System.err.println("Error writing mote metrics to file at timestep " + timestep + 
				" for mote " + (mote != null ? mote.getMoteid() : "null") + ": " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	/**
	 * Append MIS bounds to output file in format "timestep mis_lower mis_upper"
	 * @param timestep Current timestep
	 * @param lowerBound Lower bound of MIS
	 * @param upperBound Upper bound of MIS
	 */
	private void appendMISBoundsToFile(int timestep, double lowerBound, double upperBound) {
		try {
			// Use configured output directory
			File file = new File(outputDirectory, "MISBounds.txt");
			// Ensure parent directory exists
			if (file.getParentFile() != null && !file.getParentFile().exists()) {
				file.getParentFile().mkdirs();
			}
			try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
				writer.write(Integer.toString(timestep) + " " + Double.toString(lowerBound) + " " + Double.toString(upperBound));
				writer.newLine();
				writer.flush(); // Ensure data is written immediately
			}
		} catch (IOException e) {
			System.err.println("Error writing MIS bounds to file at timestep " + timestep + ": " + e.getMessage());
			System.err.println("Current working directory: " + System.getProperty("user.dir"));
			e.printStackTrace();
		}
	}
	
	/**
	 * Calculates Mutual Information Surprise (MIS) for a given mote and timestep.
	 * MIS represents the difference in mutual information (MI) between the current timestep and
	 * the value from "lookback" timesteps earlier. Returns 0.0 if there isn't enough MI history.
	 *
	 * @param transitionBeliefPrior The prior transition belief (before +1.0 update)
	 * @param transitionBeliefPosterior The posterior updated transition belief (after +1.0 update)
	 * @param action The action taken
	 * @param nextstate The next state
	 * @param moteId    The unique identifier for the mote
	 * @param timestep  The current simulation timestep
	 * @return          The computed MIS value (0.0 if insufficient MI history)
	 */
	private double calculateAndStoreMIS(double[][][] transitionBeliefPrior, double[][][] transitionBeliefPosterior, int action, int nextstate, int moteId, int timestep) {
		/// 1. CALCULATE PRIOR ENTROPY (uncertainty before observing the transition)
		double priorEntropy = this.getMoteEntropy(transitionBeliefPrior, action, nextstate);
		/// 2. CALCULATE POSTERIOR ENTROPY (uncertainty after observing the transition)
		double posteriorEntropy = this.getMoteEntropy(transitionBeliefPosterior, action, nextstate);

		/// 3. CALCULATE MUTUAL INFORMATION
		double mutualInformation = priorEntropy - posteriorEntropy;

		// Get or create MI history for this mote
		if (!miHistory.containsKey(moteId)) {
			miHistory.put(moteId, new ArrayList<Double>());
		}
		ArrayList<Double> history = miHistory.get(moteId);
		
		// Store current MI in history
		history.add(mutualInformation);
		
		// Calculate MIS if we have enough history (need at least lookback+1 entries: current + lookback previous)
		double mis = 0.0;
		if (history.size() > lookback) {
			// MIS = MI[current] - MI[current - lookback]
			mis = history.get(history.size() - 1) - history.get(history.size() - 1 - lookback);

			// Calculate upper and lower bounds of MIS according to Theorem 1
			// Theorem 1: Î_{n+m} - Î_n ∈ (log(m + n) - log n) ± (2m log(2/ρ) log(m + n)) / (m + n)
			// Where: m = lookback, n = history.size() - lookback (earlier timestep), n+m = history.size() (current)
			double rho = 0.05; // Confidence level that true MIS value lies within copmuted bounds (0.05 -> 95% confidence)
			int n = history.size() - lookback;  // Earlier timestep index
			int m = lookback;                   // Lookback period
			int nPlusM = history.size();        // Current timestep (n + m)
			
			// Pivot value: log(m + n) - log(n) = log(n+m) - log(n)
			double pivotVal = Math.log(nPlusM) - Math.log(n);
			
			// Error term: sqrt(2m log(2/ρ)) * log(m + n) / (m + n)
			double errorTerm = Math.sqrt((2.0 * m * Math.log(2.0 / rho))) * Math.log(nPlusM) / nPlusM;
			
			// Calculate bounds
			double upperBound = pivotVal + errorTerm;
			double lowerBound = pivotVal - errorTerm;
			
			// Store bounds in file: format "timestep mis_lower mis_upper"
			// Only write once per timestep (not per mote) to avoid duplicates
			// Use the first mote that has enough history for this timestep to write the bounds
			if (timestep > lastBoundsTimestep) {
				appendMISBoundsToFile(timestep, lowerBound, upperBound);
				lastBoundsTimestep = timestep;
			}
		}
		// If not enough history, return 0.0 (no surprise yet)
		
		return mis;
	}
	
	public void clearFile(String filename) {
	    try (FileWriter fw = new FileWriter(filename, false)) {
	        // Opening with false truncates the file
	    } catch (IOException e) {
	        e.printStackTrace();
	    }
	}
	
	private void updateObservationBelief(int action, int nextstate, int obs) {
		p.observationBelief[action][nextstate][obs] += 1.0;
	}
	
	private void updateTransitionBelief(int action, int nextstate) {
		// Work with copies, to ensure not overwriting the POMDPs transition beliefs unless intended to
		// Perform a deep copy to avoid aliasing/modifying p.transitionBeliefCurr
		double[][][] transitionBeliefCurrTemp = Arrays.stream(p.transitionBeliefCurr)
		    .map(twoD -> Arrays.stream(twoD)
		        .map(arr -> arr.clone())
		        .toArray(double[][]::new))
		    .toArray(double[][][]::new);
		double[][][] transitionBeliefResetTemp = Arrays.stream(p.transitionBeliefReset)
		    .map(twoD -> Arrays.stream(twoD)
		        .map(arr -> arr.clone())
		        .toArray(double[][]::new))
		    .toArray(double[][][]::new);
		
		// Update pseudo-counts by adding normalised likelihoods to relevant indexes
		// This reflects our adjustment in the confidence of the elected transition
		for (int stateIndex = 0; stateIndex < p.getNumStates(); stateIndex++) {
			transitionBeliefCurrTemp[stateIndex][action][nextstate] += 1.0; // update by relative confidence we are in each state?
			transitionBeliefResetTemp[stateIndex][action][nextstate] += 1.0;
		}

		// Select which surprise measure to use for gamma calculation
		// Options: "CC" (Confidence-Corrected Surprise - default), "BF" (Bayes Factor Surprise), or "MIS" (Mutual Information Surprise)
		// To change, call setSurpriseMeasureForGamma(measure) before running

		double surpriseCC = confidenceCorrectedSurprise(p.transitionBeliefCurr, transitionBeliefResetTemp, action, nextstate);
		double logSurpriseCC = Math.log(Math.max(this.eps, surpriseCC));
		// bayesFactorSurprise already returns a log value, so don't take log again
		double logSurpriseBF = Math.max(this.eps, bayesFactorSurprise(p.transitionBeliefCurr, p.transitionBeliefReset, action, nextstate));
		double currentMIS = calculateAndStoreMIS(p.transitionBeliefCurr, transitionBeliefCurrTemp, action, nextstate, DeltaIOTConnector.selectedmote.getMoteid(), DeltaIOTConnector.timestep);		
		
		double logSurprise = 0.0;
		if (surpriseMeasureForGamma.equals("CC")) {
			logSurprise = logSurpriseCC;
		} else if (surpriseMeasureForGamma.equals("BF")) {
			logSurprise = logSurpriseBF;
		} else if (surpriseMeasureForGamma.equals("MIS")) {
			// MIS < 0 => over-exploitation, so we are gaining no new information. Retreat to a more vague prior to open up exploration
			// MIS > 0 => over-exploration, so we are gaining new information. Continue to explore the current transition belief
			// For MIS, we need to handle the sign: positive MIS means high surprise (more learning), negative means low surprise (less learning)
			double absMIS = Math.abs(currentMIS);
			// Add a small offset to prevent log(0) when MIS is exactly 0, but scale it so small MIS values still produce reasonable gamma
			double scaledMIS = Math.max(this.eps, absMIS);
			logSurprise = Math.log(scaledMIS);
		}

		// Predefined rate m dictates how much model changes
		// p_c controls the rate of change of the transition belief
		double p_c = 0.5;
		assert p_c >= 0 && p_c < 1;
		double m = p_c / (1 - p_c);

		// varSMiLE gamma formula (Definition 4): gamma(S, m) = mS / (1 + mS)
		// Equivalent form: gamma = 1 / (1 + 1/(m*S)) where S = exp(logSurprise)
		// This ensures: high surprise -> high gamma (less learning), low surprise -> low gamma (more learning)
		// This form is numerically stable and equivalent to mS/(1+mS)
		double gamma = 1.0 / (1.0 + (1/ (m*Math.exp(logSurprise))));
		// Ensure gamma has a minimum value to allow some learning even when surprise is very low
		gamma = Math.max(this.eps, gamma);
		assert gamma >= 0.0 && gamma <= 1.0;
		
		appendToFile(new File(outputDirectory, "surpriseBF.txt").getPath(), Math.exp(logSurpriseBF), DeltaIOTConnector.selectedmote.getMoteid(), DeltaIOTConnector.timestep);
		appendToFile(new File(outputDirectory, "gamma.txt").getPath(), gamma, DeltaIOTConnector.selectedmote.getMoteid(), DeltaIOTConnector.timestep);
		appendToFile(new File(outputDirectory, "surpriseCC.txt").getPath(), Math.exp(logSurpriseCC), DeltaIOTConnector.selectedmote.getMoteid(), DeltaIOTConnector.timestep);
		appendToFile(new File(outputDirectory, "surpriseMIS.txt").getPath(), currentMIS, DeltaIOTConnector.selectedmote.getMoteid(), DeltaIOTConnector.timestep);

		// varSMiLE updating of transitionBeliefCurr
		// The varSMiLE rule: new_belief = (1-gamma) * updated_current + gamma * updated_flat_prior
		// Both beliefs are updated with +1.0 for the observed transition
		for (int stateIndex = 0; stateIndex < p.getNumStates(); stateIndex++) {
			for (int nextStateIndex = 0; nextStateIndex < p.getNumStates(); nextStateIndex++) {
				transitionBeliefCurrTemp[stateIndex][action][nextStateIndex] = 
						(1- gamma) * transitionBeliefCurrTemp[stateIndex][action][nextStateIndex] 
						+ gamma * transitionBeliefResetTemp[stateIndex][action][nextStateIndex];
			}
				
		}
		
		p.transitionBeliefCurr = transitionBeliefCurrTemp;
	}

	public int performAction(int action) {
		////Perform ITP or DTP on the link on the simulator
		///return rewards and observations
		//update belief value and change initial belief
		
		///Immediate Reward
		//double reward = p.getReward(p.getCurrentState(), action);
		int nextstate;

		// Depending on if action==0 or 1, it will perform either DTP ITP
		nextstate = p.nextState(p.getCurrentState(), action);
		p.setCurrentState(nextstate);
		
		/// 2. UPDATE TRANSITION BELIEFS based on the new observation
		// update world probabilities by taking Expectation[transitionBeliefCurr] 
		this.updateTransitionBelief(action, nextstate);
		// I've circumvented this by using the pseudo counts to just calculate instances of probabilities in the getTransitionProbability function when required
		
		/// 4. Observation belief + updates
		int obs = p.getObservation(action, nextstate);
		// Despite being called "Initial" belief, consider this the current belief
		this.updateObservationBelief(action, nextstate, obs);
		
		BeliefPoint b = p.updateBelief(p.getInitialBelief(), action, obs); // CHANGE THIS ADAPTATION FOR THE SMILE RULE
		
		// Despite being called initialBelief, consider this the updated current belief for states
		p.setInitialBelief(b);
	
		return 0;
	}

	
	///SF Check
	public void performDTP() { 		
		// Check if mote is failed before performing actions
		if (noiseInjector != null && noiseInjector.isMoteOff(
			DeltaIOTConnector.selectedmote.getMoteid())) {
				// skip this mote -> it's faild/off
				return;
			}

		int value;
		Link left, right;
		int valueleft,valueright;

		for(Link link : DeltaIOTConnector.selectedmote.getLinks()) {

			// check if link is failed before performing actions
			if (noiseInjector != null &&
				noiseInjector.isLinkOff(link.getSource(), link.getDest())
			) {
				// Skip this link -> it's faild/off
				// Or create settings with power = 0 to disable it
				continue;
			}

			//DeltaIOTConnector.selectedlink=m.getLink(0);
			DeltaIOTConnector.selectedlink = link;
				if (DeltaIOTConnector.selectedlink.getSNR() > 0 && DeltaIOTConnector.selectedlink.getPower() > 0) {				
					value = DeltaIOTConnector.selectedlink.getPower() - 1; // decreasing power by 1
					int valueSF = DeltaIOTConnector.selectedlink.getSF(); // spreading factor
					if(valueSF > 7) {
						//System.out.println(valueSF+"       "+value+"~~~~~~~~~~~~~");
						valueSF=DeltaIOTConnector.selectedlink.getSF() - 1; // decreasing SF by 1
						//System.out.println(valueSF+"       "+value+"~~~~~~~~~~~~~");
					}
					List<LinkSettings> newSettings = new LinkedList<LinkSettings>();
					newSettings.add(new LinkSettings(DeltaIOTConnector.selectedmote.getMoteid(), DeltaIOTConnector.selectedlink.getDest(), value, DeltaIOTConnector.selectedlink.getDistribution(), valueSF));
		
					DeltaIOTConnector.networkMgmt.getEffector().setMoteSettings(DeltaIOTConnector.selectedmote.getMoteid(),newSettings);	
				}
			}
			
				
		for (Mote mote : DeltaIOTConnector.motes) {
			if(mote.getLinks().size() > 1) {
				
				left = mote.getLinks().get(0);
				right = mote.getLinks().get(1);
				if (left.getPower() != right.getPower()) {
					// If distribution of all links is 100 then change it to 50
					// 50
					if (left.getDistribution() == 100 && right.getDistribution() == 100) {
						left.setDistribution(50);
						right.setDistribution(50);
					}
					if (left.getPower() > right.getPower() && left.getDistribution() < 100) {
						valueleft = left.getDistribution() + 10;
						 valueright = right.getDistribution() - 10;
						 left.setDistribution(valueleft);
						 right.setDistribution(valueright);
					} else if (right.getDistribution() < 100) {
						valueright = right.getDistribution() + 10;
						valueleft = left.getDistribution() - 10;
						left.setDistribution(valueleft);
						right.setDistribution(valueright);
						}
					}
				}
			}
		}
	
	
	
	///perform actions for simulator DeltaIOT
	public void performITP() { 	
		// Check if mote is failed before performing actions
		if (noiseInjector != null && noiseInjector.isMoteOff(
			DeltaIOTConnector.selectedmote.getMoteid())) {
				// skip this mote -> it's faild/off
				return;
			}

		int value;
		Link left, right;
		int valueleft,valueright;

				for(Link link : DeltaIOTConnector.selectedmote.getLinks()) {

					// check if link is failed before performing actions
					if (noiseInjector != null &&
						noiseInjector.isLinkOff(link.getSource(), link.getDest())
					) {
						// Skip this link -> it's faild/off
						// Or create settings with power = 0 to disable it
						continue;
					}
				
					//DeltaIOTConnector.selectedlink=m.getLink(0);
					DeltaIOTConnector.selectedlink = link;
					
					// SNR = Signal to Noise Ratio -> used as a basis for adjusting transmission power
					// If SNR < 0, the logic increases the power, if SNR > 0, decrease power
					// Goal: keep SNR at a level where packets aren't lost but without wasting energy
					if (DeltaIOTConnector.selectedlink.getSNR() < 0 && DeltaIOTConnector.selectedlink.getPower() < 15) {
						//DeltaIOTConnector.selectedlink=l;
					
						value=DeltaIOTConnector.selectedlink.getPower() + 1;
						int valueSF=DeltaIOTConnector.selectedlink.getSF();
						if(valueSF<12)
						{
						valueSF=DeltaIOTConnector.selectedlink.getSF() + 1;
						}
						List<LinkSettings> newSettings=new LinkedList<LinkSettings>();
						newSettings.add(new LinkSettings(DeltaIOTConnector.selectedmote.getMoteid(), DeltaIOTConnector.selectedlink.getDest(), value, DeltaIOTConnector.selectedlink.getDistribution(), valueSF));
			
						DeltaIOTConnector.networkMgmt.getEffector().setMoteSettings(DeltaIOTConnector.selectedmote.getMoteid(),newSettings);
						
					}
				}

		for (Mote mote : DeltaIOTConnector.motes) {
			if(mote.getLinks().size() == 2) {
				
				left =mote.getLinks().get(0);
				right = mote.getLinks().get(1);
				if (left.getPower() != right.getPower()) {
					// If distribution of all links is 100 then change it to 50
					// 50
					if (left.getDistribution() == 100 && right.getDistribution() == 100) {
						left.setDistribution(50);
						right.setDistribution(50);
					}
					if (left.getPower() > right.getPower() && left.getDistribution() < 100) {
						valueleft=left.getDistribution() + 10;
						 valueright=right.getDistribution() - 10;
						 left.setDistribution(valueleft);
						 right.setDistribution(valueright);
					} else if (right.getDistribution() < 100) {
						valueright=right.getDistribution() + 10;
						valueleft=left.getDistribution() - 10;
						left.setDistribution(valueleft);
						right.setDistribution(valueright);
						}
					}
				}
			}
		}
	}


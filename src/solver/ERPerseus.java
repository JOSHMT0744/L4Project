/*******************************************************************************
 * SolvePOMDP
 * Copyright (C) 2017 Erwin Walraven
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/

package solver;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import pomdp.POMDP;
import pomdp.SolverProperties;

/**
 * Solving POMDPs using point-based value iteration
 */

public class ERPerseus implements Solver {	
	private Random rnd;
	private SolverProperties sp;
	private long totalSolveTime = 0;
	private double expectedValue;
	private double lambda = 1.0;  // temperature parameter for entropy regularization (default: deterministic)
	
	public ERPerseus(SolverProperties solverProperties, Random rnd, double lambda) {
		this.rnd = rnd;
		this.sp = solverProperties;
		this.lambda = lambda;
	}
	
	/**
	 * Get lambda (temperature parameter)
	 * @return lambda value
	 */
	public double getLambda() {
		return lambda;
	}
	
	/**
	 * Set lambda (temperature parameter)
	 * @param lambda temperature parameter (0 = deterministic, higher = more stochastic)
	 */
	public void setLambda(double lambda) {
		this.lambda = lambda;
	}
	
	public String getType() {
		if (lambda > 0.0) {
			return "approximate (entropy-regularized perseus)";
		}
		return "approximate (perseus)";
	}

	public double getTotalSolveTime() {
		return totalSolveTime * 0.001;
	}
	
	private ArrayList<BeliefPoint> getBeliefPoints(POMDP pomdp) {
		ArrayList<BeliefPoint> B = new ArrayList<BeliefPoint>();
		HashSet<BeliefPoint> Bset = new HashSet<BeliefPoint>();
		B.add(pomdp.getInitialBelief());
		Bset.add(pomdp.getInitialBelief());
		
		for(int run = 0; run < sp.getBeliefSamplingRuns(); run++) {
			BeliefPoint b = pomdp.getInitialBelief();
			
			for(int step = 0; step < sp.getBeliefSamplingSteps(); step++) {
				pomdp.prepareBelief(b);
				
				// select action and observation
				int action = rnd.nextInt(pomdp.getNumActions());
				ProbabilitySample ps = new ProbabilitySample(rnd);
				for(int o = 0; o < pomdp.getNumObservations(); o++) {
					double prob = b.getActionObservationProbability(action, o);
					if(prob > 1.0) prob = 1.0;
					ps.addItem(o, prob);
				}
				int observation = ps.sampleItem();
				
				// find new belief point
				BeliefPoint bao = pomdp.updateBelief(b, action, observation);
				bao.setHistory(b.getHistoryCopy());
				bao.addToHistory(action);
				bao.addToHistory(observation);
				
				// add belief point and prepare for next step
				if(!Bset.contains(bao)) {
					B.add(bao);
					Bset.add(bao);
				}
				
				b = bao;
			}
		}
		
		// add corner beliefs
		for(int s = 0; s < pomdp.getNumStates(); s++) {
			double[] beliefEntries = new double[pomdp.getNumStates()];
			beliefEntries[s] = 1.0;
			B.add(new BeliefPoint(beliefEntries));
		}
		
		return B;
	}
	
	private ArrayList<AlphaVector> backupStage(POMDP pomdp, ArrayList<AlphaVector> immediateRewards, ArrayList<AlphaVector> V, ArrayList<BeliefPoint> B) {
		int nStates = pomdp.getNumStates();
		int nActions = pomdp.getNumActions();
		int nObservations = pomdp.getNumObservations();
		
		ArrayList<AlphaVector> Vnext = new ArrayList<AlphaVector>();
		List<BeliefPoint> Btilde = new ArrayList<BeliefPoint>();
		Btilde.addAll(B);
		
		// initialize gao vectors
		AlphaVector[][][] gkao = new AlphaVector[V.size()][nActions][nObservations];
		for(int k=0; k<V.size(); k++) {
			for(int a=0; a<nActions; a++) {
				for(int o=0; o<nObservations; o++) {
					double[] entries = new double[nStates];
					
					for(int s=0; s<nStates; s++) {
						double val = 0.0;
						
						for(int sPrime = 0; sPrime < nStates; sPrime++) {
							val += pomdp.getObservationProbability(a, sPrime, o) * pomdp.getTransitionProbability(s, a, sPrime) * V.get(k).getEntry(sPrime);
						}
						
						entries[s] = val;
					}
					
					AlphaVector av = new AlphaVector(entries);
					av.setAction(a);
					gkao[k][a][o] = av;
				}
			}
		}
		assert gkao.length == V.size();
		
		// run the backup stage
		while(Btilde.size() > 0) {
			// sample a belief point uniformly at random
			int beliefIndex = rnd.nextInt(Btilde.size());
			BeliefPoint b = Btilde.get(beliefIndex);
			
			// compute backup(b)
			AlphaVector alpha = backup(pomdp, immediateRewards, gkao, b);
			
			// check if we need to add alpha
			double oldValue = AlphaVector.getValue(b.getBelief(), V);
			double newValue = alpha.getDotProduct(b.getBelief());
			
			if(newValue >= oldValue) {
				assert alpha.getAction() >= 0 && alpha.getAction() < pomdp.getNumActions() : "invalid action: "+alpha.getAction();
				Vnext.add(alpha);
			}
			else {
				int bestVectorIndex = AlphaVector.getBestVectorIndex(b.getBelief(), V);
				assert V.get(bestVectorIndex).getAction() >= 0 && V.get(bestVectorIndex).getAction() < pomdp.getNumActions() : "invalid action: "+V.get(bestVectorIndex).getAction();
				Vnext.add(V.get(bestVectorIndex));
			}
			
			// compute new Btilde containing non-improved belief points
			List<BeliefPoint> newBtilde = new ArrayList<BeliefPoint>();			
			for(BeliefPoint bp : B) {
				double oV = AlphaVector.getValue(bp.getBelief(), V);
				double nV = AlphaVector.getValue(bp.getBelief(), Vnext);
				
				if(nV < oV) {
					newBtilde.add(bp);
				}
			}
			
			Btilde = newBtilde;
		}
		
		return Vnext;
	}
	
	private AlphaVector backup(POMDP pomdp, List<AlphaVector> immediateRewards, AlphaVector[][][] gkao, BeliefPoint b) {
		int nStates = pomdp.getNumStates();
		int nActions = pomdp.getNumActions();
		int nObservations = pomdp.getNumObservations();
		
		List<AlphaVector> ga = new ArrayList<AlphaVector>();
		
		for(int a=0; a<nActions; a++) {
			List<AlphaVector> oVectors = new ArrayList<AlphaVector>();
			for(int o=0; o<nObservations; o++) {
				int K = gkao.length; // Number of Q-function sets (e.g., prior value function sets)

				// For each observation, compute the best backup vector for action a
				if (lambda > 0.0) {
					// Entropy-regularized: use softmax-weighted combination
					double[] dotProducts = new double[K];
					for(int k=0; k<K; k++) {
						dotProducts[k] = gkao[k][a][o].getDotProduct(b.getBelief()) / lambda;
					}
					double[] weights = softmax(dotProducts);
					
					// Compute weighted sum of vectors
					double[] weightedSum = new double[nStates];
					for(int s=0; s<nStates; s++) {
						weightedSum[s] = 0.0;
						for(int k=0; k<K; k++) {
							weightedSum[s] += weights[k] * gkao[k][a][o].getEntry(s);
						}
					}
					AlphaVector weightedVector = new AlphaVector(weightedSum);
					weightedVector.setAction(a);
					oVectors.add(weightedVector);
				} else {
					// Deterministic: use hard max (original behavior)
					double maxVal = Double.NEGATIVE_INFINITY;
					AlphaVector maxVector = null;
					
					for(int k=0; k<K; k++) {
						double product = gkao[k][a][o].getDotProduct(b.getBelief());
						if(product > maxVal) {
							maxVal = product;
							maxVector = gkao[k][a][o];
						}
					}
					
					assert maxVector != null;
					oVectors.add(maxVector);
				}
			}
			
			assert oVectors.size() > 0;
			
			// take sum of the vectors
			AlphaVector sumVector = oVectors.get(0);
			for(int j=1; j<oVectors.size(); j++) {
				sumVector = AlphaVector.sumVectors(sumVector, oVectors.get(j));
			}
			
			// multiply by discount factor
			double[] sumVectorEntries = sumVector.getEntries();
			for(int s=0; s<nStates; s++) {
				sumVectorEntries[s] = pomdp.getDiscountFactor() * sumVectorEntries[s];
			}
			sumVector.setEntries(sumVectorEntries);
			
			AlphaVector av = AlphaVector.sumVectors(immediateRewards.get(a), sumVector);
			av.setAction(a);
			ga.add(av);
		}
		
		assert ga.size() == nActions;
		
		// Action selection: use softmax if lambda > 0, otherwise hard max
		if (lambda > 0.0) {
			// Compute Q-values for all actions
			double[] qValues = new double[nActions];
			for(int a=0; a<nActions; a++) {
				qValues[a] = ga.get(a).getDotProduct(b.getBelief()) / lambda;
			}
			
			// Compute softmax probabilities
			double[] actionProbs = softmax(qValues);
			
			// Return weighted combination of action vectors
			double[] weightedAlpha = new double[nStates];
			for(int s=0; s<nStates; s++) {
				weightedAlpha[s] = 0.0;
				for(int a=0; a<nActions; a++) {
					weightedAlpha[s] += actionProbs[a] * ga.get(a).getEntry(s);
				}
			}
			AlphaVector vFinal = new AlphaVector(weightedAlpha);
			// Set action to the most probable one for compatibility
			int bestAction = 0;
			double bestProb = actionProbs[0];
			for(int a=1; a<nActions; a++) {
				if(actionProbs[a] > bestProb) {
					bestProb = actionProbs[a];
					bestAction = a;
				}
			}
			vFinal.setAction(bestAction);
			return vFinal;
		} else {
			// Deterministic: find the maximizing vector (original behavior)
			double maxVal = Double.NEGATIVE_INFINITY;
			AlphaVector vFinal = null;
			for(AlphaVector av : ga) {
				double product = av.getDotProduct(b.getBelief());
				if(product > maxVal) {
					maxVal = product;
					vFinal = av;
				}
			}
			assert vFinal != null;
			return vFinal;
		}
	}

	/////Persues Solver
	public ArrayList<AlphaVector> solve(POMDP pomdp) {		
		int nStates = pomdp.getNumStates();
		int nActions = pomdp.getNumActions();
		
		System.out.println();
		System.out.println("=== RUN POMDP SOLVER ===");
		System.out.println("Algorithm: ERPerseus (point-based value iteration)");
		System.out.println("Belief sampling started...");
		
		ArrayList<BeliefPoint> B = getBeliefPoints(pomdp);
		System.out.println("Number of beliefs: "+B.size());
		System.out.println();
		
		// create initial vector set and vectors defining immediate rewards
		ArrayList<AlphaVector> V = new ArrayList<AlphaVector>();
		ArrayList<AlphaVector> immediateRewards = new ArrayList<AlphaVector>();
		
		for(int a = 0; a < nActions; a++) {
			// entires stores the rewards for each state for current action based on the reward function
			double[] entries = new double[nStates];
			for(int s = 0; s < nStates; s++) {
				entries[s] = pomdp.getReward(s, a);
			}
			AlphaVector av = new AlphaVector(entries);
			av.setAction(a);
			V.add(av);
			// immediateRewards is a list of AlphaVectors, each representing the immediate rewards for a given action
			immediateRewards.add(av);
		}
		
		//System.out.println("Stage 1: "+V.size()+" vectors");
		
		//OutputFileWriter.dumpValueFunction(pomdp, V, sp.getOutputDir()+"/"+pomdp.getInstanceName()+".alpha"+1, sp.dumpActionLabels());
		
		// run the backup stages
		long startTime = System.currentTimeMillis();
		while(true) {
			ArrayList<AlphaVector> Vnext = backupStage(pomdp, immediateRewards, V, B);
			// Vnext is the new value function after the backup stage
			double valueDifference = getValueDifference(B, V, Vnext);
			//System.out.println("Stage: "+Vnext.size()+" vectors, diff "+valueDifference+", time elapsed "+((System.currentTimeMillis() - startTime) * 0.001)+" sec");
			
			// V is updated to the new value function
			V = Vnext;
			
			//OutputFileWriter.dumpValueFunction(pomdp, V, sp.getOutputDir()+"/"+pomdp.getInstanceName()+".alpha"+stage, sp.dumpActionLabels());
			
			double elapsedTime = (System.currentTimeMillis() - startTime) * 0.001;
			if(valueDifference < sp.getValueFunctionTolerance() || elapsedTime > sp.getTimeLimit()) {
				break;
			}
		}
		
		totalSolveTime = (System.currentTimeMillis() - startTime);
		expectedValue = AlphaVector.getValue(pomdp.getInitialBelief().getBelief(), V);
		int bestindex=AlphaVector.getBestVectorIndex(pomdp.getInitialBelief().getBelief(), V);
		
		System.out.print("Initial Belief: ");
		for(int i=0;i<pomdp.getInitialBelief().getBelief().length;i++)
		{
			System.out.print(pomdp.getInitialBelief().getBelief()[i]+" ");
		}
		System.out.println();
		
		//System.out.println("Best Index:  "+bestindex);
		System.out.println("Selected Action "+V.get(bestindex).getAction());
		// Use File constructor to properly join paths and handle absolute paths
		File outputDir = new File(sp.getOutputDir());
		File outputFile = new File(outputDir, pomdp.getInstanceName() + ".alpha");
		OutputFileWriter.dumpValueFunction(pomdp, V, outputFile.getAbsolutePath(), sp.dumpActionLabels());
		
		return V;
	}

	private double getValueDifference(List<BeliefPoint> B, ArrayList<AlphaVector> V, ArrayList<AlphaVector> Vnext) {
		double maxDifference = Double.NEGATIVE_INFINITY;
		
		for(BeliefPoint b : B) {
			double diff = AlphaVector.getValue(b.getBelief(), Vnext) - AlphaVector.getValue(b.getBelief(), V);
			if(diff > maxDifference) maxDifference = diff;
		}
		
		return maxDifference;
	}
	
	/**
	 * Compute softmax weights: softmax(x_i) = exp(x_i) / sum(exp(x_j))
	 * Uses numerically stable version with max subtraction
	 * 
	 * @param x the input array of values
	 * @return array of softmax probabilities
	 */
	private double[] softmax(double[] x) {
		double[] result = new double[x.length];
		
		// Find max for numerical stability
		double max = Double.NEGATIVE_INFINITY;
		for (double v : x) {
			if (v > max) max = v;
		}
		
		// Compute exp and sum
		double sum = 0.0;
		for (int i = 0; i < x.length; i++) {
			result[i] = Math.exp(x[i] - max);
			sum += result[i];
		}
		
		// Normalize
		for (int i = 0; i < x.length; i++) {
			result[i] /= sum;
		}
		
		return result;
	}
	
	/**
	 * Get expected value of the solution
	 * @return expected value
	 */
	public double getExpectedValue() {
		return expectedValue;
	}
}

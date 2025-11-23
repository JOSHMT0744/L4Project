package iot;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import deltaiot.client.SimulationClient;
import deltaiot.services.Link;
import deltaiot.services.LinkSettings;
import deltaiot.services.Mote;
import pomdp.POMDP;
import solver.BeliefPoint;

import org.apache.commons.math3.special.Gamma;

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
	public static int timestepiot;
	//private static StopWatch stopwatchiot;
	public static Integer moteids[];
	public int selectedindex;
	
	public double entropy;
	public double mutualInformation;
	private double eps;
	
	public DeltaIOTConnector()
	{
		selectedindex=0;
		entropy = 0.0;
		mutualInformation = 0.0;
		eps = 1e-300; // prevent underflow
		//stopwatchiot = StopWatch.getGlobalStopWatch();		
	}
	
	public List<Link> getSelectedMoteLinks(Mote selectedmote) {
		List<Link> l=selectedmote.getLinks();
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
	
	public double getMoteEntropy() {
		System.out.println("Transition function belief: mote no: "+DeltaIOTConnector.selectedmote.getMoteid());
		return this.entropy;
	}
	
	public double getMoteMI() {
		System.out.println("Transition function belief: mote no: "+DeltaIOTConnector.selectedmote.getMoteid());
		return this.mutualInformation;
	}
	
	private double confidenceCorrectedSurprise(double[][][] transitionBelief, double[][][] transitionBeliefReset, int action, int nextstate) {
		// For each belief state, calculate the surpriseCC for going to next state
		// calculated using KL(Dir(curr || reset)
		// Then do weighted sum of all these surprises
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
	
	private double[] getLogPredeProbs(double[][][] transitionBelief, int action, int nextstate) {
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
	
	/* *
	 * Calculating entropy for one dirichlet distribution set of alpha pseudo-counts
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
	
	private double getMoteEntropy(int action, int nextstate) {
		// MUTUAL INFORMATION CALCULATION
		// iterate through each dirichlet distribution 
		double entropy = 0.0;
		for (int stateIndex = 0; stateIndex < p.getNumStates(); stateIndex++) {
			// as we want to know total entropy of the transition beliefs, rather than just the transition entropy, we will be iterating over all possible next states
			// And then weighting this entropy belief by our state belief
			entropy += p.getInitialBelief().getBelief(stateIndex) * dirichlet_entropy(p.transitionBeliefCurr[stateIndex][action]);
		}
		return entropy;
	}
	
	private void appendToFile(String filename, double variable, int moteNumber, int timestep) {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename, true))) {
            writer.write(Integer.toString(moteNumber)+" "+Integer.toString(timestep)+" "+Double.toString(variable));
            writer.newLine(); // adds a newline
        } catch (IOException e) {
            e.printStackTrace();
        }
	}
	
	public void clearFile(String filename) {
	    try (FileWriter fw = new FileWriter(filename, false)) {
	        // Opening with false truncates the file
	    } catch (IOException e) {
	        e.printStackTrace();
	    }
	}
	
	private void updateTransitionBelief(int action, int nextstate) {
		// Work with copies, to ensure not overwriting the POMDPs transition beliefs unless intended to
		double[][][] transitionBeliefCurr = p.transitionBeliefCurr.clone();
		double[][][] transitionBeliefReset = p.transitionBeliefReset.clone();
		
		// Update pseudo-counts by adding normalised likelihoods to relevant indexes
		// This reflects our adjustment in the confidence of the elected transition
		for (int stateIndex = 0; stateIndex < p.getNumStates(); stateIndex++) {
			transitionBeliefCurr[stateIndex][action][nextstate] += 1.0; //(1.0 / Double.valueOf(p.getNumStates())); // update by relative confidence we are in each state?
			transitionBeliefReset[stateIndex][action][nextstate] += 1.0; //(1.0 / Double.valueOf(p.getNumStates()));
		}
			
		// Calculating the probability of moving to nextstate, weighted by the current belief state, for each current belief state
		double[] logPredProbCurrVals = this.getLogPredeProbs(transitionBeliefCurr, action, nextstate);
		double logPredProbCurr = 0;
		for (int currState = 0; currState < p.getNumStates(); currState++) {
			logPredProbCurr += Math.log(p.getInitialBelief().getBelief(currState)) + logPredProbCurrVals[currState];
		}
		assert Math.exp(logPredProbCurr) >= 0 && Math.exp(logPredProbCurr) <= 1;
		
		double[] logPredProbResetVals = this.getLogPredeProbs(transitionBeliefReset, action, nextstate);
		double logPredProbReset = 0;
		for (int currState = 0; currState < p.getNumStates(); currState++) {
			logPredProbReset += Math.log(p.getInitialBelief().getBelief(currState) * Math.exp(logPredProbResetVals[currState]));
		}
		assert Math.exp(logPredProbReset) >= 0 && Math.exp(logPredProbReset) <= 1;
		
		// prevent underflow
		//logPredProbReset = Math.max(logPredProbReset, eps);
		//logPredProbCurr = Math.max(logPredProbCurr, eps);
		
		
		// Calculate Bayes Factor Surprise
		double logSurpriseBF = logPredProbReset- logPredProbCurr;
		double logSurpriseCC = Math.log(confidenceCorrectedSurprise(transitionBeliefCurr, transitionBeliefReset, action, nextstate)); 
				
		// Predefined rate m dictates how much model changes
		//double p_c = 0.6;
		//assert p_c >= 0 && p_c < 1;
		double m = 0.6; //p_c / (1 - p_c);
		
		double gamma = 1.0 / (1.0 + Math.max(eps, Math.exp(-logSurpriseBF)) / m);
		assert gamma >= 0.0 && gamma <= 1.0;
		//double gamma = 1.0 / (1.0 + Math.exp(-logSurpriseBF) / m);
		
		appendToFile("surpriseBF.txt", Math.exp(logSurpriseBF), DeltaIOTConnector.selectedmote.getMoteid(), DeltaIOTConnector.timestepiot);
		appendToFile("gamma.txt", gamma, DeltaIOTConnector.selectedmote.getMoteid(), DeltaIOTConnector.timestepiot);
		appendToFile("surpriseCC.txt", Math.exp(logSurpriseCC), DeltaIOTConnector.selectedmote.getMoteid(), DeltaIOTConnector.timestepiot);
		
		// varSMiLE updating of transitionBeliefCurr
		// CHECK THIS. do we update all transitions, or just that of the next state
		for (int stateIndex = 0; stateIndex < p.getNumStates(); stateIndex++) {
			for (int nextStateIndex = 0; nextStateIndex < p.getNumStates(); nextStateIndex++) {
				transitionBeliefCurr[stateIndex][action][nextStateIndex] = 
						(1- gamma) * transitionBeliefCurr[stateIndex][action][nextStateIndex] 
						+ gamma * transitionBeliefReset[stateIndex][action][nextStateIndex];
			}
				
		}
		
		p.transitionBeliefCurr = transitionBeliefCurr;
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
		
		// Calculate entropy of this new transition given the current belief
		// set mote entropy
		double priorEntropy = this.getMoteEntropy(action, nextstate);
		this.entropy = priorEntropy;
		
		
		// update world probabilities by taking Expectation[transitionBeliefCurr] 
		//// TODO HERE
		this.updateTransitionBelief(action, nextstate);
		// I've circumvented this by using the pseudo counts to just calculate instances of probabilities in the getTransitionProbability function when required
		
		double posteriorEntropy = this.getMoteEntropy(action, nextstate);
		this.mutualInformation = priorEntropy - posteriorEntropy;
		
		///Observation
		int obs = p.getObservation(action, nextstate);
		// Despite being called "Initial" belief, consider this the current belief
		
		BeliefPoint b = p.updateBelief(p.getInitialBelief(), action, obs); // CHANGE THIS ADAPTATION FOR THE SMILE RULE
		
		// Compute surprise here
		
		p.setInitialBelief(b);
	
		return 0;
	}

	
	///SF Check
	public void performDTP() { 		
		int value;
		Link left, right;
		int valueleft,valueright;

		for(Link link : DeltaIOTConnector.selectedmote.getLinks()) {
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
		int value;
		Link left, right;
		int valueleft,valueright;

				for(Link link : DeltaIOTConnector.selectedmote.getLinks()) {
				
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


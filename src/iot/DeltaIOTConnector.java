package iot;

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
	
	public DeltaIOTConnector()
	{
		selectedindex=0;
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
	
	private double[] getStatePredictiveProbs(double[][][] transitionBelief, int action, int nextstate) {
		// Calculate belief pseudo-counts for all possible next states, and then for the specifically chosen next state
		// Use sum over rows as a normaliser so probability is in [0,1]
		double[] nextstatePredictiveProbVals  = new double[p.getNumStates()];

		for (int stateIndex = 0; stateIndex < p.getNumStates(); stateIndex++) {
			double normaliser = Arrays.stream(transitionBelief[stateIndex][action]).sum();
			double beliefProb = p.getInitialBelief().getBelief(stateIndex);
			
			double rowExpectationProb = transitionBelief[stateIndex][action][nextstate] / normaliser;
			nextstatePredictiveProbVals[stateIndex] = beliefProb * rowExpectationProb;
		}
		
		return nextstatePredictiveProbVals;
	}
	
	private void updateTransitionBelief(int action, int nextstate) {
		double[][][] transitionBeliefCurr = p.transitionBeliefCurr;
		double[][][] transitionBeliefReset = p.transitionBeliefReset;
		
		double[] predictiveProbCurrVals = this.getStatePredictiveProbs(transitionBeliefCurr, action, nextstate);
		double predictiveProbCurr = Arrays.stream(predictiveProbCurrVals).sum();
		assert predictiveProbCurr >= 0 && predictiveProbCurr <= 1;
		
		double[] predictiveProbResetVals = this.getStatePredictiveProbs(transitionBeliefReset, action, nextstate);
		double predictiveProbReset = Arrays.stream(predictiveProbResetVals).sum();
		assert predictiveProbReset >= 0 && predictiveProbReset <= 1;
		
		// Calculate Bayes Factor Surprise
		double surpriseBF = predictiveProbReset / predictiveProbCurr;
		
		// Predefined rate m dictates how much model changes
		double m = 0.6;
		double gamma = (m * surpriseBF) / (1 + m * surpriseBF);
		
		// Now compute the likelihood of the transition coming from each state, to be able to update the transition beliefs
		// Normalise each prob value by dividing by sum of all the probs in the list
		double[] currPredProbsNormalised = new double[p.getNumStates()];
		double[] resetPredProbsNormalised = new double[p.getNumStates()];
		for (int stateIndex = 0; stateIndex < p.getNumStates(); stateIndex++) {
			currPredProbsNormalised[stateIndex] = predictiveProbCurrVals[stateIndex] / Arrays.stream(predictiveProbCurrVals).sum();
			resetPredProbsNormalised[stateIndex] = predictiveProbResetVals[stateIndex] / Arrays.stream(predictiveProbResetVals).sum();
		}
		
		// Update pseudo-counts by adding normalised likelihoods to relevant indexes
		// This reflects our adjustment in the confidence of the elected transition
		for (int stateIndex = 0; stateIndex < p.getNumStates(); stateIndex++) {
			transitionBeliefCurr[stateIndex][action][nextstate] += currPredProbsNormalised[stateIndex];
			transitionBeliefReset[stateIndex][action][nextstate] += resetPredProbsNormalised[stateIndex];		
		}
		
		// varSMiLE updating of transitionBeliefCurr
		for (int stateIndex = 0; stateIndex < p.getNumStates(); stateIndex++) {
			transitionBeliefCurr[stateIndex][action][nextstate] = (1- gamma) * transitionBeliefCurr[stateIndex][action][nextstate] + gamma * transitionBeliefReset[stateIndex][action][nextstate];
		}
		
		p.transitionBeliefCurr = transitionBeliefCurr;
		p.transitionBeliefReset = transitionBeliefReset;
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
		
		// Calculate surprise of nextstate
		// Do so by iterating over probability of being in each state and multiplying by expected transition probability (using alpha hparams)
		
		
		// update world probabilities by taking Expectation[transitionBeliefCurr] 
		//// TODO HERE
		this.updateTransitionBelief(action, nextstate);
		// I've circumvented this by using the pseudo counts to just calculate instances of probabilities in the getTransitionProbability function when required
		
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


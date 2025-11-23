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

package pomdp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import deltaiot.services.Link;
import simulator.QoS;
import solver.BeliefPoint;

public class POMDP {	
	private String filename;
	private String instanceName;
	private int nStates;
	private int nActions;
	private int nObservations;
	private double discountFactor;
	private int currentState; ///Added for IoT
	
	private double[][] rewardFunction;
	//Changed transitionFunction to public from private
	public double[][][] transitionFunction;
	private double[][][] observationFunction;
	private double minReward = Double.POSITIVE_INFINITY;
	
	// alpha vectors for Dir(.) distribution representing transition probabilities
	public double[][][] transitionBeliefReset;
	public double[][][] transitionBeliefCurr;
	
	// vectors storing entropy at each timestep for transition belief distributions
	public double[] entropy;
	
	private BeliefPoint b0;
	
	private HashMap<Integer,String> actionLabels;
	
	public POMDP(String filename, 
			int nStates, 
			int nActions, 
			int nObservations, 
			double discountFactor, 
			double[][] rewardFunction, 
			double[][][] transitionFunction, 
			double[][][] observationFunction, 
			HashMap<Integer,String> actionLabels, 
			BeliefPoint b0,
			double[][][] transitionBeliefReset, // effectively a collection of SxA dirichlet distribution hyperparameter collections of size S
			double[][][] transitionBeliefCurr
			) {		
		String[] filenameSplit = filename.split("/");
		this.filename = filenameSplit[filenameSplit.length-1];
		this.instanceName = filenameSplit[filenameSplit.length-1].replace(".POMDP", "");
		this.nStates = nStates;
		this.nActions = nActions;
		this.nObservations = nObservations;
		this.discountFactor = discountFactor;
		this.rewardFunction = rewardFunction;
		this.transitionFunction = transitionFunction;
		this.observationFunction = observationFunction;
		this.actionLabels = actionLabels;
		this.b0 = b0;
				
		// Using beliefs instead of fixed probs for transitions
		this.transitionBeliefReset = transitionBeliefReset;
		this.transitionBeliefCurr = transitionBeliefCurr;
		
		
		// compute min reward
		for(int s=0; s<nStates; s++) {
			for(int a=0; a<nActions; a++) {
				minReward = Math.min(minReward, rewardFunction[s][a]);
			}
		}
	}
	
	public int getNumStates() {
		return nStates;
	}
	
	public int getNumActions() {
		return nActions;
	}
	
	public int getNumObservations() {
		return nObservations;
	}
	
	public double getDiscountFactor() {
		return discountFactor;
	}
	
	public double getTransitionProbability(int s, int a, int sNext) {
		assert s < nStates && a < nActions && sNext < nStates;
		// take expectation over beliefs as an update of the world model
		double worldTransitionFn = transitionBeliefCurr[s][a][sNext] / (Arrays.stream(transitionBeliefCurr[s][a]).sum()); // 1e-6 + 
		return worldTransitionFn;
	}
	
	public double getReward(int s, int a) {
		assert s < nStates && a < nActions;
		return rewardFunction[s][a];
	}
	
	public double getObservationProbability(int a, int sNext, int o) {
		assert a < nActions && sNext<nStates && o < nObservations;
		return observationFunction[a][sNext][o];
	}
	
	public double getMinReward() {
		return minReward;
	}
	
	public String getFilename() {
		return filename;
	}
	
	public String getInstanceName() {
		return instanceName;
	}
	
	public String getActionLabel(int a) {
		return actionLabels.get(a);
	}
	
	/**
	 * 
	 * @param b = belief at current timestep
	 * @param a = action executed by SAS
	 * @param o = observation from action a
	 * @return
	 */
	public BeliefPoint updateBelief(BeliefPoint b, int a, int o) {
		assert a < nActions && o < nObservations;
		double[] newBelief = new double[nStates];
		
		// check if belief point has been prepared
		if(!b.hasActionObservationProbabilities()) {
			prepareBelief(b);
		}
		
		// compute normalizing constant
		double nc = b.getActionObservationProbability(a, o);
		assert nc > 0.0 : "o cannot be observed when executing a in belief b";
		
		// compute the new belief vector
		// -> For each state we are "possibly" in (according to belief likelihood), calculate transition probability for each state we could "possibly" end up in
		for(int sNext = 0; sNext < nStates; sNext++) {
			double beliefEntry = 0.0;
			
			for(int s = 0; s < nStates; s++) {
				// getTransitionProbability(s, a, sNext) is X_n (in  S!S!L)
				beliefEntry += getTransitionProbability(s, a, sNext) * b.getBelief(s);
			}
			
			newBelief[sNext] = beliefEntry * (getObservationProbability(a, sNext, o) / nc);
		}
		
		return new BeliefPoint(newBelief);
	}
	
	/**
	 * Calculates the `aoprobs` for the belief, b. 
	 * This is done by iterating over the matrix and for each possible state, calculate the transition probability (getTransitionProbability()).
	 * Then sum the average probability based on confidence across all belief state probabilities.
	 * @param b
	 */
	public void prepareBelief(BeliefPoint b) {
		assert b != null;
		if(b.hasActionObservationProbabilities()) return;
		
		double[][] aoProbs = new double[nActions][nObservations];
		
		for(int action = 0; action < nActions; action++) {
			for(int obs=0; obs < nObservations; obs++) {
				double prob = 0.0;
				
				for(int sNext=0; sNext < nStates; sNext++) {
					double p = 0.0;
					
					for(int s=0; s<nStates; s++) {
						// p = the belief-confidence averaged transition probability
						// so p is effectively the belief's quantification of T(s', s, a)
						p += getTransitionProbability(s, action, sNext) * b.getBelief(s);
					}
					
					prob += getObservationProbability(action, sNext, obs) * p;
				}
				
				aoProbs[action][obs] = prob;
			}
		}
		
		b.setActionObservationProbabilities(aoProbs);
	}
	
	public BeliefPoint getInitialBelief() {
		return b0;
	}
	
	/////Added for for IoT
	public void setInitialBelief(BeliefPoint b)
	{
		b0=b;
	}
	
	///// Decrease power transmission
	
	
	////Added for IoT//to perform action
	public int nextState(int currentState, int action) {
		// TODO Auto-generated method stub
		///check for DeltaIOT//////////////////////////////
		iot.DeltaIOTConnector dataConnector = new iot.DeltaIOTConnector();
		
		if(action == 0) {
			System.out.println("DTP");
			dataConnector.performDTP(); // decrease transmission power			
		}
		else if(action==1) {
			System.out.println("ITP");
			dataConnector.performITP();	 // increase transmission power		
		}
		
		ArrayList<QoS> result = iot.DeltaIOTConnector.networkMgmt.getNetworkQoS(iot.DeltaIOTConnector.timestepiot+1); // get a visual of what this ArrayList<QoS> actually looks like
		double packetLoss = result.get(result.size()-1).getPacketLoss();
		// This is being performed inside of loop of the motes, so use timestepiot to get QoS for that specific mote
		double energyConsumption = result.get(result.size()-1).getEnergyConsumption();
		
		if(energyConsumption < 20 && packetLoss < 0.20) {
			return 0;
		}
		else if(energyConsumption < 20 && packetLoss >= 0.20) {
			return 1;
		}
		else if(energyConsumption >= 20 && packetLoss < 0.20) {
			return 2;
		}
		else if(energyConsumption >= 20 && packetLoss >= 0.20) {
			return 3;
		}
		
		return 0;
	}
	
	///Set it to currentState at the beginning. Each integer indicates the state
	public int getInitialState() {
		// (PacketLoss, PowerConsumption, Period (timestep)) for all timesteps up to now
		ArrayList<QoS> result = iot.DeltaIOTConnector.networkMgmt.getNetworkQoS(1);
		
		System.out.println("result size"+result.size());
		// Get PL and EC at current timestep
		double packetLoss = result.get(result.size()-1).getPacketLoss();
		double energyConsumption = result.get(result.size()-1).getEnergyConsumption();
		
		if(energyConsumption < 20 && packetLoss < 0.20) {
			return 0;
		}
		else if(energyConsumption < 20 && packetLoss >= 0.20) {
			return 1;
		}
		else if(energyConsumption >= 20 && packetLoss < 0.20) {
			return 2;
		}
		else if(energyConsumption >= 20 && packetLoss >= 0.20) {
			return 3;
		}
		
		return 0;
	}
	
	public int getCurrentState() {
		return currentState;
	}
	
	public void setCurrentState(int s) {
		currentState=s;
	}
	
	
	public int getObservation(Integer action, Integer statePrime) {
		// TODO Auto-generated method stub
		
		System.out.println("observation function: mote no:  "+iot.DeltaIOTConnector.selectedmote.getMoteid());
		//for (Link link : DeltaIOTConnector.selectedmote.getLinks()) {
		for (Link link : iot.DeltaIOTConnector.selectedmote.getLinks()) {	
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
}
	
	

	
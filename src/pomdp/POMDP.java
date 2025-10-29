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
	
	private BeliefPoint b0;
	
	private HashMap<Integer,String> actionLabels;
	
	public POMDP(String filename, int nStates, int nActions, int nObservations, double discountFactor, double[][] rewardFunction, double[][][] transitionFunction, double[][][] observationFunction, HashMap<Integer,String> actionLabels, BeliefPoint b0) {		
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
		assert s<nStates && a<nActions && sNext<nStates;
		return transitionFunction[s][a][sNext];
	}
	
	public double getReward(int s, int a) {
		assert s<nStates && a<nActions;
		return rewardFunction[s][a];
	}
	
	public double getObservationProbability(int a, int sNext, int o) {
		assert a<nActions && sNext<nStates && o<nObservations;
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
	
	public BeliefPoint updateBelief(BeliefPoint b, int a, int o) {
		assert a<nActions && o<nObservations;
		double[] newBelief = new double[nStates];
		
		// check if belief point has been prepared
		if(!b.hasActionObservationProbabilities()) {
			prepareBelief(b);
		}
		
		// compute normalizing constant
		double nc = b.getActionObservationProbability(a, o);
		assert nc > 0.0 : "o cannot be observed when executing a in belief b";
		
		// compute the new belief vector
		for(int sNext=0; sNext<nStates; sNext++) {
			double beliefEntry = 0.0;
			
			for(int s=0; s<nStates; s++) {
				beliefEntry += getTransitionProbability(s, a, sNext) * b.getBelief(s);
			}
			
			newBelief[sNext] = beliefEntry * (getObservationProbability(a, sNext, o) / nc);
		}
		
		return new BeliefPoint(newBelief);
	}
	
	public void prepareBelief(BeliefPoint b) {
		assert b != null;
		if(b.hasActionObservationProbabilities()) return;
		
		double[][] aoProbs = new double[nActions][nObservations];
		
		for(int a=0; a<nActions; a++) {
			for(int o=0; o<nObservations; o++) {
				double prob = 0.0;
				
				for(int sNext=0; sNext<nStates; sNext++) {
					double p = 0.0;
					
					for(int s=0; s<nStates; s++) {
						p += getTransitionProbability(s, a, sNext) * b.getBelief(s);
					}
					
					prob += getObservationProbability(a, sNext, o) * p;
				}
				
				aoProbs[a][o] = prob;
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
		iot.DeltaIOTConnector dataConnector=new iot.DeltaIOTConnector();
		if(action==0)
		{
			System.out.println("DTP");
			dataConnector.performDTP(); // decrease transmission power
			
			
			
			///check it
			ArrayList<QoS> result = iot.DeltaIOTConnector.networkMgmt.getNetworkQoS(iot.DeltaIOTConnector.timestepiot+1);
			
			double packetLoss=result.get(result.size()-1).getPacketLoss();
			double energyConsumption=result.get(result.size()-1).getEnergyConsumption();
			//System.out.println("packet loss: "+pl+"   "+ec);
			if(energyConsumption<20 && packetLoss<0.20)
			{
				return 0;
			}
			else if(energyConsumption<20 && packetLoss>=0.20)
			{
				return 1;
			}
			else if(energyConsumption>=20 && packetLoss<0.20)
			{
				return 2;
			}
			else if(energyConsumption>=20 && packetLoss>=0.20)
			{
				return 3;
			}
			
			
		}
		else if(action==1)
		{
			dataConnector.performITP();
			
			///check it
			ArrayList<QoS> result = iot.DeltaIOTConnector.networkMgmt.getNetworkQoS(iot.DeltaIOTConnector.timestepiot+1);
			
			
			double packetLoss=result.get(result.size()-1).getPacketLoss();
			double energyConsumption=result.get(result.size()-1).getEnergyConsumption();
			
			if(energyConsumption<20 && packetLoss<0.20)
			{
				return 0;
			}
			else if(energyConsumption<20 && packetLoss>=0.20)
			{
				return 1;
			}
			else if(energyConsumption>=20 && packetLoss<0.20)
			{
				return 2;
			}
			else if(energyConsumption>=20 && packetLoss>=0.20)
			{
				return 3;
			}
			
		}
		
		
		
		
		
		return 0;
	}
	///Set it to currentState at the beginning. Each integer indicates the state
	public int getInitialState()
	{
		///check it
		ArrayList<QoS> result = iot.DeltaIOTConnector.networkMgmt.getNetworkQoS(1);
		
		System.out.println("result size"+result.size());
		double packetLoss = result.get(result.size()-1).getPacketLoss();
		double energyConsumption = result.get(result.size()-1).getEnergyConsumption();
		
		if(energyConsumption < 20 && packetLoss < 0.20)
		{
			return 0;
		}
		else if(energyConsumption < 20 && packetLoss >= 0.20)
		{
			return 1;
		}
		else if(energyConsumption >= 20 && packetLoss < 0.20)
		{
			return 2;
		}
		else if(energyConsumption >= 20 && packetLoss >= 0.20)
		{
			return 3;
		}
		
	
		
		return 0;
	}
	
	public int getCurrentState()
	{
		return currentState;
	}
	
	public void setCurrentState(int s)
	{
		currentState=s;
	}
	
	
	public int getObservation(Integer action, Integer statePrime) {
		// TODO Auto-generated method stub
		
		//DeltaIOTConnector.motes = DeltaIOTConnector.networkMgmt.getProbe().getAllMotes();
			
		//DeltaIOTConnector.motes = DeltaIOTConnector.networkMgmt.getProbe().getAllMotes();
		//ArrayList<Mote> motesobs=DeltaIOTConnector.networkMgmt.getProbe().getAllMotes();	
			
			//SF example
		//for(Mote m:DeltaIOTConnector.motes)
		//{
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
		
		//}
		//}
		/*if (mote.getLinks().size() == 2) {
			if (mote.getLinks().get(0).getPower() != mote.getLinks().get(1).getPower())
				return true;
		}
		*/
		return 0;
	}	

		
}
	
	

	
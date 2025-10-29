package main;

import java.util.ArrayList;

import deltaiot.client.Effector;
import deltaiot.client.Probe;
import deltaiot.client.SimulationClient;
import mapek.FeedbackLoop;
import simulator.QoS;
import simulator.Simulator;

public class SimpleAdaptation {
	
	SimulationClient networkMgmt;
	public void start(){
		
		// Create a simulation client object		
		networkMgmt = new SimulationClient();
		
		// Create Feedback loop
		FeedbackLoop feedbackLoop = new FeedbackLoop();

		// get probe and effectors
		Probe probe = networkMgmt.getProbe();
		Effector effector = networkMgmt.getEffector();

		// Connect probe and effectors with feedback loop
		feedbackLoop.setProbe(probe);
		feedbackLoop.setEffector(effector);

		// StartFeedback loop
		feedbackLoop.start();
		
		// QoS (Quality of Service) contains 
		// (1) the time when the last period finished 
		// (2) the packet loss of the network
		// (3) Energy consumption of the network
		ArrayList<QoS> result = networkMgmt.getNetworkQoS(96);
		
		System.out.println("Run, PacketLoss, EnergyConsumption");
		result.forEach(qos -> System.out.println(qos));

	}
	
	public static void main(String[] args) {
		SimpleAdaptation client = new SimpleAdaptation();
		client.start();
 	}

	public Simulator getSimulator() {
		return networkMgmt.getSimulator();
	}
}

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

package main;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Random;

import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonObject;

import deltaiot.client.SimulationClient;
import deltaiot.services.Mote;
import iot.DeltaIOTConnector;
import pruning.PruneStandard;
import simulator.QoS;

import pruning.PruneAccelerated;
import pruning.PruneMethod;
import solver.AlphaVector;
import solver.BeliefPoint;
import solver.Solver;
import solver.SolverApproximate;
import solver.SolverExact;

import lpsolver.LPGurobi;
import lpsolver.LPModel;
import lpsolver.LPSolve;
import lpsolver.LPjoptimizer;
import pomdp.POMDP;
//import pomdp.Parser;
import pomdp.PomdpParser;
import pomdp.SolverProperties;

import charts.LineChart;
import charts.BoxWhiskerChart;



public class SolvePOMDP {
	/* Class for configuring and running each component of the  */
	private SolverProperties sp;     // object containing user-defined properties
	private PruneMethod pm;          // pruning method used by incremental pruning
	private LPModel lp;              // linear programming solver used by incremental pruning
	private Solver solver;           // the solver that we use to solve a POMDP, which is exact or approximate
	private String domainDirName;    // name of the directory containing .POMDP files
	private String domainDir;        // full path of the domain directory
	
	public SolvePOMDP() {
		// read parameters from config file
		readConfigFile();
		
		// check if required directories exist
		configureDirectories();

		// configure LP solver
		lp.setEpsilon(sp.getEpsilon());
		lp.setAcceleratedLPThreshold(sp.getAcceleratedLPThreshold());
		lp.setAcceleratedLPTolerance(sp.getAcceleratedLPTolerance());
		lp.setCoefficientThreshold(sp.getCoefficientThreshold());
		lp.init();
	}
	
	/**
	 * Read the solver.config file. It creates a properties object and it initialises
	 * the pruning method and LP solver.
	 */
	private void readConfigFile() {
		this.sp = new SolverProperties();
		
		Properties properties = new Properties();
		
		try {
			FileInputStream file = new FileInputStream("src/solver.config");
			properties.load(file);
			file.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		// Exact Algorithm Settings
		sp.setEpsilon(Double.parseDouble(properties.getProperty("epsilon")));
		sp.setAcceleratedLPThreshold(Integer.parseInt(properties.getProperty("acceleratedLPThreshold")));
		sp.setAcceleratedLPTolerance(Double.parseDouble(properties.getProperty("acceleratedTolerance")));
		sp.setCoefficientThreshold(Double.parseDouble(properties.getProperty("coefficientThreshold")));

		// Directories
		sp.setOutputDirName(properties.getProperty("outputDirectory"));
		this.domainDirName = properties.getProperty("domainDirectory");
		
		// Approximate Algorithm Settings
		sp.setBeliefSamplingRuns(Integer.parseInt(properties.getProperty("beliefSamplingRuns")));
		sp.setBeliefSamplingSteps(Integer.parseInt(properties.getProperty("beliefSamplingSteps")));
		
		// General Settings
		String algorithmType = properties.getProperty("algorithmType");
		sp.setTimeLimit(Double.parseDouble(properties.getProperty("timeLimit")));
		sp.setValueFunctionTolerance(Double.parseDouble(properties.getProperty("valueFunctionTolerance")));

		// Error checking solver.config parameters
		if(!algorithmType.equals("perseus") && !algorithmType.equals("gip")) {
			throw new RuntimeException("Unexpected algorithm type in properties file");
		}
		
		String dumpPolicyGraphStr = properties.getProperty("dumpPolicyGraph");
		if(!dumpPolicyGraphStr.equals("true") && !dumpPolicyGraphStr.equals("false")) {
			throw new RuntimeException("Policy graph property must be either true or false");
		}
		else {
			sp.setDumpPolicyGraph(dumpPolicyGraphStr.equals("true") && algorithmType.equals("gip"));
		}
		
		String dumpActionLabelsStr = properties.getProperty("dumpActionLabels");
		if(!dumpActionLabelsStr.equals("true") && !dumpActionLabelsStr.equals("false")) {
			throw new RuntimeException("Action label property must be either true or false");
		}
		else {
			sp.setDumpActionLabels(dumpActionLabelsStr.equals("true"));
		}
		
		System.out.println();
		System.out.println("=== SOLVER PARAMETERS ===");
		System.out.println("Epsilon: "+sp.getEpsilon());
		System.out.println("Value function tolerance: "+sp.getValueFunctionTolerance());
		System.out.println("Accelerated LP threshold: "+sp.getAcceleratedLPThreshold());
		System.out.println("Accelerated LP tolerance: "+sp.getAcceleratedLPTolerance());
		System.out.println("LP coefficient threshold: "+sp.getCoefficientThreshold());
		System.out.println("Time limit: "+sp.getTimeLimit());
		System.out.println("Belief sampling runs: "+sp.getBeliefSamplingRuns());
		System.out.println("Belief sampling steps: "+sp.getBeliefSamplingSteps());
		System.out.println("Dump policy graph: "+sp.dumpPolicyGraph());
		System.out.println("Dump action labels: "+sp.dumpActionLabels());
		
		// load required LP solver
		String lpSolver = properties.getProperty("lpsolver");
		switch (lpSolver) {
			case "gurobi":
				this.lp = new LPGurobi();
				break;
			case "joptimizer":
				this.lp = new LPjoptimizer();
				break;
			case "lpsolve":
				this.lp = new LPSolve();
				break;
			default:
				throw new RuntimeException("Unexpected LP solver in properties file");
		}
		
		// load required pruning algorithm
		String pruningAlgorithm = properties.getProperty("pruningMethod");
		switch (pruningAlgorithm) {
			case "standard":
				this.pm = new PruneStandard();
				this.pm.setLPModel(lp);
				break;
			case "accelerated":
				this.pm = new PruneAccelerated();
				this.pm.setLPModel(lp);
				break;
			default:
				throw new RuntimeException("Unexpected pruning method in properties file");
		}
		
		// load required POMDP algorithm
		switch (algorithmType) {
			case "gip":
				this.solver = new SolverExact(sp, lp, pm);
				break;
			case "perseus":
				this.solver = new SolverApproximate(sp, new Random(222));
				break;
			default:
				throw new RuntimeException("Unexpected algorithm type in properties file");
		}
		
		System.out.println("Algorithm: "+algorithmType);
		System.out.println("LP solver: "+lp.getName());
	}
	
	/**
	 * Checks if the desired domain and output directories exist, and it sets the full path to these directories.
	 */
	private void configureDirectories() {
		String path = SolvePOMDP.class.getProtectionDomain().getCodeSource().getLocation().getPath();
		System.out.println("path"+path);
		String decodedPath = "";
		
		try {
			decodedPath = URLDecoder.decode(path, "UTF-8");
			System.out.println("decodedPath"+decodedPath);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		
		if(decodedPath.endsWith(".jar")) {
			// solver has been started from jar, so we assume that output exists in the same directory as the jar file			
			int endIndex = decodedPath.lastIndexOf("/");
			String workingDir = decodedPath.substring(0, endIndex);
			sp.setWorkingDir(workingDir);
			domainDir = workingDir+"/"+domainDirName;
		}
		else {
			// solver has not been started from jar, so we assume that output exists in the current directory
			sp.setWorkingDir("");
			domainDir = domainDirName;
		}	

		File dir = new File(sp.getOutputDir());
		System.out.println("dir"+dir);
		
		if(!dir.exists()) {
		    boolean created = dir.mkdirs();
		    if (!created) {
		        throw new RuntimeException("Output directory could not be created");
		    }
		}
		else if(!dir.isDirectory()) {
		    throw new RuntimeException("Output path exists but is not a directory");
		}
		
		System.out.println("Output directory: "+sp.getOutputDir());
		System.out.println("Domain directory: "+domainDir);
	}
	
	/**
	 * Close the LP (Linear Programming (Exact method)) solvers
	 */
	public void close () {
		lp.close();
	}
	
	/**
	 * Solve a POMDP defined by a .POMDP file
	 * @param pomdpFileName filename of a domain in the domain directory
	 */
	public void run(String pomdpFileName) {
		
		if(pomdpFileName.equals("IoT.POMDP"))
		{
			runCaseIoT(pomdpFileName);
		}	
	}
	
	
	/**
	 * Method to run experiments for DeltaIoT case using POMDP
	 * @param pomdpFileName
	 */
	public void runCaseIoT(String pomdpFileName) {
		///Results Log
		try
		{
		FileWriter fwMECSatProb=new FileWriter("MECSatProb.txt"); // Logs the probability that MEC is satisfied 
		PrintWriter pwMECSatProb=new PrintWriter(fwMECSatProb);
		FileWriter fwRPLSatProb=new FileWriter("RPLSatProb.txt"); // Logs the probability that RPL is satisfied
		PrintWriter pwRPLSatProb=new PrintWriter(fwRPLSatProb);
		
		FileWriter fwMECSat=new FileWriter("MECSat.txt"); // Logs the MECSat value
		PrintWriter pwMECSat=new PrintWriter(fwMECSat);
		FileWriter fwRPLSat=new FileWriter("RPLSat.txt"); // Logs the RPLSat value
		PrintWriter pwRPLSat=new PrintWriter(fwRPLSat);
		FileWriter fwaction=new FileWriter("SelectedAction.txt"); // Logs which action is taken increase or decrease power)
		PrintWriter pwaction=new PrintWriter(fwaction);
		
		FileWriter fwMECSattimestep=new FileWriter("MECSattimestep.txt"); // At specific timesteps
		PrintWriter pwMECSattimestep=new PrintWriter(fwMECSattimestep);
		FileWriter fwRPLSattimestep=new FileWriter("RPLSattimestep.txt");
		PrintWriter pwRPLSattimestep=new PrintWriter(fwRPLSattimestep);
		
		JsonArray rlist = new JsonArray();
		
		
		// read POMDP file
		POMDP pomdp = PomdpParser.readPOMDP(domainDir+"/"+pomdpFileName);
		iot.DeltaIOTConnector.p=pomdp;
		
	
		
		////////IoT Code///////////
		
		//iot.DeltaIOTConnector.timestepiot=timestep;
		//System.out.println("timestep: "+timestep);
		iot.DeltaIOTConnector.networkMgmt = new SimulationClient();
		
		iot.DeltaIOTConnector deltaConnector = new iot.DeltaIOTConnector();
		iot.DeltaIOTConnector.timestepiot = 0;
		
		for (int timestep = 0; timestep < 100; timestep++) {
			/*
			 * MONITOR
			 */
			JsonObject obj =new JsonObject();
			obj.put("timestep", timestep+"");
			iot.DeltaIOTConnector.motes = iot.DeltaIOTConnector.networkMgmt.getProbe().getAllMotes();
		
			System.out.println("motes recieved");
		

			int currState = pomdp.getInitialState();
			System.out.println("Initial state: "+currState);
			pomdp.setCurrentState(currState);
			
			System.out.println("current state: "+ pomdp.getCurrentState());
			
			
			/*
			 * Make KNOWLEDGE adjustment here??
			 * POMDP contains `public double[][][] transitionFunction;` that needs to be adjusted
			 */
		
		
			for(Mote m : iot.DeltaIOTConnector.motes) {
				System.out.println("\nTime Step: "+timestep);
				// Simulator object holds the list of motes, gateways, turnOrder, runInfo and qos values.
				// THis will simulate sending packets through the network to the gateways
				// Each gateway will aggregate information about packet-loss and power-consumption
				// The QoS values will be stored in the Simulator object
				
				/*
				 * ANALYSE
				 */
				iot.DeltaIOTConnector.networkMgmt.getSimulator().doSingleRun();
				
				iot.DeltaIOTConnector.selectedmote = m;
				System.out.println("Mote Id"+iot.DeltaIOTConnector.selectedmote.getMoteid());
				
				obj.put("Mote Id", iot.DeltaIOTConnector.selectedmote.getMoteid()+"");		
			
				BeliefPoint initialbelief = pomdp.getInitialBelief(); // b0
				double b[] = initialbelief.getBelief();
				System.out.println(b[0]+" "+b[1]+" "+b[2]+" "+b[3]);
				double mecsatprob = b[0]+b[1]; // How does this line work..?
				double rplsatprob = b[0]+b[2];
				pwMECSatProb.println(timestep+" "+mecsatprob);
				pwRPLSatProb.println(timestep+" "+rplsatprob);
				pwMECSatProb.flush();
				pwRPLSatProb.flush();
				
				/*
				 * PLANNING
				 */
				// Each AlphaVector encodes a linear function over beliefs V(b) = alpha * b
				ArrayList<AlphaVector> V1 = solver.solve(pomdp);
				System.out.println("Value size: "+V1.size()+"  Action labels: "+ V1.get(0).getAction());
				
				// Loop over alpha vectors: inspect each vector and compute its value at the belief
				for(int i=0; i < V1.size(); i++) {
					System.out.println("~~~~~~~~~~~~~~~~~~~~~~~");
					System.out.println("Action labels: "+ V1.get(i).getAction());
					System.out.println("~~~~~~~~~~~~~~~~~~~~~~~");
					double expectedvalue=V1.get(i).getDotProduct(pomdp.getInitialBelief().getBelief());
					System.out.println("Expected Value: "+ expectedvalue);
				
				}
				// Select the best alpha vector and its action
				int bestindex = AlphaVector.getBestVectorIndex(pomdp.getInitialBelief().getBelief(), V1);
				int selectedAction = V1.get(bestindex).getAction();
				System.out.println("Selected Action: "+selectedAction);
				
				
				pwaction.println(timestep+" "+selectedAction);
				pwaction.flush();
				
				/*
				 * EXECUTE
				 */
				obj.put("Selected Action", selectedAction+"");
				pomdp.setInitialBelief(initialbelief); 
				iot.DeltaIOTConnector.p = pomdp;
				deltaConnector.performAction(selectedAction);
				pomdp=iot.DeltaIOTConnector.p; // as POMDP is being updated in performAction, must adjust the variable `pomdp` here
			 
				System.out.println("Current State: "+pomdp.getCurrentState());
				// timestepiot is acting as an index for retrieving QoS for each mote
			 	ArrayList<QoS> result = DeltaIOTConnector.networkMgmt.getNetworkQoS(iot.DeltaIOTConnector.timestepiot+1); 
			 	System.out.println("QOS list size: "+result.size());
				
			 	double packetLoss=result.get(result.size()-1).getPacketLoss();
			 	double energyConsumption=result.get(result.size()-1).getEnergyConsumption();
			 	System.out.println("packet loss: "+packetLoss+"   Energy Consumption: "+energyConsumption);
			 	
			 	pwMECSat.println(timestep+" "+energyConsumption);
			 	pwRPLSat.println(timestep+" "+packetLoss);
			 	pwMECSat.flush();
			 	pwRPLSat.flush();
			 	
			 	obj.put("packet loss", packetLoss+"");
			 	obj.put("Energy Consumption",energyConsumption+"");
			 	iot.DeltaIOTConnector.timestepiot++;				
			 	rlist.add(obj);
			 	
			}///End of Motes loop
			
			String plstimestep = "";
			String ecstimestep = "";
			ArrayList<QoS> result1 = (ArrayList<QoS>)DeltaIOTConnector.networkMgmt.getSimulator().getQosValues();
			
			// Total packet loss and energy consumption across every mote in the network
			double pl1=result1.get(result1.size()-1).getPacketLoss();
			double ec1=result1.get(result1.size()-1).getEnergyConsumption();
			plstimestep = timestep+" ";
			ecstimestep = timestep+" ";
			plstimestep = plstimestep+pl1;
			ecstimestep = ecstimestep+ec1;
			
			System.out.println("packet loss: "+plstimestep+"energy consumption"+ecstimestep);
			pwMECSattimestep.println(ecstimestep);
			pwRPLSattimestep.println(plstimestep);
			pwMECSattimestep.flush();
			pwRPLSattimestep.flush();
		}
		
		////////////////////////////////
		
		
		pwMECSat.close();
		pwRPLSat.close();
		pwaction.close();
		fwaction.close();
		fwMECSat.close();
		fwRPLSat.close();	
		pwMECSattimestep.close();
		pwRPLSattimestep.close();
		pwMECSatProb.close();
		pwRPLSatProb.close();
		fwMECSatProb.close();
		fwRPLSatProb.close();
		
		// print results
		String outputFilePG = sp.getOutputDir()+"/"+pomdp.getInstanceName()+".pg";
		String outputFileAlpha = sp.getOutputDir()+"/"+pomdp.getInstanceName()+".alpha";
		System.out.println();
		System.out.println("=== RESULTS ===");
		System.out.println("Expected value: "+solver.getExpectedValue());
		System.out.println("Alpha vectors: "+outputFileAlpha);
		if(sp.dumpPolicyGraph()) System.out.println("Policy graph: "+outputFilePG);
		System.out.println("Running time: "+solver.getTotalSolveTime()+" sec");
		
		}
		catch(IOException ioex)
		{
			ioex.printStackTrace();
		}
	}
	
	/**
	 * Main entry point of the SolvePOMDP software
	 * @param args first argument should be a filename of a .POMDP file
	 */
	public static void main(String[] args) {		
		System.out.println("SolvePOMDP v0.0.3");
		System.out.println("Author: Erwin Walraven");
		System.out.println("Web: erwinwalraven.nl/solvepomdp");
		System.out.println("Delft University of Technology");
		
		if(args.length == 0) {
			System.out.println();
			System.out.println("First argument must be the name of a file in the domains directory!");
			//System.exit(0);
		}
		
		SolvePOMDP ps = new SolvePOMDP();
		
		ps.run("IoT.POMDP");
		ps.close();

		
		// Graph output		
		LineChart linechart_MEC = new LineChart("MECSattimestep.txt", "MEC Satisfaction", "MEC over time");
		LineChart linechart_RPL = new LineChart("RPLSattimestep.txt", "RPL Satisfaction", "RPL over time");
		linechart_MEC.pack();
		linechart_RPL.pack();
		
		BoxWhiskerChart bw_MEC = new BoxWhiskerChart("MECSattimestep.txt", "MEC Satisfaction", "MEC over time");
		bw_MEC.pack();
		
		linechart_MEC.setVisible(true);
		linechart_RPL.setVisible(true);
		bw_MEC.setVisible(true);
	}
}

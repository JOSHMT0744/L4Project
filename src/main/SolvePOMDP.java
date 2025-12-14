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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
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
import pomdp.POMDP;
//import pomdp.Parser;
import pomdp.PomdpParser;
import pomdp.SolverProperties;
import simulator.QoS;
import solver.AlphaVector;
import solver.BeliefPoint;
import solver.Solver;
import solver.SolverApproximate;



public class SolvePOMDP {
	/* Class for configuring and running each component of the  */
	private SolverProperties sp;     // object containing user-defined properties
	private Solver solver;           // the solver that we use to solve a POMDP, which is exact or approximate
	private String domainDirName;    // name of the directory containing .POMDP files
	private String domainDir;        // full path of the domain directory
	
	/**
	 * Find Python executable in virtual environment
	 */
	private static String findPythonExecutable() {
		// Try Windows path first
		File venvWindows = new File(".venv\\Scripts\\python.exe");
		if (venvWindows.exists()) {
			return venvWindows.getPath();
		}
		
		// Try Linux/Mac path
		File venvUnix = new File(".venv/bin/python");
		if (venvUnix.exists()) {
			return venvUnix.getPath();
		}
		
		// Try from L4Project directory
		File venvL4Windows = new File("L4Project/.venv/Scripts/python.exe");
		if (venvL4Windows.exists()) {
			return venvL4Windows.getPath();
		}
		
		File venvL4Unix = new File("L4Project/.venv/bin/python");
		if (venvL4Unix.exists()) {
			return venvL4Unix.getPath();
		}
		
		return null;
	}
	
	/**
	 * Find createCharts.py script
	 */
	private static String findChartsScript() {
		File script = new File("createCharts.py");
		if (script.exists()) {
			return script.getPath();
		}
		
		File scriptL4 = new File("L4Project/createCharts.py");
		if (scriptL4.exists()) {
			return scriptL4.getPath();
		}
		
		return null;
	}
	
	public static void runPython() throws Exception {
		// Try to find Python executable in virtual environment
		String pythonPath = findPythonExecutable();
		if (pythonPath == null) {
			System.err.println("Warning: Python virtual environment not found. Skipping chart generation.");
			System.err.println("Expected path: .venv\\Scripts\\python.exe (Windows) or .venv/bin/python (Linux/Mac)");
			return;
		}
		
		// Find createCharts.py relative to project root
		String chartsScript = findChartsScript();
		if (chartsScript == null) {
			System.err.println("Warning: createCharts.py not found. Skipping chart generation.");
			return;
		}
		
		ProcessBuilder pb = new ProcessBuilder(pythonPath, chartsScript);
		pb.redirectErrorStream(true);
		Process p = pb.start();
		
		BufferedReader reader = new BufferedReader(
				new InputStreamReader(p.getInputStream())
				);
		
		String line;
		while ((line = reader.readLine()) != null) {
			System.out.println("PYTHON: " + line); // receives pandas output
		}
		p.waitFor();
	}
	
	public SolvePOMDP() {
		// read parameters from config file
		readConfigFile();
		
		// check if required directories exist
		configureDirectories();
	}
	
	/**
	 * Helper method to get property with error handling
	 */
	private String getPropertyOrThrow(Properties properties, String key) {
		String value = properties.getProperty(key);
		if (value == null || value.trim().isEmpty()) {
			throw new RuntimeException("Missing or empty property '" + key + "' in solver.config");
		}
		return value.trim();
	}
	
	/**
	 * Find the solver.config file path, handling both IDE and command-line execution
	 */
	private String findConfigFile() {
		// Try relative path first (works when running from project root)
		File configFile = new File("src/solver.config");
		if (configFile.exists()) {
			return configFile.getPath();
		}
		
		// Try L4Project/src/solver.config (when running from workspace root)
		configFile = new File("L4Project/src/solver.config");
		if (configFile.exists()) {
			return configFile.getPath();
		}
		
		// Try using class location (works when running from JAR or compiled classes)
		try {
			String path = SolvePOMDP.class.getProtectionDomain().getCodeSource().getLocation().getPath();
			String decodedPath = URLDecoder.decode(path, "UTF-8");
			
			if (decodedPath.endsWith(".jar")) {
				// Running from JAR - config should be in same directory or src/
				int endIndex = decodedPath.lastIndexOf("/");
				String jarDir = decodedPath.substring(0, endIndex);
				configFile = new File(jarDir + "/src/solver.config");
				if (configFile.exists()) {
					return configFile.getPath();
				}
			} else {
				// Running from compiled classes - look for src/ relative to class location
				File classDir = new File(decodedPath);
				// Navigate up from bin/ to project root, then to src/
				File projectRoot = classDir.getParentFile().getParentFile();
				configFile = new File(projectRoot, "src/solver.config");
				if (configFile.exists()) {
					return configFile.getPath();
				}
			}
		} catch (Exception e) {
			// Fall through to default
		}
		
		// Default fallback
		return "src/solver.config";
	}
	
	/**
	 * Read the solver.config file. It creates a properties object and it initialises
	 */
	private void readConfigFile() {
		this.sp = new SolverProperties();
		
		Properties properties = new Properties();
		
		// Find config file relative to the class location (works from both IDE and command line)
		String configPath = findConfigFile();
		
		try {
			FileInputStream file = new FileInputStream(configPath);
			properties.load(file);
			file.close();
		} catch (FileNotFoundException e) {
			System.err.println("Error: Could not find solver.config at: " + configPath);
			System.err.println("Current working directory: " + System.getProperty("user.dir"));
			e.printStackTrace();
			throw new RuntimeException("solver.config file not found. Please ensure it exists in the src/ directory.", e);
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException("Error reading solver.config file", e);
		}
		
		// Validate that properties were loaded
		if (properties.isEmpty()) {
			throw new RuntimeException("solver.config file is empty or could not be read");
		}
		
		// Exact Algorithm Settings
		sp.setEpsilon(Double.parseDouble(getPropertyOrThrow(properties, "epsilon")));

		// Directories
		sp.setOutputDirName(getPropertyOrThrow(properties, "outputDirectory"));
		this.domainDirName = getPropertyOrThrow(properties, "domainDirectory");
		
		// Approximate Algorithm Settings
		sp.setBeliefSamplingRuns(Integer.parseInt(getPropertyOrThrow(properties, "beliefSamplingRuns")));
		sp.setBeliefSamplingSteps(Integer.parseInt(getPropertyOrThrow(properties, "beliefSamplingSteps")));
		
		// General Settings
		String algorithmType = getPropertyOrThrow(properties, "algorithmType");
		sp.setTimeLimit(Double.parseDouble(getPropertyOrThrow(properties, "timeLimit")));
		sp.setValueFunctionTolerance(Double.parseDouble(getPropertyOrThrow(properties, "valueFunctionTolerance")));

		// Error checking solver.config parameters
		if(!algorithmType.equals("perseus") && !algorithmType.equals("gip")) {
			throw new RuntimeException("Unexpected algorithm type in properties file");
		}
		
		String dumpPolicyGraphStr = getPropertyOrThrow(properties, "dumpPolicyGraph");
		if(!dumpPolicyGraphStr.equals("true") && !dumpPolicyGraphStr.equals("false")) {
			throw new RuntimeException("Policy graph property must be either true or false");
		}
		else {
			sp.setDumpPolicyGraph(dumpPolicyGraphStr.equals("true") && algorithmType.equals("gip"));
		}
		
		String dumpActionLabelsStr = getPropertyOrThrow(properties, "dumpActionLabels");
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
		System.out.println("Time limit: "+sp.getTimeLimit());
		System.out.println("Belief sampling runs: "+sp.getBeliefSamplingRuns());
		System.out.println("Belief sampling steps: "+sp.getBeliefSamplingSteps());
		System.out.println("Dump policy graph: "+sp.dumpPolicyGraph());
		System.out.println("Dump action labels: "+sp.dumpActionLabels());
		
		// load required POMDP algorithm
		switch (algorithmType) {
			case "gip":
				throw new RuntimeException("GIP is not supported");
			case "perseus":
				this.solver = new SolverApproximate(sp, new Random(222));
				break;
			default:
				throw new RuntimeException("Unexpected algorithm type in properties file");
		}
		
		System.out.println("Algorithm: "+algorithmType);
	}
	
	/**
	 * Find the domain directory by searching from current directory up to project root
	 */
	private File findDomainDirectory(File startDir, String domainDirName) {
		File current = startDir;
		int maxDepth = 5; // Prevent infinite loops
		int depth = 0;
		
		while (current != null && depth < maxDepth) {
			File domainDir = new File(current, domainDirName);
			if (domainDir.exists() && domainDir.isDirectory()) {
				return domainDir;
			}
			// Also check for L4Project/domains pattern
			File l4ProjectDir = new File(current, "L4Project");
			if (l4ProjectDir.exists() && l4ProjectDir.isDirectory()) {
				File domainDirInL4 = new File(l4ProjectDir, domainDirName);
				if (domainDirInL4.exists() && domainDirInL4.isDirectory()) {
					return domainDirInL4;
				}
			}
			current = current.getParentFile();
			depth++;
		}
		return null;
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
			// solver has not been started from jar
			// Try to find the project root by looking for common project directories
			File currentDir = new File(System.getProperty("user.dir"));
			File domainDirFile = findDomainDirectory(currentDir, domainDirName);
			
			if (domainDirFile != null && domainDirFile.exists()) {
				domainDir = domainDirFile.getAbsolutePath();
				sp.setWorkingDir(domainDirFile.getParent());
			} else {
				// Fallback: assume current directory
				sp.setWorkingDir("");
				domainDir = domainDirName;
			}
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
	 * Waits for QoS data to be ready for a specific run by validating that entries exist and have valid data.
	 * This ensures the simulator has complete data before we access it, preventing "Unknown value data" warnings.
	 * The method polls getNetworkQoS() and validates that the expected entry index has valid data.
	 * 
	 * @param runNumber The run number to wait for
	 * @param maxRetries Maximum number of retry attempts (default: 20)
	 * @param retryDelayMs Delay between retries in milliseconds (default: 50ms)
	 * @return The QoS data when ready, or the last result if timeout
	 */
	public static ArrayList<QoS> waitForQoSDataReady(int runNumber, int maxRetries, long retryDelayMs) {
		for (int attempt = 0; attempt < maxRetries; attempt++) {
			try {
				// First, check if the run exists by getting the full QoS list
				// getNetworkQoS(runNumber) internally accesses qosValues.get(runNumber - 1)
				// We need to ensure the list has at least runNumber entries before accessing
				ArrayList<QoS> result = null;
				boolean hasWarnings = false;
				
				// Capture both stdout and stderr to detect "Unknown value data" warnings
				PrintStream originalOut = System.out;
				PrintStream originalErr = System.err;
				ByteArrayOutputStream outCapture = new ByteArrayOutputStream();
				ByteArrayOutputStream errCapture = new ByteArrayOutputStream();
				PrintStream outStream = new PrintStream(outCapture, true);
				PrintStream errStream = new PrintStream(errCapture, true);
				
				try {
					System.setOut(outStream);
					System.setErr(errStream);
					// Get the full QoS list to check its size
					// getNetworkQoS() with a run number internally accesses the list at index (runNumber - 1)
					// We need to ensure the list has at least runNumber entries
					result = (ArrayList<QoS>) DeltaIOTConnector.networkMgmt.getNetworkQoS(runNumber);
				} finally {
					System.setOut(originalOut);
					System.setErr(originalErr);
					String outOutput = outCapture.toString();
					String errOutput = errCapture.toString();
					hasWarnings = outOutput.contains("Unknown value data") || errOutput.contains("Unknown value data");
					
					// If warnings were detected, also check if values match default error values
					// The warnings say "returning default (1.0)", so if we see 1.0 values, data might not be ready
					if (!hasWarnings && result != null && !result.isEmpty()) {
						// Check for default error values (1.0) that indicate missing data
						for (int i = 0; i < result.size(); i++) {
							try {
								QoS qosEntry = result.get(i);
								if (qosEntry != null) {
									double packetLoss = qosEntry.getPacketLoss();
									double energyConsumption = qosEntry.getEnergyConsumption();
									// If both values are exactly 1.0, this might be a default error value
									if (packetLoss == 1.0 && energyConsumption == 1.0) {
										hasWarnings = true; // Treat as warning indicator
										break;
									}
								}
							} catch (Exception e) {
								// Ignore
							}
						}
					}
				}
				
				if (result != null && !result.isEmpty()) {
					// Check if list size is sufficient - the list must have at least runNumber entries
					// because getNetworkQoS(runNumber) accesses result.get(runNumber - 1)
					if (result.size() >= runNumber) {
						// If no warnings were detected AND all entries are valid, data is ready
						if (!hasWarnings) {
							// Validate ALL entries to ensure they have valid data
							boolean allEntriesValid = true;
							
							for (int i = 0; i < result.size(); i++) {
								try {
									QoS qosEntry = result.get(i);
									if (qosEntry != null) {
										double packetLoss = qosEntry.getPacketLoss();
										double energyConsumption = qosEntry.getEnergyConsumption();
										
										boolean isValid = !Double.isNaN(packetLoss) && !Double.isNaN(energyConsumption) &&
										                  packetLoss >= 0.0 && packetLoss <= 1.0 &&
										                  energyConsumption >= 0.0 && !Double.isInfinite(energyConsumption);
										
										if (!isValid) {
											allEntriesValid = false;
											break;
										}
									} else {
										allEntriesValid = false;
										break;
									}
								} catch (Exception e) {
									allEntriesValid = false;
									break;
								}
							}
							
							if (allEntriesValid) {
								// No warnings and all entries valid - data is ready
								return result;
							}
						}
					}
				}
				// Data not ready yet, wait and retry
				Thread.sleep(retryDelayMs);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			} catch (Exception e) {
				// On error, wait and retry
				try {
					Thread.sleep(retryDelayMs);
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					break;
				}
			}
		}
		// Timeout - return whatever we have (last attempt)
		return (ArrayList<QoS>) DeltaIOTConnector.networkMgmt.getNetworkQoS(runNumber);
	}
	
	
	/**
	 * Method to run experiments for DeltaIoT case using POMDP
	 * @param pomdpFileName
	 */
	public void runCaseIoT(String pomdpFileName) {
		///Results Log
		// Declare resources outside try block so they can be closed in finally
		FileWriter fwMECSatProb = null;
		PrintWriter pwMECSatProb = null;
		FileWriter fwRPLSatProb = null;
		PrintWriter pwRPLSatProb = null;
		FileWriter fwMECSat = null;
		PrintWriter pwMECSat = null;
		FileWriter fwRPLSat = null;
		PrintWriter pwRPLSat = null;
		FileWriter fwaction = null;
		PrintWriter pwaction = null;
		FileWriter fwMECSattimestep = null;
		PrintWriter pwMECSattimestep = null;
		FileWriter fwRPLSattimestep = null;
		PrintWriter pwRPLSattimestep = null;
		
		try
		{
		fwMECSatProb = new FileWriter("output_dir/MECSatProb.txt"); // Logs the probability that MEC is satisfied 
		pwMECSatProb = new PrintWriter(fwMECSatProb);
		fwRPLSatProb = new FileWriter("output_dir/RPLSatProb.txt"); // Logs the probability that RPL is satisfied
		pwRPLSatProb = new PrintWriter(fwRPLSatProb);
		
		fwMECSat = new FileWriter("output_dir/MECSat.txt"); // Logs the MECSat value
		pwMECSat = new PrintWriter(fwMECSat);
		fwRPLSat = new FileWriter("output_dir/RPLSat.txt"); // Logs the RPLSat value
		pwRPLSat = new PrintWriter(fwRPLSat);
		fwaction = new FileWriter("output_dir/SelectedAction.txt"); // Logs which action is taken increase or decrease power)
		pwaction = new PrintWriter(fwaction);
		
		fwMECSattimestep = new FileWriter("output_dir/MECSattimestep.txt"); // At specific timesteps
		pwMECSattimestep = new PrintWriter(fwMECSattimestep);
		fwRPLSattimestep = new FileWriter("output_dir/RPLSattimestep.txt");
		pwRPLSattimestep = new PrintWriter(fwRPLSattimestep);
		
		JsonArray rlist = new JsonArray();
		
		
		// read POMDP file
		File pomdpFile = new File(domainDir, pomdpFileName);
		if (!pomdpFile.exists()) {
			throw new RuntimeException("POMDP file not found: " + pomdpFile.getAbsolutePath() + 
				"\nDomain directory: " + domainDir + 
				"\nCurrent working directory: " + System.getProperty("user.dir"));
		}
		POMDP pomdp = PomdpParser.readPOMDP(pomdpFile.getAbsolutePath());
		
		int numTimesteps = 500;
		// set alpha-vectors here (in future can have in POMDP file)
		iot.DeltaIOTConnector.p=pomdp;
		
		// list to record entropys
		double[] entropies = new double[numTimesteps];
		double[] mutualInformations = new double[numTimesteps];
			
		
		////////IoT Code///////////
		
		//iot.DeltaIOTConnector.timestepiot=timestep;
		//System.out.println("timestep: "+timestep);
		iot.DeltaIOTConnector.networkMgmt = new SimulationClient();
		
		iot.DeltaIOTConnector deltaConnector = new iot.DeltaIOTConnector();
		deltaConnector.clearFile("output_dir/gamma.txt");
		deltaConnector.clearFile("output_dir/surpriseBF.txt");
		deltaConnector.clearFile("output_dir/surpriseCC.txt");
		// Initialize timestepiot to 0. This tracks the run number for QoS retrieval.
		// The simulator uses 1-indexed run numbers, so run number = timestepiot + 1
		// After each doSingleRun(), timestepiot is incremented to match the created run
		iot.DeltaIOTConnector.timestepiot = 0;
		
		for (int timestep = 0; timestep < numTimesteps; timestep++) {
			/*
			 * MONITOR
			 */
			JsonObject obj =new JsonObject();
			obj.put("timestep", timestep+"");
		iot.DeltaIOTConnector.motes = iot.DeltaIOTConnector.networkMgmt.getProbe().getAllMotes();
		System.out.println("motes recieved");

		// For timestep 0, no runs exist yet, so use default state 0
		// For timestep > 0, getInitialState() can safely access run 1 which exists from previous timesteps
		int currState;
		if (timestep == 0) {
			// No runs exist yet at timestep 0, use default state
			currState = 0;
		} else {
			// For timestep > 0, run 1 exists from previous timesteps, so we can safely call getInitialState()
			currState = pomdp.getInitialState();
		}
		System.out.println("Initial state: "+currState);
		pomdp.setCurrentState(currState);
			
			System.out.println("current state: "+ pomdp.getCurrentState());		
			
			// Creating random order of motes to perform adaptation 
			int numMotes = iot.DeltaIOTConnector.motes.size();
			int[] moteIndexes = new int[numMotes];
			for (int i = 0; i < numMotes; i++) {
				moteIndexes[i] = i;
			}
			// Fisher–Yates shuffle
			Random random = new Random();
			for (int i = numMotes - 1; i > 0; i--) {
			    int j = random.nextInt(i + 1);
			    int tmp = moteIndexes[i];
			    moteIndexes[i] = moteIndexes[j];
			    moteIndexes[j] = tmp;
			}
			// End of randomised motes
			
			for(int moteIndex : moteIndexes) {
				Mote m = iot.DeltaIOTConnector.motes.get(moteIndex);
				System.out.println("\nTime Step: "+timestep);
				// Simulator object holds the list of motes, gateways, turnOrder, runInfo and qos values.
				// THis will simulate sending packets through the network to the gateways
				// Each gateway will aggregate information about packet-loss and power-consumption
				// The QoS values will be stored in the Simulator object
				
				/*
				 * ANALYSE
				 */
				// Run simulation to get baseline QoS before planning
				// This creates a new run. After this call, increment timestepiot to track the run number
				// Capture stdout/stderr during first doSingleRun to suppress warnings
				PrintStream originalOut1 = System.out;
				PrintStream originalErr1 = System.err;
				ByteArrayOutputStream outCapture1 = new ByteArrayOutputStream();
				ByteArrayOutputStream errCapture1 = new ByteArrayOutputStream();
				PrintStream outStream1 = new PrintStream(outCapture1, true);
				PrintStream errStream1 = new PrintStream(errCapture1, true);
				try {
					System.setOut(outStream1);
					System.setErr(errStream1);
					iot.DeltaIOTConnector.networkMgmt.getSimulator().doSingleRun();
				} finally {
					System.setOut(originalOut1);
					System.setErr(originalErr1);
				}
				iot.DeltaIOTConnector.timestepiot++; // Track the run that was just created
				
				iot.DeltaIOTConnector.selectedmote = m;
				System.out.println("Mote Id"+iot.DeltaIOTConnector.selectedmote.getMoteid());
				
				obj.put("Mote Id", iot.DeltaIOTConnector.selectedmote.getMoteid()+"");		
			
				BeliefPoint initialbelief = pomdp.getInitialBelief(); // b0
				double b[] = initialbelief.getBelief();
				System.out.println(b[0]+" "+b[1]+" "+b[2]+" "+b[3]);
				double mecsatprob = b[0]+b[1]; // Sum of all states in which MEC is satisfied
				double rplsatprob = b[0]+b[2];
				pwMECSatProb.println(moteIndex+" "+timestep+" "+mecsatprob);
				pwRPLSatProb.println(moteIndex+" "+timestep+" "+rplsatprob);
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
				System.out.println("Selected Action: " + selectedAction);
				
				// Put knowledge update here?
				// Really, want this function in the deltaiotconnector, as we want it triggered before belief is updated. 
				// Otherwise, we are comparing the posterior belief rather than the prior
				
				
				pwaction.println(timestep+" "+selectedAction);
				pwaction.flush();
				
				/*
				 * EXECUTE
				 */
				obj.put("Selected Action: ", selectedAction+"");
				pomdp.setInitialBelief(initialbelief); // update initial belief for the next step
				iot.DeltaIOTConnector.p = pomdp;
				// Capture stdout/stderr during performAction to suppress warnings
				PrintStream originalOut = System.out;
				PrintStream originalErr = System.err;
				ByteArrayOutputStream outCapture = new ByteArrayOutputStream();
				ByteArrayOutputStream errCapture = new ByteArrayOutputStream();
				PrintStream outStream = new PrintStream(outCapture, true);
				PrintStream errStream = new PrintStream(errCapture, true);
				try {
					System.setOut(outStream);
					System.setErr(errStream);
					deltaConnector.performAction(selectedAction);
				} finally {
					System.setOut(originalOut);
					System.setErr(originalErr);
				}
				pomdp = iot.DeltaIOTConnector.p; // as POMDP is being updated in performAction, must adjust the variable `pomdp` here
			 
				System.out.println("Current State: " + pomdp.getCurrentState());
				// It is best to increment timestepiot *after* doSingleRun(), because doSingleRun() actually creates the new run in the simulator,
				// and only after that does the run count (timestepiot) reflect the latest run that contains the effect of the action.
				// Capture stdout/stderr during doSingleRun to detect and suppress warnings
				// Keep streams redirected during doSingleRun() and the waiting period to prevent warnings from appearing in console
				PrintStream originalOut2 = System.out;
				PrintStream originalErr2 = System.err;
				ByteArrayOutputStream outCapture2 = new ByteArrayOutputStream();
				ByteArrayOutputStream errCapture2 = new ByteArrayOutputStream();
				PrintStream outStream2 = new PrintStream(outCapture2, true);
				PrintStream errStream2 = new PrintStream(errCapture2, true);
				boolean hasWarnings2 = false;
				try {
					// Redirect streams BEFORE doSingleRun() to suppress warnings from appearing in console
					System.setOut(outStream2);
					System.setErr(errStream2);
					iot.DeltaIOTConnector.networkMgmt.getSimulator().doSingleRun();
					// Now the simulator has completed the next run. Increment timestepiot so it matches the latest run index.
					iot.DeltaIOTConnector.timestepiot++;
					
					// Check if warnings were printed during doSingleRun()
					String outOutput2 = outCapture2.toString();
					String errOutput2 = errCapture2.toString();
					hasWarnings2 = outOutput2.contains("Unknown value data") || errOutput2.contains("Unknown value data");
					
					// If warnings were detected during doSingleRun(), wait until they stop appearing
					// Keep streams redirected during this period to suppress warnings from appearing in console
					if (hasWarnings2) {
						int retryCount = 0;
						int maxRetries = 20;
						long retryDelayMs = 50;
						while (retryCount < maxRetries) {
							try {
								Thread.sleep(retryDelayMs);
								// Clear previous capture and check if warnings still appear
								outCapture2.reset();
								errCapture2.reset();
								// Access the run that was just created to check if warnings still appear
								int currentRun = iot.DeltaIOTConnector.timestepiot;
								DeltaIOTConnector.networkMgmt.getNetworkQoS(currentRun);
								
								// Check if warnings still appear
								String outOutput3 = outCapture2.toString();
								String errOutput3 = errCapture2.toString();
								boolean stillHasWarnings = outOutput3.contains("Unknown value data") || errOutput3.contains("Unknown value data");
								
								if (!stillHasWarnings) {
									// Warnings stopped - data is ready
									break;
								}
								retryCount++;
							} catch (InterruptedException e) {
								Thread.currentThread().interrupt();
								break;
							}
						}
					}
				} finally {
					// Restore original streams only after doSingleRun() and waiting period are complete
					System.setOut(originalOut2);
					System.setErr(originalErr2);
				}
				// The currentRun variable now points exactly to the run we just simulated.
				// Note: getNetworkQoS() expects 1-indexed run numbers (1, 2, 3, ...)
				int currentRun = iot.DeltaIOTConnector.timestepiot;
				
				// Validate run number is reasonable (should not exceed expected number of runs)
				// Expected: 2 runs per mote per timestep (before and after action)
				int expectedMaxRuns = (timestep + 1) * numMotes * 2;
				if (currentRun > expectedMaxRuns) {
					System.err.println("Warning: Run number " + currentRun + " exceeds expected maximum " + expectedMaxRuns);
					System.err.println("Timestep: " + timestep + ", Mote: " + moteIndex + ", timestepiot: " + iot.DeltaIOTConnector.timestepiot);
				}
				
				// Wait for QoS data to be ready before accessing it to prevent warnings
				ArrayList<QoS> result = waitForQoSDataReady(currentRun, 20, 100);
				if (result == null || result.isEmpty()) {
					System.err.println("Warning: No QoS data available for run " + currentRun + ". Using defaults.");
					System.err.println("Timestep: " + timestep + ", Mote: " + moteIndex + ", timestepiot: " + iot.DeltaIOTConnector.timestepiot);
					result = new ArrayList<QoS>();
					System.err.println("Continuing with default QoS values for this mote iteration");
				}
				if (result != null && !result.isEmpty()) {
					System.out.println("QOS list size: "+result.size());
				}
			 	/*
			 	 * MONITOR
			 	 */
			 	// Validate that we have QoS data before accessing it
			 	double packetLoss = 0.0;
			 	double energyConsumption = 0.0;
			 	if (result != null && !result.isEmpty()) {
			 		packetLoss = result.get(result.size()-1).getPacketLoss();
			 		energyConsumption = result.get(result.size()-1).getEnergyConsumption();
			 	} else {
			 		System.err.println("Warning: Using default QoS values (packetLoss=0.0, energyConsumption=0.0)");
			 	}
			 	// Get calculating entropy of current mote's transition belief given previous action and state movement
			 	double entropy = deltaConnector.getMoteEntropy();
			 	double mutualInformation = deltaConnector.getMoteMI();
			 	entropies[timestep] += entropy;
			 	mutualInformations[timestep] += mutualInformation;
			 	System.out.println("packet loss: "+packetLoss+"   Energy Consumption: "+energyConsumption+"   Entropy: "+entropy+"   Mutual Information: "+mutualInformation);
			 	
			 	pwMECSat.println(moteIndex+" "+timestep+" "+energyConsumption);
			 	pwRPLSat.println(moteIndex+" "+timestep+" "+packetLoss);
			 	pwMECSat.flush();
			 	pwRPLSat.flush();
			 	
			 	obj.put("packet loss", packetLoss+"");
			 	obj.put("Energy Consumption",energyConsumption+"");
			 	// Note: timestepiot was already incremented above after doSingleRun()
			 	rlist.add(obj);
			 	
			}///End of Motes loop
			
			String plstimestep = "";
			String ecstimestep = "";
			
			// QoS (Quality of Service) contains 
			// (1) the time when the last period finished 
			// (2) the packet loss of the network
			// (3) Energy consumption of the network
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
			
			// Writing entropy values to file
			try (BufferedWriter writer = new BufferedWriter(new FileWriter("output_dir/entropy.txt"))) {
				for (int i = 0; i < entropies.length; i++) {
					writer.write(Integer.toString(i)+" "+Double.toString(entropies[i]));
					writer.newLine();
				}
			}
			try (BufferedWriter writer = new BufferedWriter(new FileWriter("output_dir/mutualInformation.txt"))) {
				for (int i = 0; i < mutualInformations.length; i++) {
					writer.write(Integer.toString(i)+" "+Double.toString(mutualInformations[i]));
					writer.newLine();
				}
			}
			iot.DeltaIOTConnector.timestep++;
		}
		
		// Mutual information surprise (MIS)
		double[] mis = new double[mutualInformations.length];
		mis[0] = 0; 
		mis[1] = 0;
		int lookback = 2; // m
		double significanceLevel = 0.99;
		
		double miBound;
		double[] misBound = new double[2];
		
		// Calculate MIS
		for (int i = lookback; i < mutualInformations.length; i++) {
			// MI is calculated as a sum over all motes
			// to scale down (for compatability with bound), take mean MI across the system
			mis[i] = (mutualInformations[i] - mutualInformations[i - lookback]) / iot.DeltaIOTConnector.motes.size();
			
			// Calculate MIS bound (with probability at least 1- significanceLevel) at each timestep
			miBound = (Math.sqrt(2 * lookback * Math.log(2.0 / significanceLevel)) * Math.log(lookback + i)) / (lookback + i);
			misBound[0] = (Math.log(lookback + i) - Math.log(i)) - miBound;
			misBound[1] = (Math.log(lookback + i) - Math.log(i)) + miBound;
		}
		
		try (BufferedWriter writer = new BufferedWriter(new FileWriter("output_dir/meanMIS.txt"))) {
			for (int i = lookback; i < mutualInformations.length; i++) {
				writer.write(Integer.toString(i)+" "+Double.toString(mis[i]));
				writer.newLine();
			}
		}	

		
		try (BufferedWriter writer = new BufferedWriter(new FileWriter("output_dir/misBounds.txt"))) {
			for (int i = lookback; i < mutualInformations.length; i++) {
				writer.write(Integer.toString(i)+" "+Double.toString(misBound[0])+" "+Double.toString(misBound[1]));
				writer.newLine();
			}
		}
		
		// Resources will be closed in finally block
		
		// print results
		// Use File constructor to properly join paths and handle absolute paths
		File outputDir = new File(sp.getOutputDir());
		String outputFilePG = new File(outputDir, pomdp.getInstanceName() + ".pg").getAbsolutePath();
		String outputFileAlpha = new File(outputDir, pomdp.getInstanceName() + ".alpha").getAbsolutePath();
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
		finally
		{
			// Ensure all resources are closed even if an exception occurs
			closeResource(pwMECSatProb);
			closeResource(pwRPLSatProb);
			closeResource(pwMECSat);
			closeResource(pwRPLSat);
			closeResource(pwaction);
			closeResource(pwMECSattimestep);
			closeResource(pwRPLSattimestep);
			closeResource(fwMECSatProb);
			closeResource(fwRPLSatProb);
			closeResource(fwMECSat);
			closeResource(fwRPLSat);
			closeResource(fwaction);
			closeResource(fwMECSattimestep);
			closeResource(fwRPLSattimestep);
		}
	}
	
	/**
	 * Helper method to safely close resources
	 */
	private void closeResource(java.io.Closeable resource) {
		if (resource != null) {
			try {
				resource.close();
			} catch (IOException e) {
				// Log but don't throw - we're in cleanup
				System.err.println("Warning: Error closing resource: " + e.getMessage());
			}
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

		try {
			runPython();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
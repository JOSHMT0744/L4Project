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
import java.util.List;
import java.util.Properties;
import java.util.Random;

import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonObject;

import deltaiot.client.SimulationClient;
import deltaiot.services.Mote;
import iot.DeltaIOTConnector;
import iot.NoiseInjector;
import iot.QoSDataHelper;
import pomdp.POMDP;
import pomdp.PomdpParser;
import pomdp.SolverProperties;
import simulator.QoS;
import solver.AlphaVector;
import solver.BeliefPoint;
import solver.ERPBVI;	
import solver.Solver;
import solver.Perseus;
import solver.ERPerseus;
import solver.fastERPBVI;
import solver.ERPolicy;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SolvePOMDP {
	private static final Logger log = LogManager.getLogger(SolvePOMDP.class);
	/* Class for configuring and running each component of the  */
	private SolverProperties sp;     // object containing user-defined properties
	private Solver solver;           // the solver that we use to solve a POMDP, which is exact or approximate
	private ERPolicy erPolicy;       // the entropy-regularized policy that we use to select actions
	private String domainDirName;    // name of the directory containing .POMDP files
	private String domainDir;        // full path of the domain directory
	
	/** Optional experiment parameters (from solver.config): run seed, surprise measure, p_c, useSurpriseUpdating, lookback. Used for reproducible runs and paper experiments. */
	private int runSeed = 222;
	private String surpriseMeasureForGamma = "MIS";
	private double p_c = 0.5;
	private boolean useSurpriseUpdating = true;
	private int lookback = 5;

	/** Optional link failure injection (from solver.config): timestep and link list to turn off; optional recovery timestep to turn them back on. -1 means disabled. */
	private int linkFailureTimestep = -1;
	private List<int[]> linkFailureLinksList = null;
	private int linkRecoveryTimestep = -1;
	
	/** Optional NFR thresholds (from solver.config): MEC = energy threshold (default 20), RPL = packet loss ratio (default 0.2). */
	private double mecThreshold = 20.0;
	private double rplThreshold = 0.20;
	
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
	
	/** Returns the configured output directory (from solver.config outputDirectory). Used when invoking createCharts.py. */
	public String getOutputDir() {
		return sp.getOutputDir();
	}

	/** Returns mecThreshold from solver.config (for createCharts.py MEC satisfaction plot). */
	public double getMecThreshold() {
		return mecThreshold;
	}

	/** Returns rplThreshold from solver.config (for createCharts.py RPL satisfaction plot). */
	public double getRplThreshold() {
		return rplThreshold;
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
	
	/**
	 * Run createCharts.py to generate graphs from the solver output.
	 * @param outputDirForCharts Directory where the solver wrote output (MECSattimestep.txt, gamma.txt, etc.);
	 *                           should match solver.config outputDirectory. Passed to createCharts.py as --output-dir.
	 * @param mecThreshold MEC threshold from solver.config (--mec-threshold).
	 * @param rplThreshold RPL threshold from solver.config (--rpl-threshold).
	 */
	public static void runPython(String outputDirForCharts, double mecThreshold, double rplThreshold) throws Exception {
		// Try to find Python executable in virtual environment
		String pythonPath = findPythonExecutable();
		if (pythonPath == null) {
			log.warn("Python virtual environment not found. Skipping chart generation. Expected: .venv\\Scripts\\python.exe or .venv/bin/python");
			return;
		}
		
		// Find createCharts.py relative to project root
		String chartsScript = findChartsScript();
		if (chartsScript == null) {
			log.warn("createCharts.py not found. Skipping chart generation.");
			return;
		}
		
		// Resolve output dir to absolute path so createCharts.py reads from the correct run directory (e.g. init_runs/s222)
		String outputDirAbs = new File(outputDirForCharts).getAbsolutePath();
		log.info("Running createCharts.py with --output-dir {} --mec-threshold {} --rpl-threshold {}", outputDirAbs, mecThreshold, rplThreshold);
		ProcessBuilder pb = new ProcessBuilder(
			pythonPath, chartsScript,
			"--output-dir", outputDirAbs,
			"--mec-threshold", String.valueOf(mecThreshold),
			"--rpl-threshold", String.valueOf(rplThreshold)
		);
		pb.redirectErrorStream(true);
		Process p = pb.start();
		
		BufferedReader reader = new BufferedReader(
				new InputStreamReader(p.getInputStream())
				);
		
		String line;
		while ((line = reader.readLine()) != null) {
			log.debug("PYTHON: {}", line);
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
	
	/** Optional property with default; used for experiment parameters (runSeed, surpriseMeasureForGamma, p_c). */
	private String getProperty(Properties properties, String key, String defaultValue) {
		String value = properties.getProperty(key);
		if (value == null || value.trim().isEmpty()) {
			return defaultValue;
		}
		return value.trim();
	}
	
	/**
	 * Find the solver.config file path, handling both IDE and command-line execution.
	 * If -DconfigPath=<path> is set, that path is used (for experiment runners).
	 */
	private String findConfigFile() {
		String configPathOverride = System.getProperty("configPath");
		if (configPathOverride != null && !configPathOverride.isEmpty()) {
			File override = new File(configPathOverride);
			if (override.exists()) {
				return override.getAbsolutePath();
			}
		}
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
			log.error("Could not find solver.config at: {}; cwd={}", configPath, System.getProperty("user.dir"));
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

		// Directories (optional JVM override -DoutputDirectory=... for experiment runners)
		String outputDirFromConfig = getPropertyOrThrow(properties, "outputDirectory");
		String outputDirOverride = System.getProperty("outputDirectory");
		sp.setOutputDirName(outputDirOverride != null && !outputDirOverride.trim().isEmpty() ? outputDirOverride.trim() : outputDirFromConfig);
		this.domainDirName = getPropertyOrThrow(properties, "domainDirectory");
		
		// Approximate Algorithm Settings
		sp.setBeliefSamplingRuns(Integer.parseInt(getPropertyOrThrow(properties, "beliefSamplingRuns")));
		sp.setBeliefSamplingSteps(Integer.parseInt(getPropertyOrThrow(properties, "beliefSamplingSteps")));
		
		// General Settings
		String algorithmType = getPropertyOrThrow(properties, "algorithmType");
		String lambda = getPropertyOrThrow(properties, "lambda");
		sp.setLambda(Double.parseDouble(lambda));
		sp.setTimeLimit(Double.parseDouble(getPropertyOrThrow(properties, "timeLimit")));
		sp.setValueFunctionTolerance(Double.parseDouble(getPropertyOrThrow(properties, "valueFunctionTolerance")));

		// Optional experiment parameters (for reproducible runs and paper experiments)
		this.runSeed = Integer.parseInt(getProperty(properties, "runSeed", "222"));
		this.surpriseMeasureForGamma = getProperty(properties, "surpriseMeasureForGamma", "MIS");
		this.p_c = Double.parseDouble(getProperty(properties, "p_c", "0.5"));
		if (this.surpriseMeasureForGamma != null && !this.surpriseMeasureForGamma.matches("CC|BF|MIS")) {
			throw new RuntimeException("surpriseMeasureForGamma must be CC, BF, or MIS; got '" + this.surpriseMeasureForGamma + "'");
		}
		if (this.p_c <= 0 || this.p_c >= 1) {
			throw new RuntimeException("p_c must be in (0, 1); got " + this.p_c);
		}
		String useSurpriseUpdatingStr = getProperty(properties, "useSurpriseUpdating", "true");
		if (!useSurpriseUpdatingStr.equals("true") && !useSurpriseUpdatingStr.equals("false")) {
			throw new RuntimeException("useSurpriseUpdating must be true or false; got '" + useSurpriseUpdatingStr + "'");
		}
		this.useSurpriseUpdating = useSurpriseUpdatingStr.equals("true");
		this.lookback = Integer.parseInt(getProperty(properties, "lookback", "5"));
		if (this.lookback <= 0) {
			throw new RuntimeException("lookback must be > 0; got " + this.lookback);
		}

		// Optional link failure injection
		String linkFailureTimestepStr = getProperty(properties, "linkFailureTimestep", "");
		if (linkFailureTimestepStr != null && !linkFailureTimestepStr.trim().isEmpty()) {
			this.linkFailureTimestep = Integer.parseInt(linkFailureTimestepStr.trim());
			String linkFailureLinksStr = getProperty(properties, "linkFailureLinks", "");
			if (linkFailureLinksStr != null && !linkFailureLinksStr.trim().isEmpty()) {
				this.linkFailureLinksList = new ArrayList<>();
				for (String pair : linkFailureLinksStr.split(",")) {
					String[] parts = pair.trim().split("-");
					if (parts.length == 2) {
						this.linkFailureLinksList.add(new int[] {
							Integer.parseInt(parts[0].trim()),
							Integer.parseInt(parts[1].trim())
						});
					}
				}
			}
			String linkRecoveryTimestepStr = getProperty(properties, "linkRecoveryTimestep", "");
			if (linkRecoveryTimestepStr != null && !linkRecoveryTimestepStr.trim().isEmpty()) {
				this.linkRecoveryTimestep = Integer.parseInt(linkRecoveryTimestepStr.trim());
			}
		}
		
		// Optional NFR thresholds for state discretisation
		String mecThresholdStr = getProperty(properties, "mecThreshold", "20");
		String rplThresholdStr = getProperty(properties, "rplThreshold", "0.2");
		this.mecThreshold = Double.parseDouble(mecThresholdStr.trim());
		this.rplThreshold = Double.parseDouble(rplThresholdStr.trim());
		if (this.mecThreshold <= 0) {
			throw new RuntimeException("mecThreshold must be > 0; got " + this.mecThreshold);
		}
		if (this.rplThreshold <= 0 || this.rplThreshold >= 1) {
			throw new RuntimeException("rplThreshold must be in (0, 1); got " + this.rplThreshold);
		}

		// Error checking solver.config parameters
		if(!algorithmType.equals("perseus") && !algorithmType.equals("gip") && !algorithmType.equals("erpbvi") && !algorithmType.equals("erperseus")) {
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
		
		log.info("Solver parameters: epsilon={}, valueFunctionTolerance={}, timeLimit={}, beliefSamplingRuns={}, beliefSamplingSteps={}, lambda={}",
			sp.getEpsilon(), sp.getValueFunctionTolerance(), sp.getTimeLimit(), sp.getBeliefSamplingRuns(), sp.getBeliefSamplingSteps(), sp.getLambda());
		log.info("Experiment: runSeed={}, surpriseMeasure={}, p_c={}, useSurpriseUpdating={}, lookback={}, mecThreshold={}, rplThreshold={}",
			runSeed, surpriseMeasureForGamma, p_c, useSurpriseUpdating, lookback, mecThreshold, rplThreshold);
		
		// load required POMDP algorithm (use runSeed for reproducible experiments)
		switch (algorithmType) {
			case "gip":
				throw new RuntimeException("GIP is not supported");
			case "perseus":
				this.solver = new Perseus(sp, new Random(runSeed));
				break;
			case "erperseus":
				this.solver = new ERPerseus(sp, new Random(runSeed), sp.getLambda());
				break;
			case "fasterpbvi":
				this.solver = new fastERPBVI(sp, new Random(runSeed), sp.getLambda(), false);
				break;
			case "erpbvi":
				// Entropy-Regularized PBVI with default parameters
				this.solver = new ERPBVI(sp, new Random(runSeed), sp.getLambda(), false);
				break;
			default:
				throw new RuntimeException("Unexpected algorithm type in properties file");
		}
		
		log.info("Algorithm: {}", algorithmType);
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
		String decodedPath = "";
		try {
			decodedPath = URLDecoder.decode(path, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			log.error("Failed to decode path", e);
		}
		log.debug("Code source path: {}", decodedPath);
		
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
		if(!dir.exists()) {
		    boolean created = dir.mkdirs();
		    if (!created) {
		        throw new RuntimeException("Output directory could not be created");
		    }
		}
		else if(!dir.isDirectory()) {
		    throw new RuntimeException("Output path exists but is not a directory");
		}
		
		log.info("Output directory: {}; Domain directory: {}", sp.getOutputDir(), domainDir);
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
		FileWriter fwStateTrans = null;
		PrintWriter pwStateTrans = null;

		try
		{
		// Use configured output directory instead of hardcoded path
		String outputDir = sp.getOutputDir();
		
		fwMECSatProb = new FileWriter(new File(outputDir, "MECSatProb.txt").getPath()); // Logs the probability that MEC is satisfied 
		pwMECSatProb = new PrintWriter(fwMECSatProb);
		fwRPLSatProb = new FileWriter(new File(outputDir, "RPLSatProb.txt").getPath()); // Logs the probability that RPL is satisfied
		pwRPLSatProb = new PrintWriter(fwRPLSatProb);
		
		fwMECSat = new FileWriter(new File(outputDir, "MECSat.txt").getPath()); // Logs the MECSat value
		pwMECSat = new PrintWriter(fwMECSat);
		fwRPLSat = new FileWriter(new File(outputDir, "RPLSat.txt").getPath()); // Logs the RPLSat value
		pwRPLSat = new PrintWriter(fwRPLSat);
		fwaction = new FileWriter(new File(outputDir, "SelectedAction.txt").getPath()); // Logs which action is taken increase or decrease power)
		pwaction = new PrintWriter(fwaction);
		
		fwMECSattimestep = new FileWriter(new File(outputDir, "MECSattimestep.txt").getPath()); // At specific timesteps
		pwMECSattimestep = new PrintWriter(fwMECSattimestep);
		fwRPLSattimestep = new FileWriter(new File(outputDir, "RPLSattimestep.txt").getPath());
		pwRPLSattimestep = new PrintWriter(fwRPLSattimestep);
		fwStateTrans = new FileWriter(new File(outputDir, "state_transitions.txt").getPath());
		pwStateTrans = new PrintWriter(fwStateTrans);

		JsonArray rlist = new JsonArray();
		
		
		// read POMDP file
		File pomdpFile = new File(domainDir, pomdpFileName);
		if (!pomdpFile.exists()) {
			throw new RuntimeException("POMDP file not found: " + pomdpFile.getAbsolutePath() + 
				"\nDomain directory: " + domainDir + 
				"\nCurrent working directory: " + System.getProperty("user.dir"));
		}
		POMDP pomdp = PomdpParser.readPOMDP(pomdpFile.getAbsolutePath());
		pomdp.setMecThreshold(mecThreshold);
		pomdp.setRplThreshold(rplThreshold);
		
		int numTimesteps = 500;
		// set alpha-vectors here (in future can have in POMDP file)
		iot.DeltaIOTConnector.p=pomdp;		
		
		////////IoT Code///////////
		
		iot.DeltaIOTConnector.networkMgmt = new SimulationClient();
		
		iot.DeltaIOTConnector deltaConnector = new iot.DeltaIOTConnector();
		
		// set noise injector
		NoiseInjector noiseInjector = new NoiseInjector();
		noiseInjector.setEnabled(true);
		noiseInjector.setLinkFailureEnabled(true);
		noiseInjector.setMoteFailureEnabled(false);
		noiseInjector.setSeed(42);
		deltaConnector.setNoiseInjector(noiseInjector);
		
		// Set the active connector instance so POMDP.nextState() can use it
		// This ensures the connector with properly configured noiseInjector is used
		iot.DeltaIOTConnector.activeInstance = deltaConnector;

		// Set output directory for DeltaIOTConnector to use
		deltaConnector.setOutputDirectory(outputDir);
		deltaConnector.clearFile(new File(outputDir, "gamma.txt").getPath());
		deltaConnector.clearFile(new File(outputDir, "surpriseBF.txt").getPath());
		deltaConnector.clearFile(new File(outputDir, "surpriseCC.txt").getPath());
		deltaConnector.clearFile(new File(outputDir, "surpriseMIS.txt").getPath());
		deltaConnector.clearFile(new File(outputDir, "MISBounds.txt").getPath());
		deltaConnector.clearMoteMetricsFile(new File(outputDir, "mote_metrics.txt").getPath());

		// Initialize timestepiot to 0. This tracks the run number for QoS retrieval.
		// The simulator uses 1-indexed run numbers, so run number = timestepiot + 1
		// After each doSingleRun(), timestepiot is incremented to match the created run
		iot.DeltaIOTConnector.timestepiot = 0;
		iot.DeltaIOTConnector.timestep = 0;
		// Experiment parameters from solver.config (surprise measure, p_c, useSurpriseUpdating, lookback)
		deltaConnector.setSurpriseMeasureForGamma(surpriseMeasureForGamma);
		deltaConnector.setP_c(p_c);
		deltaConnector.setUseSurpriseUpdating(useSurpriseUpdating);
		deltaConnector.setLookback(lookback);
		
		// test turning a link fully of for full duration of simulation
		noiseInjector.setLinkFailureDuration(400);	

		for (int timestep = 0; timestep < numTimesteps; timestep++) {
			// Set the static timestep variable to current loop timestep for use in MIS calculation
			iot.DeltaIOTConnector.timestep = timestep;
			
			// update failure states if noise injector is enabled
			if (deltaConnector.getNoiseInjector() != null) {
				deltaConnector.getNoiseInjector().updateFailures(
					iot.DeltaIOTConnector.motes, timestep);
			}

			// Config-driven link failure: at linkFailureTimestep turn off configured links; at linkRecoveryTimestep turn them back on
			if (noiseInjector != null && linkFailureTimestep >= 0 && linkFailureLinksList != null && !linkFailureLinksList.isEmpty()) {
				if (timestep == linkFailureTimestep) {
					for (int[] link : linkFailureLinksList) {
						noiseInjector.turnLinkOff(link[0], link[1]);
					}
				}
				if (linkRecoveryTimestep >= 0 && timestep == linkRecoveryTimestep) {
					for (int[] link : linkFailureLinksList) {
						noiseInjector.turnLinkOn(link[0], link[1]);
					}
				}
			}
			
			/*
			 * MAPE-K PHASE: MONITOR (timestep-level initialization)
			 * 
			 * Initialize the monitoring phase for this timestep by:
			 * - Retrieving current network state (all motes)
			 * - Getting the initial POMDP state from previous timestep
			 * - Preparing for per-mote adaptation loop
			 * 
			 * Each mote goes through: Analyse -> Plan -> Execute -> Monitor
			 * The baseline for each mote is implicitly the previous mote's post-action state
			 * (or the previous timestep's last post-action state for the first mote).
			 */
			JsonObject obj =new JsonObject();
			obj.put("timestep", timestep+"");

			iot.DeltaIOTConnector.motes = iot.DeltaIOTConnector.networkMgmt.getProbe().getAllMotes();
			log.debug("Timestep {}: motes received, count={}", timestep, iot.DeltaIOTConnector.motes.size());

			// For timestep 0, no runs exist yet, so use default state 0
			// For timestep > 0, getInitialState() uses timestepiot (last run of previous timestep) for current network state
			int currState;
			if (timestep == 0) {
				currState = 0;
			} else {
				currState = pomdp.getInitialState();
			}
			pomdp.setCurrentState(currState);
			log.debug("Timestep {}: initial state={}", timestep, currState);		
			
			// Creating random order of motes to perform adaptation 
			int numMotes = iot.DeltaIOTConnector.motes.size();
			int[] moteIndexes = new int[numMotes];
			for (int i = 0; i < numMotes; i++) {
				moteIndexes[i] = i;
			}
			// Fisher–Yates shuffle
			// Use runSeed + timestep for reproducible but varied order per timestep
			Random random = new Random(runSeed + timestep);
			for (int i = numMotes - 1; i > 0; i--) {
			    int j = random.nextInt(i + 1);
			    int tmp = moteIndexes[i];
			    moteIndexes[i] = moteIndexes[j];
			    moteIndexes[j] = tmp;
			}
			// End of randomised motes
			
			for(int moteIndex : moteIndexes) {
				Mote m = iot.DeltaIOTConnector.motes.get(moteIndex);
				log.trace("Time step {} mote {}", timestep, moteIndex);
				// Simulator object holds the list of motes, gateways, turnOrder, runInfo and qos values.
				// This will simulate sending packets through the network to the gateways
				// Each gateway will aggregate information about packet-loss and power-consumption
				// The QoS values will be stored in the Simulator object
				
				/*
				 * MAPE-K PHASE: MONITOR (implicit via previous mote's post-action state)
				 * 
				 * The baseline network state for this mote is implicitly available from the
				 * previous mote's post-action simulation run. Since we're looping over motes
				 * sequentially, each mote's "baseline" is the previous mote's "post-action" state.
				 * This eliminates the need for a separate baseline measurement per mote.
				 * 
				 * For the first mote in the loop, the baseline comes from:
				 * - Previous timestep's last post-action run (if timestep > 0)
				 * - Or default state 0 (if timestep == 0)
				 */
				
				iot.DeltaIOTConnector.selectedmote = m;
				log.trace("Mote id: {}", iot.DeltaIOTConnector.selectedmote.getMoteid());
				obj.put("Mote Id", iot.DeltaIOTConnector.selectedmote.getMoteid()+"");		
			
				/*
				 * MAPE-K PHASE: ANALYSE
				 * 
				 * Compute belief state and satisfaction probabilities. The belief represents our
				 * uncertainty about which state the system is currently in. The baseline network
				 * state is implicitly available from the previous mote's post-action simulation run
				 * (or from the previous timestep for the first mote).
				 */
				BeliefPoint initialbelief = pomdp.getInitialBelief(); // b0
				double beliefValues[] = initialbelief.getBelief();
				log.trace("Belief: [{}, {}, {}, {}]", beliefValues[0], beliefValues[1], beliefValues[2], beliefValues[3]);
				double mecsatprob = beliefValues[0]+beliefValues[1]; // Sum of all states in which MEC is satisfied
				double rplsatprob = beliefValues[0]+beliefValues[2];
				pwMECSatProb.println(moteIndex+" "+timestep+" "+mecsatprob);
				pwRPLSatProb.println(moteIndex+" "+timestep+" "+rplsatprob);
				pwMECSatProb.flush();
				pwRPLSatProb.flush();				
				
				/*
				 * MAPE-K PHASE: PLAN
				 * 
				 * Solve the POMDP to determine the optimal adaptation action given the current
				 * belief state. Each AlphaVector encodes a linear function over beliefs V(b) = alpha * b.
				 * The solver computes a value function that represents the expected long-term reward.
				 */
				// Each AlphaVector encodes a linear function over beliefs V(b) = alpha * b
				ArrayList<AlphaVector> V1 = solver.solve(pomdp);
				log.debug("Timestep {} mote {}: value size={}, best action={}", timestep, moteIndex, V1.size(), V1.get(0).getAction());

				// Select action using stochastic policy (softmax) based on Q-functions
				int selectedAction;
				if (solver instanceof ERPBVI) {
					// ERPBVI has Q-functions directly available
					erPolicy = new ERPolicy(pomdp, (ERPBVI)solver, new Random(runSeed));
					selectedAction = erPolicy.selectAction(pomdp.getInitialBelief());
				} else if (solver instanceof ERPerseus) {
					// ERPerseus: extract Q-functions from value function
					double lambda = ((ERPerseus) solver).getLambda();
					erPolicy = new ERPolicy(pomdp, V1, lambda, new Random(runSeed));
					selectedAction = erPolicy.selectAction(pomdp.getInitialBelief());
				} else {
					int bestIndex = AlphaVector.getBestVectorIndex(pomdp.getInitialBelief().getBelief(), V1);
					selectedAction = V1.get(bestIndex).getAction();
				}
				log.debug("Selected action: {}", selectedAction);				
				
				pwaction.println(timestep+" "+selectedAction);
				pwaction.flush();
				
				/*
				 * MAPE-K PHASE: EXECUTE
				 * 
				 * Step 1: Perform the selected action (DTP or ITP) which modifies network
				 * configuration (transmission power, spreading factor, link distribution).
				 * This also updates POMDP beliefs and transition probabilities.
				 */
				obj.put("Selected Action: ", selectedAction+"");
				int preState = pomdp.getCurrentState();
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
				log.trace("Current state after action: {}", pomdp.getCurrentState());
				
				/*
				 * MAPE-K PHASE: MONITOR (post-execution measurement)
				 * 
				 * Monitor the network state after the action has been executed by running a simulation.
				 * This doSingleRun() call simulates packet flow through the network to measure the
				 * actual effects of the adaptation action.
				 * 
				 * What this monitoring phase does:
				 * - Runs network simulation (doSingleRun()) to observe packet flow and network behavior
				 * - Measures QoS metrics (packet loss, energy consumption) that reflect the action's impact
				 * - Creates data that serves as the baseline for the next mote in the loop
				 * - Enables sequential adaptation where each mote sees effects of previous motes
				 * 
				 * Why we need this simulation AFTER execution:
				 * - Actions modify network settings (power, distribution, etc.)
				 * - We need to observe/measure whether the adaptation achieved desired outcomes
				 * - This feedback loop enables learning and adaptive behavior
				 * 
				 * Expected: 1 run per mote per timestep (post-action measurement only)
				 * Each mote's baseline is the previous mote's post-action state.
				 */
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
				try {
					// Redirect streams BEFORE doSingleRun() to suppress warnings from appearing in console
					System.setOut(outStream2);
					System.setErr(errStream2);
					iot.DeltaIOTConnector.networkMgmt.getSimulator().doSingleRun();
					// Now the simulator has completed the next run. Increment timestepiot so it matches the latest run index.
					iot.DeltaIOTConnector.timestepiot++;
				} finally {
					// Restore original streams after doSingleRun() completes
					// Note: QoSDataHelper.waitForQoSDataReady() below handles validation and waiting
					System.setOut(originalOut2);
					System.setErr(originalErr2);
				}
				// The currentRun variable now points exactly to the run we just simulated.
				// Note: getNetworkQoS() expects 1-indexed run numbers (1, 2, 3, ...)
				int currentRun = iot.DeltaIOTConnector.timestepiot;
				
				// Validate run number is reasonable (should not exceed expected number of runs)
				// Expected: 1 run per mote per timestep (post-action measurement only)
				// Each mote's baseline is implicitly the previous mote's post-action state
				int expectedMaxRuns = (timestep + 1) * numMotes;
				if (currentRun > expectedMaxRuns) {
					log.warn("Run number {} exceeds expected max {} (timestep={}, mote={})", currentRun, expectedMaxRuns, timestep, moteIndex);
				}
				log.debug("Waiting for QoS data for run {}", currentRun);
				ArrayList<QoS> result = QoSDataHelper.waitForQoSDataReady(currentRun, 50, 300);
				if (result == null || result.isEmpty()) {
					log.warn("No QoS data for run {} (timestep={}, mote={}); using defaults", currentRun, timestep, moteIndex);
					result = new ArrayList<QoS>();
				}
				
				// Extract and log QoS metrics from the post-action simulation run.
				// These metrics represent the network state AFTER the adaptation action.
				// This state will serve as the baseline for the next mote in the loop
				// (or for the next timestep if this is the last mote).
				// 
				// This completes the MAPE-K cycle for this mote:
				// 1. MONITOR (implicit via previous mote) -> 2. ANALYSE -> 3. PLAN -> 4. EXECUTE -> 5. MONITOR (results)
			 	// Validate that we have QoS data before accessing it
			 	double packetLoss = 0.0;
			 	double energyConsumption = 0.0;
			 	if (result != null && !result.isEmpty()) {
			 		packetLoss = result.get(result.size()-1).getPacketLoss();
			 		energyConsumption = result.get(result.size()-1).getEnergyConsumption();
			 	} else {
			 		log.warn("Using default QoS (packetLoss=0, energyConsumption=0)");
			 	}
			 	if (timestep % 50 == 0) {
			 		log.info("Timestep {}: packetLoss={}, energyConsumption={}", timestep, packetLoss, energyConsumption);
			 	}
			 	
			 	pwMECSat.println(moteIndex+" "+timestep+" "+energyConsumption);
			 	pwRPLSat.println(moteIndex+" "+timestep+" "+packetLoss);
			 	pwMECSat.flush();
			 	pwRPLSat.flush();
			 	
			 	obj.put("packet loss", packetLoss+"");
			 	obj.put("Energy Consumption",energyConsumption+"");
			 	// Note: timestepiot was already incremented above after doSingleRun()
			 	rlist.add(obj);

				// MONITOR: log per-mote state transition for post-hoc feedback-loop analysis
				int postState = pomdp.getCurrentState();
				pwStateTrans.println(timestep + " " + moteIndex + " " + preState + " "
					+ selectedAction + " " + postState + " "
					+ beliefValues[0] + " " + beliefValues[1] + " "
					+ beliefValues[2] + " " + beliefValues[3]);
				pwStateTrans.flush();

			}///End of Motes loop
			
			// Log comprehensive metrics for all motes at this timestep
			// This captures the network state AFTER all actions have been performed
			// Actions (performDTP/performITP) already enforce distribution=0 for failed links,
			// so the logged metrics will accurately reflect the enforced state
			iot.DeltaIOTConnector.motes = iot.DeltaIOTConnector.networkMgmt.getProbe().getAllMotes();
			for (Mote mote : iot.DeltaIOTConnector.motes) {
				deltaConnector.logMoteAndLinkMetrics(mote, timestep);
			}
			
			String plstimestep = "";
			String ecstimestep = "";
			
			// QoS (Quality of Service) contains 
			// (1) the time when the last period finished 
			// (2) the packet loss of the network
			// (3) Energy consumption of the network
			ArrayList<QoS> result1 = (ArrayList<QoS>)DeltaIOTConnector.networkMgmt.getSimulator().getQosValues();
			
			// Validate that we have QoS data before accessing it
			if (result1 == null || result1.isEmpty()) {
				log.warn("getQosValues() empty at timestep {}; skipping timestep-level write", timestep);
				continue;
			}
			
			// Total packet loss and energy consumption across every mote in the network
			// Use the last entry in the QoS list, which represents the most recent network-wide QoS
			int lastIndex = result1.size() - 1;
			QoS lastQoS = result1.get(lastIndex);
			
			if (lastQoS == null) {
				log.warn("Last QoS entry null at timestep {} (list size={})", timestep, result1.size());
				continue;
			}
			
			double pl1 = lastQoS.getPacketLoss();
			double ec1 = lastQoS.getEnergyConsumption();
			
			plstimestep = timestep+" ";
			ecstimestep = timestep+" ";
			plstimestep = plstimestep+pl1;
			ecstimestep = ecstimestep+ec1;
			
			if (timestep % 50 == 0) {
				log.debug("Timestep {} aggregate: pl={}, ec={}", timestep, pl1, ec1);
			}
			
			// Write to timestep-level files
			pwMECSattimestep.println(ecstimestep);
			pwRPLSattimestep.println(plstimestep);
			pwMECSattimestep.flush();
			pwRPLSattimestep.flush();
			
			log.debug("Failure stats: {}", noiseInjector.getFailureStats());
		}
		
		String outputFilePG = new File(outputDir, pomdp.getInstanceName() + ".pg").getAbsolutePath();
		String outputFileAlpha = new File(outputDir, pomdp.getInstanceName() + ".alpha").getAbsolutePath();
		log.info("Results: expectedValue={}, alphaVectors={}, runningTimeSec={}", solver.getExpectedValue(), outputFileAlpha, solver.getTotalSolveTime());
		if (sp.dumpPolicyGraph()) log.info("Policy graph: {}", outputFilePG);
		}
		catch(IOException ioex) {
			log.error("IOException in runCaseIoT", ioex);
		}
		catch(Exception ex) {
			log.error("Unexpected exception in runCaseIoT", ex);
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
			closeResource(pwStateTrans);
			closeResource(fwMECSatProb);
			closeResource(fwRPLSatProb);
			closeResource(fwMECSat);
			closeResource(fwRPLSat);
			closeResource(fwaction);
			closeResource(fwMECSattimestep);
			closeResource(fwRPLSattimestep);
			closeResource(fwStateTrans);
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
				log.warn("Error closing resource: {}", e.getMessage());
			}
		}
	}
	
	/**
	 * Main entry point of the SolvePOMDP software
	 * @param args first argument should be a filename of a .POMDP file
	 */
	public static void main(String[] args) {	
		long startTime = System.currentTimeMillis();
		
		log.info("SolvePOMDP v0.0.3 (Erwin Walraven, erwinwalraven.nl/solvepomdp, TU Delft)");
		if (args.length == 0) {
			log.info("First argument should be the name of a file in the domains directory");
		}
		
		SolvePOMDP ps = new SolvePOMDP();
		ps.run("IoT.POMDP");

		long endTime = System.currentTimeMillis();
		long totalTime = endTime - startTime;
		double totalTimeSeconds = totalTime / 1000.0;
		log.info("Total execution time: {} seconds", String.format("%.2f", totalTimeSeconds));

		String noPlotsProp = System.getProperty("noPlots");
		boolean noPlots = noPlotsProp != null && Boolean.parseBoolean(noPlotsProp);
		if (!noPlots) {
			try {
				runPython(ps.getOutputDir(), ps.getMecThreshold(), ps.getRplThreshold());
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}
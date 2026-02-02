/**
 * Module definition for L4Project
 * This module exports all internal packages and requires external dependencies
 */
module L4Project {
	// Export internal packages so they can be used within the module
	exports main;
	exports charts;
	exports iot;
	exports pomdp;
	exports solver;
	exports mapek;
	
	// External module dependencies
	// Note: Automatic module names are derived from JAR filenames
	// If these don't work, check Eclipse's module view or try:
	// - For json-simple-4.0.1.jar: try "json.simple" or "json.simple.4.0.1"
	// - For Simulator.jar: try "Simulator" (exact case matters)
	requires transitive Simulator;  // Contains deltaiot.* and simulator.* packages (transitive: exposes Mote, Link, etc. to clients)
	requires json.simple;  // Contains com.github.cliftonlabs.json_simple.*
	requires gurobi;
	requires joptimizer;
	requires org.apache.logging.log4j;
	requires org.jfree.jfreechart;
	requires java.desktop;
	requires commons.math3;
    requires org.python.jython2.standalone;
}
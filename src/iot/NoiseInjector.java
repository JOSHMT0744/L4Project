package iot;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import deltaiot.services.Link;
import deltaiot.services.Mote;

/**
 * Failure injection system for simulating link and mote failures
 * in the IoT network without modifying the SimulationClient library.
 * 
 * This class provides:
 * - Link failures: Turn links on/off (simulate disconnections)
 * - Mote failures: Turn motes on/off (simulate device malfunctions)
 */
public class NoiseInjector {
	
	// Configuration flags
	private boolean enabled = false;
	private boolean linkFailureEnabled = false;
	private boolean moteFailureEnabled = false;
	
	// Failure parameters
	private double linkFailureProbability = 0.0; // Probability of link failure per timestep
	private double moteFailureProbability = 0.0; // Probability of mote failure per timestep
	
	// Failure tracking
	private List<Integer> failedMotes = new ArrayList<>(); // Mote IDs that are currently failed
	private List<String> failedLinks = new ArrayList<>(); // "source-dest" pairs that are currently failed
	
	// Random number generator
	private Random random;
	private long seed = System.currentTimeMillis(); // Default seed
	
	// Failure duration (in timesteps)
	private int linkFailureDuration = 1; // Links fail for 1 timestep by default
	private int moteFailureDuration = 1; // Motes fail for 1 timestep by default
	
	// Failure timers: track when failures will recover
	private java.util.Map<Integer, Integer> moteFailureTimers = new java.util.HashMap<>();
	private java.util.Map<String, Integer> linkFailureTimers = new java.util.HashMap<>();
	
	public NoiseInjector() {
		this.random = new Random(seed);
	}
	
	public NoiseInjector(long seed) {
		this.seed = seed;
		this.random = new Random(seed);
	}
	
	// ==================== Configuration Methods ====================
	
	/**
	 * Enable or disable failure injection globally
	 */
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}
	
	public boolean isEnabled() {
		return enabled;
	}
	
	/**
	 * Enable/disable link failures
	 */
	public void setLinkFailureEnabled(boolean enabled) {
		this.linkFailureEnabled = enabled;
	}
	
	public boolean isLinkFailureEnabled() {
		return linkFailureEnabled;
	}
	
	/**
	 * Enable/disable mote failures
	 */
	public void setMoteFailureEnabled(boolean enabled) {
		this.moteFailureEnabled = enabled;
	}
	
	public boolean isMoteFailureEnabled() {
		return moteFailureEnabled;
	}
	
	/**
	 * Set link failure probability (0.0 to 1.0)
	 */
	public void setLinkFailureProbability(double probability) {
		this.linkFailureProbability = Math.max(0.0, Math.min(1.0, probability));
	}
	
	public double getLinkFailureProbability() {
		return linkFailureProbability;
	}
	
	/**
	 * Set mote failure probability (0.0 to 1.0)
	 */
	public void setMoteFailureProbability(double probability) {
		this.moteFailureProbability = Math.max(0.0, Math.min(1.0, probability));
	}
	
	public double getMoteFailureProbability() {
		return moteFailureProbability;
	}
	
	/**
	 * Set failure durations (in timesteps)
	 */
	public void setLinkFailureDuration(int timesteps) {
		this.linkFailureDuration = Math.max(1, timesteps);
	}
	
	public int getLinkFailureDuration() {
		return linkFailureDuration;
	}
	
	public void setMoteFailureDuration(int timesteps) {
		this.moteFailureDuration = Math.max(1, timesteps);
	}
	
	public int getMoteFailureDuration() {
		return moteFailureDuration;
	}
	
	/**
	 * Set random seed for reproducibility
	 */
	public void setSeed(long seed) {
		this.seed = seed;
		this.random = new Random(seed);
	}
	
	public long getSeed() {
		return seed;
	}
	
	// ==================== Failure Simulation Methods ====================
	
	/**
	 * Update failure states at the beginning of each timestep
	 * This should be called once per timestep to:
	 * 1. Check for new failures (based on probability)
	 * 2. Update failure timers
	 * 3. Remove recovered failures
	 * 
	 * @param motes List of all motes in the network
	 * @param timestep Current timestep (for logging/debugging)
	 */
	public void updateFailures(List<Mote> motes, int timestep) {
		if (!enabled) {
			return;
		}
		
		// Update mote failure timers
		if (moteFailureEnabled && motes != null) {
			// Check for new mote failures
			for (Mote mote : motes) {
				if (mote == null) continue;
				int moteId = mote.getMoteid();
				
				// Skip if already failed
				if (failedMotes.contains(moteId)) {
					// Decrement timer
					int remainingTime = moteFailureTimers.get(moteId) - 1;
					if (remainingTime <= 0) {
						// Mote recovered (turned back on)
						failedMotes.remove((Integer) moteId);
						moteFailureTimers.remove(moteId);
					} else {
						moteFailureTimers.put(moteId, remainingTime);
					}
				} else {
					// Check for new failure
					if (random.nextDouble() < moteFailureProbability) {
						failedMotes.add(moteId);
						moteFailureTimers.put(moteId, moteFailureDuration);
					}
				}
			}
		}
		
		// Update link failure timers
		if (linkFailureEnabled && motes != null) {
			// Check for new link failures
			for (Mote mote : motes) {
				if (mote == null) continue;
				if (failedMotes.contains(mote.getMoteid())) {
					continue; // Skip links of failed motes (mote failure implies all its links are down)
				}
				
				for (Link link : mote.getLinks()) {
					if (link == null) continue;
					String linkKey = link.getSource() + "-" + link.getDest();
					
					// Skip if already failed
					if (failedLinks.contains(linkKey)) {
						// Decrement timer
						int remainingTime = linkFailureTimers.get(linkKey) - 1;
						if (remainingTime <= 0) {
							// Link recovered (turned back on)
							failedLinks.remove(linkKey);
							linkFailureTimers.remove(linkKey);
						} else {
							linkFailureTimers.put(linkKey, remainingTime);
						}
					} else {
						// Check for new failure
						if (random.nextDouble() < linkFailureProbability) {
							failedLinks.add(linkKey);
							linkFailureTimers.put(linkKey, linkFailureDuration);
						}
					}
				}
			}
		}
	}
	
	/**
	 * Manually turn a mote off (trigger a mote failure)
	 * @param moteId The ID of the mote to turn off
	 */
	public void turnMoteOff(int moteId) {
		if (!failedMotes.contains(moteId)) {
			failedMotes.add(moteId);
			moteFailureTimers.put(moteId, moteFailureDuration);
		}
	}
	
	/**
	 * Manually turn a mote on (recover from failure)
	 * @param moteId The ID of the mote to turn on
	 */
	public void turnMoteOn(int moteId) {
		if (failedMotes.contains(moteId)) {
			failedMotes.remove((Integer) moteId);
			moteFailureTimers.remove(moteId);
		}
	}
	
	/**
	 * Manually turn a link off (trigger a link failure)
	 * @param source The source mote ID
	 * @param dest The destination mote ID
	 */
	public void turnLinkOff(int source, int dest) {
		String linkKey = source + "-" + dest;
		if (!failedLinks.contains(linkKey)) {
			failedLinks.add(linkKey);
			linkFailureTimers.put(linkKey, linkFailureDuration);
		}
	}
	
	/**
	 * Manually turn a link on (recover from failure)
	 * @param source The source mote ID
	 * @param dest The destination mote ID
	 */
	public void turnLinkOn(int source, int dest) {
		String linkKey = source + "-" + dest;
		if (failedLinks.contains(linkKey)) {
			failedLinks.remove(linkKey);
			linkFailureTimers.remove(linkKey);
		}
	}
	
	/**
	 * Check if a mote is currently off (failed)
	 * @param moteId The mote ID to check
	 * @return true if the mote is failed/off, false if it's operational/on
	 */
	public boolean isMoteOff(int moteId) {
		return failedMotes.contains(moteId);
	}
	
	/**
	 * Check if a mote is currently on (operational)
	 * @param moteId The mote ID to check
	 * @return true if the mote is operational/on, false if it's failed/off
	 */
	public boolean isMoteOn(int moteId) {
		return !failedMotes.contains(moteId);
	}
	
	/**
	 * Check if a link is currently off (failed).
	 * A link is considered off if: the link is explicitly failed, or the source mote is off
	 * (when a mote is off, all its outgoing links are treated as off).
	 * @param source The source mote ID
	 * @param dest The destination mote ID
	 * @return true if the link is failed/off, false if it's operational/on
	 */
	public boolean isLinkOff(int source, int dest) {
		if (isMoteOff(source)) {
			return true;
		}
		return failedLinks.contains(source + "-" + dest);
	}
	
	/**
	 * Check if a link is currently on (operational)
	 * @param source The source mote ID
	 * @param dest The destination mote ID
	 * @return true if the link is operational/on, false if it's failed/off
	 */
	public boolean isLinkOn(int source, int dest) {
		return !isLinkOff(source, dest);
	}
	
	/**
	 * Get list of all currently failed (off) motes
	 * @return List of mote IDs that are currently off
	 */
	public List<Integer> getFailedMotes() {
		return new ArrayList<>(failedMotes); // Return copy to prevent external modification
	}
	
	/**
	 * Get list of all currently failed (off) links
	 * @return List of "source-dest" strings representing failed links
	 */
	public List<String> getFailedLinks() {
		return new ArrayList<>(failedLinks); // Return copy to prevent external modification
	}
	
	/**
	 * Clear all failures (turn everything back on)
	 * Useful for resetting between runs
	 */
	public void clearFailures() {
		failedMotes.clear();
		failedLinks.clear();
		moteFailureTimers.clear();
		linkFailureTimers.clear();
	}
	
	/**
	 * Get statistics about current failures
	 * @return String describing current failure state
	 */
	public String getFailureStats() {
		return String.format("Failed motes: %d, Failed links: %d", 
			failedMotes.size(), failedLinks.size());
	}
	
	/**
	 * Get the number of currently failed motes
	 */
	public int getFailedMoteCount() {
		return failedMotes.size();
	}
	
	/**
	 * Get the number of currently failed links
	 */
	public int getFailedLinkCount() {
		return failedLinks.size();
	}
}

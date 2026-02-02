/*******************************************************************************
 * QoSDataHelper - Utility for retrieving QoS data from the DeltaIoT simulator.
 * Separates QoS retrieval from SolvePOMDP to avoid circular dependencies.
 *******************************************************************************/

package iot;

import java.util.ArrayList;
import java.util.List;

import simulator.QoS;

/**
 * Helper class for waiting and retrieving QoS (Quality of Service) data from the
 * DeltaIoT simulator. Used by both SolvePOMDP and POMDP without creating circular
 * dependencies.
 */
public final class QoSDataHelper {

	private QoSDataHelper() {
		// Utility class - prevent instantiation
	}

	/**
	 * Waits for QoS data to be ready for a specific run by validating that entries
	 * exist and have valid data. Ensures the simulator has complete data before
	 * access, preventing "Unknown value data" warnings.
	 *
	 * @param runNumber   The run number to wait for (1-indexed). Waits until at least
	 *                    this many runs exist.
	 * @param maxRetries  Maximum number of retry attempts (default: 20)
	 * @param retryDelayMs Delay between retries in milliseconds (default: 50ms)
	 * @return The last runNumber entries from the QoS list when ready, or empty list
	 *         on failure/timeout
	 */
	public static ArrayList<QoS> waitForQoSDataReady(int runNumber, int maxRetries, long retryDelayMs) {
		System.out.println("Waiting for QoS data ready... maxRetries: " + maxRetries);

		if (runNumber <= 0) {
			System.err.println("Warning: Invalid runNumber " + runNumber + ", must be > 0");
			return new ArrayList<>();
		}

		for (int attempt = 0; attempt < maxRetries; attempt++) {
			try {
				if (DeltaIOTConnector.networkMgmt == null) {
					System.err.println("Warning: networkMgmt is null, waiting...");
					Thread.sleep(retryDelayMs);
					continue;
				}

				if (DeltaIOTConnector.networkMgmt.getSimulator() == null) {
					System.err.println("Warning: simulator is null, waiting...");
					Thread.sleep(retryDelayMs);
					continue;
				}

				List<QoS> qosValues = DeltaIOTConnector.networkMgmt.getSimulator().getQosValues();
				if (qosValues == null) {
					System.err.println("Warning: qosValues list is null, waiting...");
					Thread.sleep(retryDelayMs);
					continue;
				}

				int qosSize = qosValues.size();
				System.out.println("getting network qos");
				System.out.println("run number: " + runNumber);
				System.out.println("qosValues size: " + qosSize);

				if (qosSize >= runNumber) {
					ArrayList<QoS> result = (ArrayList<QoS>) DeltaIOTConnector.networkMgmt.getNetworkQoS(runNumber);
					System.out.println("result size: " + result.size());
					return result;
				}

				Thread.sleep(retryDelayMs);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			} catch (Exception e) {
				System.err.println("Warning: Exception while waiting for QoS data: " + e.getMessage());
				try {
					Thread.sleep(retryDelayMs);
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					break;
				}
			}
		}

		System.err.println("Warning: Timeout waiting for QoS data for run " + runNumber);
		try {
			if (DeltaIOTConnector.networkMgmt != null) {
				return (ArrayList<QoS>) DeltaIOTConnector.networkMgmt.getNetworkQoS(runNumber);
			}
		} catch (Exception e) {
			System.err.println("Warning: Failed to get QoS data on timeout: " + e.getMessage());
		}
		return new ArrayList<>();
	}
}

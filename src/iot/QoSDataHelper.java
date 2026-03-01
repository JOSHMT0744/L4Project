/*******************************************************************************
 * QoSDataHelper - Utility for retrieving QoS data from the DeltaIoT simulator.
 * Separates QoS retrieval from SolvePOMDP to avoid circular dependencies.
 *******************************************************************************/

package iot;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import simulator.QoS;

/**
 * Helper class for waiting and retrieving QoS (Quality of Service) data from the
 * DeltaIoT simulator. Used by both SolvePOMDP and POMDP without creating circular
 * dependencies.
 */
public final class QoSDataHelper {
	private static final Logger log = LogManager.getLogger(QoSDataHelper.class);

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
		log.debug("Waiting for QoS data for run {}, maxRetries={}", runNumber, maxRetries);

		if (runNumber <= 0) {
			log.warn("Invalid runNumber {}, must be > 0", runNumber);
			return new ArrayList<>();
		}

		for (int attempt = 0; attempt < maxRetries; attempt++) {
			try {
				if (DeltaIOTConnector.networkMgmt == null) {
					log.trace("networkMgmt null, waiting...");
					Thread.sleep(retryDelayMs);
					continue;
				}
				if (DeltaIOTConnector.networkMgmt.getSimulator() == null) {
					log.trace("simulator null, waiting...");
					Thread.sleep(retryDelayMs);
					continue;
				}
				List<QoS> qosValues = DeltaIOTConnector.networkMgmt.getSimulator().getQosValues();
				if (qosValues == null) {
					log.trace("qosValues null, waiting...");
					Thread.sleep(retryDelayMs);
					continue;
				}
				int qosSize = qosValues.size();
				if (qosSize >= runNumber) {
					ArrayList<QoS> result = (ArrayList<QoS>) DeltaIOTConnector.networkMgmt.getNetworkQoS(runNumber);
					log.trace("QoS ready for run {}, result size={}", runNumber, result.size());
					return result;
				}
				Thread.sleep(retryDelayMs);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			} catch (Exception e) {
				log.warn("Exception waiting for QoS: {}", e.getMessage());
				try {
					Thread.sleep(retryDelayMs);
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					break;
				}
			}
		}

		log.warn("Timeout waiting for QoS data for run {}", runNumber);
		try {
			if (DeltaIOTConnector.networkMgmt != null) {
				return (ArrayList<QoS>) DeltaIOTConnector.networkMgmt.getNetworkQoS(runNumber);
			}
		} catch (Exception e) {
			log.warn("Failed to get QoS on timeout: {}", e.getMessage());
		}
		return new ArrayList<>();
	}
}

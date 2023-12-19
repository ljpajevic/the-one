package report;

import core.DTNHost;
import core.Settings;
import core.SettingsError;
import core.SimClock;
import core.UpdateListener;
import movement.ProhibitedMap;

import java.util.List;

/**
 * Abstract class that makes it easier to implement sampling reports.
 *
 * @author teemuk
 */
public class SamplingQueueSizeReport
extends Report
implements UpdateListener {

	//========================================================================//
	// Settings
	//========================================================================//
	/** Interval in seconds between samples ({@value}). */
	public static final String SAMPLE_INTERVAL_SETTING = "sampleInterval";
	/** Default value for sample interval ({@value} seconds). */
	public static final double DEFAULT_SAMPLE_INTERVAL = 60;
	//========================================================================//


	//========================================================================//
	// Instance vars
	//========================================================================//
	private double lastRecord = Double.MIN_VALUE;
	protected final double interval = DEFAULT_SAMPLE_INTERVAL;
	//========================================================================//

	@Override
	public void updated(final List<DTNHost> hosts) {
		if (SimClock.getTime() - lastRecord < interval) return;
		lastRecord = SimClock.getTime();

		write(getSimTime() + ": " + ProhibitedMap.queue_size);
	}
	//========================================================================//
}

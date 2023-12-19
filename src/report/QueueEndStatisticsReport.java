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
public class QueueEndStatisticsReport
extends Report {

	@Override
	public void done() {
		System.out.println("Test");
		write("number of independents who got coffee: " + ProhibitedMap.ind_got_coffee);
		write("number of dependents who got coffee: " + ProhibitedMap.dep_got_coffee);
		write("number of independents who gave up on coffee: " + ProhibitedMap.ind_gave_up_coffee);
		write("number of dependents who gave up on coffee: " + ProhibitedMap.dep_gave_up_coffee);

		super.done();
	}
	
}

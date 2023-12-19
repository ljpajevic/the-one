/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package report;

import core.ConnectionListener;
import core.DTNHost;
import core.Message;
import core.MessageListener;
import input.StandardEventsReader;

/**
 * Report that creates same output as the GUI's event log panel but formatted
 * like {@link input.StandardEventsReader} input. Message relying event has
 * extra one-letter identifier to tell that message was delivered to
 * final destination, delivered there again, or just normally relayed
 * (see the public constants).
 */
public class EventLogReportCoffee extends Report
implements ConnectionListener, MessageListener {

	public void processEvent(final DTNHost host1, 
							  final DTNHost host2) {

		if (host2.canBeInfluenced==true) write(getSimTime() + ": " + (host1 != null ? host1 : "") +" influenced "
		+ (host2 != null ? (" " + host2) : ""));

	}

	@Override
	public void messageTransferStarted(Message m, DTNHost from, DTNHost to) {
	}

	@Override
	public void messageTransferred(Message m, DTNHost from, DTNHost to, boolean firstDelivery) {
	}

	@Override
	public void newMessage(Message m) {
		processEvent(m.getFrom(), m.getTo());
	}

	@Override
	public void messageDeleted(Message m, DTNHost where, boolean dropped) {
	}

	@Override
	public void messageTransferAborted(Message m, DTNHost from, DTNHost to) {
	}

	@Override
	public void hostsConnected(DTNHost host1, DTNHost host2) {

	}

	@Override
	public void hostsDisconnected(DTNHost host1, DTNHost host2) {
		
	}
		

}

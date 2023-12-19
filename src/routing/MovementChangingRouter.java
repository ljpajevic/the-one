/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package routing;

import core.DTNHost;
import core.Message;
import core.Settings;



public class MovementChangingRouter extends ActiveRouter {

	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public MovementChangingRouter(Settings s) {
		super(s);
	}

	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected MovementChangingRouter(MovementChangingRouter r) {
		super(r);
	}

    @Override
    protected int checkReceiving(Message m, DTNHost from) {

		//people who can be influenced receive coffee
		if (this.getHost().canBeInfluenced==false) return MessageRouter.DENIED_POLICY;
        else return super.checkReceiving(m, from);
    }
	
    @Override
    public boolean createNewMessage(Message m) {
        // Only people drinking coffee send
        if ((this.getHost().isDrinkingCoffee==true)&&(m.getTo().canBeInfluenced==true)) {
            makeRoomForNewMessage(m.getSize());
			//the interactions between the routers and the movement class are done through the DTNHost class.
			m.getTo().sawCoffee=true;
            return super.createNewMessage(m);
        } else {
            return false;
        }
    }

	@Override
	public MovementChangingRouter replicate() {
		return new MovementChangingRouter(this);
	}

}

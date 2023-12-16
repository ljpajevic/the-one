/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package movement;

import input.WKTMapReader;
import javafx.scene.shape.Polygon;

import java.io.File;
import java.io.IOException;
import java.util.List;

import movement.map.MapNode;
import movement.map.SimMap;
import core.Coord;
import core.Settings;
import core.SimError;
import java.util.Random;

import java.awt.geom.Path2D;
import java.awt.geom.Line2D;

/**
 * The moving nodes are forbidden of touching the edges of the map. It is
 * a way of defining prohibited regions.
 * @author TheMarshalMole (Cristian Sandu)
 */
public class ProhibitedMap extends MovementModel {
	public static final String MAP_BASE_FORBIDDEN_NS = "ProhibitedMap";
	public static final String NROF_FILES_S = "nrofMapFiles";
	public static final String FILE_S = "mapFile";
	public static final String STARTING_POINT_s = "startingPoint";
	public static final String ALLOWD_S = "allowedZone";
	public static final String TABLE_S = "tableRestrictions";
	public static final String COFFEE_QUEUE_S = "coffeequeue";

	/** sim map for the model */
	private SimMap map = null;
	private SimMap startRegion;
	private Coord lastWaypoint;
	public SimMap coffeShopQueue;

	/** use java polygon */
	private Path2D pStartRegion;
	private Path2D plMap;
	public Path2D pAllowed;

	enum STATE { NONARRIVED, ARRIVED, DEPENDENT, INDEPENDENT, INDEPENDENT_QUEUE };
	public STATE nodestate = STATE.NONARRIVED;


	public ProhibitedMap() {
		readMap();
	}

	public ProhibitedMap(Settings settings) {
		super(settings);
		readMap();

		nodestate = STATE.NONARRIVED;
	}

	public ProhibitedMap(ProhibitedMap pMap) {
		super(pMap);
		map = pMap.getMap();
		this.startRegion = pMap.getStartRegion();
		this.pStartRegion = pMap.getPStartRegion();
		this.plMap = pMap.getPMap();
		pAllowed = pMap.pAllowed;
		nodestate = pMap.nodestate;
		coffeShopQueue = pMap.coffeShopQueue;
	}

	private void readMap() {
		SimMap simMap;
		Settings settings = new Settings(MAP_BASE_FORBIDDEN_NS);
		WKTMapReader r = new WKTMapReader(true);

		try {
			int nrofMapFiles = settings.getInt(NROF_FILES_S);

			for (int i = 1; i <= nrofMapFiles; i++ ) {
				String pathFile = settings.getSetting(FILE_S + i);
				r.addPaths(new File(pathFile), i);
			}
		} catch (IOException e) {
			throw new SimError(e.toString(),e);
		}

		WKTMapReader rP = new WKTMapReader(true);
		try {
			String path = settings.getSetting(STARTING_POINT_s);
			rP.addPaths(new File(path), 0);
		} catch (IOException e) {
			throw new SimError(e.toString(), e);
		}

		WKTMapReader dP = new WKTMapReader(true);
		try {
			String path = settings.getSetting(ALLOWD_S);
			dP.addPaths(new File(path), 0);
		} catch (IOException e) {
			throw new SimError(e.toString(), e);
		}

		WKTMapReader tP = new WKTMapReader(true);
		try {
			String path = settings.getSetting(ALLOWD_S);
			tP.addPaths(new File(path), 0);
		} catch (IOException e) {
			throw new SimError(e.toString(), e);
		}

		WKTMapReader qCHSP = new WKTMapReader(true);
		try {
			String path = settings.getSetting(COFFEE_QUEUE_S);
			qCHSP.addPaths(new File(path), 0);
		} catch (IOException e) {
			throw new SimError(e.toString(), e);
		}


		// accumulate all maps
		simMap = r.getMap();
		simMap.mirror();
		Coord offset = simMap.getMinBound().clone();
		simMap.translate(-offset.getX(), -offset.getY());

		SimMap mSP = rP.getMap();
		mSP.mirror();
		mSP.translate(-offset.getX(), -offset.getY());

		SimMap mAZ = dP.getMap();
		mAZ.mirror();
		mAZ.translate(-offset.getX(), -offset.getY());
	
		SimMap mTP = tP.getMap();
		mTP.mirror();
		mTP.translate(-offset.getX(), -offset.getY());
	
		SimMap qCSTP = qCHSP.getMap();
		qCSTP.mirror();
		qCSTP.translate(-offset.getX(), -offset.getY());

		this.coffeShopQueue = qCSTP;

		this.startRegion = mSP;
		List<MapNode> mn = startRegion.getNodes();
		this.pStartRegion = new Path2D.Double();

		Coord loc = mn.get(0).getLocation();
		this.pStartRegion.moveTo(loc.getX(), loc.getY());
		for(int i=0; i<mn.size(); i++) {
			loc = mn.get(i).getLocation();
			this.pStartRegion.lineTo(loc.getX(), loc.getY());
		}
		this.pStartRegion.closePath();

		this.map = simMap;
		mn = map.getNodes();
		this.plMap = new Path2D.Double();

		loc = mn.get(0).getLocation();
		this.plMap.moveTo(loc.getX(), loc.getY());
		for(int i=0; i<mn.size(); i++) {
			loc = mn.get(i).getLocation();
			this.plMap.lineTo(loc.getX(), loc.getY());
		}
		this.plMap.closePath();


		mn = mAZ.getNodes();
		this.pAllowed = new Path2D.Double();

		loc = mn.get(0).getLocation();
		this.pAllowed.moveTo(loc.getX(), loc.getY());
		for(int i=0; i < mn.size(); i++) {
			loc = mn.get(i).getLocation();
			this.pAllowed.lineTo(loc.getX(), loc.getY());
		}
		this.pAllowed.closePath();
	}

	public boolean isReady() {
		return true;
	}

	// TODO:
	public static final int frequency = 6500;
	public static int arrived = frequency;
	public static double independent_freq = 0.5;

	@Override
	public Path getPath() {
		// Creates a new path from the previous waypoint to a new one.
		final Path p;
		p = new Path( super.generateSpeed() );
		p.addWaypoint( this.lastWaypoint.clone() );

		if (nodestate == STATE.NONARRIVED) {
			arrived -= 1;
			if (arrived == 0) {
				nodestate = STATE.ARRIVED;
				this.lastWaypoint = new Coord(this.lastWaypoint.getX() - 10, this.lastWaypoint.getY() + 20);
				p.addWaypoint( this.lastWaypoint );
				arrived = frequency;
				return p;
			} else {
				return p;
			}
		}

		if (nodestate == STATE.ARRIVED) {
			// decide if it is going to coffee shop or the queue
			if (rng.nextDouble() < independent_freq) {
				nodestate = STATE.INDEPENDENT;
			} else {
				nodestate = STATE.DEPENDENT;
			}
		}
		
		if (nodestate == STATE.INDEPENDENT) {
			// move closer to the queue
			Coord c = this.randomCoord();
			Coord queueEnqPos = coffeShopQueue.getNodes().get(
				coffeShopQueue.getNodes().size() - 1
			).getLocation();
			double rDistance = this.lastWaypoint.distance(queueEnqPos);
			do {
				c = this.randomCoord();
			} while ( !(pAllowed.contains(c.getX(), c.getY()) && c.distance(queueEnqPos) <= rDistance) );

			rDistance = c.distance(queueEnqPos);
			System.out.println(c.distance(queueEnqPos));
			if (rDistance <= 5) {
				// enroll in queue
				System.out.println(rDistance);
				nodestate = STATE.INDEPENDENT_QUEUE;
			}

			p.addWaypoint( c );
			this.lastWaypoint = c;
			return p;
		} else if (nodestate == STATE.INDEPENDENT_QUEUE) {
			return p;
		} else {
			// Add only one point. An arbitrary number of Coords could be added to
			// the path here and the simulator will follow the full path before
			// asking for the next one.
			Coord c = this.randomCoord();
			do {
				c = this.randomCoord();
			} while ( !pAllowed.contains(c.getX(), c.getY()) );

			p.addWaypoint( c );
			this.lastWaypoint = c;
			return p;
		}
	}

	@Override
	public Coord getInitialLocation() {
		do {
			this.lastWaypoint = this.randomCoord();
		} while ( !pStartRegion.contains(lastWaypoint.getX(), lastWaypoint.getY()) );
		return this.lastWaypoint;
	}

	@Override
	public MovementModel replicate() {
		return new ProhibitedMap(this);
	}

	private Coord randomCoord() {
		return new Coord(
			rng.nextDouble() * super.getMaxX(),
			rng.nextDouble() * super.getMaxY() );
	}

	public SimMap getMap() {
		return map;
	}

	public SimMap getStartRegion() {
		return startRegion;
	}

	public Path2D getPStartRegion() {
		return pStartRegion;
	}

	public Coord getLastWaypoint() {
		return lastWaypoint;
	}

	public Path2D getPMap() {
		return plMap;
	}
}

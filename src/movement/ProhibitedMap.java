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

	/** sim map for the model */
	private SimMap map = null;
	private SimMap startRegion;
	private Coord lastWaypoint;

	/** use java polygon */
	private Path2D pStartRegion;
	private Path2D plMap;
	public Path2D pAllowed;

	enum STATE { NONARRIVED, ARRIVED, DEPENDENT, INDEPENDENT, QUEUE };
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
				arrived = frequency;
			} else {
				return p;
			}
		}

	
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

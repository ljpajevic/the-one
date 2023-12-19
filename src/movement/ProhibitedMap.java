/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package movement;

import input.WKTMapReader;
import javafx.geometry.Point2D;
import javafx.scene.shape.Polygon;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

import movement.map.MapNode;
import movement.map.SimMap;
import core.Coord;
import core.Settings;
import core.SimError;
import java.util.Random;

import javax.sound.sampled.Line;

import java.awt.geom.Path2D;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

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
	public static final String QUEUE_S = "coffeeRestrictions";
	public static final String COFFEE_QUEUE_S = "coffeequeue";
	public static final String CLASSROOM_s = "classroom";

	/** sim map for the model */
	private SimMap map = null;
	private SimMap startRegion;
	private Coord lastWaypoint;
	public SimMap coffeShopQueue;
	public SimMap restredAreasMap;

	/** use java polygon */
	private Path2D pStartRegion;
	private Path2D plMap;
	public Path2D pAllowed;
	public List<MapNode> qPostitions;
	public List<List<Coord>> restredAreas;
	public List<Coord> classroom;
	public List<Coord> table;

	enum STATE { NONARRIVED, ARRIVED, DEPENDENT, INDEPENDENT, INDEPENDENT_QUEUE, GOING_TO_CLASSROOM,
		CLASSROOM, LEAVING, OUT, GOING_TO_TABLE, TABLE };
	public STATE nodestate = STATE.NONARRIVED;

	public class MapNodesComparator implements Comparator<MapNode> {
		@Override
		public int compare(MapNode a, MapNode b) {
			if(a.getLocation().getY() == b.getLocation().getY()) {
				return 0;
			} else if(a.getLocation().getY() < b.getLocation().getY()) {
				return -1;
			}
			return 1;
		}
	}

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
		qPostitions = pMap.qPostitions;
		restredAreas = pMap.restredAreas;
		restredAreasMap = pMap.restredAreasMap;
		classroom = pMap.classroom;
		table = pMap.table;
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

		restredAreas = new ArrayList<List<Coord>>(20);
		try {
			int nrofMapFiles = settings.getInt(NROF_FILES_S);

			for (int i = 1; i <= nrofMapFiles; i++ ) {
				String pathFile = settings.getSetting(FILE_S + i);
				try (BufferedReader br = new BufferedReader(new FileReader(pathFile))) {
					String line;
					while ((line = br.readLine()) != null) {
						if (line.length() < 10) {
							continue;
						} else if (line.startsWith("POLYGON")) {
							List<Coord> polygon = processPolygon(line, -offset.getX(), -offset.getY());
							restredAreas.add(polygon);
						}

					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		} catch (Exception e) {
			throw new SimError(e.toString(),e);
		}

		this.table = new ArrayList<>();
		WKTMapReader restrP = new WKTMapReader(true);
		int numberTables = 0;
		try {
			String path = settings.getSetting(TABLE_S);
			restrP.addPaths(new File(path), 0);
			/* read the files */
			try (BufferedReader br = new BufferedReader(new FileReader(path))) {
				String line;
				while ((line = br.readLine()) != null) {
					if (line.length() < 10) {
						continue;
					}
					List<Coord> polygon = processPolygon(line, -offset.getX(), -offset.getY());
					double xc = 0, yc = 0;
					for(int i=0; i<polygon.size(); i++){
						xc += polygon.get(i).getX();
						yc += polygon.get(i).getY();
					}
					xc /= polygon.size();
					yc /= polygon.size();
					table.add(new Coord(xc, yc));
					restredAreas.add(polygon);
					numberTables += 1;
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		} catch (IOException e) {
			throw new SimError(e.toString(), e);
		}
		this.tableID = rng.nextInt(numberTables);


		try {
			String path = settings.getSetting(QUEUE_S);
			/* read the files */
			try (BufferedReader br = new BufferedReader(new FileReader(path))) {
				String line;
				while ((line = br.readLine()) != null) {
					if (line.length() < 10) {
						continue;
					}
					List<Coord> polygon = processPolygon(line, -offset.getX(), -offset.getY());
					restredAreas.add(polygon);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		} catch (Exception e) {
			throw new SimError(e.toString(), e);
		}

		/* READS CLASSROOM */
		try {
			String path = settings.getSetting(CLASSROOM_s);
			/* read the files */
			try (BufferedReader br = new BufferedReader(new FileReader(path))) {
				String line;
				while ((line = br.readLine()) != null) {
					if (line.length() < 10) {
						continue;
					}
					List<Coord> polygon = processPolygon(line, -offset.getX(), -offset.getY());
					classroom = polygon;
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		} catch (Exception e) {
			throw new SimError(e.toString(), e);
		}

		WKTMapReader qCHSP = new WKTMapReader(true);
		try {
			String path = settings.getSetting(COFFEE_QUEUE_S);
			qCHSP.addPaths(new File(path), 0);
		} catch (IOException e) {
			throw new SimError(e.toString(), e);
		}


		SimMap mSP = rP.getMap();
		mSP.mirror();
		mSP.translate(-offset.getX(), -offset.getY());

		SimMap mAZ = dP.getMap();
		mAZ.mirror();
		mAZ.translate(-offset.getX(), -offset.getY());
	
		SimMap mTP = restrP.getMap();
		mTP.mirror();
		mTP.translate(-offset.getX(), -offset.getY());
		this.restredAreasMap = mTP;
	
		SimMap qCSTP = qCHSP.getMap();
		List<MapNode> list = new ArrayList<>(qCHSP.getNodes());
		list.sort(new MapNodesComparator());
		this.qPostitions = list;

		/* sort the nodes based on Y */
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
	public static final int frequency = 5500;
	public static int arrived = frequency;
	public static double independent_freq = 0.5;
	public static double independent_freq_class = 0.5;
	public static double BUY_COFFEE_NEXT_DST = 0.5;
	public static int queue_size = 0;
	public static int ind_got_coffee = 0;
	public static int dep_got_coffee = 0;
	public static int ind_gave_up_coffee = 0;
	public static int dep_gave_up_coffee = 0;
	public static final int MAX_QUEUE = 10;
	public int queue_position = 0; 

	public static final int TIME_BEFORE_LEAVE = 32000; 
	public long timer = 0;
	public Coord initialpoint, vinitial ; 
	public boolean mustleave = false;

	public static final int CLASS_STARTIME = 20000, CLASS_DURATION = 10000; 
	public static final int COFFEE_DRINKING_TIME = 50000;
	public int coffee_timer = 0;
	public int start_class_timer = CLASS_STARTIME; 
	public int class_timer = 0; 
	public boolean classended = false;

	public int selectedTable = 0;
	/* the place where they will sit */
	public int tableID = 0;
	public Coord tableCoord;

	public final int TABLE_SITTING = 5000; 
	
	public long table_timer = 0;
	@Override
	public void setTimer(int timer) {
		this.timer = timer;
	}

	@Override
	public Path getPath() {
		// Creates a new path from the previous waypoint to a new one.
		final Path p;
		p = new Path( super.generateSpeed() );
		p.addWaypoint( this.lastWaypoint.clone() );

		if (timer > TIME_BEFORE_LEAVE) {
			mustleave = true;
		}

		if (timer > CLASS_STARTIME + CLASS_DURATION) {
			classended = true;
		}	

		if (coffee_timer>0) coffee_timer= coffee_timer-1;
		else this.getHost().isDrinkingCoffee=false;

		if (this.getHost().sawCoffee==true){
			this.getHost().canBeInfluenced=false;
			this.getHost().sawCoffee=false;
			nodestate = STATE.INDEPENDENT;
		}


		if (nodestate == STATE.NONARRIVED) {
			arrived -= 1;
			if (arrived == 0) {
				nodestate = STATE.ARRIVED;
				vinitial = this.lastWaypoint.clone();
				this.lastWaypoint = new Coord(this.lastWaypoint.getX() - 10, this.lastWaypoint.getY() + 20);
				initialpoint = this.lastWaypoint.clone();
				p.addWaypoint( this.lastWaypoint );
				arrived = frequency;
				return p;
			} else {
				return p;
			}
		}
		if (nodestate == STATE.ARRIVED) {
			tableID = rng.nextInt(table.size());
			// decide if it is going to coffee shop or the queue
			if ("ind".equals(this.getHost().groupId)) {
				nodestate = STATE.INDEPENDENT;
			} else {
				nodestate = STATE.DEPENDENT;
				
			}
		}	
	
		if (nodestate == STATE.INDEPENDENT) {
			// move closer to the queue
			Coord c = this.randomCoord();
			Coord queueEnqPos = qPostitions.get(
				coffeShopQueue.getNodes().size() - 1
			).getLocation();
			double rDistance = this.lastWaypoint.distance(queueEnqPos);
			do {
				c = this.randomCoord();
			} while ( !(pAllowed.contains(c.getX(), c.getY()) && c.distance(queueEnqPos) <= rDistance) );

			rDistance = c.distance(queueEnqPos);
			if (rDistance <= 5) {
				if (queue_size < MAX_QUEUE) {
					queue_size+=1;
					nodestate = STATE.INDEPENDENT_QUEUE;
					queue_position = coffeShopQueue.getNodes().size() - 2;
				} else {
					nodestate = STATE.DEPENDENT;
					if(this.getHost().groupId.equals("ind"))ProhibitedMap.ind_gave_up_coffee+=1;
					else ProhibitedMap.dep_gave_up_coffee+=1;
				}
			}

			p.addWaypoint( c );
			this.lastWaypoint = c;

			return p;
		} else if (nodestate == STATE.INDEPENDENT_QUEUE) {
			// change the speed
			Path pq = new Path( super.generateSpeed() / 2 );
			pq.addWaypoint( this.lastWaypoint.clone() );

			// move closer to the queue
			Coord queueEnqPos = qPostitions.get(
				queue_position
			).getLocation();

			Coord newPost = getDestinationCoordinate(this.lastWaypoint.getX(), this.lastWaypoint.getY(), 
						queueEnqPos.getX(), queueEnqPos.getY(), 0.10);
			if (newPost.distance(queueEnqPos) < 1) {
				queue_position -= 1;
			}

			if (queue_position == -1) {
				queue_size -= 1;
				if(this.getHost().groupId.equals("ind"))ProhibitedMap.ind_got_coffee+=1;
				else ProhibitedMap.dep_got_coffee+=1;

				//starts coffee counter
				this.getHost().isDrinkingCoffee=true;
				this.coffee_timer = COFFEE_DRINKING_TIME;

				if (rng.nextDouble() < independent_freq_class) {
					nodestate = STATE.GOING_TO_CLASSROOM;
					newPost = new Coord(newPost.getX() + 35, newPost.getY());
				} else {
					nodestate = STATE.GOING_TO_TABLE;
					newPost = new Coord(newPost.getX() - 35, newPost.getY());
				}

				/* getting out */
				pq.addWaypoint( newPost );
			}
			pq.addWaypoint( newPost );
			this.lastWaypoint = newPost;
			return pq;
		} else if (nodestate == STATE.GOING_TO_CLASSROOM) {
			Path pq = new Path( super.generateSpeed());
			pq.addWaypoint( this.lastWaypoint.clone() );

			// move closer to the queue
			Coord classroomEntrance = 
				new Coord((classroom.get(0).getX() + classroom.get(1).getX())/2, 
						(classroom.get(0).getY() + classroom.get(1).getY())/2);
			Coord c = this.randomCoord();
			double rDistance = this.lastWaypoint.distance(classroomEntrance), d;
			if (rDistance > 25) {
				do {
					c = this.randomCoord();
					d = c.distance(classroomEntrance);
				} while ( (!(pAllowed.contains(c.getX(), c.getY()) && d <= (rDistance + 5)))
					|| checkTableRestrictions(this.lastWaypoint, c) );
			} else {
				c = this.lastWaypoint;
			}

			if (c.distance(classroomEntrance) < 25) {
				nodestate = STATE.CLASSROOM;
				pq.addWaypoint( c );

				c = new Coord(c.getX() + 45, c.getY() - 20);
				pq.addWaypoint( c );
			} else {
				pq.addWaypoint( c );

			}
			this.lastWaypoint = c;
			return pq;
		} else if (nodestate == STATE.LEAVING) {
			this.getHost().canBeInfluenced=false;
			Path pq = new Path( super.generateSpeed());
			pq.addWaypoint( this.lastWaypoint.clone() );

			Coord c = this.randomCoord();
			double rDistance = this.lastWaypoint.distance(initialpoint), d;
			if (rDistance > 25) {
				do {
					c = this.randomCoord();
					d = c.distance(initialpoint);
				} while ( (!(pAllowed.contains(c.getX(), c.getY()) && d <= (rDistance + 5)))
					|| checkTableRestrictions(this.lastWaypoint, c) );
			} else {
				c = this.lastWaypoint;
			}

			d = c.distance(initialpoint);
			if (d < 40) {
				nodestate = STATE.OUT;
				pq.addWaypoint( c );

				c = vinitial.clone();
				pq.addWaypoint( c );
			} else {
				pq.addWaypoint( c );

			}

			this.lastWaypoint = c;
			return pq;
		} else if (nodestate == STATE.CLASSROOM) {
			Path pq = new Path( super.generateSpeed());
			pq.addWaypoint( this.lastWaypoint.clone() );

			if (classended) {
				Coord c = new Coord(this.lastWaypoint.getX() - 45, this.lastWaypoint.getY() + 30);
				p.addWaypoint( c );
				this.lastWaypoint = c;

				nodestate = STATE.DEPENDENT;
			}

			return pq;
		} else if (nodestate == STATE.DEPENDENT) {

			this.getHost().canBeInfluenced=true;
			Path pq = new Path( super.generateSpeed());
			pq.addWaypoint( this.lastWaypoint.clone() );
			
			// Add only one point. An arbitrary number of Coords could be added to
			// the path here and the simulator will follow the full path before
			// asking for the next one.
			Coord c = this.randomCoord();
			do {
				c = this.randomCoord();
			} while ( !pAllowed.contains(c.getX(), c.getY()) || checkTableRestrictions(this.lastWaypoint, c) );

			pq.addWaypoint( c );
			this.lastWaypoint = c;

			if (mustleave) {
				nodestate = STATE.LEAVING;
			}
			return pq;
		} else if (nodestate == STATE.LEAVING) {
			this.getHost().canBeInfluenced=false;
			Path pq = new Path( super.generateSpeed());
			pq.addWaypoint( this.lastWaypoint.clone() );

			// move closer to the queue
			Coord c = this.randomCoord();
			double rDistance = this.lastWaypoint.distance(initialpoint), d;
			if (rDistance > 25) {
				do {
					c = this.randomCoord();
					d = c.distance(initialpoint);
				} while ( (!(pAllowed.contains(c.getX(), c.getY()) && d <= (rDistance + 5)))
					|| checkTableRestrictions(this.lastWaypoint, c) );
			} else {
				c = this.lastWaypoint;
			}

			if (c.distance(initialpoint) < 25) {
				//nodestate = STATE.OUT;
				pq.addWaypoint( c );

				c = new Coord(c.getX() - 5, c.getY() + 20);
				pq.addWaypoint( c );
			} else {
				pq.addWaypoint( c );
			}
			this.lastWaypoint = c;
			return pq;
		} else if (nodestate == STATE.OUT) {
			this.getHost().canBeInfluenced=false;
			Path pq = new Path( super.generateSpeed());
			pq.addWaypoint( this.lastWaypoint.clone() );
			return pq;
		} else if (nodestate == STATE.GOING_TO_TABLE) {
			Path pq = new Path( super.generateSpeed());
			pq.addWaypoint( this.lastWaypoint.clone() );

			// move closer to the queue
			Coord tableCoord = table.get(tableID);
			// Coord table = 
			double rDistance = this.lastWaypoint.distance(tableCoord), d;
			Coord c;
			if (rDistance > 50) {
				do {
					c = this.randomCoord();
					d = c.distance(tableCoord);
				} while ( (!(pAllowed.contains(c.getX(), c.getY()) && d <= (rDistance + 5)))
					|| checkTableRestrictions(this.lastWaypoint, c) );
			} else {
				c = this.lastWaypoint;
			}

			if (c.distance(tableCoord) < 50) {
				nodestate = STATE.TABLE;
				table_timer = timer;

				pq.addWaypoint( c );

			} else {
				pq.addWaypoint( c );
			}
			this.lastWaypoint = c;
			
			return pq;
		} else if (nodestate == STATE.TABLE) {
			Path pq = new Path( super.generateSpeed());
			pq.addWaypoint( this.lastWaypoint.clone() );
			if (timer - table_timer >= TABLE_SITTING) {
				nodestate = STATE.LEAVING;
			}
			return pq;
		}
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

	public SimMap getRestrMap() {
		return restredAreasMap;
	}

	public SimMap getQueueMap() {
		return coffeShopQueue;
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

	private static Coord getDestinationCoordinate(double x, double y, double x1, double y1, double distance) {
        double deltaX = x1 - x;
        double deltaY = y1 - y;

        double length = Math.sqrt(deltaX * deltaX + deltaY * deltaY);

        double normalizedDeltaX = deltaX / length;
        double normalizedDeltaY = deltaY / length;

        double newX = x + normalizedDeltaX * distance;
        double newY = y + normalizedDeltaY * distance;

        return new Coord(newX, newY);
    }

    private static List<Coord> processPolygon(String polygonLine, double transX, double transY) {
        String coordinates = polygonLine.substring(polygonLine.indexOf("((") + 2, polygonLine.indexOf("))"));
        String[] points = coordinates.split(", ");

        List<Coord> polygon = new ArrayList<Coord>();
        for (String point : points) {
            String[] xy = point.split(" ");
            double x = Double.parseDouble(xy[0]) + transX;
            double y = -Double.parseDouble(xy[1]) + transY;
            polygon.add(new Coord(x, y));
        }

        return polygon;
    }

	private boolean checkTableRestrictions(Coord start, Coord end) {
		Line2D lineV = new Line2D.Double(start.getX(), start.getY(), end.getX(), end.getY());
        for (int i=0; i<this.restredAreas.size(); i++) {
			List<Coord> poly = restredAreas.get(i);

			for (int j=0; j<poly.size(); j++) {
				Coord c1 = poly.get(j);
				Coord c2 = poly.get((j+1) % poly.size());
				Line2D lineP = new Line2D.Double(c1.getX(), c1.getY(), c2.getX(), c2.getY());

				if (lineP.intersectsLine(lineV)) {
					return true;
				}
			}
		}
		return false;
    }

}

/* *********************************************************************** *
 * project: org.matsim.*
 * MATSim4UrbanSim.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2010 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

/**
 *
 */
package org.matsim.contrib.matsim4urbansim.run;


import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.matsim.contrib.analysis.kai.KaiAnalysisListener;
import org.matsim.contrib.matsim4urbansim.analysis.DanielAnalysisListenerEvents;
import org.matsim.core.controler.MatsimServices;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.facilities.ActivityFacilities;



/**
 * @author thomas
 * 
 * improvements jan'12:
 * 
 * - This class is a revised version of "MATSim4UrbanSim".
 * - Increased configurability: 
 * 	First approach to increase the configurability of MATSim4UrbanSim modules such as
 * 	the zonz2zone impedance matrix, zone based- and grid based accessibility computation. Modules can be en-disabled
 * 	additional modules can be added by other classes extending MATSim4UrbanSimV2.
 * - Data Processing on Demand:
 *  Particular input data is processed when a corresponding module is enabled, e.g. an array of aggregated work places will
 *  be generated when either the zone based- or grid based accessibility computation is activated.
 * - Extensibility:
 * 	This class provides standard functionality such as configuring MATSim, reading UrbanSim input data, running the 
 * 	mobility simulation and so forth... This functionality can be extended by an inheriting class (e.g. MATSim4UrbanSimZurichAccessibility) 
 * 	by implementing certain stub methods such as "addFurtherControlerListener", "modifyNetwork", "modifyPopulation" ...
 * - Backup Results:
 *  This was also available before but not documented. Some data is overwritten with each run, e.g. the zone2zone impedance matrix or data
 *  in the MATSim output folder. If the backup is activated the most imported files (see BackupRun class) are saved in a new folder. In order 
 *  to match the saved data with the corresponding run or year the folder names contain the "simulation year" and a time stamp.
 * - Other improvements:
 * 	For a better readability some functionality is out-sourced into helper classes
 * 
 * improvements jan'13:
 * 
 * - The MATSim4URbanSim zone version now supports warm and hot start, i.e. it is possible to reuse a plans file from a previous MATSim run. 
 *  The plans file will be merged with the current UrbanSim population. This means, persons that emigrated, moved into a new home, 
 *  changed their employment status or got a new job are getting new plans.
 * 
 */
 class MATSim4UrbanSimZone extends MATSim4UrbanSimParcel{

	// logger
	private static final Logger log = Logger.getLogger(MATSim4UrbanSimZone.class);
	
	static final boolean BRUSSELS_SCENARIO_MODIFY_NETWORK = true ;
	static final boolean BRUSSELS_SCENARIO_CALCULATE_ZONE2ZONE_MATRIX = false ;

	private String cleFile;

	/**
	 * constructor
	 * 
	 * @param args contains at least a reference to 
	 * 		  MATSim4UrbanSim configuration generated by UrbanSim
	 */
	MATSim4UrbanSimZone(String args[]){
		super(args);
		// set flag to false (needed for ReadFromUrbanSimModel to choose the right method)
		isParcelMode = false;
	}
	
	MATSim4UrbanSimZone(String args[], String cleFile){
		this(args);
		this.cleFile = cleFile;
	}
	
	@Override
	void addFurtherControlerListener(ActivityFacilities zones, ActivityFacilities parcels, MatsimServices controler) {
		controler.addControlerListener(new KaiAnalysisListener()) ;
		// do nothing when no file is specified, otherwise check if it exists
		if(!(cleFile == null)){
			if(new File(cleFile).exists()){
				log.info("loading " + DanielAnalysisListenerEvents.class.getSimpleName() + " with " + cleFile + "...");
				List<Tuple<Integer, Integer>> timeslots = new ArrayList<Tuple<Integer,Integer>>();
				timeslots.add(new Tuple<Integer, Integer>(0, 6));
				timeslots.add(new Tuple<Integer, Integer>(6, 10));
				timeslots.add(new Tuple<Integer, Integer>(10, 14));
				timeslots.add(new Tuple<Integer, Integer>(14, 18));
				timeslots.add(new Tuple<Integer, Integer>(18, 24));
				timeslots.add(new Tuple<Integer, Integer>(0, 24));
				controler.addControlerListener(new DanielAnalysisListenerEvents(cleFile, zones, timeslots));
			}else{
//				log.error("can not find " + cleFile);
				throw new RuntimeException("You specified a cleFile but it does not exist: " + cleFile + 
						". This is very special and only used for the brussels case-study. Usually you should call" +
						" Matsim4UrbanSimZone.main with only one argument...");
			}
		}
	}

	/**
	 * Entry point
	 * @param args UrbanSim command prompt
	 */
	public static void main(String args[]){
		
		long start = System.currentTimeMillis();
		String[] arguments = null;
		String cleFile = null;
		if(args.length == 1){
			arguments = args;
		}else if(args.length == 2){
			arguments = new String[1];
			arguments[0] = args[0];
			cleFile = args[1];
		}else{
			log.error("only one or two arguments are allowed.");
			System.exit(-1);
		}
		
		MATSim4UrbanSimZone m4u = new MATSim4UrbanSimZone(arguments, cleFile);
		m4u.run();
		m4u.matsim4UrbanSimShutdown();
		MATSim4UrbanSimZone.isSuccessfulMATSimRun = Boolean.TRUE;

		log.info("Computation took " + ((System.currentTimeMillis() - start)/60000) + " minutes. Computation done!");
	}
}

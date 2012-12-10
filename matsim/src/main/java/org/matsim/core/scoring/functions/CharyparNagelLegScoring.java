/* *********************************************************************** *
 * project: org.matsim.*
 * CharyparNagelOpenTimesScoringFunctionFactory.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
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

package org.matsim.core.scoring.functions;

import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.api.experimental.events.ActivityEndEvent;
import org.matsim.core.api.experimental.events.AgentDepartureEvent;
import org.matsim.core.api.experimental.events.Event;
import org.matsim.core.api.experimental.events.PersonEntersVehicleEvent;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.scoring.ScoringFunctionAccumulator.ArbitraryEventScoring;
import org.matsim.core.scoring.ScoringFunctionAccumulator.LegScoring;
import org.matsim.core.utils.misc.RouteUtils;
import org.matsim.pt.PtConstants;

/**
 * This is a re-implementation of the original CharyparNagel function, based on a
 * modular approach.
 * @see <a href="http://www.matsim.org/node/263">http://www.matsim.org/node/263</a>
 * @author rashid_waraich
 */
public class CharyparNagelLegScoring implements LegScoring, ArbitraryEventScoring {

	protected double score;
	private double lastTime;

	private static final double INITIAL_LAST_TIME = 0.0;
	private static final double INITIAL_SCORE = 0.0;

	/** The parameters used for scoring */
	protected final CharyparNagelScoringParameters params;
	private Leg currentLeg;
	protected Network network;
	private boolean nextEnterVehicleIsFirstOfTrip = true ;
	private boolean nextStartPtLegIsFirstOfTrip = true ;

	public CharyparNagelLegScoring(final CharyparNagelScoringParameters params, Network network) {
		this.params = params;
		this.network = network;
		this.reset();
	}

	@Override
	public void reset() {
		this.lastTime = INITIAL_LAST_TIME;
		this.score = INITIAL_SCORE;
		this.nextEnterVehicleIsFirstOfTrip = true ;
		this.nextStartPtLegIsFirstOfTrip = true ;
	}

	@Override
	public void startLeg(final double time, final Leg leg) {
		assert leg != null;
		this.lastTime = time;
		this.currentLeg = leg;
	}

	@Override
	public void endLeg(final double time) {
		handleLeg(this.currentLeg, time);
		this.lastTime = time;
	}

	private void handleLeg(Leg leg, final double time) {
		this.score += calcLegScore(this.lastTime, time, leg);
	}

	@Override
	public void finish() {

	}

	@Override
	public double getScore() {
		return this.score;
	}

	protected double calcLegScore(final double departureTime, final double arrivalTime, final Leg leg) {
		double tmpScore = 0.0;
		double travelTime = arrivalTime - departureTime; // travel time in seconds	
		if (TransportMode.car.equals(leg.getMode())) {
			double dist = 0.0; // distance in meters
			if (this.params.marginalUtilityOfDistanceCar_m != 0.0) {
				Route route = leg.getRoute();
				dist = getDistance(route);
			}
			tmpScore += travelTime * this.params.marginalUtilityOfTraveling_s + this.params.marginalUtilityOfDistanceCar_m * dist;
			tmpScore += this.params.constantCar ;
			// (yyyy once we have multiple car legs without "real" activities in between, this will produce wrong results.  kai, dec'12)
		} else if (TransportMode.pt.equals(leg.getMode())) {
			double dist = 0.0; // distance in meters
			if (this.params.marginalUtilityOfDistancePt_m != 0.0) {
				Route route = leg.getRoute();
				dist = getDistance(route);
			}
			tmpScore += travelTime * this.params.marginalUtilityOfTravelingPT_s + this.params.marginalUtilityOfDistancePt_m * dist;

			// (yyyyyy NOTE: pt wait is not separately scored!! --> should be done!  kai, nov'12)

			tmpScore += this.params.constantPt ;
			// (yyyyyy NOTE: the pt constant is added for _every_ pt leg.  This is not how such models are estimated.  kai, nov'12)
			// see below.  kai, dec'12

		} else if (TransportMode.walk.equals(leg.getMode()) || TransportMode.transit_walk.equals(leg.getMode())) {
			double dist = 0.0; // distance in meters
			if (this.params.marginalUtilityOfDistanceWalk_m != 0.0) {
				Route route = leg.getRoute();
				dist = getDistance(route);
			}
			tmpScore += travelTime * this.params.marginalUtilityOfTravelingWalk_s + this.params.marginalUtilityOfDistanceWalk_m * dist;
			tmpScore += this.params.constantWalk ;
		} else if (TransportMode.bike.equals(leg.getMode())) {
			tmpScore += travelTime * this.params.marginalUtilityOfTravelingBike_s;
			tmpScore += this.params.constantBike ;
			// (yyyy once we have multiple bike legs without "real" activities in between, this will produce wrong results.  kai, dec'12)
		} else {
			double dist = 0.0; // distance in meters
			if (this.params.marginalUtilityOfDistanceOther_m != 0.0) {
				Route route = leg.getRoute();
				dist = getDistance(route);
			}
			tmpScore += travelTime * this.params.marginalUtilityOfTravelingOther_s + this.params.marginalUtilityOfDistanceOther_m * dist;
			tmpScore += this.params.constantOther ;
			// (yyyy once we have multiple "other" legs without "real" activities in between, this will produce wrong results.  kai, dec'12)
		}
		return tmpScore;
	}

	private double getDistance(Route route) {
		double dist;
		if (route instanceof NetworkRoute) {
			dist =  RouteUtils.calcDistance((NetworkRoute) route, network);
		} else {
			dist = route.getDistance();
		}
		return dist;
	}
	
	@Override
	public void handleEvent(Event event) {
		if ( event instanceof ActivityEndEvent ) {
			// When there is a "real" activity, flags are reset:
			if ( !PtConstants.TRANSIT_ACTIVITY_TYPE.equals( ((ActivityEndEvent)event).getActType()) ) {
				this.nextEnterVehicleIsFirstOfTrip  = true ;
				this.nextStartPtLegIsFirstOfTrip = true ;
			}
		} else if ( event instanceof PersonEntersVehicleEvent ) {
			if ( !this.nextEnterVehicleIsFirstOfTrip ) {
				// all vehicle entering after the first triggers the disutility of line switch:
				this.score  += params.utilityOfLineSwitch ;
			}
			this.nextEnterVehicleIsFirstOfTrip = false ;
		} else if ( event instanceof AgentDepartureEvent ) {
			if ( TransportMode.pt.equals( ((AgentDepartureEvent)event).getLegMode() ) ) {
				if ( !this.nextStartPtLegIsFirstOfTrip ) {
					this.score -= params.constantPt ;
					// (yyyy deducting this again, since is it wrongly added above.  should be consolidated; this is so the code
					// modification is minimally invasive.  kai, dec'12)
				}
				this.nextStartPtLegIsFirstOfTrip = false ;
			}
		}
	}


}

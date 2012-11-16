/* *********************************************************************** *
 * project: org.matsim.*
 * TransitTimeAllocationMutator.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2009 by the members listed in the COPYING,        *
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

package org.matsim.pt.replanning;

import org.matsim.core.config.Config;
import org.matsim.core.config.groups.VspExperimentalConfigGroup.ActivityDurationInterpretation;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.replanning.modules.AbstractMultithreadedModule;
import org.matsim.population.algorithms.PlanAlgorithm;

/**
 * Copy/Paste of TimeAllocationMutator, that calls TransitPlanMutateTimeAllocation instead
 * of PlanMutateTimeAllocation.
 *
 * @author mrieser
 */
public class TransitTimeAllocationMutator extends AbstractMultithreadedModule {
	public final static String CONFIG_GROUP = "TimeAllocationMutator";
	public final static String CONFIG_MUTATION_RANGE = "mutationRange";

	private double mutationRange = 1800.0;
	private boolean useActivityDurations = true;

	/**
	 * Creates a new TimeAllocationMutator with a mutation range as defined in
	 * the configuration (module "TimeAllocationMutator", param "mutationRange").
	 */
	public TransitTimeAllocationMutator(Config config) {
		super(config.global());
		this.mutationRange = config.timeAllocationMutator().getMutationRange() ;
		if ( config.vspExperimental().getActivityDurationInterpretation().equals( ActivityDurationInterpretation.minOfDurationAndEndTime) ) {
			useActivityDurations = true;
		} else if ( config.vspExperimental().getActivityDurationInterpretation().equals( ActivityDurationInterpretation.endTimeOnly ) ) {
			useActivityDurations = false;
		} else if ( config.vspExperimental().getActivityDurationInterpretation().equals( ActivityDurationInterpretation.tryEndTimeThenDuration ) ) {
			throw new UnsupportedOperationException("need to clarify the correct setting here.  Probably not a big deal, but not done yet.  kai, aug'10");
		} else {
			throw new IllegalStateException("beahvior not defined for this configuration setting");
		}
	}

	public TransitTimeAllocationMutator(Config config, final double mutationRange) {
		super(config.global());
		this.mutationRange = mutationRange;
	}

	@Override
	public PlanAlgorithm getPlanAlgoInstance() {
		TransitPlanMutateTimeAllocation pmta = new TransitPlanMutateTimeAllocation(this.mutationRange, MatsimRandom.getLocalInstance());
		pmta.setUseActivityDurations(this.useActivityDurations);
		return pmta;
	}

}

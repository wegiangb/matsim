package playground.mmoyo.PTRouter;

import java.util.ArrayList;
import java.util.List;

import org.matsim.api.basic.v01.Coord;
import org.matsim.api.basic.v01.Id;
import org.matsim.api.basic.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.basic.v01.IdImpl;
import org.matsim.core.config.Config;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.network.LinkImpl;
import org.matsim.core.network.NetworkLayer;
import org.matsim.core.network.NodeImpl;
import org.matsim.core.population.ActivityImpl;
import org.matsim.core.population.LegImpl;
import org.matsim.core.population.MatsimPopulationReader;
import org.matsim.core.population.PersonImpl;
import org.matsim.core.population.PlanImpl;
import org.matsim.core.population.PopulationImpl;
import org.matsim.core.population.PopulationWriter;
import org.matsim.core.population.routes.LinkNetworkRouteImpl;
import org.matsim.core.population.routes.NetworkRouteWRefs;
import org.matsim.core.router.util.LeastCostPathCalculator.Path;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.transitSchedule.api.TransitSchedule;

import playground.mmoyo.TransitSimulation.SimplifyPtLegs;
import playground.mmoyo.TransitSimulation.TransitRouteFinder;

/**
 * Reads a plan file, finds a PT connection between two acts creating new PT legs and acts between them
 * and writes a output_plan file
 */
public class PTActWriter {
	private PTValues ptValues;
	private final PopulationImpl population;
	private final String outputFile;
	private NodeImpl originNode;
	private NodeImpl destinationNode;
	private LinkImpl accessLink;
	private LinkImpl egressLink;
	private final double firstWalkRange;
	
	private NetworkLayer logicNet;
	private NetworkLayer plainNet;
	private PTRouter ptRouter;
	private LogicIntoPlainTranslator logicToPlainConverter;
	
	public PTActWriter(final LogicFactory logicFactory, final PTValues ptValues, final String configFile, final String plansFile, final String outputFile){
		this.outputFile= outputFile;
		this.ptValues= ptValues;
		this.logicNet= logicFactory.getLogicNet();
		//03dic no Plain net this.plainNet= logicFactory.getPlainNet();
		this.ptRouter = new PTRouter(logicNet);
		this.logicToPlainConverter = logicFactory.getLogicToPlainTranslator();
		this.firstWalkRange = ptValues.FIRST_WALKRANGE;
		
		Config config = new Config();
		config = Gbl.createConfig(new String[]{ configFile, "http://www.matsim.org/files/dtd/plans_v4.dtd"});
		
		this.population = new PopulationImpl();
		MatsimPopulationReader plansReader = new MatsimPopulationReader(this.population, this.plainNet);
		plansReader.readFile(plansFile);
	}
	
	public void simplifyPtLegs(){
		
		SimplifyPtLegs SimplifyPtLegs = new SimplifyPtLegs();
		
		for (Person person: population.getPersons().values()) {
			//if (true){ Person person = population.getPersons().get(new IdImpl("3937204"));
			System.out.println(person.getId());
			SimplifyPtLegs.run(person.getPlans().get(0));
		}
		
		System.out.println("writing output plan file..." + outputFile );
		new PopulationWriter(this.population).writeFile(outputFile);
		System.out.println("done");	
	}

	/**
	 * Shows in console the legs that are created between the plan activities 
	 */
	public void printPTLegs(final TransitSchedule transitSchedule){
		TransitRouteFinder transitRouteFinder= new TransitRouteFinder (transitSchedule);
		
		for (Person person: this.population.getPersons().values()) {
		//if (true){	
			//PersonImpl person = population.getPersons().get(new IdImpl("2180188"));   //2180188
	
			Plan plan = person.getPlans().get(0);
	 		ActivityImpl act1 = (ActivityImpl)plan.getPlanElements().get(0);
			ActivityImpl act2 = (ActivityImpl)plan.getPlanElements().get(2);
			List<Leg> legList = transitRouteFinder.calculateRoute (act1, act2, person);
			
			for (Leg leg : legList){
				NetworkRouteWRefs networkRoute = (NetworkRouteWRefs)leg.getRoute(); 
				System.out.println(" ");
				System.out.println(leg.toString());
				
				for (Node node : networkRoute.getNodes()){
					if (node != null) System.out.print(" " + node.getId() + " " );
				}
			}
		}
	}
	
	public void findRouteForActivities(){
		PopulationImpl newPopulation = new PopulationImpl();
		
		int numPlans=0;
		int found =0;
		int trips=0;
		int inWalkRange=0;
		int lessThan2Node =0;
		int nulls =0;
		
		List<Double> durations = new ArrayList<Double>();  
		
		for (Person person: this.population.getPersons().values()) {
			//if ( true ) {
			//PersonImpl person = population.getPersons().get(new IdImpl("905449")); // 5228308   5636428  2949483 
 			System.out.println(numPlans + " id:" + person.getId());
			Plan plan = person.getPlans().get(0);

			boolean first =true;
			boolean addPerson= true;
			ActivityImpl lastAct = null;       
			ActivityImpl thisAct= null;		 
			
			double startTime=0;
			double duration=0;
			
			PlanImpl newPlan = new PlanImpl(person);
			
			//for (PlanElement pe : plan.getPlanElements()) {   		//temporarily commented in order to find only the first leg
			for	(int elemIndex=0; elemIndex<3; elemIndex++){            //jun09  finds only
				PlanElement pe= plan.getPlanElements().get(elemIndex);  //jun09  the first trip
				if (pe instanceof ActivityImpl) {  				
					thisAct= (ActivityImpl) pe;					
					if (!first) {								
						Coord lastActCoord = lastAct.getCoord();
			    		Coord actCoord = thisAct.getCoord();
	
						trips++;
			    		double distanceToDestination = CoordUtils.calcDistance(lastActCoord, actCoord);
			    		if (distanceToDestination<= firstWalkRange){  //<- try without this.
			    		//if (true){
			    			newPlan.addLeg(walkLeg(lastAct,thisAct));
			    			inWalkRange++;
			    		}else{
				    		startTime = System.currentTimeMillis();
				    		Path path = ptRouter.findPTPath(lastActCoord, actCoord, lastAct.getEndTime());
				    		duration= System.currentTimeMillis()-startTime;
				    		if(path!=null){
				    			if (path.nodes.size()>1){
					    			found++;
				    				createWlinks(lastActCoord, path, actCoord);
				    			    durations.add(duration);
				    			    insertLegActs(path, lastAct.getEndTime(), newPlan);
				    				removeWlinks();
				    			}else{
				    				newPlan.addLeg(walkLeg(lastAct, thisAct));
				    				lessThan2Node++;
				    			}
				    		}else{
				    			newPlan.addLeg(walkLeg(lastAct,thisAct));
				    			nulls++;
				    		}
			    		}
					}
			    	thisAct.setLink(logicNet.getNearestLink(thisAct.getCoord()));

			    	newPlan.addActivity(newPTAct(thisAct.getType(), thisAct.getCoord(), thisAct.getLink(), thisAct.getStartTime(), thisAct.getEndTime()));
					lastAct = thisAct;
					first=false;
				}
			}

			if (addPerson){
				((PersonImpl) person).exchangeSelectedPlan(newPlan, true);
				((PersonImpl) person).removeUnselectedPlans();
				newPopulation.addPerson(person);
			}
			numPlans++;
		}//for person

		double startTime = System.currentTimeMillis();
		logicToPlainConverter.convertToPlain(newPopulation);
		System.out.println("translation lasted: " + (System.currentTimeMillis()-startTime));
		
		System.out.println("writing output plan file...");
		new PopulationWriter(newPopulation).writeFile(outputFile);
		System.out.println("Done");
		System.out.println("plans:        " + numPlans + "\n--------------");
		System.out.println("\nTrips:      " + trips +  "\nfound: "  +  found +  "\ninWalkRange:  "+ inWalkRange + "\nnulls:        " + nulls + "\nlessThan2Node:" + lessThan2Node);
		
		System.out.println("printing routing durations");
		double total=0;
		double average100=0;
		int x=1;
		for (double d : durations ){
			total=total+d;
			average100= average100 + d;
			if(x==100){
				System.out.println(average100/100);
				average100=0;
				x=0;
			}
			x++;
		}

		System.out.println("total " + total + " average: " + (total/durations.size()));
		
		/*
		// start the control(l)er with the network and plans as defined above
		Controler controler = new Controler(Gbl.getConfig(),net,(Population) newPopulation);
		// this means existing files will be over-written.  Be careful!
		controler.setOverwriteFiles(true);
		// start the matsim iterations (configured by the config file)
		controler.run();
		*/
		
		// This is to compare the travelTime of path with different cost calculations
		/*
		System.out.println ("---------------------------------------------");
		for(Map.Entry <Id,Double> entry: costMap.entrySet() ){
			Id idAgent = entry.getKey(); 
			Double travelTime = entry.getValue();
			System.out.println (idAgent + "-"  + Double.toString(travelTime));
		}
		*/
		
		
	}//createPTActs
	
	/**
	 * Cuts up the found path into acts and legs according to the type of links contained in the path
	 */
	public void insertLegActs(final Path path, double depTime, final PlanImpl newPlan){
		List<Link> routeLinks = path.links;
		List<Link> legRouteLinks = new ArrayList<Link>();
		double accumulatedTime=depTime;
		double arrTime;
		double legTravelTime=0;
		double legDistance=0;
		double linkTravelTime=0;
		double linkDistance=0;
		double walkTime=0;
		int linkIndex=1;
		LinkImpl lastLink = null;
		
		for(Link link2: routeLinks){
			LinkImpl link = (LinkImpl) link2;
			linkTravelTime=this.ptRouter.ptTravelTime.getLinkTravelTime(link,accumulatedTime);
			linkDistance = link.getLength();
			
			if (link.getType().equals("Standard")){
				if (!lastLink.getType().equals("Standard")){  //reset to start a new ptLeg
					legRouteLinks.clear();
					depTime=accumulatedTime;
					legTravelTime=0;
					legDistance=0;
				}
				legTravelTime=legTravelTime+linkTravelTime;
				legRouteLinks.add(link);
				if(linkIndex == (routeLinks.size()-1)){//Last PTAct: getting off
					arrTime= depTime+ legTravelTime;
					legDistance=legDistance + linkDistance;
					newPlan.addLeg(newPTLeg(TransportMode.car, legRouteLinks, legDistance, depTime, legTravelTime, arrTime)); //Attention: The legMode car is temporal only for visualization purposes
					newPlan.addActivity(newPTAct("exit pt veh", link.getToNode().getCoord(), link, arrTime, arrTime)); //describes the location
				}

			}else if(link.getType().equals("Transfer") ){  //add the PTleg and a Transfer Act
				//if (lastLink.getType().equals(STANDARD)){
					arrTime= depTime+ legTravelTime;
					legDistance= legDistance+ linkDistance;
					newPlan.addLeg(newPTLeg(TransportMode.car, legRouteLinks, legDistance, depTime, legTravelTime, arrTime)); //-->: The legMode car is temporal only for visualization purposes
					//newPlan.addAct(newPTAct("wait pt", link.getFromNode().getCoord(), link, accumulatedTime, linkTravelTime, accumulatedTime + linkTravelTime));
					double endTime = accumulatedTime + linkTravelTime;
					newPlan.addActivity(newPTAct("transf", link.getFromNode().getCoord(), link, accumulatedTime, endTime));
						
					/*
					////////////////////////////// find roundabout connections
					NodeImpl nodeA = path.nodes.get(0);
					NodeImpl nodeB = path.nodes.get(path.nodes.size()-1);
					double a_bDistance = CoordUtils.calcDistance(nodeA.getCoord() , nodeB.getCoord());
					double a_TransterDistance = CoordUtils.calcDistance(nodeA.getCoord() , link.getFromNode().getCoord());
					double b_TransterDistance = CoordUtils.calcDistance(nodeB.getCoord() , link.getFromNode().getCoord());
					if(a_TransterDistance > a_bDistance || b_TransterDistance > a_bDistance){
						PersonImpl detouredPerson = newPlan.getPerson();
						if (!detouredPopulation.getPersons().containsValue(deoturedPerson))
							detouredPopulation.addPerson(newPlan.getPerson());
					}
					///////////////////////////*/
					
				//}
			}else if (link.getType().equals("DetTransfer")){
				/**standard links*/
				arrTime= depTime+ legTravelTime;
				legDistance= legDistance + linkDistance;
				newPlan.addLeg(newPTLeg(TransportMode.car, legRouteLinks, legDistance, depTime, legTravelTime, arrTime));		
				
				/**act exit ptv*/
				newPlan.addActivity(newPTAct("transf off", link.getFromNode().getCoord(), link, arrTime, arrTime));
				
				/**like a Walking leg*/
				walkTime= linkDistance * ptValues.AV_WALKING_SPEED;
				legRouteLinks.clear();
				legRouteLinks.add(link);
				depTime=arrTime;
				arrTime= arrTime + walkTime;
				newPlan.addLeg(newPTLeg(TransportMode.walk, legRouteLinks, linkDistance, depTime, walkTime, arrTime));

				/**wait pt*/
				double endTime= depTime + linkTravelTime; // The ptTravelTime must be calculated like this: travelTime = walk + transferTime;
				newPlan.addActivity(newPTAct("transf on", link.getToNode().getCoord(), link, arrTime, endTime));
			
			}else if (link.getType().equals("Egress")){
				legRouteLinks.clear();
				legRouteLinks.add(link);
				arrTime= accumulatedTime+ linkTravelTime;
				newPlan.addLeg(newPTLeg(TransportMode.walk, legRouteLinks, linkDistance, accumulatedTime, linkTravelTime, arrTime));
			
			}else if (link.getType().equals("Access")){
					
				/**like a Walking leg*/
				walkTime= linkDistance * ptValues.AV_WALKING_SPEED;
				legRouteLinks.clear();
				legRouteLinks.add(link);
				depTime=accumulatedTime;
				arrTime = depTime + walkTime;
				newPlan.addLeg(newPTLeg(TransportMode.walk, legRouteLinks, linkDistance, depTime, walkTime, arrTime));

				/**wait pt*/
				double endTime= depTime + linkTravelTime; 
				newPlan.addActivity(newPTAct("wait pt", link.getToNode().getCoord(), link, arrTime, endTime));

			}		
					/*
					double waitTime  = ((PTTravelTime)ptRouter.ptTravelTime).transferTime(lastLink, accumulatedTime);					
					newPlan.addActivity(newPTAct("wait pt", link.getFromNode().getCoord(), link, accumulatedTime , accumulatedTime + waitTime));
					accumulatedTime = accumulatedTime + waitTime; 
					first=false;
					*/
			accumulatedTime =accumulatedTime+ linkTravelTime;
			lastLink = link;
			linkIndex++;
		}//for Link
	}//insert

	private ActivityImpl newPTAct(final String type, final Coord coord, final Link link, final double startTime, final double endTime){
		ActivityImpl ptAct= new ActivityImpl(type, coord, link);
		ptAct.setStartTime(startTime);
		ptAct.setEndTime(endTime);
		return ptAct;
	}

	private LegImpl newPTLeg(final TransportMode mode, final List<Link> routeLinks, final double distance, final double depTime, final double travTime, final double arrTime){
		NetworkRouteWRefs legRoute = new LinkNetworkRouteImpl(null, null); 
		
		if (mode!=TransportMode.walk){
			legRoute.setLinks(null, routeLinks, null);
		}else{
			//mode= TransportMode.car;   //-> temporarly for Visualizer
		}
		
		legRoute.setTravelTime(travTime);
		legRoute.setDistance(distance);
		LegImpl leg = new LegImpl(mode);
		leg.setRoute(legRoute);
		leg.setDepartureTime(depTime);
		leg.setTravelTime(travTime);
		leg.setArrivalTime(arrTime);
		return leg;
	}

	private LegImpl walkLeg(final ActivityImpl act1, final ActivityImpl act2){
		double distance= CoordUtils.calcDistance(act1.getCoord(), act2.getCoord());
		double walkTravelTime = distance * ptValues.AV_WALKING_SPEED;
		double depTime = act1.getEndTime();
		double arrTime = depTime + walkTravelTime;
		return newPTLeg(TransportMode.walk, new ArrayList<Link>(), distance, depTime, walkTravelTime, arrTime);
	}
	
	private void createWlinks(final Coord coord1, final Path path, final Coord coord2){
		originNode= createWalkingNode(new IdImpl("W1"), coord1);
		destinationNode= createWalkingNode(new IdImpl("W2"), coord2);
		path.nodes.add(0, originNode);
		path.nodes.add(destinationNode);
		accessLink = logicNet.createAndAddLink( new IdImpl("Access"), originNode, path.nodes.get(1), CoordUtils.calcDistance(originNode.getCoord(), path.nodes.get(1).getCoord()), 3600, 1, 1, "0", "Access"); 
		egressLink = logicNet.createAndAddLink( new IdImpl("Egress"), path.nodes.get(path.nodes.size()-2), destinationNode, CoordUtils.calcDistance(path.nodes.get(path.nodes.size()-2).getCoord(), destinationNode.getCoord()), 3600, 1, 1, "0", "Egress");
	}
	
	/**
	 * Creates a temporary origin or destination node
	 * avoids the method net.createNode because it is not necessary to rebuild the Quadtree*/
	public NodeImpl createWalkingNode(final Id id, final Coord coord){
		NodeImpl node = new Station(id, coord);
		logicNet.getNodes().put(id, node);
		return node;
	}
	
	public LinkImpl createPTLink999(final String strIdLink, final Node fromNode, final Node toNode, final String type){
		return logicNet.createAndAddLink( new IdImpl(strIdLink), (NodeImpl) fromNode, (NodeImpl) toNode, CoordUtils.calcDistance(fromNode.getCoord(), toNode.getCoord()), 3600, 1, 1, "0", type); 
	}
	
	private void removeWlinks(){
		logicNet.removeLink(accessLink);
		logicNet.removeLink(egressLink);
		//logicNet.removeNode(originNode);
		//logicNet.removeNode(destinationNode);
	}

}
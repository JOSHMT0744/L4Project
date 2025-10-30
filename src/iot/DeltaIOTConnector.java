package iot;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import deltaiot.client.SimulationClient;
import deltaiot.services.Link;
import deltaiot.services.LinkSettings;
import deltaiot.services.Mote;
import pomdp.POMDP;
import solver.BeliefPoint;

/**
 * The connector between the Simulator and the MAPE-K loop
 */
public class DeltaIOTConnector {
	
	public static POMDP p;
	
	
	//public static Probe probe;
	//public static Effector effector;
	public static Mote selectedmote;
	public static Link selectedlink;
	public static boolean refsetcreation=false;
	public static ArrayList<Mote> motes;
	
	public static SimulationClient networkMgmt;
	public static int timestepiot;
	//private static StopWatch stopwatchiot;
	public static Integer moteids[];
	public int selectedindex;
	
	public DeltaIOTConnector()
	{
		selectedindex=0;
		//stopwatchiot = StopWatch.getGlobalStopWatch();		
	}
	
	
	
//////checking with simulator
/*	public static void testIOTGUI(boolean dim3, 
			boolean type_none, boolean type_full, 
			boolean random_solver, boolean random_solver_without,
			long maxTime, int timeInterval, int bsetsize){
		
		
		
		ArrayList<SimpleVector<?>> refset;
		if(dim3)
			refset = readRefSetFromFile("iot3ref.txt"); 
		else 
			refset = readRefSetFromFile("C:\\Users\\160010321\\workspace\\MOPOMDP\\iot2ref.txt"); 
			//refset = readRefSetFromFile("iot2ref.txt"); 
		
		ReusingPerseusSolver.LOG = true;
		
		ArrayList<Long> times = new ArrayList<Long>(); 
		long time = 0; 
		while(time<maxTime){
			time+=timeInterval;
			times.add(time);
		}
		
		int repeat=1;
		
		int nSamples = 1000;
		int d3 = dim3? 3 : 2;
		//double[] b02 = {0.5, 0.5, 0}; double[] vc = {0.5, 0.5, 0};
		ArrayList<Long> endTimesNone = new ArrayList<Long>(repeat);
		ArrayList<Long> endTimesFull = new ArrayList<Long>(repeat);
		ArrayList<Long> endTimesCPS  = new ArrayList<Long>(repeat);
		ArrayList<Long> endTimesClose= new ArrayList<Long>(repeat);
		ArrayList<Long> endTimesRandom= new ArrayList<Long>(repeat);
		ArrayList<Long> endTimesRW= new ArrayList<Long>(repeat);
		
		ArrayList<Double> epsMeanNone = new ArrayList<Double>(repeat);
		ArrayList<Double> epsMeanFull = new ArrayList<Double>(repeat);
		ArrayList<Double> epsMeanCPS  = new ArrayList<Double>(repeat);
		ArrayList<Double> epsMeanClose= new ArrayList<Double>(repeat);
		ArrayList<Double> epsMeanRandom= new ArrayList<Double>(repeat);
		ArrayList<Double> epsMeanRW= new ArrayList<Double>(repeat);
		
		ArrayList<Double> epsStdvNone = new ArrayList<Double>(repeat);
		ArrayList<Double> epsStdvFull = new ArrayList<Double>(repeat);
		ArrayList<Double> epsStdvCPS  = new ArrayList<Double>(repeat);
		ArrayList<Double> epsStdvClose= new ArrayList<Double>(repeat);
		ArrayList<Double> epsStdvRandom= new ArrayList<Double>(repeat);
		ArrayList<Double> epsStdvRW= new ArrayList<Double>(repeat);
		
		////////////////////////////////////
		// need to assign current belief value
		/////////////////////////////////////
		IOTStates rs=new IOTStates();
		double[] b0=Mopomdp.uniformBelief(rs);
		
		MOIOT moiot = new MOIOT(dim3, b0, 0.9);
		ReusingPerseusSolver<Integer,Integer,Integer> reuser;
		LinearSupporter<Integer> lstst=null;
		ArrayList<ValueAtBeliefVector<Integer>> lst=null;
		String res="";
		String res_ValMec="";
		String res_ValMr="";
		String res_WtMec="";
		String res_WtMr="";
		String res_action="";
		
		Integer cstate=moiot.currentState();
try {
	FileWriter fw=new FileWriter("ResultsIOT.txt");
	PrintWriter pw=new PrintWriter(fw);
	FileWriter fw1=new FileWriter("ValMECIOT.txt");
	PrintWriter pw1=new PrintWriter(fw1);
	FileWriter fw2=new FileWriter("ValRPLIOT.txt");
	PrintWriter pw2=new PrintWriter(fw2);
	FileWriter fww1=new FileWriter("WtMECIOT.txt");
	PrintWriter pww1=new PrintWriter(fww1);
	FileWriter fww2=new FileWriter("WtRPLIOT.txt");
	PrintWriter pww2=new PrintWriter(fww2);
	FileWriter fwact=new FileWriter("selectedactionIOT.txt");
	PrintWriter pwact=new PrintWriter(fwact);
	
	for(int timestep=0;timestep<5;timestep++)
	{
		DeltaIOTConnector.timestepiot=timestep;
		System.out.println("timestep: "+timestep);
		
		DeltaIOTConnector.motes = DeltaIOTConnector.probe.getAllMotes();
		
		for (Mote mote : DeltaIOTConnector.motes) {
			DeltaIOTConnector.selectedmote=mote;
		
				
			
		
		if(type_full){
			ArrayList<ArrayList<Double>> epsCollection = new ArrayList<ArrayList<Double>>(repeat);
			for(int i=0; i<repeat; i++){
				DeltaIOTConnector.stopwatchiot.reset();
				System.out.println("running type full");
				ReusingPerseusSolver.resetMeasurement(true);
				if(timestep==0) {
					//cstate=mordm.currentState();
					reuser = new ReusingPerseusSolver<Integer,Integer,Integer>(moiot,b0,bsetsize,50,0.000001, ReusingPerseusSolver.TYPE_FULL);
				}
				else {
					//cstate=mordm.currentState();
					reuser = new ReusingPerseusSolver<Integer,Integer,Integer>(moiot,moiot.currentBelief(),bsetsize,50,0.000001, ReusingPerseusSolver.TYPE_FULL);
					
				}
				//reuser = new ReusingPerseusSolver<Integer,Integer,Integer>(mordm,b0,bsetsize,500,0.000001, ReusingPerseusSolver.TYPE_FULL);
				reuser.initSamples();
				lstst = new LinearSupporter<Integer>(reuser, 0.00000001, 0.00001, d3);
				reuser.setCPSreference(lstst);
				
				DeltaIOTConnector.stopwatchiot.start();
				lst = lstst.runLinearSupport(25000000);
				
				ArrayList<SimpleVector<?>> veccies = ReusingPerseusSolver.valuelist; 
				refset.addAll(veccies);
				ArrayList<Long> timez = ReusingPerseusSolver.timelist; 
				endTimesFull.add(timez.get(timez.size()-1));
				System.out.print("f");
				ArrayList<Double> epss = toEpsilon(veccies, refset);
				ArrayList<Double> epssbis = epsilonTimeVector(epss, timez, times);
				epsCollection.add(epssbis);	
				

				
				
				
			}
			for(int i=0; i<times.size(); i++){
				Tuple<Double,Double> meanStdv = getMeanStdDev(epsCollection, i);
				epsMeanFull.add(meanStdv.x);
				epsStdvFull.add(meanStdv.y);
			}
			System.out.println("+");
		} else {
			for(int i=0; i<times.size(); i++){
				epsMeanFull.add(0.0);
				epsStdvFull.add(0.0);
			}
		}
		
		
		if(timestep==0)//timestep check
		{
			
		//Display Epsilon Value (error value)
		System.out.println();
		for(int i=0; i<times.size(); i++){
			System.out.print(""+times.get(i)+" milliseconds ");
			System.out.print("Epsilon Mean: "+epsMeanFull.get(i)+", Epsilon Std Deviation: "+epsStdvFull.get(i)+",");
			
			System.out.println();
		}
		
		
		System.out.println();
		System.out.print("endFull <- c(");
		for(int i=0; i<endTimesFull.size(); i++){
			System.out.print(endTimesFull.get(i));
			if(i<endTimesFull.size()-1)
				System.out.print(",");
			else
				System.out.print(")");
		}
		}//end of timestep check
		
		res+="current belief: ";
		
		for(int i=0;i<moiot.currentBelief().length;i++)
		{
			res+=moiot.currentBelief()[i]+"  ";
			
		}
		res+="\n";
		
		
		
		//System.out.println("Value at Belief Vector size"+lst.size());
		
////////////////////////////
//////checking max value at belief vector
ArrayList<Double> maxxvall=new ArrayList<Double>();

for(int i=0;i<lstst.listmaxlnsupp.size();i++)
{

Double valvb=lstst.listmaxlnsupp.get(i).linearScalValue(lstst.listmaxlnsupp.get(i).weight);
maxxvall.add(valvb);

}

double maxValue2 = maxxvall.get(0);
for(int j=1;j < maxxvall.size();j++){
if(maxxvall.get(j) > maxValue2){
maxValue2 = maxxvall.get(j);
}
}

for(int i=0;i<maxxvall.size();i++)
{
if(maxValue2==maxxvall.get(i))
{ 

System.out.println("###############################################################");
System.out.println("selected weights :"+lstst.listmaxlnsupp.get(i).weight[0]+" "+lstst.listmaxlnsupp.get(i).weight[1]);
System.out.println("selected action    tag :"+lstst.listmaxlnsupp.get(i).retrieveTags().get(0));
System.out.println("selected value    valuevec:"+lstst.listmaxlnsupp.get(i).getValue()[0]+" "+lstst.listmaxlnsupp.get(i).getValue()[1]);
System.out.println("###############################################################");


res+="Time Step: "+timestep+" ";
res_ValMec+=timestep+" ";
res_ValMr+=timestep+" ";
res_WtMec+=timestep+" ";
res_WtMr+=timestep+" ";
res_action+=timestep+" ";

moiot.currentState=cstate.intValue();

res+="Current State: "+moiot.currentState()+"  ";
//res+="Selected Action: "+lstst.listmaxlnsupp.get(i).retrieveTags().get(0).toString()+" ";


System.out.println("hello Current State: "+moiot.currentState());
if(lstst.listmaxlnsupp.get(i).retrieveTags().get(0)!=null)
{
res+="Selected Action: "+lstst.listmaxlnsupp.get(i).retrieveTags().get(0).toString()+" ";
res_action+=lstst.listmaxlnsupp.get(i).retrieveTags().get(0).toString();

moiot.performAction(lstst.listmaxlnsupp.get(i).retrieveTags().get(0));

}
System.out.println("hello new current state"+moiot.currentState());
res+="Next State: "+moiot.currentState()+"  ";
cstate=moiot.currentState();

res+="value vector:"+lstst.listmaxlnsupp.get(i).getValue()[0]+" "+lstst.listmaxlnsupp.get(i).getValue()[1]+" ";
res_ValMec+=lstst.listmaxlnsupp.get(i).getValue()[0];
res_ValMr+=lstst.listmaxlnsupp.get(i).getValue()[1];

res+="selected weights"+lstst.listmaxlnsupp.get(i).weight[0]+" "+lstst.listmaxlnsupp.get(i).weight[1]+" ";
res_WtMec+=lstst.listmaxlnsupp.get(i).weight[0];
res_WtMr+=lstst.listmaxlnsupp.get(i).weight[1];

pw.println(res);
pw1.println(res_ValMec);
pw2.println(res_ValMr);
pww1.println(res_WtMec);
pww2.println(res_WtMr);
pwact.println(res_action);
res="";
res_action="";
res_ValMec="";
res_ValMr="";
res_WtMec="";
res_WtMr="";
}

}
System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
System.out.println("vb scalarized maxValuelsssssss:  "+maxValue2);

System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");

lstst.listmaxlnsupp.clear();
maxxvall.clear();

		
		
		
		
		///////////Just to display values
		////Code to print values of the ValueAtBelief Vector ArrayList
		//System.out.println("size   "+lst.size());
		//////////////////////////////////////////////////////////////////////////*************
		
		

		//////~~~~~~~~~~double scalarizedvals[]=new double[lst.size()];
		///////~~~for(int i=0;i<lst.size();i++)
		//////~#############{
			//System.out.println("Size of alpha matrix");
			//System.out.println(lst.get(i).alphas.size());
			//System.out.println("Value for state 0 and objective 0 in alpha matrix");
			//for(int j=0;j<lst.get(i).alphas.size();j++)
			//{ 
				//System.out.println("alpha matrix# "+j+"####################");
				/*for(int state=0;state<20;state++)
				{
					for(int obj=0;obj<2;obj++)
					{
						//System.out.println("Value for state"+state+ " and objective "+obj+" in alpha matrix");
						System.out.print(lst.get(0).alphas.get(0).getValue(state, obj)+"  ");
						
					}
					System.out.println("");
				}
			}*/
			
			//new comment
			///////////////////*****************************
			/*System.out.println("\n\nweight length :"+lst.get(i).weight.length +" weight values  "+lst.get(i).weight[0]+"    "+lst.get(i).weight[1]);
			//System.out.println("Simple Vector values"+lst.get(i).getValue().length);
			System.out.println("Values");
			System.out.println(lst.get(i).getValue()[0]+"    "+lst.get(i).getValue()[1]);
			
			System.out.println("Action Tag");
			System.out.println(lst.get(i).retrieveTags().size()+"   "+ lst.get(i).retrieveTags().toString());
			*/
			/////////////////******************
			
			//System.out.println("current state: "+mordm.currentState());
			//RDMTransitions mt=new RDMTransitions();
			//Integer nxtst=mt.nextState(mordm.currentState(), lst.get(0).retrieveTags().get(0));
			//System.out.println("next state:"+nxtst);
			
			//Scalarization with weights
			
			/////////~~~~~~~~~~~~scalarizedvals[i]=lst.get(i).linearScalValue(lst.get(i).weight);
			//System.out.println("##########################################\n");
			//System.out.println("scalarized value    "+ scalarizedvals[i]);
			//System.out.println("\n##########################################\n");
			
			
			//////////~~~~~~~~~~~~~~~~~~~~}
		
		/*for(int i=0;i<scalarizedvals.length;i++)
		{
			System.out.println(i+": scalarized value: "+scalarizedvals[i]);
		}*/
		
	/*	double maxValue = scalarizedvals[0];
		  for(int j=1;j < scalarizedvals.length;j++){
		    if(scalarizedvals[j] > maxValue){
			  maxValue = scalarizedvals[j];
			}
		  }
		  
		System.out.println("maxValue:  "+maxValue);
		
		
		 for(int j=1;j < scalarizedvals.length;j++){
			    if(scalarizedvals[j] == maxValue){
			       res+="Time Step: "+timestep+" ";
			       mordm.currentState=cstate.intValue();
			       res+="Current State: "+mordm.currentState()+"  ";
			       res+="Selected Action: "+lst.get(j).retrieveTags().get(0).toString()+" ";
			       
			       //mordm.currentState=cstate.intValue();
			       
			      System.out.println("Value Vector: "+lst.get(j).getValue()[0]+"  "+lst.get(j).getValue()[1]);
			      
			      //System.out.println("current state"+mordm.currentState());
				  System.out.println("The selected action is:"+lst.get(j).retrieveTags().get(0).toString());
				  System.out.println("The ccurrent weights :"+lst.get(j).weight[0]+"  "+lst.get(j).weight[1]);
				  mordm.performAction(lst.get(j).retrieveTags().get(0));
				  System.out.println("new current state"+mordm.currentState());
				  cstate=mordm.currentState();
				  res+=" Next State: "+mordm.currentState()+" ";
				  res+="Value Vector: "+lst.get(j).getValue()[0]+"  "+lst.get(j).getValue()[1]+" \n ";
				  
				  pw.println(res);
				  res="";
				  
				}
			  }*/
		 /*///////////////////////////***********************************
		 	///////////////////////////////////////********************
		/*for(int timestep=0;timestep<5;timestep++) 
		{	
			System.out.println("\n\ntimestep  "+timestep);
		 /////////////////////////////////////////////////////
		 ///Executing for next action
		 ReusingPerseusSolver<Integer,Integer,Integer> reuser1 = 
					new ReusingPerseusSolver<Integer,Integer,Integer>(mordm,mordm.currentBelief(),bsetsize,50,0.000001, ReusingPerseusSolver.TYPE_FULL);
			reuser1.initSamples();
			int d33 = dim3? 3 : 2;
			
			//@param Reusing Perseus Solver, precision, stopcrit, number of objectives
			LinearSupporter<Integer> lstst1 = new LinearSupporter<Integer>(reuser1, 0.00000001, 0.00001, d33);
			reuser1.setCPSreference(lstst1);
			ArrayList<ValueAtBeliefVector<Integer>> lst1 = lstst1.runLinearSupport(600000);
			
			double scalarizedvals1[]=new double[lst1.size()];
			double maxValue1 = scalarizedvals1[0];
			  for(int j=1;j < scalarizedvals1.length;j++){
			    if(scalarizedvals1[j] > maxValue1){
				  maxValue1 = scalarizedvals1[j];
				}
			  }
			System.out.println("maxValue:  "+maxValue1);
			 for(int j=1;j < scalarizedvals1.length;j++){
				    if(scalarizedvals1[j] == maxValue1){
				    	if(lst1.get(j).retrieveTags().get(0)!=null)
				    	{
				    		System.out.println("The selected action is:"+lst1.get(j).retrieveTags().get(0));
				    		mordm.performAction(lst1.get(j).retrieveTags().get(0));
				    		System.out.println("new current state"+mordm.currentState());
				    	}
					  
					  
					  
					}
				  }
			
		}*/
		 /////////////////////////////////////////////////////
			
	//}///end of time step for loop	
			
	/*	}// motes for loop	
		DeltaIOTConnector.timestepiot++;
}//timestep for loop
	
	ArrayList<QoS> result = DeltaIOTConnector.networkMgmt.getNetworkQoS(3);
	
	System.out.println("Run, PacketLoss, EnergyConsumption");
	result.forEach(qos -> System.out.println(qos)); 
	pw.flush();
	pw1.flush();
	pw2.flush();
	pww1.flush();
	pww2.flush();
	pwact.flush();
	
}catch(IOException ioex)
{
	  ioex.printStackTrace();

}		
}


	/*public static ArrayList<SimpleVector<?>> readRefSetFromFile(String filename){
		ArrayList<String> sVecs = new ArrayList<String>();

	    // wrap a BufferedReader around FileReader
	    BufferedReader bufferedReader;
		try {
			bufferedReader = new BufferedReader(new FileReader(filename));
			while (bufferedReader.ready())
		    {
		    	String line = bufferedReader.readLine();
		        sVecs.add(line);
		    }
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}

		ArrayList<SimpleVector<?>> refSet = new ArrayList<SimpleVector<?>>(sVecs.size());
		for(int i=0; i<sVecs.size(); i++){
			SimpleVector<Integer> sv = new SimpleVector<Integer>(sVecs.get(i));
			refSet.add(sv);
		}
		
		return refSet;
	}	
	
	
	 private static Tuple<Double,Double> getMeanStdDev(ArrayList<ArrayList<Double>> data, int index)
	    {
	    	Tuple<Double,Double> tup = getMeanVariance(data, index);
	        return new Tuple<Double,Double>(tup.x,Math.sqrt(tup.y));
	    }
		
		public static ArrayList<Double> toEpsilon(ArrayList<SimpleVector<?>> vecs, ArrayList<SimpleVector<?>> refSet){
			ArrayList<Double> result = new ArrayList<Double>();
			ArrayList<SimpleVector<?>> part = new ArrayList<SimpleVector<?>>(vecs.size());
			for(int i=0; i<vecs.size(); i++){
				part.add(vecs.get(i));
				result.add(compareToRefSet(part, refSet));
			}
			return result;
		}
		
		public static ArrayList<Double> epsilonTimeVector(ArrayList<Double> eps, ArrayList<Long> fromTimes, ArrayList<Long> toTimes){
			ArrayList<Double> result = new ArrayList<Double>(toTimes.size());
			long cTime; 
			int index = 0;
			for(int i=0; i<toTimes.size(); i++){
				cTime = toTimes.get(i);
				boolean b = true;
				while(b){
					if(index==(eps.size()-1)){
						b=false;
					} else {
						if(fromTimes.get(index+1)<cTime){
							index++;
						} else {
							b=false;
						}
					}
				}
				result.add(eps.get(index).doubleValue());
			}
			return result;
		}
		
		private static double getMean(ArrayList<ArrayList<Double>> data, int index)
	    {
	        double sum = 0.0;
	        for(ArrayList<Double> a  : data)
	            sum += a.get(index);
	        return sum/data.size();
	    }
		
		private static Tuple<Double,Double> getMeanVariance(ArrayList<ArrayList<Double>> data, int index)
	    {
	        double mean = getMean(data, index);
	        double temp = 0;
	        for(ArrayList<Double> a : data)
	            temp += (mean-a.get(index))*(mean-a.get(index));
	        double var = temp/(data.size());
	        return new Tuple<Double,Double>(mean,var);
	    }
		
		public static double compareToRefSet(ArrayList<SimpleVector<?>> vecs, ArrayList<SimpleVector<?>> refSet){
			CPruner<SimpleVector<?>> cpr = new CPruner<SimpleVector<?>>();
			double highestLoss = Double.NEGATIVE_INFINITY;
			for(int i=0; i<refSet.size(); i++){
				double imp = cpr.findImprovement(refSet.get(i), vecs);
				if(imp>highestLoss){
					highestLoss = imp;
				}
			}
			return highestLoss;
		}
		
		
		public static void start()
		{
			DeltaIOTConnector.networkMgmt = new SimulationClient();
			// get probe and effectors
			Probe probe = DeltaIOTConnector.networkMgmt.getProbe();
			Effector effector = DeltaIOTConnector.networkMgmt.getEffector();
			DeltaIOTConnector.probe=probe;
			DeltaIOTConnector.effector=effector;
			DeltaIOTConnector.testIOTGUI(false, false, true, false, false, 25, 6, 5);
		}
	*/
	
	
	
	/*public Mote getMoteById(Integer moteid)
	{
		ArrayList<Mote> motelist = DeltaIOTConnector.networkMgmt.getProbe().getAllMotes();
		 for (int i=0;i<motelist.size();i++)
		 {
			 Mote m=(Mote)motelist.get(i);
			 if(m.getMoteid().equals(moteid))
			 {
				// System.out.println(moteid+"moteiddddddddddddddddddddddddd");
				 return m;
			 }
			 
		 }
		
		return null;
		
	}*/
	
	/*public Integer[] getAllMoteIds()
	{
		Integer[] moteidss;
		ArrayList<Mote> motesforids = DeltaIOTConnector.networkMgmt.getProbe().getAllMotes();
		moteidss=new Integer[motesforids.size()];
		for (int i=0;i<motesforids.size();i++) {
			moteidss[i]=motesforids.get(i).getMoteid();
		}
		return moteidss;
		
	}*/
	
	public List<Link> getSelectedMoteLinks(Mote selectedmote)
	{
		List<Link> l=selectedmote.getLinks();
		return l;
		
	}

	public int getObservation()
	{
		System.out.println("observation function: mote no:  "+DeltaIOTConnector.selectedmote.getMoteid());
		//for (Link link : DeltaIOTConnector.selectedmote.getLinks()) {
		for (Link link : DeltaIOTConnector.selectedmote.getLinks()) {	
			System.out.println("SNR: "+link.getSNR());
			System.out.println("Distribution factor:"+link.getDistribution());
		//if (link.getSNR() > 0 && link.getPower()>0) {
			if (link.getSNR() > 0) {
				//DeltaIOTConnector.selectedmote=m;
				//DeltaIOTConnector.selectedlink=link;
				return 2;
			}
			else if (link.getSNR() == 0) {
				//DeltaIOTConnector.selectedmote=m;
				//DeltaIOTConnector.selectedlink=link;
				return 1;
			}
			//else if (link.getSNR()<0 && link.getPower()<15) {
			else if (link.getSNR() <0) {
				//DeltaIOTConnector.selectedmote=m;
				//DeltaIOTConnector.selectedlink=link;
				return 0;
			}
		}
		
		return 0;
	}
	
	public int performAction(int action) {
		////Perform ITP or DTP on the link on the simulator
		///return rewards and observations
		//update belief value and change initial belief
		
		///Immediate Reward
		//double reward = p.getReward(p.getCurrentState(), action);
		int nextstate;

		// Depending on if action==0 or 1, it will perform either DTP ITP
		nextstate = p.nextState(p.getCurrentState(), action);
		p.setCurrentState(nextstate);
		
		///Observation
		int obs = p.getObservation(action, nextstate);
		// Despite being called "Initial" belief, consider this the current belief
		BeliefPoint b=p.updateBelief(p.getInitialBelief(), action, obs);
		p.setInitialBelief(b);
		
		//p.getReward(s, action);
		
		/*S currentS  = states.stateIdentifier(currentState);
		
		S nextState = this.transitions.nextState(currentS, action);
		
		this.currentState = states.stateNumber(nextState);
		
		
		double[] reward = this.rewards.getReward(currentS, action, nextState);
		
		
		
			O obs = this.observationFunction.getObservation(action, nextState);
			
			this.beliefUpdate(action, obs);*/
	
		return 0;
	}

	
	///SF Check
	public void performDTP() { 		
		int value;
		Link left, right;
		int valueleft,valueright;
	
	
	
			//for (Link link : mote.getLinks()) {
				
				//if (link.getSNR() > 0 && link.getPower() > 0) {
					//value=link.getPower()-1;
					//List<LinkSettings> newSettings=new LinkedList<LinkSettings>();
					//newSettings.add(new LinkSettings(mote.getMoteid(), link.getDest(), value, link.getDistribution(), link.getSF()));
		
					//DeltaIOTConnector.networkMgmt.getEffector().setMoteSettings(mote.getMoteid(),newSettings);
			
				//} else 
	//for(Mote m:DeltaIOTConnector.motes) {
		//if(m.getLinks().size()==1)
		//{
		for(Link l : DeltaIOTConnector.selectedmote.getLinks()) {
			//DeltaIOTConnector.selectedlink=m.getLink(0);
			DeltaIOTConnector.selectedlink = l;
				if (DeltaIOTConnector.selectedlink.getSNR() > 0 && DeltaIOTConnector.selectedlink.getPower() >0) {
					
				
					value=DeltaIOTConnector.selectedlink.getPower()-1;
					int valueSF=DeltaIOTConnector.selectedlink.getSF();
					if(valueSF > 7) {
						//System.out.println(valueSF+"       "+value+"~~~~~~~~~~~~~");
						valueSF=DeltaIOTConnector.selectedlink.getSF()-1;
						//System.out.println(valueSF+"       "+value+"~~~~~~~~~~~~~");
					}
					List<LinkSettings> newSettings=new LinkedList<LinkSettings>();
					newSettings.add(new LinkSettings(DeltaIOTConnector.selectedmote.getMoteid(), DeltaIOTConnector.selectedlink.getDest(), value, DeltaIOTConnector.selectedlink.getDistribution(), valueSF));
		
					DeltaIOTConnector.networkMgmt.getEffector().setMoteSettings(DeltaIOTConnector.selectedmote.getMoteid(),newSettings);	
				}
			}
	//}
			
				
		for (Mote mote : DeltaIOTConnector.motes) {
			if(mote.getLinks().size() > 1) {
				
				left =mote.getLinks().get(0);
				right = mote.getLinks().get(1);
				if (left.getPower() != right.getPower()) {
					// If distribution of all links is 100 then change it to 50
					// 50
					if (left.getDistribution() == 100 && right.getDistribution() == 100) {
						left.setDistribution(50);
						right.setDistribution(50);
					}
					if (left.getPower() > right.getPower() && left.getDistribution() < 100) {
						valueleft=left.getDistribution() + 10;
						 valueright=right.getDistribution() - 10;
						 left.setDistribution(valueleft);
						 right.setDistribution(valueright);
					} else if (right.getDistribution() < 100) {
						valueright=right.getDistribution() + 10;
						valueleft=left.getDistribution() - 10;
						left.setDistribution(valueleft);
						right.setDistribution(valueright);
						}
					}
				}
			}
		}
	
	
	
	///perform actions for simulator DeltaIOT
	public void performITP() { 	
		int value;
		Link left, right;
		int valueleft,valueright;
			//for (Link link : mote.getLinks()) {
				
				//if (link.getSNR() > 0 && link.getPower() > 0) {
					//value=link.getPower()-1;
					//List<LinkSettings> newSettings=new LinkedList<LinkSettings>();
					//newSettings.add(new LinkSettings(mote.getMoteid(), link.getDest(), value, link.getDistribution(), link.getSF()));
		
					//DeltaIOTConnector.networkMgmt.getEffector().setMoteSettings(mote.getMoteid(),newSettings);
					
				//} else 
			//for(Mote m:DeltaIOTConnector.motes) {
				//if(m.getLinks().size()==1)
				//{
				for(Link l : DeltaIOTConnector.selectedmote.getLinks()) {
				
					//DeltaIOTConnector.selectedlink=m.getLink(0);
					DeltaIOTConnector.selectedlink = l;
					
					// SNR = Signal to Noise Ratio -> used as a basis for adjusting transmission power
					// If SNR < 0, the logic increases the power, if SNR > 0, decrease power
					// Goal: keep SNR at a level where packets aren't lost but without wasting energy
					if (DeltaIOTConnector.selectedlink.getSNR() < 0 && DeltaIOTConnector.selectedlink.getPower() < 15) {
						//DeltaIOTConnector.selectedlink=l;
					
						value=DeltaIOTConnector.selectedlink.getPower()+1;
						int valueSF=DeltaIOTConnector.selectedlink.getSF();
						if(valueSF<12)
						{
						valueSF=DeltaIOTConnector.selectedlink.getSF()+1;
						}
						List<LinkSettings> newSettings=new LinkedList<LinkSettings>();
						newSettings.add(new LinkSettings(DeltaIOTConnector.selectedmote.getMoteid(), DeltaIOTConnector.selectedlink.getDest(), value, DeltaIOTConnector.selectedlink.getDistribution(), valueSF));
			
						DeltaIOTConnector.networkMgmt.getEffector().setMoteSettings(DeltaIOTConnector.selectedmote.getMoteid(),newSettings);
						
					}
				}
	//}

			//}
			//}
		//}
		for (Mote mote : DeltaIOTConnector.motes) {
			if(mote.getLinks().size() == 2) {
				
				left =mote.getLinks().get(0);
				right = mote.getLinks().get(1);
				if (left.getPower() != right.getPower()) {
					// If distribution of all links is 100 then change it to 50
					// 50
					if (left.getDistribution() == 100 && right.getDistribution() == 100) {
						left.setDistribution(50);
						right.setDistribution(50);
					}
					if (left.getPower() > right.getPower() && left.getDistribution() < 100) {
						valueleft=left.getDistribution() + 10;
						 valueright=right.getDistribution() - 10;
						 left.setDistribution(valueleft);
						 right.setDistribution(valueright);
					} else if (right.getDistribution() < 100) {
						valueright=right.getDistribution() + 10;
						valueleft=left.getDistribution() - 10;
						left.setDistribution(valueleft);
						right.setDistribution(valueright);
						}
					}
				}
			}
		}
	}


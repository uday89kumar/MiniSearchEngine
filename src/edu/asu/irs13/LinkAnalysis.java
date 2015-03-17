package edu.asu.irs13;

import java.io.*;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.Map.Entry;

import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.FSDirectory;

public class LinkAnalysis {

	public static final String linksFile = "IntLinks.txt";
	public static final String citationsFile = "IntCitations.txt";
	public static int numDocs = -1;
	private static double startTime = 0.0;
	private static double endTime = 0.0;
	private int[][] links;
	private int[][] citations;
	private static LinkedHashMap<Integer,Double> rootSet = new LinkedHashMap<Integer, Double>();
	private static IndexReader ir;
	private static LinkedHashMap<Integer,Double> authorities = new LinkedHashMap<Integer,Double>();
	private static LinkedHashMap<Integer,Double> hubs = new LinkedHashMap<Integer,Double>();

	public LinkAnalysis()
	{
		try
		{
			ir = IndexReader.open(FSDirectory.open(new File("index")));
			numDocs = ir.maxDoc();
			TFWeights.setK(10);
			// Read in the links file
			links = new int[numDocs][];
			BufferedReader br = new BufferedReader(new FileReader(linksFile));
			String s = "";
			while ((s = br.readLine())!=null)
			{
				String[] words = s.split("->"); // split the src->dest1,dest2,dest3 string
				int src = Integer.parseInt(words[0]);
				if (words.length > 1 && words[1].length() > 0)
				{
					String[] dest = words[1].split(",");
					links[src] = new int[dest.length];
					for (int i=0; i<dest.length; i++)
					{
						links[src][i] = Integer.parseInt(dest[i]);
					}
				}
				else
				{
					links[src] = new int[0];
				}
			}
			br.close();

			// Read in the citations file
			citations = new int[numDocs][];
			br = new BufferedReader(new FileReader(citationsFile));
			s = "";
			while ((s = br.readLine())!=null)
			{
				String[] words = s.split("->"); // split the src->dest1,dest2,dest3 string
				int src = Integer.parseInt(words[0]);
				if (words.length > 1 && words[1].length() > 0)
				{
					String[] dest = words[1].split(",");
					citations[src] = new int[dest.length];
					for (int i=0; i<dest.length; i++)
					{
						citations[src][i] = Integer.parseInt(dest[i]);
					}
				}
				else
				{
					citations[src] = new int[0];
				}

			}
			br.close();
		}
		catch(NumberFormatException e)
		{
			System.err.println("links file is corrupt: ");
			e.printStackTrace();			
		}
		catch (CorruptIndexException e) {
			System.out.println("CorruptIndexException : "+ e.getMessage());
			e.printStackTrace();
		}
		catch(IOException e)
		{
			System.err.println("Failed to open links file: ");
			e.printStackTrace();
		}
	}

	public int[] getLinks(int docNumber)
	{
		return links[docNumber];
	}

	public int[] getCitations(int docNumber)
	{
		return citations[docNumber];
	}

	public static void main(String[] args)
	{
		LinkedHashSet<Integer> linkSet = new LinkedHashSet<Integer>();
		LinkedHashSet<Integer> citSet = new LinkedHashSet<Integer>();
		LinkedHashSet<Integer> dimensions = new LinkedHashSet<Integer>();

		LinkAnalysis l = new LinkAnalysis();
		LinkAnalysis.numDocs = ir.maxDoc();
		
		startTime = System.currentTimeMillis();
		TFWeights.preComputeNorm(ir);
		endTime = System.currentTimeMillis();
		System.out.println("Time taken to process query : " + (endTime-startTime)/1000 + "(s)");
		@SuppressWarnings("resource")
		Scanner sc = new Scanner(System.in);
		String str = "";
		System.out.print("query> ");
		while(!(str = sc.nextLine()).equals("quit"))
		{
			startTime = System.currentTimeMillis();
			String[] terms = str.split("\\s+");
			TFWeights.tfidf = new LinkedHashMap<Integer,Double>();
			//Compute the cosine similarity of the given keywords
			rootSet = TFWeights.computeCosineSimilarity(ir, terms, Integer.parseInt(args[0]));
			endTime = System.currentTimeMillis();
			System.out.println("Time taken to process query : " + (endTime-startTime) + "(ms)");
			if(!TFWeights.flag){
				Iterator<Entry<Integer, Double>> it = rootSet.entrySet().iterator();
				int count = 0;
				while(it.hasNext()){
					Map.Entry<Integer,Double> pairs = (Entry<Integer, Double>)it.next();
					int docID = pairs.getKey();
					int []links = l.getLinks(docID);
					for(int pb:links)
					{
						linkSet.add(pb);
					}
					int []cit = l.getCitations(docID);
					for(int pb:cit)
					{
						citSet.add(pb);
					}
					dimensions.add(docID);
					count++;
					if(count > TFWeights.getK()){
						break;
					}
				}
				/*//Printing Links
				System.out.println("Link");
				for(int t: linkSet){
					System.out.println(t);
				}
				
				//Printing Citations
				System.out.println("Citation");
				for(int t:citSet){
					System.out.println(t);
				}*/
				
				dimensions.addAll(linkSet);
				dimensions.addAll(citSet);

				//Union of Links/Citation for Adjacency Matrix
				//System.out.println("Total Dimensions :" + dimensions.size());
				int []dim = new int[dimensions.size()];
				
				//Printing Dimensions
				int i = 0;
				for(Integer temp: dimensions){
					dim[i] = temp;
					i++;
					//System.out.println(temp);
				}
				
				//Setting Authorities and Hub default values.
				for(int temp: dimensions){
					authorities.put(temp, 1.0);
					hubs.put(temp,1.0);
				}
				//Compute HubsandAuthorities
				computeAuthoritiesAndHub(dim, l);
			}
			//Resetting all variables for the next query
			resetVariables();
			citSet = new LinkedHashSet<Integer>();
			linkSet = new LinkedHashSet<Integer>();
			dimensions = new LinkedHashSet<Integer>();
			System.out.print("query> ");
		}
	}

	private static void resetVariables(){
		TFWeights.flag = false;
		rootSet = new LinkedHashMap<Integer,Double>();
		authorities = new LinkedHashMap<Integer,Double>();
		hubs = new LinkedHashMap<Integer,Double>();
	}
	private static void computeTop10(LinkedHashMap<Integer,Double> map, String msg){
		LinkedHashMap<Integer,Double> sortedMap = (LinkedHashMap<Integer, Double>) TFWeights.sortByValue(map, 1);
		display(sortedMap,msg,TFWeights.getK());
	}
	
	@SuppressWarnings("unused")
	private static void computeAuthoritiesAndHub(int []dimensions, LinkAnalysis l){
		try {
			int [][] adjMatrix = computeAdjacencyMatrix(dimensions, l);
			int [][] transposeAdjMatrix = computeTransposeofMatrix(adjMatrix);
			double[] operationO = new double[dimensions.length];
			double[] operationI = new double[dimensions.length];
			LinkedHashMap<Integer,Double> oldAuthorities = new LinkedHashMap<Integer,Double>();
			LinkedHashMap<Integer,Double> oldHubs = new LinkedHashMap<Integer,Double>();
			for(int temp:dimensions){
				oldAuthorities.put(temp,0.0);
				oldHubs.put(temp,0.0);
			}
			int count = 0;
			
			do{
				double norm = 0.0;
				for(int temp: dimensions){
					authorities.put(temp, 0.0);
					for(int incoming: l.getCitations(temp)){
						if(authorities.containsKey(temp) && hubs.containsKey(incoming)){
							Double value = authorities.get(temp) + hubs.get(incoming);
							authorities.put(temp, value);
						}
					}
					norm += Math.pow(authorities.get(temp),2);
				}
				norm = Math.sqrt(norm);
				//Compute Operation I -> ai = A(t) hi-1
				operationI = computeScore(transposeAdjMatrix,convertMapToArray(hubs, dimensions.length)); 
				for(int temp:dimensions){
					double val = authorities.get(temp)/norm;
					authorities.put(temp, val);
				}
				norm = 0.0;
				for(int temp: dimensions){
					hubs.put(temp, 0.0);
					for(int outgoing: l.getLinks(temp)){
						if(hubs.containsKey(temp) && authorities.containsKey(outgoing)){
							Double value = hubs.get(temp) + authorities.get(outgoing);
							hubs.put(temp, value);
						}
					}
					norm += Math.pow(hubs.get(temp), 2);
				}
				norm = Math.sqrt(norm);
				//Compute Operation O -> hi = A * ai-1
				operationO = computeScore(adjMatrix,convertMapToArray(authorities, dimensions.length));
				for(int temp:dimensions){
					double val = hubs.get(temp)/norm;
					hubs.put(temp, val);
				}
				count++;
				if(checkForConvergence(convertMapToArray(authorities, dimensions.length), convertMapToArray(oldAuthorities, dimensions.length), 0.0000001, dimensions.length)
					&& checkForConvergence(convertMapToArray(hubs, dimensions.length), convertMapToArray(oldHubs, dimensions.length), 0.0000001, dimensions.length)){
					break;
				}
				else {
					for(int temp:dimensions){
						oldAuthorities.put(temp, authorities.get(temp));
						oldHubs.put(temp, hubs.get(temp));
					}
				}
			}while(true);
			
			System.out.println("Iterations : "  + count);
			//Printing OperationI/OperationO and Authorities/Hubs
			computeTop10(authorities, "################\n Authorities \n################");
			computeTop10(hubs,"################\n Hubs \n################");
			//display(operationI, "####\n Operation I", dimensions);
			//display(operationO, "####\n Operation O", dimensions);
		}
		catch(Exception e){
			System.out.println("Exception : " + e.getMessage());
		}
	}

	public static boolean checkForConvergence(double[] prevUq, double[] neighbourWalk, double d, int length) {
		double[] res = new double[length];
		for(int i = 0; i < length; i++){
			res[i] = (float)(Math.pow(neighbourWalk[i], 2) - Math.pow(prevUq[i], 2));
		}
		double sumAll = 0;
		for(int i = 0; i < length; i++){
			sumAll = sumAll + res[i];
		}
		sumAll = (double)Math.sqrt(sumAll);
		if(sumAll < d)
			return true;
		return false;
	}
	
	public static void display(LinkedHashMap<Integer,Double> map, String msg){
		System.out.println(msg);
		display(map,msg,Integer.MAX_VALUE);
	}
	
	public static void display(LinkedHashMap<Integer,Double> map, String msg, int limit){
		System.out.println(msg);
		int i = 0;
		Iterator<Entry<Integer, Double>> it = map.entrySet().iterator();
		while(it.hasNext()){
			Map.Entry<Integer,Double> pairs = (Entry<Integer, Double>)it.next();
			//System.out.println(pairs.getKey() + " : " + pairs.getValue());
			System.out.println("[" + pairs.getKey() + "]");
			if(i++ >= limit){
				break;
			}
		}
	}
	
	public static void display(double [] array, String msg, int []dimensions){
		System.out.println(msg);
		for(int i=0;i<array.length;i++){
			System.out.println(dimensions[i] + " : " + array[i]);
		}
	}

	private static double[] convertMapToArray(LinkedHashMap<Integer,Double> map, int length){
		double []result = new double[length]; 
		if(map == null || !(map.size()>0)){
			return new double[length];
		}
		else {
			int count =0;
			Iterator<Entry<Integer, Double>> it = map.entrySet().iterator();
			while(it.hasNext()){
				Map.Entry<Integer,Double> pairs = (Entry<Integer, Double>)it.next();
				result[count++] = pairs.getValue();
			}
		}
		return result;
	}
	
	private static double[] computeScore(int [][]matrix, double []vec){
		double[] result = new double[matrix.length];
		if(vec.length == matrix[0].length){
			for(int i=0;i<matrix.length;i++){
				for(int j=0;j<matrix[0].length;j++){
					result[i] += (matrix[i][j] * vec[j]);
				}
			}
		}
		return result;
	}
	
	private static int[][] computeTransposeofMatrix(int [][]matrix){
		int [][] result = new int[matrix.length][matrix[0].length];
		for(int i=0;i<matrix.length;i++){
			for(int j=0;j<matrix[0].length;j++){
				result[j][i] = matrix[i][j];
			}
		}
		return result;
	}
	
	private static int[][] computeAdjacencyMatrix(int []dim, LinkAnalysis l){
		int [][]adjMatrix = new int[dim.length][dim.length];

		Iterator<Entry<Integer, Double>> it = rootSet.entrySet().iterator();
		int count = 0;
		while(it.hasNext()){
			Map.Entry<Integer,Double> pairs = (Entry<Integer, Double>)it.next();
			int docID = pairs.getKey();
			int []links = l.getLinks(docID);
			int []cits = l.getCitations(docID);
			for(int temp:links){
				adjMatrix[findIndex(docID, dim)][findIndex(temp, dim)] = 1;
			}
			for(int temp:cits){
				adjMatrix[findIndex(docID, dim)][findIndex(temp, dim)] = 1;
			}
			count++;
			if(count > TFWeights.getK()){
				break;
			}
		}
		
		/*//Printing Matrix
		System.out.print("\t");
		for(int k:dim){
			System.out.print(k + "\t");
		}
		System.out.println("");
		for(int i=0;i<dim.length;i++){
			System.out.print(dim[i]+ "\t");
			for(int k=0;k<dim.length;k++){
				System.out.print(adjMatrix[i][k] + "\t");
			}
			System.out.print("\n");
		}*/
		return adjMatrix;
	}

	private static int findIndex(int key, int []dim){
		int idx = -1;
		for(int i=0;i<dim.length;i++){
			if(dim[i]==key){
				idx = i;
				break;
			}
		}
		return idx;		
	}

}

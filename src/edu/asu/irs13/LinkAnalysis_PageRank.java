package edu.asu.irs13;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.FSDirectory;

public class LinkAnalysis_PageRank {
	public static final String linksFile = "IntLinks.txt";
	public static final String citationsFile = "IntCitations.txt";
	public static int numDocs = -1;
	private int[][] links;
	private int[][] citations;
	private static IndexReader ir;
	public static double[] pageRank;

	public LinkAnalysis_PageRank(){
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

	public static void main(String[] args) {
		try{
			LinkAnalysis_PageRank linkPageRank = new LinkAnalysis_PageRank();
			//Get all the documents
			int count = 0;
			for(int i=0;i<numDocs;i++){
				Document d = ir.document(i);
				if(d==null){
					System.out.println("Document " + i + " doesnt exist");
				}
				else
					count++;
			}
			if(count == numDocs){
				System.out.println("All docs exists between 0 and " + numDocs);
			}
			System.out.println("Precessing Page Rank...");
			long startTime = System.currentTimeMillis();
			computePageRank(linkPageRank);
			System.out.println("Time Taken : " + (System.currentTimeMillis() - startTime)/1000 + "s");
		}
		catch(IOException ioe){
			System.out.println("IOException "+ ioe.getMessage());
		}
		catch(Exception e){
			System.out.println("Exception " + e.getMessage());
		}
	}

	private static void computePageRank(LinkAnalysis_PageRank laPR) throws IOException, Exception{
		//Compute Page Rank of Document Corpus
		pageRank = new double[numDocs];
		double[] tempPageRank = new double[numDocs];
		double[] tempM = new double[numDocs];
		
		//Initializing page rank of all documents to 1/numDocs
		for(int i = 0;i<numDocs;i++){
			pageRank[i] = 1.0/numDocs;
		}

		//Till Page rank converges, compute the page rank
		do{
			tempPageRank = pageRank;
			for(int i=0;i<numDocs;i++){
				//Compute Row
				tempM = computeRow(i, laPR);
				pageRank[i] = computeVal(tempM, pageRank);
			}
			pageRank = normalizeArray(pageRank);
			if(LinkAnalysis.checkForConvergence(tempPageRank, pageRank, 0.00001, numDocs)){
				break;
			}
		}while(true);
		
		//Writing Page Rank into file
		writePageRankinFile();
	}
	
	//Writing Page Rank array into flat file
	private static void writePageRankinFile() throws IOException{
		File directory = new File("PR");
		if(!directory.exists()){
			directory.mkdir();
		}
		File file = new File("PR/PageRank.txt");
		if(!file.exists()){
			file.createNewFile();
			FileWriter fw = new FileWriter(new File("PR/PageRank.txt"));
			fw.write("DocID,PageRank\n");
			
			for(int i = 0;i<pageRank.length;i++){
				fw.write(i + "|" + pageRank[i] + "\n");
			}
			fw.flush();fw.close();
		}
	}
	
	//Normalize Array
	@SuppressWarnings("unused")
	private static double[] normalizeArray(double[] PR) throws Exception{
		double max = 0.0;
		double min = Double.MAX_VALUE;
		int maxi=-1;
		int mini=-1;
		
		for(int i=0;i<PR.length;i++){
			if(PR[i] > max){
				max = PR[i];
				maxi=i;
			}
			if(PR[i] < min){
				min = PR[i];
				mini=i;
			}
		}
		if(max == min){
			for(int i=0;i<PR.length;i++){
				PR[i] = 0.5;
			}
		}
		else{
			for(int i=0;i<PR.length;i++){
				PR[i] = (PR[i] - min)/(max - min);
			}
		}
		return PR;
	}
	
	//Computes the page rank of a document. Returns 1/numDocs in case of result being 0
	private static double computeVal(double[] M, double[] PR) throws Exception{
		double result = 0.0;
		if(M.length == PR.length){
			for(int i=0;i<M.length;i++){
				result += (M[i] * PR[i]);
			}
		}
		return result>0.0?result:(1.0/numDocs);
	}
	
	//Compute the Adjacency matrix row on the fly using Citations and Links
	private static double[] computeRow(int doc, LinkAnalysis_PageRank laPR) throws Exception{
		int []links = laPR.getLinks(doc);
		int []citations = laPR.getCitations(doc);
		if(doc == 13188){
			System.out.println("Links" + links.length);
			System.out.println("Citations" + citations.length);
		}
		double[] temp = new double[numDocs];

		for(int i=0;i<links.length;i++){
			temp[links[i]] = 1.0;
		}
		for(int i=0;i<citations.length;i++){
			temp[citations[i]] = 1.0;
		}
		return temp;
	}

}

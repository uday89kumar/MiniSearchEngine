package edu.asu.irs13;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.store.*;

import java.io.File;
import java.io.FileWriter;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;


public class TFWeights {
	private static String delimeter = "|";
	private static HashMap<Integer,Double> tfNorm = new LinkedHashMap<Integer, Double>();
	private static HashMap<Integer,Double> idfNorm = new LinkedHashMap<Integer, Double>();
	private static HashMap<Integer,Double> tfidf = new LinkedHashMap<Integer, Double>();
	private static double max = 0.0;
	private static double min = Double.MAX_VALUE;
	private static double startTime = 0.0;
	private static double endTime = 0.0;
	private static HashMap<String,Double> idfWord = new LinkedHashMap<String,Double>();

	public static void main(String args[]){
		try {			
			startTime = System.currentTimeMillis();
			// the IndexReader object is the main handle that will give you 
			// all the documents, terms and inverted index
			IndexReader r = IndexReader.open(FSDirectory.open(new File("index")));

			// You can figure out the number of documents using the maxDoc() function
			System.out.println("The number of documents in this index is: " + r.maxDoc());
			//Computing the norms TFNorm, TFIDFNorm
			preComputeNorm(r);
			endTime = System.currentTimeMillis();
			System.out.println("Norm Computation Time : " + (endTime-startTime)/1000 + "(s)");
			if(Integer.parseInt(args[0]) == 1){
				System.out.println("TF");
			}
			else {
				System.out.println("TFIDF");
			}
			@SuppressWarnings("resource")
			Scanner sc = new Scanner(System.in);
			String str = "";
			System.out.print("query> ");
			while(!(str = sc.nextLine()).equals("quit"))
			{
				startTime = System.currentTimeMillis();
				String[] terms = str.split("\\s+");
				tfidf = new LinkedHashMap<Integer,Double>();
				//Compute the cosine similarity of the given keywords
				computeCosineSimilarity(r, terms, Integer.parseInt(args[0]));
				endTime = System.currentTimeMillis();
				System.out.println("Time taken to process query : " + (endTime-startTime) + "(ms)");
				System.out.print("query> ");
			}
			startTime = System.currentTimeMillis();
			//Compute the terms with lowest IDF
			computeLowestIDFwords();
			endTime = System.currentTimeMillis();
			System.out.println("Time Taken : " + (endTime - startTime) + "(ms)");
		}
		catch(Exception e){
			System.out.println("Exception : " + e.getMessage());
		}
	}

	//If Choice == 1 ==> TF
	//If Choice == 2 ==> TFIDF
	private static void computeCosineSimilarity(IndexReader ir, String words[], int choice) {
		try{
			for(String word : words)
			{
				Term term = new Term("contents", word);
				TermDocs tdocs = ir.termDocs(term);
				double docCount = ir.docFreq(term);
				double idf = 0;
				if(docCount > 0){
					idf = Math.log(ir.maxDoc()/docCount);
					if(choice == 1){
						idf = 1.0;
					}
				}else {
					System.out.println("No such document with keyword '" + word + "' exists in the document");
				}				
				while(tdocs.next())
				{					
					double tf = tdocs.freq();
					int docID = tdocs.doc();
					//Computing cosine similarity (ignoring norm of query as it will be common for all documents)
					if(tfidf.containsKey(docID)){
						double value = tfidf.get(docID);
						tfidf.put(docID, value + ((tf*idf)/(choice == 1?Math.sqrt(tfNorm.get(docID)):Math.sqrt(idfNorm.get(docID)))));
					}
					else {
						tfidf.put(docID, ((tf*idf)/(choice == 1?Math.sqrt(tfNorm.get(docID)):Math.sqrt(idfNorm.get(docID)))));
					}					
				}
			}

			//Computing Max and Min values in TFIDF cosine similarity data structure
			for(double d : tfidf.values()){
				if(d > max) {
					max = d;
				}
				if(d < min) {
					min = d;
				}
			}

			//Printing Results
			startTime = System.currentTimeMillis();
			LinkedHashMap<Integer, Double> sortedMap = (LinkedHashMap<Integer, Double>) sortByValue(tfidf, 1);
			endTime = System.currentTimeMillis();
			System.out.println("Sort Time : " + (endTime-startTime));
			Iterator<Entry<Integer, Double>> it = sortedMap.entrySet().iterator();
			int first10 = 0;
			while(it.hasNext()){
				Map.Entry<Integer,Double> pairs = (Entry<Integer, Double>)it.next();
				Document d = ir.document(pairs.getKey());
				String url = d.getFieldable("path").stringValue(); // the 'path' field of the Document object holds the URL
				System.out.println("[" + pairs.getKey() + delimeter + normalizeCosine(pairs.getValue())+"]" + " - " + url.replace("%%", "/"));
				++first10;
				if(first10 >= 10) {
					break;
				}
			}

		}
		catch(Exception e){
			System.out.println("Exception : " + e.getMessage());
		}
	}

	//Normalizing the cosine values
	private static double normalizeCosine(double cosine){
		try {			
			double normCosine = 0.0;
			if(max == min) {
				normCosine = 0.5;
			}
			else {
				normCosine = (cosine - min)/(max - min);				
			}
			//Reducing the precision to 5 digits only.
			return new BigDecimal(normCosine ).setScale(5, BigDecimal.ROUND_HALF_UP).doubleValue();
		}
		catch(Exception e){
			System.out.println("Exception : " + e.getMessage());
			return 0.0;
		}
	}

	//Using comparator to sort by value
	//Source - StackOverflow
	public static <K, V extends Comparable<? super V>> Map<K, V> 
	sortByValue( Map<K, V> map , final int decend)
	{
		List<Map.Entry<K, V>> list =
				new LinkedList<>( map.entrySet() );
				Collections.sort( list, new Comparator<Map.Entry<K, V>>()
						{
					@Override
					public int compare( Map.Entry<K, V> o1, Map.Entry<K, V> o2 )
					{
						return decend == 1?(o2.getValue()).compareTo(o1.getValue()):(o1.getValue()).compareTo(o2.getValue());
					}
						} );

				Map<K, V> result = new LinkedHashMap<>();
				for (Map.Entry<K, V> entry : list)
				{
					result.put( entry.getKey(), entry.getValue() );
				}
				return result;
	}

	//Compute the lowest IDF in the document corpus
	private static void computeLowestIDFwords(){
		LinkedHashMap<String,Double> sortedLowestIDF = (LinkedHashMap<String, Double>)sortByValue(idfWord,2);
		Iterator<Entry<String, Double>> it = sortedLowestIDF.entrySet().iterator();
		int first5 = 0;
		while(it.hasNext()){
			Map.Entry<String,Double> pairs = (Entry<String, Double>)it.next();
			//Restrict to only 5 values
			if(pairs.getValue() >=0){
				System.out.println("Words : " + pairs.getKey() + "\t" + "IDF : " + new BigDecimal(pairs.getValue() ).setScale(6, BigDecimal.ROUND_HALF_UP).doubleValue());
				++first5;
				if(first5 >= 5){
					break;
				}
			}
		}
	}

	//Pre Compute the TFIDF and TF norm 
	private static void preComputeNorm(IndexReader ir){
		try{
			TermEnum termEnum = ir.terms();
			while(termEnum.next()){
				Term term = new Term("contents", termEnum.term().text());
				TermDocs tdocs = ir.termDocs(term);
				double idf = 0;
				double docCount = ir.docFreq(term);
				if(docCount > 0){
					idf = Math.log(ir.maxDoc() / docCount);
					idfWord.put(termEnum.term().text(), idf);
				}
				else {
					idfWord.put(termEnum.term().text(), -1.0);
				}

				while(tdocs.next()){
					if(tfNorm.containsKey(tdocs.doc())){
						double value = tfNorm.get(tdocs.doc());
						tfNorm.put(tdocs.doc(), value + Math.pow(tdocs.freq(), 2));
					}
					else {
						tfNorm.put(tdocs.doc(), Math.pow(tdocs.freq(),2));
					}
					if(idfNorm.containsKey(tdocs.doc())){
						idf = Math.abs(idf);
						double value = idfNorm.get(tdocs.doc());
						idfNorm.put(tdocs.doc(), value + Math.pow(tdocs.freq()*idf, 2));
					}
					else {
						idfNorm.put(tdocs.doc(), Math.pow(tdocs.freq()*idf,2));
					}
				}
			}

			File directory = new File("TF");
			if(!directory.exists()){
				directory.mkdir();
				File file = new File("TF/TF.txt");
				file.createNewFile();

				FileWriter fw = new FileWriter(new File("TF/TF.txt"));
				fw.write("DocID,TFNorm\n");
				Iterator<Entry<Integer, Double>> it = tfNorm.entrySet().iterator();
				while(it.hasNext()){
					Map.Entry<Integer,Double> pairs = (Entry<Integer, Double>)it.next();
					fw.write(pairs.getKey() + delimeter + Math.sqrt(pairs.getValue()) + "\n");
				}
				fw.flush();fw.close();

				File idffile = new File("TF/IDF.txt");
				idffile.createNewFile();

				FileWriter idfw = new FileWriter(new File("TF/IDF.txt"));
				idfw.write("DocID,IDFNorm\n");
				Iterator<Entry<Integer, Double>> itidf = idfNorm.entrySet().iterator();
				while(itidf.hasNext()){
					Map.Entry<Integer,Double> pairs = (Entry<Integer, Double>)itidf.next();
					idfw.write(pairs.getKey() + delimeter + Math.sqrt(pairs.getValue()) + "\n");
				}
				idfw.flush();idfw.close();
			}
		}
		catch(Exception e){
			System.out.println("Excetpion : "+ e.getMessage());
		}
	}
}


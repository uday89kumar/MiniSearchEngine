package edu.asu.irs13;

import org.apache.lucene.index.*;
import org.apache.lucene.store.*;
import org.apache.lucene.document.*;

import java.io.File;
import java.util.Scanner;

public class SearchFiles {
	public static void main(String[] args) throws Exception
	{
		// the IndexReader object is the main handle that will give you 
		// all the documents, terms and inverted index
		IndexReader r = IndexReader.open(FSDirectory.open(new File("index")));
		
		// You can figure out the number of documents using the maxDoc() function
		System.out.println("The number of documents in this index is: " + r.maxDoc());
		
		int i = 0;
		// You can find out all the terms that have been indexed using the terms() function
		TermEnum t = r.terms();
		while(t.next())
		{
			// Since there are so many terms, let us try printing only term #100000-#100010
			if (i > 100000) System.out.println("["+i+"] " + t.term().text());
			if (++i > 100010) break;
		}
		
		// You can create your own query terms by calling the Term constructor, with the field 'contents'
		// In the following example, the query term is 'brute'
		Term te = new Term("contents", "grades");
		
		// You can also quickly find out the number of documents that have term t
		System.out.println("Number of documents with the word 'constructor' is: " + r.docFreq(te));
		
		// You can use the inverted index to find out all the documents that contain the term 'brute'
		//  by using the termDocs function
		TermDocs td = r.termDocs(te);
		while(td.next())
		{
			System.out.println("Document number ["+td.doc()+"] contains the term 'constructor' " + td.freq() + " time(s).");
		}
		
		// You can find the URL of the a specific document number using the document() function
		// For example, the URL for document number 14191 is:
		Document d = r.document(14191);
		String url = d.getFieldable("path").stringValue(); // the 'path' field of the Document object holds the URL
		System.out.println(url.replace("%%", "/"));
		

		// -------- Now let us use all of the functions above to make something useful --------
		// The following bit of code is a worked out example of how to get a bunch of documents
		// in response to a query and show them (without ranking them according to TF/IDF)
		@SuppressWarnings("resource")
		Scanner sc = new Scanner(System.in);
		String str = "";
		System.out.print("query> ");
		while(!(str = sc.nextLine()).equals("quit"))
		{
			String[] terms = str.split("\\s+");
			for(String word : terms)
			{
				Term term = new Term("contents", word);
				TermDocs tdocs = r.termDocs(term);
				while(tdocs.next())
				{
					String d_url = r.document(tdocs.doc()).getFieldable("path").stringValue().replace("%%", "/");
					System.out.println("["+tdocs.doc()+"] " + d_url);
				}
			}
			System.out.print("query> ");
		}
	}
}

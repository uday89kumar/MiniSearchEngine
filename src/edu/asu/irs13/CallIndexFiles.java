package edu.asu.irs13;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericField;
import org.apache.lucene.index.FieldInfo.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;

public class CallIndexFiles {
    final static String docsPath = "C:\\Data\\ta\\try\\cse494-v1\\result3\\";

    /** Index all text files under a directory. */
	  public static void main(String[] args) {
	    String indexPath = "index";
	    boolean create = true;

	    final File docDir = new File(docsPath);
	    if (!docDir.exists() || !docDir.canRead()) {
	      System.out.println("Document directory '" +docDir.getAbsolutePath()+ "' does not exist or is not readable, please check the path");
	      System.exit(1);
	    }
	    
	    Date start = new Date();
	    try {
	      System.out.println("Indexing to directory '" + indexPath + "'...");

	      Directory dir = FSDirectory.open(new File(indexPath));
	      Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_31);
	      IndexWriterConfig iwc = new IndexWriterConfig(Version.LUCENE_31, analyzer);

	      if (create) {
	        // Create a new index in the directory, removing any
	        // previously indexed documents:
	        iwc.setOpenMode(OpenMode.CREATE);
	      } else {
	        // Add new documents to an existing index:
	        iwc.setOpenMode(OpenMode.CREATE_OR_APPEND);
	      }

	      // Optional: for better indexing performance, if you
	      // are indexing many documents, increase the RAM
	      // buffer.  But if you do this, increase the max heap
	      // size to the JVM (eg add -Xmx512m or -Xmx1g):
	      //
	      // iwc.setRAMBufferSizeMB(256.0);

	      IndexWriter writer = new IndexWriter(dir, iwc);
	      indexDocs(writer, docDir);

	      // NOTE: if you want to maximize search performance,
	      // you can optionally call forceMerge here.  This can be
	      // a terribly costly operation, so generally it's only
	      // worth it when your index is relatively static (ie
	      // you're done adding documents to it):
	      //
	      // writer.forceMerge(1);

	      writer.close();

	      Date end = new Date();
	      System.out.println(end.getTime() - start.getTime() + " total milliseconds");

	    } catch (IOException e) {
	      System.out.println(" caught a " + e.getClass() +
	       "\n with message: " + e.getMessage());
	    }
	  }
	
	 /**
	   * Indexes the given file using the given writer, or if a directory is given,
	   * recurses over files and directories found under the given directory.
	   * 
	   * NOTE: This method indexes one document per input file.  This is slow.  For good
	   * throughput, put multiple documents into your input file(s).  An example of this is
	   * in the benchmark module, which can create "line doc" files, one document per line,
	   * using the
	   * <a href="../../../../../contrib-benchmark/org/apache/lucene/benchmark/byTask/tasks/WriteLineDocTask.html"
	   * >WriteLineDocTask</a>.
	   *  
	   * @param writer Writer to the index where the given file/dir info will be stored
	   * @param file The file to index, or the directory to recurse into to find files to index
	   * @throws IOException
	   */
	  static void indexDocs(IndexWriter writer, File file)
	    throws IOException {
	    // do not try to index files that cannot be read
	    if (file.canRead()) {
	      if (file.isDirectory()) {
	        String[] files = file.list();
	        // an IO error could occur
	        if (files != null) {
	          for (int i = 0; i < files.length; i++) {
	            indexDocs(writer, new File(file, files[i]));
	          }
	        }
	      } else {

	        FileInputStream fis;
	        try {
	          fis = new FileInputStream(file);
	        } catch (FileNotFoundException fnfe) {
	          // at least on windows, some temporary files raise this exception with an "access denied" message
	          // checking if the file can be read doesn't help
	          return;
	        }

	        try {

	          // make a new, empty document
	          Document doc = new Document();

	          // Add the path of the file as a field named "path".  Use a
	          // field that is indexed (i.e. searchable), but don't tokenize 
	          // the field into separate words and don't index term frequency
	          // or positional information:
	          //Field pathField = new Field("path", file.getPath(), Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS);
	          Field pathField = new Field("path", file.getPath().substring(docsPath.length()), Field.Store.YES, Field.Index.NO);
	          pathField.setIndexOptions(IndexOptions.DOCS_ONLY);
	          doc.add(pathField);

	          // Add the last modified date of the file a field named "modified".
	          // Use a NumericField that is indexed (i.e. efficiently filterable with
	          // NumericRangeFilter).  This indexes to milli-second resolution, which
	          // is often too fine.  You could instead create a number based on
	          // year/month/day/hour/minutes/seconds, down the resolution you require.
	          // For example the long value 2011021714 would mean
	          // February 17, 2011, 2-3 PM.
	          NumericField modifiedField = new NumericField("modified");
	          modifiedField.setLongValue(file.lastModified());
	          doc.add(modifiedField);

	          // Add the contents of the file to a field named "contents".  Specify a Reader,
	          // so that the text of the file is tokenized and indexed, but not stored.
	          // Note that FileReader expects the file to be in UTF-8 encoding.
	          // If that's not the case searching for special characters will fail.
	          doc.add(new Field("contents", new BufferedReader(new InputStreamReader(fis, "UTF-8"))));

	          if (writer.getConfig().getOpenMode() == OpenMode.CREATE) {
	            // New index, so we just add the document (no old document can be there):
	            System.out.println("adding " + file);
	            writer.addDocument(doc);
	          } else {
	            // Existing index (an old copy of this document may have been indexed) so 
	            // we use updateDocument instead to replace the old one matching the exact 
	            // path, if present:
	            System.out.println("updating " + file);
	            writer.updateDocument(new Term("path", file.getPath()), doc);
	          }
	          
	        } finally {
	          fis.close();
	        }
	      }
	    }
	  }
}

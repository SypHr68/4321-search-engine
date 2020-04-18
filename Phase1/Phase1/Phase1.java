import org.htmlparser.beans.LinkBean;
import org.htmlparser.beans.StringBean;

import org.htmlparser.Node;
import org.htmlparser.NodeFilter;
import org.htmlparser.Parser;
import org.htmlparser.filters.AndFilter;
import org.htmlparser.filters.NodeClassFilter;
import org.htmlparser.tags.LinkTag;
import org.htmlparser.util.NodeList;
import org.htmlparser.util.ParserException;

import org.rocksdb.RocksDB;
import org.rocksdb.Options;  
import org.rocksdb.RocksIterator;
import org.rocksdb.RocksDBException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Document;

import java.net.URL;
import java.net.URLConnection;
import java.net.MalformedURLException;

import java.util.Date;
import java.util.Vector;
import java.util.StringTokenizer;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.IOException;

import IRUtilities.*;
import java.io.*;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;


class StopStem
{
  // StopStem Variables
	private Porter porter;
	private java.util.HashSet stopWords;
	
	// Constructor
	public StopStem(String str) throws Exception {
		super();
		porter = new Porter();
		stopWords = new java.util.HashSet();
    
    String line;
		BufferedReader reader = new BufferedReader(new FileReader(str));
		while ((line = reader.readLine()) != null) { stopWords.add(line);}
		reader.close();
} 
	// Stemming
	public String stem(String str){ return porter.stripAffixes(str);}
	
	// Stopword
	public boolean isStopWord(String str){ return stopWords.contains(str);}
	
}

public class Phase1{

  // Phase1 Variables
	private RocksDB db;
  private static Options options= new Options();
	private static String url;
	
	// Constructors
	Phase1(String _url, String db_path) throws Exception {
		this.url = _url;
    this.options.setCreateIfMissing(true);
		this.db = RocksDB.open(options, db_path);
	}
	
	Phase1(String _url){ this.url = _url;} // overload constructor  
	
	// Extract Words from URL, Returns String Vector Array of Words
	public Vector<String> extractWords() throws ParserException {
		// use StringTokenizer to tokenize the result from StringBean
    Vector<String> result = new Vector<String>();
    StringBean bean = new StringBean();
    bean.setURL(url);
    bean.setLinks(false);
    String contents = bean.getStrings();
    StringTokenizer st = new StringTokenizer(contents);
    while (st.hasMoreTokens()) {result.add(st.nextToken());}
    
    return result;
	}
	
	// Extract Links from URL, Returns String Vector Array of Links
	public Vector<String> extractLinks() throws ParserException {
		Vector<String> result = new Vector<String>();
    LinkBean bean = new LinkBean();
    bean.setURL(url);
    URL[] urls = bean.getLinks();
    for (URL s : urls) { result.add(s.toString());}
    
    return result;            
	}
	
	// Add Word Entry to DB (DocX + Y)
	public void addEntry(String word, int x, int y) throws RocksDBException {
	    if (word.length() > 1){
      byte[] content = db.get(word.getBytes());
      
      // Create new key for new word
      if (content == null) {  content = ("doc" + x + " " + y).getBytes();}
      
      // Append existing key for word
      else { content = (new String(content) + " doc" + x + " " + y).getBytes();}
      db.put(word.getBytes(), content);
    }
    }
  
  // Add URL Entry to DB
  public int addUrl(String url , RocksDB url_db) throws RocksDBException
  {
    byte[] content = url_db.get(url.getBytes());
    int count;
    
    // If the URL doesn't exist, Returns iterated count/ length
    if (content == null) {
      count = 0;
      RocksIterator iter = url_db.newIterator();
      for(iter.seekToFirst(); iter.isValid(); iter.next()){ count++;}
      content = (String.valueOf(count)).getBytes();
      url_db.put(url.getBytes(), content);	
    } 
    
    // Do nothing as the URL exists in DB, Returns original count
    else { count = Integer.parseInt(new String(url_db.get(url.getBytes())));}
    
    return count;
  }
	
	//Count Occurences of word in String, Returns count
	public static int countOccurences(String str, String word) { 
    // Split the String by spaces in str_array 
    String str_array[] = str.split(" "); 
   
    int count = 0; 

    // Increment count for every match
    for (String match: str_array){
    if (word.equals(match)) 
        count++; 
    } 
  
    return count; 	
	} 
	
	// Prints out Word Entries and Occurences to Terminal and FileWrite
	public void printAll(PrintWriter printWriter,int i) throws Exception{  // Overload funtion for writer
    RocksIterator iter = db.newIterator();
		for(iter.seekToFirst(); iter.isValid(); iter.next()) {
			String temp = new String(iter.value());
			String delimiter = new String("doc"+ i);
			
			if (countOccurences(temp,delimiter)>0){
        printWriter.print(new String(iter.key()) + " " + countOccurences(temp,delimiter) + ";");
        System.out.print(new String(iter.key()) + " " + countOccurences(temp,delimiter) + ";");
			}
    }
		printWriter.println();
		System.out.println();
  } 
	
	public static void writing (PrintWriter printWriter,Phase1 master,Phase1 child,int doc_id,int size,int size_format) throws Exception
	{
		//Prints Title to Terminal and FileWrite
    String title = Jsoup.connect(child.url).get().title();
    printWriter.println(title);			
    System.out.println(title);	
		
		
		//Prints URL to Terminal and FileWrite
		printWriter.println(child.url);
		System.out.println(child.url);
		
		//Prints Date to Terminal and FileWrite
		Date mod;
		URL u = new URL(child.url);		
		long date = u.openConnection().getLastModified();
		
		// Last Modified Date not mentionned in URL Header
		if (date == 0) { mod = new Date();} 
		
		// Last Modified Date mentionned in URL Header
		else {
      System.out.print("Last Modification on:");
      printWriter.print("Last Update on:");
      mod = new Date(date);
		}
      System.out.print(mod +"\t");
      printWriter.print(mod +"\t");

    //Prints Size to Terminal and FileWrite
    
    // Size mentionned in URL Header
		if (size_format == 0){
      System.out.println("SIZE: " + size + "B");
      printWriter.println("SIZE: " + size + "B");
		}
		
		// Size not mentionned in URL Header, Count
		else if (size_format == 1){
      System.out.println("SIZE = " + size + " Characters");
      printWriter.println("SIZE = " + size + " Characters");
		}

    // Prints out Word Entries and Occurences to Terminal and FileWrite 
		master.printAll(printWriter,doc_id);
	
		//Child links
		Vector<String> clinks = child.extractLinks();
		for(int i = 0; i < clinks.size(); i++){		
      printWriter.println(clinks.get(i));
      System.out.println(clinks.get(i));
		}
		
		printWriter.println("-------------------------------------------------------------------------------------------");
		System.out.println("-------------------------------------------------------------------------------------------");
	}
	
	public static void main (String[] args) throws Exception
	{
    StopStem stopStem = new StopStem("stopwords.txt");
		FileWriter fileWriter = new FileWriter("../Phase1/spider_result.txt");
		PrintWriter printWriter = new PrintWriter(fileWriter);
		
    String db_path = "../Phase1/db/word";		
		RocksDB.loadLibrary();

    Options options = new Options();
    options.setCreateIfMissing(true);
    RocksDB url_db = RocksDB.open(options,"../Phase1/db/URL");

		Phase1 crawler = new Phase1("http://www.cse.ust.hk/",db_path);
		Vector<String> plinks = crawler.extractLinks();

		// Parent Link: One Off
    int doc_id = crawler.addUrl(crawler.url,url_db);
    Vector<String> words = crawler.extractWords();
    for(int i = 0; i < words.size(); i++){   
    if (stopStem.isStopWord(words.get(i)) == false){
      String wd = stopStem.stem(words.get(i));
      if (stopStem.isStopWord(wd) == false){
          crawler.addEntry(wd,doc_id ,i);
      }
     }
    }
				 				
    URL u = new URL(crawler.url);							
    URLConnection uc = u.openConnection();
    int psize = uc.getContentLength();
    if (psize == -1){
      int totalchar = 0; for (String count: words){ totalchar += count.length();};
      psize = totalchar;
      writing (printWriter,crawler,crawler,doc_id,psize,1);
      }else{
      writing (printWriter,crawler,crawler,doc_id,psize,0);
      }
						
    //2. recursive part
    for (int j = 1; j < 31;j++){
    Phase1 child = new Phase1(plinks.get(j));
    Vector<String> cwords = child.extractWords();
    doc_id = child.addUrl(child.url,url_db);
    for(int i = 0; i < cwords.size(); i++){  // insert all word in a page 
    if (stopStem.isStopWord(cwords.get(i)) == false){
      String wd = stopStem.stem(cwords.get(i));
      if (stopStem.isStopWord(wd) == false){
        crawler.addEntry(wd,doc_id ,i);
      }
     }
    } 
								
    u = new URL(child.url);							
    uc = u.openConnection();
    int size = uc.getContentLength();
    if (size == -1){
      int totalchar = 0; for (String count: cwords){ totalchar += count.length();};
      size = totalchar;
      writing (printWriter,crawler,child,doc_id,size,1);
      }else{
      writing (printWriter,crawler,child,doc_id,size,0);
      }
    }
    printWriter.close();
	}	
}
	
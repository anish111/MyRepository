package myProject;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern; 

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element; 
import org.jsoup.select.Elements;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;


/**
 * @author ANISH_2
 *
 */
public class Crawler{

	// stores all the links that are visited
	private static Set<String> visitedLinks = Collections.synchronizedSet(new HashSet<String>()); 
	
	// stores all the links that are yet to be visited
	private static BlockingQueue<String> qLinks = new LinkedBlockingQueue<>();  
	
	// stores all the email ids found
	private static Set<String> emails = Collections.synchronizedSet(new HashSet<String>());   
	
	// Max time in milliseconds to wait for JavaScript to execute
	private final static int WAIT_FOR_JAVASCRIPT = 500;                                        
	
	// Max number of threads to be created
	private final static int THREAD_SIZE = 10;		
	
	static{
		// turn off logging
		System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");
	}

	public static class MyRunnable implements Runnable{
		@Override
		public void run() {			
			try(WebClient client = new WebClient(BrowserVersion.CHROME);){
				client.getOptions().setThrowExceptionOnScriptError(false);
				client.getOptions().setThrowExceptionOnFailingStatusCode(false);
				client.getOptions().setJavaScriptEnabled(true);
				client.getOptions().setCssEnabled(false);
				client.getOptions().setAppletEnabled(false);
				crawl(client);
			}	
		}
	}


	public static void main(String[] args) {

		String input ="";
		if(args.length>0){
			input = args[0];
		}
		final String URL ="http://"+input;
		
		// Add seed URL in the queue
		qLinks.add(URL);
		 
		ExecutorService executorService = Executors.newFixedThreadPool(THREAD_SIZE);
		for (int i = 0; i <THREAD_SIZE; i++) {
			Runnable worker = new MyRunnable();
			executorService.execute(worker);
		}
		executorService.shutdown(); 
		try {
			executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
		} catch (InterruptedException e) {
		} 
		
		System.out.println("Found these email addresses:");
		for(String email:emails){
			System.out.println(email);
		} 
	}

	/** Method to perform crawl links one by one 
	 * @param driver WebClient of type htmlunit.WebClient
	 */
	private static void crawl(WebClient client) {

		String nextLink = "";
		String htmlSource = "";
		try {
			while((nextLink = qLinks.poll(20000, TimeUnit.MILLISECONDS))!=null){
				
				htmlSource = getHTML(nextLink,client);
				parseHTML(Jsoup.parse(htmlSource)); 
				extractEMAIL(htmlSource);
			}			
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}

	/** Downloads the HTML of the web page specified by link
	 * @param link web page link
	 * @param client WebClient of type htmlunit.WebClient
	 * @return
	 */
	private static String getHTML(String link,WebClient client) {
		// TODO Auto-generated method stub
		
		HtmlPage page;
		String source ="";
		try {
			if(!visitedLinks.contains(link)){
				visitedLinks.add(link); 
				// Retrieve the Web Page
				page = client.getPage(link);
				
				// Wait for JavaScript to run
				Thread.sleep(WAIT_FOR_JAVASCRIPT);
				
				// Convert to HTML from XML 
				source = page.asXml().replaceFirst("<\\?xml version=\"1.0\" encoding=\"(.+)\"\\?>", "<!DOCTYPE html>");

			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}catch(Exception e){	
			e.printStackTrace();
		}
		return source;
	}

	/** Parses the given HTML document to find additional links to be searched
	 * @param document HTML document of type org.jsoup.nodes.Document
	 */
	private static void parseHTML(Document document) {

		Elements urls = document.getElementsByTag("a");
		for (Element link : urls) {
			String linkHref = link.attr("href");
			URI uri;
			String domain = "";
			try {
				uri = new URI(linkHref);
				domain = uri.getHost();
			} catch (URISyntaxException e) {
				e.printStackTrace();
			}
			if(domain!=null && domain.endsWith("jana.com")){
				if(!visitedLinks.contains(linkHref)){
					qLinks.add(linkHref);
				}
			}
		}
	}

	/** Find email address within the given String
	 * 
	 * @param htmlpage HTML string in which email addresses is to be searched
	 * 
	 */
	private static void extractEMAIL(String htmlpage) {

		if(htmlpage !=null){
			String regex = "\\b[A-Z0-9._%+-]+@([A-Z0-9.-]+\\.)[A-Z]{2,4}\\b";
			try{
				Pattern p = Pattern.compile(regex,Pattern.CASE_INSENSITIVE);
				Matcher matcher = p.matcher(htmlpage);
				while(matcher.find()) {
					emails.add(matcher.group()); 
				}
			}catch(Exception e){
				e.printStackTrace();
			}
		}	
	}
}

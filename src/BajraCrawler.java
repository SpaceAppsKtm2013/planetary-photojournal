import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringEscapeUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import edu.uci.ics.crawler4j.crawler.Page;
import edu.uci.ics.crawler4j.crawler.WebCrawler;
import edu.uci.ics.crawler4j.parser.HtmlParseData;
import edu.uci.ics.crawler4j.url.WebURL;


public class BajraCrawler extends WebCrawler {

	public static AtomicInteger counter = new AtomicInteger(0);

	private final static Pattern FILTERS = Pattern.compile(".*(\\.(css|js|bmp|gif" 
			+ "|png|tiff?|mid|mp2|mp3|mp4"
			+ "|wav|avi|mov|mpeg|ram|m4v|pdf" 
			+ "|rm|smil|wmv|swf|wma|zip|rar|gz))$");
	
	private String jplId;

	/**
	 * You should implement this function to specify whether
	 * the given url should be crawled or not (based on your
	 * crawling logic).
	 */
	@Override
	public boolean shouldVisit(WebURL url) {
		
		String href = url.getURL().toLowerCase();
		
		boolean traverse = true;
		traverse = !FILTERS.matcher(href).matches() && 
				href.startsWith("http://photojournal.jpl.nasa.gov/");
		Lock readersLock = GlobalStore.listLock.readLock();
		readersLock.lock();
		if(GlobalStore.traversed.contains(url)) {
			traverse = false;
			readersLock.unlock();
		} else {
			readersLock.unlock();
			Lock writersLock = GlobalStore.listLock.writeLock();
			writersLock.lock();
			GlobalStore.traversed.add(url);
			writersLock.unlock();
		}
		if(!href.contains("catalog") && !href.contains("gallery")) traverse = false; 
		return traverse;
	}

	/**
	 * This function is called when a page is fetched and ready 
	 * to be processed by your program.
	 */
	@Override
	public void visit(Page page) {          
		String url = page.getWebURL().getURL();
		String base_url = "";
		try {
			URL javaUrl = new URL(url);
			base_url = "http://" + javaUrl.getHost();
		} catch (MalformedURLException e4) {
			// TODO Auto-generated catch block
			e4.printStackTrace();
		}
		
		System.out.println("URL: " + url);
		if(!url.contains("catalog")) return;

		if (page.getParseData() instanceof HtmlParseData) {
			HtmlParseData htmlParseData = (HtmlParseData) page.getParseData();
			String html = htmlParseData.getHtml();
			
			Document doc = Jsoup.parse(html);
			
			String title = "";
			Elements titleElements = doc.select("caption b");
			for(int i = 0; i < titleElements.size(); i++) {
				Element tElement = titleElements.get(i);
				title = tElement.text();
				System.out.println("Title = " + title);
			}
			title = StringEscapeUtils.escapeSql(title);
			title = title.trim();
			
			String jplId = title.split(":")[0];
			System.out.println(jplId);
			
			Lock readLock = GlobalStore.idListLock.readLock();
			readLock.lock();
			if(GlobalStore.traversedIds.contains(jplId)) {
				readLock.unlock();
				return;
			} else {
				readLock.unlock();
				Lock writersLock = GlobalStore.idListLock.writeLock();
				writersLock.lock();
				GlobalStore.traversedIds.add(jplId);
				writersLock.unlock();
			}
			
			Elements imgs = doc.select("a[href$=.jpg");
			String imageUrl = "";
			String imageMidUrl = "";
			for(int i = 0; i < imgs.size(); ++i) {
				Element img = imgs.get(i);
				String hrefString = img.attr("href");
				if(hrefString.contains("jpeg/")) {
					imageUrl = base_url + img.attr("href");
				}
				if(hrefString.contains("jpegMod/")) {
					imageMidUrl = base_url + img.attr("href");
				}
			}

			String description = "";
			Elements desc = doc.select("table p");
			for(int i = 0; i < desc.size(); ++i) {
				Element d = desc.get(i);
				description = d.text();
			}

			Elements tds = doc.select("td");

			/* fields for storing the data */
			/* NOTE: I hate initializing to empty strings, but I had to for
			 * speed purposes
			 */
			String targetName = "";
			String mission = "";
			String instrument = "";
			
			String prevText = null;
			String currentText;
			for(int i = 0; i < tds.size(); i++) {
				Element td = tds.get(i);
				currentText = td.text();
				if(prevText != null && prevText.equalsIgnoreCase("Target Name:")) {
					targetName = currentText;
				} else if(prevText != null && prevText.equalsIgnoreCase("Mission:")) {
					mission = currentText;
				} else if(prevText != null && prevText.equalsIgnoreCase("Instrument:")) {
					instrument = currentText;
				}

				prevText = currentText;
			}

			System.out.println("Target Name = " + targetName);
			System.out.println("Mission = " + mission);
			System.out.println("Instrument = " + instrument);
			System.out.println("Description = " + description);
			
			targetName = StringEscapeUtils.escapeSql(targetName);
			mission = StringEscapeUtils.escapeSql(mission);
			instrument = StringEscapeUtils.escapeSql(instrument);
			description = StringEscapeUtils.escapeSql(description);
			
			
			description = description.trim();

			String SQL = "INSERT INTO photos(url, description, mission, target, instrument," +
					"category_id, url_mid, title, jpl_id) VALUES ('" + imageUrl + "','" + description + "','" + 
					mission + "','" + targetName + "','" + instrument + "', 10,'" + imageMidUrl +
					"','" + title + "','" + jplId + "')";
			
			System.out.println(SQL);
			
			Connection connection = getConnection();
			PreparedStatement statement;
			try {
				statement = connection.prepareStatement(SQL);
				statement.execute();
			} catch (SQLException e3) {
				// TODO Auto-generated catch block
				e3.printStackTrace();
			}
			
			try {
				Class.forName("com.mysql.jdbc.Driver");
			} catch (ClassNotFoundException e2) {
				// TODO Auto-generated catch block
				e2.printStackTrace();
			}
		}
	}

	private Connection getConnection() {

		Connection dbconn = null;
		try {
			Class.forName("com.mysql.jdbc.Driver");
		} catch (ClassNotFoundException e1) {
			e1.printStackTrace();
			return null;
		}

		try {
			dbconn = DriverManager.getConnection("jdbc:mysql://localhost:8889/spaceapps", "root", "root");
		} catch (SQLException e) {
			e.printStackTrace();
			return null;
		}

		return dbconn;
	}
}

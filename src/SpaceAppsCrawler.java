import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
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


public class SpaceAppsCrawler extends WebCrawler {

	private final static Pattern FILTERS = Pattern.compile(".*(\\.(css|js|bmp|gif" 
			+ "|png|tiff?|mid|mp2|mp3|mp4"
			+ "|wav|avi|mov|mpeg|ram|m4v|pdf" 
			+ "|rm|smil|wmv|swf|wma|zip|rar|gz))$");
	
	/**
	 * I did this in a somewhat roundabout way.
	 * In this method, I check a few things in addition to checking whether the 
	 * URL has already been visited. If it hasn't, I add it to a list. I then check to see if 
	 * the page with that ID has been visited. There are lot of additional pages that
	 * have parameters that I didn't want to revisit, so needed to exclude those.
	 * 
	 * Locks used to ensure thread safety.
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
		if(!href.contains("catalog") && !href.contains("gallery") 
				&& !href.contains("targetFamily")) traverse = false; 
		
		if(href.contains("catalog")) {
			int lastSlash = href.lastIndexOf("/");
			int questionIndex = href.indexOf("?");
			questionIndex = questionIndex > 0 ? questionIndex : href.length();
			String jplId = href.substring(lastSlash + 1, questionIndex);
			Lock readLock = GlobalStore.idListLock.readLock();
			readLock.lock();
			if(GlobalStore.traversedIds.contains(jplId)) {
				readLock.unlock();
				traverse = false;
			} else {
				readLock.unlock();
				Lock writersLock = GlobalStore.idListLock.writeLock();
				writersLock.lock();
				GlobalStore.traversedIds.add(jplId);
				writersLock.unlock();
			}
		}
		
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
		
		if(url.contains("gallery")) {
			HtmlParseData parseData = (HtmlParseData) page.getParseData();
			List<WebURL> outgoingLinks = parseData.getOutgoingUrls();
			for(int i = 0; i < outgoingLinks.size(); ++i) {
				System.out.println("outgoing link = " + outgoingLinks.get(i).getURL());
			}
		} else {
			System.out.println("No gallery in the URL. " + page.getWebURL().getURL());
		}
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
			}
			title = StringEscapeUtils.escapeSql(title);
			title = title.trim();
			
			String jplId = title.split(":")[0];
			
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

			targetName = StringEscapeUtils.escapeSql(targetName);
			mission = StringEscapeUtils.escapeSql(mission);
			instrument = StringEscapeUtils.escapeSql(instrument);
			description = StringEscapeUtils.escapeSql(description);
			
			
			description = description.trim();

			/* INSERTION INTO DB. I just used straight JDBC for this; to avoid any config/dev
			 * overhead of using an ORM.
			 */
			
			String SQL = "INSERT INTO photos(url, description, mission, target, instrument," +
					"category_id, url_mid, title, jpl_id) VALUES ('" + imageUrl + "','" + description + "','" + 
					mission + "','" + targetName + "','" + instrument + "'," + GlobalStore.CURRENT_CATEGORY  
					+ ",'" + imageMidUrl +
					"','" + title + "','" + jplId + "')";
			
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

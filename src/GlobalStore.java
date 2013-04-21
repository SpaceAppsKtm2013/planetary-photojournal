import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import edu.uci.ics.crawler4j.url.WebURL;


public class GlobalStore {

	public static ReadWriteLock listLock = new ReentrantReadWriteLock();
	public static List<WebURL> traversed = new ArrayList<WebURL>();

	public static ReadWriteLock idListLock = new ReentrantReadWriteLock();
	public static List<String> traversedIds = new ArrayList<String>();

	public static List<String> seeds = new ArrayList<String>();

	public static final int CURRENT_CATEGORY = 3;
	
	static {

		seeds.add("http://photojournal.jpl.nasa.gov/target/Sun");
		seeds.add("http://photojournal.jpl.nasa.gov/targetFamily/Mercury");
		seeds.add("http://photojournal.jpl.nasa.gov/targetFamily/Venus");
		seeds.add("http://photojournal.jpl.nasa.gov/targetFamily/Earth");
		seeds.add("http://photojournal.jpl.nasa.gov/targetFamily/Mars");
		seeds.add("http://photojournal.jpl.nasa.gov/targetFamily/Jupiter");
		seeds.add("http://photojournal.jpl.nasa.gov/targetFamily/Saturn");
		seeds.add("http://photojournal.jpl.nasa.gov/targetFamily/Uranus");
		seeds.add("http://photojournal.jpl.nasa.gov/targetFamily/Neptune");
		seeds.add("http://photojournal.jpl.nasa.gov/targetFamily/Pluto");
		seeds.add("http://photojournal.jpl.nasa.gov/gallery/universe");
		seeds.add("http://photojournal.jpl.nasa.gov/target/Other");
	}

}

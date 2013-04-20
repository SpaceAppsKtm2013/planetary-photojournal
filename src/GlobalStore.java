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
}

package application.rest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CollectionsUtils {
	
	
	public static String changeCollectionIntoStringList(List<Map> list) {
		String collections = "";
		for (Map map : list) {
			String name = (String) map.get("name");
			String version = (String) map.get("version");
			String element = "( "+name+" , "+version+" ) ";
			collections = collections + element + ",";
		}
		collections = collections.substring(0, collections.length() - 1);
		return collections;
	}
	
	public static List filterNewCollections(List<Map> fromGit, List<Map> fromKabanero) {
		ArrayList<Map> newCollections=new ArrayList<Map>();
		
		try {
			for (Map map : fromGit) {
				String name = (String) map.get("name");
				String version = (String) map.get("version");
				name=name.trim();
				version=version.trim();
				boolean match=false;
				HashMap gitMap = new HashMap();
				for (Map map1 : fromKabanero) {
					Map metadata = (Map) map1.get("metadata");
		        	String name1 = (String) metadata.get("name");
					name1=name1.trim();
					if (name1.contentEquals(name)) {
						match=true;
					}
				}
				if (!match) {
					gitMap.put("name", name);
					gitMap.put("version", version);
					newCollections.add(gitMap);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return newCollections;
	}
	
	public static List filterDeletedCollections(List<Map> fromGit, List<Map> fromKabanero) {
		ArrayList<Map> collectionsToDelete = new ArrayList<Map>();
		String name = null;
		String version = null;
		try {
			for (Map map : fromKabanero) {
				Map metadata = (Map) map.get("metadata");
	        	name = (String) metadata.get("name");
	        	Map spec = (Map) map.get("spec");
	        	version = (String) spec.get("version");
				name=name.trim();
				HashMap kabMap = new HashMap();
				boolean match=false;
				for (Map map1 : fromGit) {
					String name1 = (String) map1.get("name");
					name1=name1.trim();
					if (name1.contentEquals(name)) {
						match=true;
					}
				}
				if (!match) {
					kabMap.put("name", name);
					kabMap.put("version", version);
					collectionsToDelete.add(kabMap);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return collectionsToDelete;
	}
	
	public static List filterVersionChanges(List<Map> fromGit, List<Map> fromKabanero) {
		ArrayList<Map> newCollections=new ArrayList<Map>();
		try {
			for (Map map : fromGit) {
				String version = (String) map.get("version");
				String name = (String) map.get("name");
				name = name.trim();
				version=version.trim();
				boolean match=true;
				HashMap gitMap = new HashMap();
				for (Map map1 : fromKabanero) {
					Map metadata = (Map) map1.get("metadata");
		        	String name1 = (String) metadata.get("name");
		        	name1=name1.trim();
					Map spec = (Map) map1.get("spec");
		        	String version1 = (String) spec.get("version");
		        	version1=version1.trim();
		        	if (name.contentEquals(name1)) {
						if (!version1.contentEquals(version)) {
							match=false;
						}
		        	}
				}
				if (!match) {
					gitMap.put("name", name);
					gitMap.put("version", version);
					newCollections.add(map);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return newCollections;
	}

}

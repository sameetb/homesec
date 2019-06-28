/**
 * 
 */
package org.sb.homesec;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;

import org.sb.libevl.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author sam
 *
 */
public class ScriptNotificationHandler
{
	private static final Logger log = LoggerFactory.getLogger(ScriptNotificationHandler.class);
	
	private final Map<Notification.Type, Set<String>> cfg;
	
	public ScriptNotificationHandler(JsonObject cfg) 
	{
		HashMap<Notification.Type, Set<String>> map = new HashMap<>();
		
		cfg.forEach((k, v) -> 
		{
			if(v instanceof JsonArray)
			{
				((JsonArray)v).forEach(jnt -> 
				{
					if(jnt instanceof JsonString)
					{
						makeEntry(map, k, jnt);
					}
				});
			}
			else if(v instanceof JsonString)
			{
				makeEntry(map, k, v);
			}
		});
		this.cfg = Collections.unmodifiableMap(map);
	}

	private void makeEntry(HashMap<Notification.Type, Set<String>> map, String k, JsonValue jnt) 
	{
		try {
			Notification.Type nt = Notification.Type.valueOf(((JsonString)jnt).getString().toUpperCase());
			Set<String> ls = map.get(nt);
			if(ls == null)
			{
				ls = new HashSet<String>();
				map.put(nt, ls);
			}
			ls.add(k);
		} 
		catch (IllegalArgumentException ill) 
		{
			throw new RuntimeException("Unsupported notification type " + jnt.toString(), ill);
		}
	}

	public void notify(Notification event) 
	{
		cfg.getOrDefault(event.type, Collections.emptySet()).stream().forEach(script -> 
		{
			try 
			{
				log.debug("Executing " + script + " for " + event.type);
				Runtime.getRuntime().exec(
						new String[]{script, event.type.toString(), event.date(), "\"" + event.msg + "\""});
			} 
			catch (Exception e) 
			{
				log.error("Failed to execute " + script + " for " + event.type, e);
			}
		});
	}
}

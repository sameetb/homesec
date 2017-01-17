/**
 * 
 */
package org.sb.homesec;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map.Entry;
import java.util.stream.Stream;

import javax.json.JsonObject;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriBuilderException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author sam
 *
 */
public class Foscam 
{
	private static final Logger log = LoggerFactory.getLogger(Foscam.class);
	
	private final String name;
	private final JsonObject cfg;
	
	public Foscam(String name, JsonObject cfg)
	{
		this.name = name;
		this.cfg = cfg;
		log.trace("Foscam " + name);
	}
	
	public void awayarm() throws IOException
	{
		Commands cmds = new Commands();
		cmds(cmds.enableMotionDetectConfig(), cmds.enableAudioAlarmConfig());
	}
	
	public void disarm() throws IOException
	{
		Commands cmds = new Commands();
		cmds(cmds.disableMotionDetectConfig(), cmds.disableAudioAlarmConfig());
	}
	
	public void cmds(Stream<Entry<String, String>>... cmds) throws IOException
	{
		for(Stream<Entry<String, String>> cmd : cmds)
		{
			log.info("Response:" + sendCommand(cmd));
		}
	}
	
	String toString(URL url)
	{
		return url.getProtocol() + "://" + url.getHost() + ":" + url.getPort() + "/" + url.getPath();
	}
	
	private String sendCommand(Stream<Entry<String, String>> cmd) throws IOException
	{
		try 
		{
			URL url = makeGetURL(cmd);
			
			log.info("Executing GET " + toString(url));
			if(log.isTraceEnabled()) log.trace("URL:" + url);
			
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			  
			conn.setRequestMethod(HttpMethod.GET);
			conn.setReadTimeout(10*1000);
			conn.connect();
			
			StringBuilder sb;

			log.info("Got response code: " + conn.getResponseCode());
			
			try 
			{
				BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
				try 
				{
					sb = new StringBuilder();
					String line = null;
					while ((line = br.readLine()) != null)
						sb.append(line + "\n");
				} 
				finally 
				{
					br.close();
				} 
			} 
			finally 
			{
				conn.disconnect();
			}
			return sb.toString();
		} 
		catch (IOException e) 
		{
			log.error("Failed to send command");
			throw e;
		}
	}
	
	public static class Commands{
		
		public Stream<Entry<String, String>> disableMotionDetectConfig()
		{
			return Stream.of(
				pair("cmd", "setMotionDetectConfig"),
				pair("isEnable", "0"));
		}
				
		public Stream<Entry<String, String>> enableMotionDetectConfig()
		{
			return Stream.of(
				pair("cmd", "setMotionDetectConfig"),
				pair("isEnable", "1"),
				pair("linkage", "138"),
				pair("snapInterval", "2"),
				pair("sensitivity", "1"),
				pair("triggerInterval", "10"),
				pair("isMovAlarmEnable", "1"),
				pair("isPirAlarmEnable", "1"),
				pair("schedule0", "281474976710655"),
				pair("schedule1", "281474976710655"),
				pair("schedule2", "281474976710655"),
				pair("schedule3", "281474976710655"),
				pair("schedule4", "281474976710655"),
				pair("schedule5", "281474976710655"),
				pair("schedule6", "281474976710655"),
				pair("area0", "1023"),
				pair("area1", "1023"),
				pair("area2", "1023"),
				pair("area3", "1023"),
				pair("area4", "1023"),
				pair("area5", "1023"),
				pair("area6", "1023"),
				pair("area7", "1023"),
				pair("area8", "1023"),
				pair("area9", "1023"));
		}
		
		public Stream<Entry<String, String>> disableAudioAlarmConfig()
		{
			return Stream.of(
					pair("cmd", "setAudioAlarmConfig"),
					pair("isEnable", "0"));
			
		}
		
		public Stream<Entry<String, String>> enableAudioAlarmConfig()
		{
			return Stream.of(
				pair("cmd", "setAudioAlarmConfig"),
				pair("isEnable", "1"),
				pair("linkage", "138"),
				pair("snapInterval", "2"),
				pair("sensitivity", "1"),
				pair("triggerInterval", "10"),
				pair("schedule0", "281474976710655"),
				pair("schedule1", "281474976710655"),
				pair("schedule2", "281474976710655"),
				pair("schedule3", "281474976710655"),
				pair("schedule4", "281474976710655"),
				pair("schedule5", "281474976710655"),
				pair("schedule6", "281474976710655"));
		}
	}
	
	private URL makeGetURL(Stream<Entry<String, String>> params)
	{
		UriBuilder ub = UriBuilder.fromUri(cfg.getString("uri"));
		
		auth(params).filter(p -> p.getValue() != null)
					.forEach(p -> ub.queryParam(p.getKey(), p.getValue()));
		
		try 
		{
			return ub.build().toURL();
		} 
		catch (MalformedURLException | IllegalArgumentException | UriBuilderException e) 
		{
			throw new RuntimeException("Failed to create command url with " + ub, e);
		}
	}
	
	private Stream<Entry<String, String>> auth(Stream<Entry<String, String>> params)
	{
		return Stream.concat(params, 
   							 Stream.of(
									pair("usr", cfg.getString("username")),
									pair("pwd", cfg.getString("password"))));
	}
	
	private static <K, V> Entry<K, V> pair(K k, V v)
    {
        return new SimpleImmutableEntry<>(k, v);
    }

	public String getName() 
	{
		return name;
	}
	
}

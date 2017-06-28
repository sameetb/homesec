/**
 * 
 */
package org.sb.homesec;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
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
		cmds(cmds.enableMotionDetectConfig(cfg), cmds.enableAudioAlarmConfig(cfg));
	}
	
	public void disarm() throws IOException
	{
		Commands cmds = new Commands();
		cmds(cmds.disableMotionDetectConfig(), cmds.disableAudioAlarmConfig());
	}
	
	public void snapPicture(BiConsumer<byte[], Integer> reader) throws IOException
	{
		Commands cmds = new Commands();
		sendCommand(cmds.snapPicture2(), (rc, is) -> 
											{
												if(rc/100 == 2)
													try
													{
														byte[] buff = new byte[2048];
														int size;
														while ((size = is.read(buff)) > 0)
															reader.accept(buff, size);
													}
													catch(IOException io)
													{
														throw new RuntimeException(io);
													}
													finally
													{
														try 
														{
															is.close();
														} 
														catch (Exception e) 
														{
														}
													}
												else
													log.info("Request not successful " + rc);
												return null;
											});
	}

	public void snapPicture(Consumer<InputStream> reader) throws IOException
	{
		Commands cmds = new Commands();
		sendCommand(cmds.snapPicture2(), (rc, is) -> 
											{
												if(rc/100 == 2)
													reader.accept(is);
												else
													log.info("Request not successful " + rc);
												return null;
											});
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
		return sendCommand(cmd, (rc, is) -> respToString(is));
	}
	
	private String respToString(InputStream is)
	{
		BufferedReader br = new BufferedReader(new InputStreamReader(is));
		try 
		{
			StringBuilder sb = new StringBuilder();
			String line = null;
			while ((line = br.readLine()) != null)
				sb.append(line).append('\n');
			return sb.toString();
		} 
		catch(IOException io)
		{
			throw new RuntimeException(io);
		}
		finally 
		{
			try 
			{
				br.close();
			} 
			catch (Exception e) 
			{
			}
		}
	}
	
	private <T> T sendCommand(Stream<Entry<String, String>> cmd, BiFunction<Integer, InputStream, T> respProc) throws IOException
	{
		try 
		{
			URL url = makeGetURL(cmd);
			
			log.info("Executing GET " + toString(url));
			if(log.isTraceEnabled()) log.trace("URL:" + url);
			
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			  
			conn.setRequestMethod(HttpMethod.GET);
			conn.setReadTimeout(cfg.getInt("readTimeout", 10*1000));
			conn.connect();
			
			int rc = conn.getResponseCode();
			log.info("Got response : " + rc + " " + conn.getResponseMessage());
			
			try 
			{
				if(Stream.of(4, 5).anyMatch(c -> c == rc/100))
					throw new IOException(rc + " " + conn.getResponseMessage() + "\n" + respToString(conn.getInputStream()));
				return respProc.apply(rc, conn.getInputStream());
			} 
			catch(RuntimeException re)
			{
				if(re.getCause() instanceof IOException)
					throw (IOException)re.getCause();
				throw re;
			}
			finally 
			{
				conn.disconnect();
			}
		} 
		catch (IOException e) 
		{
			log.error("Command execution failed");
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
				
		public Stream<Entry<String, String>> enableMotionDetectConfig(JsonObject cfg)
		{
			Optional<JsonObject> omdc = Optional.ofNullable(cfg.getJsonObject("setMotionDetectConfig"));
			
			return Stream.of(
				pair("cmd", "setMotionDetectConfig"),
				pair("isEnable", "1"),
				pair("linkage", "12", omdc),
				pair("snapInterval", "2", omdc),
				pair("sensitivity", "0", omdc),
				pair("triggerInterval", "10", omdc),
				pair("isMovAlarmEnable", "1", omdc),
				pair("isPirAlarmEnable", "1", omdc),
				pair("schedule0", "281474976710655", omdc),
				pair("schedule1", "281474976710655", omdc),
				pair("schedule2", "281474976710655", omdc),
				pair("schedule3", "281474976710655", omdc),
				pair("schedule4", "281474976710655", omdc),
				pair("schedule5", "281474976710655", omdc),
				pair("schedule6", "281474976710655", omdc),
				pair("area0", "1023", omdc),
				pair("area1", "1023", omdc),
				pair("area2", "1023", omdc),
				pair("area3", "1023", omdc),
				pair("area4", "1023", omdc),
				pair("area5", "1023", omdc),
				pair("area6", "1023", omdc),
				pair("area7", "1023", omdc),
				pair("area8", "1023", omdc),
				pair("area9", "1023", omdc));
		}
		
		public Stream<Entry<String, String>> disableAudioAlarmConfig()
		{
			return Stream.of(
					pair("cmd", "setAudioAlarmConfig"),
					pair("isEnable", "0"));
			
		}
		
		public Stream<Entry<String, String>> enableAudioAlarmConfig(JsonObject cfg)
		{
			Optional<JsonObject> oaac = Optional.ofNullable(cfg.getJsonObject("setAudioAlarmConfig"));
			return Stream.of(
				pair("cmd", "setAudioAlarmConfig"),
				pair("isEnable", "1"),
				pair("linkage", "12", oaac),
				pair("snapInterval", "2", oaac),
				pair("sensitivity", "3", oaac),
				pair("triggerInterval", "10", oaac),
				pair("schedule0", "281474976710655", oaac),
				pair("schedule1", "281474976710655", oaac),
				pair("schedule2", "281474976710655", oaac),
				pair("schedule3", "281474976710655", oaac),
				pair("schedule4", "281474976710655", oaac),
				pair("schedule5", "281474976710655", oaac),
				pair("schedule6", "281474976710655", oaac));
		}
		
		public Stream<Entry<String, String>> snapPicture2()
		{
			return Stream.of(pair("cmd", "snapPicture2"));
		}

		public Stream<Entry<String, String>> reboot()
		{
			return Stream.of(pair("cmd", "rebootSystem"));
		}
	}
	
    private static String jsonString(Optional<JsonObject> obj, String name, String dflt)
    {
        return obj.flatMap(c -> Optional.ofNullable(c.getJsonString(name))).map(jn -> jn.getString()).orElse(dflt);
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

	private static Entry<String, String> pair(String k, String v, Optional<JsonObject> obj)
    {
        return pair(k, jsonString(obj, k, v));
    }
	
	public String getName() 
	{
		return name;
	}
	
}

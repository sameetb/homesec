/**
 * 
 */
package org.sb.homesec;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URI;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Date;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriBuilderException;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.sb.libevl.Lazy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * @author sam
 *
 */
public class Foscam 
{
	private static final Logger log = LoggerFactory.getLogger(Foscam.class);
	
	public enum AlarmState {DISABLED, NONE, ALARM};
	
	public enum Model {C1, FI9900};
	
	private final String name;
	private final JsonObject cfg;
	private final Model model; 
	
	private final Lazy<ExecutorService> exec = Lazy.wrap(Executors::newCachedThreadPool); 
	
	public Foscam(String name, JsonObject cfg)
	{
		this.name = name;
		this.cfg = cfg;
		this.model = Optional.ofNullable(cfg.getJsonString("model")).map(js -> js.getString()).map(Model::valueOf).orElse(Model.C1);
		log.trace("Foscam " + name + "(" + model.name() + ")");
	}
	
	public void awayarm() throws IOException
	{
		Commands cmds = new Commands();
		cmd(model == Model.FI9900 ? cmds.setMotionDetectConfig1(cfg) : cmds.setMotionDetectConfig(cfg));
		cmd(cmds.setAudioAlarmConfig(cfg));
	}
	
	public void disarm() throws IOException
	{
		Commands cmds = new Commands();
		cmd(model == Model.FI9900 ? cmds.disableMotionDetectConfig1() : cmds.disableMotionDetectConfig());
		cmd(cmds.disableAudioAlarmConfig());
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
	
	public void cmd(Stream<Entry<String, String>> cmd) throws IOException
	{
		log.info("Response:" + sendCommand(cmd));
	}
	
	private static String toString(URL url)
	{
		return url.getProtocol() + "://" + url.getHost() + ":" + url.getPort() + "/" + url.getPath() + "?" + queryToString(url);
	}
	
	private static String queryToString(URL url) {
		return Stream.of(url.getQuery().split("&"))
				     .filter(parm -> !parm.startsWith("usr=") && !parm.startsWith("pwd="))
				     .collect(Collectors.joining("&"));
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
			
			final HttpURLConnection conn = connect(url);
			
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

	private HttpURLConnection connect(URL url) throws IOException, ProtocolException 
	{
		int retries = cfg.getInt("connectAttempts", 3);
		
		while(retries-- > 0)
		{
			try 
			{
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				conn.setRequestMethod(HttpMethod.GET);
				conn.setReadTimeout(cfg.getInt("readTimeout", 10 * 1000));
				conn.setConnectTimeout(cfg.getInt("connectTimeout", 60 * 1000));
				conn.connect();
				return conn;
			} 
			catch (ConnectException ce) 
			{
				if(retries == 0) throw ce;
				log.error("Failed to connect to " + url + ", retrying ...", ce);
			}
		}
		throw new ConnectException("All attempts to connect to " + url + " failed");
	}
	
	public static class Commands{
		
		public Stream<Entry<String, String>> disableMotionDetectConfig()
		{
			return Stream.of(
				pair("cmd", "setMotionDetectConfig"),
				pair("isEnable", "0"));
		}
				
		public Stream<Entry<String, String>> setMotionDetectConfig(JsonObject cfg)
		{
			Optional<JsonObject> omdc = Optional.ofNullable(cfg.getJsonObject("setMotionDetectConfig"));
			
			return Stream.of(
				pair("cmd", "setMotionDetectConfig"),
				pair("isEnable", "1"),
				pair("linkage", "12", omdc),
				pair("snapInterval", "5", omdc),
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
		
		public Stream<Entry<String, String>> getMotionDetectConfig()
		{
			return Stream.of(pair("cmd", "getMotionDetectConfig"));
		}
		
		public Stream<Entry<String, String>> disableAudioAlarmConfig()
		{
			return Stream.of(
					pair("cmd", "setAudioAlarmConfig"),
					pair("isEnable", "0"));
			
		}
		
		public Stream<Entry<String, String>> setAudioAlarmConfig(JsonObject cfg)
		{
			Optional<JsonObject> oaac = Optional.ofNullable(cfg.getJsonObject("setAudioAlarmConfig"));
			return Stream.of(
				pair("cmd", "setAudioAlarmConfig"),
				pair("isEnable", "1"),
				pair("linkage", "12", oaac),
				pair("snapInterval", "5", oaac),
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
		
		public Stream<Entry<String, String>> getAudioAlarmConfig()
		{
			return Stream.of(pair("cmd", "getAudioAlarmConfig"));
		}
		
		public Stream<Entry<String, String>> snapPicture2()
		{
			return Stream.of(pair("cmd", "snapPicture2"));
		}

		public Stream<Entry<String, String>> reboot()
		{
			return Stream.of(pair("cmd", "rebootSystem"));
		}

		public Stream<Entry<String, String>> name()
		{
			return Stream.of(pair("cmd", "getDevName"));
		}
	
		public Stream<Entry<String, String>> state()
		{
			return Stream.of(pair("cmd", "getDevState"));
		}
	
		public Stream<Entry<String, String>> disableMotionDetectConfig1()
		{
			return Stream.of(
				pair("cmd", "setMotionDetectConfig1"),
				pair("isEnable", "0"));
		}
				
		public Stream<Entry<String, String>> setMotionDetectConfig1(JsonObject cfg)
		{
			Optional<JsonObject> omdc = Optional.ofNullable(cfg.getJsonObject("setMotionDetectConfig1"));
			
			return Stream.of(
				pair("cmd", "setMotionDetectConfig1"),
				pair("isEnable", "1"),
				pair("linkage", "12", omdc),
				pair("snapInterval", "5", omdc),
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
				pair("x1", "4050", omdc),
				pair("y1", "3600", omdc),
				pair("width1", "875", omdc),
				pair("height1", "6400", omdc),
				pair("threshold1", "50", omdc),
				pair("sensitivity1", "4", omdc),
				pair("valid1", "1", omdc),
				pair("x2", "4920", omdc),
				pair("y2", "6200", omdc),
				pair("width2", "1000", omdc),
				pair("height2", "1300", omdc),
				pair("threshold2", "50", omdc),
				pair("sensitivity2", "4", omdc),
				pair("valid2", "1", omdc),
				pair("x3", "4920", omdc),
				pair("y3", "8300", omdc),
				pair("width3", "1000", omdc),
				pair("height3", "1700", omdc),
				pair("threshold3", "50", omdc),
				pair("sensitivity3", "4", omdc),
				pair("valid3", "1", omdc));
		}
		
		public Stream<Entry<String, String>> getMotionDetectConfig1()
		{
			return Stream.of(pair("cmd", "getMotionDetectConfig1"));
		}
	
	}
	
    private static String jsonString(Optional<JsonObject> obj, String name, String dflt)
    {
        return obj.flatMap(c -> Optional.ofNullable(c.getJsonString(name))).map(jn -> jn.getString()).orElse(dflt);
    }
    
	private URL makeGetURL(Stream<Entry<String, String>> params)
	{
		UriBuilder ub = UriBuilder.fromUri(uri());
		
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

	private String uri() {
		return Objects.requireNonNull(cfg.getJsonString("uri"), "No uri for " + cfg).getString();
	}
	
	private Stream<Entry<String, String>> auth(Stream<Entry<String, String>> params)
	{
		return Stream.concat(params, 
   							 Stream.of(
									pair("usr", cfgParam("username")),
									pair("pwd", cfgParam("password"))));
	}

	private String cfgParam(String parmNm) {
		return Optional.ofNullable(cfg.getJsonString(parmNm)).map(JsonString::getString).orElse(null);
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
	
	public String getDescription()
	{
		return jsonString(Optional.of(cfg), "description", "");
	}

	public JsonObject getStatus() throws IOException
	{
		return sendCommand(new Commands().state(), (rc, is) -> enrich(dom2json(parseXml(is))));
	}

	private Document parseXml(InputStream is) 
	{
		try
		{
			return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
		}
		catch(IOException | SAXException | ParserConfigurationException io)
		{
			throw new RuntimeException(io);
		}
		finally
		{
			try 
			{
				is.close();
			} 
			catch (IOException e) 
			{
				log.error("Failed to close response stream", e);
			}
		}
	}
	
	private JsonObjectBuilder dom2json(Document doc)
	{
		JsonObjectBuilder job = Json.createObjectBuilder();
		Node child = doc.getDocumentElement().getFirstChild();
		while(child != null)
		{
			if(child instanceof Element)
			{
				Element elem = (Element)child;
				job.add(elem.getTagName(), elem.getTextContent());
			}
			child = child.getNextSibling();
		}
		return job;
	}
	
	private JsonObject enrich(JsonObjectBuilder job)
	{
		job.add("name", getName());
		job.add("description", getDescription());
		return job.build();
	}

	public void record(final int durationSecs) throws IOException
	{
		final Optional<JsonObject> rtspCfg = Optional.ofNullable(cfg.getJsonObject("rtsp"));
		
		UriBuilder ub = rtspCfg.flatMap(jo -> Optional.ofNullable(jo.getJsonString("uri")))
							.map(JsonString::getString)
							.map(UriBuilder::fromUri)
							.orElseGet(() -> UriBuilder.fromUri(uri())
													  .scheme("rtsp")
													  .replacePath("videoSub"));
		
		rtspCfg.flatMap(jo -> Optional.ofNullable(jo.getJsonNumber("port"))).ifPresent(port -> ub.port(port.intValue()));
		
		final URI rtspUri = ub.build();
		log.info("Recording video from " + rtspUri); 
		
		final String prefix = makePrefix(jsonString(rtspCfg, "prefix", "Record"));
		final File recordDir = new File(jsonString(rtspCfg, "recordDir", System.getProperty("java.io.tmpdir")));
		final Process orstp = new ProcessBuilder(jsonString(rtspCfg, "openrstp", "/usr/bin/openRTSP"),
													jsonString(rtspCfg, "format", "-4"),
													"-w", jsonString(rtspCfg, "width", "1280"),
													"-h", jsonString(rtspCfg, "height", "720"),
													"-f", jsonString(rtspCfg, "framerate", "15"),
													"-P", String.valueOf(Math.min(Math.max(20, durationSecs), 60)),
													"-d", String.valueOf(Math.max(durationSecs, 60)),
													"-Q",
													"-V",
													"-F", prefix,
													"-u", jsonString(rtspCfg, "username", cfgParam("username")), 
														  jsonString(rtspCfg, "password", cfgParam("password")),
													rtspUri.toString())
											.directory(recordDir)
											.start();
		
		exec.get().execute(() -> {
			if(orstp.isAlive())
			{
			    final long window = Math.max(Math.round(durationSecs*2), 60)*1000;
				log.info("Video client for " + getName() + " started, will wait not more than " + window + "ms");
				try(BufferedReader es = new BufferedReader(new InputStreamReader(orstp.getErrorStream())))
				{
			        final long life = System.currentTimeMillis() + window;
					while(life >= System.currentTimeMillis()) 
					{
					   String line;
					   while((line = es.readLine()) != null)
						log.info(line);

					   if(!orstp.isAlive()){
						 log.info("Video client has exited");
						 break;
                       }

					   Thread.sleep(1000L);
					}
				}
				catch(Exception ex)
				{
					log.error("Failed to read streams", ex);
				}
			}
			
			if(orstp.isAlive())
			{
				log.info("Video client for " + getName() + " still running, terminating ...");
				orstp.destroy(); //may cause file corruption, need to SIGHUP instead
			}
			else
				log.info("Completed recording video for " + getName() + ", exit code " + orstp.exitValue());
			
			String notifyScript = jsonString(rtspCfg, "notify", null);
			if(notifyScript != null)
				for(File file : recordDir.listFiles((dir, name) -> name.startsWith(prefix)))
						notifyRecord(notifyScript, file.getAbsolutePath());
			else
				log.info("No notification command configured");
		});
	}


	public void stream(final int durationSecs, BiConsumer<byte[], Integer> reader) throws IOException
	{
		final Optional<JsonObject> rtspCfg = Optional.ofNullable(cfg.getJsonObject("rtsp"));
		
		UriBuilder ub = rtspCfg.flatMap(jo -> Optional.ofNullable(jo.getJsonString("uri")))
							.map(JsonString::getString)
							.map(UriBuilder::fromUri)
							.orElseGet(() -> UriBuilder.fromUri(uri())
													  .scheme("rtsp")
													  .replacePath("videoSub"));
		
		rtspCfg.flatMap(jo -> Optional.ofNullable(jo.getJsonNumber("port"))).ifPresent(port -> ub.port(port.intValue()));
		
		final URI rtspUri = ub.build();
		
		final File recordDir = new File(System.getProperty("java.io.tmpdir"));
		final File outFile = File.createTempFile( getName(), ".mp4");
		log.info("Streaming video from " + rtspUri + " to " + outFile); 
		final Process orstp = new ProcessBuilder(jsonString(rtspCfg, "openrstp", "/usr/bin/openRTSP"),
													jsonString(rtspCfg, "format", "-4"),
													"-w", jsonString(rtspCfg, "width", "1280"),
													"-h", jsonString(rtspCfg, "height", "720"),
													"-f", jsonString(rtspCfg, "framerate", "15"),
													"-d", String.valueOf(Math.max(durationSecs, 30)),
													"-Q",
													"-V",
													"-u", jsonString(rtspCfg, "username", cfgParam("username")), 
														  jsonString(rtspCfg, "password", cfgParam("password")),
													rtspUri.toString())
											.directory(recordDir)
											.redirectOutput(outFile)
											.start();
		
		if(orstp.isAlive())
			log.info("Video client for " + getName() + " started");
		
		exec.get().execute(() -> {
			try(BufferedReader es = new BufferedReader(new InputStreamReader(orstp.getErrorStream())))
			{
				String line;
				while((line = es.readLine()) != null) log.info(line);
			}
			catch(Exception ex)
			{
				log.error("Failed to read error stream", ex);
			}
		});
		
		long life = Math.max(Math.round(durationSecs*2), 30)*1000;
		while(orstp.isAlive() && life > 0 )
		{
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				
			}
			life -= 100;
		}
		
		
		if(orstp.isAlive())
		{
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				
			}
			log.info("Video client for " + getName() + " still running, terminating ...");
			orstp.destroy(); //SIGHUP .... zombie ??
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				
			}
		}
		else
			log.info("Video client for " + getName() + ", exit code " + orstp.exitValue());
			
		try(InputStream is = new FileInputStream(outFile))
		{
			byte[] buff = new byte[4096];
			int size = 0;
			do
			{
				if ((size = is.read(buff)) > 0)
					reader.accept(buff, size);
			}
			while(size >= 0);
		}
		catch(Exception ex)
		{
			log.error("Failed to read input stream", ex);
		}
		
		try {
			outFile.delete();
		} catch (Exception e) {
			log.error("Failed to delete " + outFile, e);
		}
		
		log.info("Completed streaming video for " + getName() + ", exit code " + orstp.exitValue());
		
	}
	
	private static String makePrefix(String jsonString) {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
		return jsonString + "_" + sdf.format(new Date());
	}
	
	private static void notifyRecord(String command, String absolutePath)
	{
		try {
			new ProcessBuilder(command, absolutePath).start();
			log.info("Notified for " + absolutePath);
		} catch (Exception e) {
			log.error("Failed to execute notify command for " + absolutePath, e);
		}
	}

	public boolean isOutdoor()
	{
		return cfg.getBoolean("outdoor", false);
	}

	public JsonObject getMotionDetectConfig() throws IOException
	{
		Stream<Entry<String, String>> mdc = (model == Model.FI9900) ? new Commands().getMotionDetectConfig1(): new Commands().getMotionDetectConfig();
		return sendCommand(mdc, (rc, is) -> dom2json(parseXml(is)).build());
	}

	public JsonObject getAudioAlarmConfig() throws IOException
	{
		return sendCommand(new Commands().getAudioAlarmConfig(), (rc, is) -> dom2json(parseXml(is)).build());
	}
}

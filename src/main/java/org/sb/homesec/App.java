package org.sb.homesec;

import java.io.File;
import java.io.FileReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;
import javax.websocket.server.ServerContainer;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfig;

import org.apache.cxf.jaxrs.servlet.CXFNonSpringJaxrsServlet;
import org.eclipse.jetty.security.Authenticator;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer;
import org.sb.homesec.rs.HomesecApp;
import org.sb.homesec.ws.EndpointConfigurator;
import org.sb.homesec.ws.EventSocket;
import org.sb.libevl.Commands;
import org.sb.libevl.DscPanel;
import org.sb.libevl.EvlConnection;
import org.sb.libevl.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App 
{
    private static final Logger log = LoggerFactory.getLogger(App.class);

    private final Server server;
    private final DscPanel panel = new DscPanel(this::sendNotification);
    private final Optional<JsonObject> cfg;
    private final EndpointConfigurator epCfg = new EndpointConfigurator();
    private final Supplier<EvlConnection> conn = ConnMgr.wrap(() ->  loadStatus(evlConnect()), 
										    							evl -> evl.isAlive(), evl -> evl.close());
    private final Optional<ScriptNotificationHandler> sh;
    
    private final Set<Foscam> cams;
    
    private final ScheduledExecutorService exec;
    
    public static void main( String[] args ) throws Exception
    {
        new App(args).start();
    }
    
    public App( String[] args ) throws Exception
    {
        cfg = readCfg(args);
              
        int port = optJsonInt(cfg, "port").orElse(8080);
    	server = new Server(optJsonString(cfg, "ipAddress").map(ip -> new InetSocketAddress(ip, port))
    	                                        .orElseGet(() -> new InetSocketAddress(port)));

        ResourceHandler staticContent = new ResourceHandler();
        staticContent.setWelcomeFiles(new String[]{ "index.html" });

        staticContent.setBaseResource(
                        optJsonString(cfg, "webpath")
                           .map(path -> 
                                {
                                    try
                                    {
                                        return Resource.newResource(path);
                                    }
                                    catch(RuntimeException re)
                                    {
                                        throw re;
                                    }
                                    catch(Exception io)
                                    {
                                        throw new RuntimeException("Failed to read webpath", io);
                                    }                        
                                })
                           .orElseGet(() -> Resource.newClassPathResource("web")));

        ContextHandler staticContext = new ContextHandler();
        staticContext.setContextPath( "/homesec" );
        staticContext.setHandler(staticContent);
        
		cams = cfg.filter(c -> !c.isNull("foscams"))
				.map(c -> c.getJsonObject("foscams"))
				.map(cams -> cams.entrySet().stream()
								  .filter(es -> es.getValue().getValueType() ==  JsonValue.ValueType.OBJECT)
								  .map(es -> new Foscam(es.getKey(), (JsonObject)es.getValue()))
								  .collect(Collectors.collectingAndThen(Collectors.toSet(), Collections::unmodifiableSet)))
				.orElseGet(() -> Collections.emptySet());
		
		exec = Executors.newScheduledThreadPool(optJsonInt(cfg, "minBackgroundThreads").orElse(3));
		
        final HomesecApp rsApp = new HomesecApp(panel, conn, epCfg, cams);
        
		ServletHolder rsServlet = new ServletHolder(new CXFNonSpringJaxrsServlet(rsApp));
        rsServlet.setName("api");
        rsServlet.setDisplayName("api");
        ServletContextHandler rsContext = new ServletContextHandler();
        rsContext.setContextPath("/api");
        rsContext.addServlet(rsServlet, "/*");
        
        ServletContextHandler wsContext = new ServletContextHandler(ServletContextHandler.SESSIONS);
        wsContext.setContextPath("/ws");
        
        HandlerList handlers = new HandlerList();
        handlers.setHandlers(new Handler[] { rsContext, wsContext, staticContext, new DefaultHandler() });
        
        server.setHandler(cfg.flatMap(c -> Optional.ofNullable(c.getJsonObject("security")))
					           .map(sec -> makeSecurityHandler(sec))
					           .map(sh -> {sh.setHandler(handlers);return (Handler)sh;})
					           .orElse(handlers));

        ServerContainer wsCont = WebSocketServerContainerInitializer.configureContext(wsContext);
		wsCont.addEndpoint( ServerEndpointConfig.Builder.create(EventSocket.class, 
													EventSocket.class.getAnnotation(ServerEndpoint.class).value())
						        			.configurator(epCfg).build());
		
		server.addBean(new QueuedThreadPool(optJsonInt(cfg, "maxThreads").orElse(6), 
											optJsonInt(cfg, "minThreads").orElse(2)));

		sh = cfg.flatMap(c -> Optional.ofNullable(c.getJsonObject("notifications"))).map(cn -> new ScriptNotificationHandler(cn));
		
    }

	public void start() throws Exception
    {
        server.start();
        if(connOnInit()) 
	        conn.get();
        
        Runtime.getRuntime().addShutdownHook(new Thread(() ->  
            {
                try
                {
                    exec.shutdown();
                    panel.close();
                    conn.get().close();
                }
                catch(Exception ex)
                {
                    log.warn("", ex);
                }
                
                try
                {
                    server.stop();
                    exec.awaitTermination(30, TimeUnit.SECONDS);
                }
                catch(Exception ex)
                {
                    log.warn("", ex);
                }
                System.console().flush();
            }));
		
        setupKeepAlive();
        
        server.join();
    }

	private boolean connOnInit() 
	{
		return !cfg.flatMap(c -> Optional.ofNullable(c.getJsonObject("envisalink")))
		           .flatMap(c -> Optional.ofNullable(c.get("connectOnInit")))
		           .map(jbool -> jbool == JsonValue.FALSE).orElse(false);
	}

    private static Optional<String> optJsonString(Optional<JsonObject> obj, String name)
    {
        return obj.flatMap(c -> Optional.ofNullable(c.getJsonString(name))).map(jn -> jn.getString());
    }

    private static Optional<Integer> optJsonInt(Optional<JsonObject> obj, String name)
    {
        return obj.flatMap(c -> Optional.ofNullable(c.getJsonNumber(name))).map(jn -> jn.intValue());
    }
    
    private static Optional<JsonObject> readCfg(String[] args)
    {
        return Stream.of(Optional.of(args).filter(ar -> ar.length > 0).map(ar -> ar[0]), 
                         Optional.of("cfg/homesec.json"),
                         Optional.of("homesec.json"))
                      .map(ocfgNm -> ocfgNm.map(nm -> new File(nm)))
                      .filter(ocfg -> ocfg.isPresent())
                      .map(ocfg -> ocfg.get())
                      .filter(cfg ->  cfg.exists())
                      .map(cfg -> 
                        {
                            try
                            {
                                log.info("Reading cfg from {}", cfg);
                                JsonReader jr = Json.createReader(new FileReader(cfg));
                                JsonObject jo = jr.readObject();
                                jr.close();
                                return jo;
                            }
                            catch(RuntimeException re)
                            {
                            	log.error("Failed to read cfg", re);
                                throw re;
                            }
                            catch(Exception io)
                            {
                            	log.error("Failed to read cfg", io);
                                throw new RuntimeException("Failed to read cfg", io);
                            }                        
                        })
                      .findFirst();
    }   
    
    private EvlConnection evlConnect()
    {
        try
        {         
            Optional<JsonObject> dsc = cfg.flatMap(c -> Optional.ofNullable(c.getJsonObject("envisalink")));
            return new EvlConnection(InetAddress.getByName(optJsonString(dsc, "hostname").orElse("envisalink")), 
                                        optJsonInt(dsc, "port"), 
                                        () -> getPassword(dsc), 
                                        panel.stateHandler);
        }
        catch(RuntimeException re)
        {
            throw re;
        }
        catch(Exception io)
        {
            throw new RuntimeException("Failed to create a connection to envisalink", io);
        }       
    }

	private EvlConnection loadStatus(EvlConnection evl) 
	{
		exec.submit(() -> {
			try 
			{
				evl.send(new Commands().statusReport());
			} 
			catch (Exception e) 
			{
				log.error("Failed to send statusReport command", e);
			}
		});
		return evl;
	}
    
    protected void sendNotification(Notification note)
    {
        epCfg.notify(note.toJson());
        sh.ifPresent(s -> s.notify(note));
        notifyCams(note);
    }

	private void notifyCams(Notification note) 
	{
		if(note.type == Notification.Type.ARM)
		try
        {
        	String[] msgs = note.msg.split("\\.");
        	
        	DscPanel.PartitionState ps = DscPanel.PartitionState.valueOf(msgs[0]);
			Consumer<Foscam> armCam = cam -> {
				try 
				{
					log.info("Away arming " + cam.getName());
					cam.awayarm();
				} 
				catch (Exception e) 
				{
					log.error("Failed to away arm " + cam.getName(), e);
				};
			};
			
        	switch(ps)
        	{
        		case ARMED:
	        		{
	        			DscPanel.PartitionArmState as = DscPanel.PartitionArmState.valueOf(msgs[1]);
	        			switch(as)
	        			{
	        				case AWAYARMED: 
	        				case ZERO_ENTRY_AWAY:
	        					asyncExec(cams, armCam);
	        					break;
	        				case STAYARMED:
	        				case ZERO_ENTRY_STAY:
	        					asyncExec(cams.stream().filter(Foscam::isOutdoor).collect(Collectors.toSet()), armCam);
	        					break;
	        				default:
	        	        		log.trace("Nothing to do about " + as);
	        			}
	        			
	        		}
        			break;
        		case DISARMED: 
        			asyncExec(cams, cam ->	{
												try 
												{
													log.info("Disarming " + cam.getName());
													cam.disarm();
												} 
												catch (Exception e) 
												{
													log.error("Failed to disarm " + cam.getName(), e);
												}
											});
					break;
	        	default:
	        		log.trace("Nothing to do about " + ps);
        	}
        }
		catch(IllegalArgumentException ill)
		{
			log.error("Unsupported notification msg", ill);
		}
	}    
    
    private static String getPassword(Optional<JsonObject> dsc)
    {
         return optJsonString(dsc, "password").orElseGet(() ->
                                String.valueOf(System.console().readPassword("Envisalink Password:")));
    }

    private SecurityHandler makeSecurityHandler(JsonObject sec) 
    {
    	Authenticator au = makeAuthenticator(sec);
    	
    	ConstraintSecurityHandler security = new ConstraintSecurityHandler();
    	
    	Constraint constraint = new Constraint();
        constraint.setName("auth");
        constraint.setAuthenticate(true);
        constraint.setRoles(new String[] { "*", "**" });

        ConstraintMapping mapping = new ConstraintMapping();
        mapping.setPathSpec("/*");
        mapping.setConstraint(constraint);

        security.setConstraintMappings(Collections.singletonList(mapping));
        security.setAuthenticator(au);

        Optional.ofNullable(sec.getJsonObject("loginService"))
        		.map(ls -> makeLoginService(ls))
        		.ifPresent(ls -> security.setLoginService(ls));
    	
		return security;
	}

	private Authenticator makeAuthenticator(JsonObject sec)
	{
		try 
		{
			return Authenticator.class.cast(Class.forName(sec.getString("authenticator", BasicAuthenticator.class.getName())).newInstance());
		} 
		catch (Exception e) 
		{
			throw new RuntimeException("Failed to create authenticator from "  + sec);
		}
	}

	private LoginService makeLoginService(JsonObject ls)
	{
		try 
		{
			Class<LoginService> cls = (Class<LoginService>) Class.forName(ls.getString("class"));
			return cls.getConstructor(String.class, String.class).newInstance(ls.getString("name"), ls.getString("config"));
		} 
		catch (Exception e) 
		{
			throw new RuntimeException("Failed to create login service from "  + ls, e);
		}
	}
	
	private <T> void asyncExec(Set<T> cams, Consumer<T> cons)
	{
		cams.forEach(cam -> exec.submit(() -> cons.accept(cam)));
	}

	private void setupKeepAlive() 
	{
		Integer ka = optJsonInt(cfg, "keepAliveIntervalMins").orElse(15);
        
        Random rand = new Random();
		exec.scheduleWithFixedDelay(() ->	{
												try 
												{
													conn.get().send(new Commands().poll());
													return;
												} 
												catch (Exception e) 
												{
													log.warn("Failed to poll panel", e);
												}
											},
									ka + rand.nextInt(ka), ka, TimeUnit.MINUTES);
		
		cams.forEach(cam -> 
			exec.scheduleWithFixedDelay(() ->	{
													try 
													{
														cam.cmd(new Foscam.Commands().name());
													} 
													catch (Exception e) 
													{
														log.error("Failed to send keep alive to " + cam.getName(), e);
													}
												},
										ka + rand.nextInt(ka), ka, TimeUnit.MINUTES));
	}    
}


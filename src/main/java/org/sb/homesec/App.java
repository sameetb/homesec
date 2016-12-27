package org.sb.homesec;

import javax.websocket.server.ServerContainer;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfig;

import org.apache.cxf.jaxrs.servlet.CXFNonSpringJaxrsServlet;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer;
import org.sb.homesec.rs.HomesecApp;
import org.sb.homesec.ws.EndpointConfigurator;
import org.sb.homesec.ws.EventSocket;
import javax.json.Json;
import javax.json.JsonReader;
import javax.json.JsonObject;
import java.io.File;
import java.io.FileReader;
import java.util.Optional;
import java.util.stream.Stream;
import org.sb.libevl.DscPanel;
import org.sb.libevl.Notification;
import org.sb.libevl.EvlConnection;
import org.sb.libevl.Commands;
import org.sb.libevl.Lazy;
import java.net.InetSocketAddress;
import java.net.InetAddress;

import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App 
{
    private static final Logger log = LoggerFactory.getLogger(App.class);

    private final Server server;
    private final DscPanel panel = new DscPanel(this::sendNotification);
    private final Optional<JsonObject> cfg;
    private final EndpointConfigurator epCfg = new EndpointConfigurator();
    private final Supplier<EvlConnection> conn = Lazy.wrap(() ->  evlConnect());
    
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
        
        final HomesecApp rsApp = new HomesecApp(panel, conn, epCfg);
        
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
        server.setHandler(handlers);

        ServerContainer wsCont = WebSocketServerContainerInitializer.configureContext(wsContext);
		wsCont.addEndpoint( ServerEndpointConfig.Builder.create(EventSocket.class, 
													EventSocket.class.getAnnotation(ServerEndpoint.class).value())
						        			.configurator(epCfg).build());
    }

    public void start() throws Exception
    {
        server.start();
        conn.get().send(new Commands().statusReport());
        Runtime.getRuntime().addShutdownHook(new Thread(() ->  
            {
                try
                {
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
                }
                catch(Exception ex)
                {
                    log.warn("", ex);
                }
                System.console().flush();
            }));
        server.join();
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
                                throw re;
                            }
                            catch(Exception io)
                            {
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
    
    protected void sendNotification(Notification note)
    {
        epCfg.notify(note.toJson());
    }    
    
    private static String getPassword(Optional<JsonObject> dsc)
    {
         return optJsonString(dsc, "password").orElseGet(() ->
                                String.valueOf(System.console().readPassword("Envisalink Password:")));
    }
}

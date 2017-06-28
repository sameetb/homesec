package org.sb.homesec.rs;

import java.util.function.Supplier;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.sb.libevl.Commands;
import org.sb.libevl.DscPanel;
import org.sb.libevl.EvlConnection;
import org.sb.libevl.JsonHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/dsc")
public class DscApi 
{
    private static final Logger log = LoggerFactory.getLogger(DscApi.class);
    
    private final DscPanel panel;
    private final Supplier<EvlConnection> conn;
    private final Commands cmds = new Commands();
	
	public DscApi(DscPanel panel, Supplier<EvlConnection> conn) 
	{
	    this.panel = panel;
	    this.conn = conn;
	}

	@Path("/leds")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response leds()
	{
		return Response.ok(panel.getKeypadLeds()).build();
		
	}

	@Path("/alarms")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response alarms()
	{
		return Response.ok(JsonHelper.array(panel.alarms())).build();
	}
	
	@Path("/troubles")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response troubles()
	{
		return Response.ok(JsonHelper.array(panel.troubles())).build();
	}
	
	@Path("/stayarm")
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	public Response stayarm()
	{
		log("stay arming");
		try
		{
		    conn.get().send(cmds.stayArm(1, () -> ""));
    		return Response.ok().build();
        }
    	catch(Exception ex)
    	{
    	    log("stay arming failed", ex);
    		return Response.serverError().entity(ex.getMessage()).build();
    		
    	}	
	}

	@Path("/awayarm")
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	public Response awayarm() throws Exception
	{
	    try
	    {
		    log("away arming");
		    conn.get().send(cmds.awayArm(1, () -> ""));
		    return Response.ok().build();
        }
    	catch(Exception ex)
    	{
    	    log("away arming failed", ex);
    		return Response.serverError().entity(ex.getMessage()).build();
    	}	
	}
	
	@Path("/disarm")
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	public Response disarm(String usercode) throws Exception
	{
	    try
	    {
		    log("disarming");
		    conn.get().send(cmds.disarm(1, usercode));
		    return Response.ok().build();
        }
    	catch(Exception ex)
    	{
    	    log("disarming failed", ex);
    		return Response.serverError().entity(ex.getMessage()).build();
    	}	
	}
	
	@Path("/refresh")
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	public Response refresh() throws Exception
	{
	    try
	    {
    	    log("refresh");
		    conn.get().send(cmds.statusReport());
		    return Response.ok().build();
        }
    	catch(Exception ex)
    	{
    	    log("refresh failed", ex);
    		return Response.serverError().entity(ex.getMessage()).build();
    	}	
	}

	@Path("/panic")
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	public Response panic(String type) throws Exception
	{
	    try
	    {
    	    log("panic alarm");
		    conn.get().send(cmds.triggerPanicAlarm(Commands.PanicType.valueOf(type.toUpperCase())));
		    return Response.ok().build();
        }
	    catch(IllegalArgumentException ill)
	    {
    	    log.error("unsupported panic type " + type, ill);
    		return Response.status(Status.BAD_REQUEST).entity(ill.getMessage()).build();
	    }
    	catch(Exception ex)
    	{
    	    log("panic failed", ex);
    		return Response.serverError().entity(ex.getMessage()).build();
    	}	
	}
	
    private void log(String msg)
    {
    	log.info(msg);
    }
    
    private void log(String msg, Throwable t)
    {
    	log.error(msg, t);
    }
}

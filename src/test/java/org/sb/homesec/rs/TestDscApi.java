package org.sb.homesec.rs;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.sb.homesec.ws.EventHandler;
import org.sb.libevl.Notification;

@Path("/dsc")
public class TestDscApi 
{
	private final EventHandler eh;
	private final ExecutorService asyncNotifier = Executors.newSingleThreadExecutor();

	enum LedState {OFF, ON, FLASH, UNKNOWN};
	
	private LedState ready = LedState.ON;
	private LedState armed = LedState.OFF;
	
	public TestDscApi(EventHandler eh) 
	{
		this.eh = eh;
	}

	@Path("/leds")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response leds()
	{
		return Response.ok(
			json(
				json("READY", ready),
				json("ARMED", armed),
				json("TROUBLE", "OFF"),
				json("PROGRAM", "OFF"))).build();
		
	}

	@Path("/alarms")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response alarms()
	{
		return Response.ok("[" +  jq("Duress") + "," + jq("Smoke") + "]").build();
	}
	
	@Path("/troubles")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response troubles()
	{
		return Response.ok("[" +  jq("Panel Battery") + "," + jq("FTC") + "]").build();
	}
	
	@Path("/stayarm")
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	public Response stayarm()
	{
		log("stay armed");
		sendNotification(Notification.Type.ARM, 1000, Optional.of(() -> {armed = LedState.FLASH;ready = LedState.ON;}), "exit delay");
		sendNotification(Notification.Type.ARM, 9000, Optional.of(() -> {armed = LedState.ON;ready = LedState.OFF;}), "stay armed");
		return Response.ok().build();
	}

	@Path("/awayarm")
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	public Response awayarm()
	{
		log("away armed");
		sendNotification(Notification.Type.ARM, 1000, Optional.of(() -> {armed = LedState.FLASH;ready = LedState.ON;}), "exit delay");
		sendNotification(Notification.Type.ARM, 9000, Optional.of(() -> {armed = LedState.ON;ready = LedState.OFF;}), "away armed");
		return Response.ok().build();
	}
	
	@Path("/disarm")
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	public Response disarm(String usercode)
	{
		log("disarmed with " + usercode);
		sendNotification(Notification.Type.ARM, 2000, Optional.of(() -> {armed = LedState.FLASH;ready = LedState.OFF;}), "entry delay");
		sendNotification(Notification.Type.ARM, 10000, Optional.of(() -> {armed = LedState.OFF;ready = LedState.ON;}), "disarmed");
		return Response.ok().build();
	}
	
	@Path("/refresh")
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	public Response refresh()
	{
		sendNotification(Notification.Type.ALARM, "refresh");
		sendNotification(Notification.Type.TROUBLE, "refresh");
		sendNotification(Notification.Type.ARM, "refresh");
		return Response.ok().build();
	}
	
	private static String jq(String str)
	{
		return "\"" + str + "\"";
	}
	
	private static String json(String name, Object val)
    {
        return "\"" + name + "\" : \"" + String.valueOf(val) + "\"";
    }

    private static String json(String... pairs)
    {
        return Arrays.stream(pairs).collect(Collectors.joining(",\n", "{\n", "}\n"));
    }

    private void log(String msg)
    {
    	System.out.println(msg);
    }
    
    private void log(String msg, Throwable t)
    {
    	log(msg);
    	t.printStackTrace();
    }
    
    private void sendNotification(Notification.Type type, String... msg)
    {
    	sendNotification(type, 5000, Optional.empty(), msg);
    }
    
    private void sendNotification(Notification.Type type, int delayMillis, Optional<Runnable> after, String... msg)
    {
    	asyncNotifier.submit(() ->
    	{
    		try {
				Thread.sleep(new Random().nextInt(delayMillis));
			} catch (Exception e) {
			}
    		eh.notify(makeNotification(type, msg));
    		after.ifPresent(r -> r.run());
    	});
    }
    
    private String makeNotification(Notification.Type type, String... msg)
    {
    	return json(
	    	json("type", type.name()),
	    	json("ts", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())),
	    	json("msg", Stream.of(msg).collect(Collectors.joining(" "))));
    }
}

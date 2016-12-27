package org.sb.homesec.ws;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.websocket.server.ServerEndpointConfig;

public class EndpointConfigurator extends ServerEndpointConfig.Configurator 
		implements EventHandler
{
	private final List<EventHandler> hands = Collections.synchronizedList(new ArrayList<>());
	
	@Override
	public <T> T getEndpointInstance(Class<T> endpointClass) throws InstantiationException 
	{
		T inst = super.getEndpointInstance(endpointClass);
		if(inst instanceof EventHandler) hands.add((EventHandler)inst);
		return inst;
	}

	@Override
	public boolean isAlive() 
	{
		return true;
	}

	@Override
	public void notify(String event) 
	{
		for(Iterator<EventHandler> it = hands.iterator(); it.hasNext();)
		{
			EventHandler eh = it.next();
			if(eh.isAlive()) eh.notify(event);
			else it.remove();
		}
	}
}

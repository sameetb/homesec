package org.sb.homesec.ws;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServerEndpoint(value="/events")
public class EventSocket implements EventHandler
{
    private static final Logger log = LoggerFactory.getLogger(EventSocket.class);
    
    private Session session;
    private RemoteEndpoint.Async remote;

    @OnClose
    public void onWebSocketClose(CloseReason close)
    {
        this.session = null;
        this.remote = null;
        log.info("WebSocket Close: {} - {}",close.getCloseCode(),close.getReasonPhrase());
    }

    @OnOpen
    public void onWebSocketOpen(Session session)
    {
        this.session = session;
        this.remote = this.session.getAsyncRemote();
        log.info("WebSocket Connect: {}",session);
    }

    @OnError
    public void onWebSocketError(Throwable cause)
    {
        log.error("WebSocket Error",cause);
    }

    @OnMessage
    public String onWebSocketText(String message)
    {
        log("Got message {}",message);
        return "message acknowledged";
    }
    
    private void log(String msg, Object...msgs )
    {
        log.trace(msg, msgs);
    }

	@Override
	public void notify(String event) {
		if(isAlive())
		{
			log("sending event {}", event);
			remote.sendText(event);
		}
	}

	@Override
	public boolean isAlive() 
	{
		return session != null && session.isOpen() && remote != null;
	}
}

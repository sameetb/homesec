package org.sb.homesec.rs;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.Application;

import org.sb.homesec.ws.EventHandler;
import org.sb.libevl.DscPanel;
import org.sb.libevl.Notification;
import org.sb.libevl.EvlConnection;
import java.util.function.Supplier;

public class HomesecApp extends Application 
{

    private final HashSet<Object> singletons = new HashSet<Object>();
    
	public HomesecApp(DscPanel panel, Supplier<EvlConnection> conn, EventHandler eh) {
        singletons.add(new DscApi(panel, conn, eh));
    }

    @Override
    public Set<Class<?>> getClasses()
    {
        return Collections.emptySet();
    }

    @Override
    public Set<Object> getSingletons()
    {
        return singletons;
    }	
}

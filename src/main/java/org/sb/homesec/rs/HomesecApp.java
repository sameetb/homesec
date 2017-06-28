package org.sb.homesec.rs;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import javax.ws.rs.core.Application;

import org.sb.homesec.Foscam;
import org.sb.homesec.ws.EventHandler;
import org.sb.libevl.DscPanel;
import org.sb.libevl.EvlConnection;

public class HomesecApp extends Application 
{

    private final HashSet<Object> singletons = new HashSet<Object>();
    
	public HomesecApp(DscPanel panel, Supplier<EvlConnection> conn, EventHandler eh, Set<Foscam> cams) 
	{
        singletons.add(new DscApi(panel, conn));
        singletons.add(new FoscamApi(cams));
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

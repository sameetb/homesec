package org.sb.homesec;

import java.util.Arrays;
import java.util.Date;
import java.util.Random;
import java.util.stream.Collectors;

import org.sb.libevl.Notification;

/**
 * Unit test for simple App.
 */
public class AppTest extends App
{
    public AppTest( String[] args ) throws Exception
    {
        super(args);
        dummyEvents();
    }

    /**
     * @return the suite of tests being tested
     */
    public static void main(String[] args) throws Exception
    {
        new AppTest(args).start();
    }

    private void dummyEvents()
    {
		new Thread(() -> 
		{
			System.out.println("Starting dummy event thread");
			while(true)
			{
				sendNotification(new Notification(Notification.Type.MISC, new Date(), makeWord()));
				try {
					Thread.sleep(new Random().nextInt(60000));
				} catch (Exception e) {
					return;
				}
			}
		}).start();
    }
    
    private static String makeWord()
    {
    	return new Random().ints(new Random().nextInt(20))
    		.mapToObj(i -> (char)i)
    		.map(ch -> String.valueOf(ch))
    		.collect(Collectors.joining());
    }
    
    private static String json(String name, Object val)
    {
        return "\"" + name + "\" : \"" + String.valueOf(val) + "\"";
    }

    private static String json(String... pairs)
    {
        return Arrays.stream(pairs).collect(Collectors.joining(",\n", "{\n", "}\n"));
    }
}

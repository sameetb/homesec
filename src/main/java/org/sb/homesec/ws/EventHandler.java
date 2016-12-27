/**
 * 
 */
package org.sb.homesec.ws;

/**
 * @author sam
 *
 */
public interface EventHandler 
{
	boolean isAlive();
	void notify(String event);
}

/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.dataProviders.interfaces;

/**
 * @author sreichholf
 * 
 */
public interface DataParser {
	/**
	 * @param input
	 * @return
	 */
	boolean parse(String input);
}

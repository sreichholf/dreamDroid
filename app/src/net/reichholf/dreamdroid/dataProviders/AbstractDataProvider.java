/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.dataProviders;

import net.reichholf.dreamdroid.dataProviders.interfaces.DataParser;

/**
 * @author sreichholf
 * 
 */
public abstract class AbstractDataProvider {
	protected DataParser mParser;

	/**
	 * @param dp
	 */
	public AbstractDataProvider(DataParser dp) {
		mParser = dp;
	}

	/**
	 * @return
	 */
	public DataParser getParser() {
		return mParser;
	}

	/**
	 * @param input
	 * @return
	 */
	public boolean parse(String input) {
		return mParser.parse(input);
	}
}

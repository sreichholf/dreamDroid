/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2.requesthandler;

import net.reichholf.dreamdroid.helpers.enigma2.URIStore;
import net.reichholf.dreamdroid.parsers.enigma2.saxhandler.E2EventListHandler;

/**
 * @author sre
 * 
 */
public class EventListRequestHandler extends AbstractListRequestHandler {
	public EventListRequestHandler() {
		super(URIStore.EPG_SERVICE, new E2EventListHandler());
	}

	public EventListRequestHandler(String uri) {
		super(uri, new E2EventListHandler());
	}
}

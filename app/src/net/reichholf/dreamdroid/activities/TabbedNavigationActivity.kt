/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import net.reichholf.dreamdroid.DreamDroid

class TabbedNavigationActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val targetClass = if (DreamDroid.isTV(this)) {
            net.reichholf.dreamdroid.tv.activities.MainActivity::class.java
        } else {
            MainActivity::class.java
        }
        val incoming = intent ?: Intent()
        startActivity(forwardedLauncherIntent(this, incoming, targetClass))
        finish()
    }
}

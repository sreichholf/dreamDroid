package net.reichholf.dreamdroid.activities

import android.content.Context
import android.content.Intent

/**
 * Copy the launcher trampoline's VIEW payload onto the real phone or TV activity.
 * [TabbedNavigationActivity] is only a router; dropping action/data/extras drops
 * `dreamdroid://` links.
 */
internal fun forwardedLauncherIntent(
    context: Context,
    source: Intent,
    targetClass: Class<*>
): Intent = applyLauncherPayload(source, Intent(context, targetClass))

internal fun applyLauncherPayload(source: Intent, target: Intent): Intent {
    if (source.action != null) {
        target.action = source.action
    }
    if (source.data != null || source.type != null) {
        target.setDataAndType(source.data, source.type)
    }
    source.extras?.let { extras ->
        target.putExtras(extras)
    }
    source.categories?.forEach { category ->
        target.addCategory(category)
    }
    return target
}

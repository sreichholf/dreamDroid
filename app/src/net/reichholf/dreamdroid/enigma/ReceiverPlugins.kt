package net.reichholf.dreamdroid.enigma

import net.reichholf.dreamdroid.enigma.autotimer.AutoTimerPlugin

/**
 * The plugins dreamDroid uses that a receiver may lack, as one [ReceiverApi.plugins] answer
 * reports them.
 */
data class ReceiverPlugins(
    /** Whether the AutoTimer plugin is there, and which API it speaks. */
    val autoTimer: AutoTimerPlugin,
    /** Whether the VPS plugin is there, so timers can follow a broadcast's real start and end. */
    val vps: Boolean
)

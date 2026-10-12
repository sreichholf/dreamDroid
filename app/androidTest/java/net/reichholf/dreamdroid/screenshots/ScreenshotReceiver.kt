package net.reichholf.dreamdroid.screenshots

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer

/**
 * A Dreambox WebInterface for the store screenshots: a few bouquets of public channels, a
 * programme around [nowSec] for each of them, timers and recordings. Every answer is built
 * from that one schedule, so the service list, the EPG, the MultiEPG and the current
 * service agree with each other and with the device clock.
 */
class ScreenshotReceiver(private val nowSec: Long) : Dispatcher() {
    data class Channel(val ref: String, val name: String, val shows: List<Show>)

    data class Show(val title: String, val short: String, val minutes: Int)

    data class Bouquet(val ref: String, val name: String, val channels: List<Channel>)

    data class Programme(val id: Int, val channel: Channel, val show: Show, val startSec: Long) {
        val endSec: Long get() = startSec + show.minutes * 60L
    }

    private val schedule: Map<Channel, List<Programme>> = buildSchedule()

    /** Served for any picon the app loads through `/file`, as for a profile's online picons. */
    @Volatile
    var piconPng: ByteArray? = null

    /** Every request path with its status, for the test's failure report. */
    val log: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())

    override fun dispatch(request: RecordedRequest): MockResponse =
        answer(request).also { log += "${it.status} ${request.path}" }

    private fun answer(request: RecordedRequest): MockResponse {
        val url = request.requestUrl ?: return notFound()
        if (url.encodedPath == "/file") {
            val png = piconPng ?: return notFound()
            return MockResponse().setHeader(
                "Content-Type",
                "image/png"
            ).setBody(Buffer().write(png))
        }
        val body = when (url.encodedPath) {
            "/web/session" -> "<e2sessionid>screenshots</e2sessionid>"

            "/web/deviceinfo" -> DEVICE_INFO

            "/web/external" -> "<e2webifexternals></e2webifexternals>"

            "/web/getservices" -> services(url.queryParameter("sRef").orEmpty())

            "/web/epgnownext" -> nowNext(bouquetChannels(url.queryParameter("bRef")))

            "/web/epgnow" -> events(bouquetChannels(url.queryParameter("bRef")).mapNotNull(::now))

            "/web/epgbouquet" -> {
                val at = url.queryParameter("time")?.toLongOrNull() ?: nowSec
                events(bouquetChannels(url.queryParameter("bRef")).mapNotNull { at(it, at) })
            }

            "/web/epgmulti" -> multi(url.queryParameter("bRef"), url)

            "/web/epgservice" -> serviceEpg(url.queryParameter("sRef"), url)

            "/web/epgsearch" -> search(url.queryParameter("search").orEmpty())

            "/web/getcurrent" -> current()

            "/web/timerlist" -> timers()

            "/web/movielist" -> movies()

            "/web/getlocations" ->
                "<e2locations><e2location>/media/hdd/movie/</e2location></e2locations>"

            "/web/gettags" -> "<e2tags><e2tag>Documentary</e2tag><e2tag>Series</e2tag></e2tags>"

            "/web/signal" -> SIGNAL

            "/web/powerstate" -> "<e2powerstate><e2instandby>false</e2instandby></e2powerstate>"

            "/web/vol" -> VOLUME

            else -> return notFound()
        }
        return MockResponse()
            .setHeader("Content-Type", "text/xml; charset=UTF-8")
            .setBody("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n$body")
    }

    private fun notFound() = MockResponse().setResponseCode(404)

    /** What [channel] shows now, as the service list and the MultiEPG print it. */
    fun titleNow(channel: Channel): String = checkNotNull(now(channel)).show.title

    /** The show after next on [channel]: near the top of its EPG. */
    fun titleLater(channel: Channel): String =
        schedule.getValue(channel).filter { it.startSec > nowSec }[1].show.title

    fun timerTitles(): List<String> = timerProgrammes().map { it.first.show.title }

    private fun services(ref: String): String {
        val rows = when {
            ref.contains("bouquets.tv") -> TV_BOUQUETS.map { it.ref to it.name }
            ref.contains("bouquets.radio") -> listOf(RADIO_BOUQUET.ref to RADIO_BOUQUET.name)
            else -> bouquetChannels(ref).map { it.ref to it.name }
        }
        return "<e2servicelist>" + rows.joinToString("") { (r, n) ->
            "<e2service><e2servicereference>${esc(r)}</e2servicereference>" +
                "<e2servicename>${esc(n)}</e2servicename></e2service>"
        } + "</e2servicelist>"
    }

    /** A bouquet's channels; any other container (providers, all services) lists every TV one. */
    private fun bouquetChannels(ref: String?): List<Channel> {
        if (ref == RADIO_BOUQUET.ref) return RADIO_BOUQUET.channels
        return TV_BOUQUETS.firstOrNull { it.ref == ref }?.channels ?: TV_CHANNELS
    }

    private fun now(channel: Channel): Programme? = at(channel, nowSec)

    private fun at(channel: Channel, sec: Long): Programme? =
        schedule.getValue(channel).firstOrNull { sec >= it.startSec && sec < it.endSec }

    private fun next(channel: Channel): Programme? =
        schedule.getValue(channel).firstOrNull { it.startSec >= nowSec }

    private fun nowNext(channels: List<Channel>): String =
        events(channels.flatMap { listOfNotNull(now(it), next(it)) })

    private fun multi(bouquetRef: String?, url: okhttp3.HttpUrl): String {
        val (from, to) = window(url)
        return events(
            bouquetChannels(bouquetRef).flatMap { channel ->
                schedule.getValue(channel).filter { it.endSec > from && it.startSec < to }
            }
        )
    }

    private fun serviceEpg(serviceRef: String?, url: okhttp3.HttpUrl): String {
        val channel = (TV_CHANNELS + RADIO_CHANNELS).firstOrNull { it.ref == serviceRef }
            ?: return events(emptyList())
        val (from, to) = window(url, defaultMinutes = 24 * 60)
        return events(schedule.getValue(channel).filter { it.endSec > from && it.startSec < to })
    }

    /** `time` is the start, `endTime` the window length in minutes. */
    private fun window(url: okhttp3.HttpUrl, defaultMinutes: Long = 6 * 60): Pair<Long, Long> {
        val from = url.queryParameter("time")?.toLongOrNull() ?: nowSec
        val minutes = url.queryParameter("endTime")?.toLongOrNull() ?: defaultMinutes
        return from to from + minutes * 60L
    }

    private fun search(query: String): String = events(
        schedule.values.flatten()
            .filter { it.startSec >= nowSec && it.show.title.contains(query, ignoreCase = true) }
    )

    private fun events(programmes: List<Programme>): String =
        "<e2eventlist>" + programmes.joinToString("") { event(it) } + "</e2eventlist>"

    private fun event(p: Programme): String = "<e2event>" +
        "<e2eventid>${p.id}</e2eventid>" +
        "<e2eventstart>${p.startSec}</e2eventstart>" +
        "<e2eventduration>${p.show.minutes * 60}</e2eventduration>" +
        "<e2eventcurrenttime>$nowSec</e2eventcurrenttime>" +
        "<e2eventtitle>${esc(p.show.title)}</e2eventtitle>" +
        "<e2eventdescription>${esc(p.show.short)}</e2eventdescription>" +
        "<e2eventdescriptionextended>${esc(extended(p))}</e2eventdescriptionextended>" +
        "<e2eventservicereference>${esc(p.channel.ref)}</e2eventservicereference>" +
        "<e2eventservicename>${esc(p.channel.name)}</e2eventservicename>" +
        "</e2event>"

    private fun extended(p: Programme): String =
        "${p.show.short}. ${p.show.title} on ${p.channel.name}, ${p.show.minutes} minutes. " +
            EXTENDED_MARK

    private fun current(): String {
        val channel = TV_CHANNELS.first()
        val events = listOfNotNull(now(channel), next(channel)).joinToString("") { p ->
            "<e2event>" +
                "<e2eventservicereference>${esc(channel.ref)}</e2eventservicereference>" +
                "<e2eventservicename>${esc(channel.name)}</e2eventservicename>" +
                "<e2eventprovidername>ARD</e2eventprovidername>" +
                "<e2eventid>${p.id}</e2eventid>" +
                "<e2eventname>${esc(p.show.title)}</e2eventname>" +
                "<e2eventtitle>${esc(p.show.title)}</e2eventtitle>" +
                "<e2eventdescription>${esc(p.show.short)}</e2eventdescription>" +
                "<e2eventstart>${p.startSec}</e2eventstart>" +
                "<e2eventduration>${p.show.minutes * 60}</e2eventduration>" +
                "<e2eventremaining>${(p.endSec - nowSec).coerceAtLeast(0)}</e2eventremaining>" +
                "<e2eventcurrenttime>$nowSec</e2eventcurrenttime>" +
                "<e2eventdescriptionextended>${esc(extended(p))}</e2eventdescriptionextended>" +
                "</e2event>"
        }
        return "<e2currentserviceinformation><e2service>" +
            "<e2servicereference>${esc(channel.ref)}</e2servicereference>" +
            "<e2servicename>${esc(channel.name)}</e2servicename>" +
            "<e2providername>ARD</e2providername>" +
            "<e2videowidth>1920</e2videowidth><e2videoheight>1080</e2videoheight>" +
            "<e2servicevideosize>1920x1080</e2servicevideosize>" +
            "<e2iswidescreen>True</e2iswidescreen>" +
            "<e2apid>5122</e2apid><e2vpid>5121</e2vpid><e2pcrpid>5121</e2pcrpid>" +
            "<e2pmtpid>5100</e2pmtpid><e2txtpid>5124</e2txtpid>" +
            "<e2tsid>1019</e2tsid><e2onid>1</e2onid><e2sid>10301</e2sid>" +
            "</e2service><e2eventlist>$events</e2eventlist></e2currentserviceinformation>"
    }

    /** Recordings of what airs later today, and one running now. */
    private fun timerProgrammes(): List<Pair<Programme, Int>> = listOfNotNull(
        now(TV_CHANNELS[3])?.let { it to STATE_RUNNING },
        laterOn(TV_CHANNELS[1], 1)?.let { it to STATE_WAITING },
        laterOn(TV_CHANNELS[2], 2)?.let { it to STATE_WAITING },
        laterOn(TV_CHANNELS[5], 3)?.let { it to STATE_WAITING },
        laterOn(TV_CHANNELS[0], 5)?.let { it to STATE_WAITING }
    )

    private fun timers(): String =
        "<e2timerlist>" + timerProgrammes().joinToString("") { (p, state) ->
            "<e2timer>" +
                "<e2servicereference>${esc(p.channel.ref)}</e2servicereference>" +
                "<e2servicename>${esc(p.channel.name)}</e2servicename>" +
                "<e2eit>${p.id}</e2eit>" +
                "<e2name>${esc(p.show.title)}</e2name>" +
                "<e2description>${esc(p.show.short)}</e2description>" +
                "<e2descriptionextended>${esc(extended(p))}</e2descriptionextended>" +
                "<e2disabled>0</e2disabled>" +
                "<e2timebegin>${p.startSec - 120}</e2timebegin>" +
                "<e2timeend>${p.endSec + 300}</e2timeend>" +
                "<e2duration>${p.endSec - p.startSec + 420}</e2duration>" +
                "<e2startprepare>${p.startSec - 140}</e2startprepare>" +
                "<e2justplay>0</e2justplay>" +
                "<e2afterevent>3</e2afterevent>" +
                "<e2location>/media/hdd/movie/</e2location>" +
                "<e2tags></e2tags><e2logentries></e2logentries><e2filename></e2filename>" +
                "<e2backoff>0</e2backoff><e2nextactivation></e2nextactivation>" +
                "<e2firsttryprepare>True</e2firsttryprepare>" +
                "<e2state>$state</e2state>" +
                "<e2repeated>0</e2repeated><e2dontsave>0</e2dontsave>" +
                "<e2cancled>False</e2cancled><e2toggledisabled>1</e2toggledisabled>" +
                "</e2timer>"
        } + "</e2timerlist>"

    private fun laterOn(channel: Channel, skip: Int): Programme? =
        schedule.getValue(channel).filter { it.startSec > nowSec }.getOrNull(skip)

    private fun movies(): String {
        val day = 24 * 3600L
        return "<e2movielist>" + MOVIES.mapIndexed { i, m ->
            val time = nowSec - (i + 1) * day - i * 5400L
            val file = "/media/hdd/movie/${m.title.replace(Regex("[^A-Za-z0-9]+"), "_")}.ts"
            "<e2movie>" +
                "<e2servicereference>1:0:0:0:0:0:0:0:0:0:${esc(file)}</e2servicereference>" +
                "<e2title>${esc(m.title)}</e2title>" +
                "<e2description>${esc(m.short)}</e2description>" +
                "<e2descriptionextended>${esc(m.short)}.</e2descriptionextended>" +
                "<e2servicename>${esc(TV_CHANNELS[i % TV_CHANNELS.size].name)}</e2servicename>" +
                "<e2time>$time</e2time>" +
                "<e2length>${m.minutes}:00</e2length>" +
                "<e2tags>${esc(m.tags)}</e2tags>" +
                "<e2filename>${esc(file)}</e2filename>" +
                "<e2filesize>${m.minutes * 38_000_000L}</e2filesize>" +
                "</e2movie>"
        } + "</e2movielist>"
    }

    /**
     * Each channel's shows repeat from a start a little before [nowSec], shifted per
     * channel so the running shows end at different times.
     */
    private fun buildSchedule(): Map<Channel, List<Programme>> {
        var id = 10_000
        val halfHour = 1800L
        val base = nowSec - nowSec % halfHour
        return (TV_CHANNELS + RADIO_CHANNELS).withIndex().associate { (index, channel) ->
            var start = base - SCHEDULE_BACK_SEC - (index % 4) * 900L
            val end = nowSec + SCHEDULE_AHEAD_SEC
            val programmes = mutableListOf<Programme>()
            var i = index
            while (start < end) {
                val show = channel.shows[i % channel.shows.size]
                programmes += Programme(id++, channel, show, start)
                start += show.minutes * 60L
                i++
            }
            channel to programmes
        }
    }

    private data class Recording(
        val title: String,
        val short: String,
        val minutes: Int,
        val tags: String
    )

    companion object {
        /** Ends every extended description, so a test can wait for the event detail. */
        const val EXTENDED_MARK = "A made-up programme for the dreamDroid screenshots."
        const val STATE_WAITING = 0
        const val STATE_RUNNING = 2
        private const val SCHEDULE_BACK_SEC = 6 * 3600L
        private const val SCHEDULE_AHEAD_SEC = 30 * 3600L

        private fun ref(sid: Int, tsid: Int = 0x3FB, type: Int = 0x19) =
            "1:0:${type.toString(16).uppercase()}:${sid.toString(16).uppercase()}:" +
                "${tsid.toString(16).uppercase()}:1:C00000:0:0:0:"

        private fun bouquetRef(file: String, radio: Boolean = false) =
            "1:7:${if (radio) 2 else 1}:0:0:0:0:0:0:0:FROM BOUQUET \"$file\" ORDER BY bouquet"

        private val NEWS = listOf(
            Show("Evening News", "The day's headlines", 30),
            Show("Weather Outlook", "The forecast for the week", 15),
            Show("World Report", "Correspondents from five continents", 45),
            Show("Business Today", "Markets and companies", 30)
        )
        private val DOCS = listOf(
            Show("Oceans of Light", "Bioluminescent life in the deep sea", 50),
            Show("The Alpine Year", "Four seasons in the mountains", 45),
            Show("Wild Islands", "Galápagos: islands of change", 50),
            Show("Hidden Cities", "Underground networks of old Europe", 45)
        )
        private val SERIES = listOf(
            Show("Harbour Lights", "Episode 12: The Long Night", 45),
            Show("The Inspector", "A quiet village keeps its secret", 90),
            Show("Kitchen Stories", "Three courses, one hour", 30),
            Show("Mountain Clinic", "Episode 4: First Snow", 45)
        )
        private val SPORT = listOf(
            Show("Sports Weekend", "Highlights and results", 60),
            Show("Cycling Live", "Stage 14: into the mountains", 120),
            Show("Football Magazine", "All goals of the matchday", 45)
        )
        private val KIDS = listOf(
            Show("The Little Explorers", "Animated series", 25),
            Show("Science Lab", "Why is the sky blue?", 25),
            Show("Story Time", "Stories for bedtime", 20)
        )
        private val MUSIC = listOf(
            Show("Morning Show", "Music and news", 180),
            Show("Afternoon Mix", "Hits and new releases", 120),
            Show("Classic Evening", "Symphonies and chamber music", 120)
        )

        val TV_CHANNELS = listOf(
            Channel(ref(0x283D), "Das Erste HD", NEWS + SERIES),
            Channel(ref(0x2B66, 0x3F9), "ZDF HD", SERIES + NEWS),
            Channel(ref(0x2B7A, 0x3F9), "3sat HD", DOCS),
            Channel(ref(0x283E), "arte HD", DOCS + SERIES),
            Channel(ref(0x2B98, 0x3F9), "ZDFneo HD", SERIES),
            Channel(ref(0x2843), "ONE HD", SERIES + SPORT),
            Channel(ref(0x2B8E, 0x3F9), "KiKA HD", KIDS),
            Channel(ref(0x2887), "phoenix HD", NEWS + DOCS),
            Channel(ref(0x2888), "tagesschau24 HD", NEWS),
            Channel(ref(0x2B84, 0x3F9), "ZDFinfo HD", DOCS + NEWS),
            Channel(ref(0x2855), "BR Fernsehen Süd HD", SERIES + DOCS),
            Channel(ref(0x285B), "WDR HD Köln", NEWS + SERIES)
        )

        val RADIO_CHANNELS = listOf(
            Channel(ref(0x2F1C, 0x421, 2), "Bayern 3", MUSIC),
            Channel(ref(0x6E, 0x421, 2), "DLF", NEWS + DOCS),
            Channel(ref(0x2F1D, 0x421, 2), "BR-KLASSIK", MUSIC),
            Channel(ref(0x6F, 0x421, 2), "Deutschlandfunk Kultur", MUSIC + DOCS)
        )

        val FAVOURITES = Bouquet(
            bouquetRef("userbouquet.favourites.tv"),
            "Favourites (TV)",
            TV_CHANNELS
        )
        val DOCUMENTARY = Bouquet(
            bouquetRef("userbouquet.documentary.tv"),
            "Documentary",
            listOf(TV_CHANNELS[2], TV_CHANNELS[3], TV_CHANNELS[7], TV_CHANNELS[9])
        )
        val NEWS_BOUQUET = Bouquet(
            bouquetRef("userbouquet.news.tv"),
            "News",
            listOf(TV_CHANNELS[0], TV_CHANNELS[1], TV_CHANNELS[7], TV_CHANNELS[8])
        )
        val TV_BOUQUETS = listOf(FAVOURITES, DOCUMENTARY, NEWS_BOUQUET)
        val RADIO_BOUQUET = Bouquet(
            bouquetRef("userbouquet.favourites.radio", radio = true),
            "Favourites (Radio)",
            RADIO_CHANNELS
        )

        private val MOVIES = listOf(
            Recording(
                "Northern Lights",
                "A winter night above the Arctic Circle",
                60,
                "Documentary"
            ),
            Recording("Oceans of Light", "Bioluminescent life in the deep sea", 50, "Documentary"),
            Recording("The Inspector", "A quiet village keeps its secret", 90, "Series"),
            Recording("Cycling Live", "Stage 14: into the mountains", 120, ""),
            Recording("Wild Islands", "Galápagos: islands of change", 50, "Documentary"),
            Recording("Harbour Lights", "Episode 11: Low Tide", 45, "Series"),
            Recording("Hidden Cities", "Underground networks of old Europe", 45, "Documentary"),
            Recording("Mountain Clinic", "Episode 3: The Avalanche", 45, "Series")
        )

        val MOVIE_TITLES: List<String> = MOVIES.map { it.title }

        private const val DEVICE_INFO =
            "<e2deviceinfo>" +
                "<e2enigmaversion>2026-09-30-dreambox</e2enigmaversion>" +
                "<e2imageversion>4.5</e2imageversion>" +
                "<e2webifversion>1.8.6</e2webifversion>" +
                "<e2fpversion>0</e2fpversion>" +
                "<e2devicename>dm920</e2devicename>" +
                "<e2frontends>" +
                "<e2frontend><e2name>Tuner A</e2name>" +
                "<e2model>DVB-S2X NIM (FBC)</e2model></e2frontend>" +
                "<e2frontend><e2name>Tuner B</e2name>" +
                "<e2model>DVB-S2X NIM (FBC)</e2model></e2frontend>" +
                "</e2frontends>" +
                "<e2network><e2interface><e2name>eth0</e2name>" +
                "<e2mac>00:09:34:00:00:01</e2mac><e2dhcp>True</e2dhcp>" +
                "<e2ip>192.168.0.20</e2ip><e2gateway>192.168.0.1</e2gateway>" +
                "<e2netmask>255.255.255.0</e2netmask></e2interface></e2network>" +
                "<e2hdds><e2hdd><e2model>ATA(WDC WD20SPZX)</e2model>" +
                "<e2capacity>1.82 TB</e2capacity><e2free>1203.4 GB</e2free></e2hdd></e2hdds>" +
                "</e2deviceinfo>"

        private const val SIGNAL =
            "<e2frontendstatus><e2snrdb>13.20 dB</e2snrdb><e2snr>78 %</e2snr>" +
                "<e2ber>0</e2ber><e2acg>84 %</e2acg></e2frontendstatus>"

        private const val VOLUME =
            "<e2volume><e2result>True</e2result><e2resulttext>State</e2resulttext>" +
                "<e2current>42</e2current><e2ismuted>False</e2ismuted></e2volume>"

        private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;")
    }
}

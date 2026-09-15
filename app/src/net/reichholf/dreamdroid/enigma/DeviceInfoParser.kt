package net.reichholf.dreamdroid.enigma

import org.xmlpull.v1.XmlPullParser

object DeviceInfoParser {
    fun parse(xml: String): DeviceInfo? =
        parseEnigmaXml(xml, emptyResult = null, onFail = null) { parser ->
            parseDeviceInfo(parser)
        }
}

private fun parseDeviceInfo(parser: XmlPullParser): DeviceInfo? {
    val guiVersion = StringBuilder()
    val imageVersion = StringBuilder()
    val interfaceVersion = StringBuilder()
    val fpVersion = StringBuilder()
    val deviceName = StringBuilder()
    val frontendName = StringBuilder()
    val frontendModel = StringBuilder()
    val nicName = StringBuilder()
    val nicMac = StringBuilder()
    val nicDhcp = StringBuilder()
    val nicIp = StringBuilder()
    val nicGateway = StringBuilder()
    val nicNetmask = StringBuilder()
    val hddModel = StringBuilder()
    val hddCapacity = StringBuilder()
    val hddFree = StringBuilder()
    val frontends = ArrayList<DeviceFrontend>()
    val nics = ArrayList<DeviceNic>()
    val hdds = ArrayList<DeviceHdd>()
    var current: StringBuilder? = null
    var inFrontend = false
    var inNic = false
    var inHdd = false
    var result: DeviceInfo? = null

    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> {
                when (parser.localTag()) {
                    "e2enigmaversion" -> current = guiVersion

                    "e2imageversion" -> current = imageVersion

                    "e2webifversion" -> current = interfaceVersion

                    "e2fpversion" -> current = fpVersion

                    "e2devicename" -> current = deviceName

                    "e2frontend" -> {
                        inFrontend = true
                        frontendName.setLength(0)
                        frontendModel.setLength(0)
                        current = null
                    }

                    "e2interface" -> {
                        inNic = true
                        nicName.setLength(0)
                        nicMac.setLength(0)
                        nicDhcp.setLength(0)
                        nicIp.setLength(0)
                        nicGateway.setLength(0)
                        nicNetmask.setLength(0)
                        current = null
                    }

                    "e2hdd" -> {
                        inHdd = true
                        hddModel.setLength(0)
                        hddCapacity.setLength(0)
                        hddFree.setLength(0)
                        current = null
                    }

                    "e2name" -> current = when {
                        inFrontend -> frontendName
                        inNic -> nicName
                        else -> null
                    }

                    "e2model" -> current = when {
                        inFrontend -> frontendModel
                        inHdd -> hddModel
                        else -> null
                    }

                    "e2mac" -> current = if (inNic) nicMac else null

                    "e2dhcp" -> current = if (inNic) nicDhcp else null

                    "e2ip" -> current = if (inNic) nicIp else null

                    "e2gateway" -> current = if (inNic) nicGateway else null

                    "e2netmask" -> current = if (inNic) nicNetmask else null

                    "e2capacity" -> current = if (inHdd) hddCapacity else null

                    "e2free" -> current = if (inHdd) hddFree else null
                }
            }

            XmlPullParser.TEXT -> current?.let { parser.appendText(it) }

            XmlPullParser.END_TAG -> {
                when (parser.localTag()) {
                    "e2frontend" -> {
                        inFrontend = false
                        current = null
                        frontends.add(
                            DeviceFrontend(
                                name = frontendName.toString().trim(),
                                model = frontendModel.toString().trim()
                            )
                        )
                    }

                    "e2interface" -> {
                        inNic = false
                        current = null
                        nics.add(
                            DeviceNic(
                                name = nicName.toString().trim(),
                                mac = nicMac.toString().trim(),
                                dhcp = nicDhcp.toString().trim(),
                                ip = nicIp.toString().trim(),
                                gateway = nicGateway.toString().trim(),
                                netmask = nicNetmask.toString().trim()
                            )
                        )
                    }

                    "e2hdd" -> {
                        inHdd = false
                        current = null
                        hdds.add(
                            DeviceHdd(
                                model = hddModel.toString().trim(),
                                capacity = hddCapacity.toString().trim(),
                                free = hddFree.toString().trim()
                            )
                        )
                    }

                    "e2deviceinfo" -> {
                        current = null
                        result = finalizeDeviceInfo(
                            guiVersion,
                            imageVersion,
                            interfaceVersion,
                            fpVersion,
                            deviceName,
                            frontends,
                            nics,
                            hdds
                        )
                    }

                    else -> current = null
                }
            }
        }
        event = parser.next()
    }
    if (result == null) {
        result = finalizeDeviceInfo(
            guiVersion,
            imageVersion,
            interfaceVersion,
            fpVersion,
            deviceName,
            frontends,
            nics,
            hdds
        )
    }
    return result
}

private fun finalizeDeviceInfo(
    guiVersion: StringBuilder,
    imageVersion: StringBuilder,
    interfaceVersion: StringBuilder,
    fpVersion: StringBuilder,
    deviceName: StringBuilder,
    frontends: List<DeviceFrontend>,
    nics: List<DeviceNic>,
    hdds: List<DeviceHdd>
): DeviceInfo? {
    val built = DeviceInfo(
        guiVersion = guiVersion.toString().trim(),
        imageVersion = imageVersion.toString().trim(),
        interfaceVersion = interfaceVersion.toString().trim(),
        frontProcessorVersion = fpVersion.toString().trim(),
        deviceName = deviceName.toString().trim(),
        frontends = frontends.toList(),
        nics = nics.toList(),
        hdds = hdds.toList()
    )
    return if (built.isEmpty()) null else built
}

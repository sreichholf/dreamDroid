package net.reichholf.dreamdroid.enigma

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory

object DeviceInfoParser {
    fun parse(xml: String): DeviceInfo? {
        if (xml.isEmpty()) {
            return null
        }
        return parseSanitized(xml, aggressive = false)
            ?: parseSanitized(xml, aggressive = true)
    }

    private fun parseSanitized(xml: String, aggressive: Boolean): DeviceInfo? {
        return try {
            val handler = DeviceInfoHandler()
            val factory = SAXParserFactory.newInstance()
            factory.isValidating = false
            val reader = factory.newSAXParser().xmlReader
            reader.contentHandler = handler
            reader.parse(InputSource(StringReader(XmlInput.sanitize(xml, aggressive))))
            handler.result
        } catch (e: Exception) {
            null
        }
    }
}

private class DeviceInfoHandler : DefaultHandler() {
    var result: DeviceInfo? = null

    private var inGuiVersion = false
    private var inImageVersion = false
    private var inInterfaceVersion = false
    private var inFpVersion = false
    private var inDeviceName = false
    private var inFrontend = false
    private var inNic = false
    private var inHdd = false
    private var inName = false
    private var inModel = false
    private var inMac = false
    private var inDhcp = false
    private var inIp = false
    private var inGateway = false
    private var inNetmask = false
    private var inCapacity = false
    private var inFree = false

    private val guiVersion = StringBuilder()
    private val imageVersion = StringBuilder()
    private val interfaceVersion = StringBuilder()
    private val fpVersion = StringBuilder()
    private val deviceName = StringBuilder()

    private val frontendName = StringBuilder()
    private val frontendModel = StringBuilder()
    private val nicName = StringBuilder()
    private val nicMac = StringBuilder()
    private val nicDhcp = StringBuilder()
    private val nicIp = StringBuilder()
    private val nicGateway = StringBuilder()
    private val nicNetmask = StringBuilder()
    private val hddModel = StringBuilder()
    private val hddCapacity = StringBuilder()
    private val hddFree = StringBuilder()

    private val frontends = ArrayList<DeviceFrontend>()
    private val nics = ArrayList<DeviceNic>()
    private val hdds = ArrayList<DeviceHdd>()

    override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
        when (tag(localName, qName)) {
            "e2enigmaversion" -> inGuiVersion = true
            "e2imageversion" -> inImageVersion = true
            "e2webifversion" -> inInterfaceVersion = true
            "e2fpversion" -> inFpVersion = true
            "e2devicename" -> inDeviceName = true
            "e2frontend" -> {
                inFrontend = true
                frontendName.setLength(0)
                frontendModel.setLength(0)
            }
            "e2interface" -> {
                inNic = true
                nicName.setLength(0)
                nicMac.setLength(0)
                nicDhcp.setLength(0)
                nicIp.setLength(0)
                nicGateway.setLength(0)
                nicNetmask.setLength(0)
            }
            "e2hdd" -> {
                inHdd = true
                hddModel.setLength(0)
                hddCapacity.setLength(0)
                hddFree.setLength(0)
            }
            "e2name" -> inName = true
            "e2model" -> inModel = true
            "e2mac" -> inMac = true
            "e2dhcp" -> inDhcp = true
            "e2ip" -> inIp = true
            "e2gateway" -> inGateway = true
            "e2netmask" -> inNetmask = true
            "e2capacity" -> inCapacity = true
            "e2free" -> inFree = true
        }
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        when (tag(localName, qName)) {
            "e2enigmaversion" -> inGuiVersion = false
            "e2imageversion" -> inImageVersion = false
            "e2webifversion" -> inInterfaceVersion = false
            "e2fpversion" -> inFpVersion = false
            "e2devicename" -> inDeviceName = false
            "e2frontend" -> {
                inFrontend = false
                frontends.add(
                    DeviceFrontend(
                        name = frontendName.toString().trim(),
                        model = frontendModel.toString().trim(),
                    ),
                )
            }
            "e2interface" -> {
                inNic = false
                nics.add(
                    DeviceNic(
                        name = nicName.toString().trim(),
                        mac = nicMac.toString().trim(),
                        dhcp = nicDhcp.toString().trim(),
                        ip = nicIp.toString().trim(),
                        gateway = nicGateway.toString().trim(),
                        netmask = nicNetmask.toString().trim(),
                    ),
                )
            }
            "e2hdd" -> {
                inHdd = false
                hdds.add(
                    DeviceHdd(
                        model = hddModel.toString().trim(),
                        capacity = hddCapacity.toString().trim(),
                        free = hddFree.toString().trim(),
                    ),
                )
            }
            "e2name" -> inName = false
            "e2model" -> inModel = false
            "e2mac" -> inMac = false
            "e2dhcp" -> inDhcp = false
            "e2ip" -> inIp = false
            "e2gateway" -> inGateway = false
            "e2netmask" -> inNetmask = false
            "e2capacity" -> inCapacity = false
            "e2free" -> inFree = false
            "e2deviceinfo" -> finalizeResult()
        }
    }

    override fun endDocument() {
        if (result == null) {
            finalizeResult()
        }
    }

    private fun finalizeResult() {
        val built = DeviceInfo(
            guiVersion = guiVersion.toString().trim(),
            imageVersion = imageVersion.toString().trim(),
            interfaceVersion = interfaceVersion.toString().trim(),
            frontProcessorVersion = fpVersion.toString().trim(),
            deviceName = deviceName.toString().trim(),
            frontends = frontends.toList(),
            nics = nics.toList(),
            hdds = hdds.toList(),
        )
        result = if (built.isEmpty()) null else built
    }

    override fun characters(ch: CharArray, startIdx: Int, length: Int) {
        when {
            inGuiVersion -> guiVersion.append(ch, startIdx, length)
            inImageVersion -> imageVersion.append(ch, startIdx, length)
            inInterfaceVersion -> interfaceVersion.append(ch, startIdx, length)
            inFpVersion -> fpVersion.append(ch, startIdx, length)
            inDeviceName -> deviceName.append(ch, startIdx, length)
            inFrontend && inName -> frontendName.append(ch, startIdx, length)
            inFrontend && inModel -> frontendModel.append(ch, startIdx, length)
            inNic && inName -> nicName.append(ch, startIdx, length)
            inNic && inMac -> nicMac.append(ch, startIdx, length)
            inNic && inDhcp -> nicDhcp.append(ch, startIdx, length)
            inNic && inIp -> nicIp.append(ch, startIdx, length)
            inNic && inGateway -> nicGateway.append(ch, startIdx, length)
            inNic && inNetmask -> nicNetmask.append(ch, startIdx, length)
            inHdd && inModel -> hddModel.append(ch, startIdx, length)
            inHdd && inCapacity -> hddCapacity.append(ch, startIdx, length)
            inHdd && inFree -> hddFree.append(ch, startIdx, length)
        }
    }

    private fun tag(localName: String?, qName: String?): String {
        val raw = if (!localName.isNullOrEmpty()) localName else (qName ?: "")
        val colon = raw.lastIndexOf(':')
        return if (colon >= 0) raw.substring(colon + 1) else raw
    }
}

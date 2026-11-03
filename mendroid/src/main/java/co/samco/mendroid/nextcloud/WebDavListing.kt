package co.samco.mendroid.nextcloud

import android.util.Xml
import co.samco.mendroid.remote.RemoteDirectoryEntry
import okhttp3.HttpUrl
import org.xmlpull.v1.XmlPullParser

/** Only successful DAV resource types on immediate children become entries. */
internal class WebDavListing {
    fun parse(root: HttpUrl, path: RemotePath, xml: String): List<RemoteDirectoryEntry> {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput(xml.reader())
        }
        parser.nextTag()
        if (parser.name != "multistatus" || parser.namespace != DAV) throw NextcloudException(NextcloudFailure.INVALID_RESPONSE)
        val entries = mutableListOf<RemoteDirectoryEntry>()
        var complete = false
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.END_TAG && parser.depth == 1 && parser.name == "multistatus") complete = true
            if (parser.eventType == XmlPullParser.START_TAG && parser.namespace == DAV && parser.name == "response") {
                val entry = response(parser)
                if (entry.collection != null) child(root, path, entry.href)?.let {
                    entries.add(RemoteDirectoryEntry(it, entry.collection))
                }
            }
        }
        if (!complete) throw NextcloudException(NextcloudFailure.INVALID_RESPONSE)
        return entries.distinctBy { it.path }.sortedBy { it.path.name.lowercase() }
    }

    private data class Entry(val href: String, val collection: Boolean?)

    private fun response(parser: XmlPullParser): Entry {
        val depth = parser.depth
        var href = ""
        var collection: Boolean? = null
        var valid = true
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.END_TAG && parser.depth == depth) break
            if (parser.eventType != XmlPullParser.START_TAG || parser.namespace != DAV || parser.depth != depth + 1) continue
            when (parser.name) {
                "href" -> href = parser.nextText()
                "propstat" -> collection = propstat(parser) ?: collection
                "status" -> valid = successful(parser.nextText())
            }
        }
        return Entry(href, if (valid) collection else null)
    }

    private fun propstat(parser: XmlPullParser): Boolean? {
        val depth = parser.depth
        var collection = false
        var hasType = false
        var status = ""
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.END_TAG && parser.depth == depth) break
            if (parser.eventType != XmlPullParser.START_TAG || parser.namespace != DAV) continue
            when {
                parser.name == "resourcetype" && parser.depth == depth + 2 -> {
                    hasType = true
                    collection = resourceType(parser)
                }
                parser.name == "status" && parser.depth == depth + 1 -> status = parser.nextText()
            }
        }
        return if (hasType && successful(status)) collection else null
    }

    private fun resourceType(parser: XmlPullParser): Boolean {
        val depth = parser.depth
        var collection = false
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.END_TAG && parser.depth == depth) break
            if (parser.eventType == XmlPullParser.START_TAG && parser.depth == depth + 1 &&
                parser.namespace == DAV && parser.name == "collection") collection = true
        }
        return collection
    }

    private fun successful(status: String) = status.split(' ').getOrNull(1)?.toIntOrNull() in 200..299

    private fun child(root: HttpUrl, path: RemotePath, href: String): RemotePath? {
        val url = root.resolve(href) ?: return null
        if (url.scheme != root.scheme || url.host != root.host || url.port != root.port) return null
        val parent = root.pathSegments.filter(String::isNotEmpty)
        val segments = url.pathSegments.filter(String::isNotEmpty)
        if (segments.size != parent.size + 1 || segments.take(parent.size) != parent) return null
        val name = segments.last()
        if (name == "." || name == ".." || '/' in name || name.isBlank()) return null
        return path.child(name)
    }

    private companion object { const val DAV = "DAV:" }
}

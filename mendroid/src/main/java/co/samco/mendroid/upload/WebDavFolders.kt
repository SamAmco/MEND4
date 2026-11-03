package co.samco.mendroid.upload

import android.util.Xml
import okhttp3.HttpUrl
import org.xmlpull.v1.XmlPullParser

data class RemoteFolder(val name: String, val path: RemotePath)

/** Only successful DAV properties on immediate child collections become folder entries. */
internal class WebDavFolders {
    fun parse(root: HttpUrl, path: RemotePath, xml: String): List<RemoteFolder> {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput(xml.reader())
        }
        parser.nextTag()
        if (parser.name != "multistatus" || parser.namespace != DAV) throw UploadException(UploadFailure.INVALID_RESPONSE)
        val folders = mutableListOf<RemoteFolder>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.namespace == DAV && parser.name == "response") {
                val entry = response(parser)
                if (entry.collection) child(root, path, entry.href)?.let(folders::add)
            }
        }
        return folders.distinctBy { it.path }.sortedBy { it.name.lowercase() }
    }

    private data class Entry(val href: String, val collection: Boolean)

    private fun response(parser: XmlPullParser): Entry {
        val depth = parser.depth
        var href = ""
        var collection = false
        var valid = true
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.END_TAG && parser.depth == depth) break
            if (parser.eventType != XmlPullParser.START_TAG || parser.namespace != DAV || parser.depth != depth + 1) continue
            when (parser.name) {
                "href" -> href = parser.nextText()
                "propstat" -> collection = propstat(parser) || collection
                "status" -> valid = successful(parser.nextText())
            }
        }
        return Entry(href, valid && collection)
    }

    private fun propstat(parser: XmlPullParser): Boolean {
        val depth = parser.depth
        var collection = false
        var status = ""
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.END_TAG && parser.depth == depth) break
            if (parser.eventType != XmlPullParser.START_TAG || parser.namespace != DAV) continue
            when {
                parser.name == "collection" && parser.depth == depth + 3 -> collection = true
                parser.name == "status" && parser.depth == depth + 1 -> status = parser.nextText()
            }
        }
        return collection && successful(status)
    }

    private fun successful(status: String) = status.split(' ').getOrNull(1)?.toIntOrNull() in 200..299

    private fun child(root: HttpUrl, path: RemotePath, href: String): RemoteFolder? {
        val url = root.resolve(href) ?: return null
        if (url.scheme != root.scheme || url.host != root.host || url.port != root.port) return null
        val parent = root.pathSegments.filter(String::isNotEmpty)
        val segments = url.pathSegments.filter(String::isNotEmpty)
        if (segments.size != parent.size + 1 || segments.take(parent.size) != parent) return null
        val name = segments.last()
        if (name == "." || name == ".." || '/' in name || name.isBlank()) return null
        return RemoteFolder(name, path.child(name))
    }

    private companion object { const val DAV = "DAV:" }
}

package co.samco.mend4.core

interface IBase64EncodingProvider {
    fun decodeBase64(base64String: String): ByteArray
    fun encodeBase64URLSafeString(data: ByteArray): String
}
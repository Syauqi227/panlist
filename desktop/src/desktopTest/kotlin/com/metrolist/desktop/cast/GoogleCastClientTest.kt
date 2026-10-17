package com.metrolist.desktop.cast

import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class GoogleCastClientTest {

    private fun encodeVarint(out: ByteArrayOutputStream, value: Int) {
        var v = value
        while (v and 0x7F.inv() != 0) {
            out.write((v and 0x7F) or 0x80)
            v = v ushr 7
        }
        out.write(v and 0x7F)
    }

    private fun writeStringField(out: ByteArrayOutputStream, fieldNum: Int, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        val tag = (fieldNum shl 3) or 2
        encodeVarint(out, tag)
        encodeVarint(out, bytes.size)
        out.write(bytes)
    }

    private fun buildCastMessage(sourceId: String, destId: String, namespace: String, payloadJson: String): ByteArray {
        val body = ByteArrayOutputStream()
        body.write(0x08)
        body.write(0x00)

        writeStringField(body, 2, sourceId)
        writeStringField(body, 3, destId)
        writeStringField(body, 4, namespace)

        body.write(0x28)
        body.write(0x00)

        writeStringField(body, 6, payloadJson)

        val bodyBytes = body.toByteArray()
        val envelope = ByteArrayOutputStream()
        val dos = DataOutputStream(envelope)
        dos.writeInt(bodyBytes.size)
        dos.write(bodyBytes)
        return envelope.toByteArray()
    }

    @Test
    fun testLaunchDefaultMediaReceiver() {
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate>? = null
            override fun checkClientTrusted(certs: Array<X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(certs: Array<X509Certificate>?, authType: String?) {}
        })

        val sc = SSLContext.getInstance("TLS")
        sc.init(null, trustAll, SecureRandom())

        val socket = sc.socketFactory.createSocket("192.168.1.5", 8009) as SSLSocket
        socket.soTimeout = 4000

        val out = socket.outputStream
        val inStream = DataInputStream(socket.inputStream)

        // 1. Connect
        out.write(buildCastMessage("sender-0", "receiver-0", "urn:x-cast:com.google.cast.tp.connection", """{"type":"CONNECT"}"""))
        out.flush()

        // 2. Launch Default Media Receiver (CC1AD845)
        out.write(buildCastMessage("sender-0", "receiver-0", "urn:x-cast:com.google.cast.receiver", """{"type":"LAUNCH","appId":"CC1AD845","requestId":2}"""))
        out.flush()

        // Read responses
        for (i in 1..4) {
            try {
                val len = inStream.readInt()
                val buf = ByteArray(len)
                inStream.readFully(buf)
                val rawStr = String(buf, Charsets.UTF_8)
                val jsonIdx = rawStr.indexOf("{\"")
                if (jsonIdx != -1) {
                    println("LAUNCH Response: ${rawStr.substring(jsonIdx)}")
                }
            } catch (e: Throwable) {
                break
            }
        }

        socket.close()
    }
}

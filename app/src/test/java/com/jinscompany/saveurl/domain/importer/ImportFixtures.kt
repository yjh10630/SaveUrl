package com.jinscompany.saveurl.domain.importer

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object ImportFixtures {
    val SEOUL: TimeZone = TimeZone.getTimeZone("Asia/Seoul")
    /** 2026-10-13 (테스트 기준 "지금") */
    const val NOW = 1_791_849_600_000L

    fun bytes(name: String): ByteArray =
        requireNotNull(javaClass.classLoader!!.getResourceAsStream("import/$name")) { "missing fixture $name" }.readBytes()

    fun text(name: String): String = String(bytes(name), Charsets.UTF_8)

    fun reader() = ImportFileReader(timeZone = SEOUL, nowMillis = { NOW })

    fun read(name: String, data: ByteArray = bytes(name)): ReadOutcome =
        reader().read(name, data.size.toLong(), ByteArrayInputStream(data))

    fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, data) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(data)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    fun millis(y: Int, mo: Int, d: Int, h: Int, mi: Int, tz: TimeZone = SEOUL): Long =
        java.util.Calendar.getInstance(tz).apply { clear(); set(y, mo - 1, d, h, mi, 0) }.timeInMillis
}

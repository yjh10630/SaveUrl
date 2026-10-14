package com.jinscompany.saveurl.domain.importer

import java.io.InputStream
import java.io.Reader
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction

/**
 * 줄 단위로 읽되 한 줄을 [maxLine] 글자 넘게 메모리에 올리지 않는다 (39번 독립 리뷰 R4).
 *
 * 줄바꿈 없는 큰 파일(축소된 JSON·HTML)도 조각으로 나눠 돌려준다. 링크가 조각 경계에서 끊기지 않도록
 * 상한에 닿으면 마지막 공백·따옴표·`<` `>` 뒤에서 자르고, 그런 글자가 없을 때만 그 자리에서 자른다.
 * 줄바꿈은 `\n`, `\r\n`, `\r` 를 모두 줄 끝으로 본다 (BufferedReader.readLine 과 같음).
 */
internal class BoundedLineReader(private val reader: Reader, private val maxLine: Int = DEFAULT_MAX_LINE) {
    private val buf = CharArray(8192)
    private var pos = 0
    private var len = 0
    private var eof = false
    private var skipLf = false
    private val line = StringBuilder()
    /** 앞 조각에서 자르고 남은 부분 (다음 조각 앞에 붙는다) */
    private var carry: String? = null

    fun readLine(): String? {
        line.setLength(0)
        carry?.let { line.append(it); carry = null }
        var readAny = line.isNotEmpty()
        while (true) {
            if (pos >= len) {
                if (eof) return if (readAny) line.toString() else null
                len = reader.read(buf, 0, buf.size)
                pos = 0
                if (len <= 0) { len = 0; eof = true; continue }
            }
            val c = buf[pos++]
            if (skipLf) {
                skipLf = false
                if (c == '\n') continue
            }
            when (c) {
                '\n' -> return line.toString()
                '\r' -> { skipLf = true; return line.toString() }
                else -> {
                    readAny = true
                    line.append(c)
                    if (line.length >= maxLine) return cut()
                }
            }
        }
    }

    private fun cut(): String {
        var at = -1
        for (i in line.length - 1 downTo line.length / 2) {
            val ch = line[i]
            if (ch.isWhitespace() || ch == '"' || ch == '\'' || ch == '<' || ch == '>') { at = i; break }
        }
        if (at < 0) return line.toString()
        carry = line.substring(at + 1)
        return line.substring(0, at + 1)
    }

    companion object {
        const val DEFAULT_MAX_LINE = 64 * 1024
    }
}

/**
 * UTF-8 로 읽다가 잘못된 바이트가 나오면 그 자리부터 [fallback](CP949) 으로 읽는다 (39번 독립 리뷰 R11).
 *
 * 인코딩은 앞 64 KB 로 정하는데, 앞부분이 영어(ASCII)뿐이고 뒤에 한글이 나오는 CP949 파일은 UTF-8 로 판정된다.
 * ASCII 는 두 인코딩에서 같으므로, 처음 잘못된 바이트에서 바꿔 읽으면 앞부분도 뒷부분도 맞게 읽힌다.
 * 바꾼 뒤에도 못 읽는 바이트는 �로 바꾼다.
 */
internal class Utf8OrFallbackReader(private val input: InputStream, private val fallback: Charset?) : Reader() {
    private var decoder: CharsetDecoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    private var switched = false
    private val bytes = ByteBuffer.allocate(16 * 1024).apply { flip() }
    private var eof = false
    private var flushed = false

    /** 테스트용: 읽다가 [fallback] 으로 바꿨는지 */
    val switchedToFallback: Boolean get() = switched

    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        val out = CharBuffer.wrap(cbuf, off, len)
        while (out.position() == off) {
            if (!eof && bytes.remaining() < 8) refill()
            val result = decoder.decode(bytes, out, eof)
            when {
                result.isError -> switchToFallback()
                result.isOverflow -> break
                // underflow: 바이트가 더 필요함
                eof -> {
                    if (!flushed) { decoder.flush(out); flushed = true }
                    if (out.position() == off) return -1
                    break
                }
                else -> refill()
            }
        }
        return out.position() - off
    }

    private fun refill() {
        bytes.compact()
        val n = input.read(bytes.array(), bytes.arrayOffset() + bytes.position(), bytes.remaining())
        if (n < 0) eof = true else bytes.position(bytes.position() + n)
        bytes.flip()
    }

    private fun switchToFallback() {
        val action = CodingErrorAction.REPLACE
        decoder = if (!switched && fallback != null) {
            fallback.newDecoder().onMalformedInput(action).onUnmappableCharacter(action)
        } else {
            Charsets.UTF_8.newDecoder().onMalformedInput(action).onUnmappableCharacter(action)
        }
        switched = true
    }

    override fun close() = input.close()
}

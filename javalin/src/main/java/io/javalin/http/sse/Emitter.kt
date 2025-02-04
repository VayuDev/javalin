package io.javalin.http.sse

import java.io.IOException
import java.io.InputStream
import jakarta.servlet.http.HttpServletResponse

const val COMMENT_PREFIX = ":"
const val NEW_LINE = "\n"

class Emitter(private var response: HttpServletResponse) {

    var closed = false
        private set

    fun emit(event: String, data: InputStream, id: String?) = synchronized(this) {
        try {
            if (id != null) {
                write("id: $id$NEW_LINE")
            }
            write("event: $event$NEW_LINE")
            write("data: ")
            data.copyTo(response.outputStream)
            write(NEW_LINE)
            write(NEW_LINE)
            response.flushBuffer()
        } catch (ignored: IOException) {
            closed = true
        }
    }

    fun emit(comment: String) =
        try {
            comment.split(NEW_LINE).forEach {
                write("$COMMENT_PREFIX $it$NEW_LINE")
            }
            response.flushBuffer()
        } catch (ignored: IOException) {
            closed = true
        }

    private fun write(value: String) =
        response.outputStream.print(value)

}

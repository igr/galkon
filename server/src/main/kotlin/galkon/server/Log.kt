package galkon.server

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/** Create a logger named after the calling class. Usage: `private val log = logger()` */
fun logger(): Logger {
    val callerClass = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
        .callerClass
    return LoggerFactory.getLogger(callerClass)
}

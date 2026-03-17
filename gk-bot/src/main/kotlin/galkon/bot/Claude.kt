package galkon.bot

import galkon.common.FleetOrderDto
import java.io.File

private var turnCounter = 0
val botCode = "%05d".format(System.currentTimeMillis() % 100000)

/**
 * Writes the prompt to a file and executes Claude Code CLI referencing it.
 * Streams Claude's output to stdout in real-time.
 */
fun askClaude(gameCode: String, prompt: String): String {
    turnCounter++
    val botDir = File("bot").apply { mkdirs() }
    val promptFile = File(botDir, "$gameCode-$botCode-$turnCounter.md")
    val outputFile = File(botDir, "$gameCode-$botCode-$turnCounter-out.md")
    promptFile.writeText(prompt)

    val process = ProcessBuilder(
        "claude",
        "-p", "-",
        "--output-format", "text",
        "--dangerously-skip-permissions",
    )
        .redirectErrorStream(true)
        .start()

    // Send prompt via stdin and close
    process.outputStream.bufferedWriter().use { it.write(prompt) }

    // Stream output in real-time while collecting it
    val output = StringBuilder()
    process.inputStream.bufferedReader().forEachLine { line ->
        println("  claude> $line")
        output.appendLine(line)
    }

    val exitCode = process.waitFor()
    val result = output.toString().trim()

    // Save response for records
    outputFile.writeText(result)

    if (exitCode != 0) {
        throw RuntimeException("Claude exited with code $exitCode: $result")
    }

    return result
}

/**
 * Parses Claude's response into fleet orders.
 * Expected format: one order per line as "FROM TO SHIPS"
 * e.g. "A B 5"
 */
fun parseOrders(response: String, myPlanets: Set<String>): List<FleetOrderDto> {
    val orders = mutableListOf<FleetOrderDto>()
    val lines = response.lines()

    for (line in lines) {
        val trimmed = line.trim()
        // Skip empty lines, markdown fences, headers, "NONE"
        if (trimmed.isEmpty() || trimmed.startsWith("```") || trimmed.startsWith("#") || trimmed.equals("NONE", ignoreCase = true)) {
            continue
        }
        // Match pattern: LETTER LETTER NUMBER (with optional separators)
        val match = Regex("""^([A-Z0-9])\s+([A-Z0-9])\s+(\d+)$""").find(trimmed)
        if (match != null) {
            val (from, to, ships) = match.destructured
            val shipCount = ships.toIntOrNull() ?: continue
            if (shipCount > 0 && from != to && from in myPlanets) {
                orders.add(FleetOrderDto(from = from, to = to, ships = shipCount))
            }
        }
    }

    return orders
}

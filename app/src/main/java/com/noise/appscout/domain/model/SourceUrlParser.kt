package com.noise.appscout.domain.model

/** Coordinates of a source parsed from user input, before an id is assigned. */
data class ParsedSource(
    val type: SourceType,
    val owner: String,
    val repository: String,
    val url: String,
)

/**
 * Turns user input into source coordinates.
 *
 * The interface lives in the domain layer so that provider knowledge (currently GitHub only)
 * stays replaceable infrastructure, exactly like the AI provider.
 */
interface SourceUrlParser {

    /** @return the parsed source, or `null` when the input is not a supported source URL. */
    fun parse(input: String): ParsedSource?
}

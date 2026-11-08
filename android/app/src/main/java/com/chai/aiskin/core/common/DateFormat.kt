package com.chai.aiskin.core.common

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val INDONESIAN = Locale.forLanguageTag("id-ID")
private val DATE_TIME = DateTimeFormatter.ofPattern("d MMM yyyy · HH.mm", INDONESIAN)
private val DATE_LONG = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", INDONESIAN)

fun Instant.formatDateTime(zone: ZoneId = ZoneId.systemDefault()): String = DATE_TIME.format(atZone(zone))
fun Instant.formatLongDate(zone: ZoneId = ZoneId.systemDefault()): String = DATE_LONG.format(atZone(zone))

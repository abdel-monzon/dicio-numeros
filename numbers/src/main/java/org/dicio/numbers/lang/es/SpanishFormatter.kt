package org.dicio.numbers.lang.es

import org.dicio.numbers.formatter.Formatter
import org.dicio.numbers.unit.MixedFraction
import org.dicio.numbers.util.Utils
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

class SpanishFormatter : Formatter("config/es-es") {

    override fun niceNumber(mixedFraction: MixedFraction, speech: Boolean): String {
        if (speech) {
            val sign = if (mixedFraction.negative) "menos " else ""
            if (mixedFraction.numerator == 0) {
                return sign + pronounceNumber(mixedFraction.whole.toDouble(), 0, true, false, false)
            }

            var denominatorString = when (mixedFraction.denominator) {
                2 -> "medio"
                4 -> "cuarto"
                else -> pronounceNumber(mixedFraction.denominator.toDouble(), 0, true, false, true)
            }

            val numeratorString: String
            if (mixedFraction.numerator == 1) {
                numeratorString = "un"
                if (mixedFraction.denominator == 2) {
                    denominatorString = "medio"
                } else if (mixedFraction.denominator == 4) {
                    denominatorString = "cuarto"
                }
            } else {
                denominatorString = if (mixedFraction.denominator == 2) {
                    "medios"
                } else if (mixedFraction.denominator == 4) {
                    "cuartos"
                } else {
                    denominatorString.substring(0, denominatorString.length - 1) + "avos"
                }
                numeratorString = pronounceNumber(mixedFraction.numerator.toDouble(), 0, true, false, false)
            }

            return if (mixedFraction.whole == 0L) {
                "$sign$numeratorString $denominatorString"
            } else {
                (sign + pronounceNumber(mixedFraction.whole.toDouble(), 0, true, false, false)
                        + " y " + numeratorString + " " + denominatorString)
            }
        } else {
            return niceNumberNotSpeech(mixedFraction)
        }
    }

    override fun pronounceNumber(
        number: Double,
        places: Int,
        shortScale: Boolean,
        scientific: Boolean,
        ordinal: Boolean
    ): String {
        if (number == Double.POSITIVE_INFINITY) {
            return "infinito"
        } else if (number == Double.NEGATIVE_INFINITY) {
            return "menos infinito"
        } else if (java.lang.Double.isNaN(number)) {
            return "no es un número"
        }

        if (scientific || abs(number) > 999999999999999934463.0) {
            val scientificFormatted = String.format(Locale.ENGLISH, "%E", number)
            val parts = scientificFormatted.split("E".toRegex(), limit = 2).toTypedArray()
            val power = parts[1].toInt().toDouble()

            if (power != 0.0) {
                val n = parts[0].toDouble()
                return String.format(
                    "%s por diez a la %s",
                    pronounceNumber(n, places, shortScale, false, false),
                    pronounceNumber(power, places, shortScale, false, false)
                )
            }
        }

        val result = StringBuilder()
        var varNumber = number
        if (varNumber < 0) {
            varNumber = -varNumber
            if (places != 0 || varNumber >= 0.5) {
                result.append("menos ")
            }
        }

        val realPlaces = Utils.decimalPlacesNoFinalZeros(varNumber, places)
        val numberIsWhole = realPlaces == 0
        val numberLong = varNumber.toLong() + (if (varNumber % 1 >= 0.5 && numberIsWhole) 1 else 0)

        if (shortScale) {
            var ordi = ordinal && numberIsWhole
            val groups = Utils.splitByModulus(numberLong, 1000)
            val groupNames = ArrayList<String>()
            for (i in groups.indices) {
                val z = groups[i]
                if (z == 0L) {
                    continue
                }
                var groupName = subThousand(z, i == 0 && ordi)

                if (i != 0) {
                    val magnitude = Utils.longPow(1000, i)
                    if (ordi) {
                        if (z == 1L) {
                            groupName = ORDINAL_NAMES_SHORT_SCALE[magnitude]!!
                        } else {
                            groupName += " " + ORDINAL_NAMES_SHORT_SCALE[magnitude]
                        }
                    } else {
                        groupName += " " + NUMBER_NAMES_SHORT_SCALE[magnitude]
                    }
                }

                groupNames.add(groupName)
                ordi = false
            }

            appendSplitGroups(result, groupNames)
        } else {
            var ordi = ordinal && numberIsWhole
            val groups = Utils.splitByModulus(numberLong, 1000000)
            val groupNames = ArrayList<String>()
            for (i in groups.indices) {
                val z = groups[i]
                if (z == 0L) {
                    continue
                }

                var groupName: String
                if (z < 1000) {
                    groupName = subThousand(z, i == 0 && ordi)
                } else {
                    groupName = subThousand(z / 1000, false) + " mil"
                    if (z % 1000 != 0L) {
                        groupName += (if (i == 0) ", " else " ") + subThousand(
                            z % 1000,
                            i == 0 && ordi
                        )
                    } else if (i == 0 && ordi) {
                        if (z / 1000 == 1L) {
                            groupName = "milésimo"
                        } else {
                            groupName += "ésimo"
                        }
                    }
                }

                if (i != 0) {
                    val magnitude = Utils.longPow(1000000, i)
                    if (ordi) {
                        if (z == 1L) {
                            groupName = ORDINAL_NAMES_LONG_SCALE[magnitude]!!
                        } else {
                            groupName += " " + ORDINAL_NAMES_LONG_SCALE[magnitude]
                        }
                    } else {
                        groupName += " " + NUMBER_NAMES_LONG_SCALE[magnitude]
                    }
                }

                groupNames.add(groupName)
                ordi = false
            }

            appendSplitGroups(result, groupNames)
        }

        if (realPlaces > 0) {
            if (varNumber < 1.0 && (result.isEmpty() || "menos ".contentEquals(result))) {
                result.append("cero")
            }
            result.append(" coma")

            val fractionalPart = String.format("%." + realPlaces + "f", varNumber % 1)
            for (i in 2 until fractionalPart.length) {
                result.append(" ")
                result.append(NUMBER_NAMES[(fractionalPart[i].code - '0'.code).toLong()])
            }
        }

        return result.toString()
    }

    override fun niceTime(
        time: LocalTime,
        speech: Boolean,
        use24Hour: Boolean,
        showAmPm: Boolean
    ): String {
        if (speech) {
            if (use24Hour) {
                val result = StringBuilder()
                if (time.hour < 10) {
                    result.append("cero ")
                }
                result.append(pronounceNumberDuration(time.hour.toLong()))

                result.append(" ")
                if (time.minute == 0) {
                    result.append("cero")
                } else {
                    if (time.minute < 10) {
                        result.append("cero ")
                    }
                    result.append(pronounceNumberDuration(time.minute.toLong()))
                }

                return result.toString()
            } else {
                if (time.hour == 0 && time.minute == 0) {
                    return "medianoche"
                } else if (time.hour == 12 && time.minute == 0) {
                    return "mediodía"
                }

                val normalizedHour = (time.hour + 11) % 12 + 1
                val result = StringBuilder()
                when (time.minute) {
                    15 -> {
                        result.append("cuarto pasadas las ")
                        result.append(pronounceNumberDuration(normalizedHour.toLong()))
                    }
                    30 -> {
                        result.append("media pasada las ")
                        result.append(pronounceNumberDuration(normalizedHour.toLong()))
                    }
                    45 -> {
                        result.append("cuarto para las ")
                        result.append(pronounceNumberDuration((normalizedHour % 12 + 1).toLong()))
                    }
                    else -> {
                        result.append("las ")
                        result.append(pronounceNumberDuration(normalizedHour.toLong()))

                        if (time.minute == 0) {
                            if (!showAmPm) {
                                return "$result en punto"
                            }
                        } else {
                            result.append(" y ")
                            if (time.minute < 10) {
                                result.append("cero ")
                            }
                            result.append(pronounceNumberDuration(time.minute.toLong()))
                        }
                    }
                }

                if (showAmPm) {
                    result.append(if (time.hour >= 12) " p.m." else " a.m.")
                }
                return result.toString()
            }
        } else {
            return if (use24Hour) {
                time.format(DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("es-ES")))
            } else {
                val result = time.format(
                    DateTimeFormatter.ofPattern(
                        if (showAmPm) "h:mm a" else "h:mm", Locale.forLanguageTag("es-ES")
                    )
                )
                if (result.startsWith("0:")) {
                    "12:" + result.substring(2)
                } else {
                    result
                }
            }
        }
    }

    private fun subThousand(n: Long, ordinal: Boolean): String {
        if (ordinal && ORDINAL_NAMES.containsKey(n)) {
            return ORDINAL_NAMES[n]!!
        } else if (n < 100) {
            if (!ordinal && NUMBER_NAMES.containsKey(n)) {
                return NUMBER_NAMES[n]!!
            }
            return (NUMBER_NAMES[n - n % 10]
                    + (if (n % 10 > 0) " y " + subThousand(n % 10, ordinal) else ""))
        } else {
            return (NUMBER_NAMES[n / 100] + "cientos"
                    + (if (n % 100 > 0) " " + subThousand(n % 100, ordinal)
            else (if (ordinal) "ésimo" else "")))
        }
    }

    private fun appendSplitGroups(result: StringBuilder, groupNames: List<String>) {
        if (groupNames.isNotEmpty()) {
            result.append(groupNames[groupNames.size - 1])
        }

        for (i in groupNames.size - 2 downTo 0) {
            result.append(", ")
            result.append(groupNames[i])
        }
    }

    companion object {
        val NUMBER_NAMES = mapOf(
            0L to "cero",
            1L to "uno",
            2L to "dos",
            3L to "tres",
            4L to "cuatro",
            5L to "cinco",
            6L to "seis",
            7L to "siete",
            8L to "ocho",
            9L to "nueve",
            10L to "diez",
            11L to "once",
            12L to "doce",
            13L to "trece",
            14L to "catorce",
            15L to "quince",
            16L to "dieciséis",
            17L to "diecisiete",
            18L to "dieciocho",
            19L to "diecinueve",
            20L to "veinte",
            30L to "treinta",
            40L to "cuarenta",
            50L to "cincuenta",
            60L to "sesenta",
            70L to "setenta",
            80L to "ochenta",
            90L to "noventa",
            100L to "cien",
            1000L to "mil",
            1000000L to "millón",
        )

        val NUMBER_NAMES_SHORT_SCALE = NUMBER_NAMES + mapOf(
            1000000000L to "mil millones",
            1000000000000L to "billón",
            1000000000000000L to "mil billones",
            1000000000000000000L to "trillón",
        )

        val NUMBER_NAMES_LONG_SCALE = NUMBER_NAMES + mapOf(
            1000000000000L to "billón",
            1000000000000000000L to "trillón",
        )

        val ORDINAL_NAMES = mapOf(
            1L to "primero",
            2L to "segundo",
            3L to "tercero",
            4L to "cuarto",
            5L to "quinto",
            6L to "sexto",
            7L to "séptimo",
            8L to "octavo",
            9L to "noveno",
            10L to "décimo",
            100L to "centésimo",
            1000L to "milésimo",
            1000000L to "millonésimo",
        )

        val ORDINAL_NAMES_SHORT_SCALE = ORDINAL_NAMES + mapOf(
            1000000000L to "mil millonésimo",
            1000000000000L to "billonésimo",
            1000000000000000L to "mil billonésimo",
            1000000000000000000L to "trillonésimo",
        )

        val ORDINAL_NAMES_LONG_SCALE = ORDINAL_NAMES + mapOf(
            1000000000000L to "billonésimo",
            1000000000000000000L to "trillonésimo",
        )
    }
}

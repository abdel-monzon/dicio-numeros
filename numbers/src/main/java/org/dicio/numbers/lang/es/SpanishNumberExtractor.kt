package org.dicio.numbers.lang.es

import org.dicio.numbers.parser.lexer.TokenStream
import org.dicio.numbers.unit.Number
import org.dicio.numbers.util.NumberExtractorUtils

class SpanishNumberExtractor internal constructor(
    private val ts: TokenStream,
    private val shortScale: Boolean
) {
    fun numberPreferOrdinal(): Number? {
        var number = numberSuffixMultiplier()
        if (number == null) {
            number = numberSignPoint(true)
        }
        return divideByDenominatorIfPossible(number)
    }

    fun numberPreferFraction(): Number? {
        var number = numberSuffixMultiplier()
        if (number == null) {
            number = numberSignPoint(false)
        }
        number = divideByDenominatorIfPossible(number)
        if (number == null) {
            number = numberSignPoint(true)
        }
        return number
    }

    fun numberNoOrdinal(): Number? {
        var number = numberSuffixMultiplier()
        if (number == null) {
            number = numberSignPoint(false)
        }
        number = divideByDenominatorIfPossible(number)
        return number
    }

    fun divideByDenominatorIfPossible(numberToEdit: Number?): Number? {
        if (numberToEdit == null) {
            if (ts[0].isValue("un") || ts[0].isValue("una")) {
                val originalPosition = ts.position
                ts.movePositionForwardBy(1)
                val denominator = numberInteger(true)
                if (denominator != null && denominator.isOrdinal && denominator.moreThan(2)) {
                    return Number(1).divide(denominator)
                } else {
                    ts.position = originalPosition
                }
            }
            return null
        }

        if (!numberToEdit.isOrdinal && !numberToEdit.isDecimal && !ts[0].hasCategory("ignore")) {
            val originalPosition = ts.position
            val denominator = numberInteger(true)
            if (denominator == null) {
                if (ts[0].hasCategory("suffix_multiplier")) {
                    ts.movePositionForwardBy(1)
                    val multiplier = ts[-1].number
                    if (multiplier!!.isDecimal && (1 / multiplier.decimalValue()).toLong()
                            .toDouble() == (1 / multiplier.decimalValue())
                    ) {
                        return numberToEdit.divide((1 / multiplier.decimalValue()).toLong())
                    }
                    return numberToEdit.multiply(multiplier)
                }
            } else if (denominator.isOrdinal && denominator.moreThan(2)) {
                return numberToEdit.divide(denominator)
            } else {
                ts.position = originalPosition
            }
        }
        return numberToEdit
    }

    fun numberSuffixMultiplier(): Number? {
        if (ts[0].hasCategory("suffix_multiplier")) {
            ts.movePositionForwardBy(1)
            return ts[-1].number
        } else if ((ts[0].isValue("un") || ts[0].isValue("una")) && ts[1].hasCategory("suffix_multiplier")) {
            ts.movePositionForwardBy(2)
            return ts[-1].number
        } else {
            return null
        }
    }

    fun numberSignPoint(allowOrdinal: Boolean): Number? {
        return NumberExtractorUtils.signBeforeNumber(ts) { numberPoint(allowOrdinal) }
    }

    fun numberPoint(allowOrdinal: Boolean): Number? {
        var n = numberInteger(allowOrdinal)
        if (n != null && n.isOrdinal) {
            return n
        }

        if (ts[0].hasCategory("point")) {
            if (!ts[1].hasCategory("digit_after_point")
                && (!NumberExtractorUtils.isRawNumber(ts[1]) || ts[2].hasCategory("ordinal_suffix"))
            ) {
                return n
            }

            ts.movePositionForwardBy(1)
            if (n == null) {
                n = Number(0.0)
            }

            var magnitude = 0.1
            if (ts[0].value.length > 1 && NumberExtractorUtils.isRawNumber(ts[0])) {
                for (i in 0 until ts[0].value.length) {
                    n = n!!.plus((ts[0].value[i].code - '0'.code) * magnitude)
                    magnitude /= 10.0
                }
                ts.movePositionForwardBy(1)
            } else {
                while (true) {
                    if (ts[0].hasCategory("digit_after_point")
                        || (ts[0].value.length == 1 && NumberExtractorUtils.isRawNumber(ts[0])
                                && !ts[1].hasCategory("ordinal_suffix"))
                    ) {
                        n = n!!.plus(ts[0].number!!.multiply(magnitude))
                        magnitude /= 10.0
                    } else {
                        break
                    }
                    ts.movePositionForwardBy(1)
                }
            }
        } else if (n != null && ts[0].hasCategory("fraction_separator")) {
            val originalPosition = ts.position
            ts.movePositionForwardBy(1)
            if (ts[0].hasCategory("fraction_separator_secondary")) {
                ts.movePositionForwardBy(1)
            }

            val denominator = numberInteger(false)
            if (denominator == null || (denominator.isInteger && denominator.integerValue() == 0L)
                || (denominator.isDecimal && denominator.decimalValue() == 0.0)
            ) {
                ts.position = originalPosition
            } else {
                return n.divide(denominator)
            }
        }

        return n
    }

    fun numberInteger(allowOrdinal: Boolean): Number? {
        if (ts[0].hasCategory("ignore")
            && (!ts[0].isValue("un") || ts[1].hasCategory("ignore"))
        ) {
            return null
        }

        var n = NumberExtractorUtils.numberMadeOfGroups(
            ts,
            allowOrdinal,
            if (shortScale)
                NumberExtractorUtils::numberGroupShortScale
            else
                NumberExtractorUtils::numberGroupLongScale
        )
        if (n == null) {
            return NumberExtractorUtils.numberBigRaw(ts, allowOrdinal)
        } else if (n.isOrdinal) {
            return n
        }

        if (n.lessThan(21) && n.moreThan(9) && !ts[-1].hasCategory("raw")) {
            val secondGroup = numberYearSecondGroup(allowOrdinal)
            if (secondGroup != null) {
                return n.multiply(100).plus(secondGroup).withOrdinal(secondGroup.isOrdinal)
            }
        }

        if (n.lessThan(100)) {
            val nextNotIgnore = ts.indexOfWithoutCategory("ignore", 0)
            if (ts[nextNotIgnore].hasCategory("hundred")) {
                val ordinal = ts[nextNotIgnore].hasCategory("ordinal")
                if (allowOrdinal || !ordinal) {
                    ts.movePositionForwardBy(nextNotIgnore + 1)
                    return n.multiply(100).withOrdinal(ordinal)
                }
            }
        }

        if (n.lessThan(1000)) {
            if (NumberExtractorUtils.isRawNumber(ts[-1]) && ts[0].hasCategory("thousand_separator") &&
                ts[1].value.length == 3 && NumberExtractorUtils.isRawNumber(ts[1])
            ) {
                val originalPosition = ts.position - 1

                while (ts[0].hasCategory("thousand_separator") && ts[1].value.length == 3 &&
                    NumberExtractorUtils.isRawNumber(ts[1])
                ) {
                    n = n!!.multiply(1000).plus(ts[1].number)
                    ts.movePositionForwardBy(2)
                }

                if (ts[0].hasCategory("ordinal_suffix")) {
                    if (allowOrdinal) {
                        ts.movePositionForwardBy(1)
                        return n!!.withOrdinal(true)
                    } else {
                        ts.position = originalPosition
                        return null
                    }
                }
            }
        }

        return n
    }

    fun numberYearSecondGroup(allowOrdinal: Boolean): Number? {
        val nextNotIgnore = ts.indexOfWithoutCategory("ignore", 0)

        if (ts[nextNotIgnore].isNumberEqualTo(0)) {
            val digitIndex = ts.indexOfWithoutCategory("ignore", nextNotIgnore + 1)
            val ordinal = ts[digitIndex].hasCategory("ordinal")
            if (ts[digitIndex].number?.lessThan(10) == true && (allowOrdinal || !ordinal)) {
                ts.movePositionForwardBy(digitIndex + 1)
                return ts[-1].number!!.withOrdinal(ordinal)
            }
        } else if (ts[nextNotIgnore].hasCategory("teen")) {
            val ordinal = ts[nextNotIgnore].hasCategory("ordinal")
            if (!allowOrdinal && ordinal) {
                return null
            } else {
                ts.movePositionForwardBy(nextNotIgnore + 1)
                return ts[-1].number!!.withOrdinal(ordinal)
            }
        } else if (ts[nextNotIgnore].value.length == 2
            && NumberExtractorUtils.isRawNumber(ts[nextNotIgnore])
        ) {
            val ordinal = ts[nextNotIgnore + 1].hasCategory("ordinal_suffix")
            if (!allowOrdinal && ordinal) {
                return null
            } else {
                ts.movePositionForwardBy(nextNotIgnore + (if (ordinal) 2 else 1))
                return ts[if (ordinal) -2 else -1].number!!.withOrdinal(ordinal)
            }
        } else if (ts[nextNotIgnore].hasCategory("tens")) {
            val tens = ts[nextNotIgnore].number
            if (ts[nextNotIgnore].hasCategory("ordinal")) {
                if (allowOrdinal) {
                    ts.movePositionForwardBy(nextNotIgnore + 1)
                    return tens!!.withOrdinal(true)
                } else {
                    return null
                }
            }
            ts.movePositionForwardBy(nextNotIgnore + 1)

            val digitIndex = ts.indexOfWithoutCategory("ignore", 0)
            val ordinal = ts[digitIndex].hasCategory("ordinal")
            if (ts[digitIndex].hasCategory("digit") && (allowOrdinal || !ordinal)) {
                ts.movePositionForwardBy(digitIndex + 1)
                return tens!!.plus(ts[-1].number).withOrdinal(ordinal)
            } else {
                return tens
            }
        }

        return null
    }
}

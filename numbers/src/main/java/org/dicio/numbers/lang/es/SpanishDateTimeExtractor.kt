package org.dicio.numbers.lang.es

import org.dicio.numbers.parser.lexer.TokenStream
import org.dicio.numbers.unit.Duration
import org.dicio.numbers.util.DateTimeExtractorUtils
import org.dicio.numbers.util.DurationExtractorUtils
import org.dicio.numbers.util.NumberExtractorUtils
import org.dicio.numbers.util.Utils
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

class SpanishDateTimeExtractor internal constructor(
    private val ts: TokenStream,
    private val shortScale: Boolean,
    private val preferMonthBeforeDay: Boolean,
    private val now: LocalDateTime
) {
    private val numberExtractor = SpanishNumberExtractor(ts, shortScale)
    private val durationExtractor = DurationExtractorUtils(ts, numberExtractor::numberNoOrdinal)
    private val dateTimeExtractor = DateTimeExtractorUtils(ts, now, this::extractIntegerInRange)

    private fun extractIntegerInRange(
        fromInclusive: Int,
        toInclusive: Int,
        allowOrdinal: Boolean = false
    ): Int? {
        return NumberExtractorUtils.extractOneIntegerInRange(
            ts, fromInclusive, toInclusive
        ) { NumberExtractorUtils.signBeforeNumber(ts) { numberExtractor.numberInteger(allowOrdinal) } }
    }

    fun dateTime(): LocalDateTime? {
        return ts.firstWhichUsesMostTokens({ dateTime(false) }, { dateTime(true) })
    }

    private fun dateTime(timeFirst: Boolean): LocalDateTime? {
        var date: LocalDate? = null
        var time: LocalTime? = null

        if (!timeFirst) {
            date = relativeSpecialDay()

            if (date == null) {
                val duration = Utils.firstNotNull(
                    this::relativeDuration,
                    dateTimeExtractor::relativeMonthDuration
                )
                if (duration == null) {
                    date = date()
                } else if (duration.nanos == 0L && duration.days != 0L) {
                    date = duration.applyAsOffsetToDateTime(now).toLocalDate()
                } else if (duration.nanos != 0L && duration.days == 0L && duration.months == 0L && duration.years == 0L) {
                    time = duration.applyAsOffsetToDateTime(now).toLocalTime()
                } else {
                    return duration.applyAsOffsetToDateTime(now)
                }
            }
        }

        if (time == null) {
            time = ts.tryOrSkipDateTimeIgnore(date != null) { this.timeWithAmpm() }
        }

        if (date == null && time != null) {
            val originalPosition = ts.position
            val duration = ts.tryOrSkipDateTimeIgnore(true) { this.relativeDuration() }
            if (duration == null) {
                date = ts.tryOrSkipDateTimeIgnore(true) {
                    Utils.firstNotNull(this::relativeSpecialDay, this::date)
                }
            } else if (duration.nanos == 0L && duration.days != 0L) {
                date = duration.applyAsOffsetToDateTime(now).toLocalDate()
            } else {
                ts.position = originalPosition
            }
        }

        return if (date == null) {
            time?.atDate(now.toLocalDate())
        } else {
            if (time == null) date.atTime(now.toLocalTime()) else date.atTime(time)
        }
    }

    fun timeWithAmpm(): LocalTime? {
        var time = time()
        val pm: Boolean?
        if (time == null) {
            val momentOfDay = momentOfDay() ?: return null

            time = ts.tryOrSkipDateTimeIgnore(true) { this.time() }
            if (time == null) {
                return LocalTime.of(momentOfDay, 0)
            } else {
                pm = DateTimeExtractorUtils.isMomentOfDayPm(momentOfDay)
            }
        } else {
            pm = ts.tryOrSkipDateTimeIgnore(true) {
                Utils.firstNotNull(
                    dateTimeExtractor::ampm,
                    { momentOfDay()?.let(DateTimeExtractorUtils::isMomentOfDayPm) }
                )
            }
        }

        if (time.hour != 0 && pm != null) {
            if (!pm && time.hour == 12) {
                time = time.withHour(0)
            } else if (pm && !DateTimeExtractorUtils.isMomentOfDayPm(time.hour)) {
                time = time.withHour((time.hour + 12) % DateTimeExtractorUtils.HOURS_IN_DAY)
            }
        }
        return time
    }

    fun time(): LocalTime? {
        val originalPosition = ts.position
        val specialMinute = specialMinute()

        val hour = Utils.firstNotNull(this::noonMidnightLike, this::hour)
        if (hour == null) {
            ts.position = originalPosition
            return null
        } else if (specialMinute != null) {
            return if (specialMinute < 0) {
                LocalTime.of(
                    (hour + DateTimeExtractorUtils.HOURS_IN_DAY - 1) % DateTimeExtractorUtils.HOURS_IN_DAY,
                    60 + specialMinute
                )
            } else {
                LocalTime.of(hour, specialMinute)
            }
        }
        var result = LocalTime.of(hour, 0)

        if (oClock()) {
            return result
        }

        val minute = ts.tryOrSkipDateTimeIgnore(true) { dateTimeExtractor.minute() }
        if (minute == null) {
            return result
        }
        result = result.withMinute(minute)

        val second = ts.tryOrSkipDateTimeIgnore(true) { dateTimeExtractor.second() }
        if (second == null) {
            return result
        }
        return result.withSecond(second)
    }

    fun date(): LocalDate? {
        var result = now.toLocalDate()

        val dayOfWeek = dateTimeExtractor.dayOfWeek()
        val firstNum = ts.tryOrSkipDateTimeIgnore(
            dayOfWeek != null
        ) { extractIntegerInRange(1, 31, true) }

        if (firstNum == null && dayOfWeek != null) {
            return result.plus((dayOfWeek - result.dayOfWeek.ordinal).toLong(), ChronoUnit.DAYS)
        }

        val monthName = ts.tryOrSkipDateTimeIgnore(
            firstNum != null
        ) { dateTimeExtractor.monthName() }
        if (monthName == null) {
            result = if (firstNum == null) {
                result.withDayOfMonth(1).withMonth(1)
            } else {
                val secondNumMax = if (firstNum <= 12) 31 else 12
                val secondNum = ts.tryOrSkipDateTimeIgnore(
                    true
                ) { extractIntegerInRange(1, secondNumMax, true) }

                if (secondNum == null) {
                    return if (preferMonthBeforeDay && firstNum <= 12) {
                        result.withDayOfMonth(1).withMonth(firstNum)
                    } else {
                        result.withDayOfMonth(firstNum)
                    }
                } else {
                    if ((preferMonthBeforeDay || secondNum > 12) && firstNum <= 12) {
                        result.withDayOfMonth(secondNum).withMonth(firstNum)
                    } else {
                        result.withDayOfMonth(firstNum).withMonth(secondNum)
                    }
                }
            }
        } else {
            result = result.withMonth(monthName)

            if (firstNum == null) {
                val secondNum = ts.tryOrSkipDateTimeIgnore(
                    true
                ) { extractIntegerInRange(1, 31, true) }
                result = if (secondNum == null) {
                    result.withDayOfMonth(1)
                } else {
                    result.withDayOfMonth(secondNum)
                }
            } else {
                result = result.withDayOfMonth(firstNum)
            }
        }
        val dayOrMonthFound = firstNum != null || monthName != null

        var bcad = ts.tryOrSkipDateTimeIgnore(dayOrMonthFound) { this.bcad() }

        val year = ts.tryOrSkipDateTimeIgnore(
            dayOrMonthFound && bcad == null
        ) { extractIntegerInRange(0, 999999999) }
        if (year == null) {
            if (dayOrMonthFound) {
                return result
            }
            return null
        }

        if (bcad == null) {
            bcad = bcad()
        }
        return result.withYear(year * (if (bcad == null || bcad) 1 else -1))
    }

    fun bcad(): Boolean? {
        val bcad = dateTimeExtractor.bcad()
        if (bcad != null && !bcad) {
            val nextNotIgnore = ts.indexOfWithoutCategory("date_time_ignore", 0)
            if (ts[nextNotIgnore].hasCategory("bcad_era")) {
                ts.movePositionForwardBy(nextNotIgnore + 1)
            }
        }
        return bcad
    }

    fun noonMidnightLike(): Int? {
        return noonMidnightLikeOrMomentOfDay("noon_midnight_like")
    }

    fun momentOfDay(): Int? {
        return noonMidnightLikeOrMomentOfDay("moment_of_day")
    }

    private fun noonMidnightLikeOrMomentOfDay(category: String): Int? {
        val originalPosition = ts.position

        var relativeIndicator = 0
        if (ts[0].hasCategory("pre_special_hour")) {
            if (ts[0].hasCategory("pre_relative_indicator")) {
                relativeIndicator = if (ts[0].hasCategory("negative")) -1 else 1
                ts.movePositionForwardBy(ts.indexOfWithoutCategory("date_time_ignore", 1))
            } else {
                ts.movePositionForwardBy(1)
            }
        }

        if (ts[0].hasCategory(category)) {
            ts.movePositionForwardBy(1)
            return ((ts[-1].number!!.integerValue().toInt()
                    + DateTimeExtractorUtils.HOURS_IN_DAY + relativeIndicator)
                    % DateTimeExtractorUtils.HOURS_IN_DAY)
        }

        if (ts[0].value.startsWith("medio") || ts[0].value.startsWith("media")) {
            if (ts[1].value.startsWith("día") || ts[1].value.startsWith("dia")) {
                ts.movePositionForwardBy(2)
                return 12 + relativeIndicator
            } else if (ts[1].value.startsWith("noche")) {
                ts.movePositionForwardBy(2)
                return (DateTimeExtractorUtils.HOURS_IN_DAY + relativeIndicator) % DateTimeExtractorUtils.HOURS_IN_DAY
            }
        }

        ts.position = originalPosition
        return null
    }

    fun hour(): Int? {
        val originalPosition = ts.position

        ts.movePositionForwardBy(ts.indexOfWithoutCategory("pre_hour", 0))

        val number = extractIntegerInRange(0, DateTimeExtractorUtils.HOURS_IN_DAY)
        if (number == null) {
            ts.position = originalPosition
            return null
        }

        return number % DateTimeExtractorUtils.HOURS_IN_DAY
    }

    fun specialMinute(): Int? {
        val originalPosition = ts.position

        ts.movePositionForwardBy(ts.indexOfWithoutCategory("pre_hour", 0))

        val number = numberExtractor.numberNoOrdinal()
        if (number != null && number.isDecimal && number.decimalValue() > 0.0 && number.decimalValue() < 1.0) {
            return Utils.roundToInt(number.decimalValue() * 60)
        }

        ts.position = originalPosition
        return null
    }

    fun oClock(): Boolean {
        if (ts[0].hasCategory("pre_oclock")) {
            val nextNotIgnore = ts.indexOfWithoutCategory("date_time_ignore", 1)
            if (ts[nextNotIgnore].hasCategory("post_oclock")) {
                ts.movePositionForwardBy(nextNotIgnore + 1)
                return true
            }
        } else if (ts[0].hasCategory("oclock_combined")) {
            ts.movePositionForwardBy(1)
            return true
        }
        return false
    }

    private fun relativeSpecialDay(): LocalDate? {
        val days = Utils.firstNotNull(
            this::relativeYesterday,
            dateTimeExtractor::relativeToday,
            this::relativeTomorrow,
            dateTimeExtractor::relativeDayOfWeekDuration
        )
        if (days == null) {
            return null
        }
        return now.toLocalDate().plusDays(days.toLong())
    }

    fun relativeYesterday(): Int? {
        val originalPosition = ts.position

        var dayCount = 0
        while (ts[0].hasCategory("yesterday_adder")) {
            ++dayCount
            ts.movePositionForwardBy(ts.indexOfWithoutCategory("date_time_ignore", 1))
        }

        if (!ts[0].hasCategory("yesterday")) {
            ts.position = originalPosition
            return null
        }
        ts.movePositionForwardBy(1)
        ++dayCount

        val nextNotIgnore = ts.indexOfWithoutCategory("date_time_ignore", 0)
        if (dayCount == 1 && ts[nextNotIgnore].hasCategory("yesterday_adder")) {
            ++dayCount
            ts.movePositionForwardBy(nextNotIgnore + 1)
        }

        return -dayCount
    }

    fun relativeTomorrow(): Int? {
        val originalPosition = ts.position

        var dayCount = 0
        while (ts[0].hasCategory("tomorrow_adder")) {
            ++dayCount
            ts.movePositionForwardBy(ts.indexOfWithoutCategory("date_time_ignore", 1))
        }

        if (!ts[0].hasCategory("tomorrow")) {
            ts.position = originalPosition
            return null
        }
        ts.movePositionForwardBy(1)
        ++dayCount

        return dayCount
    }

    fun relativeDuration(): Duration? {
        return dateTimeExtractor.relativeIndicatorDuration(
            { durationExtractor.duration() },
            { duration -> duration.multiply(-1) }
        )
    }
}

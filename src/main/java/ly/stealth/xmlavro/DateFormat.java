package ly.stealth.xmlavro;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

public class DateFormat {
    private ZoneId zoneId;
    private String[] formats;

    private DateFormat(String zoneId, String[] formats) {
        this.zoneId = ZoneId.of(zoneId);
        this.formats = formats;
    }

    private static DateFormat dateFormatter = new DateFormat("UTC", new String[]{
            "yyyy/MM/dd", "yyyy-MM-dd", "yyyyMMdd", "dd-MM-yyyy", "dd/MM/yyyy"
    });

    private static DateFormat dateTimeFormatter = new DateFormat("UTC", new String[]{
            "yyyy-MM-ddTmmss_SSS", "yyyy-MM-ddTmmss", "yyyy-MM-ddTmm", "yyyyMMdd'T'HHmmss_SSS", "yyyy-MM-dd'T'HH:mm:ss",
            "yy-MM-ddTmmss_SSS", "yy-MM-ddTmmss", "yy-MM-ddTmm", "yyMMdd'T'HHmmss_SSS", "yy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss[XXX][X]", "yyyy-MM-dd'T'HH:mm:ss[X]"
    });

    private static DateFormat getDateInstance() {
        return dateFormatter;
    }

    private static DateFormat getDateTimeInstance() {
        return dateTimeFormatter;
    }

    private Optional<Long> parseDate(String text, String format) {
        try {
            DateTimeFormatter dtformat = DateTimeFormatter.ofPattern(format);
            LocalDate localDate = LocalDate.parse(text, dtformat);
            return Optional.of(1000 * localDate.atStartOfDay(zoneId).toEpochSecond());
        } catch (DateTimeParseException e) {
        }
        return Optional.empty();
    }

    private Optional<Long> parseZoneDate(String text, String format) {
        try {
            DateTimeFormatter dtformat = DateTimeFormatter.ofPattern(format).withZone(zoneId);
            ZonedDateTime zoneDate = ZonedDateTime.parse(text, dtformat);
            return Optional.of(zoneDate.toEpochSecond());
        } catch (DateTimeParseException e) {
        }
        try {
            DateTimeFormatter dtformat = DateTimeFormatter.ofPattern(format);
            ZonedDateTime zoneDate = ZonedDateTime.parse(text, dtformat);
            return Optional.of(zoneDate.toEpochSecond());
        } catch (DateTimeParseException e) {
        }
        return Optional.empty();
    }

    public long parse(String text) {
        for (String format : formats) {
            Optional<Long> value;

            try {
                value = parseZoneDate(text, format);
                if (value.isPresent()) return value.get();
            } catch (Throwable t) {
            }
        }
        for (String format : formats) {
            Optional<Long> value;

            try {
                value = parseDate(text, format);
                if (value.isPresent()) return value.get();
            } catch (Throwable t) {
            }
        }
        return Long.parseLong(text);
    }

    public static long parseAnything(String text) {
        try {
            return getDateTimeInstance().parse(text);
        } catch (Throwable t) {
        }
        try {
            return getDateInstance().parse(text);
        } catch (Throwable t) {
        }
        return -1;
    }
}


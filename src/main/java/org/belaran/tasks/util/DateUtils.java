package org.belaran.tasks.util;

import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.logging.Logger;

import com.google.api.client.util.DateTime;

public class DateUtils {

	private static final long ONE_DAY__IN_MILLIS = 86400000;
	private static SimpleDateFormat SIMPLE_DATE_FORMATTER = new SimpleDateFormat("yyyyMMdd");
	private final static Logger LOGGER = Logger.getLogger(DateUtils.class.getName());

	public static DateTime today() {
		return new DateTime(System.currentTimeMillis());
	}

	public static DateTime tomorrow() {
		return new DateTime(System.currentTimeMillis() + ONE_DAY__IN_MILLIS);
	}

	public static boolean isSameDay(DateTime day, DateTime otherDay) {
		if (areDatesNull(day, otherDay)) {
			return false;
		}
		return day.toStringRfc3339().substring(0, 10).equals(otherDay.toStringRfc3339().substring(0, 10));
	}

	private static boolean areDatesNull(DateTime day, DateTime otherDay) {
		return (day == null || otherDay == null);
	}

	private static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
			.withZone(java.time.ZoneId.of("CET"));

	public static String formatDate(DateTime date) {
        if (date != null )
		    return formatter.format(new Date(date.getValue()).toInstant());
        return "(no date)";
	}

	private static int compareDateTo(Date date1, Date date2) {
		return SIMPLE_DATE_FORMATTER.format(date1).compareTo(SIMPLE_DATE_FORMATTER.format(date2));
	}

	private static int compareDateTo(DateTime date1, DateTime date2) {
		return compareDateTo(new Date(date1.getValue()), new Date(date2.getValue()));
	}

	public static boolean isDueDateBefore(DateTime dueDate, DateTime date) {
		return areDatesNull(dueDate, date) ? false : (compareDateTo(dueDate, date) > 0);
	}
}

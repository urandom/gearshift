package us.supositi.gearshift;

import java.util.Calendar;
import java.util.Date;

public class TimeHelper {
  private static final int MILLIS_TO_MINUTES = 1000 * 60;
  private static final int MILLIS_TO_HOURS = MILLIS_TO_MINUTES * 60;

  private static final String SEPARATOR_ESTIMATED_TIME = ",";
  private static final int SPACE_PER_REMAINING_TIME = 10;

  /**
   * utility class - no instance
   */
  private TimeHelper() {
  }

  public static String toRemainingTime(Date now, String timeData,
      String formatOnlyMinutes, String formatMinutesAndHours) {
    final Calendar calendar = Calendar.getInstance();
    calendar.setTime(now);

    final String[] times = timeData.split(SEPARATOR_ESTIMATED_TIME);
    final StringBuilder result = new StringBuilder(times.length * SPACE_PER_REMAINING_TIME);
    for (String time : times) {
      try {
        result.append(toRemainingTime(now, time, formatOnlyMinutes, formatMinutesAndHours, calendar));
        result.append(SEPARATOR_ESTIMATED_TIME);
      } catch (Exception e) {
          TorrentListActivity.logE("could not convert " + time, e);
          result.append(time);
      }
    }
    if (result.length()>0) {
      //remove last comma
      result.deleteCharAt(result.length() - 1);
    }
    
    return result.toString();
  }

  private static String toRemainingTime(Date now, String time,
      String formatOnlyMinutes, String formatMinutesAndHours,
      Calendar calendar) {
    final String[] hoursMinutes = time.split(":");
    if (hoursMinutes.length != 2) {
      throw new IllegalArgumentException(
          "could not split hours-minutes: " + time);
    }
    calendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(hoursMinutes[0]));
    calendar.set(Calendar.MINUTE, Integer.parseInt(hoursMinutes[1]));

    final long arrivingTime = calendar.getTimeInMillis();

    final long remainingTimeInMillis = arrivingTime - now.getTime();
    if (remainingTimeInMillis < 0) {
      throw new IllegalArgumentException(String.format(
          "negative remaining time: time = %s, now = %s, diff = %s",
          time, now, remainingTimeInMillis));
    }
    return remainingTimeToHumanReadableForm(remainingTimeInMillis, formatOnlyMinutes, formatMinutesAndHours);
  }

  /**
   * @param remaining
   * @return something like ~1?.22?.
   */
  private static String remainingTimeToHumanReadableForm(long remaining,
      String formatOnlyMinutes, String formatMinutesAndHours) {
    int minutes = (int) ((remaining % MILLIS_TO_HOURS) / MILLIS_TO_MINUTES);
    int hours = (int) (remaining / MILLIS_TO_HOURS);
    
    if (hours > 0) {
      return String.format(formatMinutesAndHours, hours, minutes);
    } else {
      return String.format(formatOnlyMinutes, minutes);
    }
  }

  public static String removeTrailingSeparator(String timeData) {
    if (!timeData.endsWith(SEPARATOR_ESTIMATED_TIME)) {
      return timeData;
    }
    // sometimes estimated time has one more comma: 21:00,21:20,
    return timeData.substring(0, timeData.length() - 1);
  }

}
package org.sugr.gearshift.viewmodel.ext

import android.content.Context
import org.sugr.gearshift.R
import java.text.DecimalFormat

fun Long.readableFileSize() : String {
	if (this <= 0) return "0 B";

	val units = arrayOf("B", "KB", "MB", "GB", "TB")
	val digitGroups = (Math.log10(this.toDouble())/Math.log10(1024.0)).toInt();
	val scaledSize = this / Math.pow(1024.0, digitGroups.toDouble());
	val format = if (scaledSize < 100) "#,##0.##" else "#,##0.#"

	return DecimalFormat(format).format(scaledSize) + " " + units[digitGroups]
}

fun Long.readableRemainingTime(ctx : Context) : String {
	if (this < 0) {
		return ctx.getString(R.string.traffic_remaining_time_unknown);
	}

	val days = Math.floor(this / 86400.0).toInt();
	val hours = Math.floor((this % 86400.0) / 3600).toInt();
	val minutes = Math.floor((this % 3600.0) / 60).toInt();
	val seconds = Math.floor(this % 60.0).toInt();

	val d = Integer.toString(days) + ' ' + ctx.getString(if (days > 1) R.string.time_days else R.string.time_day);
	val h = Integer.toString(hours) + ' ' + ctx.getString(if (hours > 1) R.string.time_hours else R.string.time_hour);
	val m = Integer.toString(minutes) + ' ' + ctx.getString(if (minutes > 1) R.string.time_minutes else R.string.time_minute);
	val s = Integer.toString(seconds) + ' ' + ctx.getString(if (seconds > 1) R.string.time_seconds else R.string.time_second);

	if (days > 0) {
		if (days >= 4 || hours == 0) {
			return d;
		}
		return d + ", " + h;
	}

	if (hours > 0) {
		if (hours >= 4 || minutes == 0) {
			return h;
		}
		return h + ", " + m;
	}

	if (minutes > 0) {
		if (minutes >= 4 || seconds == 0) {
			return m;
		}
		return m + ", " + s;
	}

	return s;
}

fun Float.readablePercent() : String {
	val format =
			if (this < 10.0) "#.##"
			else if (this < 100.0) "#.#"
			else "#"

	return DecimalFormat(format).format(this)
}

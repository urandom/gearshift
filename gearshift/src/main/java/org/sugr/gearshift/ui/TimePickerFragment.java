package org.sugr.gearshift.ui;

import android.app.Dialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.text.format.DateFormat;
import android.widget.Button;
import android.widget.TimePicker;

import org.sugr.gearshift.R;

import java.util.Calendar;

public class TimePickerFragment extends DialogFragment
    implements TimePickerDialog.OnTimeSetListener {

    public static final String ARG_HOUR = "hour";
    public static final String ARG_MINUTE = "minute";
    public static final String ARG_BEGIN = "begin";

    @NonNull @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the current time as the default values for the picker
        final Calendar c = Calendar.getInstance();

        int hour, minute;

        if (getArguments().containsKey(ARG_HOUR)) {
            hour = getArguments().getInt(ARG_HOUR);
            c.set(Calendar.HOUR_OF_DAY, hour);
        } else {
            hour = c.get(Calendar.HOUR_OF_DAY);
        }

        if (getArguments().containsKey(ARG_MINUTE)) {
            minute = getArguments().getInt(ARG_MINUTE);
            c.set(Calendar.MINUTE, minute);
        } else {
            minute = c.get(Calendar.MINUTE);
        }

        // Create a new instance of TimePickerDialog and return it
        return new TimePickerDialog(getActivity(), this, hour, minute,
            DateFormat.is24HourFormat(getActivity()));
    }

    @Override
    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
        Button button;
        if (getArguments().containsKey(ARG_BEGIN) && getArguments().getBoolean(ARG_BEGIN)) {
            button = (Button) getActivity().findViewById(R.id.transmission_session_alt_limit_time_from);
            ((TransmissionSessionActivity) getActivity()).setAltSpeedLimitTimeBegin(hourOfDay * 60 + minute);
        } else {
            button = (Button) getActivity().findViewById(R.id.transmission_session_alt_limit_time_to);
            ((TransmissionSessionActivity) getActivity()).setAltSpeedLimitTimeEnd(hourOfDay * 60 + minute);
        }

        button.setText(String.format(
            getActivity().getString(R.string.session_settings_alt_limit_time_format),
            hourOfDay,
            minute));
    }
}

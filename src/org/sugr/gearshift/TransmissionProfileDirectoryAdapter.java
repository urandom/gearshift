package org.sugr.gearshift;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;


public class TransmissionProfileDirectoryAdapter extends ArrayAdapter<String> {

    public TransmissionProfileDirectoryAdapter(Context context, int textViewResourceId) {
        super(context, textViewResourceId);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = super.getView(position, convertView, parent);

        TextView textView = (TextView) view.findViewById(android.R.id.text1);
        String text = (String) textView.getText();

        int lastSlash = text.lastIndexOf('/');
        if (lastSlash > -1) {
            if (lastSlash == 0) {
                textView.setText(text.substring(lastSlash + 1) + " (/)");
            } else {
                textView.setText(text.substring(lastSlash + 1) + " (" + text.substring(0, lastSlash) + ')');
            }
        }

        return view;
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        View view = super.getDropDownView(position, convertView, parent);

        TextView textView = (TextView) view.findViewById(android.R.id.text1);
        String text = (String) textView.getText();

        int lastSlash = text.lastIndexOf('/');
        if (lastSlash > -1) {
            if (lastSlash == 0) {
                textView.setText(text.substring(lastSlash + 1) + " (/)");
            } else {
                textView.setText(text.substring(lastSlash + 1) + " (" + text.substring(0, lastSlash) + ')');
            }
        }

        return view;
    }

    public void sort() {
        sort(G.SIMPLE_STRING_COMPARATOR);
    }
}

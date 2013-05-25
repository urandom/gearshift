package org.sugr.gearshift;

import org.sugr.gearshift.TransmissionSessionManager.TransmissionExclusionStrategy;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class TransmissionSessionActivity extends FragmentActivity {
    public static final String ARG_PROFILE = "profile";
    public static final String ARG_JSON_SESSION = "json_session";

    private TransmissionProfile mProfile;
    private TransmissionSession mSession;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Intent in = getIntent();
        Gson gson = new GsonBuilder().setExclusionStrategies(new TransmissionExclusionStrategy()).create();

        mProfile = in.getParcelableExtra(ARG_PROFILE);
        mSession = gson.fromJson(in.getStringExtra(ARG_JSON_SESSION), TransmissionSession.class);

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_transmission_session);
    }
}

package com.androidchan.client;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class ThreadPreferences extends PreferenceActivity 
{
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
    	super.onCreate(savedInstanceState);
    	this.addPreferencesFromResource(R.xml.threadsortoptions);
    }
}

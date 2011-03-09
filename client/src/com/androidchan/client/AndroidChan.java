package com.androidchan.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class AndroidChan extends ListActivity implements DialogInterface.OnClickListener, 
							 OnSharedPreferenceChangeListener 
{
	
	String[] m_ids = null;
	private ProgressDialog m_wait_dialog = null;
	private boolean m_threads_loaded = false;
	private AtomicBoolean m_loading_threads = new AtomicBoolean(false);
	private boolean m_waiting_for_location_settings = false;
	private boolean m_preferences_changed = false;
	
	public static final int OPTIONS_MENU_SELECTION_REQUEST_CODE = 1;
	public static final int PREFERENCES_MENU_SELECTION_REQUEST_CODE = 2;
	
	public class UIState
	{
		public UIState(String[] i, ListAdapter li)
		{
			ids = i;
			list_adapter = li;
		}
		
		public String[] ids;
		public ListAdapter list_adapter;
	}
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        this.setContentView(R.layout.main);
        
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);
        
        final Intent intent = getIntent();
        final String action = intent.getAction();
          
        if (Intent.ACTION_MAIN.equals(action)) 
        {
            if (!LocationSelector.HasValidLocation(this.getApplicationContext()))
            {
                forceLocationSetup();
                return;
            }

       		Toast.makeText(getApplicationContext(), R.string.welcome_message, Toast.LENGTH_LONG).show();
        }
        
        final Object data = getLastNonConfigurationInstance();
        if (data == null)
        {
        	LoadThreads();
        }
        else
        {
        	UIState existing_state = (UIState)data;
        	this.m_threads_loaded = true;
        	this.m_ids = existing_state.ids;
        	this.setListAdapter(existing_state.list_adapter);
        }
    }

    private void LoadThreads()
    {
    	if (!m_loading_threads.compareAndSet(false, true))
    		return;
    	new GetThreadsTask().execute();
    }
    
    private void forceLocationSetup()
    {
    	AlertDialog.Builder builder = new AlertDialog.Builder(this);
    	builder.setTitle(this.getString(R.string.need_to_set_location_title));
    	builder.setMessage(this.getString(R.string.need_to_set_location_msg));
    	
    	builder.setPositiveButton(this.getString(R.string.take_me_to_settings), this);
    	
    	AlertDialog ad = builder.create();
    	ad.show();
    }
    
    //Dialog.OnClickListener
    public void onClick(DialogInterface v, int buttonId)
	{
    	ComponentName c = new ComponentName("com.android.settings","com.android.settings.SecuritySettings");
    	Intent i = new Intent(Settings.ACTION_SECURITY_SETTINGS);
    	i.addCategory(Intent.CATEGORY_LAUNCHER);
    	i.setComponent(c);
    	i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    	this.startActivity(i);
    	m_waiting_for_location_settings = true;
    	Toast.makeText(this.getApplicationContext(), this.getString(R.string.do_it_dude), Toast.LENGTH_LONG).show();
	}
    
    //OnSharedPreferenceChangeListener
    @Override
    public void onSharedPreferenceChanged(SharedPreferences pref, String key) 
    {
    	Log.d("AndroidChan", "Preferences changed for key " + key);
    	m_preferences_changed = true;
    }
    
    @Override
    public void onPause()
    {
    	super.onPause();
    	if (m_wait_dialog != null)
    		m_wait_dialog.dismiss();
    }
    
    @Override
    public void onResume()
    {
    	super.onResume();
    	if (m_waiting_for_location_settings)
    	{
    		if (!LocationSelector.HasValidLocation(this.getApplicationContext()))
            {
    			LocationSelector.SetFakeLocation(this.getApplicationContext());
    			Toast.makeText(this.getApplicationContext(), R.string.using_nyc_location, Toast.LENGTH_LONG).show();
            }
    		LoadThreads();
    		m_waiting_for_location_settings = false;
    	}
    }
    
    @Override
    public Object onRetainNonConfigurationInstance() 
    {
    	if (!m_threads_loaded)
    	{
    		return null;
    	}
    	else
    	{
    		return new UIState(m_ids, this.getListAdapter());
    	}
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
    	super.onCreateOptionsMenu(menu);
    	
    	int base = Menu.FIRST;
    	
    	MenuItem refresh = menu.add(base, base, base, R.string.refresh);
    	refresh.setIcon(android.R.drawable.ic_menu_rotate);
    	
    	MenuItem addpost = menu.add(base, base+1, base+1, R.string.new_thread);
    	addpost.setIcon(android.R.drawable.ic_menu_add);
    	
    	MenuItem preferences = menu.add(base, base+2, base+2, R.string.preferences);
    	preferences.setIcon(android.R.drawable.ic_menu_preferences);
    	
    	MenuItem about = menu.add(base, base+3, base+3, R.string.about);
    	about.setIcon(android.R.drawable.ic_menu_help);
    	
    	return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
    	if (item.getItemId() == Menu.FIRST)
    	{
    		LoadThreads();
    	}
    	else if (item.getItemId() == Menu.FIRST + 1)
    	{
            Intent intent = new Intent("com.androidchan.client.POST_EDITOR");
            startActivityForResult(intent, OPTIONS_MENU_SELECTION_REQUEST_CODE);
    	}
    	else if (item.getItemId() == Menu.FIRST + 2)
    	{
    		Intent intent = new Intent("com.androidchan.client.THREAD_PREFERENCES");
    		startActivityForResult(intent, PREFERENCES_MENU_SELECTION_REQUEST_CODE);
    	}
    	else if (item.getItemId() == Menu.FIRST + 3)
    	{
    		String url = "http://androidchan.com/m";
    		Intent intent = new Intent(Intent.ACTION_VIEW);
    		intent.setData(Uri.parse(url));
    		startActivity(intent);
    	}
    	return true;
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
    	super.onActivityResult(requestCode, resultCode, data);
    	if (requestCode == OPTIONS_MENU_SELECTION_REQUEST_CODE)
    	{
    		if (resultCode == RESULT_OK)
    		{
    			LoadThreads();
    		}
    	}
    	else if (requestCode == PREFERENCES_MENU_SELECTION_REQUEST_CODE)
    	{
    		if (m_preferences_changed)
    		{
    			LoadThreads();
    			m_preferences_changed = false;
    		}
    	}
    }
    
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {     
    	super.onListItemClick(l, v, position, id);

		Intent intent = new Intent("com.androidchan.client.POSTS");
		intent.putExtra("thread_id", m_ids[position]);
		startActivity(intent);
    }    
        
    private static String convertStreamToString(InputStream is) {
    	 
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
 
        String line = null;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return sb.toString();
    }

    private class GetThreadsTask extends AsyncTask<Void, Void, JSONArray> 
    {
    	
		@Override
		protected JSONArray doInBackground(Void... params) 
		{
			double latitude, longitude;
			Pair<Double, Double> location = LocationSelector.GetLastLocation(AndroidChan.this.getApplicationContext());
			latitude = location.first;
			longitude = location.second;

			try
			{
				String result = "";
				SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(AndroidChan.this);
				String sorting = prefs.getString(AndroidChan.this.getString(R.string.sort_preference_key), 
						                         AndroidChan.this.getString(R.string.sort_preference_default));
				String path = "/threads?latitude=" + latitude + "&longitude=" + longitude + "&sort=" + sorting;
				Log.d("AndroidChan", "Sending get request: " + path);
				InputStream response = ((HTTPApplication)AndroidChan.this.getApplication()).getInputStream(path);
				if (response == null)
					return null;
				result = convertStreamToString(response);
				return new JSONArray(result);
			}
			catch (JSONException e) 
			{
				Log.e("AndroidChan", Log.getStackTraceString(e));
			}                               	
			return null;
		}
		
		@Override 
		protected void onPreExecute()
		{
			m_threads_loaded = false;
	        m_wait_dialog = ProgressDialog.show(AndroidChan.this, 
	        		                            AndroidChan.this.getString(R.string.blank), 
	        		                            AndroidChan.this.getString(R.string.refreshing_closest_threads),
	        		                            true, 
	        		                            true);
		}
		
		@Override
		protected void onPostExecute(JSONArray threads)
		{
			if (threads == null)
			{
				m_wait_dialog.dismiss();
	        	m_loading_threads.set(false);
				Toast.makeText(AndroidChan.this, 
						       AndroidChan.this.getString(R.string.could_not_connect_for_threads), 
						       Toast.LENGTH_LONG).show();
				return;
			}
			
			ArrayList<Post> messages = new ArrayList<Post>();
        	m_ids = new String[threads.length()];
        	for (int i = 0; i < threads.length(); i++)
        	{
        		try
        		{
	        		JSONObject obj = threads.getJSONObject(i); 
	        		Post post = new Post(obj);
	        		messages.add(post);
	        		m_ids[i] = post.ThreadId;
        		}
        		catch (JSONException e) 
        		{
        			Log.e("AndroidChan", Log.getStackTraceString(e));
        		}
        	}
        	
        	PostAdapter current_adapter = (PostAdapter)AndroidChan.this.getListAdapter();
        	if (current_adapter == null)
        	{
            	current_adapter = new PostAdapter(AndroidChan.this, R.layout.post, messages, (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE));
            	AndroidChan.this.setListAdapter(current_adapter);
        	}
        	else
        	{
        		((PostAdapter)AndroidChan.this.getListAdapter()).UpdatePosts(messages);
        	}
        	
        	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(AndroidChan.this);
			String sorting = prefs.getString(AndroidChan.this.getString(R.string.sort_preference_key), 
					                         AndroidChan.this.getString(R.string.sort_preference_default));
			String[] all_options = AndroidChan.this.getResources().getStringArray(R.array.thread_sort_options);
			String[] all_values = AndroidChan.this.getResources().getStringArray(R.array.thread_sort_options_values);
			String display_value = all_options[Arrays.asList(all_values).indexOf(sorting)];
			AndroidChan.this.setTitle("AndroidChan: " + display_value);
        	
        	m_wait_dialog.dismiss();
        	m_threads_loaded = true;
        	m_loading_threads.set(false);
		}
    }

}
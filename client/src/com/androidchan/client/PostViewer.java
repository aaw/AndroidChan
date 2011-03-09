package com.androidchan.client;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.androidchan.client.Androidchan.UIState;

import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class PostViewer extends ListActivity 
{
	private static String LOG_TAG = "PostViewer";
	
	ArrayList<Post> m_posts;
	String m_thread_id;
	private ProgressDialog m_wait_dialog;
	private boolean m_threads_loaded = false;
	private AtomicBoolean m_loading_posts = new AtomicBoolean(false);
	
	public class UIState
	{
		public UIState(String ti, ListAdapter li, ArrayList<Post> p)
		{
			thread_id = ti;
			list_adapter = li;
			posts = p;
		}
		
		public String thread_id;
		public ListAdapter list_adapter;
		public ArrayList<Post> posts;
	}
	
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.setContentView(R.layout.post_view);
        
        ListView lv = getListView();
        lv.setBackgroundResource(R.color.transparent);
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> av, View v, int pos, long id) {
                onListItemClick(av, v,pos,id);
            }
        });
        this.registerForContextMenu(lv);

        Intent intent = getIntent();
        Uri uri = intent.getData();
        m_thread_id = intent.getExtras().getString("thread_id");
        //if (intent.getData() == null) {
        //    
        //}
        final Object data = getLastNonConfigurationInstance();
        if (data == null)
        {
        	LoadPosts(m_thread_id);
        }
        else
        {
        	UIState existing_state = (UIState)data;
        	this.m_threads_loaded = true;
        	this.m_thread_id = existing_state.thread_id;
        	this.m_posts = existing_state.posts;
        	this.setListAdapter(existing_state.list_adapter);
        }    
    }
    
    private void LoadPosts(String thread_id)
    {
    	if (!m_loading_posts.compareAndSet(false, true))
    		return;
    	new GetPostsTask().execute(thread_id);
    }
    
    @Override
    public void onSaveInstanceState(Bundle outBundle)
    {
    	Log.d(LOG_TAG, "Entering onSaveInstanceState");
    }
    
    @Override
    public void onPause()
    {
    	super.onPause();
    	if (m_wait_dialog != null)
    		m_wait_dialog.dismiss();
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
    		return new UIState(m_thread_id, this.getListAdapter(), m_posts);
    	}
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
    	super.onCreateOptionsMenu(menu);
    	
    	int base = Menu.FIRST;
    	
    	MenuItem refresh = menu.add(base, base, base, R.string.refresh);
    	refresh.setIcon(android.R.drawable.ic_menu_rotate);
    	
    	//MenuItem addpost = menu.add(base, base+1, base+1, "Reply to post");
    	//addpost.setIcon(android.R.drawable.ic_menu_add);
    	
    	return true;
    }
        
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
    	if (item.getItemId() == 1)
    	{
    		LoadPosts(m_thread_id);
    	}
    	//else if (item.getItemId() == 2)
    	//{
    	//	NewThread("here's a new thread!");
    	//	RefreshThreads("from menu");
    	//}
    	return true;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) 
    {
    	AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)menuInfo;
    	String image_id = null;
    	String caption = "";
   		caption = m_posts.get(info.position).Body;
   		image_id = m_posts.get(info.position).ImageId;
		if (caption.length() > 20)
			caption = caption.substring(0, 20) + "...";
		menu.setHeaderTitle(caption);
		if (image_id != null)
		{
			menu.add(Menu.NONE, 200, 200, "View image");
		}
		menu.add(Menu.NONE, 201, 201, "Post reply");
    	menu.add(Menu.NONE, 202, 202, "Toggle thread highlight");
    }
    
    @Override
    public boolean onContextItemSelected(MenuItem item)
    {
    	AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
    	if (item.getItemId() == 200)
    	{
    		Intent intent = new Intent("com.androidchan.client.IMAGE_VIEWER");
    		intent.putExtra("image_id", m_posts.get(info.position).ImageId);
            this.startActivity(intent);
    	}
    	else if (item.getItemId() == 201)
    	{
    		Intent intent = new Intent("com.androidchan.client.POST_EDITOR");
            intent.putExtra("in_reply_to", m_posts.get(info.position).Id);
            intent.putExtra("reply_body", m_posts.get(info.position).Body);
            startActivityForResult(intent, 1);
    	}
    	else if (item.getItemId() == 202)
    	{
    		PostAdapter post_adapter = (PostAdapter)this.getListAdapter();
    		String next_id_to_highlight = post_adapter.posts.get(info.position).Id;
    		boolean highlight = !post_adapter.posts.get(info.position).ThreadHighlighted;
    		for(int i = post_adapter.posts.size() - 1; i >= 0; i--)
    		{
    			String current_id = post_adapter.posts.get(i).Id;
    			if (current_id.equals(next_id_to_highlight))
    			{
    				post_adapter.posts.get(i).ThreadHighlighted = highlight;
    				next_id_to_highlight = post_adapter.posts.get(i).Parent;
    			}
    			else
    			{
    				post_adapter.posts.get(i).ThreadHighlighted = false;
    			}
    		}

    		post_adapter.notifyDataSetChanged();
    	}
    	return true;
    }
    
    //@Override
    //protected void onListItemClick(ListView l, View v, int position, long id) {       
    protected void onListItemClick(AdapterView<?> av, View v, int position, long id) 
    {
    	Intent intent = new Intent("com.androidchan.client.POST_EDITOR");
        intent.putExtra("in_reply_to", m_posts.get(position).Id);
        intent.putExtra("reply_body", m_posts.get(position).Body);
        startActivityForResult(intent, 1);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
    	super.onActivityResult(requestCode, resultCode, data);
    	//assume requestCode=1, which is hardcoded in onListItemClick for now
    	if (resultCode == RESULT_OK)
    	{
    		LoadPosts(m_thread_id);
    	}    	
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
    
    private class GetPostsTask extends AsyncTask<String, Void, JSONArray> 
    {
    	
		@Override
		protected JSONArray doInBackground(String... params) 
		{
			try
			{
				Log.d(LOG_TAG, "Starting doInBackground for GetPostsTask");
				String result = "";
				String path = "/posts/" + params[0];
				Log.d(LOG_TAG, "Sending get request: " + path);
				InputStream response = ((HTTPApplication)PostViewer.this.getApplication()).getInputStream(path);
				if (response == null)
					return null;
				result = convertStreamToString(response);
				return new JSONArray(result);
			} 
			catch (JSONException e) 
			{
				Log.e(LOG_TAG, Log.getStackTraceString(e));
			}                               	
			return null;
		}
		
		@Override 
		protected void onPreExecute()
		{
			Log.d("PostViewer", "Entering GetPostsTask::onPreExecute");
			m_threads_loaded = false;
	        m_wait_dialog = ProgressDialog.show(PostViewer.this, 
	        		                            PostViewer.this.getString(R.string.blank), 
	        		                            PostViewer.this.getString(R.string.loading_posts),
	        		                            true,
	        		                            true);
		}
		
		@Override
		protected void onPostExecute(JSONArray threads)
		{
			Log.d("PostViewer", "Entering GetPostsTask::onPostExecute");
			if (threads == null)
			{
				m_wait_dialog.dismiss();
				m_loading_posts.set(false);
				Toast.makeText(PostViewer.this, 
						       PostViewer.this.getString(R.string.could_not_connect_for_posts), 
						       Toast.LENGTH_LONG).show();
				return;
			}

			try
			{
	        	ArrayList<Post> posts = new ArrayList<Post>();
	        	for (int i = 0; i < threads.length(); i++)
	        	{
	        		JSONObject obj;
					try {
						obj = threads.getJSONObject(i);
						posts.add(new Post(obj));
					} catch (JSONException e) {
						Log.e("PostViewer", Log.getStackTraceString(e));
					}
	        	}
	        	m_posts = posts;
	        	Log.d("PostViewer", "Resetting PostAdapter");
	        	PostAdapter current_adapter = (PostAdapter)PostViewer.this.getListAdapter();
	        	if (current_adapter == null)
	        	{
	            	current_adapter = new PostAdapter(PostViewer.this, R.layout.post, posts, (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE));
	            	PostViewer.this.setListAdapter(current_adapter);
	        	}
	        	else
	        	{
	        		((PostAdapter)PostViewer.this.getListAdapter()).UpdatePosts(posts);
	        	}
	        	m_threads_loaded = true;
			}
			catch (Exception e)
			{
				Log.e("PostViewer", Log.getStackTraceString(e));
			}
			finally
			{
				m_wait_dialog.dismiss();
				m_loading_posts.set(false);
			}
		}
    }
}

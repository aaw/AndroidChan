package com.androidchan.client;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import com.github.droidfu.imageloader.ImageLoader;
import com.github.droidfu.imageloader.ImageLoaderHandler;
import com.github.droidfu.widgets.WebImageView;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.util.Linkify;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;


public class PostAdapter extends BaseAdapter {

	public ArrayList<Post> posts;
	private LayoutInflater m_inflater;
	private int m_layout_id;
	private String m_service_address;
	private Context m_context;
	
	public PostAdapter(Context context, int viewResourceId, ArrayList<Post> objects, LayoutInflater inflater) {
		super();
		ImageLoader.initialize(context);
		
		m_context = context;
		m_layout_id = viewResourceId;
		m_inflater = inflater;
		m_service_address = ((HTTPApplication)((Activity)context).getApplication()).getServiceAddress();
		posts = objects;
	}
	
	@Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewGroup vg = (ViewGroup)convertView;
        if (vg == null) {
            vg = (ViewGroup)m_inflater.inflate(m_layout_id, null);
        }
        Post p = posts.get(position);
       
        WebImageView iv = (WebImageView)vg.getChildAt(0);
        iv.setOnClickListener(new ImageViewClickListener(m_context, p.ImageId));
        if (p.ImageId != null)
        {
            iv.setImageUrl(m_service_address + "/img/thumbnail/" + p.ImageId);
            iv.loadImage();
            iv.setVisibility(View.VISIBLE);
        }
        else
        {
            iv.setNoImageDrawable(R.drawable.no_image);
            iv.setVisibility(View.GONE);
        }

        LinearLayout text_fields = (LinearLayout)vg.getChildAt(1);
             
        TextView number = (TextView)text_fields.getChildAt(0);
        number.setText("Post #" + p.Id);
             
        TextView dateView = (TextView)text_fields.getChildAt(1);
        Date date = new Date(p.Timestamp);
        DateFormat timeFormat = DateFormat.getTimeInstance(DateFormat.LONG);
        DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.SHORT);
        dateView.setText(dateFormat.format(date) + " " + timeFormat.format(date));
             
        TextView distanceView = (TextView)text_fields.getChildAt(2);
        String distance = LocationSelector.DistanceFromMe(m_context, p.Latitude, p.Longitude);
        distanceView.setText("Distance from you: " + distance);
        
        TextView tv = (TextView)text_fields.getChildAt(3);//(TextView)v;
    	tv.setText(p.Body);
    	//hackity hack! this if branch keeps recycled autolinked listview frames from
    	//fucking up later frames and not allowing us to click through things that shouldn't
    	//have autolink click capturing
    	if (!Linkify.addLinks(tv, Linkify.ALL))
    	{
    		tv.setMovementMethod(null);
    	}
        if (p.ThreadHighlighted)
            tv.setBackgroundResource(R.color.red);
        else
            tv.setBackgroundResource(R.color.transparent);
        return vg;
    }
	
	private class ImageViewClickListener implements OnClickListener
	{
		private String m_image_id;
		private Context m_context;
		
		public ImageViewClickListener(Context context, String image_id)
		{
			m_context = context;
			m_image_id = image_id;
		}
		
		@Override
		public void onClick(View v) {
			Intent intent = new Intent("com.androidchan.client.IMAGE_VIEWER");
    		intent.putExtra("image_id", m_image_id);
            m_context.startActivity(intent);			
		}
	}
	
	
	public void UpdatePosts(ArrayList<Post> latest)
	{
	     posts = latest;
	     this.notifyDataSetChanged();
	}
	 
    @Override
	public boolean hasStableIds()
	{
		return false;
	}
	 
	@Override
	public int getCount() 
	{
		return posts.size();
	}

	@Override
	public Object getItem(int position) 
	{
		return posts.get(position);
	}

	@Override
	public long getItemId(int position) 
	{
		//TODO: should we be doing this and what we're doing in hasStableIds?
		return position;
	}

}

package com.androidchan.client;

import java.util.Date;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

public class Post
{
	
	public Post(JSONObject obj)
	{
		try 
		{
			Body = obj.getString("body");
			Id = getObjectId(obj, "_id");
			Parent = getObjectId(obj, "parent");
			ImageId = getObjectId(obj, "image_id");
			ThreadId = getObjectId(obj, "thread_id");
			Timestamp = obj.getLong("timestamp");
			JSONArray location = obj.getJSONArray("location");
			Latitude = (Double)location.get(0);
			Longitude = (Double)location.get(1);
		} 
		catch (JSONException e) 
		{
			Log.e("Post", Log.getStackTraceString(e));
		}
		ThreadHighlighted = false;
	}
	
	private String getObjectId(JSONObject obj, String name)
	{
		JSONObject oid = obj.optJSONObject(name);
		if (oid == null)
			return null;
		try {
			return oid.getString("$oid");
		} catch (JSONException e) {
			return null;
		}
	}
	
	public String Body;
	public String Id;
	public String ImageId;
	public String ThreadId;
	public String Parent;
	public long Timestamp;
	public boolean ThreadHighlighted;
	public double Latitude;
	public double Longitude;
}

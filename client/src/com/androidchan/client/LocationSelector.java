package com.androidchan.client;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.provider.Settings;
import android.util.Log;
import android.util.Pair;

public class LocationSelector 
{
	public static final String SAVED_LOCATION_FILE = "saved_location";
	
	public static Pair<Double,Double> GetLastLocation(Context context)
	{
		LocationManager loc_manager = (LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
		Location location = loc_manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
		
		double latitude = 0.0;
		double longitude = 0.0;
		if (location != null)
		{
			latitude = location.getLatitude();
			longitude = location.getLongitude();
			WriteSavedLocation(context, latitude, longitude);
		}
		else if (context.getFileStreamPath(SAVED_LOCATION_FILE).exists())
		{
			return ReadSavedLocation(context);
		}
		else
		{
			Log.w("LocationSelector", "Couldn't determine location. Using (0.0, 0.0)");
		}
		
		return Pair.create(latitude, longitude);
	}
	
	public static boolean HasValidLocation(Context context)
	{
		LocationManager loc_manager = (LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
		Location location = loc_manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
		return location != null || context.getFileStreamPath(SAVED_LOCATION_FILE).exists();
	}
	
	public static void SetFakeLocation(Context context)
	{
		WriteSavedLocation(context, 40.776686, -73.975807);
	}
	
	public static String DistanceFromMe(Context context, double latitude, double longitude)
	{
		if (latitude == 0.0 && longitude == 0.0)
			return "???";
		Pair<Double,Double> myLocation = ReadSavedLocation(context);
		if (myLocation.first == 0.0 && myLocation.second == 0.0)
			return "???";
		double dist = distance(myLocation.first, myLocation.second, latitude, longitude);
		return new Long(Math.round(dist)).toString() + " miles";
	}
	
	private static double distance(double lat1, double lon1, double lat2, double lon2) 
	{
	    double theta = lon1 - lon2;
	    double dist = Math.sin(deg2rad(lat1)) * Math.sin(deg2rad(lat2)) + Math.cos(deg2rad(lat1)) * Math.cos(deg2rad(lat2)) * Math.cos(deg2rad(theta));
	    dist = Math.acos(dist);
	    dist = rad2deg(dist);
	    dist = dist * 60 * 1.1515;
	    return dist;
	}

    private static double deg2rad(double deg) 
    {
    	return (deg * Math.PI / 180.0);
    }

	private static double rad2deg(double rad) 
	{
	    return (rad * 180.0 / Math.PI);
	}
	
	private static void WriteSavedLocation(Context context, double latitude, double longitude)
	{
		FileOutputStream out = null;
		try 
		{
			out = context.openFileOutput(SAVED_LOCATION_FILE, Context.MODE_PRIVATE);
			String data = String.format("%s;%s", latitude, longitude);
			out.write(data.getBytes());
			out.flush();
			Log.i("LocationSelector", "Wrote location (" + latitude + ", " + longitude + ") to saved location file");
		} 
		catch (FileNotFoundException e) 
		{
			Log.e("LocationSelector", Log.getStackTraceString(e));
		} 
		catch (IOException e) 
		{
			Log.e("LocationSelector", Log.getStackTraceString(e));
		}
		finally
		{
			if (out != null)
				try {
					out.close();
				} catch (IOException e) {
					Log.e("LocationSelector", Log.getStackTraceString(e));
				}
		}
	}
	
	private static Pair<Double,Double> ReadSavedLocation(Context context)
	{
		try 
		{
			InputStream inStream = context.openFileInput(SAVED_LOCATION_FILE);
			InputStreamReader in = new InputStreamReader(inStream);
			BufferedReader buffreader = new BufferedReader(in);
			String line = buffreader.readLine();
			String[] position = line.split(";");
			double latitude = Double.parseDouble(position[0]);
			double longitude = Double.parseDouble(position[1]);
			in.close();
			Log.i("LocationSelector", "Read (" + latitude + ", " + longitude + ") as location from saved location file");
			return Pair.create(latitude, longitude);
		} 
		catch (FileNotFoundException e) 
		{	
			Log.e("LocationSelector", Log.getStackTraceString(e));
		} catch (IOException e) {
			Log.e("LocationSelector", Log.getStackTraceString(e));
		}
		
		Log.i("LocationSelector", "Returning location (0.0,0.0)");
		Log.i("LocationSelector", "Since it's obviously defective, trying to remove saved location file");
		context.getFileStreamPath(SAVED_LOCATION_FILE).delete();
		
		return Pair.create(0.0, 0.0);
	}
}

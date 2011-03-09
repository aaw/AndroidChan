package com.androidchan.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;

import android.app.Application;
import android.util.Log;

public class HTTPApplication extends Application
{
    private static final String TAG = "HTTPApplication";
    private HttpClient httpClient = null;
    //!!! Set SERVICE_ADDRESS to your server. For example, if you're serving from www.androidchan.com,
    //!!! you would set SERVICE_ADDRESS to "http://www.androidchan.com/1
    private static final String SERVICE_ADDRESS = "!!!YOU NEED TO SET THIS";
    
    @Override
    public void onLowMemory()
    {
        super.onLowMemory();
        Log.w(TAG, "onLowMemory, shutting down HTTP client");
        shutdownHttpClient();
    }

    @Override
    public void onTerminate()
    {
        super.onTerminate();
        Log.w(TAG, "onTerminate, shutting down HTTP client");
        shutdownHttpClient();
    }


    private HttpClient createHttpClient()
    {
        Log.d(TAG,"createHttpClient()");
        HttpParams params = new BasicHttpParams();
        
        HttpConnectionParams.setConnectionTimeout(params, 15000);
        HttpConnectionParams.setSoTimeout(params, 15000);
        
        HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
        HttpProtocolParams.setContentCharset(params, HTTP.DEFAULT_CONTENT_CHARSET);
        HttpProtocolParams.setUseExpectContinue(params, true);

        SchemeRegistry schReg = new SchemeRegistry();
        schReg.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        schReg.register(new Scheme("https", SSLSocketFactory.getSocketFactory(), 443));
        ClientConnectionManager conMgr = new ThreadSafeClientConnManager(params,schReg);

        return new DefaultHttpClient(conMgr, params);
    }

    public HttpClient getHttpClient() {
    	if (httpClient == null)
    		httpClient = createHttpClient();
        return httpClient;
    }

    public String getServiceAddress() {
    	return SERVICE_ADDRESS;
    }
    
    private void shutdownHttpClient()
    {
        if(httpClient!=null && httpClient.getConnectionManager()!=null)
        {
            httpClient.getConnectionManager().shutdown();
        }
        httpClient = null;
    }
    
    public InputStream getInputStream(String path)
    {
    	int timeouts[] = { 4, 8, 16, 32 };
    	for (int timeout : timeouts)
    	{
    		URL url;
			try {
				url = new URL(getServiceAddress() + path);
			} catch (MalformedURLException e) {
				Log.e("HTTPApplication", Log.getStackTraceString(e));
				return null;
			}
			Log.d("HTTPApplication", "Connecting to " + url.toString() + " with timeout = " + timeout);
    		URLConnection conn;
    		try {
				conn = url.openConnection();
			} catch (IOException e) {
				Log.i("HTTPApplication", "Problem opening connection: " + e.getMessage() + " with timeout = " + timeout);
				continue;
			}
    		conn.setConnectTimeout(timeout * 1000);
    		conn.setReadTimeout(timeout * 1000);
    		try {
    			if (conn.getInputStream() == null)
    				continue;
				return (InputStream)conn.getContent();
			} catch (IOException e) {
				Log.i("HTTPApplication", "Problem getting content: " + e.getMessage() + " with timeout = " + timeout);
				continue;
			}
    	}
    	return null;
    }
}

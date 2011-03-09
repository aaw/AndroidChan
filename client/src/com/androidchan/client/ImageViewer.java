package com.androidchan.client;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.atomic.AtomicBoolean;


import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.Toast;

public class ImageViewer extends Activity {
	
	private BitmapDrawable m_bitmap_drawable;
	private ProgressDialog m_wait_dialog;
	private AtomicBoolean m_image_loaded;
	private boolean m_keep_bitmap;
	private String m_image_id;
	
	private static final String BUCKET_NAME = "AndroidChan";
	private static final File BUCKET_DIR = new File(Environment.getExternalStorageDirectory(), BUCKET_NAME);
	
	@Override
    public void onCreate(Bundle savedInstanceState) 
	{
        super.onCreate(savedInstanceState);
        Log.d("ImageViewer", "Entering onCreate");
        
        BUCKET_DIR.mkdirs();
        
        this.setContentView(R.layout.image_view);
        
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        m_image_id = extras.getString("image_id");
        
        m_image_loaded = new AtomicBoolean(false);
        final Object data = getLastNonConfigurationInstance();
        if (data == null)
        {
        	new GetImageTask().execute(m_image_id);
        }
        else
        {
        	m_bitmap_drawable = (BitmapDrawable)data;
        	ImageView image_view = (ImageView)ImageViewer.this.findViewById(R.id.large_image);
			image_view.setImageDrawable(m_bitmap_drawable);
        	m_image_loaded.set(true);
        }
	}
	
	@Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
    	super.onCreateOptionsMenu(menu);
    	
    	int base = Menu.FIRST;
    	
    	MenuItem refresh = menu.add(base, base, base, R.string.save_image);
    	refresh.setIcon(android.R.drawable.ic_menu_save);
    	    	
    	return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
    	if (item.getItemId() == 1)
    	{
    		if (!m_image_loaded.get())
    		{
    			Toast.makeText(this.getApplicationContext(), R.string.image_could_not_be_saved, Toast.LENGTH_SHORT).show();
    			return true;
    		}
    		else if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                Log.w("ImageViewer", "SD card is not mounted");
                Toast.makeText(this.getApplicationContext(), R.string.sd_card_not_mounted, Toast.LENGTH_SHORT).show();
                return true;
            }
    		
    		Bitmap image_bitmap = m_bitmap_drawable.getBitmap();
    		File image_file = new File(BUCKET_DIR, m_image_id);
    		FileOutputStream os;
    		String failure_reason = null;
			try {
				os = new FileOutputStream(image_file);
				boolean result = image_bitmap.compress(Bitmap.CompressFormat.JPEG, 100, os);
				Log.d("ImageViewer", "Result of call to compress is " + result);
				os.flush();
				os.close();
			} catch (FileNotFoundException e) {
				Log.e("ImageViewer", Log.getStackTraceString(e));
				failure_reason = e.getMessage();
			} catch (IOException e) {
				Log.e("ImageViewer", Log.getStackTraceString(e));
				failure_reason = e.getMessage();
			}
			
			String file_path = "";
			try {
				file_path = image_file.getCanonicalPath();
			} catch (IOException e) {
				Log.e("ImageViewer", Log.getStackTraceString(e));
				failure_reason = e.getMessage();
			}
    		
			if (failure_reason != null)
			{
				Toast.makeText(this.getApplicationContext(), failure_reason, Toast.LENGTH_SHORT).show();
				return true;
			}

    		ContentValues image = new ContentValues();
            image.put(MediaStore.Images.ImageColumns.BUCKET_ID, BUCKET_NAME);
            image.put(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME, BUCKET_NAME);
            image.put(MediaStore.Images.ImageColumns.MIME_TYPE, "image/jpeg");
            image.put(MediaStore.Images.ImageColumns.DATA, file_path);

            this.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, image);
    		
    		Toast.makeText(this.getApplicationContext(), R.string.image_saved, Toast.LENGTH_SHORT).show();
    	}
    	return true;
    }
	
    @Override
    public void onPause()
    {
    	super.onPause();
    	Log.d("ImageViewer", "Entering onPause");
    	if (!m_image_loaded.get())
    		m_wait_dialog.dismiss();
    	//onPause is called before onRetainNonConfigurationInstance
    	m_keep_bitmap = false;
    }
    
    @Override
    public Object onRetainNonConfigurationInstance() 
    {
    	Log.d("ImageViewer", "Entering onRetainNonConfigurationInstance");
    	if (!m_image_loaded.get())
    	{
    		return null;
    	}
    	else
    	{
    		m_keep_bitmap = true;
    		return m_bitmap_drawable;
    	}
    }
	
	@Override
    protected void onDestroy()
    {
    	super.onDestroy();
    	Log.d("ImageViewer", "Entering onDestroy");
    	if (!m_keep_bitmap && m_image_loaded.get())
    	{
    		Log.d("ImageViewer", "Looks like we don't need bitmap anymore, recycling");
    		m_bitmap_drawable.getBitmap().recycle();
    		m_bitmap_drawable = null;
    	}
    	System.gc();
    	System.runFinalization();
    	System.gc();
    }
	
	private class GetImageTask extends AsyncTask<String, Void, BitmapDrawable> implements DialogInterface.OnCancelListener 
    {
		boolean m_cancelled;
    	
		@Override
		protected BitmapDrawable doInBackground(String... params) {
			BitmapDrawable result = null;
			try {
				URL url = new URL(((HTTPApplication)ImageViewer.this.getApplication()).getServiceAddress() + "/img/normal/" + params[0]);
				URLConnection conn = url.openConnection();
				conn.setUseCaches(true);
				InputStream response = (InputStream)conn.getContent();
				result = new BitmapDrawable(getResources(), response);
			} catch (MalformedURLException e) {
				Log.e("ImageViewer", Log.getStackTraceString(e));
			} catch (IOException e) {
				Log.e("ImageViewer", Log.getStackTraceString(e));
			}
			return result;
		}
		
		@Override 
		protected void onPreExecute()
		{
			m_cancelled = false;
			m_image_loaded.set(false);
	        m_wait_dialog = ProgressDialog.show(ImageViewer.this, 
	        		                            ImageViewer.this.getString(R.string.blank), 
	        		                            ImageViewer.this.getString(R.string.loading_image),
	        		                            true,
	        		                            true,
	        		                            this);
		}
		
		@Override
		protected void onPostExecute(BitmapDrawable result)
		{
			if (m_cancelled)
			{
				if (result != null)
					result.getBitmap().recycle();
				finish();
			}
			else if (result == null)
			{
				m_wait_dialog.dismiss();
				Toast.makeText(ImageViewer.this, 
						       ImageViewer.this.getString(R.string.could_not_download_image), 
						       Toast.LENGTH_LONG).show();
			}
			else
			{
				m_bitmap_drawable = result;
				ImageView image_view = (ImageView)ImageViewer.this.findViewById(R.id.large_image);
				image_view.setImageDrawable(m_bitmap_drawable);
				m_wait_dialog.dismiss();
				m_image_loaded.set(true);
			}
		}

		@Override
		public void onCancel(DialogInterface dialog) 
		{
			Toast.makeText(ImageViewer.this.getApplicationContext(), R.string.image_download_cancelled, Toast.LENGTH_SHORT).show();
			m_cancelled = true;
		}
    }

}

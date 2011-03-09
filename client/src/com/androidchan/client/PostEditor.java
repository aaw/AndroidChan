package com.androidchan.client;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
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
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.RelativeLayout.LayoutParams;

public class PostEditor extends Activity 
                        implements RadioGroup.OnCheckedChangeListener, OnClickListener
{
	String m_in_reply_to;
	String m_reply_body;
	Uri m_image_uri = null;
	String m_image_filename = null;
	boolean m_ignore_next_radio_button = false;
	
	private static int NONE = 0;
	private static int GALLERY = 1;
	private static int CAMERA = 2;
	int m_chosen_image_type = NONE;
	
	private static String ORIGINAL_CAMERA_IMAGE_FILE = "camera-original.jpg";
	private static String ROTATED_CAMERA_IMAGE_FILE = "camera-resampled-rotated.png";
	private static String RESAMPLED_CAMERA_IMAGE_FILE = "camera-resampled.jpg";
	private static String TAG = "PostEditor";
	
	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Log.d(TAG, "Executing onCreate");
        
        this.setContentView(R.layout.post_editor);
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras == null)
        {
        	Log.d(TAG, "This post is a new thread");
        	m_in_reply_to = null;
        	m_reply_body = null;
        }
        else
        {
        	m_in_reply_to = extras.getString("in_reply_to");
        	Log.d(TAG, "This post will be in reply to post " + m_in_reply_to);
        	m_reply_body = extras.getString("reply_body");
        }
        
        PopulateInReplyTo();
        
        Button showButton = (Button)findViewById(R.id.post_button);
        showButton.setOnClickListener(this);
        
        RadioGroup image_select_group = (RadioGroup)findViewById(R.id.post_editor_radio_group);
        image_select_group.setOnCheckedChangeListener(this);    
        
        if (savedInstanceState == null)
        {
        	RadioButton no_image = (RadioButton)findViewById(R.id.radio_button_no_image);
        	no_image.setChecked(true);
        }
    }
    
    @Override
    protected void onDestroy()
    {
    	super.onDestroy();
    	ImageView image_selected = (ImageView)findViewById(R.id.post_editor_selected_image);
    	image_selected.setImageBitmap(null);
    	System.gc();
    	System.runFinalization();
    	System.gc();
    }
    
    @Override
    protected void onSaveInstanceState( Bundle outState ) 
    {
    	Log.d("PostEditor", "Entering onSaveInstanceState. Saving instance state");
    	
    	outState.putString("image_uri", m_image_uri == null ? null : m_image_uri.toString());
    	outState.putString("image_filename", m_image_filename);
    	outState.putString("post_body", ((EditText)findViewById(R.id.post_body)).getText().toString());
    	outState.putInt("radio_button_selected", ((RadioGroup)findViewById(R.id.post_editor_radio_group)).getCheckedRadioButtonId());
    	outState.putInt("image_type_selected", m_chosen_image_type);
    	outState.putString("in_reply_to", m_in_reply_to);
    	outState.putString("reply_body", m_reply_body);
    }
    
    @Override
    protected void onRestoreInstanceState( Bundle savedInstanceState)
    {
    	Log.d("PostEditor", "Entering onRestoreInstanceState. Restoring instance state");
    	RadioButton selected_button = (RadioButton)findViewById(savedInstanceState.getInt("radio_button_selected"));
    	m_ignore_next_radio_button = true;
    	selected_button.setChecked(true);
    	((EditText)findViewById(R.id.post_body)).setText(savedInstanceState.getString("post_body"));
    	String uri_string = savedInstanceState.getString("image_uri");
    	m_image_uri = uri_string == null ? null : Uri.parse(uri_string);
    	m_image_filename = savedInstanceState.getString("image_filename");
    	m_chosen_image_type = savedInstanceState.getInt("image_type_selected");
    	Log.d("PostEditor", "image_uri restored to " + (m_image_uri == null ? "null" : m_image_uri));
    	
    	m_in_reply_to = savedInstanceState.getString("in_reply_to");
    	m_reply_body = savedInstanceState.getString("reply_body");
        PopulateInReplyTo();
    	DisplayImage();
    }
        
    private void PopulateInReplyTo()
    {
    	TextView in_reply_to = (TextView)findViewById(R.id.in_reply_to);
    	if (m_in_reply_to == null)
    		in_reply_to.setText(getString(R.string.new_thread));
    	else
    		in_reply_to.setText(getString(R.string.in_reply_to) + " " + m_reply_body);
    }
    
    @Override
    public void onCheckedChanged(RadioGroup arg0, int checkedId) 
    {
    	if (m_ignore_next_radio_button)
    	{
    		m_ignore_next_radio_button = false;
    		return;
    	}
    	
    	if (checkedId == R.id.radio_button_no_image)
    	{
    		Log.d("PostEditor", "No image radio button selected");
    		m_chosen_image_type = NONE;
    		m_image_uri = null;
    		ImageView image_selected = (ImageView)findViewById(R.id.post_editor_selected_image);
            image_selected.setImageResource(R.drawable.no_image);
    	}
    	else if (checkedId == R.id.radio_button_from_camera)
    	{
    		Log.d("PostEditor", "Image from camera radio button selected");
    		m_chosen_image_type = CAMERA;
    		try 
    		{
				this.openFileOutput(ORIGINAL_CAMERA_IMAGE_FILE, MODE_WORLD_WRITEABLE).close();
			} 
    		catch (Exception e) 
    		{
				Log.e("PostEditor", Log.getStackTraceString(e));
			}
    		File f = getFileStreamPath(ORIGINAL_CAMERA_IMAGE_FILE);
    		Intent takePictureFromCameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
    		takePictureFromCameraIntent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, Uri.fromFile(f));
    		startActivityForResult(takePictureFromCameraIntent, 3); //3 is hardcoded, used in onActivityResult
    	}
    	else if (checkedId == R.id.radio_button_from_phone)
    	{
    		Log.d("PostEditor", "Image from phone radio button selected");
    		m_chosen_image_type = GALLERY;
    		Intent intent = new Intent();  
    		intent.setType("image/*");  
    		intent.setAction(Intent.ACTION_GET_CONTENT);  
    		startActivityForResult(Intent.createChooser(intent, "Select Picture"),2);  //2 is hardcoded, used in onActivityResult
    	}
    }
        

    public String getFileFromURI(Uri contentUri) {
        String[] proj = { MediaStore.Images.Media.DATA };
        Cursor cursor = managedQuery(contentUri, proj, null, null, null);
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        return cursor.getString(column_index);
    }
    
    @Override  
    public void onActivityResult(int requestCode, int resultCode, Intent data) 
    {     
    	Log.d("PostEditor", "Got an activity result: " + resultCode + " from request #" + requestCode);
        if (resultCode == RESULT_OK) 
        {  
        	String fileName = ORIGINAL_CAMERA_IMAGE_FILE;
        	if (requestCode == 2)
        		fileName = data.getData().toString();
        	Log.d("PostEditor", "Path to file returned is " + fileName);
            new ProcessImageTask().execute(String.valueOf(requestCode), fileName);
        }  
        else
        {
        	Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT);
        	((RadioButton)findViewById(R.id.radio_button_no_image)).setChecked(true);
        }
    }  
    
    private class ProcessImageTask extends AsyncTask<String, Void, String>
    {
    	private ProgressDialog m_wait_dialog;
    	
		@Override 
		protected void onPreExecute()
		{
	        m_wait_dialog = ProgressDialog.show(PostEditor.this, 
	        		                            PostEditor.this.getString(R.string.blank), 
	        		                            PostEditor.this.getString(R.string.processing_image),
	        		                            true);
		}
		
		@Override
		protected String doInBackground(String... params) 
		{
			int requestCode = Integer.parseInt(params[0]);
			String resultFileName = params[1];
			File f = null;
            if (requestCode == 3) //3 is hardcoded, = from camera
            {
	        	f = PostEditor.this.getFileStreamPath(resultFileName);
            }
	        else // request_code == 2, hardcoded, from gallery
	        {
	        	String file_prefix = "file://";
	        	if (resultFileName.startsWith(file_prefix))
	        		f = new File(resultFileName.substring(file_prefix.length()));
	        	else
	        		f = new File(getFileFromURI(Uri.parse(resultFileName)));
        	}

          
        	long original_size = f.length() / 1024;
        	Log.d("PostEditor", "Original image size is " + original_size + "KB");

        	try
        	{
        	    BitmapFactory.Options options = new BitmapFactory.Options();
    			Bitmap image_bitmap; 
    			
    			int IMAGE_SIZE_UPPER_BOUND = 200;
    			int IMAGE_SIZE_LOWER_BOUND = 100;
    			int scale = original_size <= IMAGE_SIZE_UPPER_BOUND? 100 : 10;
    			int num_iters = 0;
    			long outputSize = 0;

    			while (num_iters < 5)
    			{
    				image_bitmap = BitmapFactory.decodeStream(new FileInputStream(f), null, options);
    				FileOutputStream os = PostEditor.this.openFileOutput(RESAMPLED_CAMERA_IMAGE_FILE, MODE_WORLD_WRITEABLE);
        			boolean result = image_bitmap.compress(Bitmap.CompressFormat.JPEG, scale, os);
        			Log.d("PostEditor", "Result of call to compress is " + result);
        			os.flush();
        			os.close();
        			image_bitmap.recycle();
        			image_bitmap = null;
        			System.gc();
        			outputSize = PostEditor.this.getFileStreamPath(RESAMPLED_CAMERA_IMAGE_FILE).length();
        			Log.d("PostEditor", "When scaled with quality=" + scale + ", image size is " + outputSize / 1024 + "KB.");
        			if (outputSize < 1024 * IMAGE_SIZE_LOWER_BOUND && scale <= 50)
        				scale *= 2;
        			else if (outputSize > 1024 * IMAGE_SIZE_UPPER_BOUND && scale >= 1)
        				scale /= 2;
        			else
        				break;
        			num_iters++;
        			Log.d("PostEditor", "Changing scale to " + scale);
    			}
    			Log.i("PostEditor", "Settling on image quality=" + scale + ", which yields an image size of " + outputSize / 1024 + "KB.");
    			
    			Matrix matrix = new Matrix();
    			int rotation = getExifRotation(PostEditor.this.getFileStreamPath(ORIGINAL_CAMERA_IMAGE_FILE).getCanonicalPath());        			
    			if (requestCode == 3 && rotation != 0)
    			{
    				Log.i("PostEditor", "Image needs rotation by " + rotation + " degrees. Rotating...");
    				File rf = PostEditor.this.getFileStreamPath(RESAMPLED_CAMERA_IMAGE_FILE);
    				image_bitmap = BitmapFactory.decodeStream(new FileInputStream(rf), null, options);
    				matrix.postRotate(rotation);
    				Bitmap rotated_bitmap = Bitmap.createBitmap(image_bitmap, 0, 0, image_bitmap.getWidth(),
    								                            image_bitmap.getHeight(), matrix, true);
    				image_bitmap.recycle();
    				image_bitmap = null;
    				System.gc();
    				
    				FileOutputStream os = PostEditor.this.openFileOutput(ROTATED_CAMERA_IMAGE_FILE, MODE_WORLD_WRITEABLE);
    				boolean result = rotated_bitmap.compress(Bitmap.CompressFormat.JPEG, scale, os);
    				os.flush();
    				os.close();
    				rotated_bitmap.recycle();
    				rotated_bitmap = null;
    				System.gc();
    				outputSize = PostEditor.this.getFileStreamPath(ROTATED_CAMERA_IMAGE_FILE).length();
        			Log.i("PostEditor", "After rotation, final image size is " + outputSize / 1024 + "KB.");
        			return ROTATED_CAMERA_IMAGE_FILE;
    			}
    			else
    			{
    				return RESAMPLED_CAMERA_IMAGE_FILE;
    			}
    			    			
            } catch (FileNotFoundException e) {
                // TODO Auto-generated catch block
                Log.e("PostEditor", Log.getStackTraceString(e));
            }
            catch (IOException e) {
				// TODO Auto-generated catch block
                Log.e("PostEditor", Log.getStackTraceString(e));
			}
            Log.e("PostEditor", "Error processing image. Returning original image and hoping for the best");
            return ORIGINAL_CAMERA_IMAGE_FILE;
		}
		
		@Override
		protected void onPostExecute(String result)
		{
			m_wait_dialog.dismiss();
			m_image_filename = result;
			m_image_uri = Uri.fromFile(PostEditor.this.getFileStreamPath(m_image_filename));
			DisplayImage();
		}

    }
    
    public int getExifRotation(String imgPath) {
        try {
            ExifInterface exif = new ExifInterface(imgPath);
            String rotationAmount = exif.getAttribute(ExifInterface.TAG_ORIENTATION);
            Log.d("PostEditor", "Orientation of image is: " + rotationAmount);
            if (!TextUtils.isEmpty(rotationAmount)) {
                int rotationParam = Integer.parseInt(rotationAmount);
                switch (rotationParam) {
                    case ExifInterface.ORIENTATION_NORMAL:
                        return 0;
                    case ExifInterface.ORIENTATION_ROTATE_90:
                        return 90;
                    case ExifInterface.ORIENTATION_ROTATE_180:
                        return 180;
                    case ExifInterface.ORIENTATION_ROTATE_270:
                        return 270;
                    default:
                        return 0;
                }
            } else {
                return 0;
            }
        } catch (Exception ex) {
            return 0;
        }
    }
    
    private void DisplayImage()
    {
    	ImageView image_selected = (ImageView)findViewById(R.id.post_editor_selected_image);
    	
    	try
    	{	
    		RadioGroup image_select_group = (RadioGroup)findViewById(R.id.post_editor_radio_group);
    		int selection = image_select_group.getCheckedRadioButtonId();
    		if (selection == R.id.radio_button_no_image)
    		{
    			image_selected.setImageResource(R.drawable.no_image);
    		}
    		else
    		{
    			image_selected.setImageURI(m_image_uri);
    		}
    	}
    	catch (Exception e)
    	{
    		image_selected.setImageResource(R.drawable.no_image);
    	}
    }
    
	@Override
    public void onClick(View view)
	{
		String body = ((EditText)findViewById(R.id.post_body)).getText().toString().trim();
		if (m_chosen_image_type == NONE && body.equals(""))
		{
			Toast.makeText(PostEditor.this, 
                           PostEditor.this.getString(R.string.warning_empty_post), 
				           Toast.LENGTH_LONG).show();
			return;
		}
		new PostTask().execute();
	}
	
	private class PostTask extends AsyncTask<Void, Void, Long> implements DialogInterface.OnCancelListener 
    {
		private String m_body;
    	private ProgressDialog m_wait_dialog;
    	private AtomicBoolean m_cancelled;
    	
		@Override
		protected Long doInBackground(Void... params) {
			try
			{
				String image_id = null;
				if (m_image_uri != null)
				{
					Log.d("PostEditor", "Image attached, uri is " + m_image_uri);
					if (m_cancelled.get())
						return -2L;
					image_id = PostImage(m_image_uri);
					if (image_id == null)
						return -1L;
				}
				Log.d("PostEditor", "Done posting image, image id = " + image_id + ". Now posting thread.");
				if (m_cancelled.get())
					return -2L;
				NewPost(m_in_reply_to, m_body, image_id);
				Log.d("PostEditor", "Done with post.");
			}
			catch (Exception e)
			{
				return -1L;
			}
			return 0L;
		}
		
		@Override 
		protected void onPreExecute()
		{
			m_cancelled = new AtomicBoolean(false);
			m_body = ((EditText)findViewById(R.id.post_body)).getText().toString().trim();
	        m_wait_dialog = ProgressDialog.show(PostEditor.this, 
	        		                            PostEditor.this.getString(R.string.blank), 
	        		                            PostEditor.this.getString(R.string.posting),
	        		                            true,
	        		                            true,
	        		                            this);
		}
		

		@Override
		public void onCancel(DialogInterface arg0) 
		{
			Log.d("PostEditor", "User is attempting to cancel the post");
			if (m_wait_dialog != null)
				m_wait_dialog.dismiss();
			m_wait_dialog = ProgressDialog.show(PostEditor.this, 
                                                PostEditor.this.getString(R.string.blank), 
                                                PostEditor.this.getString(R.string.attempting_to_cancel_post),
                                                true,
                                                true);
			m_cancelled.set(true);
		}
		
		@Override
		protected void onPostExecute(Long result)
		{
			m_wait_dialog.dismiss();
			if (result == -2L)
			{
				Log.d("PostEditor", "Post was cancelled before it could be committed");
				Toast.makeText(PostEditor.this.getApplicationContext(), R.string.post_cancelled, Toast.LENGTH_SHORT).show();				
			}
			else if (result < 0)
			{
				Log.d("PostEditor", "An error occurred during the post");
				Toast.makeText(PostEditor.this, 
						       PostEditor.this.getString(R.string.could_not_connect_to_post), 
						       Toast.LENGTH_LONG).show();
			}
			else
			{
				Log.d("PostEditor", "Post was successful: return code " + result);
				Toast.makeText(PostEditor.this.getApplicationContext(), R.string.post_successful, Toast.LENGTH_SHORT).show();
		        Intent iresult = new Intent();
		        setResult(RESULT_OK, iresult); //can set data to indicate post/thread here
		        finish();
			}
		}
		
		private String PostImage(Uri uri)
	    {
			Log.d("PostEditor", "Sending image to server via HTTP POST");
	    	HttpClient client = ((HTTPApplication)PostEditor.this.getApplication()).getHttpClient();
	        HttpPost post_request = new HttpPost(((HTTPApplication)PostEditor.this.getApplication()).getServiceAddress() + "/img");

	        //this is too round-about - getFileFromURI won't work with RESAMPLED stuff, so just set a flag if we're using camera
	        File file = PostEditor.this.getFileStreamPath(m_image_filename);
	        Log.d("PostEditor", "Selected image file " + file.getAbsolutePath() + " for posting");
	        //String content_type = getContentTypeFromURI(uri);
	        //Log.d("PostEditor", "Content type is " + content_type);

	        MultipartEntity mpEntity = new MultipartEntity();
	        ContentBody cbFile = new FileBody(file, "image/jpeg");
	        mpEntity.addPart("image", cbFile);

	        post_request.setEntity(mpEntity);
	        Log.d("PostEditor", "Added image as multi-part entity");

			String result = "";
	        try
	        {
	        	HttpResponse response = client.execute(post_request);
	        	HttpEntity entity = response.getEntity();
	        	if (entity != null) {
	                InputStream instream = entity.getContent();
	                result = convertStreamToString(instream);
	                instream.close();
	            }
	        }
	        catch (ClientProtocolException e)  
	        {
	            Log.e("PostEditor", Log.getStackTraceString(e));
	        } 
	        catch (IOException e) 
	        {
	            Log.e("PostEditor", Log.getStackTraceString(e));
	        }
	        
	        String image_id = null;
	        try {
				JSONObject result_struct = new JSONObject(result);
				image_id = result_struct.getString("_id");
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				Log.e("PostEditor", Log.getStackTraceString(e));
			}
	        
	        return image_id;
	    }
	    
	    public String getContentTypeFromURI(Uri contentUri)
	    {
	    	String[] proj = { MediaStore.Images.Media.MIME_TYPE };
	        Cursor cursor = managedQuery(contentUri, proj, null, null, null);
	        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE);
	        cursor.moveToFirst();
	        return cursor.getString(column_index);
	    }

	    private String convertStreamToString(InputStream is) 
	    {	 
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

	    
		private void NewPost(String post_id, String body, String image_id)
	    {
			double latitude, longitude;
			Pair<Double, Double> location = LocationSelector.GetLastLocation(PostEditor.this.getApplicationContext());
			latitude = location.first;
			longitude = location.second;
			
			HttpClient client = ((HTTPApplication)PostEditor.this.getApplication()).getHttpClient();
	        HttpPost post_request;
	        if (post_id == null)
	        	post_request = new HttpPost(((HTTPApplication)PostEditor.this.getApplication()).getServiceAddress() + "/threads");
	        else
	        	post_request = new HttpPost(((HTTPApplication)PostEditor.this.getApplication()).getServiceAddress() + "/posts/" + post_id);
	        Log.d(TAG, "Post request being sent to " + post_request.getURI().toString());
	        ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
	        params.add(new BasicNameValuePair("body", body));
	        params.add(new BasicNameValuePair("latitude", String.valueOf(latitude)));
	        params.add(new BasicNameValuePair("longitude", String.valueOf(longitude)));
	        if (image_id != null)
	        	params.add(new BasicNameValuePair("image", image_id));
	        
	        try {
				post_request.setEntity(new UrlEncodedFormEntity(params, HTTP.UTF_8));
			} catch (UnsupportedEncodingException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

			String result = "";
	        try
	        {
	        	HttpResponse response = client.execute(post_request);
	        	HttpEntity entity = response.getEntity();
	        	if (entity != null) {
	                InputStream instream = entity.getContent();
	                result = convertStreamToString(instream);
	                instream.close();
	            }
	        }
	        catch (ClientProtocolException e)  
	        {
	        	Log.e(TAG, Log.getStackTraceString(e));
	        } 
	        catch (IOException e) 
	        {
	        	Log.e(TAG, Log.getStackTraceString(e));
	        }                           	
	    }
    }
}

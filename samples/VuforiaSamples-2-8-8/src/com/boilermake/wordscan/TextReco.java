/*==============================================================================
 Copyright (c) 2012-2013 Qualcomm Connected Experiences, Inc.
 All Rights Reserved.
 ==============================================================================*/

package com.boilermake.wordscan;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.support.v7.media.MediaRouter;
import android.text.Layout.Alignment;
import android.text.Html;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.boilermake.wordscan.TextRecoRenderer.WordDesc;
import com.boilermake.wordscan.samples.SampleApplication.SampleApplicationControl;
import com.boilermake.wordscan.samples.SampleApplication.SampleApplicationException;
import com.boilermake.wordscan.samples.SampleApplication.SampleApplicationSession;
import com.boilermake.wordscan.samples.SampleApplication.utils.LoadingDialogHandler;
import com.boilermake.wordscan.samples.SampleApplication.utils.SampleApplicationGLView;
import com.boilermake.wordscan.samples.SampleApplication.utils.SampleUtils;
import com.boilermake.wordscan.R;
import com.parse.Parse;
import com.qualcomm.vuforia.CameraDevice;
import com.qualcomm.vuforia.RectangleInt;
import com.qualcomm.vuforia.Renderer;
import com.qualcomm.vuforia.State;
import com.qualcomm.vuforia.TextTracker;
import com.qualcomm.vuforia.Tracker;
import com.qualcomm.vuforia.TrackerManager;
import com.qualcomm.vuforia.VideoBackgroundConfig;
import com.qualcomm.vuforia.VideoMode;
import com.qualcomm.vuforia.Vuforia;
import com.qualcomm.vuforia.WordList;
import java.util.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;


public class TextReco extends Activity implements SampleApplicationControl
{
	private static final String LOGTAG = "TextReco";
	private static final String URL = "https://ajax.googleapis.com/ajax/services/search/images?v=1.0&safe=high&imgsz=medium&q=";
	
	Context context;

	SampleApplicationSession vuforiaAppSession;

	private final static int COLOR_OPAQUE = Color.argb(0, 255, 246, 112);//Color.argb(178, 0, 0, 0); #FFF670
	private final static int WORDLIST_MARGIN = 10;
	public static TextView definitionView;

	// Our OpenGL view:
	private SampleApplicationGLView mGlView;

	Activity activity;
	
	// Our renderer:
	private TextRecoRenderer mRenderer;

	//private SampleAppMenu mSampleAppMenu;

	private ArrayList<View> mSettingsAdditionalViews;

	private RelativeLayout mUILayout;
	
	private TextView definition, posView;
	
	private ImageView img_def, play_sound;

	private LoadingDialogHandler loadingDialogHandler = new LoadingDialogHandler(
			this);
	private boolean mIsTablet = false;

	private boolean mIsVuforiaStarted = false;

	private GestureDetector mGestureDetector;

	private boolean mFlash = false;

	private View mFlashOptionView;

	boolean mIsDroidDevice = false;

	private MediaRouter mMediaRouter;


	// Called when the activity first starts or the user navigates back to an
	// activity.
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		Log.d(LOGTAG, "onCreate");
		super.onCreate(savedInstanceState);
		
		context = this;

		vuforiaAppSession = new SampleApplicationSession(this);

		mMediaRouter = MediaRouter.getInstance(getApplicationContext());

		startLoadingAnimation();

		vuforiaAppSession
		.initAR(this, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

		mGestureDetector = new GestureDetector(this, new GestureListener());

		mIsDroidDevice = android.os.Build.MODEL.toLowerCase().startsWith(
				"droid");
		definitionView = (TextView) findViewById(R.id.def);
		definitionView.setTextColor(Color.parseColor("#ffc754"));
		posView = (TextView) findViewById(R.id.pos);
		posView.setTextColor(Color.parseColor("#ffc754"));
		play_sound = (ImageView) findViewById(R.id.play_sound);
		img_def = (ImageView) findViewById(R.id.img_def);
		
		if(definitionView == null)
		{
			System.exit(0);
		}
		
		Parse.initialize(this, "dNSrgbF0wn6FqmxiApvKo4ygEShIS25E8QOBLrYy", "6nLbWZTpHE9hASVgNC2IADpfi6A4OVDzsx8w3Kdn");
	}

	// Process Single Tap event to trigger autofocus
	private class GestureListener extends
	GestureDetector.SimpleOnGestureListener
	{
		// Used to set autofocus one second after a manual focus is triggered
		private final Handler autofocusHandler = new Handler();


		@Override
		public boolean onDown(MotionEvent e)
		{
			return true;
		}


		@Override
		public boolean onSingleTapUp(MotionEvent e)
		{
			// Generates a Handler to trigger autofocus
			// after 1 second
			autofocusHandler.postDelayed(new Runnable()
			{
				public void run()
				{
					boolean result = CameraDevice.getInstance().setFocusMode(
							CameraDevice.FOCUS_MODE.FOCUS_MODE_TRIGGERAUTO);

					if (!result)
						Log.e("SingleTapUp", "Unable to trigger focus");
				}
			}, 1000L);

			return true;
		}
	}

	 private String capitalize(String line)
	    {
	      return Character.toUpperCase(line.charAt(0)) + line.substring(1);
	    }
	 
	//HERE IS THIS FUCKING HANDLER
	private final Handler handler = new Handler()
	{
		public void handleMessage(Message msg)
		{            
			try
			{
				Bundle b = msg.getData();
				String data[] = b.getStringArray("def");
				data[0] = data[0].replace(":", "");
				data[0] = data[0].replace(" .", ".");
				data[0] = data[0].replace("&amp;", "&");
				data[0] = data[0].replace("Un>", "");
				definitionView.setText(Html.fromHtml("<i><font color=\"white\">" + capitalize(data[3]) + ": <\font></i>" + (capitalize(data[0])) + "."));
				
				if(data[1].equals("noun"))
				{
					posView.setText(Html.fromHtml("<font color=\"white\"><i>Part of speech: </i><\font><font color=\"cyan\">" + capitalize(data[1]) + "<\font>"));
				}
				else if(data[1].equals("verb"))
				{
					posView.setText(Html.fromHtml("<font color=\"white\"><i>Part of speech: </i><\font><font color=\"magenta\">" + capitalize(data[1]) + "<\font>"));
				}
				else if(data[1].equals("adjective"))
				{
					posView.setText(Html.fromHtml("<font color=\"white\"><i>Part of speech: </i><\font><font color=\"green\">" + capitalize(data[1]) + "<\font>"));
				}
				else if(data[1].equals("adverb"))
				{
					posView.setText(Html.fromHtml("<font color=\"white\"><i>Part of speech: </i><\font><font color=\"red\">" + capitalize(data[1]) + "<\font>"));
				}
				else if(data[1].equals("pronoun"))
				{
					posView.setText(Html.fromHtml("<font color=\"white\"><i>Part of speech: </i><\font><font color=\"blue\">" + capitalize(data[1]) + "<\font>"));
				}
				else if(data[1].equals("conjunction"))
				{
					posView.setText(Html.fromHtml("<font color=\"white\"><i>Part of speech: </i><\font><font color=\"brown\">" + capitalize(data[1]) + "<\font>"));
				}
				
				final String URL = data[2];
				final String name = data[3];
				//new ImageSearchApi().execute(img_def, data[3]);
				
				play_sound.setOnClickListener(new OnClickListener(){

					@Override
					public void onClick(View v) {
						// TODO Auto-generated method stub
						new PlaySound().execute(URL);
					}
					
				});
				
				img_def.setOnClickListener(new OnClickListener(){

					@Override
					public void onClick(View v) {
						// TODO Auto-generated method stub
						Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?site=imghp&tbm=isch&source=hp&biw=1440&bih=779&q=" + name));
						startActivity(browserIntent);
						
					}
					
					
					
				});
				
				/*img_def.setOnLongClickListener(new OnLongClickListener(){

					@Override
					public boolean onLongClick(View v) {
						// TODO Auto-generated method stub
						 MediaPlayer mp = MediaPlayer.create(context, R.raw.fox_short);
		                    mp.setOnCompletionListener(new OnCompletionListener() {

		                        @Override
		                        public void onCompletion(MediaPlayer mp) {
		                            // TODO Auto-generated method stub
		                            mp.release();
		                        }

		                    });   
		                    mp.start();
						return false;
					}
					
				});*/
			}
			catch(Exception e)
			{
				e.printStackTrace();
			}
		}
	};
	
	private class PlaySound extends AsyncTask<String, Void, Void>
	{
		public String url = "http://media.merriam-webster.com/soundc11/";
		MediaPlayer mediaPlayer;
		
		protected Void doInBackground(String... params)
        {
			try
			{
				url += params[0].charAt(0) + "/" + params[0];
				mediaPlayer = new MediaPlayer();
				mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
				mediaPlayer.setDataSource(url);
				mediaPlayer.prepareAsync();
				//You can show progress dialog here untill it prepared to play
				mediaPlayer.setOnPreparedListener(new OnPreparedListener() {
				        @Override
				        public void onPrepared(MediaPlayer mp) {
				            //Now dismis progress dialog, Media palyer will start playing
				            mp.start();
				        }
				    });
				    mediaPlayer.setOnErrorListener(new OnErrorListener() {
				        @Override
				        public boolean onError(MediaPlayer mp, int what, int extra) {
				            // dissmiss progress bar here. It will come here when MediaPlayer
				            //  is not able to play file. You can show error message to user
				            return false;
				        }
				    });
			}
			catch(Exception e)
			{
				e.printStackTrace();
			}

			return null;
        }
		
		protected void onPostExecute(Void result)
		{
			
		}
	}
	public class ImageSearchApi extends AsyncTask<Object, Void, String>
	{
		ImageView iv;
		String endpoint;
	protected String doInBackground(Object... arg0)
	{
		String result = null;
		String img = null;
		iv = (ImageView) arg0[0];
		endpoint = URL + (String) arg0[1];
		
		if (endpoint.startsWith("http://") || endpoint.startsWith("https://"))
		{
			// Send a GET request
			try
			{
				// Send data
				URL url = new URL(endpoint);
				URLConnection conn = url.openConnection();

				// Get the response
				BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
				StringBuffer sb = new StringBuffer();
				String line;
				while ((line = rd.readLine()) != null)
				{
					sb.append(line);
				}
				rd.close();
				result = sb.toString();
			} catch (Exception e)
			{
				e.printStackTrace();
				result = null;
			}
		}
		
		try 
		{
			//HERE?
			JSONObject object = (JSONObject) new JSONTokener(result).nextValue();
			JSONObject resultsObject =  (JSONObject) object.getJSONObject("responseData").getJSONArray("results").get(0);
			String url = (String) resultsObject.get("unescapedUrl");
			img = url.toString();
		    
		} catch (Exception e) 
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
			result = null;
		}
		return null;
	}
	
		protected void onPostExecute(String img)
		{
			if(img != null)
			{
				new loadImageFromParse().execute(iv, img);
			}
		}
	}
	
	
	protected class loadImageFromParse extends AsyncTask<Object, Integer, Void>
    {
        ImageView photo;
        Bitmap image;
        File compressed;
        String word;

        protected Void doInBackground(Object... arg0) //ImageView as arg0, word as arg1
        {
            try
            {
                photo = (ImageView) arg0[0];
                URL url = new URL((String)arg0[1]);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                InputStream is = connection.getInputStream();

                compressed = new File(context.getFilesDir() + "/" + word);
                FileOutputStream fos = new FileOutputStream(compressed);
                BufferedOutputStream bos = new BufferedOutputStream(fos, 1024);
                byte data[] = new byte[1024];

                int bytesRead = 0;
                while((bytesRead = is.read(data, 0, data.length)) >= 0)
                {
                    bos.write(data, 0, bytesRead);
                }

                bos.close();
                fos.close();
                is.close();

               

                BitmapFactory.Options o2 = new BitmapFactory.Options();
                o2.inSampleSize=1;
                image = BitmapFactory.decodeStream(new FileInputStream(compressed), null, o2);


            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
            return null;
        }

        protected void onPostExecute(Void v)
        {
            if(image != null)
            {
                //final Animation in = new AlphaAnimation(0.0f, 1.0f);
                //in.setDuration(1000);
                photo.setImageBitmap(image);
                //photo.startAnimation(in);

                photo.setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View view)
                    {
                        //Intent fullscreenimage = new Intent(context, FullScreenImageView.class);
                        //fullscreenimage.putExtra("image", file.getAbsolutePath());
                        //fullscreenimage.putExtra("caption", " ");
                        //startActivity(fullscreenimage);
                    }
                });
            }
        }
    }



	// Called when the activity will start interacting with the user.
	@Override
	protected void onResume()
	{
		Log.d(LOGTAG, "onResume");
		super.onResume();
		
		try
        {
        	TextRecoRenderer.wordCache = (HashMap<String, String[]>) PersistenceManager.readObject(this, "cache");
        }
        catch(Exception e)
        {
        	e.printStackTrace();
        }

		// This is needed for some Droid devices to force portrait
		if (mIsDroidDevice)
		{
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		}

		try
		{
			vuforiaAppSession.resumeAR();
		} catch (SampleApplicationException e)
		{
			Log.e(LOGTAG, e.getString());
		}

		if (mIsVuforiaStarted)
			postStartCamera();

		// Resume the GL view:
			if (mGlView != null)
			{
				mGlView.setVisibility(View.VISIBLE);
				mGlView.onResume();
			}
			

	}


	// Callback for configuration changes the activity handles itself
	@Override
	public void onConfigurationChanged(Configuration config)
	{
		Log.d(LOGTAG, "onConfigurationChanged");
		super.onConfigurationChanged(config);

		vuforiaAppSession.onConfigurationChanged();
		configureVideoBackgroundROI();
	}


	@Override
	public boolean onTouchEvent(MotionEvent event)
	{
		// Process the Gestures
		//if (mSampleAppMenu != null && mSampleAppMenu.processEvent(event))
		//return true;

		return mGestureDetector.onTouchEvent(event);
	}


	// Called when the system is about to start resuming a previous activity.
	@SuppressLint("NewApi")
	@Override
	protected void onPause()
	{
		Log.d(LOGTAG, "onPause");
		super.onPause();

		if (mGlView != null)
		{
			mGlView.setVisibility(View.INVISIBLE);
			mGlView.onPause();
		}

		// Turn off the flash
		if (mFlashOptionView != null && mFlash)
		{
			// OnCheckedChangeListener is called upon changing the checked state
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
			{
				((Switch) mFlashOptionView).setChecked(false);
			} else
			{
				((CheckBox) mFlashOptionView).setChecked(false);
			}
		}

		try
		{
			vuforiaAppSession.pauseAR();
		} catch (SampleApplicationException e)
		{
			Log.e(LOGTAG, e.getString());
		}

		stopCamera();
		
		try
		{
			PersistenceManager.writeObject(this, "cache", TextRecoRenderer.wordCache);
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		
	}


	// The final call you receive before your activity is destroyed.
	@Override
	protected void onDestroy()
	{
		Log.d(LOGTAG, "onDestroy");
		super.onDestroy();

		try
		{
			vuforiaAppSession.stopAR();
		} catch (SampleApplicationException e)
		{
			Log.e(LOGTAG, e.getString());
		}

		System.gc();
	}


	private void startLoadingAnimation()
	{
		LayoutInflater inflater = LayoutInflater.from(this);
		mUILayout = (RelativeLayout) inflater.inflate(
				R.layout.camera_overlay_textreco, null, false);

		mUILayout.setVisibility(View.VISIBLE);
		mUILayout.setBackgroundColor(Color.BLACK);

		// Gets a reference to the loading dialog
		loadingDialogHandler.mLoadingDialogContainer = mUILayout
				.findViewById(R.id.loading_indicator);

		// Shows the loading indicator at start
		loadingDialogHandler
		.sendEmptyMessage(LoadingDialogHandler.SHOW_LOADING_DIALOG);

		// Adds the inflated layout to the view
		addContentView(mUILayout, new LayoutParams(LayoutParams.MATCH_PARENT,
				LayoutParams.MATCH_PARENT));

	}


	// Initializes AR application components.
	private void initApplicationAR()
	{
		// Create OpenGL ES view:
		int depthSize = 16;
		int stencilSize = 0;
		boolean translucent = Vuforia.requiresAlpha();

		mGlView = new SampleApplicationGLView(this);
		mGlView.init(translucent, depthSize, stencilSize);

		mRenderer = new TextRecoRenderer(this, vuforiaAppSession, handler);
		mGlView.setRenderer(mRenderer);

		showLoupe(false);

	}


	private void postStartCamera()
	{
		// Sets the layout background to transparent
		mUILayout.setBackgroundColor(Color.TRANSPARENT);

		// start the image tracker now that the camera is started
		Tracker t = TrackerManager.getInstance().getTracker(
				TextTracker.getClassType());
		if (t != null)
			t.start();

		configureVideoBackgroundROI();
	}


	void configureVideoBackgroundROI()
	{
		VideoMode vm = CameraDevice.getInstance().getVideoMode(
				CameraDevice.MODE.MODE_DEFAULT);
		VideoBackgroundConfig config = Renderer.getInstance()
				.getVideoBackgroundConfig();

		DisplayMetrics metrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(metrics);
		int screenWidth = metrics.widthPixels;
		int screenHeight = metrics.heightPixels;

		{
			// calc ROI
			// width of margin is :
				// 5% of the width of the screen for a phone
			// 20% of the width of the screen for a tablet
			int marginWidth = mIsTablet ? (screenWidth * 20) / 100
					: (screenWidth * 5) / 100;

			// loupe height is :
			// 15% of the screen height for a phone
			// 10% of the screen height for a tablet
			int loupeHeight = mIsTablet ? (screenHeight * 10) / 100
					: (screenHeight * 15) / 100;

			// lupue width takes the width of the screen minus 2 margins
			int loupeWidth = screenWidth - (2 * marginWidth);

			// definition of the region of interest
			mRenderer.setROI(screenWidth / 2, marginWidth + (loupeHeight / 2),
					loupeWidth, loupeHeight);
		}

		// convert into camera coords
		int[] loupeCenterX = { 0 };
		int[] loupeCenterY = { 0 };
		int[] loupeWidth = { 0 };
		int[] loupeHeight = { 0 };
		SampleUtils.screenCoordToCameraCoord((int) mRenderer.ROICenterX,
				(int) mRenderer.ROICenterY, (int) mRenderer.ROIWidth,
				(int) mRenderer.ROIHeight, screenWidth, screenHeight,
				vm.getWidth(), vm.getHeight(), loupeCenterX, loupeCenterY,
				loupeWidth, loupeHeight);

		RectangleInt detROI = new RectangleInt(loupeCenterX[0]
				- (loupeWidth[0] / 2), loupeCenterY[0] - (loupeHeight[0] / 2),
				loupeCenterX[0] + (loupeWidth[0] / 2), loupeCenterY[0]
						+ (loupeHeight[0] / 2));

		TextTracker tt = (TextTracker) TrackerManager.getInstance().getTracker(
				TextTracker.getClassType());
		if (tt != null)
			tt.setRegionOfInterest(detROI, detROI,
					TextTracker.UP_DIRECTION.REGIONOFINTEREST_UP_IS_9_HRS);

		int[] size = config.getSize().getData();
		int[] pos = config.getPosition().getData();
		int offx = ((screenWidth - size[0]) / 2) + pos[0];
		int offy = ((screenHeight - size[1]) / 2) + pos[1];
		mRenderer.setViewport(offx, offy, size[0], size[1]);
	}


	private void stopCamera()
	{
		doStopTrackers();

		CameraDevice.getInstance().stop();
		CameraDevice.getInstance().deinit();
	}


	void updateWordListUI(final List<WordDesc> words)
	{
		runOnUiThread(new Runnable()
		{

			public void run()
			{
				/*RelativeLayout wordListLayout = (RelativeLayout) mUILayout
						.findViewById(R.id.wordList);
				
				
				wordListLayout.removeAllViews();

				if (words.size() > 0)
				{
					LayoutParams params = wordListLayout.getLayoutParams();
					// Changes the height and width to the specified pixels
					int maxTextHeight = params.height - (2 * WORDLIST_MARGIN);

					int[] textInfo = fontSizeForTextHeight(maxTextHeight,
							words.size(), params.width, 32, 12);

					int count = -1;
					int nbWords = textInfo[2]; // number of words we can display
					TextView previousView = null;
					TextView tv;
					for (WordDesc word : words)
					{
						count++;
						if (count == nbWords)
						{
							break;
						}
						tv = new TextView(TextReco.this);
						//tv.setText(word.text);
						RelativeLayout.LayoutParams txtParams = new RelativeLayout.LayoutParams(
								LayoutParams.MATCH_PARENT,
								LayoutParams.WRAP_CONTENT);

						if (previousView != null)
							txtParams.addRule(RelativeLayout.BELOW,
									previousView.getId());

						txtParams.setMargins(0, (count == 0) ? WORDLIST_MARGIN
								: 0, 0, (count == (nbWords - 1)) ? WORDLIST_MARGIN
										: 0);
						tv.setLayoutParams(txtParams);
						tv.setGravity(Gravity.CENTER_VERTICAL
								| Gravity.CENTER_HORIZONTAL);
						tv.setTextSize(textInfo[0]);
						tv.setTextColor(Color.WHITE);
						tv.setHeight(textInfo[1]);
						tv.setId(count + 100);

						wordListLayout.addView(tv);
						previousView = tv;
					}
				}*/
			}
		});
	}


	private void showLoupe(boolean isActive)
	{
		DisplayMetrics metrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(metrics);
		int width = metrics.widthPixels;
		int height = metrics.heightPixels;

		// width of margin is :
		// 5% of the width of the screen for a phone
		// 20% of the width of the screen for a tablet
		int marginWidth = mIsTablet ? (width * 20) / 100 : (width * 5) / 100;

		// loupe height is :
		// 33% of the screen height for a phone
		// 20% of the screen height for a tablet
		int loupeHeight = mIsTablet ? (height * 10) / 100 : (height * 15) / 100;

		// lupue width takes the width of the screen minus 2 margins
		int loupeWidth = width - (2 * marginWidth);

		int wordListHeight = height - (loupeHeight + marginWidth);

		// definition of the region of interest
		mRenderer.setROI(width / 2, marginWidth + (loupeHeight / 2),
				loupeWidth, loupeHeight);

		// Gets a reference to the loading dialog
		View loadingIndicator = mUILayout.findViewById(R.id.loading_indicator);

		RelativeLayout loupeLayout = (RelativeLayout) mUILayout
				.findViewById(R.id.loupeLayout);

		ImageView topMargin = (ImageView) mUILayout
				.findViewById(R.id.topMargin);

		ImageView leftMargin = (ImageView) mUILayout
				.findViewById(R.id.leftMargin);

		ImageView rightMargin = (ImageView) mUILayout
				.findViewById(R.id.rightMargin);

		ImageView loupeArea = (ImageView) mUILayout.findViewById(R.id.loupe);

		RelativeLayout wordListLayout = (RelativeLayout) mUILayout
				.findViewById(R.id.wordList);

		wordListLayout.setBackgroundColor(COLOR_OPAQUE);

		if (isActive)
		{
			topMargin.getLayoutParams().height = marginWidth;
			topMargin.getLayoutParams().width = width;

			leftMargin.getLayoutParams().width = marginWidth;
			leftMargin.getLayoutParams().height = loupeHeight;

			rightMargin.getLayoutParams().width = marginWidth;
			rightMargin.getLayoutParams().height = loupeHeight;

			RelativeLayout.LayoutParams params;

			params = (RelativeLayout.LayoutParams) loupeLayout
					.getLayoutParams();
			params.height = loupeHeight;
			loupeLayout.setLayoutParams(params);

			loupeArea.getLayoutParams().width = loupeWidth;
			loupeArea.getLayoutParams().height = loupeHeight;
			loupeArea.setVisibility(View.VISIBLE);

			params = (RelativeLayout.LayoutParams) wordListLayout
					.getLayoutParams();
			params.height = wordListHeight;
			params.width = width;
			wordListLayout.setLayoutParams(params);

			loadingIndicator.setVisibility(View.GONE);
			loupeArea.setVisibility(View.VISIBLE);
			topMargin.setVisibility(View.VISIBLE);
			loupeLayout.setVisibility(View.VISIBLE);
			wordListLayout.setVisibility(View.VISIBLE);

		} else
		{
			loadingIndicator.setVisibility(View.VISIBLE);
			loupeArea.setVisibility(View.GONE);
			topMargin.setVisibility(View.GONE);
			loupeLayout.setVisibility(View.GONE);
			wordListLayout.setVisibility(View.GONE);
		}

	}


	// the funtions returns 3 values in an array of ints
	// [0] : the text size
	// [1] : the text component height
	// [2] : the number of words we can display
	private int[] fontSizeForTextHeight(int totalTextHeight, int nbWords,
			int textWidth, int textSizeMax, int textSizeMin)
	{

		int[] result = new int[3];
		String text = "Agj";
		TextView tv = new TextView(this);
		tv.setText(text);
		tv.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
				LayoutParams.WRAP_CONTENT));
		tv.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
		// tv.setTextSize(30);
		// tv.setHeight(textHeight);
		int textSize = 0;
		int layoutHeight = 0;

		final float densityMultiplier = getResources().getDisplayMetrics().density;

		for (textSize = textSizeMax; textSize >= textSizeMin; textSize -= 2)
		{
			// Get the font size setting
			float fontScale = Settings.System.getFloat(getContentResolver(),
					Settings.System.FONT_SCALE, 1.0f);
			// Text view line spacing multiplier
			float spacingMult = 1.0f * fontScale;
			// Text view additional line spacing
			float spacingAdd = 0.0f;
			TextPaint paint = new TextPaint(tv.getPaint());
			paint.setTextSize(textSize * densityMultiplier);
			// Measure using a static layout
			StaticLayout layout = new StaticLayout(text, paint, textWidth,
					Alignment.ALIGN_NORMAL, spacingMult, spacingAdd, true);
			layoutHeight = layout.getHeight();
			if ((layoutHeight * nbWords) < totalTextHeight)
			{
				result[0] = textSize;
				result[1] = layoutHeight;
				result[2] = nbWords;
				return result;
			}
		}

		// we won't be able to display all the fonts
		result[0] = textSize;
		result[1] = layoutHeight;
		result[2] = totalTextHeight / layoutHeight;
		return result;
	}


	@Override
	public void onInitARDone(SampleApplicationException exception)
	{

		if (exception == null)
		{
			initApplicationAR();

			// Hint to the virtual machine that it would be a good time to
			// run the garbage collector:
			//
			// NOTE: This is only a hint. There is no guarantee that the
			// garbage collector will actually be run.
			System.gc();

			// Activate the renderer:
			mRenderer.mIsActive = true;

			// Now add the GL surface view. It is important
			// that the OpenGL ES surface view gets added
			// BEFORE the camera is started and video
			// background is configured.
			addContentView(mGlView, new LayoutParams(LayoutParams.MATCH_PARENT,
					LayoutParams.MATCH_PARENT));

			// Hides the Loading Dialog
			loadingDialogHandler
			.sendEmptyMessage(LoadingDialogHandler.HIDE_LOADING_DIALOG);
			showLoupe(true);

			// Sets the UILayout to be drawn in front of the camera
			mUILayout.bringToFront();

			try
			{
				vuforiaAppSession.startAR(CameraDevice.CAMERA.CAMERA_DEFAULT);
			} catch (SampleApplicationException e)
			{
				Log.e(LOGTAG, e.getString());
			}

			mIsVuforiaStarted = true;

			postStartCamera();



		} else
		{
			Log.e(LOGTAG, exception.getString());
			finish();
		}
	}


	// Functions to load and destroy tracking data.
	@Override
	public boolean doLoadTrackersData()
	{
		TrackerManager tm = TrackerManager.getInstance();
		TextTracker tt = (TextTracker) tm
				.getTracker(TextTracker.getClassType());
		WordList wl = tt.getWordList();

		return wl.loadWordList("TextReco/Vuforia-English-word.vwl",
				WordList.STORAGE_TYPE.STORAGE_APPRESOURCE);
	}


	@Override
	public boolean doUnloadTrackersData()
	{
		// Indicate if the trackers were unloaded correctly
		boolean result = true;
		TrackerManager tm = TrackerManager.getInstance();
		TextTracker tt = (TextTracker) tm
				.getTracker(TextTracker.getClassType());
		WordList wl = tt.getWordList();
		wl.unloadAllLists();

		return result;
	}


	@Override
	public void onQCARUpdate(State state)
	{
	}


	@Override
	public boolean doInitTrackers()
	{
		TrackerManager tManager = TrackerManager.getInstance();
		Tracker tracker;

		// Indicate if the trackers were initialized correctly
		boolean result = true;

		tracker = tManager.initTracker(TextTracker.getClassType());
		if (tracker == null)
		{
			Log.e(
					LOGTAG,
					"Tracker not initialized. Tracker already initialized or the camera is already started");
			result = false;
		} else
		{
			Log.i(LOGTAG, "Tracker successfully initialized");
		}

		return result;
	}


	@Override
	public boolean doStartTrackers()
	{
		// Indicate if the trackers were started correctly
		boolean result = true;

		Tracker textTracker = TrackerManager.getInstance().getTracker(
				TextTracker.getClassType());
		if (textTracker != null)
			textTracker.start();

		return result;
	}


	@Override
	public boolean doStopTrackers()
	{
		// Indicate if the trackers were stopped correctly
		boolean result = true;

		Tracker textTracker = TrackerManager.getInstance().getTracker(
				TextTracker.getClassType());
		if (textTracker != null)
			textTracker.stop();

		return result;
	}


	@Override
	public boolean doDeinitTrackers()
	{
		// Indicate if the trackers were deinitialized correctly
		boolean result = true;
		Log.e(LOGTAG, "UnloadTrackersData");

		TrackerManager tManager = TrackerManager.getInstance();
		tManager.deinitTracker(TextTracker.getClassType());

		return result;
	}

	final public static int CMD_BACK = -1;
	final public static int CMD_FLASH = 1;


	// This method sets the additional views to be moved along with the GLView
	private void setSampleAppMenuAdditionalViews()
	{
		mSettingsAdditionalViews = new ArrayList<View>();
		mSettingsAdditionalViews.add(mUILayout.findViewById(R.id.topMargin));
		mSettingsAdditionalViews.add(mUILayout.findViewById(R.id.loupeLayout));
		mSettingsAdditionalViews.add(mUILayout.findViewById(R.id.wordList));
		
	}




	private void showToast(String text)
	{
		Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
	}

}
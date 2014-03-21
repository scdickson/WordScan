package com.boilermake.wordscan;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

import org.json.JSONObject;
import org.json.JSONTokener;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

public class DefPage extends Fragment 
{
	private TextView definition, posView;
	private ImageView img_def, play_sound;
	public TextView definitionView;
	public WordListAdapter adapter;
	public ListView multipleWordView;
	public LinearLayout defLayout, listLayout, buttonLayout;
	Context context;
	int type;
	
	public DefPage(int type)
	{
		this.type = type;
	}
	
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) 
    {
        ViewGroup rootView = (ViewGroup) inflater.inflate(R.layout.defpage, container, false);
        context = rootView.getContext();
        defLayout = (LinearLayout) rootView.findViewById(R.id.defLayout);
        listLayout = (LinearLayout) rootView.findViewById(R.id.listLayout);
        buttonLayout = (LinearLayout) rootView.findViewById(R.id.buttonLayout);
        definitionView = (TextView) rootView.findViewById(R.id.def);
		definitionView.setTextColor(Color.parseColor("#ffc754"));
		posView = (TextView) rootView.findViewById(R.id.pos);
		posView.setTextColor(Color.parseColor("#ffc754"));
		play_sound = (ImageView) rootView.findViewById(R.id.play_sound);
		img_def = (ImageView) rootView.findViewById(R.id.img_def);
		multipleWordView = (ListView) rootView.findViewById(R.id.multiple_word_list);
		String[] nul = {"No Words Scanned..."};
		adapter = new WordListAdapter(context, nul);
		multipleWordView.setAdapter(adapter);
		
        
        if(type == TextReco.TYPE_DEF)
        {
        	defLayout.setVisibility(View.VISIBLE);
	        listLayout.setVisibility(View.GONE);
	        buttonLayout.setVisibility(View.VISIBLE);
			
			if(definitionView == null)
			{
				System.exit(0);
			}
        }
        else if(type == TextReco.TYPE_MWV)
        {
        	defLayout.setVisibility(View.GONE);
			listLayout.setVisibility(View.VISIBLE);
			buttonLayout.setVisibility(View.GONE);
        }
        
        return rootView;
    }
    
    public Handler getHandler()
    {
    	if(type == TextReco.TYPE_MWV)
    	{
    		return mwHandler;
    	}
    	
    	return defHandler;
    }
    
    private final Handler mwHandler = new Handler()
	{
		public void handleMessage(Message msg)
		{    
			try
			{
				//System.out.println("handled okay?");
				Bundle b = msg.getData();
				String data[] = b.getStringArray("words");
				adapter.setArray(data);
		        adapter.notifyDataSetChanged();
			}
			catch(Exception e)
			{
				e.printStackTrace();
			}
		}
	};
    
    private final Handler defHandler = new Handler()
	{
		public void handleMessage(Message msg)
		{            
			try
			{
				Bundle b = msg.getData();
				String data[] = b.getStringArray("def");
				String words[] = b.getStringArray("words");
				
				data[0] = data[0].replace(":", "");
				data[0] = data[0].replace(" .", ".");
				data[0] = data[0].replace("&amp;", "&");
				data[0] = data[0].replace("Un>", "");
				
				definitionView.setText(Html.fromHtml("<i><font color=\"white\">" + (data[3]) + ": <\font></i>" + ((data[0])) + "."));
				
				if(data[1].equalsIgnoreCase("noun"))
				{
					posView.setText(Html.fromHtml("<font color=\"white\"><i>Part of speech: </i><\font><font color=\"cyan\">" + (data[1]) + "<\font>"));
				}
				else if(data[1].equalsIgnoreCase("verb"))
				{
					posView.setText(Html.fromHtml("<font color=\"white\"><i>Part of speech: </i><\font><font color=\"magenta\">" + (data[1]) + "<\font>"));
				}
				else if(data[1].equalsIgnoreCase("adjective"))
				{
					posView.setText(Html.fromHtml("<font color=\"white\"><i>Part of speech: </i><\font><font color=\"green\">" + (data[1]) + "<\font>"));
				}
				else if(data[1].equalsIgnoreCase("adverb"))
				{
					posView.setText(Html.fromHtml("<font color=\"white\"><i>Part of speech: </i><\font><font color=\"red\">" + (data[1]) + "<\font>"));
				}
				else if(data[1].equalsIgnoreCase("pronoun"))
				{
					posView.setText(Html.fromHtml("<font color=\"white\"><i>Part of speech: </i><\font><font color=\"blue\">" + (data[1]) + "<\font>"));
				}
				else if(data[1].equalsIgnoreCase("conjunction"))
				{
					posView.setText(Html.fromHtml("<font color=\"white\"><i>Part of speech: </i><\font><font color=\"brown\">" + (data[1]) + "<\font>"));
				}
				else
				{
					posView.setText(Html.fromHtml("<font color=\"white\"><i>Part of speech: </i><\font><font color=\"cyan\">" + (data[1]) + "<\font>"));
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

}
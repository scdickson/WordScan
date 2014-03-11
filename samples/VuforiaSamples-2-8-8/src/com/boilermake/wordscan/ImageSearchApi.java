package com.boilermake.wordscan;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.os.AsyncTask;


public class ImageSearchApi extends AsyncTask<String, Void, String>
{
	private static final String URL = "https://ajax.googleapis.com/ajax/services/search/images?v=1.0&q=";  //Request without the parameter.

	/*
	 * Requests the weather data from the API. Returns null if the request fails.
	 */
	protected String doInBackground(String... arg0)
	{
		String result = null;
		String endpoint = arg0[0];
		
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
			}
		}
		
		try {
			JSONObject object = (JSONObject) new JSONTokener(result).nextValue();
			JSONObject resultsObject =  (JSONObject) object.getJSONObject("responseData").getJSONArray("results").get(0);
			String url = (String) resultsObject.get("unescapedUrl");
			return url;
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
}

/*==============================================================================
 Copyright (c) 2012-2013 Qualcomm Connected Experiences, Inc.
 All Rights Reserved.
 ==============================================================================*/

package com.qualcomm.vuforia.samples.VuforiaSamples.app.TextRecognition;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.TextView;

import com.parse.FindCallback;
import com.parse.ParseObject;
import com.parse.ParseQuery;
import com.qualcomm.vuforia.Matrix44F;
import com.qualcomm.vuforia.Obb2D;
import com.qualcomm.vuforia.Renderer;
import com.qualcomm.vuforia.State;
import com.qualcomm.vuforia.Tool;
import com.qualcomm.vuforia.TrackableResult;
import com.qualcomm.vuforia.VIDEO_BACKGROUND_REFLECTION;
import com.qualcomm.vuforia.Vec2F;
import com.qualcomm.vuforia.Vuforia;
import com.qualcomm.vuforia.Word;
import com.qualcomm.vuforia.WordResult;
import com.qualcomm.vuforia.samples.SampleApplication.SampleApplicationSession;
import com.qualcomm.vuforia.samples.SampleApplication.utils.LineShaders;
import com.qualcomm.vuforia.samples.SampleApplication.utils.SampleUtils;
import com.qualcomm.vuforia.samples.VuforiaSamples.R;

import java.util.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.InputSource;


// The renderer class for the ImageTargets sample. 
public class TextRecoRenderer implements GLSurfaceView.Renderer
{
    private static final String LOGTAG = "TextRecoRenderer";
    
    private SampleApplicationSession vuforiaAppSession;
    
    private static final int MAX_NB_WORDS = 132;
    private static final float TEXTBOX_PADDING = 0.0f;
    
    private static final float ROIVertices[] = { -0.5f, -0.5f, 0.0f, 0.5f,
            -0.5f, 0.0f, 0.5f, 0.5f, 0.0f, -0.5f, 0.5f, 0.0f };
    
    private static final int NUM_QUAD_OBJECT_INDICES = 8;
    private static final short ROIIndices[] = { 0, 1, 1, 2, 2, 3, 3, 0 };
    
    private static final float quadVertices[] = { -0.5f, -0.5f, 0.0f, 0.5f,
            -0.5f, 0.0f, 0.5f, 0.5f, 0.0f, -0.5f, 0.5f, 0.0f, };
    
    private static final short quadIndices[] = { 0, 1, 1, 2, 2, 3, 3, 0 };
    
    private ByteBuffer mROIVerts = null;
    private ByteBuffer mROIIndices = null;
    
    public boolean mIsActive = false;
    
    // Reference to main activity *
    public TextReco mActivity;
    
    private int shaderProgramID;
    
    private int vertexHandle;
    
    private int mvpMatrixHandle;
    
    private Renderer mRenderer;
    
    private int lineOpacityHandle;
    
    private int lineColorHandle;
    
    private List<WordDesc> mWords = new ArrayList<WordDesc>();
    public float ROICenterX;
    public float ROICenterY;
    public float ROIWidth;
    public float ROIHeight;
    private int viewportPosition_x;
    private int viewportPosition_y;
    private int viewportSize_x;
    private int viewportSize_y;
    private ByteBuffer mQuadVerts;
    private ByteBuffer mQuadIndices;
    
    boolean ready = false;
    Obb2D obb;
    Vec2F wordBoxSize = null;
    TrackableResult result;
    public static Map<String, String[]> wordCache;
    String definition[] = null;
    static boolean monitorState = false;
    static final Object monitor = new Object();
    Handler handler;
    String last_word = null;
    
    
    public TextRecoRenderer(TextReco activity, SampleApplicationSession session, Handler handler)
    {
        mActivity = activity;
        vuforiaAppSession = session;
        this.handler = handler;
        
        if(wordCache == null)
        {
        	wordCache = new HashMap<String, String[]>();
        }
        
    }
    
    
    // Called when the surface is created or recreated.
    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config)
    {
        Log.d(LOGTAG, "GLRenderer.onSurfaceCreated");
        
        // Call function to initialize rendering:
        initRendering();
        
        // Call Vuforia function to (re)initialize rendering after first use
        // or after OpenGL ES context was lost (e.g. after onPause/onResume):
        vuforiaAppSession.onSurfaceCreated();
    }
    
    
    // Called to draw the current frame.
    @Override
    public void onDrawFrame(GL10 gl)
    {
        if (!mIsActive)
        {
            mWords.clear();
            mActivity.updateWordListUI(mWords);
            return;
        }
        
        // Call our function to render content
        renderFrame();
        
        List<WordDesc> words;
        synchronized (mWords)
        {
            words = new ArrayList<WordDesc>(mWords);
        }
        
        Collections.sort(words);
        
        // update UI - we copy the list to avoid concurrent modifications
        mActivity.updateWordListUI(new ArrayList<WordDesc>(words));
    }
    
    
    // Called when the surface changed size.
    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height)
    {
        Log.d(LOGTAG, "GLRenderer.onSurfaceChanged");
        
        mActivity.configureVideoBackgroundROI();
        
        // Call Vuforia function to handle render surface size changes:
        vuforiaAppSession.onSurfaceChanged(width, height);
    }
    
    
    // Function for initializing the renderer.
    private void initRendering()
    {
        // init the vert/inde buffers
        mROIVerts = ByteBuffer.allocateDirect(4 * ROIVertices.length);
        mROIVerts.order(ByteOrder.LITTLE_ENDIAN);
        updateROIVertByteBuffer();
        
        mROIIndices = ByteBuffer.allocateDirect(2 * ROIIndices.length);
        mROIIndices.order(ByteOrder.LITTLE_ENDIAN);
        for (short s : ROIIndices)
            mROIIndices.putShort(s);
        mROIIndices.rewind();
        
        mQuadVerts = ByteBuffer.allocateDirect(4 * quadVertices.length);
        mQuadVerts.order(ByteOrder.LITTLE_ENDIAN);
        for (float f : quadVertices)
            mQuadVerts.putFloat(f);
        mQuadVerts.rewind();
        
        mQuadIndices = ByteBuffer.allocateDirect(2 * quadIndices.length);
        mQuadIndices.order(ByteOrder.LITTLE_ENDIAN);
        for (short s : quadIndices)
            mQuadIndices.putShort(s);
        mQuadIndices.rewind();
        
        mRenderer = Renderer.getInstance();
        
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, Vuforia.requiresAlpha() ? 0.0f
            : 1.0f);
        
        shaderProgramID = SampleUtils.createProgramFromShaderSrc(
            LineShaders.LINE_VERTEX_SHADER, LineShaders.LINE_FRAGMENT_SHADER);
        
        vertexHandle = GLES20.glGetAttribLocation(shaderProgramID,
            "vertexPosition");
        mvpMatrixHandle = GLES20.glGetUniformLocation(shaderProgramID,
            "modelViewProjectionMatrix");
        
        lineOpacityHandle = GLES20.glGetUniformLocation(shaderProgramID,
            "opacity");
        lineColorHandle = GLES20.glGetUniformLocation(shaderProgramID, "color");
        
    }
    
    
    private void updateROIVertByteBuffer()
    {
        mROIVerts.rewind();
        for (float f : ROIVertices)
            mROIVerts.putFloat(f);
        mROIVerts.rewind();
    }
    
    public static void waitForThread()
    {
    	monitorState = true;
    	while(monitorState)
    	{
    		synchronized(monitor)
    		{
    			try
    			{
    				monitor.wait();
    			}
    			catch(Exception e)
    			{
    				
    			}
    		}
    	}
    }
    
    public static void unlockWaiter()
    {
    	synchronized(monitor)
    	{
    		monitorState = false;
    		monitor.notifyAll();
    	}
    }
    
   
    
    // The render function.
    public void renderFrame()
    {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        
        State state = mRenderer.begin();
        mRenderer.drawVideoBackground();
        
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        
        // handle face culling, we need to detect if we are using reflection
        // to determine the direction of the culling
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_BACK);
        if (Renderer.getInstance().getVideoBackgroundConfig().getReflection() == VIDEO_BACKGROUND_REFLECTION.VIDEO_BACKGROUND_REFLECTION_ON)
        {
            GLES20.glFrontFace(GLES20.GL_CW);  // Front camera
        } else
        {
            GLES20.glFrontFace(GLES20.GL_CCW);   // Back camera
        }
        
        // enable blending to support transparency
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,
            GLES20.GL_ONE_MINUS_CONSTANT_ALPHA);
        
        // clear words list
        mWords.clear();
        
        // did we find any trackables this frame?
        int num_track = state.getNumTrackableResults();
        for (int tIdx = 0; tIdx < num_track; tIdx++)
        {
        	if(num_track > 1)
        	{
        		num_track = 1;
        	}
            // get the trackable
            result = state.getTrackableResult(tIdx);
            
            
            
            if (result.isOfType(WordResult.getClassType()))
            {
                WordResult wordResult = (WordResult) result;
                Word word = (Word) wordResult.getTrackable();
                obb = wordResult.getObb();
                wordBoxSize = word.getSize();
                
                String wordU = word.getStringU();
                
                
                
                //if(!wordU.toLowerCase().equals(last_word))
                //{
                	String ret[] = null;
                	if((ret = wordCache.get(wordU.toLowerCase())) == null)
                	{
                		new GetDictionaryListing().execute(wordU.toLowerCase());
                		waitForThread();
                	}
                //}
                
                	try
                	{
                		if(ret == null)
                		{
                			ret = definition;
                		}
                		
                		if(last_word == null)
                		{
                			last_word = wordU;
                		}
                		if(!wordU.equals(last_word))
                		{
	                		ParseQuery<ParseObject> query = ParseQuery.getQuery("TestObject");
	                        query.whereEqualTo("Word", wordU.toUpperCase());
	                        final String lastWord = wordU;
	                        query.findInBackground(new FindCallback<ParseObject>() {
	                            public void done(List<ParseObject> scoreList, com.parse.ParseException e)
	                            {
	                                if(scoreList != null && scoreList.size() >= 1)
	                                {
	                                    scoreList.get(0).increment("Count");
	                                    scoreList.get(0).saveInBackground();
	                          
	                                }
	                                else
	                                {
	                                    ParseObject score = new ParseObject("TestObject");
	                                    score.put("Word", lastWord.toUpperCase());
	                                    score.put("Count", 1);
	                                    score.saveInBackground();
	                           
	                                }
	                            }
	                        });
	                        
	                        last_word = wordU;
                		}
                		
	                	Message msgObj = handler.obtainMessage();
	                    Bundle b = new Bundle();
	                    b.putStringArray("def", ret);
	                    msgObj.setData(b);
	                    handler.sendMessage(msgObj);
                	}
                	catch(Exception e)
                	{
                		e.printStackTrace();
                	}
                
                if (wordU != null)
                {
                    // in portrait, the obb coordinate is based on
                    // a 0,0 position being in the upper right corner
                    // with :
                    // X growing from top to bottom and
                    // Y growing from right to left
                    //
                    // we convert those coordinates to be more natural
                    // with our application:
                    // - 0,0 is the upper left corner
                    // - X grows from left to right
                    // - Y grows from top to bottom
                    float wordx = -obb.getCenter().getData()[1];
                    float wordy = obb.getCenter().getData()[0];
                    
                    if (mWords.size() < MAX_NB_WORDS)
                    {
                        mWords.add(new WordDesc(wordU,
                            (int) (wordx - wordBoxSize.getData()[0] / 2),
                            (int) (wordy - wordBoxSize.getData()[1] / 2),
                            (int) (wordx + wordBoxSize.getData()[0] / 2),
                            (int) (wordy + wordBoxSize.getData()[1] / 2)));
                    }
                    
                }
                
            }
            else
            {
                Log.d(LOGTAG, "Unexpected Detection : " + result.getType());
                continue;
            }
            
            Matrix44F mvMat44f = Tool.convertPose2GLMatrix(result.getPose());
            float[] mvMat = mvMat44f.getData();
            float[] mvpMat = new float[16];
            Matrix.translateM(mvMat, 0, 0, 0, 0);
            Matrix.scaleM(mvMat, 0, wordBoxSize.getData()[0] - TEXTBOX_PADDING,
                wordBoxSize.getData()[1] - TEXTBOX_PADDING, 1.0f);
            Matrix.multiplyMM(mvpMat, 0, vuforiaAppSession
                .getProjectionMatrix().getData(), 0, mvMat, 0);
            
            GLES20.glUseProgram(shaderProgramID);
            GLES20.glLineWidth(11.0f);
            GLES20.glVertexAttribPointer(vertexHandle, 3, GLES20.GL_FLOAT,
                false, 0, mQuadVerts);
            GLES20.glEnableVertexAttribArray(vertexHandle);
            GLES20.glUniform1f(lineOpacityHandle, 1.0f);
            GLES20.glUniform3f(lineColorHandle, 1.0f, 0.447f, 0.0f);
            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMat, 0);
            GLES20.glDrawElements(GLES20.GL_LINES, NUM_QUAD_OBJECT_INDICES,
                GLES20.GL_UNSIGNED_SHORT, mQuadIndices);
            GLES20.glDisableVertexAttribArray(vertexHandle);
            GLES20.glLineWidth(1.0f);
            GLES20.glUseProgram(0);
        }
        
        // Draw the region of interest
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        
        drawRegionOfInterest(ROICenterX, ROICenterY, ROIWidth, ROIHeight);
        
        GLES20.glDisable(GLES20.GL_BLEND);
        
        mRenderer.end();
        
        
    }
    
    
    public void setROI(float center_x, float center_y, float width, float height)
    {
        ROICenterX = center_x;
        ROICenterY = center_y;
        ROIWidth = width;
        ROIHeight = height;
    }
    
    
    static String fromShortArray(short[] str)
    {
        StringBuilder result = new StringBuilder();
        for (short c : str)
            result.appendCodePoint(c);
        return result.toString();
    }
    
    
    public void setViewport(int vpX, int vpY, int vpSizeX, int vpSizeY)
    {
        viewportPosition_x = vpX;
        viewportPosition_y = vpY;
        viewportSize_x = vpSizeX;
        viewportSize_y = vpSizeY;
    }
    
    
    private void drawRegionOfInterest(float center_x, float center_y,
        float width, float height)
    {
        // assumption is that center_x, center_y, width and height are given
        // here in screen coordinates (screen pixels)
        float[] orthProj = new float[16];
        setOrthoMatrix(0.0f, (float) viewportSize_x, (float) viewportSize_y,
            0.0f, -1.0f, 1.0f, orthProj);
        
        // compute coordinates
        float minX = center_x - width / 2;
        float maxX = center_x + width / 2;
        float minY = center_y - height / 2;
        float maxY = center_y + height / 2;
        
        // Update vertex coordinates of ROI rectangle
        ROIVertices[0] = minX - viewportPosition_x;
        ROIVertices[1] = minY - viewportPosition_y;
        ROIVertices[2] = 0;
        
        ROIVertices[3] = maxX - viewportPosition_x;
        ROIVertices[4] = minY - viewportPosition_y;
        ROIVertices[5] = 0;
        
        ROIVertices[6] = maxX - viewportPosition_x;
        ROIVertices[7] = maxY - viewportPosition_y;
        ROIVertices[8] = 0;
        
        ROIVertices[9] = minX - viewportPosition_x;
        ROIVertices[10] = maxY - viewportPosition_y;
        ROIVertices[11] = 0;
        
        updateROIVertByteBuffer();
        
        GLES20.glUseProgram(shaderProgramID);
        GLES20.glLineWidth(3.0f);
        
        GLES20.glVertexAttribPointer(vertexHandle, 3, GLES20.GL_FLOAT, false,
            0, mROIVerts);
        GLES20.glEnableVertexAttribArray(vertexHandle);
        
        GLES20.glUniform1f(lineOpacityHandle, 1.0f); // 0.35f);
        GLES20.glUniform3f(lineColorHandle, 0.0f, 1.0f, 0.0f);// R,G,B
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, orthProj, 0);
        
        // Then, we issue the render call
        GLES20.glDrawElements(GLES20.GL_LINES, NUM_QUAD_OBJECT_INDICES,
            GLES20.GL_UNSIGNED_SHORT, mROIIndices);
        
        // Disable the vertex array handle
        GLES20.glDisableVertexAttribArray(vertexHandle);
        
        // Restore default line width
        GLES20.glLineWidth(1.0f);
        
        // Unbind shader program
        GLES20.glUseProgram(0);
    }
    
    class WordDesc implements Comparable<WordDesc>
    {
        public WordDesc(String text, int aX, int aY, int bX, int bY)
        {
            this.text = text;
            this.Ax = aX;
            this.Ay = aY;
            this.Bx = bX;
            this.By = bY;
        }
        
        String text;
        int Ax, Ay, Bx, By;
        
        
        @Override
        public int compareTo(WordDesc w2)
        {
            WordDesc w1 = this;
            int ret = 0;
            
            // we check first if both words are on the same line
            // both words are said to be on the same line if the
            // mid point (on Y axis) of the first point
            // is between the values of the second point
            int mid1Y = (w1.Ay + w1.By) / 2;
            
            if ((mid1Y < w2.By) && (mid1Y > w2.Ay))
            {
                // words are on the same line
                ret = w1.Ax - w2.Ax;
            } else
            {
                // words on different line
                ret = w1.Ay - w2.Ay;
            }
            Log.e(LOGTAG, "Compare result> " + ret);
            return ret;
        }
    }
    
    
    private void setOrthoMatrix(float nLeft, float nRight, float nBottom,
        float nTop, float nNear, float nFar, float[] _ROIOrthoProjMatrix)
    {
        for (int i = 0; i < 16; i++)
            _ROIOrthoProjMatrix[i] = 0.0f;
        
        _ROIOrthoProjMatrix[0] = 2.0f / (nRight - nLeft);
        _ROIOrthoProjMatrix[5] = 2.0f / (nTop - nBottom);
        _ROIOrthoProjMatrix[10] = 2.0f / (nNear - nFar);
        _ROIOrthoProjMatrix[12] = -(nRight + nLeft) / (nRight - nLeft);
        _ROIOrthoProjMatrix[13] = -(nTop + nBottom) / (nTop - nBottom);
        _ROIOrthoProjMatrix[14] = (nFar + nNear) / (nFar - nNear);
        _ROIOrthoProjMatrix[15] = 1.0f;
        
    }
    
    
    
    private class GetDictionaryListing extends AsyncTask<String, Void, Void>
    {
    	public static final String API_KEY = "cd3ce78c-7dff-41fa-b9a2-a5fd0824287c";
    	public static final String DICT_URL = "http://www.dictionaryapi.com/api/v1/references/sd2/xml/"; //school?key="
    	
    	protected Void doInBackground(String... params)
        {
	    		try
	    		{
	    			URL url = new URL(DICT_URL + params[0] + "?key=" + API_KEY);
	    			URLConnection conn = url.openConnection();
	    			String line;
	    			
	    			String def = null;
	    			String pos = null;
	    			String soundpath = null;
	    			BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
	    			while((line = in.readLine()) != null)
	    			{
	    				if(line.contains("<entry id"))
	    				{
	    				 	  pos = line.substring(line.indexOf("<fl>") + 4);
	    				 	  //System.out.println(pos);
	    				 	  pos = pos.substring(0, pos.indexOf("</fl>"));
	    				 	  //System.out.println(pos);
	    				 	  
	    				 	  soundpath = line.substring(line.indexOf("<wav>") + 5);
	    				 	  //System.out.println(soundpath);
	    				 	  soundpath = soundpath.substring(0, soundpath.indexOf("</wav>"));
	    				 	  //System.out.println(soundpath);
	    				 	  
	    				 	  def = line.substring(line.indexOf("<dt>") + 5);
	    				 	  //System.out.println(def);
	    					  def = def.substring(0,def.indexOf("<"));
	    					  //System.out.println(def);
	    					  break;
	    				}
	    			}
	    				 
	    				 	    			
	    				 	    		System.out.println(def + ", " + pos + ", " + soundpath);
	    				 	    		
	    				 	    			if(def != null && pos != null && soundpath != null)
	    				 	    			{
	    				 	    				
	    				 	    				String data[] = new String[4];
	    				 	    				data[0] = def;
	    				 	    				data[1] = pos;
	    				 	    				data[2] = soundpath;
	    				 	    				data[3] = params[0];
	    				 	    				wordCache.put(params[0], data);
	    				 	    				definition = data;
	    				 	    				
	    				 	    			}
	    		    
	    		}
	    		catch(Exception e)
	    		{
	    			e.printStackTrace();
	    		}
    		return null;
        }
    	
    	protected void onPostExecute(Void result)
    	{
    		unlockWaiter();
    	}
    	

    }
    
}
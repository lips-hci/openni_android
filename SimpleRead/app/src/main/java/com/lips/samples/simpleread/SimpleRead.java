package com.lips.samples.simpleread;

import org.openni.*;

import java.io.File;
import java.util.Locale;

import android.util.Log;

/***********************************
 * OpenNI related executions.
 ***********************************/

class SimpleRead
{
    private final String TAG = getClass().getSimpleName();
    private final String TAG_DATA = TAG + "_DATA";
    private static final String SAMPLE_XML_FILE = "SamplesConfig.xml";

    private OutArg<ScriptNode> scriptNode;
    private Context context;
    private DepthGenerator depthGen;

    private static long updateStartTime = 0;

    SimpleRead(File activityFileDir)
    {
        try
        {
            scriptNode = new OutArg<ScriptNode>();
            String xmlName = activityFileDir + File.separator + SAMPLE_XML_FILE;
            context = Context.createFromXmlFile(xmlName, scriptNode);
            depthGen = DepthGenerator.create(context);
            updateStartTime = 0;
        }
        catch(GeneralException e)
        {
            Log.e(TAG, "Constructor failed.", e.fillInStackTrace());
            System.exit(1);
        }
    }

    void cleanup()
    {
        Log.d(TAG, "Start Cleanup");

        try
        {
            context.stopGeneratingAll();
        }
        catch ( StatusException e )
        {
            Log.e( TAG, "Exception!", e );
        }

        scriptNode.value.dispose();
        scriptNode = null;
        depthGen.dispose();
        depthGen = null;
        context.dispose();
        context = null;

        Log.d(TAG, "Cleanup Done");
    }

    String updateDepth()
    {
        long timePerFrame;
        float fps = 0;

        try
        {
            long currentTime = System.currentTimeMillis();
            timePerFrame = currentTime - updateStartTime;
            updateStartTime = currentTime;

            context.waitAndUpdateAll();
            DepthMetaData depthMD = depthGen.getMetaData();

            int x = depthMD.getFullXRes() / 2;
            int y = depthMD.getFullYRes() / 2;
            short depth = depthMD.getData().readPixel(x, y);

            if(timePerFrame > 0) fps = 1.0f / ((float)timePerFrame / 1000.0f);
            String result = String.format(Locale.ENGLISH, "[Frame %03d] Depth of center point: %5d mm, FPS: %5.2f\n", depthMD.getFrameID(), depth, fps);

            // Print depth data in log
            Log.v(TAG_DATA, result);

            return result;
        }
        catch(StatusException e)
        {
            Log.e(TAG, "Depth update failed.", e.fillInStackTrace());
        }

        return null;
    }
}

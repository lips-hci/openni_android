package com.lips.samples.registrationviewer;

import android.graphics.Bitmap;
import android.util.Log;

import org.openni.*;

import java.io.File;

public class RegistrationViewer
{
    private class BitmapBuffer
    {
        int[] pixels;

        BitmapBuffer( int x, int y )
        {
            pixels = new int[x * y];
        }

        void obtainPixels( boolean isDepth )
        {
            NativeMethods.readLocalBitmapToJavaBuffer( isDepth, pixels );
        }
    }

    private static final String SAMPLE_XML_FILE = "SamplesConfig.xml";
    private final String TAG = getClass().getSimpleName();

    private OutArg<ScriptNode> scriptNode;
    private Context context;
    private DepthGenerator depthGen;
    private ImageGenerator imageGen;
    private int status;

    private BitmapBuffer lastDepthBuffer, lastImageBuffer;
    private BitmapBuffer[] tempDepthBuffer, tempImageBuffer;
    private int writeDepthIndex, writeImageIndex;
    private boolean hasNewDepth = false, hasNewImage = false;
    private final Object swapLockDepth = new Object();
    private final Object swapLockImage = new Object();

    private Bitmap depthBitmap, imageBitmap;
    int depthWidth = 640, depthHeight = 480;
    int imageWidth = 640, imageHeight = 480;

    RegistrationViewer( File file )
    {
        try
        {
            scriptNode = new OutArg<>();
            String xmlName = file + File.separator + SAMPLE_XML_FILE;
            context = Context.createFromXmlFile( xmlName, scriptNode );

            depthGen = DepthGenerator.create( context );
            DepthMetaData depthMD = depthGen.getMetaData();
            depthWidth = depthMD.getFullXRes();
            depthHeight = depthMD.getFullYRes();
            tempDepthBuffer = new BitmapBuffer[]
            {
                new BitmapBuffer( depthWidth, depthHeight ),
                new BitmapBuffer( depthWidth, depthHeight )
            };
            writeDepthIndex = 0;
            depthBitmap = Bitmap.createBitmap( depthWidth, depthHeight, Bitmap.Config.ARGB_8888 );

            imageGen = ImageGenerator.create( context );
            ImageMetaData imageMD = imageGen.getMetaData();
            imageWidth = imageMD.getFullXRes();
            imageHeight = imageMD.getFullYRes();
            tempImageBuffer = new BitmapBuffer[]
            {
                new BitmapBuffer( imageWidth, imageHeight ),
                new BitmapBuffer( imageWidth, imageHeight )
            };
            writeImageIndex = 0;
            imageBitmap = Bitmap.createBitmap( imageWidth, imageHeight, Bitmap.Config.ARGB_8888 );

            status = NativeMethods.initFromContext( context.toNative() );
            WrapperUtils.throwOnError( status );

            // Set depth registration ( depth to image )
            depthGen.getAlternativeViewpointCapability().setViewpoint( imageGen );

            context.startGeneratingAll();
        }
        catch ( GeneralException e )
        {
            e.fillInStackTrace();
            Log.e( TAG, e.toString() );
            System.exit( 1 );
        }
    }

    void cleanup()
    {
        Log.d( TAG, "Cleanup" );

        try
        {
            context.stopGeneratingAll();

            scriptNode.value.dispose();
            scriptNode = null;

            depthGen.dispose();
            depthGen = null;

            imageGen.dispose();
            imageGen = null;

            status = NativeMethods.dispose();
            WrapperUtils.throwOnError( status );

            context.dispose();
            context = null;
        }
        catch( StatusException e )
        {
            Log.e( TAG, e.toString() );
        }

        Log.d( TAG, "Cleanup Done" );
    }

    double getFPS( boolean isDepth ) throws StatusException
    {
        OutArg<Double> fps = new OutArg<>();
        status = NativeMethods.getRuntimeFPS( isDepth, fps );
        WrapperUtils.throwOnError( status );

        return fps.value;
    }

    void updateData() throws StatusException
    {
        BitmapBuffer currentBuffer;
        context.waitAndUpdateAll();

        // Update depth
        currentBuffer = tempDepthBuffer[writeDepthIndex];
        status = NativeMethods.generateBitmapLocalBuffer( true );
        WrapperUtils.throwOnError( status );
        currentBuffer.obtainPixels( true );
        synchronized ( swapLockDepth )
        {
            hasNewDepth = true;
            lastDepthBuffer = currentBuffer;
            writeDepthIndex++;
            writeDepthIndex %= 2;
            swapLockDepth.notify();
        }

        // Update Image
        currentBuffer = tempImageBuffer[writeImageIndex];
        status = NativeMethods.generateBitmapLocalBuffer( false );
        WrapperUtils.throwOnError( status );
        currentBuffer.obtainPixels( false );
        synchronized ( swapLockImage )
        {
            hasNewImage = true;
            lastImageBuffer = currentBuffer;
            writeImageIndex++;
            writeImageIndex %= 2;
            swapLockImage.notify();
        }
    }

    void drawBitmap( Viewer depthView, Viewer imageView ) // input: screen
    {
        int[] depthPixels;
        synchronized ( swapLockDepth )
        {
            while ( !hasNewDepth )
            {
                try
                {
                    swapLockDepth.wait();
                }
                catch ( InterruptedException e )
                {
                    // Don't care. Do nothing here.
                }
            }
            depthPixels = lastDepthBuffer.pixels;
        }
        depthBitmap.setPixels( depthPixels, 0, depthWidth, 0, 0, depthWidth, depthHeight );
        depthView.setBitmap( depthBitmap );

        int[] imagePixels;
        synchronized ( swapLockImage )
        {
            while ( !hasNewImage )
            {
                try
                {
                    swapLockImage.wait();
                }
                catch ( InterruptedException e )
                {
                    // Don't care. Do nothing here.
                }
            }
            imagePixels = lastImageBuffer.pixels;
        }
        imageBitmap.setPixels( imagePixels, 0, imageWidth, 0, 0, imageWidth, imageHeight );
        imageView.setBitmap( imageBitmap );
    }
}

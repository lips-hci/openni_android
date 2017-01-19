package com.lips.samples.simpleread;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;

public class SimpleReadActivity extends Activity
{
    private final String TAG = getClass().getSimpleName();
    private final String TAG_USB = TAG + "_USB";
    private final String ACTION_USB_PERMISSION = "com.lips.samples.simpleread.USB_DEVICE_PERMISSION";
    private final String ACTION_USB_ATTACHED = "android.hardware.usb.action.USB_DEVICE_ATTACHED";
    private final String ACTION_USB_DETACHED = "android.hardware.usb.action.USB_DEVICE_DETACHED";
    private final String ASSETS_DIR_PREFIX = "openni";
    private final int USB_PERMISSION_GOT = 0;

    private UsbManager mUsbManager;
    private UsbDevice mToFCamera, mRGBCamera;
    private boolean bToFCameraFound = false, bRGBCameraFound = false;
    private PendingIntent mPermissionIntent;
    private IntentFilter mUsbFilter;

    // [PrimeSense/Xtion] 1d27:0601 / 1d27:0600
    private final String sPS_VID = "1D27";
    private final String sPS_PID1 = "601";
    private final String sPS_PID2 = "600";

    // [Generic LIPS Camera] (ToF)05c8:022b / (RGB) 05c8:0422
    private final String sLIPS_VID = "5C8";
    private final String sLIPS_ToF_PID = "22B";
    private final String sLIPS_RGB_PID = "422";
    private final int TOF_CAMERA = 1;
    private final int RGB_CAMERA = 2;

    private boolean isSimpleReadInitialized = false;
    private Thread simpleReadThread;
    private boolean keepRunning = true;

    private SimpleRead simpleRead;
    private TextView textShowDepth;
    private ScrollView scrollView;
    private static String depthInfo;


    //--------------------- Getting USB Permission ---------------------//
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();
            Log.d(TAG_USB, "mUsbReceiver::onReceive - " + action);
            if(ACTION_USB_PERMISSION.equals(action))
            {
                synchronized(this)
                {
                    UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
                    {
                        if(device != null)
                        {
                            Log.i(TAG_USB, "Permission granted by user!");

                            // Proceed with USB connection.
                            connectToCamera(device);

                            // Check permission of both cameras
                            if(mUsbManager.hasPermission(mToFCamera) && mUsbManager.hasPermission(mRGBCamera))
                            {
                                // Continue to start main program
                                Message msg = new Message();
                                msg.what = USB_PERMISSION_GOT;
                                mHandler.sendMessage(msg);
                            }
                            else
                            {
                                // Keep requesting the permission of other camera
                                tryRequestCamera();
                            }
                        }
                    }
                }
            }
        }
    };

    private final BroadcastReceiver mUsbAttachDetachReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();
            if(ACTION_USB_ATTACHED.equals(action))
            {
                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                Log.d(TAG_USB, "USB device attached: " + device.getProductId());
            }
            else if(ACTION_USB_DETACHED.equals(action))
            {
                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                Log.d(TAG_USB, "USB device detached: " + device.getProductId());
            }
        }
    };

    private boolean requestUsbPermission(UsbDevice device)
    {
        if(!mUsbManager.hasPermission(device))
        {
            Log.d(TAG_USB, "Try to request " + device.getProductId() + " permission.");
            mUsbManager.requestPermission(device, mPermissionIntent);
        }
        else
        {
            // User granted. This USB device can be accessed.
            Log.d(TAG_USB, "USB device " + device.getProductId() + " permission already granted!");
            return true; // USB permission granted.
        }
        return false; // Wait for user's permission.
    }

    private boolean waitPermissionOfCamera(int id) throws InterruptedException
    {
        boolean result = false;
        final int MAX_LOOP = 30; // Max total waiting time: 15s.
        final int WAIT_MS = 500; // 500 ms
        int counter = MAX_LOOP;

        do
        {
            if(counter-- <= 0) break;
            Thread.sleep(WAIT_MS);

            switch(id)
            {
                case TOF_CAMERA:
                    result = mUsbManager.hasPermission(mToFCamera);
                    break;
                case RGB_CAMERA:
                    result = mUsbManager.hasPermission(mRGBCamera);
                    break;
                default:
                    break;
            }
            Log.d(TAG_USB, "Wait for USB permission from user (" + (id == TOF_CAMERA ? "ToF" : "RGB") + ":" + result + ")... counter left " + counter);
        }while (!result);

        if(result)
        {
            return true;
        }
        else
        {
            Log.w(TAG_USB, "Timeout! Give up waiting.");
            return false;
        }
    }

    private boolean tryRequestCamera()
    {
        boolean result = true;

        if(bToFCameraFound)
        {
            result &= requestUsbPermission(mToFCamera);
        }
        if(bRGBCameraFound)
        {
            result &= requestUsbPermission(mRGBCamera);
        }

        // True: permission and connection of both USB device got.
        // False: Still need to wait for USB permission.
        return result;
    }

    private boolean connectToCamera(UsbDevice device)
    {
        boolean isConnect = false;

        // Make connection
        UsbDeviceConnection connection = mUsbManager.openDevice(device);
        if(connection != null)
        {
            // Get USB id
            int fd = connection.getFileDescriptor();
            Log.d(TAG_USB, "Get fd " + fd + " from device " + device.getProductId());
            isConnect = true;
        }
        else
        {
            Log.e(TAG_USB, "UsbManager cannot get fd from device " + device.getProductId());
            isConnect = false;
        }

        return isConnect;
    }

    private Handler mHandler = new Handler()
    {
        @Override
        public void handleMessage(Message msg)
        {
            if(msg.what == USB_PERMISSION_GOT)
            {
                Log.d(TAG, "Handler got USB_PERMISSION_GOT message: " + msg.what);
                startSimpleRead();
            }
        }
    };

    private class UsbMonitorAsyncTask extends AsyncTask<String, Integer, Boolean>
    {
        private ProgressDialog dialog;

        @Override
        protected Boolean doInBackground(String... param)
        {
            // Do something wasting time here. //
            boolean result = true;

            // Try asking USB permission
            if(tryRequestCamera())
            {
                result &= connectToCamera(mToFCamera);
                result &= connectToCamera(mRGBCamera);

                // If already got permissions, continue to start main program.
                if(result)
                {
                    Message msg = new Message();
                    msg.what = USB_PERMISSION_GOT;
                    mHandler.sendMessage(msg);
                }
            }
            else
            {
                // Ask UsbManager and wait for pending intent
                try
                {
                    if(bToFCameraFound)
                    {
                        result &= waitPermissionOfCamera(TOF_CAMERA);
                    }
                    if(bRGBCameraFound)
                    {
                        result &= waitPermissionOfCamera(RGB_CAMERA);
                    }
                }
                catch(Exception e)
                {
                    e.printStackTrace();
                }
            }

            return result;
        }

        @Override
        protected void onPostExecute(Boolean result)
        {
            super.onPostExecute(result);

            // Close the loading dialog.
            dialog.hide();
            dialog.dismiss();
        }

        @Override
        protected void onProgressUpdate(Integer... values)
        {
            super.onProgressUpdate(values);

            // Set the current progress of the dialog.
            dialog.setProgress(values[0]);
        }

        @Override
        protected void onPreExecute()
        {
            super.onPreExecute();
            dialog = ProgressDialog.show(SimpleReadActivity.this, "Starting...", "Open camera, please wait...", false, false);
        }
    }


    //-------------------- Assets Loading Functions --------------------//
    private void loadDataFromAssets()
    {
        Log.d(TAG, "Start assets loading...");

        try
        {
            // Copy "assets/openni/*" to "files/*"
            String[] contents = getAssets().list(ASSETS_DIR_PREFIX);

            // Recurse on the contents.
            for(String entry : contents)
            {
                copyAsset(entry);
            }
        }
        catch(IOException e)
        {
            Log.e(TAG, "Assets loading failed.", e.fillInStackTrace());
            System.exit(1);
        }

        Log.d(TAG, "Assets loading completed!");
    }

    private void copyAsset(String path)
    {
        // If the path is to a directory, make it.
        // If the path is to a file, its contents are copied.
        try
        {
            String[] contents = getAssets().list(ASSETS_DIR_PREFIX + File.separator + path);

            // Assume the path is to a file if the returned array is null or its length equals to 0.
            // This means empty directories will get turned into files.
            if(contents == null || contents.length == 0)
            {
                throw new IOException();
            }

            // Make the directory.
            Log.d(TAG, "Copy recursively: " + path + File.separator);
            File dir = new File(getFilesDir(), path);
            dir.mkdirs();

            // Recurse on the contents.
            for(String entry : contents)
            {
                copyAsset(path + File.separator + entry);
            }
        }
        catch(IOException e)
        {
            Log.d(TAG, "Copy file: " + path);
            copyFileAsset(path);
        }
    }

    private void copyFileAsset(String filename)
    {
        try
        {
            InputStream inStream = getAssets().open(ASSETS_DIR_PREFIX + File.separator + filename);

            File fOut = new File(getFilesDir(), filename);
            FileOutputStream outStream = new FileOutputStream(fOut.getAbsolutePath());
            byte[] buffer = new byte[1024];
            int read = inStream.read(buffer);
            while(read != -1)
            {
                outStream.write(buffer, 0, read);
                read = inStream.read(buffer);
            }
            outStream.close();
            inStream.close();
        }
        catch(IOException e)
        {
            Log.e(TAG, "Failed to copy asset files.", e.fillInStackTrace());
        }
    }


    //------- Main Program Functions (OpenNI Related Executions) -------//
    private void startSimpleRead()
    {
        initSimpleRead();

        //Start main loop of SimpleRead
        keepRunning = true;
        simpleReadThread = new Thread()
        {
            public void run()
            {
                while(keepRunning)
                {
                    depthInfo = simpleRead.updateDepth();
                    if(depthInfo == null)
                    {
                        continue;
                    }

                    runOnUiThread(new Runnable()
                    {
                        public void run()
                        {
                            textShowDepth.append(depthInfo);
                            scrollView.fullScroll(ScrollView.FOCUS_DOWN);
                        }
                    });
                }
                Log.i(TAG, "SimpleRead MainLoop finished.");

                /*if(!keepRunning)
                {
                    return;
                }*/
            }
        };
        simpleReadThread.setName("SimpleRead MainLoop Thread");
        simpleReadThread.start();
    }

    private synchronized void initSimpleRead()
    {
        if(isSimpleReadInitialized)
        {
            // The program has been already initialized.
            return;
        }

        setContentView(R.layout.main);
        textShowDepth = (TextView) findViewById(R.id.textShowDepth);
        scrollView = (ScrollView) findViewById(R.id.scrollView);
        simpleRead = new SimpleRead(SimpleReadActivity.this.getFilesDir());

        isSimpleReadInitialized = true;
    }

    private synchronized void terminateSimpleRead()
    {
        if(!isSimpleReadInitialized)
        {
            return;
        }

        keepRunning = false;
        while(simpleReadThread != null)
        {
            try
            {
                simpleReadThread.join();
                simpleReadThread = null;
                break;
            }
            catch(InterruptedException e)
            {
                // Don't care. Do nothing here.
            }
        }

        // Start clean up.
        isSimpleReadInitialized = false;
        simpleRead.cleanup();
    }


    //------------------- Activity Related Functions -------------------//
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        Log.d(TAG, "onCreate start");

        loadDataFromAssets();
        super.onCreate(savedInstanceState);

        // USB permission intent receiver registration
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        mUsbFilter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(mUsbReceiver, mUsbFilter);

        // USB attach/detach events intent receiver registration
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_ATTACHED);
        filter.addAction(ACTION_USB_DETACHED);
        registerReceiver(mUsbAttachDetachReceiver, filter);

        try
        {
            if(ACTION_USB_ATTACHED.equalsIgnoreCase(getIntent().getAction()))
            {
                Intent intent = getIntent();
                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                Log.d(TAG_USB, "USB device attached: " + device.getProductId());
            }
            else if(ACTION_USB_DETACHED.equalsIgnoreCase(getIntent().getAction()))
            {
                Intent intent = getIntent();
                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                Log.d(TAG_USB, "USB device detached: " + device.getProductId());
            }

            // Print USB device list
            mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
            HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
            Log.d(TAG_USB, deviceList.size() + " USB device(s) found.");

            Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
            while(deviceIterator.hasNext())
            {
                UsbDevice device = deviceIterator.next();
                Log.d(TAG_USB, "device: " + device.getDeviceName() + " " + device.getVendorId() + " " + device.getProductId());

                String VID = Integer.toHexString(device.getVendorId()).toUpperCase();
                String PID = Integer.toHexString(device.getProductId()).toUpperCase();
                Log.d(TAG_USB, "VID = " + VID + "/ PID = " + PID);

                // Recognize USB Devices
                if(VID.equalsIgnoreCase(sLIPS_VID))
                {
                    if(PID.equalsIgnoreCase(sLIPS_ToF_PID))
                    {
                        mToFCamera = device;
                        bToFCameraFound = true;
                    }
                    else if(PID.equalsIgnoreCase(sLIPS_RGB_PID))
                    {
                        mRGBCamera = device;
                        bRGBCameraFound = true;
                    }
                    else
                    {
                        Log.e(TAG, "Unrecognized camera module (" + device.getProductId() + "). Please contact vendor.");
                        continue;
                    }
                }
                else if(VID.equalsIgnoreCase(sPS_VID))
                {
                    // Allow PrimeSense device
                    if(PID.equalsIgnoreCase(sPS_PID1) || PID.equalsIgnoreCase(sPS_PID2))
                    {
                        mToFCamera = device;
                        bToFCameraFound = true;

                        // Assign RGB to the same device
                        mRGBCamera = device;
                        bRGBCameraFound = true;
                    }
                }
                else
                {
                    Log.w(TAG, "Not supported product. Ignore it.");
                    continue;
                }
            } // End of while
            Log.d(TAG, "onCreate done");
        }
        catch(Exception e)
        {
            Log.e(TAG, "onCreate failed", e.fillInStackTrace());
            finish();
            return;
        }
    }

    @Override
    protected void onNewIntent(Intent intent)
    {
        Log.d(TAG, "onNewIntent");
        super.onNewIntent(intent);
        setIntent(intent);

        if(ACTION_USB_ATTACHED.equalsIgnoreCase(intent.getAction()))
        {
            UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            Log.d(TAG_USB, "USB device attached: " + device.getProductId());
        }
        else if(ACTION_USB_DETACHED.equalsIgnoreCase(intent.getAction()))
        {
            UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            Log.d(TAG_USB, "USB device detached: " + device.getProductId());
        }
    }

    @Override
    protected void onStart()
    {
        Log.i(TAG, "onStart");
        super.onStart();

        if(!bRGBCameraFound || !bToFCameraFound)
        {
            Log.w(TAG, "Cannot find valid USB camera, try again!");
            finish();
            return;
        }

        // Try AsyncTask method
        UsbMonitorAsyncTask usbMonitor = new UsbMonitorAsyncTask();
        usbMonitor.execute();
    }

    @Override
    protected void onStop()
    {
        Log.i(TAG, "onStop");
        super.onStop();

        terminateSimpleRead();
        Log.d(TAG, "Triggering finish()...");
        finish();
    }

    @Override
    protected void onDestroy()
    {
        Log.d(TAG, "onDestroy");
        super.onDestroy();

        unregisterReceiver(mUsbReceiver);
        unregisterReceiver(mUsbAttachDetachReceiver);
    }

    @Override
    protected void onPause()
    {
        Log.d(TAG, "onPause");
        super.onPause();
    }

    @Override
    protected void onResume()
    {
        Log.d(TAG, "onResume");
        super.onResume();
    }

    @Override
    protected void onRestart()
    {
        Log.d(TAG, "onRestart");
        super.onRestart();
    }
}

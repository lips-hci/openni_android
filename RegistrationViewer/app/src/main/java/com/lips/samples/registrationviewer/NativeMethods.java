package com.lips.samples.registrationviewer;

import org.openni.OutArg;

public class NativeMethods
{
    static
    {
        System.loadLibrary("RegistrationViewer.jni");
    }

    static native int initFromContext( long pContext );

    static native int dispose();

    static native int generateBitmapLocalBuffer( boolean isDepth );

    static native int readLocalBitmapToJavaBuffer( boolean isDepth, int[] javaBuffer );

    static native int getRuntimeFPS( boolean isDepth, OutArg<Double> fps );
}
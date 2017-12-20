#include <jni.h>
#include <XnOpenNI.h>
#include <XnCppWrapper.h>
#include <XnFPSCalculator.h>
#include <android/log.h>

#define TAG "RegistrationViewerJNI"
#define  LOGD(x...)  __android_log_print(ANDROID_LOG_DEBUG, TAG, x)

using namespace xn;

extern "C" {

/*************************************************************************
 *                               Variable                                *
 *************************************************************************/
Context *mContext = 0;
DepthGenerator mDepthGen;
ImageGenerator mImageGen;
DepthMetaData depthMD;
ImageMetaData imageMD;

int *depthBuffer = 0;
int *imageBuffer = 0;
size_t BUFFER_SIZE  = sizeof( XnUInt32 ) * 640 * 480;
size_t MAX_DEPTH = 10000;
size_t HISTOGRAM_SIZE = MAX_DEPTH * sizeof( float );
float *pHistogram = 0;

const XnDepthPixel *pDepth;
const XnRGB24Pixel *pImage;
XnUInt32 nDepthXRes, nDepthYRes;
XnUInt32 nImageXRes, nImageYRes;
XnFPSData depthFPS, imageFPS;


/*************************************************************************
 *                                Helpers                                *
 *************************************************************************/
void SetOutArgObjectValue( JNIEnv* env, jobject p, jobject value )
{
    jclass cls = env->GetObjectClass( p );
    jfieldID fieldID = env->GetFieldID( cls, "value", "Ljava/lang/Object;" );
    env->SetObjectField( p, fieldID, value );
}

void SetOutArgDoubleValue( JNIEnv* env, jobject p, double value )
{
    jclass cls = env->FindClass( "java/lang/Double" );
    jmethodID ctor = env->GetMethodID( cls, "<init>", "(D)V" );
    SetOutArgObjectValue( env, p, env->NewObject( cls, ctor, value ) );
}


/*************************************************************************
 *                          Internal Functions                           *
 *************************************************************************/
XnStatus initGraphics()
{
    depthBuffer = ( int* ) malloc( BUFFER_SIZE );
    pHistogram =  ( float* ) malloc( HISTOGRAM_SIZE );
    imageBuffer = ( int* ) malloc( BUFFER_SIZE );
    if ( !depthBuffer || !pHistogram || !imageBuffer )
    {
        return XN_STATUS_ALLOC_FAILED;
    }
    memset( depthBuffer, 0, BUFFER_SIZE );
    memset( pHistogram,  0, HISTOGRAM_SIZE );
    memset( imageBuffer, 0, BUFFER_SIZE );

    return XN_STATUS_OK;
}

void disposeGraphics()
{
    free( depthBuffer );
    free( pHistogram );
    free( imageBuffer );
}

void calculateHistogram()
{
    unsigned int depthValue = 0;
    unsigned int numberOfPoints = 0;

    // Calculate the accumulative histogram
    memset( pHistogram, 0, HISTOGRAM_SIZE );
    for ( int y = 0 ; y < nDepthYRes ; y++ )
    {
        for ( int x = 0 ; x < nDepthXRes ; x++ )
        {
            depthValue = *pDepth;

            // Array index range CHECK and FIX!
            if ( depthValue < 0 )
            {
                depthValue = 0;
            }
            else if ( depthValue >= MAX_DEPTH )
            {
                depthValue = MAX_DEPTH - 1;
            }

            if ( depthValue > 0 )
            {
                pHistogram[depthValue]++;
                numberOfPoints++;
            }

            pDepth++;
        }
    }

    for ( int i = 1 ; i < MAX_DEPTH ; i++ )
    {
        pHistogram[i] += pHistogram[i - 1];
    }

    if ( numberOfPoints )
    {
        for ( int i = 1 ; i < MAX_DEPTH ; i++ )
        {
            pHistogram[i] = ( unsigned int )( 256 * ( 1.0f - ( pHistogram[i] / numberOfPoints ) ) );
        }
    }
}

void prepareDepth()
{
    mDepthGen.GetMetaData( depthMD );
    nDepthXRes = depthMD.XRes();
    nDepthYRes = depthMD.YRes();

    pDepth = depthMD.Data();
    calculateHistogram();

    // Rewind the pointer
    pDepth = depthMD.Data();
}

void fillDepthBitmap( int *dstBuffer )
{
    unsigned int depthValue = 0;
    float histValue = 0;
    unsigned char *pDstImage = ( unsigned char* )dstBuffer;

    // Prepare the texture map
    for ( int i = 0 ; i < ( nDepthXRes * nDepthYRes ) ; i++, pDstImage += 4 )
    {
        pDstImage[0] = 0;   // B
        pDstImage[1] = 0;   // G
        pDstImage[2] = 0;   // R
        pDstImage[3] = 255; // A

        // Prevent crash from null pointer
        if ( pDepth == 0 || pDepth == NULL || pDepth == nullptr )
        {
            continue;
        }

        depthValue = *pDepth;
        // Array index CHECK and FIX!
        if ( depthValue < 0 )
        {
            depthValue = 0;
        }
        else if ( depthValue >= MAX_DEPTH )
        {
            depthValue = MAX_DEPTH - 1;
        }

        if ( depthValue != 0 )
        {
            histValue = pHistogram[depthValue];

            pDstImage[0] = ( unsigned char )histValue;
            pDstImage[1] = ( unsigned char )histValue;
            pDstImage[2] = ( unsigned char )histValue;
        }

        pDepth++;
    }
}

void prepareImage()
{
    mImageGen.GetMetaData( imageMD );
    nImageXRes = imageMD.XRes();
    nImageYRes = imageMD.YRes();

    pImage = imageMD.RGB24Data();
}

void fillImageBitmap( int *dstBuffer )
{
    XnRGB24Pixel imagePixel;
    unsigned char *pDstImage = ( unsigned char* )dstBuffer;

    // Prepare the texture map
    for ( int i = 0 ; i < ( nImageXRes * nImageYRes ) ; i++, pDstImage += 4 )
    {
        pDstImage[0] = 0;   // B
        pDstImage[1] = 0;   // G
        pDstImage[2] = 0;   // R
        pDstImage[3] = 255; // A

        // Prevent crash from null pointer
        if ( pImage == 0 || pImage == NULL || pImage == nullptr )
        {
            continue;
        }

        imagePixel = *pImage;
        pDstImage[0] = ( unsigned char )imagePixel.nBlue;
        pDstImage[1] = ( unsigned char )imagePixel.nGreen;
        pDstImage[2] = ( unsigned char )imagePixel.nRed;

        pImage++;
    }
}


/*************************************************************************
 *                             JNI Functions                             *
 *************************************************************************/

/*
 * initFromContext
 */
JNIEXPORT jint JNICALL
Java_com_lips_samples_registrationviewer_NativeMethods_initFromContext( JNIEnv *env, jclass type, jlong pContext )
{
    LOGD( "init start..." );

    XnStatus status;
    mContext = new Context( ( XnContext* ) pContext );

    // Find depth node
    status = mContext->FindExistingNode( XN_NODE_TYPE_DEPTH, mDepthGen );
    if ( status != XN_STATUS_OK )
    {
        xnPrintError( status, "No depth node exists! Check your XML." );
        return status;
    }
    mDepthGen.GetMetaData( depthMD );

    // Find image node
    status = mContext->FindExistingNode( XN_NODE_TYPE_IMAGE, mImageGen );
    if ( status != XN_STATUS_OK )
    {
        xnPrintError( status, "No image node exists!" );
        return status;
    }
    mImageGen.GetMetaData( imageMD );

    // init buffers
    status = initGraphics();
    if ( status != XN_STATUS_OK )
    {
        xnPrintError( status, "Failed to initialize graphic buffers." );
        return status;
    }

    xnFPSInit( &depthFPS, 100 );
    xnFPSInit( &imageFPS, 100 );

    LOGD( "init end." );
    return XN_STATUS_OK;
}

/*
 * dispose
 */
JNIEXPORT jint JNICALL
Java_com_lips_samples_registrationviewer_NativeMethods_dispose( JNIEnv *env, jclass type )
{
    LOGD( "dispose start..." );

    disposeGraphics();

    mDepthGen.Release();
    mImageGen.Release();

    mContext->Release();
    delete mContext;
    mContext = 0;

    LOGD( "dispose end." );
    return XN_STATUS_OK;
}

/*
 * generateBitmapLocalBuffer
 */
JNIEXPORT jint JNICALL
Java_com_lips_samples_registrationviewer_NativeMethods_generateBitmapLocalBuffer( JNIEnv *env, jclass type, jboolean isDepth )
{
    if ( isDepth )
    {
        prepareDepth();
        fillDepthBitmap( depthBuffer );
        xnFPSMarkFrame( &depthFPS );
    }
    else
    {
        prepareImage();
        fillImageBitmap( imageBuffer );
        xnFPSMarkFrame( &imageFPS );
    }

    return XN_STATUS_OK;
}

/*
 * readLocalBitmapToJavaBuffer
 */
JNIEXPORT jint JNICALL
Java_com_lips_samples_registrationviewer_NativeMethods_readLocalBitmapToJavaBuffer( JNIEnv *env, jclass type, jboolean isDepth, jintArray javaBuffer_ )
{
    jboolean isCopy;
    jint *javaBuffer = env->GetIntArrayElements( javaBuffer_, &isCopy );
    jsize length = env->GetArrayLength( javaBuffer_ );

    if ( isCopy )
    {
        LOGD( "Copied!" );
    }

    jsize min = length * sizeof( jint );
    if ( min > BUFFER_SIZE )
    {
        min = BUFFER_SIZE;
    }

    if ( isDepth )
    {
        memcpy( javaBuffer, depthBuffer, ( size_t )min );
    }
    else
    {
        memcpy( javaBuffer, imageBuffer, ( size_t )min );
    }

    env->ReleaseIntArrayElements(javaBuffer_, javaBuffer, 0);
    return XN_STATUS_OK;
}

/*
 * getRuntimeFPS
 */
JNIEXPORT jint JNICALL
Java_com_lips_samples_registrationviewer_NativeMethods_getRuntimeFPS( JNIEnv *env, jclass type, jboolean isDepth, jobject fps )
{
    XnDouble calFPS;

    if ( isDepth )
    {
        calFPS = xnFPSCalc( &depthFPS );
    }
    else
    {
        calFPS = xnFPSCalc( &imageFPS );
    }

    SetOutArgDoubleValue( env, fps, calFPS );

    return XN_STATUS_OK;
}

} // End of extern "C"
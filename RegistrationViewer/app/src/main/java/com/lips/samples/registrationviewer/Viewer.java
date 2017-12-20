package com.lips.samples.registrationviewer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Viewer extends View
{
    abstract class BaseView
    {
        Paint paint;
        public abstract void draw( Canvas canvas );
    }

    class Label extends BaseView
    {
        static final float FONT_SIZE = 20;
        private float x, y;
        private String text;

        Label( String text, float x, float y, int color )
        {
            this.x = x;
            this.y = y;
            this.text = text;

            paint = new Paint();
            paint.setTextSize( FONT_SIZE * magicRatio );
            paint.setColor( color );
        }

        public void draw( Canvas canvas )
        {
            canvas.drawText( text, x * magicRatio, y * magicRatio, paint );
        }
    }

    class Frame extends BaseView
    {
        private float x0, y0, x1, y1;

        Frame( float left, float top, float width, float height, int color )
        {
            this.x0 = left;
            this.y0 = top;
            this.x1 = left + width;
            this.y1 = top + height;

            paint = new Paint();
            paint.setStrokeWidth( 3 );
            paint.setColor( color );
        }

        public void draw( Canvas canvas )
        {
            float[] array =
            {
                x0, y0, x1, y0,
                x1, y0, x1, y1,
                x1, y1, x0, y1,
                x0, y1, x0, y0,
            };
            canvas.drawLines( array, 0, 16, paint );
        }
    }

    private final Object viewerLock = new Object();
    private List<BaseView> viewsToDraw, viewsToPrepare;
    private Bitmap bitmap;

    private int currentColor = Color.WHITE;
    private float magicRatio = 1;
    private int inputWidth = 640, inputHeight = 480;

    public Viewer( Context context )
    {
        super( context );
        viewsToDraw = new ArrayList<>();
        viewsToPrepare = new ArrayList<>();
        bitmap = BitmapFactory.decodeResource( getResources(), R.mipmap.ic_launcher );
    }

    public void setDimensions( int width, int height )
    {
        inputWidth = width;
        inputHeight = height;
    }

    @Override
    protected void onMeasure( int widthMeasureSpec, int heightMeasureSpec )
    {
        super.onMeasure( widthMeasureSpec, heightMeasureSpec );

        float measuredWidth, measuredHeight;
        measuredWidth = View.MeasureSpec.getSize( widthMeasureSpec );
        measuredHeight = View.MeasureSpec.getSize( heightMeasureSpec );
        magicRatio = Math.min( ( measuredWidth / inputWidth ), ( measuredHeight / inputHeight ) );
    }

    @Override
    protected void onDraw( Canvas canvas )
    {
        Matrix matrix = new Matrix();
        matrix.setScale( magicRatio, magicRatio );

        if ( bitmap != null )
        {
            canvas.drawBitmap( bitmap, matrix, null );
        }

        synchronized ( viewerLock )
        {
            for ( BaseView view : viewsToDraw )
            {
                view.draw( canvas );
            }
        }

        Frame frame = new Frame( 0, 0, ( inputWidth * magicRatio ), ( inputHeight * magicRatio ), Color.YELLOW );
        frame.draw( canvas );
    }

    public void setBitmap( Bitmap bitmap )
    {
        this.bitmap = bitmap;
        this.postInvalidate();
    }

    public void drawLabel( String text, float x, float y )
    {
        viewsToPrepare.add( new Label( text, x, y, currentColor ) );
    }

    public void setColor( int color )
    {
        currentColor = color;
    }

    public void reDraw( double fps )
    {
        // Show FPS
        setColor( Color.WHITE );
        drawLabel( String.format( Locale.ENGLISH, "FPS: %.1f", fps ), ( 2 * magicRatio ), ( float )( Label.FONT_SIZE * magicRatio * 1.5 ) );

        synchronized ( viewerLock )
        {
            viewsToDraw.clear();
            viewsToDraw.addAll( viewsToPrepare );
            viewsToPrepare.clear();
        }

        this.postInvalidate();
    }
}

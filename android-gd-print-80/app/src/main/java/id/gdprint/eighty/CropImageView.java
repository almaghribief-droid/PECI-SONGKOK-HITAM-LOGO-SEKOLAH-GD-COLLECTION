package id.gdprint.eighty;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;

public class CropImageView extends View {
    private Bitmap bitmap;
    private final Paint imagePaint = new Paint(3);
    private final Paint shadePaint = new Paint(3);
    private final Paint borderPaint = new Paint(3);
    private final Paint handlePaint = new Paint(3);
    private final RectF imageRect = new RectF();
    private final RectF crop = new RectF();
    private float downX, downY;
    private boolean resizing, moving;
    private float scale, offsetX, offsetY;

    public CropImageView(Context c) {
        super(c);
        shadePaint.setColor(0x99000000);
        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(3f);
        handlePaint.setColor(0xFF20A4F3);
        handlePaint.setStyle(Paint.Style.FILL);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public void setBitmap(Bitmap b) {
        bitmap = b;
        if (b != null) crop.set(0.06f, 0.06f, 0.94f, 0.94f);
        invalidate();
    }

    public Bitmap getCroppedBitmap() {
        if (bitmap == null) return null;
        int l=Math.max(0, Math.min(bitmap.getWidth()-1, Math.round(crop.left*bitmap.getWidth())));
        int t=Math.max(0, Math.min(bitmap.getHeight()-1, Math.round(crop.top*bitmap.getHeight())));
        int r=Math.max(l+1, Math.min(bitmap.getWidth(), Math.round(crop.right*bitmap.getWidth())));
        int b=Math.max(t+1, Math.min(bitmap.getHeight(), Math.round(crop.bottom*bitmap.getHeight())));
        return Bitmap.createBitmap(bitmap,l,t,r-l,b-t);
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        c.drawColor(0xFF182536);
        if(bitmap==null) return;
        float sx=getWidth()/(float)bitmap.getWidth();
        float sy=getHeight()/(float)bitmap.getHeight();
        scale=Math.min(sx,sy);
        float w=bitmap.getWidth()*scale, h=bitmap.getHeight()*scale;
        offsetX=(getWidth()-w)/2f; offsetY=(getHeight()-h)/2f;
        imageRect.set(offsetX,offsetY,offsetX+w,offsetY+h);
        c.drawBitmap(bitmap,null,imageRect,imagePaint);
        RectF cr=new RectF(offsetX+crop.left*w,offsetY+crop.top*h,offsetX+crop.right*w,offsetY+crop.bottom*h);
        c.save();
        Path outside=new Path(); outside.addRect(imageRect,Path.Direction.CW);
        Path inside=new Path(); inside.addRect(cr,Path.Direction.CW);
        outside.op(inside,Path.Op.DIFFERENCE);
        c.drawPath(outside,shadePaint);
        c.drawRect(cr,borderPaint);
        float rad=12f;
        c.drawCircle(cr.left,cr.top,rad,handlePaint);
        c.drawCircle(cr.right,cr.top,rad,handlePaint);
        c.drawCircle(cr.left,cr.bottom,rad,handlePaint);
        c.drawCircle(cr.right,cr.bottom,rad,handlePaint);
        c.restore();
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if(bitmap==null) return false;
        float w=imageRect.width(), h=imageRect.height();
        if(w<=0||h<=0) return false;
        float nx=(e.getX()-offsetX)/w, ny=(e.getY()-offsetY)/h;
        if(e.getAction()==MotionEvent.ACTION_DOWN) {
            downX=nx; downY=ny;
            resizing=Math.abs(nx-crop.right)<0.10f && Math.abs(ny-crop.bottom)<0.10f;
            moving=!resizing && crop.contains(nx,ny);
            return true;
        } else if(e.getAction()==MotionEvent.ACTION_MOVE) {
            float dx=nx-downX, dy=ny-downY;
            if(resizing) {
                crop.right=Math.max(crop.left+0.08f,Math.min(1f,nx));
                crop.bottom=Math.max(crop.top+0.08f,Math.min(1f,ny));
            } else if(moving) {
                float cw=crop.width(), ch=crop.height();
                float left=Math.max(0,Math.min(1-cw,crop.left+dx));
                float top=Math.max(0,Math.min(1-ch,crop.top+dy));
                crop.set(left,top,left+cw,top+ch);
            }
            downX=nx; downY=ny; invalidate(); return true;
        } else if(e.getAction()==MotionEvent.ACTION_UP) {
            resizing=false; moving=false; performClick(); return true;
        }
        return true;
    }

    @Override public boolean performClick() { super.performClick(); return true; }
}

package id.gdprint.eighty;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;

public class CropImageView extends View {
    private Bitmap bitmap;
    private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint shadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF imageRect = new RectF();
    private final RectF crop = new RectF(0f,0f,1f,1f);
    private float downX, downY, offsetX, offsetY;
    private int activeCorner = -1;
    private boolean moving;
    private static final float MIN_CROP = 0.02f;

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
        resetCrop();
    }

    public void resetCrop() {
        crop.set(0f,0f,1f,1f);
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
        float scale=Math.min(getWidth()/(float)bitmap.getWidth(),getHeight()/(float)bitmap.getHeight());
        float w=bitmap.getWidth()*scale, h=bitmap.getHeight()*scale;
        offsetX=(getWidth()-w)/2f; offsetY=(getHeight()-h)/2f;
        imageRect.set(offsetX,offsetY,offsetX+w,offsetY+h);
        c.drawBitmap(bitmap,null,imageRect,imagePaint);
        RectF cr=toViewRect();
        Path outside=new Path(); outside.addRect(imageRect,Path.Direction.CW);
        Path inside=new Path(); inside.addRect(cr,Path.Direction.CW);
        outside.op(inside,Path.Op.DIFFERENCE);
        c.drawPath(outside,shadePaint);
        c.drawRect(cr,borderPaint);
        float rad=dp(9);
        c.drawCircle(cr.left,cr.top,rad,handlePaint);
        c.drawCircle(cr.right,cr.top,rad,handlePaint);
        c.drawCircle(cr.left,cr.bottom,rad,handlePaint);
        c.drawCircle(cr.right,cr.bottom,rad,handlePaint);
    }

    private float dp(float v){return v*getResources().getDisplayMetrics().density;}
    private RectF toViewRect(){
        float w=imageRect.width(),h=imageRect.height();
        return new RectF(offsetX+crop.left*w,offsetY+crop.top*h,
            offsetX+crop.right*w,offsetY+crop.bottom*h);
    }
    private float clamp(float v){return Math.max(0f,Math.min(1f,v));}

    @Override public boolean onTouchEvent(MotionEvent e) {
        if(bitmap==null) return false;
        float w=imageRect.width(), h=imageRect.height();
        if(w<=0||h<=0) return false;
        float nx=clamp((e.getX()-offsetX)/w), ny=clamp((e.getY()-offsetY)/h);
        if(e.getAction()==MotionEvent.ACTION_DOWN) {
            downX=nx; downY=ny;
            float threshold=dp(24);
            RectF cr=toViewRect();
            float[][] corners={{cr.left,cr.top},{cr.right,cr.top},{cr.left,cr.bottom},{cr.right,cr.bottom}};
            activeCorner=-1; float best=threshold;
            for(int i=0;i<4;i++){
                float dx=e.getX()-corners[i][0],dy=e.getY()-corners[i][1];
                float dist=(float)Math.sqrt(dx*dx+dy*dy);
                if(dist<best){best=dist;activeCorner=i;}
            }
            moving=activeCorner<0&&crop.contains(nx,ny);
            return true;
        } else if(e.getAction()==MotionEvent.ACTION_MOVE) {
            if(activeCorner>=0){
                float minX=MIN_CROP,minY=MIN_CROP;
                if(activeCorner==0){crop.left=Math.min(nx,crop.right-minX);crop.top=Math.min(ny,crop.bottom-minY);}
                if(activeCorner==1){crop.right=Math.max(nx,crop.left+minX);crop.top=Math.min(ny,crop.bottom-minY);}
                if(activeCorner==2){crop.left=Math.min(nx,crop.right-minX);crop.bottom=Math.max(ny,crop.top+minY);}
                if(activeCorner==3){crop.right=Math.max(nx,crop.left+minX);crop.bottom=Math.max(ny,crop.top+minY);}
                crop.left=clamp(crop.left);crop.top=clamp(crop.top);
                crop.right=clamp(crop.right);crop.bottom=clamp(crop.bottom);
            } else if(moving) {
                float dx=nx-downX,dy=ny-downY,cw=crop.width(),ch=crop.height();
                float left=Math.max(0f,Math.min(1f-cw,crop.left+dx));
                float top=Math.max(0f,Math.min(1f-ch,crop.top+dy));
                crop.set(left,top,left+cw,top+ch);
            }
            downX=nx;downY=ny;invalidate();return true;
        } else if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL) {
            activeCorner=-1;moving=false;performClick();return true;
        }
        return true;
    }

    @Override public boolean performClick(){super.performClick();return true;}
}

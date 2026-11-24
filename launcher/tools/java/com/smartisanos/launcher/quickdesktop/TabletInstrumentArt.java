package com.smartisanos.launcher.quickdesktop;

import android.graphics.*;
import java.util.Random;

/** Static surfaces are rasterized once per size or reading; only the charging glint moves. */
final class TabletInstrumentArt {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private Bitmap layer;
    private final RectF batteryCore = new RectF();
    private int lastKind, lastKey;

    void draw(Canvas out,int w,int h,int kind,int key) {
        // Two-cell tiles can be large on external displays. Bound each cached surface.
        float scale=Math.min(1f,1024f/Math.max(w,h));
        int bw=Math.max(1,Math.round(w*scale)), bh=Math.max(1,Math.round(h*scale));
        if(layer==null || layer.getWidth()!=bw || layer.getHeight()!=bh) {
            layer=Bitmap.createBitmap(bw,bh,Bitmap.Config.ARGB_8888);
            lastKind=-1;
        }
        if(lastKind!=kind || lastKey!=key) {
            Canvas c=new Canvas(layer);
            if(kind==TabletTile.BATTERY) battery(c,bw,bh,key);
            else if(kind==TabletTile.STORAGE) drive(c,bw,bh);
            else weather(c,bw,bh,key);
            lastKind=kind; lastKey=key;
        }
        solid(Color.WHITE);p.setFilterBitmap(true);
        out.drawBitmap(layer,null,new Rect(0,0,w,h),p);
    }
    void release() { layer=null; }
    private void solid(int color) {p.setShader(null);p.setColor(color);}
    private void grad(float x,float y,float ex,float ey,int... colors) {
        solid(Color.WHITE);p.setShader(new LinearGradient(x,y,ex,ey,colors,null,Shader.TileMode.CLAMP));
    }
    private void glow(Canvas c,float x,float y,float r,int color) {
        solid(Color.WHITE);p.setShader(new RadialGradient(x,y,r,color,color&0xffffff,Shader.TileMode.CLAMP));
        c.drawCircle(x,y,r,p);
    }

    // Work in a vertical cell's coordinates, rotating the cylinder as a whole in wide slots.
    private static void batterySpace(Canvas c,float w,float h) {
        if(w>h) {c.translate(w,0);c.rotate(90);}
    }
    // All battery geometry is in upright coordinates. Only the terminal projects
    // past the top cap, leaving the underlying desktop visible at its shoulders.
    private void setBatteryCore(float width, float height) {
        float diameter = Math.min(width, height);
        float length = Math.max(width, height);
        float cap = diameter * .045f;
        float terminal = diameter * .035f;
        batteryCore.set(0, terminal + cap, diameter, length - cap);
    }

    private void battery(Canvas c, float w, float h, int percent) {
        c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        c.save();
        batterySpace(c, w, h);
        setBatteryCore(w, h);
        float diameter = batteryCore.width();
        float length = Math.max(w, h);
        float terminal = diameter * .035f;
        float radius = diameter * .024f;

        grad(diameter*.34f, 0, diameter*.66f, 0, 0xff666b6a, 0xffc9cdca, 0xff979d9a, 0xff565b59);
        c.drawRoundRect(diameter*.34f, 0, diameter*.66f, terminal + radius, radius*.5f, radius*.5f, p);
        grad(0, 0, diameter, 0, 0xff535a58, 0xffbbc0bd, 0xff7f8783, 0xffdaddd8, 0xff828885, 0xff535957);
        c.drawRoundRect(0, terminal, diameter, length, radius, radius, p);

        grad(0, 0, diameter, 0, 0xff303735, 0xff464f4a, 0xff69716b, 0xff838b83, 0xff59605c, 0xff343d38);
        c.drawRect(batteryCore, p);
        float level = batteryCore.bottom - batteryCore.height() * percent / 100f;
        grad(0, 0, diameter, 0, 0xff214a1b, 0xff337729, 0xff4b9737, 0xff84c05a, 0xff489534, 0xff306824, 0xff21471c);
        c.drawRect(0, level, diameter, batteryCore.bottom, p);
        float pitch = Math.max(2f, diameter * .0105f);
        solid(0x56000000);
        for (float y = batteryCore.bottom - pitch; y > batteryCore.top; y -= pitch) {
            c.drawRect(0, y, diameter, y + pitch*.30f, p);
        }
        float seam = Math.max(1f, diameter*.004f);
        solid(0x72ffffff);
        c.drawRect(0, batteryCore.top - seam, diameter, batteryCore.top, p);
        c.drawRect(0, batteryCore.bottom, diameter, batteryCore.bottom + seam, p);
        c.restore();
    }

    void chargingGlint(Canvas c, float w, float h, float fraction, long now) {
        c.save();
        batterySpace(c, w, h);
        setBatteryCore(w, h);
        float filled = batteryCore.height() * fraction;
        float y = batteryCore.bottom - filled * ((now % 2400L) / 2400f);
        float band = batteryCore.width() * .068f;
        c.clipRect(0, batteryCore.bottom - filled, batteryCore.right, batteryCore.bottom);
        grad(0, y - band, 0, y, 0x00a2f174, 0x3062ae39, 0x74a1e96f);
        c.drawRect(0, y - band, batteryCore.right, y, p);
        c.restore();
    }

    private void drive(Canvas c,float w,float h) {
        float u=Math.min(w,h), radius=.055f*u;
        c.drawColor(0xff272a2c);
        grad(0,0,w*.67f,h,0xffa6a6a4,0xff53595c,0xff969b99,0xff494d50);
        c.drawRoundRect(u*.018f,u*.018f,w-u*.018f,h-u*.018f,radius,radius,p);
        grad(0,0,w,h,0xff62696b,0xff343b3e,0xff52595c);
        c.drawRoundRect(u*.032f,u*.032f,w-u*.032f,h-u*.032f,radius,radius,p);
        boolean wide=w>h;
        float cx=wide?w*.235f:w*.50f,cy=wide?h*.49f:h*.285f;
        float r=Math.min((wide?w:h)*.195f,u*.390f);
        solid(0xff171b1d);c.drawCircle(cx,cy+r*.038f,r*1.06f,p);
        solid(Color.WHITE);
        p.setShader(new SweepGradient(cx,cy,new int[]{0xffb6bfc2,0xff626f75,0xffd8d9cf,0xff919d9b,
                0xff677782,0xffe3e4df,0xff727b81,0xffb6bfc2},null));
        c.drawCircle(cx,cy,r,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(1,r*.008f));solid(0x28ffffff);
        for(int ring=0;ring<11;ring++) c.drawCircle(cx,cy,r*(.45f+ring*.048f),p);
        p.setStyle(Paint.Style.FILL);
        solid(0xff4a5255);c.drawCircle(cx,cy,r*.28f,p);
        grad(cx-r*.2f,cy-r*.2f,cx+r*.2f,cy+r*.22f,0xfff6f6ef,0xff889494,0xffc7c6bc);
        c.drawCircle(cx,cy,r*.225f,p);solid(0xff4c5457);c.drawCircle(cx,cy,r*.078f,p);
        float ax=cx+r*.77f,ay=cy+r*.77f;
        Path arm=new Path();arm.moveTo(ax-r*.11f,ay+r*.08f);arm.lineTo(cx+r*.02f,cy-r*.48f);
        arm.lineTo(cx+r*.16f,cy-r*.41f);arm.lineTo(ax+r*.14f,ay-r*.08f);arm.close();
        grad(cx,cy,ax,ay,0xffdedcd3,0xff6f7778,0xffc1c4bf);c.drawPath(arm,p);
        screw(c,ax,ay,r*.145f);
        float x=wide?w*.465f:u*.086f,y=wide?h*.113f:h*.54f;
        float right=w-u*.082f,bottom=h-u*.108f;
        solid(0xff373c3c);c.drawRoundRect(x-1,y,right+1,bottom+2,u*.018f,u*.018f,p);
        grad(x,y,right,bottom,0xfff0ede1,0xffdedbcf,0xffe7e3d8);
        c.drawRoundRect(x,y,right,bottom,u*.018f,u*.018f,p);
        for(float sx:new float[]{u*.066f,w-u*.066f})
            for(float sy:new float[]{u*.066f,h-u*.066f}) screw(c,sx,sy,u*.025f);
    }
    private void screw(Canvas c,float x,float y,float r) {
        solid(0xff2b3031);c.drawCircle(x,y+r*.12f,r*1.09f,p);
        grad(x-r,y-r,x+r,y+r,0xffd0d2cf,0xff898f90,0xff525a5c);c.drawCircle(x,y,r,p);
        solid(0xff485054);p.setStrokeWidth(Math.max(1,r*.23f));
        c.drawLine(x-r*.40f,y+r*.35f,x+r*.40f,y-r*.35f,p);
    }

    private void weather(Canvas c,float w,float h,int code) {
        int upper=0xff4b80c7,lower=0xff96bfee;
        switch(code) {
            case 1: upper=0xff6386b6;lower=0xffabc8e7;break;
            case 2: case 4: case 5: case 6: case 7: case 8: case 9:
                upper=0xff4c5b78;lower=0xff99accb;break;
            case 13: case 14: case 15: case 16: upper=0xff6594cc;lower=0xffb2d2eb;break;
            case 18: upper=0xff858394;lower=0xffc5bfbc;break;
            case 19: upper=0xff9e825c;lower=0xffddc5a2;break;
            case 0: break;
            default: upper=0xff647782;lower=0xffa0acb3;
        }
        grad(0,0,w*.85f,h*.65f,upper,lower);c.drawRect(0,0,w,h,p);
        Random rand=new Random(3961);
        float u=Math.min(w,h);
        if(code==0) {
            glow(c,w*.98f,-h*.07f,u*.86f,0x85fff5d9);
            for(int i=0;i<3;i++){
                solid(0x24f8fbef);c.drawCircle(w*(.78f+i*.095f),h*(.75f-i*.32f),u*(.045f+i*.025f),p);
            }
        } else if (code==1 || code==2 || code>=4 && code<=9 || code==18) {
            for(int i=0;i<36;i++) {
                float x=w*(.48f+rand.nextFloat()*.70f);
                float y=h*(.36f+.18f*(float)Math.sin(x/w*8)+rand.nextFloat()*.35f);
                glow(c,x,y,u*(.14f+.26f*rand.nextFloat()),code==1?0x46ffffff:0x2659687b);
            }
        }
        if (code>=4 && code<=9 || code>=13 && code<=16) {
            boolean snow=code>=13;
            for(int i=0;i<48;i++) {
                float x=rand.nextFloat()*w,y=rand.nextFloat()*h;
                solid(snow?0x9bf3f7ff:0x4496b0ce);
                if(snow)c.drawCircle(x,y,u*(.003f+.007f*rand.nextFloat()),p);
                else {p.setStrokeWidth(Math.max(1,u*.003f));c.drawLine(x,y,x-u*.032f,y+u*.094f,p);}
            }
        }
        // Keep white type legible even across the brightest sky.
        grad(0,0,w*.80f,0,0x640f253f,0x15122638,0x00122638);c.drawRect(0,0,w,h,p);
        solid(0x45ffffff);c.drawRect(0,0,w,1,p);
    }
}

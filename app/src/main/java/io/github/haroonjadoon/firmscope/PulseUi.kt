package io.github.haroonjadoon.firmscope

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.*
import android.view.*
import android.widget.*

class PulseIcon(context: Context, private val kind: String, private var tint: Int) : View(context) {
    fun setTint(color: Int) { tint=color;invalidate() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val side=minOf(width,height).toFloat();canvas.save();canvas.translate((width-side)/2,(height-side)/2);canvas.scale(side/24,side/24)
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=tint;style=Paint.Style.STROKE;strokeWidth=1.65f;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND }
        fun path(vararg points: Float) { val line=Path();line.moveTo(points[0],points[1]);for(i in 2 until points.size step 2)line.lineTo(points[i],points[i+1]);canvas.drawPath(line,p) }
        when(kind) {
            "home"->{ path(3f,11f,12f,3f,21f,11f);path(5f,10f,5f,21f,10f,21f,10f,15f,14f,15f,14f,21f,19f,21f,19f,10f) }
            "devices"->{canvas.drawRoundRect(6f,2f,18f,22f,2f,2f,p);canvas.drawLine(10f,18f,14f,18f,p)}
            "activity"->{canvas.drawCircle(12f,12f,9f,p);path(12f,6f,12f,12f,16f,14f)}
            "tools"->{path(4f,20f,15f,9f,19f,9f,21f,5f,17f,6f,17f,2f,13f,5f,13f,9f,2f,20f,4f,22f)}
            "settings"->{canvas.drawCircle(12f,12f,4f,p);canvas.drawCircle(12f,12f,8f,p);for(i in 0..7) { val a=i*Math.PI/4;canvas.drawLine((12+8*Math.cos(a)).toFloat(),(12+8*Math.sin(a)).toFloat(),(12+10*Math.cos(a)).toFloat(),(12+10*Math.sin(a)).toFloat(),p) }}
            "plus"->{path(12f,4f,12f,20f);path(4f,12f,20f,12f)}
            "search"->{canvas.drawCircle(10f,10f,6f,p);path(15f,15f,21f,21f)}
            "edit"->{path(5f,16f,16f,5f,20f,9f,9f,20f,4f,21f,5f,16f);path(14f,7f,18f,11f)}
            "info"->{canvas.drawCircle(12f,12f,9f,p);path(12f,11f,12f,17f);canvas.drawPoint(12f,7f,p)}
            "arrow"->path(9f,5f,16f,12f,9f,19f)
            "dropdown"->path(5f,9f,12f,16f,19f,9f)
            "back"->path(15f,5f,8f,12f,15f,19f)
            "refresh"->{canvas.drawArc(4f,4f,20f,20f,35f,280f,false,p);path(20f,3f,20f,9f,14f,9f)}
            "copy"->{canvas.drawRoundRect(8f,7f,21f,22f,2f,2f,p);path(16f,7f,16f,2f,3f,2f,3f,17f,8f,17f)}
            "bell"->{path(5f,17f,7f,14f,7f,8f,9f,4f,15f,4f,17f,8f,17f,14f,19f,17f,5f,17f);canvas.drawArc(10f,17f,14f,22f,0f,180f,false,p)}
            "share"->{canvas.drawCircle(5f,12f,2.5f,p);canvas.drawCircle(19f,5f,2.5f,p);canvas.drawCircle(19f,19f,2.5f,p);path(7f,11f,17f,6f);path(7f,13f,17f,18f)}
            else->{p.strokeWidth=2.5f;path(2f,13f,6f,13f,9f,5f,13f,20f,17f,10f,20f,13f,22f,13f);p.color=Color.rgb(165,237,176);path(13f,20f,17f,10f,20f,13f,22f,13f)}
        }
        canvas.restore()
    }
}

class PulseUi(val context: Context,val dark: Boolean) {
    val blue=Color.rgb(73,139,255)
    val blueInk=Color.parseColor(if(dark) "#B7D8FF" else "#145ABA")
    val ink=Color.parseColor(if(dark) "#F0F5FF" else "#112B48")
    val muted=Color.parseColor(if(dark) "#A2AEC1" else "#52657B")
    val background=Color.parseColor(if(dark) "#0A111D" else "#F0F3F8")
    val surface=Color.parseColor(if(dark) "#172231" else "#FFFFFF")
    val border=Color.parseColor(if(dark) "#344256" else "#DFE6EF")
    val warning=Color.parseColor(if(dark) "#FFD59B" else "#805015")
    val mint=Color.rgb(179,243,206)
    val mintInk=Color.rgb(20,77,49)
    fun dp(value: Int)=(value*context.resources.displayMetrics.density+.5f).toInt()
    fun col()=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL }
    fun row()=LinearLayout(context).apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL }
    fun margin(top: Int=0,bottom: Int=0)=LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(top);bottomMargin=dp(bottom) }
    fun weight()=LinearLayout.LayoutParams(0,-2,1f)
    fun label(value: String,size: Int=14,color: Int=ink,bold: Boolean=false)=TextView(context).apply {
        text=value;textSize=size.toFloat();setTextColor(color);includeFontPadding=false
        typeface=Typeface.create(if(bold) "sans-serif-medium" else "sans-serif",Typeface.NORMAL)
        setLineSpacing(dp(2).toFloat(),1f)
    }
    fun shape(kind: String="normal",radius: Int=22): GradientDrawable {
        val start=when(kind) { "blue"->if(dark) "#244C79" else "#E0EDFF";"mint"->if(dark) "#205345" else "#DDF7E8";"dock"->if(dark) "#E01E293B" else "#EDFFFFFF";else->if(dark) "#202C3D" else "#FFFFFF" }
        val end=when(kind) { "blue"->if(dark) "#142A47" else "#F5F9FF";"mint"->if(dark) "#15352E" else "#F3FFF7";"dock"->if(dark) "#E8101928" else "#EAF5F8FD";else->if(dark) "#151F2E" else "#FBFCFE" }
        return GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.parseColor(start),Color.parseColor(end))).apply {
            cornerRadius=dp(radius).toFloat();setStroke(1,when(kind) { "blue"->Color.parseColor(if(dark) "#5483BC" else "#BED6FA");"mint"->Color.parseColor(if(dark) "#4B9276" else "#BCE5CB");else->border })
        }
    }
    fun card(parent: LinearLayout,kind: String="normal")=col().apply {
        val base=shape(kind)
        background=if(kind=="blue"||kind=="mint") {
            val tint=if(kind=="blue") blue else Color.rgb(69,223,152)
            val glow=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.argb(if(dark)34 else 18,Color.red(tint),Color.green(tint),Color.blue(tint)),Color.TRANSPARENT)).apply {
                gradientType=GradientDrawable.RADIAL_GRADIENT;gradientRadius=dp(230).toFloat();setGradientCenter(.9f,.05f);cornerRadius=dp(22).toFloat()
            }
            LayerDrawable(arrayOf(base,glow))
        } else base
        setPadding(dp(16),dp(14),dp(16),dp(12));parent.addView(this,margin(0,10))
    }
    fun pill(value: String,released: Boolean=false)=label(value,11,if(released) mintInk else blueInk,true).apply {
        layoutParams=LinearLayout.LayoutParams(-2,-2)
        setPadding(dp(10),dp(6),dp(10),dp(6))
        background=GradientDrawable().apply { cornerRadius=dp(9).toFloat();setColor(if(released) mint else Color.parseColor(if(dark) "#183F70" else "#DFECFF")) }
    }
    fun action(value: String,primary: Boolean=false,onClick: ()->Unit)=label(value,14,if(primary) Color.WHITE else ink,true).apply {
        gravity=Gravity.CENTER;minHeight=dp(48);setPadding(dp(12),dp(10),dp(12),dp(10));isClickable=true;isFocusable=true
        val drawable=if(primary) GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,intArrayOf(Color.rgb(88,155,255),Color.rgb(47,109,231))).apply { cornerRadius=dp(18).toFloat() } else shape(radius=18)
        background=RippleDrawable(ColorStateList.valueOf(Color.argb(70,140,180,255)),drawable,null)
        setOnClickListener { onClick() };PulseMotion.press(this)
    }
    fun icon(kind: String,label: String,onClick: ()->Unit)=FrameLayout(context).apply {
        minimumWidth=dp(48);minimumHeight=dp(48);contentDescription=label;isClickable=true;isFocusable=true
        background=RippleDrawable(ColorStateList.valueOf(Color.argb(60,140,180,255)),if(kind=="info"||kind=="edit")null else shape(radius=18),shape(radius=18))
        addView(PulseIcon(context,kind,blueInk),FrameLayout.LayoutParams(dp(20),dp(20),Gravity.CENTER));setOnClickListener { onClick() };PulseMotion.press(this)
    }
    fun section(parent: LinearLayout,title: String,subtitle: String="") {
        parent.addView(label(title,25,ink,true),margin(4,14))
        if(subtitle.isNotEmpty()) parent.addView(label(subtitle,13,muted),margin(0,16))
    }
    fun line(parent: LinearLayout,name: String,value: String) {
        val r=row();r.addView(label(name,13,muted),weight());r.addView(label(value,13,ink,true),weight());parent.addView(r,margin(7,3))
    }
    fun setting(parent: LinearLayout,title: String,value: String="",onClick: ()->Unit) {
        val r=row().apply { minimumHeight=dp(50);isClickable=true;isFocusable=true;setOnClickListener { onClick() };PulseMotion.press(this)
            background=RippleDrawable(ColorStateList.valueOf(Color.argb(25,130,180,255)),null,shape(radius=10)) }
        r.addView(label(title,14),weight())
        if(value.isNotEmpty())r.addView(label(value,13,muted),LinearLayout.LayoutParams(-2,-2).apply { rightMargin=dp(8) })
        r.addView(PulseIcon(context,"arrow",muted),LinearLayout.LayoutParams(dp(16),dp(16)));parent.addView(r)
    }
    fun destructive(parent: LinearLayout,title: String,onClick: ()->Unit) {
        parent.addView(label(title,13,Color.parseColor(if(dark) "#FFB0AF" else "#B53C43")).apply {
            minHeight=dp(48);gravity=Gravity.CENTER_VERTICAL;isClickable=true;isFocusable=true
            setOnClickListener { onClick() };PulseMotion.press(this)
        })
    }
    fun tintSwitch(toggle: Switch) {
        val states=arrayOf(intArrayOf(android.R.attr.state_checked),intArrayOf())
        toggle.thumbTintList=ColorStateList(states,intArrayOf(Color.WHITE,Color.parseColor(if(dark) "#8C99AC" else "#FFFFFF")))
        toggle.trackTintList=ColorStateList(states,intArrayOf(blue,Color.parseColor(if(dark) "#3F4D62" else "#B8C4D3")))
        toggle.minimumHeight=dp(48);toggle.minimumWidth=dp(48)
    }
}

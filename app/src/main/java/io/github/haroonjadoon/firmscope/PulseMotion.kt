package io.github.haroonjadoon.firmscope

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Build
import android.view.*
import android.view.animation.Interpolator
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.cos
import kotlin.math.exp

/** Small, interruptible motions; respect both the app and Android animation settings. */
object PulseMotion {
    val settle = Interpolator { t -> if (t == 1f) 1f else (1 - exp(-7.5 * t) * cos(8.0 * t)).toFloat() }
    fun enabled(context: Context): Boolean = ValueAnimator.areAnimatorsEnabled() &&
        !context.getSharedPreferences("firmscope", Context.MODE_PRIVATE).getBoolean("reduceMotion", false)
    fun press(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> if(enabled(v.context)) v.animate().scaleX(.975f).scaleY(.975f).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if(enabled(v.context)) v.animate().scaleX(1f).scaleY(1f).setDuration(240).setInterpolator(settle).start() else { v.animate().cancel();v.scaleX=1f;v.scaleY=1f }
            }
            false // Preserve scrolling, click, keyboard and accessibility handling.
        }
    }
    fun enter(view: View, direction: Int) {
        view.animate().cancel()
        if (!enabled(view.context) || !view.isLaidOut) { view.alpha=1f;view.translationX=0f;return }
        view.alpha=.25f
        view.translationX=direction * 12f * view.resources.displayMetrics.density
        view.animate().alpha(1f).translationX(0f).setDuration(260).setInterpolator(settle).start()
    }
}

/** Captures only the scroll viewport behind the dock. Text/icons are never blurred. */
class PulseGlassDock(context: Context, private val ui: PulseUi) : FrameLayout(context) {
    private val backdrop=ImageView(context).apply {
        scaleType=ImageView.ScaleType.FIT_XY
        importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
        if(Build.VERSION.SDK_INT>=31) setRenderEffect(RenderEffect.createBlurEffect(ui.dp(14).toFloat(),ui.dp(14).toFloat(),Shader.TileMode.CLAMP))
    }
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val capsule=Path()
    private var selectedPosition=0f
    private var selection: ValueAnimator?=null
    private var pending=false
    private var bitmap: Bitmap?=null
    var glass=true
        set(value) { field=value;if(!value)backdrop.setImageDrawable(null);invalidate() }
    init {
        setWillNotDraw(false)
        clipToOutline=true
        background=ui.shape("dock",30)
        elevation=ui.dp(10).toFloat()
        addView(backdrop,LayoutParams(-1,-1))
    }
    fun select(index: Int, animate: Boolean) {
        selection?.cancel()
        if(!animate || !PulseMotion.enabled(context)) { selectedPosition=index.toFloat();invalidate();return }
        selection=ValueAnimator.ofFloat(selectedPosition,index.toFloat()).apply {
            duration=320;interpolator=PulseMotion.settle
            addUpdateListener { selectedPosition=it.animatedValue as Float;invalidate() }
            start()
        }
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w,h,oldw,oldh)
        capsule.reset()
        capsule.addRoundRect(0f,0f,w.toFloat(),h.toFloat(),ui.dp(30).toFloat(),ui.dp(30).toFloat(),Path.Direction.CW)
    }
    override fun dispatchDraw(canvas: Canvas) {
        val saved=canvas.save()
        canvas.clipPath(capsule)
        // The backdrop and tint are below the moving selection and sharp navigation.
        drawChild(canvas,backdrop,drawingTime)
        paint.color=Color.WHITE // Restore tint opacity after the previous frame's translucent border.
        paint.shader=LinearGradient(0f,0f,width.toFloat(),height.toFloat(),
            intArrayOf(Color.parseColor(if(ui.dark) "#DB182336" else "#EFFFFFFF"),Color.parseColor(if(ui.dark) "#E50C1524" else "#E4F3F6FC")),null,Shader.TileMode.CLAMP)
        if(!glass || Build.VERSION.SDK_INT<31) { paint.shader=null;paint.color=ui.surface }
        canvas.drawRoundRect(0f,0f,width.toFloat(),height.toFloat(),ui.dp(30).toFloat(),ui.dp(30).toFloat(),paint)
        paint.shader=null
        val inset=ui.dp(5).toFloat();val cell=(width-inset*2)/5f
        val left=inset+selectedPosition.coerceIn(0f,4f)*cell
        paint.color=Color.parseColor(if(ui.dark) "#D32D4668" else "#E8DCEAFF")
        canvas.drawRoundRect(left,inset,left+cell,height-inset,ui.dp(25).toFloat(),ui.dp(25).toFloat(),paint)
        paint.style=Paint.Style.STROKE;paint.strokeWidth=resources.displayMetrics.density*.6f
        paint.color=Color.parseColor(if(ui.dark) "#466CB3FF" else "#80FFFFFF")
        canvas.drawRoundRect(.5f,.5f,width-.5f,height-.5f,ui.dp(30).toFloat(),ui.dp(30).toFloat(),paint)
        paint.style=Paint.Style.FILL
        for(i in 1 until childCount)drawChild(canvas,getChildAt(i),drawingTime)
        canvas.restoreToCount(saved)
    }
    fun refreshBackdrop(source: View) {
        if(pending || !glass || Build.VERSION.SDK_INT<31 || !isAttachedToWindow)return
        pending=true
        postDelayed({
            pending=false
            if(!glass || !isAttachedToWindow || width==0 || height==0 || source.width==0)return@postDelayed
            val w=(width/6).coerceAtLeast(1);val h=(height/6).coerceAtLeast(1)
            val next=bitmap?.takeIf { it.width==w&&it.height==h } ?: Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888).also { bitmap?.recycle();bitmap=it }
            next.eraseColor(ui.background)
            val own=IntArray(2);val behind=IntArray(2);getLocationInWindow(own);source.getLocationInWindow(behind)
            val canvas=Canvas(next);canvas.scale(w.toFloat()/width,h.toFloat()/height)
            canvas.translate((behind[0]-own[0]).toFloat(),(behind[1]-own[1]).toFloat())
            source.draw(canvas);backdrop.setImageBitmap(next);backdrop.invalidate()
        },80)
    }
    override fun onDetachedFromWindow() { selection?.cancel();backdrop.setImageDrawable(null);bitmap?.recycle();bitmap=null;super.onDetachedFromWindow() }
}

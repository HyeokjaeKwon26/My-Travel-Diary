package com.traveler.feature.map.flat

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.traveler.core.model.TransportMode
import com.traveler.feature.map.renderer.TravelPlaybackState
import kotlin.math.sin

/** Small vector characters face north locally, then rotate to the recorded heading. */
class FlatVehicle {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private fun color(value: Int) { paint.color = value; paint.style = Paint.Style.FILL }
    private fun outlined(c: Canvas) {
        color(Color.WHITE); c.drawPath(path, paint)
        paint.style=Paint.Style.STROKE; paint.strokeWidth=1.5f; paint.color=0xFF214B70.toInt()
        c.drawPath(path,paint);paint.style=Paint.Style.FILL
    }
    private fun rect(c: Canvas, l: Float, t: Float, r: Float, b: Float, fill: Int, radius: Float = 3f) {
        color(fill); c.drawRoundRect(l,t,r,b,radius,radius,paint)
    }
    fun draw(c: Canvas, x: Float, y: Float, shortSide: Float, s: TravelPlaybackState) {
        val saved = c.save()
        c.translate(x,y)
        val scale = (shortSide / 420f).coerceIn(.8f, 2.2f)
        c.scale(scale,scale)
        color(0x40334455); c.drawOval(-16f, 13f, 16f, 22f, paint)
        // storyTimeMs freezes during photo holds, keeping characters still as well.
        val phase = (s.storyTimeMs % 2000L).toFloat() / 2000f * 6.283185f
        val bounce = if (s.currentSegment != null) sin(phase * 2f) * 1.6f else 0f
        c.translate(0f,bounce)
        c.rotate(s.currentHeadingDegrees)
        when (s.currentTransportMode) {
            TransportMode.TRAIN, TransportMode.SUBWAY -> {
                rect(c,-9f,9f,9f,31f,0xFFEBF2FA.toInt())
                rect(c,-7f,13f,7f,27f,0xFF1676C4.toInt())
                color(Color.WHITE); path.reset(); path.moveTo(0f,-30f)
                path.cubicTo(-11f,-23f,-11f,-10f,-11f,5f)
                path.lineTo(11f,5f); path.cubicTo(11f,-10f,11f,-23f,0f,-30f); path.close(); outlined(c)
                rect(c,-7f,-18f,7f,-8f,0xFF123B66.toInt())
                rect(c,-10f,-4f,10f,3f,0xFF168CDB.toInt())
            }
            TransportMode.AIRPLANE -> {
                color(Color.WHITE); path.reset(); path.moveTo(0f,-30f); path.lineTo(5f,-7f)
                path.lineTo(28f,9f); path.lineTo(28f,14f); path.lineTo(5f,7f)
                path.lineTo(4f,20f); path.lineTo(12f,27f); path.lineTo(0f,24f)
                path.lineTo(-12f,27f); path.lineTo(-4f,20f); path.lineTo(-5f,7f)
                path.lineTo(-28f,14f); path.lineTo(-28f,9f); path.lineTo(-5f,-7f); path.close(); outlined(c)
                rect(c,-3f,-21f,3f,-9f,0xFF2097DD.toInt())
            }
            TransportMode.CAR, TransportMode.BUS -> {
                val length = if(s.currentTransportMode == TransportMode.BUS) 26f else 21f
                rect(c,-16f,-15f,-11f,-5f,0xFF182433.toInt()); rect(c,11f,-15f,16f,-5f,0xFF182433.toInt())
                rect(c,-16f,9f,-11f,18f,0xFF182433.toInt()); rect(c,11f,9f,16f,18f,0xFF182433.toInt())
                rect(c,-12f,-length,12f,length,if(s.currentTransportMode == TransportMode.BUS) 0xFF12ACB5.toInt() else 0xFFFFBF36.toInt(),6f)
                rect(c,-9f,-length+5f,9f,-7f,0xFF183955.toInt())
                rect(c,-9f,-2f,9f,12f,if(s.currentTransportMode == TransportMode.BUS) Color.WHITE else 0xFFF04F50.toInt())
                rect(c,-10f,-length, -5f,-length+3f,Color.WHITE); rect(c,5f,-length,10f,-length+3f,Color.WHITE)
            }
            TransportMode.FERRY -> {
                color(0xFF248DC1.toInt()); path.reset(); path.moveTo(0f,-29f); path.lineTo(15f,-8f)
                path.lineTo(12f,23f); path.lineTo(-12f,23f); path.lineTo(-15f,-8f); path.close(); c.drawPath(path,paint)
                rect(c,-9f,-10f,9f,16f,Color.WHITE); rect(c,-6f,-7f,6f,2f,0xFF153E65.toInt())
            }
            else -> {
                c.rotate(-s.currentHeadingDegrees) // People stay upright; pointer shows travel direction.
                color(0xFF164A7C.toInt()); c.drawCircle(0f,7f,13f,paint)
                rect(c,-11f,14f,-2f,25f,0xFF223849.toInt()); rect(c,2f,14f,11f,25f,0xFF223849.toInt())
                color(0xFFFFC44F.toInt()); c.drawCircle(0f,-11f,10f,paint)
                if(s.currentTransportMode==TransportMode.BICYCLE) {
                    paint.style=Paint.Style.STROKE;paint.strokeWidth=3f;paint.color=0xFF189F90.toInt()
                    c.drawCircle(-15f,22f,8f,paint);c.drawCircle(15f,22f,8f,paint);paint.style=Paint.Style.FILL
                }
            }
        }
        c.restoreToCount(saved)
    }
}

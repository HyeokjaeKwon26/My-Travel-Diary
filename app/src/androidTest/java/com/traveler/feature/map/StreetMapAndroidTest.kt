package com.traveler.feature.map

import android.graphics.*
import android.opengl.EGL14
import android.opengl.GLES20 as GL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.traveler.core.common.geo.GeoPoint
import com.traveler.core.common.geo.WebMercator
import com.traveler.core.model.TransportMode
import com.traveler.feature.map.renderer.*
import com.traveler.feature.map.story.TravelStoryTimeline
import com.traveler.feature.map.flat.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.util.Properties
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class StreetMapAndroidTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun cancelledViewportCanBeRequestedAgainWithoutFailureCooldown() {
        val root=File(context.cacheDir,"cancel-revisit-${System.nanoTime()}").apply { mkdirs() }
        val started=java.util.concurrent.CountDownLatch(1)
        val release=java.util.concurrent.CountDownLatch(1)
        val count=AtomicInteger()
        val tile=StreetTile(12,8,8);val plan=StreetTilePlan(listOf(tile),12)
        val session=StreetMapSession(context,true,root,connectionFactory={
            val index=count.incrementAndGet()
            object:HttpURLConnection(URL("https://fixture.invalid/tile")) {
                override fun connect() {}
                override fun usingProxy()=false
                override fun disconnect() { if(index==1) release.countDown() }
                override fun getResponseCode():Int {
                    if(index==1) { started.countDown();release.await(5,java.util.concurrent.TimeUnit.SECONDS) }
                    return 500
                }
            }
        })
        try {
            session.request(plan);assertTrue(started.await(5,java.util.concurrent.TimeUnit.SECONDS))
            session.request(StreetTilePlan(emptyList(),12))
            session.request(plan)
            awaitCondition { count.get()>=2 }
        } finally { release.countDown();session.close();root.deleteRecursively() }
    }

    private fun fixture():Bitmap=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888).also {
        val canvas=Canvas(it);canvas.drawColor(Color.rgb(236,224,245))
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE;strokeWidth=14f }
        canvas.drawLine(0f,128f,256f,128f,paint);canvas.drawLine(128f,0f,128f,256f,paint)
        paint.color=Color.rgb(70,10,90);paint.textSize=17f
        canvas.drawText("FIXTURE STREET",8f,110f,paint)
        paint.color=Color.rgb(170,40,190);canvas.drawRect(20f,20f,85f,75f,paint)
    }
    private fun store(root:File,tile:StreetTile,expires:Long) {
        fixture().let { bitmap -> File(root,"${tile.key}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle() }
        Properties().apply { setProperty("expires",expires.toString());setProperty("etag","fixture-etag") }
            .let { p -> File(root,"${tile.key}.properties").outputStream().use { p.store(it,null) } }
    }
    private fun awaitCondition(condition:()->Boolean) {
        val until=System.currentTimeMillis()+5000
        while(!condition() && System.currentTimeMillis()<until) Thread.sleep(25)
        assertTrue(condition())
    }

    @Test fun freshCacheAndExportDoNotFetchAndExpiredCacheUsesConditionalRequest() {
        val root=File(context.cacheDir,"street-http-test-${System.nanoTime()}").apply { mkdirs() }
        val tile=StreetTile(15,5240,12660);val plan=StreetTilePlan(listOf(tile),15)
        val count=AtomicInteger(0)
        var request:HttpURLConnection?=null
        fun session(network:Boolean)=StreetMapSession(context,network,root,connectionFactory={
            count.incrementAndGet()
            object:HttpURLConnection(URL("https://fixture.invalid/tile")) {
                override fun connect() {}
                override fun disconnect() {}
                override fun usingProxy()=false
                override fun getResponseCode()=304
                override fun getHeaderField(name:String?)=when(name) { "Cache-Control"->"public, max-age=3600";"ETag"->"fixture-etag";else->null }
            }.also { request=it }
        })
        try {
            store(root,tile,System.currentTimeMillis()+86400_000)
            session(true).let { s -> try { s.request(plan);awaitCondition { s.detailCount==1 };Thread.sleep(400);assertEquals(0,count.get()) } finally { s.close() } }
            store(root,tile,0)
            session(false).let { s -> try { s.request(plan);assertEquals(1,s.detailCount);assertEquals(0,count.get()) } finally { s.close() } }
            session(true).let { s -> try {
                s.request(plan)
                awaitCondition { count.get()==1 && request?.getRequestProperty("If-None-Match")=="fixture-etag" }
                assertTrue(request!!.getRequestProperty("User-Agent").startsWith("MyTravelDiary/"))
                assertNull(request!!.getRequestProperty("Cache-Control"))
                awaitCondition {
                    val p=Properties();File(root,"${tile.key}.properties").inputStream().use { p.load(it) }
                    (p.getProperty("expires")?.toLongOrNull() ?: 0)>System.currentTimeMillis()
                }
            } finally { s.close() } }
        } finally { root.deleteRecursively() }
    }

    @Test fun disabledInternetStillLoadsCacheAndEvictedTilesReloadOnRevisit() {
        val root=File(context.cacheDir,"street-revisit-test-${System.nanoTime()}").apply { mkdirs() }
        val tiles=(0..48).map { StreetTile(15,5240+it,12660) }
        tiles.forEach { store(root,it,System.currentTimeMillis()+86400_000) }
        val session=StreetMapSession(context,true,root,connectionFactory={error("Fresh cache must not fetch")})
        try {
            session.setActive(false)
            for(batch in tiles.chunked(24)) {
                session.request(StreetTilePlan(batch,15))
                awaitCondition { session.detailCount==batch.size }
                // Ensure the next equal-size batch is not mistaken for this batch.
                session.request(StreetTilePlan(emptyList(),15))
                awaitCondition { session.detailCount==0 }
            }
            session.setActive(true)
            session.request(StreetTilePlan(listOf(tiles.first()),15))
            awaitCondition { session.detailCount==1 }
        } finally { session.close();root.deleteRecursively() }
    }

    @Test fun cachedStreetTilesAlignInPortraitAndLandscapeWithoutNetwork() {
        val root=File(context.cacheDir,"flat-tiles-${System.nanoTime()}").apply { mkdirs() }
        val tile=StreetTile(8,64,96)
        store(root,tile,System.currentTimeMillis()+86400_000)
        val session=StreetMapSession(context,false,root,connectionFactory={error("Export must never fetch")})
        try {
            val plan=StreetTilePlan(listOf(tile),8);session.request(plan)
            for ((w,h) in listOf(320 to 640,640 to 320)) {
                val image=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
                val f=MapFootprint(64.0/256,96.0/256,65.0/256,97.0/256)
                session.paint(Canvas(image),f,w,h,plan)
                assertEquals(Color.rgb(236,224,245),image.getPixel(w*3/4,h*3/4))
                assertEquals(Color.WHITE,image.getPixel(w/2,h/2))
                image.recycle()
            }
        } finally { session.close();root.deleteRecursively() }
    }
}

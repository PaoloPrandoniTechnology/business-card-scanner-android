package com.paoloprandoni.businesscard.domain.crop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CropFrameTest {

    @Test
    fun `senza rotazione i vertici sono quelli del rettangolo`() {
        val frame = CropFrame(centerX = 100f, centerY = 100f, width = 40f, height = 20f)
        val corners = frame.corners()
        assertEquals(80f, corners[0].x, 0.01f)
        assertEquals(90f, corners[0].y, 0.01f)
        assertEquals(120f, corners[1].x, 0.01f)
        assertEquals(110f, corners[2].y, 0.01f)
        assertEquals(80f, corners[3].x, 0.01f)
    }

    @Test
    fun `ruotando di 90 gradi i lati si scambiano`() {
        val frame = CropFrame(100f, 100f, 40f, 20f, angleDeg = 90f)
        val corners = frame.corners()
        assertEquals(110f, corners[0].x, 0.01f)
        assertEquals(80f, corners[0].y, 0.01f)
        assertEquals(110f, corners[1].x, 0.01f)
        assertEquals(120f, corners[1].y, 0.01f)
        val (halfX, halfY) = frame.halfExtents()
        assertEquals(10f, halfX, 0.01f)
        assertEquals(20f, halfY, 0.01f)
    }

    @Test
    fun `contains riconosce dentro e fuori`() {
        val frame = CropFrame(100f, 100f, 40f, 20f, angleDeg = 35f)
        val center = Point2(100f, 100f)
        val outside = Point2(100f, 130f)
        val rotatedCornerArea = Point2(118f, 106f)
        assertTrue(frame.contains(center))
        assertTrue(frame.contains(rotatedCornerArea))
        assertFalse(frame.contains(outside))
    }

    @Test
    fun `muovere mantiene dimensioni e rotazione`() {
        val frame = CropFrame(100f, 100f, 40f, 20f, angleDeg = 12f)
        val moved = frame.moved(10f, -5f)
        assertEquals(110f, moved.centerX, 0.01f)
        assertEquals(95f, moved.centerY, 0.01f)
        assertEquals(frame.width, moved.width, 0.01f)
        assertEquals(12f, moved.angleDeg, 0.01f)
    }

    @Test
    fun `ridimensionare tiene fermo l'angolo opposto`() {
        val frame = CropFrame(100f, 100f, 40f, 20f)
        val resized = frame.resized(-1, -1, Point2(70f, 60f), minSide = 8f)
        val fixedBefore = frame.corner(1, 1)
        val fixedAfter = resized.corner(1, 1)
        assertEquals(fixedBefore.x, fixedAfter.x, 0.01f)
        assertEquals(fixedBefore.y, fixedAfter.y, 0.01f)
        assertEquals(50f, resized.width, 0.01f)
        assertEquals(50f, resized.height, 0.01f)
        assertEquals(95f, resized.centerX, 0.01f)
        assertEquals(85f, resized.centerY, 0.01f)
    }

    @Test
    fun `ridimensionare con vincolo quadrato mantiene i lati uguali`() {
        val frame = CropFrame(100f, 100f, 40f, 20f)
        val resized = frame.resized(-1, -1, Point2(60f, 90f), minSide = 8f, keepSquare = true)
        assertEquals(resized.width, resized.height, 0.01f)
        assertEquals(60f, resized.width, 0.01f)
        assertEquals(90f, resized.centerX, 0.01f)
        assertEquals(80f, resized.centerY, 0.01f)
    }

    @Test
    fun `la maniglia sopra il centro vale zero gradi`() {
        val frame = CropFrame(100f, 100f, 40f, 20f)
        assertEquals(0f, frame.angleTowards(Point2(100f, 40f)), 0.05f)
        assertEquals(45f, frame.angleTowards(Point2(160f, 40f)), 0.05f)
        assertEquals(90f, frame.angleTowards(Point2(160f, 100f)), 0.05f)
        assertEquals(-45f, frame.angleTowards(Point2(40f, 40f)), 0.05f)
    }

    @Test
    fun `lo snap porta gli angoli vicini ai valori noti`() {
        assertEquals(0f, CropFrame(100f, 100f, 40f, 20f, 0.8f).snappedAngle(2f).angleDeg, 0.01f)
        assertEquals(45f, CropFrame(100f, 100f, 40f, 20f, 44.4f).snappedAngle(2f).angleDeg, 0.01f)
        assertEquals(7f, CropFrame(100f, 100f, 40f, 20f, 7f).snappedAngle(2f).angleDeg, 0.01f)
    }

    @Test
    fun `il ritaglio ruotato viene riportato dentro l'immagine`() {
        val frame = CropFrame(100f, 50f, 300f, 300f, angleDeg = 45f)
        val clamped = frame.clamped(200f, 100f)
        val (halfX, halfY) = clamped.halfExtents()
        assertTrue(halfX * 2f <= 200.01f)
        assertTrue(halfY * 2f <= 100.01f)
        assertEquals(100f, clamped.centerX, 0.01f)
        assertEquals(50f, clamped.centerY, 0.01f)
    }

    @Test
    fun `lo snap del quadrato rende i lati uguali`() {
        val frame = CropFrame(100f, 100f, 80f, 50f).squared()
        assertEquals(50f, frame.width, 0.01f)
        assertEquals(50f, frame.height, 0.01f)
    }
}

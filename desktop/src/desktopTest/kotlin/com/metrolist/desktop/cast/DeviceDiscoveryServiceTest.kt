package com.metrolist.desktop.cast

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DeviceDiscoveryServiceTest {

    @Test
    fun testDefaultDeviceIsThisComputer() {
        val devices = DeviceDiscoveryService.devices.value
        assertTrue("Device list must not be empty", devices.isNotEmpty())
        val first = devices.first()
        assertEquals("local_pc", first.id)
        assertEquals("This computer", first.name)
        assertEquals(DeviceType.COMPUTER, first.type)
        assertTrue(first.isCurrent)
    }

    @Test
    fun testSelectDeviceUpdatesState() {
        DeviceDiscoveryService.selectDevice("local_pc")
        assertEquals("local_pc", DeviceDiscoveryService.selectedDeviceId.value)
        val selected = DeviceDiscoveryService.devices.value.firstOrNull { it.id == "local_pc" }
        assertNotNull(selected)
        assertTrue(selected!!.isCurrent)
    }

    @Test
    fun testScanDiscoversNetworkDevices() = runBlocking {
        DeviceDiscoveryService.startScan()
        // Tunggu pemindaian berjalan 2.5 detik
        delay(2500)
        val devices = DeviceDiscoveryService.devices.value
        println("Discovered devices count: ${devices.size}")
        for (dev in devices) {
            println(" - [${dev.protocol}] ${dev.name} (${dev.ipAddress})")
        }
        assertTrue("Local PC device must always be present", devices.any { it.id == "local_pc" })
        // Pastikan tidak ada duplikasi ID
        val ids = devices.map { it.id }
        assertEquals("Device IDs must be unique", ids.size, ids.distinct().size)
    }
}

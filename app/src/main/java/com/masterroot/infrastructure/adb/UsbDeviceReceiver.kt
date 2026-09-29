package com.masterroot.infrastructure.adb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import timber.log.Timber

/**
 * Listens for USB device attach/detach events.
 * When a device is attached, notifies connection manager.
 * Never auto-authorizes — ADB authorization goes through normal ADB flow.
 */
class UsbDeviceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                Timber.i("USB device attached: ${device?.deviceName} (${device?.manufacturerName})")
            }
            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                Timber.i("USB device detached: ${device?.deviceName}")
            }
        }
    }
}

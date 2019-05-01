# Sustainabot body
Sustainabot's Android app allows you to configure the device's parameters and command it to draw images and diagrams.

The current version is configured to connect to a pre-set list of Sustainabot devices (by Bluetooth MAC address) for ease of use. Before running the application, set your own device IDs in [DeviceListActivity.java](app/src/main/java/app/akexorcist/bluetoothspp/library/DeviceListActivity.java). Alternatively, remove the MAC address checking in the `BroadcastReceiver` of the same file to display all available devices.

This app is intended to be used for local testing and material printing, rather than public release (e.g., on Google Play). If you would like to release the app publicly, you will need to change its package name. To do this, edit the `applicationId` field in the app module's [build.gradle](app/build.gradle).

## License
Apache 2.0

/*
 * Adapted from Android-BluetoothSPPLibrary: https://github.com/akexorcist/Android-BluetoothSPPLibrary
 *
 * Copyright 2014 Akexorcist
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.akexorcist.bluetoothspp.library;

public class BluetoothState {
	// Constants that indicate the current connection state
	static final int STATE_NONE = 0;        // we're doing nothing
	static final int STATE_LISTEN = 1;      // now listening for incoming connections
	static final int STATE_CONNECTING = 2;  // now initiating an outgoing connection
	static final int STATE_CONNECTED = 3;   // now connected to a remote device
	static final int STATE_NULL = -1;       // now service is null

	// Message types sent from the BluetoothChatService Handler
	static final int MESSAGE_STATE_CHANGE = 1;
	static final int MESSAGE_READ = 2;
	static final int MESSAGE_WRITE = 3;
	static final int MESSAGE_DEVICE_NAME = 4;
	static final int MESSAGE_LINE_END = 5;

	// Intent request codes
	public static final int REQUEST_CONNECT_DEVICE = 384;
	public static final int REQUEST_ENABLE_BT = 385;

	// Key names received from the BluetoothChatService Handler
	static final String DEVICE_NAME = "row_device_list";
	static final String DEVICE_ADDRESS = "device_address";

	static final boolean DEVICE_ANDROID = true;
	public static final boolean DEVICE_OTHER = false;

	// Return Intent extra
	public static String EXTRA_DEVICE_ADDRESS = "device_address";
}

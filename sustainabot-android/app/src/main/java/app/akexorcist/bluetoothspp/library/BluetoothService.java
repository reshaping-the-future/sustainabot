/*
 * Adapted from Android-BluetoothSPPLibrary: https://github.com/akexorcist/Android-BluetoothSPPLibrary
 *
 * Copyright (C) 2014 Akexorcist
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.akexorcist.bluetoothspp.library;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

@SuppressLint("NewApi")
class BluetoothService {
	// Debugging
	private static final String TAG = "Bluetooth Service";

	// Name for the SDP record when creating server socket
	private static final String NAME_SECURE = "Bluetooth Secure";

	// Unique UUID for this application
	private static final UUID UUID_ANDROID_DEVICE = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");
	private static final UUID UUID_OTHER_DEVICE = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

	// Member fields
	private final BluetoothAdapter mAdapter;
	private final Handler mHandler;
	private AcceptThread mSecureAcceptThread;
	private ConnectThread mConnectThread;
	private ConnectedThread mConnectedThread;
	private int mState;
	private boolean isAndroid = BluetoothState.DEVICE_ANDROID;

	// Constructor. Prepares a new BluetoothChat session
	// context : The UI Activity Context
	// handler : A Handler to send messages back to the UI Activity
	BluetoothService(Handler handler) {
		mAdapter = BluetoothAdapter.getDefaultAdapter();
		mState = BluetoothState.STATE_NONE;
		mHandler = handler;
	}


	// Set the current state of the chat connection
	// state : An integer defining the current connection state
	private synchronized void setState(int state) {
		Log.d(TAG, "setState() " + mState + " -> " + state);
		mState = state;

		// Give the new state to the Handler so the UI Activity can update
		mHandler.obtainMessage(BluetoothState.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
	}

	// Return the current connection state.
	synchronized int getState() {
		return mState;
	}

	// Start the chat service. Specifically start AcceptThread to begin a
	// session in listening (server) mode. Called by the Activity onResume()
	synchronized void start(boolean isAndroid) {
		// Cancel any thread attempting to make a connection
		if (mConnectThread != null) {
			mConnectThread.cancel();
			mConnectThread = null;
		}
		// Cancel any thread currently running a connection
		if (mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
		}

		setState(BluetoothState.STATE_LISTEN);

		// Start the thread to listen on a BluetoothServerSocket
		if (mSecureAcceptThread == null) {
			mSecureAcceptThread = new AcceptThread(isAndroid);
			mSecureAcceptThread.start();
			BluetoothService.this.isAndroid = isAndroid;
		}
	}

	// Start the ConnectThread to initiate a connection to a remote device
	// device : The BluetoothDevice to connect
	// secure : Socket Security type - Secure (true) , Insecure (false)
	synchronized void connect(BluetoothDevice device) {
		// Cancel any thread attempting to make a connection
		if (mState == BluetoothState.STATE_CONNECTING) {
			if (mConnectThread != null) {
				mConnectThread.cancel();
				mConnectThread = null;
			}
		}

		// Cancel any thread currently running a connection
		if (mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
		}

		// Start the thread to connect with the given device
		mConnectThread = new ConnectThread(device);
		mConnectThread.start();
		setState(BluetoothState.STATE_CONNECTING);
	}

	/**
	 * Start the ConnectedThread to begin managing a Bluetooth connection
	 *
	 * @param socket The BluetoothSocket on which the connection was made
	 * @param device The BluetoothDevice that has been connected
	 */
	private synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
		// Cancel the thread that completed the connection
		if (mConnectThread != null) {
			mConnectThread.cancel();
			mConnectThread = null;
		}

		// Cancel any thread currently running a connection
		if (mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
		}

		// Cancel the accept thread because we only want to connect to one device
		if (mSecureAcceptThread != null) {
			mSecureAcceptThread.cancel();
			mSecureAcceptThread = null;
		}

		// Start the thread to manage the connection and perform transmissions
		mConnectedThread = new ConnectedThread(socket);
		mConnectedThread.start();

		// Send the name of the connected device back to the UI Activity
		Message msg = mHandler.obtainMessage(BluetoothState.MESSAGE_DEVICE_NAME);
		Bundle bundle = new Bundle();
		bundle.putString(BluetoothState.DEVICE_NAME, device.getName());
		bundle.putString(BluetoothState.DEVICE_ADDRESS, device.getAddress());
		msg.setData(bundle);
		mHandler.sendMessage(msg);

		setState(BluetoothState.STATE_CONNECTED);
	}

	// Stop all threads
	synchronized void stop() {
		if (mConnectThread != null) {
			mConnectThread.cancel();
			mConnectThread = null;
		}

		if (mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
		}

		if (mSecureAcceptThread != null) {
			mSecureAcceptThread.cancel();
			mSecureAcceptThread.kill();
			mSecureAcceptThread = null;
		}
		setState(BluetoothState.STATE_NONE);
	}

	// Write to the ConnectedThread in an unsynchronized manner
	// out : The bytes to write
	void write(byte[] out) {
		// Create temporary object
		ConnectedThread r;
		// Synchronize a copy of the ConnectedThread
		synchronized (this) {
			if (mState != BluetoothState.STATE_CONNECTED) {
				return;
			}
			r = mConnectedThread;
		}
		// Perform the write unsynchronized
		r.write(out);
	}

	// Indicate that the connection attempt failed and notify the UI Activity
	private void connectionFailed() {
		// Start the service over to restart listening mode
		BluetoothService.this.start(BluetoothService.this.isAndroid);
	}

	// Indicate that the connection was lost and notify the UI Activity
	private void connectionLost() {
		// Start the service over to restart listening mode
		BluetoothService.this.start(BluetoothService.this.isAndroid);
	}

	// This thread runs while listening for incoming connections. It behaves
	// like a server-side client. It runs until a connection is accepted
	// (or until cancelled)
	private class AcceptThread extends Thread {
		// The local server socket
		private BluetoothServerSocket mmServerSocket;
		boolean isRunning = true;

		AcceptThread(boolean isAndroid) {
			BluetoothServerSocket tmp = null;

			// Create a new listening server socket
			try {
				if (isAndroid) {
					tmp = mAdapter.listenUsingRfcommWithServiceRecord(NAME_SECURE, UUID_ANDROID_DEVICE);
				} else {
					tmp = mAdapter.listenUsingRfcommWithServiceRecord(NAME_SECURE, UUID_OTHER_DEVICE);
				}
			} catch (IOException ignored) {
			}
			mmServerSocket = tmp;
		}

		public void run() {
			setName("AcceptThread");
			BluetoothSocket socket;

			// Listen to the server socket if we're not connected
			while (mState != BluetoothState.STATE_CONNECTED && isRunning) {
				try {
					// This is a blocking call and will only return on a
					// successful connection or an exception
					socket = mmServerSocket.accept();
				} catch (Exception e) {
					break;
				}

				// If a connection was accepted
				if (socket != null) {
					synchronized (BluetoothService.this) {
						switch (mState) {
							case BluetoothState.STATE_LISTEN:
							case BluetoothState.STATE_CONNECTING:
								// Situation normal. Start the connected thread.
								connected(socket, socket.getRemoteDevice());
								break;
							case BluetoothState.STATE_NONE:
							case BluetoothState.STATE_CONNECTED:
								// Either not ready or already connected. Terminate new socket.
								try {
									socket.close();
								} catch (Exception ignored) {
								}
								break;
						}
					}
				}
			}
		}

		void cancel() {
			try {
				mmServerSocket.close();
				mmServerSocket = null;
			} catch (Exception ignored) {
			}
		}

		void kill() {
			isRunning = false;
		}
	}


	// This thread runs while attempting to make an outgoing connection
	// with a device. It runs straight through
	// the connection either succeeds or fails
	private class ConnectThread extends Thread {
		private final BluetoothSocket mmSocket;
		private final BluetoothDevice mmDevice;

		ConnectThread(BluetoothDevice device) {
			mmDevice = device;
			BluetoothSocket tmp = null;

			// Get a BluetoothSocket for a connection with the
			// given BluetoothDevice
			try {
				if (BluetoothService.this.isAndroid) {
					tmp = device.createRfcommSocketToServiceRecord(UUID_ANDROID_DEVICE);
				} else {
					tmp = device.createRfcommSocketToServiceRecord(UUID_OTHER_DEVICE);
				}
			} catch (IOException ignored) {
			}
			mmSocket = tmp;
		}

		public void run() {
			// Always cancel discovery because it will slow down a connection
			setName("ConnectThread");
			mAdapter.cancelDiscovery();

			// Make a connection to the BluetoothSocket
			try {
				// This is a blocking call and will only return on a
				// successful connection or an exception
				mmSocket.connect();
			} catch (Exception e) {
				// Close the socket
				try {
					mmSocket.close();
				} catch (Exception ignored) {
				}
				connectionFailed();
				return;
			}

			// Reset the ConnectThread because we're done
			synchronized (BluetoothService.this) {
				mConnectThread = null;
			}

			// Start the connected thread
			connected(mmSocket, mmDevice);
		}

		void cancel() {
			try {
				mmSocket.close();
			} catch (Exception ignored) {
			}
		}
	}

	// This thread runs during a connection with a remote device.
	// It handles all incoming and outgoing transmissions.
	private class ConnectedThread extends Thread {
		private final BluetoothSocket mmSocket;
		private final InputStream mmInStream;
		private final OutputStream mmOutStream;

		ConnectedThread(BluetoothSocket socket) {
			mmSocket = socket;
			InputStream tmpIn = null;
			OutputStream tmpOut = null;

			// Get the BluetoothSocket input and output streams
			try {
				tmpIn = socket.getInputStream();
				tmpOut = socket.getOutputStream();
			} catch (IOException ignored) {
			}

			mmInStream = tmpIn;
			mmOutStream = tmpOut;
		}

		public void run() {
			setName("ConnectedThread");
			byte[] singleCharacter;
			StringBuilder combinedMessage = new StringBuilder();

			// Keep listening to the InputStream while connected
			while (true) {
				try {
					int data = mmInStream.read();

					if (data == 0x0A) { // \n
						// Log.d(TAG, "No data - sending line end (\\n)");
						// send both a message read line end (0x0A) and the line end, *including* the previous line
						mHandler.obtainMessage(BluetoothState.MESSAGE_READ, 1, -1, new byte[]{ 0x0A }).sendToTarget();
						mHandler.obtainMessage(BluetoothState.MESSAGE_LINE_END, 1, -1, combinedMessage.toString())
								.sendToTarget();
						combinedMessage.setLength(0);
					} else if (data == 0x0D) { // \r
						// Log.d(TAG, "No data (\\r)");
					} else {
						// Log.d(TAG, "Read: [" + (char) data + "]");
						singleCharacter = new byte[]{ (byte) data };
						combinedMessage.append(new String(singleCharacter)); // so we can send whole line on end
						mHandler.obtainMessage(BluetoothState.MESSAGE_READ, 1, -1, singleCharacter).sendToTarget();
					}
				} catch (IOException e) {
					connectionLost();
					// Start the service over to restart listening mode
					BluetoothService.this.start(BluetoothService.this.isAndroid);
					break;
				}
			}
		}

		// Write to the connected OutStream.
		// @param buffer  The bytes to write
		void write(byte[] buffer) {
			try {/*
                byte[] buffer2 = new byte[buffer.length + 2];
                for(int i = 0 ; i < buffer.length ; i++)
                    buffer2[i] = buffer[i];
                buffer2[buffer2.length - 2] = 0x0A;
                buffer2[buffer2.length - 1] = 0x0D;*/
				mmOutStream.write(buffer);
				mmOutStream.flush();
				// Log.d(TAG, "Written: [" + new String(buffer) + "]");
				// Share the sent message back to the UI Activity
				mHandler.obtainMessage(BluetoothState.MESSAGE_WRITE, -1, -1, buffer).sendToTarget();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		void cancel() {
			try {
				mmSocket.close();
			} catch (Exception ignored) {
			}
		}
	}
}

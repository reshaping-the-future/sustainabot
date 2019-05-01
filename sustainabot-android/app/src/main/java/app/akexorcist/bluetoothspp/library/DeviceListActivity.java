package app.akexorcist.bluetoothspp.library;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;

import ac.robinson.sustainabot.R;

public class DeviceListActivity extends AppCompatActivity {

	private static final String TAG = "DeviceList";

	private static final int REQUEST_ENABLE_BT = 123;
	private static final int REQUEST_PERMISSIONS = 321;

	public static final String KEY_DEFAULT_SUSTAINABOT = "default_sustainabot";

	private static final ArrayList<String> DEVICES = new ArrayList<>();

	static {
		DEVICES.add("..."); // placeholder so list index == Sustainabot ID
		DEVICES.add("*** YOUR BLUETOOTH IDS HERE ***");
	}

	private BluetoothAdapter mBluetoothAdapter;
	private ArrayAdapter<Spanned> mDeviceListAdapter;
	private boolean mSettingDefaultDevice = false;

	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_device_list);

		setResult(Activity.RESULT_CANCELED); // in case they press back

		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		IntentFilter intentFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
		intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
		intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		registerReceiver(mBluetoothIntentReceiver, intentFilter);

		mDeviceListAdapter = new ArrayAdapter<>(DeviceListActivity.this, R.layout.row_device_list);
		ListView listView = findViewById(R.id.list_devices);
		listView.setAdapter(mDeviceListAdapter);
		listView.setOnItemClickListener(mDeviceClickListener);
		listView.setOnItemLongClickListener(mDeviceLongClickListener);

		// always make sure Bluetooth is enabled (this activity is always called on application start)
		checkAndEnableBluetooth();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.menu_device_list, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.refresh:
				checkAndEnableBluetooth();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	protected void onDestroy() {
		super.onDestroy();
		if (mBluetoothAdapter != null) {
			mBluetoothAdapter.cancelDiscovery();
		}
		unregisterReceiver(mBluetoothIntentReceiver);
		finish();
	}

	private void checkAndEnableBluetooth() {
		if (checkHasPermissions()) {
			if (!mBluetoothAdapter.isEnabled()) {
				Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
				startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
			} else {
				SharedPreferences preferences = getPreferences(Context.MODE_PRIVATE);
				String defaultDeviceAddress = preferences.getString(KEY_DEFAULT_SUSTAINABOT, null);
				if (!TextUtils.isEmpty(defaultDeviceAddress)) {
					finishAndConnectDevice(defaultDeviceAddress);
				} else {
					startDiscovery();
				}
			}
		}
	}

	private void startDiscovery() {
		Log.d(TAG, "Starting device discovery");
		mDeviceListAdapter.clear();
		if (mBluetoothAdapter.isDiscovering()) {
			mBluetoothAdapter.cancelDiscovery();
		}
		mBluetoothAdapter.startDiscovery();
	}

	private OnItemClickListener mDeviceClickListener = new AdapterView.OnItemClickListener() {
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			if (mBluetoothAdapter.isDiscovering()) {
				mBluetoothAdapter.cancelDiscovery();
			}

			String info = ((TextView) view).getText().toString();
			String deviceAddress = info.substring(info.length() - 17); // MAC address
			finishAndConnectDevice(deviceAddress);
		}
	};

	private AdapterView.OnItemLongClickListener mDeviceLongClickListener = new AdapterView.OnItemLongClickListener() {
		@Override
		public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
			mSettingDefaultDevice = true;

			if (mBluetoothAdapter.isDiscovering()) {
				mBluetoothAdapter.cancelDiscovery();
			}

			String info = ((TextView) view).getText().toString();
			final String deviceAddress = info.substring(info.length() - 17); // MAC address

			SharedPreferences preferences = getPreferences(Context.MODE_PRIVATE);
			SharedPreferences.Editor editor = preferences.edit();
			editor.putString(KEY_DEFAULT_SUSTAINABOT, deviceAddress);
			editor.apply();

			Snackbar.make(findViewById(R.id.list_devices), getString(R.string.hint_default_sustainabot,
					DEVICES.indexOf(deviceAddress)), Snackbar.LENGTH_INDEFINITE)
					.setAction(R.string.hint_connect_device, new View.OnClickListener() {
						@Override
						public void onClick(View view) {
							finishAndConnectDevice(deviceAddress);
						}
					})
					.show();
			return true;
		}
	};

	private void finishAndConnectDevice(String address) {
		Log.d(TAG, "Connecting to device " + address);
		Intent resultIntent = new Intent();
		resultIntent.putExtra(BluetoothState.EXTRA_DEVICE_ADDRESS, address);
		setResult(Activity.RESULT_OK, resultIntent);
		finish();
	}

	private final BroadcastReceiver mBluetoothIntentReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (BluetoothDevice.ACTION_FOUND.equals(action)) {
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				String deviceAddress = device.getAddress();
				if (DEVICES.contains(deviceAddress)) {
					int deviceId = DEVICES.indexOf(deviceAddress);
					Log.d(TAG, "Found Sustainabot " + deviceId);
					findViewById(R.id.list_devices_progress).setVisibility(View.GONE);
					mDeviceListAdapter.add(Html.fromHtml("<b>Sustainabot " + deviceId + "</b><br>" + deviceAddress));
				} else {
					Log.d(TAG, "Ignoring non-sustainabot device " + deviceAddress);
				}
			} else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
				int numSustainabotsFound = mDeviceListAdapter.getCount();
				Log.d(TAG, "Device scan completed - found " + numSustainabotsFound + " Sustainabots");
				findViewById(R.id.list_devices_progress).setVisibility(View.GONE);
				if (!mSettingDefaultDevice) {
					Snackbar.make(findViewById(R.id.list_devices), numSustainabotsFound ==
							0 ? getString(R.string.no_sustainabots_found) :
							getResources().getQuantityString(R.plurals.number_sustainabots_found, numSustainabotsFound,
									numSustainabotsFound), Snackbar.LENGTH_INDEFINITE)
							.show();
				}
			} else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
				findViewById(R.id.list_devices_progress).setVisibility(View.VISIBLE);
				Snackbar.make(findViewById(R.id.list_devices), R.string.searching_for_sustainabots,
						Snackbar.LENGTH_INDEFINITE)
						.show();
			}
		}
	};

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
			case REQUEST_ENABLE_BT:
				if (!mBluetoothAdapter.isEnabled()) {
					Log.d(TAG, "Bluetooth not enabled");
					Snackbar.make(findViewById(R.id.list_devices), R.string.hint_enable_bluetooth,
							Snackbar.LENGTH_INDEFINITE)
							.setAction(R.string.hint_retry, new View.OnClickListener() {
								@Override
								public void onClick(View view) {
									checkAndEnableBluetooth();
								}
							})
							.show();
				} else {
					Log.d(TAG, "Bluetooth enabled");
					SharedPreferences preferences = getPreferences(Context.MODE_PRIVATE);
					String defaultDeviceAddress = preferences.getString(KEY_DEFAULT_SUSTAINABOT, null);
					if (!TextUtils.isEmpty(defaultDeviceAddress)) {
						finishAndConnectDevice(defaultDeviceAddress);
					} else {
						startDiscovery();
					}
				}
				return;

			default:
				super.onActivityResult(requestCode, resultCode, data);
		}
	}

	private boolean checkHasPermissions() {
		if (ActivityCompat.checkSelfPermission(DeviceListActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) !=
				PackageManager.PERMISSION_GRANTED ||
				ActivityCompat.checkSelfPermission(DeviceListActivity.this,
						Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
						PackageManager.PERMISSION_GRANTED) {
			if (ActivityCompat.shouldShowRequestPermissionRationale(DeviceListActivity.this,
					Manifest.permission.ACCESS_COARSE_LOCATION) ||
					ActivityCompat.shouldShowRequestPermissionRationale(DeviceListActivity.this,
							Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
				showPermissionMessageDialog();
			}
			ActivityCompat.requestPermissions(DeviceListActivity.this, new String[]{
					Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE
			}, REQUEST_PERMISSIONS);
			return false;
		} else {
			return true;
		}
	}

	private void showPermissionMessageDialog() {
		Snackbar.make(findViewById(R.id.list_devices), R.string.hint_location_storage_permission,
				Snackbar.LENGTH_INDEFINITE)
				.setAction(R.string.hint_retry, new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						checkHasPermissions();
					}
				})
				.show();
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
										   @NonNull int[] grantResults) {
		switch (requestCode) {
			case REQUEST_PERMISSIONS:
				if (ActivityCompat.checkSelfPermission(DeviceListActivity.this,
						Manifest.permission.ACCESS_COARSE_LOCATION) ==
						PackageManager.PERMISSION_GRANTED &&
						ActivityCompat.checkSelfPermission(DeviceListActivity.this,
								Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
								PackageManager.PERMISSION_GRANTED) {
					checkAndEnableBluetooth();
				} else {
					showPermissionMessageDialog();
				}
				break;

			default:
				super.onRequestPermissionsResult(requestCode, permissions, grantResults);
				break;
		}
	}
}

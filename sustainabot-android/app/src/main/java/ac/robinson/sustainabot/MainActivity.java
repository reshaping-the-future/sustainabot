package ac.robinson.sustainabot;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.afollestad.materialdialogs.MaterialDialog;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

import app.akexorcist.bluetoothspp.library.BluetoothSPP;
import app.akexorcist.bluetoothspp.library.BluetoothState;
import app.akexorcist.bluetoothspp.library.DeviceListActivity;

public class MainActivity extends AppCompatActivity {

	private static final String TAG = "Sustainabot";
	private static final boolean DEBUG_IMAGE_CREATION = false;
	private static final boolean EXPERT_INTERFACE_MODE = true; // helpful for debugging

	private static final boolean OPTIMISE_COMMANDS = true; // combine repeated commands when sending

	private static final int DEFAULT_PROGRESS_STEP = 10; // for UI sliders

	public static final String RESULT_IMAGE = "result_image";
	private static final int ICON_RESULT = 130;

	// TODO: read these from the device on startup (as well as when modifying them)
	private int mLeftForwardCorrection = 255;
	private int mLeftBackwardCorrection = 255;
	private int mRightForwardCorrection = 255;
	private int mRightBackwardCorrection = 255;

	private boolean mUseHeading = false;

	private final Object mDataLock = new Object();
	private boolean mLineEnded = true; // so we can send the first command
	private Handler mCommandHandler = new Handler();
	private ArrayList<String> mCommandList = new ArrayList<>();

	private String mSustainabotAddress;

	private String mImageFile;

	private ImageButton mIconButton;
	private ImageButton mCameraButton;

	private static final boolean START_WHEN_LOADED = true;

	private MaterialDialog mImageProgressDialog;

	private BluetoothSPP mBluetoothSPP;

	private Switch mMode;

	private EditText mMovementStep;
	private EditText mRotationStep;
	private EditText mManualCommand;

	private TextView mLabelCommandHistory;
	private TextView mTextCommandHistory;
	private TextView mTextSustainabotOutput;

	private ScrollView mScrollCommandHistory;
	private ScrollView mScrollSustainabotOutput;

	private SeekBar mImageSize;

	private boolean mCheckClearSucceeded;
	private boolean mSetNextHeadingAsOffset;
	private int mNumHeadingOffsetsRead;
	private int mCurrentHeading;
	private int mHeadingOffset;
	private int mMovementCorrection;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

		mBluetoothSPP = new BluetoothSPP(MainActivity.this);
		if (!mBluetoothSPP.isBluetoothAvailable()) {
			Toast.makeText(MainActivity.this, "Sorry, Bluetooth is not available on this device. Exiting",
					Toast.LENGTH_SHORT)
					.show();
			finish();
			return;
		}

		findViewById(R.id.fab).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				mIconButton.setOnClickListener(mUserButtonClickListener);
				mCameraButton.setOnClickListener(mUserButtonClickListener);
				findViewById(R.id.user_views).setVisibility(View.VISIBLE);
				findViewById(R.id.fab).setVisibility(View.GONE);
			}
		});

		// make the drop toggle button look better
		ImageSpan imageSpan = new ImageSpan(this, R.drawable.ic_file_download_blue_grey_500_36dp);
		SpannableString content = new SpannableString("X");
		content.setSpan(imageSpan, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		ToggleButton materialButton = findViewById(R.id.toggleDropMaterial);
		materialButton.setText(content);
		materialButton.setTextOn(content);
		materialButton.setTextOff(content);
		materialButton.setOnCheckedChangeListener(mCheckedChangeListener);

		mMovementStep = findViewById(R.id.inputMovementStep);
		mRotationStep = findViewById(R.id.inputRotationStep);
		mManualCommand = findViewById(R.id.inputManualCommand);

		mImageSize = findViewById(R.id.seekImageSize);
		mIconButton = findViewById(R.id.buttonLoadImage);
		mCameraButton = findViewById(R.id.buttonTakePicture);

		mLabelCommandHistory = findViewById(R.id.labelCommandHistory);
		mLabelCommandHistory.setText(getString(R.string.label_command_list, 0));
		mLabelCommandHistory.setOnLongClickListener(mLongClickListener);
		mTextCommandHistory = findViewById(R.id.textCommandHistory);
		mTextCommandHistory.setOnLongClickListener(mLongClickListener);
		mTextSustainabotOutput = findViewById(R.id.textSustainabotOutput);
		mTextSustainabotOutput.setOnLongClickListener(mLongClickListener);
		findViewById(R.id.labelSustainabotOutput).setOnLongClickListener(mLongClickListener);

		mScrollCommandHistory = findViewById(R.id.scrollCommandHistory);
		mScrollSustainabotOutput = findViewById(R.id.scrollSustainabotOutput);

		// startup - load default values (otherwise keep user's entered value)
		if (savedInstanceState == null) {
			mMovementStep.setText(String.valueOf(DEFAULT_PROGRESS_STEP));
			mRotationStep.setText(String.valueOf(DEFAULT_PROGRESS_STEP));
		}

		// add other action listeners
		((SeekBar) findViewById(R.id.seekMovementStep)).setOnSeekBarChangeListener(mSeekChangeListener);
		((SeekBar) findViewById(R.id.seekRotationStep)).setOnSeekBarChangeListener(mSeekChangeListener);
		((Switch) findViewById(R.id.switchHeading)).setOnCheckedChangeListener(mCheckedChangeListener);
		((Switch) findViewById(R.id.switchUseCorrection)).setOnCheckedChangeListener(mCheckedChangeListener);
		mMode = findViewById(R.id.switchMode);
		mMode.setOnCheckedChangeListener(mCheckedChangeListener);
		switchMode(mMode.isChecked());

		// connect to a device
		startServiceAndPromptConnection();
	}

	@Override
	public void onBackPressed() {
		// TODO: hacky!
		View fab = findViewById(R.id.fab);
		if (fab.getVisibility() == View.VISIBLE) {
			super.onBackPressed();
		} else {
			mIconButton.setOnClickListener(mDisconnectedButtonListener);
			mCameraButton.setOnClickListener(mDisconnectedButtonListener);
			findViewById(R.id.user_views).setVisibility(View.GONE);
			findViewById(R.id.fab).setVisibility(View.VISIBLE);
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		mBluetoothSPP.stopService();
	}

	private void startServiceAndPromptConnection() {
		Log.d(TAG, "Starting Bluetooth communicator service");
		if (!mBluetoothSPP.isServiceAvailable()) {
			mBluetoothSPP.setupService();
			mBluetoothSPP.startService(BluetoothState.DEVICE_OTHER);
			mBluetoothSPP.setOnDataReceivedListener(mBluetoothDataListener);
			mBluetoothSPP.setBluetoothConnectionListener(mBluetoothConnectionListener);
			mBluetoothSPP.setAutoConnectionListener(mBluetoothAutoConnectionListener);
			mBluetoothSPP.setBluetoothStateListener(mBluetoothStateListener);
		}

		Intent intent = new Intent(MainActivity.this, DeviceListActivity.class);
		startActivityForResult(intent, BluetoothState.REQUEST_CONNECT_DEVICE);
	}

	private void addMovementCommand(Sustainabot.Command command, int value, boolean fromUser) {
		if (mMode.isChecked()) {
			// clear any existing list, then send the command and run it
			sendCommandToDevice(Sustainabot.Command.Clear, 0);
			sendCommandToDevice(command, value);
			sendCommandToDevice(Sustainabot.Command.Execute, 0);
		} else {
			mTextCommandHistory.append(formatCommand(command, value) + "\n");
			int numCommands = mTextCommandHistory.getText().toString().split("\n").length;
			mLabelCommandHistory.setText(getString(R.string.label_command_list, numCommands));
			if (fromUser) {
				mScrollCommandHistory.post(mCommandHistoryScrollRunnable);
			}
			sendCommandToDevice(command, value);
		}
	}

	private void sendCommandToDevice(Sustainabot.Command command, int value) {
		// TODO: check number is 0 <= x <= 255
		sendCommandToDevice(formatCommand(command, value));
	}

	private void sendCommandToDevice(String command) {
		if (command != null) {
			mCommandList.add(command); // add non-null commands; try to run others
			if (mImageProgressDialog != null) {
				mImageProgressDialog.setMaxProgress(mImageProgressDialog.getMaxProgress() + 1);
			}
		}
		if (mCommandList.size() <= 0) {
			return; // nothing to do - we've combined all commands so there are no more to process
		}

		// run the command if we've received a line ending from the device (i.e., has processed the previous command)
		synchronized (mDataLock) {
			if (mLineEnded) {
				String nextCommand = mCommandList.remove(0);

				// combine duplicate commands - could preprocess these, but this allows us to start sending immediately
				if (OPTIMISE_COMMANDS && mCommandList.size() > 0) {
					String commandKey = mCommandList.get(0).substring(0, 1);
					if ("F".equals(commandKey) || "B".equals(commandKey) || "@".equals(commandKey)) {
						while (nextCommand.startsWith(commandKey)) {
							String repeatedCommand = mCommandList.remove(0);
							int currentValue = Integer.parseInt(nextCommand.substring(1));
							int nextValue = Integer.parseInt(repeatedCommand.substring(1));
							String newCommand = formatCommand(commandKey, currentValue + nextValue);
							Log.d(TAG, "Combining repeated commands " + nextCommand + " and " + repeatedCommand +
									" into " + newCommand);
							if (mImageProgressDialog != null) {
								mImageProgressDialog.incrementProgress(1);
							}
							nextCommand = newCommand;
							if (mCommandList.size() > 0) {
								commandKey = mCommandList.get(0).substring(0, 1);
							} else {
								break;
							}
						}

						// cancel out forwards/backwards TODO: OPTIMISE THIS
						if (mCommandList.size() > 0) {
							commandKey = nextCommand.substring(0, 1);
							String nextCommandKey = mCommandList.get(0).substring(0, 1);
							while (("F".equals(commandKey) && "B".equals(nextCommandKey)) ||
									("B".equals(commandKey) && "F".equals(nextCommandKey))) {
								int newValue = 0;
								String unnecessaryCommand = mCommandList.remove(0);
								if ("F".equals(commandKey)) {
									newValue = Integer.parseInt(nextCommand.substring(1)) -
											Integer.parseInt(unnecessaryCommand.substring(1));
								} else if ("B".equals(commandKey)) {
									newValue = Integer.parseInt(unnecessaryCommand.substring(1)) -
											Integer.parseInt(nextCommand.substring(1));
								}
								if (newValue < 0) {
									Log.d(TAG,
											"Combining F/B (1) " + nextCommand + ", " + unnecessaryCommand + " into " +
													"B" + Math.abs(newValue));
									nextCommand = formatCommand("B", Math.abs(newValue));
								} else if (newValue > 0) {
									Log.d(TAG,
											"Combining F/B (2)" + nextCommand + ", " + unnecessaryCommand + " into F" +
													newValue);
									nextCommand = formatCommand("F", newValue);
								}
								if (mImageProgressDialog != null) {
									mImageProgressDialog.incrementProgress(newValue == 0 ? 2 : 1);
								}
								if (newValue == 0) {
									Log.d(TAG, "Combining F/B " + nextCommand + ", " + unnecessaryCommand +
											" into no command");
									nextCommand = null;
									break;
								}
								commandKey = nextCommand.substring(0, 1);
								if (mCommandList.size() > 0) {
									nextCommandKey = mCommandList.get(0).substring(0, 1);
								} else {
									break;
								}
							}
						}
					}
				}

				// we might have optimised the new command away
				if (nextCommand != null) {
					// make sure we don't overflow single command byte - will continue splitting next time if needed
					String commandKey = nextCommand.substring(0, 1);
					switch (commandKey) {
						case "F":
						case "B":
						case "@":
							int currentValue = Integer.parseInt(nextCommand.substring(1));
							if (currentValue > 255) {
								String overflowCommand = formatCommand(commandKey, currentValue - 255);
								currentValue = 255;
								mCommandList.add(0, overflowCommand);
								Log.d(TAG, "Splitting overflowing command " + nextCommand + " into " + commandKey +
										currentValue + " and " + overflowCommand);
							}

							switch (commandKey) {
								case "F":
								case "B":
									// scale forward/backward so they are as equal as possible, even at small distances
									int newValue = scaleMovement(currentValue);
									Log.d(TAG,
											"Scaling " + commandKey + " movement " + currentValue + " to " + newValue);

									if (newValue == 0) {
										nextCommand = null;
									} else if (newValue < 0) {
										switch (commandKey) {
											case "F":
												nextCommand = formatCommand("B", newValue);
												break;
											case "B":
												nextCommand = formatCommand("F", newValue);
												break;
										}
									} else {
										nextCommand = formatCommand(commandKey, newValue);
									}
									break;

								default:
									break;
							}
							break;

						default:
							// TODO: handle overflow of other commands, e.g., left/right/heading/lanes
							break;
					}

					if (nextCommand != null) {
						// execute the command
						Log.d(TAG, "Sending command to Sustainabot: $" + nextCommand);
						mLineEnded = false;
						mBluetoothSPP.send("$" + nextCommand, false);

						if (mImageProgressDialog != null) {
							if (nextCommand.startsWith("G")) {
								mImageProgressDialog.dismiss();
								mImageProgressDialog = null;
							} else {
								mImageProgressDialog.incrementProgress(1);
							}
						}
					}
				}
			}
		}

		// queue up any remaining commands
		if (mCommandList.size() > 0) {
			mCommandHandler.removeCallbacks(mSendCommandRunnable);
			mCommandHandler.postDelayed(mSendCommandRunnable, 10);
		}
	}

	private Runnable mSendCommandRunnable = new Runnable() {
		@Override
		public void run() {
			// Log.d(TAG, "Running next delayed command");
			sendCommandToDevice(null);
		}
	};

	private String formatCommand(Sustainabot.Command command, int value) {
		return formatCommand(Sustainabot.getCommandString(command), value);
	}

	private String formatCommand(String command, int value) {
		return command + String.format(Locale.US, "%03d", value);
	}

	/**
	 * Calculating this value (which may be different per device). Easiest with a tool such as, e.g.:
	 * https://mycurvefit.com/
	 * <p>
	 * 1) Measure the *actual* moved distance at steps of the maximum movement. E.g., for max = 231:
	 * 231 = 8.3cm
	 * 116 = 4.2cm
	 * 58 = 2.1cm
	 * 29 = 1.1cm
	 * 14 = 0.6cm
	 * 7 = 0.4cm
	 * <p>
	 * <p>
	 * 2) Use the tool to calculate the curve from the measured numbers. Then, scale this to the maximum movement's
	 * distance (e.g., 231) to calculate the actual movement
	 * = (72.19626 + (0.2057699 - 72.19626)/(1 + (116/1470.541)^1.11622))*(231/8.3)
	 * <p>
	 * <p>
	 * 3) Fit a curve with those numbers, e.g.:
	 * 231 = 231
	 * 116 = 117
	 * 58 = 60
	 * 29 = 31
	 * 14 = 16
	 * 7 = 10
	 * <p>
	 * <p>
	 * 4) Use the fitted curve on every movement instruction to calculate the actual movement that should be made.
	 * E.g., for 231, use either:
	 * Linear regression: y = 1.011523 * x - 2.559665
	 * Polynomial quadratic: y = -2.70441 + 1.016806*x - 0.00002206869*x^2
	 */
	// TODO: need to include battery charge in this calculation. Version 2 of embedded code will automatically include
	// TODO: this in its initial calibration step
	private int scaleMovement(int movement) {
		return (int) Math.round(
				-2.70441 + 1.016806 * (double) movement - 0.00002206869 * Math.pow((double) movement, 2));
	}

	private Runnable mCommandHistoryScrollRunnable = new Runnable() {
		@Override
		public void run() {
			mScrollCommandHistory.fullScroll(ScrollView.FOCUS_DOWN);
		}
	};

	private Runnable mSustainabotOutputScrollRunnable = new Runnable() {
		@Override
		public void run() {
			mScrollSustainabotOutput.fullScroll(ScrollView.FOCUS_DOWN);
		}
	};

	public void handleClick(View view) {
		switch (view.getId()) {
			case R.id.buttonForward:
				addMovementCommand(Sustainabot.Command.Forward, getMovementStep(), true);
				break;

			case R.id.buttonBackward:
				addMovementCommand(Sustainabot.Command.Backward, getMovementStep(), true);
				break;

			case R.id.buttonLeft:
				if (mUseHeading) {
					int newLeftHeading = 360 - getRotationStep();
					addMovementCommand(Sustainabot.Command.TurnToHeading, scaleHeadingToDevice(
							mHeadingOffset + mCurrentHeading + newLeftHeading), true);
					mCurrentHeading += newLeftHeading;
				} else {
					addMovementCommand(Sustainabot.Command.ConfigSetTurnStepLeftRight, 5, false);
					addMovementCommand(Sustainabot.Command.TurnLeft, getRotationStep(), true);
				}
				break;

			case R.id.buttonRight:
				if (mUseHeading) {
					int newRightHeading = getRotationStep();
					addMovementCommand(Sustainabot.Command.TurnToHeading, scaleHeadingToDevice(
							mHeadingOffset + mCurrentHeading + newRightHeading), true);
					mCurrentHeading += newRightHeading;
				} else {
					addMovementCommand(Sustainabot.Command.ConfigSetTurnStepLeftRight, 5, false);
					addMovementCommand(Sustainabot.Command.TurnRight, getRotationStep(), true);
				}
				break;

			case R.id.buttonCalibrateCompass:
				sendCommandToDevice(Sustainabot.Command.CalibrateCompass, 100);
				break;

			case R.id.buttonGetBatteryVoltage:
				sendCommandToDevice(Sustainabot.Command.GetBatteryVoltage, 0);
				break;

			case R.id.buttonGetCurrentHeading:
				mSetNextHeadingAsOffset = true;
				sendCommandToDevice(Sustainabot.Command.GetHeading, 0);
				break;

			case R.id.buttonManualCommand:
				String manualCommand = mManualCommand.getText().toString();
				if (manualCommand.length() == 4) {
					mManualCommand.setError(null);
					mTextSustainabotOutput.append(
							"[ Manual command: " + manualCommand + "]\n"); // just so we know is sent
					sendCommandToDevice(manualCommand);
				} else {
					mManualCommand.setError("Command must be 4 characters long");
				}
				break;

			case R.id.buttonSendCommands:
				synchronized (mDataLock) {
					mLineEnded = true; // we always want to send the execute command
				}
				sendCommandToDevice(Sustainabot.Command.Execute, 0);
				break;

			case R.id.buttonTestDeadStop:
				break; // currently unused - ideally this would act as an interrupt (e.g., like the reed switch does)

			case R.id.buttonTestForward:
				sendCommandToDevice(Sustainabot.Command.Clear, 0);
				sendCommandToDevice(Sustainabot.Command.Forward, 255);
				synchronized (mDataLock) {
					mLineEnded = true; // we always want to send the execute command
				}
				sendCommandToDevice(Sustainabot.Command.Execute, 0);
				break;

			case R.id.buttonTestBackward:
				sendCommandToDevice(Sustainabot.Command.Clear, 0);
				sendCommandToDevice(Sustainabot.Command.Backward, 255);
				synchronized (mDataLock) {
					mLineEnded = true; // we always want to send the execute command
				}
				sendCommandToDevice(Sustainabot.Command.Execute, 0);
				break;

			case R.id.buttonIncreasePulseLeftForward:
				mLeftForwardCorrection += 1;
				if (mLeftForwardCorrection > 255) {
					mLeftForwardCorrection = 255;
				}
				sendCommandToDevice(Sustainabot.Command.ConfigSetLeftForwardCalibration, mLeftForwardCorrection);
				break;
			case R.id.buttonDecreasePulseLeftForward:
				mLeftForwardCorrection -= 1;
				if (mLeftForwardCorrection < 0) {
					mLeftForwardCorrection = 0;
				}
				sendCommandToDevice(Sustainabot.Command.ConfigSetLeftForwardCalibration, mLeftForwardCorrection);
				break;

			case R.id.buttonIncreasePulseLeftBackward:
				mLeftBackwardCorrection += 1;
				if (mLeftBackwardCorrection > 255) {
					mLeftBackwardCorrection = 255;
				}
				sendCommandToDevice(Sustainabot.Command.ConfigSetLeftBackwardCalibration, mLeftBackwardCorrection);
				break;
			case R.id.buttonDecreasePulseLeftBackward:
				mLeftBackwardCorrection -= 1;
				if (mLeftBackwardCorrection < 0) {
					mLeftBackwardCorrection = 0;
				}
				sendCommandToDevice(Sustainabot.Command.ConfigSetLeftBackwardCalibration, mLeftBackwardCorrection);
				break;

			case R.id.buttonIncreasePulseRightForward:
				mRightForwardCorrection += 1;
				if (mRightForwardCorrection > 255) {
					mRightForwardCorrection = 255;
				}
				sendCommandToDevice(Sustainabot.Command.ConfigSetRightForwardCalibration, mRightForwardCorrection);
				break;
			case R.id.buttonDecreasePulseRightForward:
				mRightForwardCorrection -= 1;
				if (mRightForwardCorrection < 0) {
					mRightForwardCorrection = 0;
				}
				sendCommandToDevice(Sustainabot.Command.ConfigSetRightForwardCalibration, mRightForwardCorrection);
				break;
			case R.id.buttonIncreasePulseRightBackward:
				mRightBackwardCorrection += 1;
				if (mRightBackwardCorrection > 255) {
					mRightBackwardCorrection = 255;
				}
				sendCommandToDevice(Sustainabot.Command.ConfigSetRightBackwardCalibration, mRightBackwardCorrection);
				break;
			case R.id.buttonDecreasePulseRightBackward:
				mRightBackwardCorrection -= 1;
				if (mRightBackwardCorrection < 0) {
					mRightBackwardCorrection = 0;
				}
				sendCommandToDevice(Sustainabot.Command.ConfigSetRightBackwardCalibration, mRightBackwardCorrection);
				break;
		}
	}

	private View.OnClickListener mUserButtonClickListener = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			switch (v.getId()) {
				case R.id.buttonLoadImage:
					// always get the heading when in position in case we want to use heading mode
					mSetNextHeadingAsOffset = true;
					sendCommandToDevice(Sustainabot.Command.GetHeading, 0);
					startActivityForResult(new Intent(getApplicationContext(), IconActivity.class), ICON_RESULT);
					break;

				case R.id.buttonTakePicture:
					break;
			}
		}
	};

	private View.OnClickListener mDisconnectedButtonListener = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			showDisconnectedErrorMessage();
		}
	};

	private int getMovementStep() {
		int movementStep = DEFAULT_PROGRESS_STEP;
		try {
			movementStep = Integer.parseInt(mMovementStep.getText().toString());
		} catch (NumberFormatException ignored) {
		}
		return movementStep;
	}

	private int getRotationStep() {
		int movementStep = DEFAULT_PROGRESS_STEP;
		try {
			movementStep = Integer.parseInt(mRotationStep.getText().toString());
		} catch (NumberFormatException ignored) {
		}
		return movementStep;
	}

	private int scaleHeadingFromDevice(int heading) {
		return Math.round(((float) heading / 256f) * 360f) % 360;
	}

	private int scaleHeadingToDevice(int heading) {
		return Math.round(((float) heading / 360f) * 256f) % 256;
	}

	private void switchMode(boolean immediateMode) {
		if (immediateMode) {
			mMode.setText(R.string.hint_send_commands_immediately);
			findViewById(R.id.fab).setVisibility(View.GONE);
			findViewById(R.id.labelCommandHistory).setVisibility(View.GONE);
			findViewById(R.id.testingControlsLayout).setVisibility(View.VISIBLE);
			mScrollCommandHistory.setVisibility(View.GONE);
			findViewById(R.id.buttonSendCommands).setVisibility(View.GONE);
		} else {
			mMode.setText(R.string.hint_build_command_list);
			findViewById(R.id.fab).setVisibility(View.VISIBLE);
			findViewById(R.id.labelCommandHistory).setVisibility(View.VISIBLE);
			findViewById(R.id.testingControlsLayout).setVisibility(View.GONE);
			mScrollCommandHistory.setVisibility(View.VISIBLE);
			findViewById(R.id.buttonSendCommands).setVisibility(View.VISIBLE);
		}
	}

	private View.OnLongClickListener mLongClickListener = new View.OnLongClickListener() {
		@Override
		public boolean onLongClick(View v) {
			switch (v.getId()) {
				case R.id.labelCommandHistory:
				case R.id.textCommandHistory:
					sendCommandToDevice(Sustainabot.Command.Clear, 0);
					mTextCommandHistory.setText("");
					mLabelCommandHistory.setText(getString(R.string.label_command_list, 0));
					return true;

				case R.id.labelSustainabotOutput:
				case R.id.textSustainabotOutput:
					sendCommandToDevice(Sustainabot.Command.List, 0);
					return true;
			}
			return false;
		}
	};

	private SeekBar.OnSeekBarChangeListener mSeekChangeListener = new SeekBar.OnSeekBarChangeListener() {
		@Override
		public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

			int calculatedProgress = DEFAULT_PROGRESS_STEP * (progress + 1);
			switch (seekBar.getId()) {
				case R.id.seekMovementStep:
					mMovementStep.setText(String.valueOf(calculatedProgress));
					break;

				case R.id.seekRotationStep:
					mRotationStep.setText(String.valueOf(calculatedProgress));
					break;
			}
		}

		@Override
		public void onStartTrackingTouch(SeekBar seekBar) {
			// nothing to do
		}

		@Override
		public void onStopTrackingTouch(SeekBar seekBar) {
			// nothing to do
		}
	};

	private CompoundButton.OnCheckedChangeListener mCheckedChangeListener =
			new CompoundButton.OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			switch (buttonView.getId()) {
				case R.id.switchMode:
					switchMode(isChecked);
					break;

				case R.id.toggleDropMaterial:
					addMovementCommand(isChecked ? Sustainabot.Command.StopFullDrop :
							Sustainabot.Command.StartFullDrop, 0, true);
					break;

				case R.id.switchHeading:
					mUseHeading = isChecked;
					if (mUseHeading) {
						mSetNextHeadingAsOffset = true;
						sendCommandToDevice(Sustainabot.Command.GetHeading, 0);
					}

				case R.id.switchUseCorrection:
					mMovementCorrection = isChecked ? getMovementStep() : 0;
			}
		}
	};

	private BluetoothSPP.OnDataReceivedListener mBluetoothDataListener = new BluetoothSPP.OnDataReceivedListener() {
		public void onDataReceived(byte[] data, String message) {
			// trim to max 100 lines for speed
			String totalOutput = mTextSustainabotOutput.getText().toString() + message; // + "\n";
			int lineCount = totalOutput.length() - totalOutput.replace("\n", "").length();
			if (lineCount > 50) {
				totalOutput = totalOutput.substring(totalOutput.indexOf("\n") + 1);
			}
			mTextSustainabotOutput.setText(totalOutput);
			if (mMode.isChecked()) {
				mScrollSustainabotOutput.post(mSustainabotOutputScrollRunnable);
			}
		}

		@Override
		public void onLineEndReceived(String message) {
			synchronized (mDataLock) {
				if (mSetNextHeadingAsOffset) {
					if (message.startsWith("$Z")) {
						if (mNumHeadingOffsetsRead < 1) { // need to read twice to get an accurate value
							mNumHeadingOffsetsRead += 1;
							sendCommandToDevice(Sustainabot.Command.GetHeading, 0);
							Log.d(TAG, "Read first heading offset value - requesting second time");
						} else {
							mCurrentHeading = 0; // this is now our zero point
							mHeadingOffset = scaleHeadingFromDevice(Integer.parseInt(message.substring(2, 5)));
							mSetNextHeadingAsOffset = false;
							mNumHeadingOffsetsRead = 0;
							Log.d(TAG, "Heading offset for future commands set to " + mHeadingOffset + " degrees");
							if (EXPERT_INTERFACE_MODE) {
								Snackbar.make(findViewById(R.id.toolbar),
										"Heading offset for future commands set to " + mHeadingOffset +
												" degrees", Snackbar.LENGTH_LONG).show();
							}
						}
					}
				}

				if (mCheckClearSucceeded) {
					// connected and no need to initialise magnetometer by pressing button
					if ("Clear instruction set".equals(message) || "End compass calibration".equals(message)) {
						mTextSustainabotOutput.setText(getString(R.string.hint_connected, "\n"));
						mCheckClearSucceeded = false;
					}
				}

				// this is a movement command confirmation
				if (message.startsWith("#")) {
					Log.d(TAG, "Received movement confirmation: " + message);
				} else if (message.startsWith("LW1")) {
					mLeftForwardCorrection = Integer.parseInt(message.split(" ")[1]);
					Log.d(TAG, "Updated left wheel forward correction to " + mLeftForwardCorrection);
				} else if (message.startsWith("LW2")) {
					mLeftBackwardCorrection = Integer.parseInt(message.split(" ")[1]);
					Log.d(TAG, "Updated left wheel backward correction to " + mLeftBackwardCorrection);
				} else if (message.startsWith("RW2")) { // note right is reversed RW2 vs RW1
					mRightForwardCorrection = Integer.parseInt(message.split(" ")[1]);
					Log.d(TAG, "Updated right wheel forward correction to " + mRightForwardCorrection);
				} else if (message.startsWith("RW1")) { // note right is reversed RW2 vs RW1
					mRightBackwardCorrection = Integer.parseInt(message.split(" ")[1]);
					Log.d(TAG, "Updated right wheel backward correction to " + mRightBackwardCorrection);
				} else if (message.startsWith("Battery voltage")) {
					if (EXPERT_INTERFACE_MODE) {
						String voltageCalculation = message.split("voltage ")[1];
						String[] calculationParts = voltageCalculation.split("\\*");
						String[] calculationStart = calculationParts[0].split("/");
						String[] calculationEnd = calculationParts[1].split("-");
						float voltageCurrent = Integer.parseInt(calculationStart[0]);
						float voltageMax = Integer.parseInt(calculationStart[1]);
						float scaleFactor = Float.parseFloat(calculationEnd[0]);
						float adjustment = Float.parseFloat(calculationEnd[1]);
						float currentVoltage = voltageCurrent / voltageMax * scaleFactor - adjustment;
						Snackbar.make(findViewById(R.id.toolbar),
								"Current voltage: " + currentVoltage, Snackbar.LENGTH_LONG).show();
					}
				} else if (message.startsWith("Waiting for instruction")) {
					// nothing to do
				} else {
					Log.d(TAG, "Received other new line: " + message);
					//mLineEnded = !TextUtils.isEmpty(message); // ignore empty newlines - paired with other commands
				}

				mLineEnded = true;
			}
		}
	};

	private BluetoothSPP.BluetoothConnectionListener mBluetoothConnectionListener =
			new BluetoothSPP.BluetoothConnectionListener() {
		@Override
		public void onDeviceConnected(String name, String address) {
			Log.d(TAG, "Device connected: " + name + ", " + address);
			findViewById(R.id.connection_views).setVisibility(View.GONE);
			if (EXPERT_INTERFACE_MODE) {
				findViewById(R.id.fab).setVisibility(View.VISIBLE);
				findViewById(R.id.control_views).setVisibility(View.VISIBLE);
			} else {
				mIconButton.setOnClickListener(mUserButtonClickListener);
				mCameraButton.setOnClickListener(mUserButtonClickListener);
				findViewById(R.id.user_views).setVisibility(View.VISIBLE);
			}

			mTextSustainabotOutput.setText(R.string.hint_start_configuration);
			mCheckClearSucceeded = true;
			sendCommandToDevice(Sustainabot.Command.Clear, 0); // clear existing command list
		}

		@Override
		public void onDeviceDisconnected() {
			Log.d(TAG, "Device disconnected");
			if (mImageProgressDialog != null) {
				mImageProgressDialog.dismiss();
				mImageProgressDialog = null;
			}
			mIconButton.setOnClickListener(mDisconnectedButtonListener);
			mCameraButton.setOnClickListener(mDisconnectedButtonListener);
			showDisconnectedErrorMessage();
		}

		@Override
		public void onDeviceConnectionFailed() {
			mIconButton.setOnClickListener(mDisconnectedButtonListener);
			mCameraButton.setOnClickListener(mDisconnectedButtonListener);
			Log.d(TAG, "Device connection failed - retrying");
			showDisconnectedErrorMessage();
		}
	};

	private void showDisconnectedErrorMessage() {
		Snackbar.make(findViewById(R.id.toolbar), "Error: Sustainabot disconnected", Snackbar.LENGTH_INDEFINITE)
				.setAction("Reconnect", new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						SharedPreferences preferences = getPreferences(Context.MODE_PRIVATE);
						String defaultDeviceAddress = preferences.getString(DeviceListActivity.KEY_DEFAULT_SUSTAINABOT
								, null);
						if (TextUtils.isEmpty(defaultDeviceAddress)) {
							// show connection view again if we don't have a default
							mSustainabotAddress = null;
							Intent intent = new Intent(MainActivity.this, DeviceListActivity.class);
							startActivityForResult(intent, BluetoothState.REQUEST_CONNECT_DEVICE);
						} else {
							// TODO: view hiding/showing is hacky - fix (and redo when connection lost)
							if (!EXPERT_INTERFACE_MODE) {
								findViewById(R.id.user_views).setVisibility(View.GONE);
								findViewById(R.id.connection_views).setVisibility(View.VISIBLE);
							}
							mBluetoothSPP.connect(mSustainabotAddress);
						}
					}
				})
				.show();
	}

	private BluetoothSPP.AutoConnectionListener mBluetoothAutoConnectionListener =
			new BluetoothSPP.AutoConnectionListener() {
		@Override
		public void onAutoConnectionStarted() {
			Log.d(TAG, "Auto connection started");
		}

		@Override
		public void onNewConnection(String name, String address) {
			Log.d(TAG, "New auto connection: " + name + ", " + address);
		}
	};

	private BluetoothSPP.BluetoothStateListener mBluetoothStateListener = new BluetoothSPP.BluetoothStateListener() {
		@Override
		public void onServiceStateChanged(int state) {
			Log.d(TAG, "Service state changed to: " + state);
		}
	};

	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
			case BluetoothState.REQUEST_CONNECT_DEVICE:
				if (resultCode == Activity.RESULT_OK) {
					String address = data.getStringExtra(BluetoothState.EXTRA_DEVICE_ADDRESS);
					Log.d(TAG, "Connecting to " + address);
					mSustainabotAddress = address;
					mBluetoothSPP.connect(mSustainabotAddress);

					// TODO: view hiding/showing is hacky - fix (and redo when connection lost)
					findViewById(R.id.connection_views).setVisibility(View.VISIBLE);
					findViewById(R.id.control_views).setVisibility(View.GONE);
					findViewById(R.id.fab).setVisibility(View.GONE);
					findViewById(R.id.user_views).setVisibility(View.GONE);
					break;
				} else {
					// they pressed back
					finish();
					// previously, we did: startServiceAndPromptConnection();
					break;
				}

			case ICON_RESULT:
				if (resultCode == Activity.RESULT_OK) {
					mImageFile = data.getStringExtra(RESULT_IMAGE);
					if (mImageFile != null) {
						if (START_WHEN_LOADED) {
							mImageProgressDialog =
									new MaterialDialog.Builder(MainActivity.this).title(R.string.sending)
									.content(R.string.sending_picture_to_sustainabot)
									.progress(false, 0, false)
									.cancelable(false)
									.show();
						}

						loadAndDrawImage(START_WHEN_LOADED);
					} else {
						Log.d(TAG, "Image load error");
					}
				}
				break;

			default:
				super.onActivityResult(requestCode, resultCode, data);
		}
	}

	private void loadAndDrawImage(@SuppressWarnings("SameParameterValue") boolean startWhenLoaded) {
		Bitmap image = loadImage(mImageFile);
		if (image != null) {
			boolean[][] resultMap = imageToDots(image);
			draw(resultMap, startWhenLoaded);
		} else {
			Log.d(TAG, "Error loading image");
		}
	}

	private Bitmap loadImage(String fileName) {
		FileInputStream fileInputStream = null;
		try {
			fileInputStream = openFileInput(fileName);
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inMutable = true;
			return BitmapFactory.decodeStream(fileInputStream, null, options);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		} finally {
			if (fileInputStream != null) {
				try {
					fileInputStream.close();
				} catch (IOException ignored) {
				}
			}
		}
	}

	private boolean[][] imageToDots(Bitmap inputImage) {
		int outputSize = (mImageSize.getProgress() * 2) + 11; // 11 min, 17 max
		//Log.d(TAG, inputImage.getWidth() + ", " + inputImage.getHeight());

		int width, height;
		int maxSize = Math.max(inputImage.getWidth(), inputImage.getHeight());
		int dotSize = maxSize / outputSize;
		//noinspection UnusedAssignment - only used when DEBUG_IMAGE_CREATION is enabled
		int halfDot = dotSize / 2;
		if (maxSize == inputImage.getWidth()) {
			width = outputSize;
			height = Math.round(inputImage.getHeight() / (inputImage.getWidth() / (float) outputSize));
		} else {
			width = Math.round(inputImage.getWidth() / (inputImage.getHeight() / (float) outputSize));
			height = outputSize;
		}
		Bitmap tempImage = Bitmap.createScaledBitmap(inputImage, width, height, false);

		Canvas debugCanvas = null;
		Bitmap debugBitmap = null;
		if (DEBUG_IMAGE_CREATION) {
			//noinspection UnusedAssignment - only used when DEBUG_IMAGE_CREATION is enabled
			debugBitmap = inputImage.copy(inputImage.getConfig(), true);
			//noinspection UnusedAssignment - only used when DEBUG_IMAGE_CREATION is enabled
			debugCanvas = new Canvas(debugBitmap);
			debugCanvas.drawColor(Color.WHITE);
		}
		Paint paint = new Paint();
		boolean[][] resultMap = new boolean[height][width]; // width/height switched for device to from bottom right
		for (int i = 0; i < width; i++) {
			for (int j = 0; j < height; j++) {
				// sometimes thresholding fails and gives original colours, so just check not white
				int currentColour = tempImage.getPixel(i, j);
				final boolean currentValue = currentColour != Color.WHITE && currentColour != Color.TRANSPARENT;
				resultMap[j][i] = currentValue;
				paint.setColor(currentValue ? Color.BLACK : Color.WHITE);
				if (DEBUG_IMAGE_CREATION) {
					debugCanvas.drawCircle((i * dotSize) + halfDot, (j * dotSize) + halfDot, halfDot, paint);
				}
			}
		}

		// invert picture if there are more dots than non-dots
		//if (numDots > (height * width) / 2) {
		//	for (int i = 0; i < width; i++) {
		//		for (int j = 0; j < height; j++) {
		//			resultMap[j][i] = !resultMap[j][i];
		//		}
		//	}
		//}

		// the picture is the wrong way around now we start from bottom right
		for (int i = resultMap.length - 1; i >= 0; i--) {
			resultMap[i] = reverseImageRow(resultMap[i]);
		}

		if (DEBUG_IMAGE_CREATION) {
			//noinspection UnusedAssignment - only used when DEBUG_IMAGE_CREATION is enabled
			ImageView testImage = findViewById(R.id.testImage);
			testImage.setImageBitmap(debugBitmap);
			testImage.setVisibility(View.VISIBLE);
		}

		return resultMap;
	}

	private boolean[] reverseImageRow(boolean[] imageRow) {
		for (int i = 0; i < imageRow.length / 2; i++) {
			boolean temp = imageRow[i];
			imageRow[i] = imageRow[imageRow.length - i - 1];
			imageRow[imageRow.length - i - 1] = temp;
		}
		return imageRow;
	}

	private void drawDots(int numDrops, int preDropMovement, int inDropMovement, int dropTurnCorrection,
						  int postDropMovement) {

		addMovementCommand(Sustainabot.Command.DrawDot, numDrops, false);

		// alternatively, do this manually:
		// for (int d = 0; d < numDrops; d++) {
		// 	addMovementCommand(Sustainabot.Command.Forward, preDropMovement, false);
		// 	addMovementCommand(Sustainabot.Command.StartFullDrop, 0, false);
		// 	addMovementCommand(Sustainabot.Command.Forward, inDropMovement, false);
		// 	addMovementCommand(Sustainabot.Command.StopFullDrop, 0, false);
		// 	addMovementCommand(Sustainabot.Command.TurnRight, dropTurnCorrection, false);
		// 	addMovementCommand(Sustainabot.Command.Forward, postDropMovement, false);
		// }
	}

	private void draw(boolean[][] resultMap, boolean startWhenLoaded) {
		int shakeTime = 30; // how much to shake at the end of each line
		int preDropMovement = 7; // for each drop, how much to move before opening the chute
		int dropOpenAmount = 100; // for each drop, how much to open the chute cover by
		int inDropMovement = 7; // for each drop, how much to move while dropping material
		int dropCloseAmount = 100; // for each drop, how much to close the chute cover by
		int dropTurnCorrection = 2; // moving the chute motor can cause a small left turn; correct right by this amount
		int postDropMovement = 7; //for each drop, how much to move after opening the chute

		int changeLanesForward = 68; // how much to move forward when changing lanes
		int changesLanesBackward = 70; // how much to move backward when changing lanes
		int turnLeftMovement = 100; // how much to left turn to change lanes (default = 100); not for heading
		int turnRightMovement = 100; // how much to right turn to straight after changing lanes; not for heading

		sendCommandToDevice(Sustainabot.Command.Clear, 0);

		// set movement steps so O/P can be more precise (1 = highest precision; 255 = lowest)
		if (mUseHeading) {
			sendCommandToDevice(Sustainabot.Command.ConfigSetTurnStepHeading, 7); // 7 = default
		} else {
			sendCommandToDevice(Sustainabot.Command.ConfigSetTurnStepLeftRight, 1);
		}

		// how much to shake the salt at the end of each line
		sendCommandToDevice(Sustainabot.Command.ConfigSetShakeTime, shakeTime);

		// how far to open the chute when dropping
		sendCommandToDevice(Sustainabot.Command.ConfigDropMovementOpen, dropOpenAmount);
		sendCommandToDevice(Sustainabot.Command.ConfigDropMovementClose, dropCloseAmount);

		// configure '@' command - before, during and after drop amount
		// drop correction = turn after dropping: 100 = no correction; 99 = left, 101 = right
		sendCommandToDevice(Sustainabot.Command.ConfigPreDropAmount, preDropMovement);
		sendCommandToDevice(Sustainabot.Command.ConfigInDropAmount, inDropMovement);
		sendCommandToDevice(Sustainabot.Command.ConfigPostDropAmount, postDropMovement);
		addMovementCommand(Sustainabot.Command.ConfigDropCorrectionAmount, 100 + dropTurnCorrection, false);
		addMovementCommand(Sustainabot.Command.ConfigDropCorrectionInterval, 1, false);

		// automatic turn, move and reset to switch to the next row of the image
		sendCommandToDevice(Sustainabot.Command.ConfigLaneChangeForwardAmount, changeLanesForward);
		sendCommandToDevice(Sustainabot.Command.ConfigLaneChangeBackwardAmount, changesLanesBackward);
		if (mUseHeading) {
			sendCommandToDevice(Sustainabot.Command.ConfigLaneChangeLeftHeading, scaleHeadingToDevice(30));
		} else {
			sendCommandToDevice(Sustainabot.Command.ConfigLaneChangeLeftAmount, turnLeftMovement);
			sendCommandToDevice(Sustainabot.Command.ConfigLaneChangeRightAmount, turnRightMovement);
		}

		// XXX: remember that commands are optimised/scaled, so the actual movement may be different
		int movementStep = preDropMovement + inDropMovement + postDropMovement;
		boolean hasStartedDropping = false;
		for (int i = resultMap.length - 1; i >= 0; i--) {
			int totalMovement = 0;
			int numDrops = 0;

			// Sustainabot's arm is on its left, so we start from bottom right facing left
			boolean[] oneRow = resultMap[i];

			// XXX: skip any empty rows at the start
			if (!hasStartedDropping) {
				boolean hasContent = false;
				for (boolean part : oneRow) {
					hasContent |= part;
				}
				if (!hasContent) {
					continue;
				}
			}

			boolean rowDropped = false;
			for (int j = 0; j < oneRow.length; j++) {
				boolean imagePart = oneRow[j];
				if (imagePart) {
					numDrops += 1;
				} else {
					if (numDrops > 0) { // draw the dots we've been saving
						if (mUseHeading && !rowDropped) {
							addMovementCommand(Sustainabot.Command.TurnToHeading, mCurrentHeading, false);
						}
						rowDropped = true;
						totalMovement += numDrops * movementStep;
						drawDots(numDrops, preDropMovement, inDropMovement, dropTurnCorrection, postDropMovement);
						numDrops = 0;
					}

					// XXX: check whether there are any more drops in this row
					boolean hasMoreContent = false;
					for (int k = j; k < oneRow.length; k++) {
						hasMoreContent |= oneRow[k];
					}
					if (!hasMoreContent) {
						break; // we can skip the rest of this row
					}

					totalMovement += movementStep;
					addMovementCommand(Sustainabot.Command.Forward, movementStep, false);
				}
			}

			if (numDrops > 0) { // draw any remaining dots we've been saving
				totalMovement += numDrops * movementStep;
				drawDots(numDrops, preDropMovement, inDropMovement, dropTurnCorrection, postDropMovement);
			}

			hasStartedDropping = true; // if we get to the end of a row, we've started dropping

			// XXX: remove any upcoming blank rows (and, below, move to skip that many lines)
			int numBlankRows = 0;
			for (int k = i - 1; k >= 0; k--) {
				boolean hasContent = false;
				boolean[] nextRow = resultMap[k];
				for (boolean part : nextRow) {
					hasContent |= part;
				}
				if (!hasContent) {
					numBlankRows += 1;
					i--; // so we don't draw this row normally
				} else {
					break;
				}
			}

			// back to the start unless this is the last row
			if (i > 0) {
				// XXX: skip blank rows by changing lanes repeatedly
				if (numBlankRows > 0) {
					for (int k = 0; k < numBlankRows; k++) {
						addMovementCommand(mUseHeading ? Sustainabot.Command.ChangeLanesHeading :
								Sustainabot.Command.ChangeLanesLeftRight, 0, false);
					}
				}
				addMovementCommand(Sustainabot.Command.ShakeSalt, 1, false);
				addMovementCommand(mUseHeading ? Sustainabot.Command.ChangeLanesHeading :
						Sustainabot.Command.ChangeLanesLeftRight, 0, false);


				addMovementCommand(Sustainabot.Command.Backward, totalMovement + mMovementCorrection, false);
			} else {
				addMovementCommand(Sustainabot.Command.Forward, 200, false); // move away from the picture
				//alternative: addMovementCommand(Sustainabot.Command.Backward, totalMovement, false); // back to the
				// start
				if (startWhenLoaded) {
					sendCommandToDevice(Sustainabot.Command.Execute, 0);
				}
			}
		}
	}
}

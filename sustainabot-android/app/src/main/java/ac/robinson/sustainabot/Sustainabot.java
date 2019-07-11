package ac.robinson.sustainabot;

class Sustainabot {
	/* -- command list --

	Gxxx Execute all stored commands
	Cxxx Clear all stored commands
	Lxxx List commands in store so far

	0xxx Compass calibration for xxx
	Z000 Get current heading
	ixxx Get battery voltage
	jxxx List all available commands (i.e., this list)
	Kxxx Continuously seek random headings (until reed switch is activated)

	txxx List saved wheel calib and Drop/Stop vals
	qxxx Left wheel saved Fwd correction set xxx
	wxxx Left wheel saved Bwd correction set xxx
	rxxx Right wheel saved Fwd correction set xxx
	exxx Right wheel saved Bwd correction set xxx

	Vxxx Turning duration per unit set xxx (for Left/Right)
	uxxx Turning duration per unit set xxx (for Heading)

	Fxxx Forward by xxx
	Bxxx Backward by xxx
	Oxxx Left turn by xxx
	Pxxx Right turn by xxx
	Hxxx Turn heading xxx

	axxx Left wheel temporary Fwd correction set xxx
	sxxx Left wheel temporary Bwd correction set xxx
	dxxx Right wheel temporary Fwd correction set xxx
	fxxx Right wheel temporary Bwd correction set xxx
	gxxx Reset forward/backward correction to stored (i.e., q/w/r/e) values

	Txxx Chute open set xxx
	Uxxx Chute close set xxx
	Dxxx Start drop (open)
	Sxxx Stop drop (close)

	4xxx Lane change var 1 set
	5xxx Lane change var 2 set
	6xxx Lane change var 3 set
	7xxx Lane change var 4 set
	Qxxx Lane change Left/Right
	Rxxx Lane change Heading

	1xxx Draw dot var 1 set
	2xxx Draw dot var 2 set
	3xxx Draw dot var 2 set
	8xxx Draw dot left/right correction (100+/-)
	9xxx After X dots, perform $8xxx correction
	@xxx Draw xxx dots

	Ixxx Jitter strength xxx set
	Exxx Jitter xxx times
	pxxx Pause (xxx * 50) milliseconds

	*/

	// TODO: clarify which are global configuration (e.g., ConfigXXX) and ones which add to the movement list
	public enum Command {
		/* handling commands */
		Execute,
		Clear,
		List,

		/* setup - getting/setting initial values */
		CalibrateCompass,
		GetHeading,
		GetBatteryVoltage,
		ListAllCommands,
		TestMagnetometerSeek,

		/* configuring movement */
		ConfigListWheelCalibrationValues,
		ConfigSetLeftForwardCalibration,
		ConfigSetLeftBackwardCalibration,
		ConfigSetRightForwardCalibration,
		ConfigSetRightBackwardCalibration,

		/* configuring turning amount */
		ConfigSetTurnStepLeftRight,
		ConfigSetTurnStepHeading,

		/* basic movement controls */
		Forward,
		Backward,
		TurnLeft,
		TurnRight,
		TurnToHeading,

		/* adjust PWM values temporarily (i.e., in command list) to allow drawing curves */
		SetTemporaryLeftForwardCalibration,
		SetTemporaryLeftBackwardCalibration,
		SetTemporaryRightForwardCalibration,
		SetTemporaryRightBackwardCalibration,
		ResetTemporaryCalibration,

		/* configuring dropping material */
		ConfigDropMovementOpen,
		ConfigDropMovementClose,
		StartFullDrop,
		StopFullDrop,

		/* custom command to "change lanes" */
		ConfigLaneChangeForwardAmount,
		ConfigLaneChangeLeftAmount,
		ConfigLaneChangeBackwardAmount,
		ConfigLaneChangeRightAmount,
		ChangeLanesLeftRight,
		ChangeLanesHeading,
		ConfigLaneChangeLeftHeading,

		/* custom command to draw a "dot" */
		ConfigPreDropAmount,
		ConfigInDropAmount,
		ConfigPostDropAmount,
		ConfigDropCorrectionAmount,
		ConfigDropCorrectionInterval,
		DrawDot,

		/* tweaks to movement - shake the salt container or pause */
		ConfigSetShakeTime,
		ShakeSalt,
		Pause
	}

	static String getCommandString(Sustainabot.Command command) {
		switch (command) {
			case Execute:
				return "G";
			case Clear:
				return "C";
			case List:
				return "L";

			case CalibrateCompass:
				return "0";
			case GetHeading:
				return "Z";
			case GetBatteryVoltage:
				return "i"; // returns formula to calculate voltage (5 is max?) - e.g., 2543/4096*6.6-0.1
			case ListAllCommands:
				return "j"; // prints all of the device's available command characters
			case TestMagnetometerSeek:
				return "K"; // continuously seeks random headings until stopped via magnet

			case ConfigListWheelCalibrationValues:
				return "t";
			case ConfigSetLeftForwardCalibration:
				return "q";
			case ConfigSetLeftBackwardCalibration:
				return "w";
			case ConfigSetRightForwardCalibration:
				return "r";
			case ConfigSetRightBackwardCalibration:
				return "e";

			case ConfigSetTurnStepLeftRight:
				return "V"; // how much to move by when using TurnLeft / TurnLeft
			case ConfigSetTurnStepHeading:
				return "u"; // how much to move by when turning by heading; default: 8

			case Forward:
				return "F";
			case Backward:
				return "B";
			case TurnLeft:
				return "O";
			case TurnRight:
				return "P";
			case TurnToHeading:
				return "H";

			case SetTemporaryLeftForwardCalibration:
				return "a";
			case SetTemporaryLeftBackwardCalibration:
				return "s";
			case SetTemporaryRightForwardCalibration:
				return "d";
			case SetTemporaryRightBackwardCalibration:
				return "f";
			case ResetTemporaryCalibration:
				return "g";

			case ConfigDropMovementOpen:
				return "T";
			case ConfigDropMovementClose:
				return "U";
			case StartFullDrop:
				return "D";
			case StopFullDrop:
				return "S";

			case ConfigLaneChangeForwardAmount:
				return "4";
			case ConfigLaneChangeLeftAmount:
			case ConfigLaneChangeLeftHeading: // no right equivalent, because it returns to the start heading after
				return "5";
			case ConfigLaneChangeBackwardAmount:
				return "6";
			case ConfigLaneChangeRightAmount:
				return "7";
			case ChangeLanesLeftRight:
				return "Q";
			case ChangeLanesHeading:
				return "R";

			case ConfigPreDropAmount:
				return "1";
			case ConfigInDropAmount:
				return "2";
			case ConfigPostDropAmount:
				return "3";
			case ConfigDropCorrectionAmount:
				return "8"; // as offset from 100
			case ConfigDropCorrectionInterval:
				return "9"; // after this number of drops, correct turn by ConfigDropCorrectionAmount L or R
			case DrawDot:
				return "@";

			case ConfigSetShakeTime:
				return "I";
			case ShakeSalt:
				return "E"; // 0 = don't return to previous heading; 1 = do
			case Pause:
				return "p"; // multiple of 50ms
		}

		// TODO: is there anything better we can do without checking return value each time?
		return "C"; // on failure just clear the list
	}
}

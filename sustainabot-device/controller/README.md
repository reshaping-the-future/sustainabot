# Sustainabot controller
Sustainabot is controlled using a custom-made circuit board, which runs three micro planetary-drive geared motors and a Bluetooth module, provides magnetometer readings from its onboard sensor, and handles battery charging.

This repository provides [circuit diagrams](gerber), a [bill of materials](bill-of-materials.xlsx) and [parts coordinates list](parts-list-coordinates.xlsx), a [list of commands](command-list.txt), and [general hardware documentation](sustainabot-building.pdf).

You will also need the following (or similar), in addition to the circuit board:
* Three 700:1 micro planetary motors ([example](https://coolcomponents.co.uk/products/700-1-sub-micro-plastic-planetary-gearmotor))
* A Bluetooth HC-05 module
* A Lithium-polymer battery
* Optional: Two extra 100 kOhm resistors for the battery monitor feature

For simplicity, we have provided both the [source](sustainabot.bas) and [prebuilt](sustainabot.hex) embedded code. In order to deploy the prebuilt code, you will need a debugger (e.g., [PICkit 3](https://www.microchip.com/DevelopmentTools/ProductDetails/pg164130)) and the [MPLAB IDE](https://www.microchip.com/mplab/mplab-x-ide).

To deploy the prebuilt file using the MPLAB IDE:
1. File > New Project…
2. Select `Microchip Embedded` and `Prebuilt (Hex, Loadable Image) Project`, then press Next.
3. On the following page, configure the project:
    1. In the "Prebuilt Filename" box, select the `sustainabot.hex` file from this repository.
    2. For "Family", select `Advanced 8-bit MCUs (PIC18)`.
    3. For "Device", select `PIC18F26J53` (which you can copy and paste from here to ensure accuracy).
    4. In "Hardware Tool", select `PICkit3`, then press Next.
4. On the final page, select your chose of project location (or leave the default value), then press Finish
5. Once the project is loaded, edit its properties (i.e., via the right-click menu), and in "Conf: [default]" > "PICkit 3", select the "Power" item from the dropdown menu, selecting `Power target circuit from PICkit3` and setting the Voltage Level to `3.25`.
6. Plug in your assembled Sustainabot, ensuring that the central wire on the board's programmer port is routed to the indicator arrow on the PICkit 3)
7. Power on the Sustainabot, then connect the PICkit 3 to your computer
8. Click `Make and Program Device` in the MPLAB IDE

Once you have programmed your device, you can disconnect the programming cable and use the [android app](../../sustainabot-android) to control your Sustainabot.

## License
Apache 2.0

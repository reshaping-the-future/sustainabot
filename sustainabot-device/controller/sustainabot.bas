'***************************************************************************
'*  Name    : Sustainabot                                                *
'*  Author  : Mark Holton                                                  *
'*  Date    : 03 September 2018                                            *
'*  Version : 2.0                                                          *
'***************************************************************************

Include "modedefs.bas"
#CONFIG
    CONFIG XINST = OFF
    CONFIG CPUDIV = OSC1
	CONFIG WDTEN = ON
	CONFIG WDTPS = 512
    CONFIG ADCSEL = BIT12
	CONFIG RTCOSC = T1OSCREF
	CONFIG OSC = HS
	CONFIG CLKOEC = OFF
	CONFIG CFGPLLEN = OFF
	CONFIG CP0 = ON
	CONFIG IESO = ON
	CONFIG SOSCSEL = LOW
#ENDCONFIG
DEFINE      OSC 16
DEFINE      ADC_BITS 12
DEFINE      ADC_CLOCK 3
DEFINE      ADC_SAMPLEUS 200

trig_lu                         var byte[64]
command                         var byte[4]
command_store                   var byte[3072]

adval                           var word
pause_val                       var word
dot_counter                     var byte
dot_counter_point               var byte
cnt                             var word
batt_mon_av                     var long
batt_mon_av_read                var long
batt_test                       var byte
cnt2                            var word
cnt3                            var byte
magx                            VAR word
magy                            VAR word
magz                            VAR word
magx_accum                      VAR long
magy_accum                      VAR long
magz_accum                      VAR long
trig_lookup                     var word
mag_min_x                       var long
mag_min_y                       var long
mag_max_x                       var long
mag_max_y                       var long
tmp_mag                         var long
tmp_mag2                        var long
mag_magnitude                   var long
mag_magnitude_x                 var long
mag_magnitude_y                 var long
x_diff                          var long
rnd                             var word
y_diff                          var long
x_mag_off                       var long
y_mag_off                       var long
ratio                           var long
mag_sen_ctr                     var byte
mul_val                         var long
find_angctr                     var byte
lane_chg_jitter                 var byte
ang_val                         var byte
angle                           var byte
drop_fwd_cor                    var byte
right_fwd_width                 var word
left_fwd_width                  var word
right_bck_width                 var word
left_bck_width                  var word
heading_accuracy                var byte
ser_spd                         var byte
turn_timing_val                 var byte
turn_timing_heading_val         var byte
turn_timing_val_temp            var byte
turn_timing_val_store           var byte
calib_time                      var byte
scoop_amt                       var byte
scoop_amt2                      var byte
found_ang                       var byte
command_to_store                var byte
stored_heading                  var byte
jitter_time                     var byte
heading_search_cnt              VAR BYTE
chute_open                      var byte
draw_dot1                       var byte
draw_dot2                       var byte
draw_dot3                       var byte
lane_change1                    var byte
lane_change2                    var byte
lane_change3                    var byte
lane_change4                    var byte
lane_change_hdg_val             var byte
draw_dots_repeat                var byte
draw_dots_loop                  var byte
command_ptr                     var word
search_bearing                  var bit
command_value                   var byte
command_go                      var bit
cnt4                            var word
RW1                             var byte
RW2                             var byte
LW1                             var byte
LW2                             var byte
RWP1                            var latc.2
RWP2                            var latc.1
LWP1                            var latb.5
LWP2                            var latb.4

' set PWM duty cycle for each variable to 50% (left/right wheels, both forwards/backwards)
LW1         = 128
LW2         = 128  ' set PWM duty cycle variable to 50%
RW1         = 128  ' set PWM duty cycle variable to 50%
RW2         = 128  ' set PWM duty cycle variable to 50%

calib_time  = 150

i2c_add                         var     word
i2c_ctrl                        var     byte
i2c_ctrl_r                      var     byte

high portb.7

' enable / disable changes to output pin mapping in PIC
EECON2 = $55
EECON2 = $AA
PPSCON.0 = 0
RPOR13 = 14 ' remap PWM PXA to portc.2 (motor drive "AIN2")
EECON2 = $55
EECON2 = $AA
PPSCON.0 = 0

right_wheel1                    var CCPR1L
right_wheel2                    var CCPR8L
scoop2                          var lata.1
scoop1                          var latc.7
left_wheel1                     var CCPR5L
left_wheel2                     var CCPR4L

right_wheel1                    = 0  ' set PWM duty cycle to 0%
right_wheel2                    = 0  ' set PWM duty cycle to 0%
left_wheel1                     = 0  ' set PWM duty cycle to 0%
left_wheel2                     = 0  ' set PWM duty cycle to 0%

' PWM Mode selected for each of the 4 outputs (left/right wheels, both forwards/backwards)
CCP4CON                         = %00001100
CCP1CON                         = %00001100
CCP5CON                         = %00001100
CCP8CON                         = %00001100


T2CON                           = %00000000   ' Turn off timer2
INTCON.7                        = 0           ' Disable interrupts.
PR2                             = 255         ' PWM frequency.
T2CON                           = %00000100   ' Timer2 ON + 1:1 prescale

Serial_in                       var porta.2
green_led                       var portb.2
red_led                         var portb.1
yellow_led                      var portb.7
key_press                       var portb.7
i2cSDA                          VAR PORTB.0
i2cSCL                          VAR PORTC.6
Serial_out                      var porta.3

ancon0                          = %11111111 ' bit 3 is VREF+ analogue here but is set to digital ('1') if GPS is in use for GPS to DD comm's
ancon1                          = %00011111 ' ignore this bit -> set bit 3 as 0 for analogue input on portc.2 (ch11) or 1 for digital (GPS to DD comm's)
ADCON0                          = %00101111
adcon1.7                        = 1
adcon0.7                        = 0
adcon0.6                        = 0

porta.2                         = 0

jitter_time                     = 20
turn_timing_heading_val         = 8
dot_counter_point               = 5

intcon                          = 0
intcon2                         = %11110101
intcon3                         = %00000000

' set the wheel and scoop drive pinouts to output mode
trisa                           = %00001101
trisb                           = %00000000
trisc                           = %00000000

porta                           = %00001000
portb                           = %00000000
portc                           = %00000000

WDTCON.0                        = 1
OSCTUNE.6                       = 0
OSCCON                          = %01110000

ser_spd                         = 6
turn_timing_val                 = 20

scoop_amt                       = 30

light_sense                     var byte ' AN0

low portb.5

I2CWRITE i2cSDA,i2cSCL, $3c, $24, [$00]
pause 10
I2CWRITE i2cSDA,i2cSCL, $3c, $00, [$18]
pause 10
I2CWRITE i2cSDA,i2cSCL, $3c, $00, [$1c]
pause 10
I2CWRITE i2cSDA,i2cSCL, $3c, $01, [$20]
pause 10
I2CWRITE i2cSDA,i2cSCL, $3c, $02, [$01]
pause 10

trisb.3                         = 0
trisa.1                         = 0
trisa.2                         = 1
trisa.3                         = 1

chute_open = 0

i2c_ctrl                        = $a0
i2c_ctrl_r                      = $a1
i2c_add                         = 0

INTCON.7                        = 0
CCPTMRS1                        = 0

command_ptr                     = 0


main:

    high green_led
    i2c_add = 0
    pause 1
    i2c_add = 1

    high yellow_led
    low green_led
    low red_led
    pause 100
    low yellow_led

    low left_wheel1                 ' wheels off
    low left_wheel2                 ' ...
    low right_wheel1                ' ...
    low right_wheel2
    SEROUT2 Serial_out,ser_spd,[$d,$a]
    SEROUT2 Serial_out,ser_spd,["Larry is online",$d,$a]
    high green_led
    command_ptr = 0
    pause 200

    portb.7 = 0
    high green_led
    latb.7 = 0
    low yellow_led
    pause 100
    low green_led
    draw_dots_repeat = 0

    SEROUT2 Serial_out,ser_spd,[$d,$a]
    gosub create_trig_lookup_table
    pause 50
pp:

    SEROUT2 Serial_out,ser_spd,[$d,$a,"Chute turn val ", dec scoop_amt,$d, " 'T'",$d,$a]
    SEROUT2 Serial_out,ser_spd,[$d,$a,"Waiting for instruction",$d,$a]

    command_go = 0
    i2c_add = 0

    ' read fixed variables for this Sustainabot from EEPROM and output to serial port
    i2Cread i2cSDA,i2cSCL, i2c_ctrl_r, i2c_add, [LW1, LW2, RW1, RW2, scoop_amt, scoop_amt2]
    SEROUT2 Serial_out,ser_spd,[$d,$a,"LW1 ",dec LW1,$d,$a]
    SEROUT2 Serial_out,ser_spd,[$d,$a,"LW2 ",dec LW2,$d,$a]
    SEROUT2 Serial_out,ser_spd,[$d,$a,"RW1 ",dec RW1,$d,$a]
    SEROUT2 Serial_out,ser_spd,[$d,$a,"RW2 ",dec RW2,$d,$a]
    SEROUT2 Serial_out,ser_spd,[$d,$a,"Drop set ",dec scoop_amt,$d,$a]
    SEROUT2 Serial_out,ser_spd,[$d,$a,"Stop drop set ",dec scoop_amt2,$d,$a]

    high green_led
    gosub listen_for_commands
    gosub execute_commands
    low yellow_led
    goto pp
end


listen_for_commands:

    while command_go = 0 ' while no "$Gxxx" command received, execute the few "immediate commands" but otherwise, return to wait for further instructions

        portb.7 = 0 ' ensure the magnetic reed switch output
        latb.7 = 0

        SERIN2 porta.2,ser_spd,[wait("$"),str command\4]
        if command[0] = 74 then ' live mag                              J
            gosub live_mag
            SEROUT2 Serial_out,ser_spd,[$d,$a,"Waiting for instruction",$d,$a]
        endif
        if command[0] = 67 then ' clear commands                        C
            SEROUT2 Serial_out,ser_spd,[$d,$a,"Clear instruction set",$d,$a]
            command_ptr = 0
        endif

        command_value = (command[1] - 48) * 100 + (command[2] - 48) * 10 + (command[3] - 48)

        if command[0] = 48 then ' calibrate compass                     0  (zero)
            calib_time = command_value
            gosub compass_calib
        endif
        if command[0] = 113 then ' set left wheel 1 PWM                  q
            LW1 = command_value
            SEROUT2 Serial_out,ser_spd,[$d,$a,"#Left wheel PWM1 ", dec command_value,$d,$a]
            i2c_add = 0
            I2CWRITE i2cSDA,i2cSCL, i2c_ctrl, i2c_add, [LW1]

        endif
        if command[0] = 119 then ' set left wheel 2 PWM                  w
            LW2 = command_value
            SEROUT2 Serial_out,ser_spd,[$d,$a,"#Left wheel PWM2 ", dec command_value,$d,$a]
            i2c_add = 1
            I2CWRITE i2cSDA,i2cSCL, i2c_ctrl, i2c_add, [LW2]
        endif
        if command[0] = 101 then ' set right wheel 1 PWM                 e
            RW1 = command_value
            SEROUT2 Serial_out,ser_spd,[$d,$a,"#Right wheel PWM1 ", dec command_value,$d,$a]
            i2c_add = 2
            I2CWRITE i2cSDA,i2cSCL, i2c_ctrl, i2c_add, [RW1]
        endif
        if command[0] = 114 then ' set right wheel 2 PWM                 r
            RW2 = command_value
            SEROUT2 Serial_out,ser_spd,[$d,$a,"#Right wheel PWM2 ", dec command_value,$d,$a]
            i2c_add = 3
            I2CWRITE i2cSDA,i2cSCL, i2c_ctrl, i2c_add, [RW2]
        endif
        if command[0] = 117 then ' heading turning rate value            u
            turn_timing_heading_val = command_value
            SEROUT2 Serial_out,ser_spd,[$d,$a,"Heading turning rate val ", dec command_value,$d,$a]
        endif
        if command[0] = 116 then ' list  PWM values                      t
            SEROUT2 Serial_out,ser_spd,[$d,$a,"#Fwd Left wheel (q) PWM1 ", dec LW1,$d,$a]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"#Fwd Right wheel (r) PWM2 ", dec RW2,$d,$a]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"#Bwd Left wheel (w) PWM2 ", dec LW2,$d,$a]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"#Bwd Right wheel (e) PWM1 ", dec RW1,$d,$a]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"Drop set ",dec scoop_amt,$d,$a]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"Stop drop set ",dec scoop_amt2,$d,$a]
        endif

        if command[0] = 49 then ' draw_dot1                              1
            draw_dot1 = command_value
            SEROUT2 Serial_out,ser_spd,[$d,$a,"#Draw dot var1 ", dec command_value,$d,$a]
        endif
        if command[0] = 50 then ' draw_dot2                              2
            draw_dot2 = command_value
            SEROUT2 Serial_out,ser_spd,[$d,$a,"#Draw dot var2 ", dec command_value,$d,$a]
        endif
        if command[0] = 51 then ' draw_dot3                              3
            draw_dot3 = command_value
            SEROUT2 Serial_out,ser_spd,[$d,$a,"#Draw dot var3 ", dec command_value,$d,$a]
        endif
        if command[0] = 52 then ' lane_change1                           4
            lane_change1 = command_value
            SEROUT2 Serial_out,ser_spd,[$d,$a,"#Lane change var1 ", dec command_value,$d,$a]
        endif
        if command[0] = 53 then ' lane_change2                           5
            lane_change2 = command_value
            SEROUT2 Serial_out,ser_spd,[$d,$a,"#Lane change var2 ", dec command_value,$d,$a]
        endif
        if command[0] = 54 then ' lane_change3                           6
            lane_change3 = command_value
            SEROUT2 Serial_out,ser_spd,[$d,$a,"#Lane change var3 ", dec command_value,$d,$a]
        endif
        if command[0] = 55 then ' lane_change4                           7
            lane_change4 = command_value
            SEROUT2 Serial_out,ser_spd,[$d,$a,"#Lane change var4 ", dec command_value,$d,$a]
        endif
        if command[0] = 56 then
            drop_fwd_cor = command_value ' drop_fwd_cor                  8
            SEROUT2 Serial_out,ser_spd,[$d,$a,"#Drop fwd cor val ", dec command_value,$d,$a]
        endif
        if command[0] = 57 then
            dot_counter_point = command_value ' drop_fwd_cor             9
            SEROUT2 Serial_out,ser_spd,[$d,$a,"#Dot counter correction val ", dec command_value,$d,$a]
        endif

        if command[0] = 105 then ' request battery voltage               i
            batt_mon_av = 0

            trisb.3 = 1
            adcon1.1 = 0

            for batt_test = 1 to 5
                ADCIN 9,adval
                batt_mon_av = batt_mon_av + adval
            next batt_test

            batt_mon_av = batt_mon_av / 5

            batt_mon_av_read = batt_mon_av

            SEROUT2 Serial_out,ser_spd,[$d,$a,"Battery voltage ", dec batt_mon_av_read, "/4096*6.6-0.1",$d,$a]
        endif

        if command[0] = 106 then ' list commands                         j
            SEROUT2 Serial_out,ser_spd,[$d,$a]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"Lxxx List commands in store so far"]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"Cxxx Clear all stored commands"]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"Gxxx Execute all stored commands"]
            SEROUT2 Serial_out,ser_spd,[$d,$a]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"0xxx Compass calibration for xxx"]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"Zxxx Request current heading"]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"ixxx Battery voltage request"]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"jxxx Display this list of commands"]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"Kxxx Continuously seek random headings (until magnet switch)"]
            SEROUT2 Serial_out,ser_spd,[$d,$a]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"txxx List Wheel calib and Drop/Stop values"]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"qxxx Left wheel Fwd set"]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"wxxx Left wheel Bwd set"]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"rxxx Right wheel Fwd set"]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"exxx Right wheel Bwd set"]
            SEROUT2 Serial_out,ser_spd,[$d,$a]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"Vxxx Turning duration per unit (for Left/Right)"]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"uxxx Turning duration per unit (for Heading)"]
            SEROUT2 Serial_out,ser_spd,[$d,$a]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"Fxxx Forward by xxx"]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"Bxxx Backward by xxx"]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"Oxxx Left turn by xxx"]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"Pxxx Right turn by xxx"]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"Hxxx Turn heading xxx"]
            SEROUT2 Serial_out,ser_spd,[$d,$a]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"axxx Left wheel Fwd set"]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"sxxx Left wheel Bwd set"]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"dxxx Right wheel Fwd set"]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"fxxx Right wheel Bwd set"]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"gxxx Reset forward/backward correction to stored values"]
            SEROUT2 Serial_out,ser_spd,[$d,$a]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"Txxx Chute open set"]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"Uxxx Chute close set"]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"Dxxx Start drop"]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"Sxxx Stop drop"]
            SEROUT2 Serial_out,ser_spd,[$d,$a]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"4xxx Lane change var 1 set"]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"5xxx Lane change var 2 set"]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"6xxx Lane change var 3 set"]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"7xxx Lane change var 4 set"]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"Qxxx Lane change O-P"]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"Rxxx Lane change Heading"]
            SEROUT2 Serial_out,ser_spd,[$d,$a]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"1xxx Draw dot var 1 set"]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"2xxx Draw dot var 2 set"]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"3xxx Draw dot var 3 set"]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"8xxx Draw dot left/right correction (100+/-)"]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"9xxx After X dots, perform $8xxx correction"]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"@xxx Draw dots"]
            SEROUT2 Serial_out,ser_spd,[$d,$a]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"Ixxx Jitter strength xxx set"]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"Exxx Jitter", $d,$a]
            SEROUT2 Serial_out,ser_spd,[$d,$a,"pxxx Pause (xxx * 50) milliseconds", $d,$a]
        endif

        if command[0] = 97 then ' left                                   a
            command_to_store = 13
            gosub store_command
            SEROUT2 Serial_out,ser_spd,[$d,$a,"#", dec (command_ptr + 1)," Rx'd: a:",dec command_value,$d,$a]
            command_ptr = command_ptr + 1
        endif
        if command[0] = 115 then ' left                                  s
            command_to_store = 14
            gosub store_command
            SEROUT2 Serial_out,ser_spd,[$d,$a,"#", dec (command_ptr + 1)," Rx'd: s:",dec command_value,$d,$a]
            command_ptr = command_ptr + 1
        endif
        if command[0] = 100 then ' left                                  d
            command_to_store = 15
            gosub store_command
            SEROUT2 Serial_out,ser_spd,[$d,$a,"#", dec (command_ptr + 1)," Rx'd: d:",dec command_value,$d,$a]
            command_ptr = command_ptr + 1
        endif
        if command[0] = 102 then ' left                                  f
            command_to_store = 16
            gosub store_command
            SEROUT2 Serial_out,ser_spd,[$d,$a,"#", dec (command_ptr + 1)," Rx'd: f:",dec command_value,$d,$a]
            command_ptr = command_ptr + 1
        endif
        if command[0] = 103 then ' left                                  g
            command_to_store = 17
            gosub store_command
            SEROUT2 Serial_out,ser_spd,[$d,$a,"#", dec (command_ptr + 1)," Rx'd: g:",dec command_value,$d,$a]
            command_ptr = command_ptr + 1
        endif

        if command[0] = 112 then ' pause                                 p
            command_to_store = 18
            gosub store_command
            SEROUT2 Serial_out,ser_spd,[$d,$a,"#", dec (command_ptr + 1)," Rx'd: p:",dec command_value,$d,$a]
            command_ptr = command_ptr + 1
        endif

        if command[0] = 84 then ' how much to open chute                 T
            scoop_amt = command_value
            SEROUT2 Serial_out,ser_spd,[$d,$a,"#Chute open val ", dec command_value,$d,$a]
            i2c_add = 4
            I2CWRITE i2cSDA,i2cSCL, i2c_ctrl, i2c_add, [scoop_amt]
        endif
        if command[0] = 85 then ' how much to close chute                U
            scoop_amt2 = command_value
            SEROUT2 Serial_out,ser_spd,[$d,$a,"#Chute close val ", dec command_value,$d,$a]
            i2c_add = 5
            I2CWRITE i2cSDA,i2cSCL, i2c_ctrl, i2c_add, [scoop_amt2]
        endif
        if command[0] = 86 then ' variable turn pause value              V
            turn_timing_val = command_value
            SEROUT2 Serial_out,ser_spd,[$d,$a,"#TurnTim val ", dec command_value,$d,$a]
        endif

        if command[0] = 75 then ' random headings                        K
            SEROUT2 Serial_out,ser_spd,[$d,$a,"Random 'K'",$d,$a]
        endif

        if command[0] = 70 then  ' forwards                              F
            command_to_store = 1
            gosub store_command
            SEROUT2 Serial_out,ser_spd,[$d,$a,"#", dec (command_ptr + 1)," Rx'd: F:",dec command_value,$d,$a]
            command_ptr = command_ptr + 1
        endif
        if command[0] = 66 then ' backwards                              B
            command_to_store = 2
            gosub store_command
            SEROUT2 Serial_out,ser_spd,[$d,$a,"#", dec (command_ptr + 1)," Rx'd: B:",dec command_value,$d,$a]
            command_ptr = command_ptr + 1
        endif
        if command[0] = 79 then ' left                                   O
            command_to_store = 6
            gosub store_command
            SEROUT2 Serial_out,ser_spd,[$d,$a,"#", dec (command_ptr + 1)," Rx'd: O:",dec command_value,$d,$a]
            command_ptr = command_ptr + 1
        endif
        if command[0] = 73 then  ' jitter time                           I
            jitter_time = command_value
            SEROUT2 Serial_out,ser_spd,[$d,$a,"Jitter time: ",dec jitter_time,$d,$a]
        endif
        if command[0] = 80 then ' right                                  P
            command_to_store = 7
            gosub store_command
            SEROUT2 Serial_out,ser_spd,[$d,$a,"#", dec (command_ptr + 1)," Rx'd: P:",dec command_value,$d,$a]
            command_ptr = command_ptr + 1
        endif
        if command[0] = 72 then ' heading seek                           H
            command_to_store = 3
            gosub store_command
            SEROUT2 Serial_out,ser_spd,[$d,$a,"#", dec (command_ptr + 1)," Rx'd: H:",dec command_value,$d,$a]
            command_ptr = command_ptr + 1
        endif
        if command[0] = 69 then ' jitter                                 E
            command_to_store = 9
            gosub store_command
            SEROUT2 Serial_out,ser_spd,[$d,$a,"#", dec (command_ptr + 1)," Rx'd: E:",dec command_value,$d,$a]
            command_ptr = command_ptr + 1
        endif
        if command[0] = 64 then '                                        @
            command_to_store = 10
            gosub store_command
            SEROUT2 Serial_out,ser_spd,[$d,$a,"#", dec (command_ptr + 1)," Rx'd: @:",dec command_value,$d,$a]
            command_ptr = command_ptr + 1
        endif
        if command[0] = 81 then '                                        Q
            command_to_store = 11
            gosub store_command
            SEROUT2 Serial_out,ser_spd,["#", dec (command_ptr + 1)," Rx'd: Q:",dec command_value,$d,$a]
            command_ptr = command_ptr + 1
        endif
        if command[0] = 82 then '                                        R
            command_to_store = 12
            gosub store_command
            SEROUT2 Serial_out,ser_spd,["#", dec (command_ptr + 1)," Rx'd: R:",dec command_value,$d,$a]
            command_ptr = command_ptr + 1
        endif
        if command[0] = 68 then ' drop start                             D
            command_to_store = 4
            gosub store_command
            SEROUT2 Serial_out,ser_spd,["#", dec (command_ptr + 1)," Rx'd: D:",dec command_value,$d,$a]
            command_ptr = command_ptr + 1
            chute_open = 1
        endif
        if command[0] = 83 then  ' drop stop                             S
            command_to_store = 5
            gosub store_command
            SEROUT2 Serial_out,ser_spd,["#", dec (command_ptr + 1)," Rx'd: S:",dec command_value,$d,$a]
            command_ptr = command_ptr + 1
            chute_open = 0
        endif

        if command[0] = 76 then  ' list stored commands                  L
            SEROUT2 Serial_out,ser_spd,[$d,$a,"List stored commmands",$d,$a]
            for cnt = 0 to (command_ptr - 1)
                i2c_add = cnt * 2 + 6
                i2Cread i2cSDA,i2cSCL, i2c_ctrl_r, i2c_add, [command_to_store, command_value]

                if command_to_store = 1 then SEROUT2 Serial_out,ser_spd,[$d,$a,"Forward by ",dec command_value,$d,$a]
                if command_to_store = 2 then SEROUT2 Serial_out,ser_spd,[$d,$a,"Backward by ",dec command_value,$d,$a]
                if command_to_store = 3 then SEROUT2 Serial_out,ser_spd,[$d,$a,"Heading seek ",dec command_value,$d,$a]
                if command_to_store = 4 then SEROUT2 Serial_out,ser_spd,[$d,$a,"Start drop ",$d,$a]
                if command_to_store = 5 then SEROUT2 Serial_out,ser_spd,[$d,$a,"Stop drop ",$d,$a]
                if command_to_store = 6 then SEROUT2 Serial_out,ser_spd,[$d,$a,"Left ",dec command_value,$d,$a]
                if command_to_store = 7 then SEROUT2 Serial_out,ser_spd,[$d,$a,"Right ",dec command_value,$d,$a]

                if command_to_store = 9 then SEROUT2 Serial_out,ser_spd,[$d,$a,"Jitter ",dec command_value,$d,$a]
                if command_to_store = 10 then SEROUT2 Serial_out,ser_spd,[$d,$a,"Draw dot ",dec command_value,$d,$a]
                if command_to_store = 11 then SEROUT2 Serial_out,ser_spd,[$d,$a,"Lane change OP ",$d,$a]
                if command_to_store = 12 then SEROUT2 Serial_out,ser_spd,[$d,$a,"Lane change HDG ",$d,$a]

                if command_to_store = 13 then SEROUT2 Serial_out,ser_spd,[$d,$a,"Left wheel Fwd temp set ",dec command_value,$d,$a]
                if command_to_store = 14 then SEROUT2 Serial_out,ser_spd,[$d,$a,"Left wheel Bwd temp set ",dec command_value,$d,$a]
                if command_to_store = 15 then SEROUT2 Serial_out,ser_spd,[$d,$a,"Right wheel Fwd temp set ",dec command_value,$d,$a]
                if command_to_store = 16 then SEROUT2 Serial_out,ser_spd,[$d,$a,"Right wheel Bwd temp set ",dec command_value,$d,$a]
                if command_to_store = 17 then SEROUT2 Serial_out,ser_spd,[$d,$a,"Load wheel stored defaults values ",$d,$a]

                if command_to_store = 18 then SEROUT2 Serial_out,ser_spd,[$d,$a,"Pause for (xxx * 50) milliseconds ",$d,$a]
            next cnt
            SEROUT2 Serial_out,ser_spd,[$d,$a,"---End of list---",$d,$a]
        endif

        if command[0] = 90 then ' current heading                        Z
            gosub sample_mag_for_bearing
            SEROUT2 Serial_out,ser_spd,[$d,$a,"$Z",dec3 angle," Magnitude: ",dec mag_magnitude,$d,$a]
        endif
        if command[0] = 71 then ' go - executre all stored commands      G
            SEROUT2 Serial_out,ser_spd,[$d,$a,"Go",$d,$a]
            command_go = 1
        endif

        if command[0] = 75 then gosub random_headings ' random headings  K
    wend

    return


store_command:

    i2c_add = command_ptr * 2 + 6
    I2CWRITE i2cSDA,i2cSCL, i2c_ctrl, i2c_add, [command_to_store, command_value]

    return


random_headings:

    while portb.7 = 0

        random rnd
        command_value = rnd & %11111111
        gosub seek_bearing

    wend
    return


execute_commands:

    SEROUT2 Serial_out,ser_spd,[$d,$a,"Executing commands",$d,$a]
    for cnt = 0 to (command_ptr - 1)

        SEROUT2 Serial_out,ser_spd,[$d,$a,"Command# ",dec (cnt + 1),$d,$a]
        i2c_add = cnt * 2 + 6

        ' retrieve each command from the EEPROM sequentially
        i2Cread i2cSDA,i2cSCL, i2c_ctrl_r, i2c_add, [command_to_store, command_value]

        if command_to_store = 1     then gosub drive_forward
        if command_to_store = 2     then gosub drive_backward
        if command_to_store = 3     then gosub seek_bearing
        if command_to_store = 4     then gosub start_drop
        if command_to_store = 5     then gosub stop_drop
        if command_to_store = 6     then gosub left_turn
        if command_to_store = 7     then gosub right_turn

        if command_to_store = 9     then gosub jitter
        if command_to_store = 10    then gosub draw_dots
        if command_to_store = 11    then gosub lane_change_OP
        if command_to_store = 12    then gosub lane_change_HDG

        if command_to_store = 13    then gosub modify_Left_whl_fwd
        if command_to_store = 14    then gosub modify_Left_whl_bwd
        if command_to_store = 15    then gosub modify_Right_whl_fwd
        if command_to_store = 16    then gosub modify_Right_whl_bwd
        if command_to_store = 17    then gosub reload_stored_wheel_defaults

        if command_to_store = 18    then gosub pause_xxx_ms

        if portb.7 = 1 then cnt = command_ptr - 1 ' terminate command list execution if button pressed or magnet at reed switch

    next cnt

    batt_mon_av = 0
    trisb.3 = 1
    adcon1.1 = 0

    for batt_test = 1 to 5
        ADCIN 9,adval
        batt_mon_av = batt_mon_av + adval
    next batt_test

    batt_mon_av = batt_mon_av / 5
    batt_mon_av_read = batt_mon_av

    ' perform a battery voltage check and warn if its potential is below 3.3V - requires recharging
    if batt_mon_av_read < 2050 then SEROUT2 Serial_out,ser_spd,[$d,$a,"Battery voltage below 3.3 V (", dec batt_mon_av_read, "/4096*6.6-0.1)",$d,$a]

    return


pause_xxx_ms:

    pause_val = 50 * command_value

    pause pause_val

    return


reload_stored_wheel_defaults:

    i2c_add = 0

    i2Cread i2cSDA,i2cSCL, i2c_ctrl_r, i2c_add, [LW1, LW2, RW1, RW2]

    return


modify_Left_whl_fwd:

    LW1 = command_value

    return


modify_Left_whl_bwd:

    LW2 = command_value

    return


modify_Right_whl_fwd:

    RW2 = command_value

    return


modify_Right_whl_bwd:

    RW1 = command_value

    return


lane_change_OP:

    lane_chg_jitter = command_value
    command_value = lane_change1
    gosub drive_forward

    if lane_chg_jitter = 1 then gosub jitter

    command_value = lane_change2
    gosub left_turn

    command_value = lane_change3
    gosub drive_backward

    command_value = lane_change4
    gosub right_turn

    return


lane_change_HDG:

    lane_chg_jitter = command_value
    gosub sample_mag_for_bearing

    pause 100

    gosub sample_mag_for_bearing

    lane_change_hdg_val = angle
    command_value = lane_change1
    gosub drive_forward

    if lane_chg_jitter = 1 then gosub jitter

    command_value = lane_change_hdg_val - lane_change2
    gosub seek_bearing

    command_value = lane_change3
    gosub drive_backward

    command_value = lane_change_hdg_val
    gosub seek_bearing

    return


draw_dots:

    draw_dots_repeat = command_value

    dot_counter = 0

    for draw_dots_loop = 1 to draw_dots_repeat

        command_value = draw_dot1
        gosub drive_forward
        gosub start_drop
        command_value = draw_dot2
        gosub drive_forward
        gosub stop_drop
        command_value = draw_dot3
        gosub drive_forward

        dot_counter = dot_counter + 1

        if dot_counter = dot_counter_point then
            dot_counter = 0

            if drop_fwd_cor != 100 then
                if (drop_fwd_cor < 100) then
                    command_value = 100 - drop_fwd_cor
                    gosub left_turn

                else
                    command_value = drop_fwd_cor - 100
                    gosub right_turn
                endif
            endif
        endif

        if portb.7 = 1 then draw_dots_loop = draw_dots_repeat ' terminate drawing dots if button pressed or magnet at reed switch

    next draw_dots_loop

    return


jitter:

    turn_timing_val_store = turn_timing_val
    turn_timing_val = jitter_time

    gosub turn_left
    gosub turn_right
    gosub turn_left
    gosub turn_right

    turn_timing_val = turn_timing_val_store
    command_value = stored_heading

    if (command_value = 1) then gosub seek_bearing

    return


live_mag:

    SEROUT2 Serial_out,ser_spd,[$d,$a,"Live mag",$d,$a]

    while portb.7 = 0
        gosub sample_mag_for_bearing
        SEROUT2 Serial_out,ser_spd,[$d,$a,"Heading: ",dec angle," Magnitude: ",dec mag_magnitude,$d,$a]
    wend
    SEROUT2 Serial_out,ser_spd,[$d,$a,"End test",$d,$a]

    return


start_drop:

    SEROUT2 Serial_out,ser_spd,[$d,$a,"Start drop",$d,$a]

    high scoop2

    pause scoop_amt

    Low scoop2
    return


stop_drop:

    SEROUT2 Serial_out,ser_spd,[$d,$a,"Stop drop",$d,$a]

    high scoop1

    pause scoop_amt2

    low scoop1

    return


left_turn:

    for cnt4 = 0 to command_value
        gosub turn_left
    next cnt4

    return


right_turn:

    for cnt4 = 0 to command_value
        gosub turn_right
    next cnt4

    return


drive_backward:

    SEROUT2 Serial_out,ser_spd,[$d,$a,"Backward by ",dec command_value,$d,$a]
    for cnt4 = 0 to (command_value * 5)

        left_wheel2 = LW2
        right_wheel1 = RW1

        pause 1                            ' allow wheels to drive forward briefly (1 ms)

        left_wheel2 = 0                    ' turn off both wheels
        right_wheel1 = 0                   ' ...

        pause 1

    next cnt4

    return


drive_forward:

    SEROUT2 Serial_out,ser_spd,[$d,$a,"Forward by ",dec command_value,$d,$a]

    for cnt4 = 0 to (command_value * 5)

        left_wheel1 = LW1                   ' engage left wheel one way
        right_wheel2 = RW2                  ' engage right wheel other way

        pause 1                             ' allow wheels to drive forward briefly (1 ms)

        left_wheel1 = 0                     ' ...
        right_wheel2 = 0                    ' ...

        pause 1

    next cnt4

    return


seek_bearing:

    stored_heading = command_value
    SEROUT2 Serial_out,ser_spd,[$d,$a,"x_mag_off: ",dec x_mag_off,$d,$a]
    SEROUT2 Serial_out,ser_spd,[$d,$a,"y_mag_off: ",dec y_mag_off,$d,$a]
    SEROUT2 Serial_out,ser_spd,[$d,$a,"ratio: ",dec ratio,$d,$a]
    SEROUT2 Serial_out,ser_spd,[$d,$a,"Heading seek ",dec command_value,$d,$a]
    gosub sample_mag_for_bearing
    search_bearing = 0
    turn_timing_val_temp = turn_timing_val
    heading_search_cnt = 0
    heading_accuracy = 1
    SEROUT2 Serial_out,ser_spd,[$d,$a,"Accuracy: ",dec heading_accuracy,$d,$a]

    while search_bearing = 0

        if sqr((command_value-angle)*(command_value-angle)) < 10 then
            turn_timing_val = 3

            heading_search_cnt = heading_search_cnt + 1
            if heading_search_cnt > 20 then
                heading_accuracy = 2
                SEROUT2 Serial_out,ser_spd,[$d,$a,"Accuracy chngd: ",dec heading_accuracy,$d,$a]
            endif

        else
            turn_timing_val = turn_timing_heading_val
        endif

        if sqr((command_value-angle)*(command_value-angle)) < heading_accuracy then

            search_bearing = 1
            SEROUT2 Serial_out,ser_spd,[$d,$a,"Found: ",dec angle,$d,$a]

        else

            if (angle < command_value & (command_value - angle) < 128) then gosub turn_right
            if (angle < command_value & (command_value - angle) > 128) then gosub turn_left

            if (angle > command_value & (angle - command_value) < 128) then gosub turn_left
            if (angle > command_value & (angle - command_value) > 128) then gosub turn_right

        endif

        pause 40
        gosub sample_mag_for_bearing
        if portb.7 = 1 then search_bearing = 1
    wend

    turn_timing_val = turn_timing_val_temp

    return


turn_left:

    left_wheel2 = LW2
    right_wheel2 = RW2

    pause turn_timing_val

    left_wheel2 = 0
    right_wheel2 = 0

    pause turn_timing_val

    return


turn_right:

    left_wheel1 = LW1
    right_wheel1 = RW1

    pause turn_timing_val

    left_wheel1 = 0
    right_wheel1 = 0

    pause turn_timing_val

    return


sharp_turn_right:

    left_wheel1 = LW1
    right_wheel1 = RW1

    pause 20

    left_wheel1 = 0
    right_wheel1 = 0

    pause 20

    return


create_trig_lookup_table:

    SEROUT2 Serial_out,ser_spd,["Start Lookup table creation",$d,$a]

    for trig_lookup = 0 to 63

        trig_lu[trig_lookup] = cos(trig_lookup)

    next trig_lookup

    SEROUT2 Serial_out,ser_spd,["End Lookup table creation",$d,$a]

    return


compass_calib:

    SEROUT2 Serial_out,ser_spd,[$d,$a,"Start compass calibration",$d,$a]

    ' get starting min/max values
    gosub sample_mag_sensor

    magx_accum = magx_accum / 2 ' halved as we're taking the average of 2 summed points

    if magx_accum > 32767 then magx_accum = -(65536 - magx_accum + 1)

    mag_min_x = magx_accum
    mag_max_x = magx_accum

    magy_accum = magy_accum / 2 ' halved as we're taking the average of 2 summed points

    if magy_accum > 32767 then magy_accum = -(65536 - magy_accum + 1)

    mag_min_y = magy_accum
    mag_max_y = magy_accum

    ' now turn the device clockwise, and collect min/max values to find global min/max for a complete rotation
    for cnt = 0 to calib_time

        gosub sharp_turn_right

        pause 60

        gosub sample_mag_sensor

        magx_accum = magx_accum / 2 ' halved as we're taking the average of 2 summed points
        magy_accum = magy_accum / 2 ' halved as we're taking the average of 2 summed points

        if magx_accum > 32767 then magx_accum = -(65536 - magx_accum)
        if magy_accum > 32767 then magy_accum = -(65536 - magy_accum)

        if magx_accum > mag_max_x then mag_max_x = magx_accum
        if magx_accum < mag_min_x then mag_min_x = magx_accum

        if magy_accum > mag_max_y then mag_max_y = magy_accum
        if magy_accum < mag_min_y then mag_min_y = magy_accum

    next cnt

    x_diff = mag_max_x - mag_min_x
    y_diff = mag_max_y - mag_min_y

    x_mag_off = mag_min_x + x_diff/2
    y_mag_off = mag_min_y + y_diff/2

    ratio = (x_diff*256)/y_diff

    SEROUT2 Serial_out,ser_spd,[$d,$a,"x_mag_off: ",dec x_mag_off,$d,$a]
    SEROUT2 Serial_out,ser_spd,[$d,$a,"y_mag_off: ",dec y_mag_off,$d,$a]
    SEROUT2 Serial_out,ser_spd,[$d,$a,"ratio: ",dec ratio,$d,$a]
    SEROUT2 Serial_out,ser_spd,[$d,$a,"End compass calibration",$d,$a]

    return


sample_mag_for_bearing:

    gosub sample_mag_sensor

    magx_accum = magx_accum / 2
    magy_accum = magy_accum / 2

    if magx_accum > 32767 then magx_accum = -(65536 - magx_accum)
    if magy_accum > 32767 then magy_accum = -(65536 - magy_accum)
    magx_accum = magx_accum - x_mag_off
    magy_accum = magy_accum - y_mag_off

    magy_accum = (magy_accum * ratio)/ 256

    mul_val = sqr(magx_accum * magx_accum + magy_accum * magy_accum)

    magx_accum = (magx_accum * 127)/mul_val
    magy_accum = (magy_accum * 127)/mul_val

    mag_magnitude_x = magx_accum
    mag_magnitude_y = magy_accum

    if mag_magnitude_x > 65530 then mag_magnitude_x = 0 - mag_magnitude_x + 1
    if mag_magnitude_y > 65530 then mag_magnitude_y = 0 - mag_magnitude_y + 1


    mag_magnitude = sqr((mag_magnitude_x * mag_magnitude_x)+(mag_magnitude_x*mag_magnitude_x))

    gosub find_angle

    return


sample_mag_sensor:

    magx_accum = 0
    magy_accum = 0
    magz_accum = 0

    for mag_sen_ctr = 0 to 3

        I2CWRITE i2cSDA,i2cSCL, $3c, $02, [$01]
        pause 2
        i2cread i2cSDA,i2cSCL,$3d,$03,[magx,magz,magy]
        pause 1

        tmp_mag2 = magx
        if tmp_mag2 > 32767 then tmp_mag2 = -(65536 - tmp_mag2)
        magx_accum = magx_accum + tmp_mag2

        tmp_mag2 = magy
        if tmp_mag2 > 32767 then tmp_mag2 = -(65536 - tmp_mag2)
        magy_accum = magy_accum + tmp_mag2

    next mag_sen_ctr

    return


find_angle:

    if magx_accum >= -1 & magy_accum > -1   then gosub find_angle1

    if magx_accum >= -1 & magy_accum < 0    then gosub find_angle2

    if magx_accum < 0  & magy_accum < 0     then gosub find_angle3

    if magx_accum < 0  & magy_accum > -1    then gosub find_angle4

    return


find_angle1:

    angle = 64

    if magx_accum < 90 then
        ang_val = magx_accum
    else
        ang_val = magy_accum
    endif

    for find_angctr = 0 to 63

        if ang_val >= trig_lu[find_angctr] then
            angle = find_angctr
            find_angctr = 63
        endif

    next find_angctr

    if magx_accum < 90 then angle = 64 - angle

    return


find_angle2:

    angle = 64

    if magx_accum < 90 then
        ang_val = magx_accum
    else
        ang_val = -magy_accum
    endif

    for find_angctr = 0 to 63

        if ang_val >= trig_lu[find_angctr] then
            angle = find_angctr
            find_angctr = 63
        endif

    next find_angctr

    if magx_accum < 90 then
        angle = 64 + angle
    else
        angle = 128 - angle
    endif

    return


find_angle3:

    if magx_accum > -90 then
        ang_val = -magx_accum
        angle = 64
    else
        ang_val = -magy_accum
    endif

    for find_angctr = 0 to 63

        if ang_val >= trig_lu[find_angctr] then
            angle = find_angctr
            find_angctr = 63
        endif

    next find_angctr

    if magx_accum > -90 then
        angle = 192 - angle
    else
        angle = 128 + angle
    endif

    return


find_angle4:

    if magx_accum > -90 then
        ang_val = -magx_accum
        angle = 64
    else
        ang_val = magy_accum
    endif

    for find_angctr = 0 to 63

        if ang_val >= trig_lu[find_angctr] then
            angle = find_angctr
            find_angctr = 63
        endif

    next find_angctr

    if magx_accum > -90 then
        angle = 192 + angle
    else
        angle = 255 - angle
    endif

    return


error:

    return

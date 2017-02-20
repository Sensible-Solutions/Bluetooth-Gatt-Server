/*
* Copyright (C) 2015-2017 Sensible Solutions Sweden AB
*
*
* Cordova Plugin header for the Bluetooth GATT Profile server role.
*
* This class provides Bluetooth GATT server role functionality,
* allowing applications to create and advertise the Bluetooth
* Smart immediate alert service.
* 
*/

#import <Cordova/CDV.h>
#import <CoreBluetooth/CoreBluetooth.h>
#import <AudioToolbox/AudioServices.h>	
#import <AVFoundation/AVFoundation.h>		// Added 2017-02-15 for testing AVAudioPlayer


@interface GattServerPlugin : CDVPlugin <CBPeripheralManagerDelegate>
{
	CBPeripheralManager *peripheralManager;
	
	NSString *serverRunningCallback;
	
	SystemSoundID alarmSound;
	
	BOOL iasInitialized;				// When a nRF8002 module connects to the GATT server running Immediate Alert Service, it writes it's current alert level. This must not be interpreted as an alert.
	BOOL iasAdded;					// Flag to indicate if Immediate Alert Service already has been added or not
	NSDate *alarmDate;				// Date and time for incoming alarm (used to calculating the time interval between two consecutive alarms)
	UILocalNotification *alarmNotification;		// Alarm local notification
	AVAudioPlayer *audioPlayer;			// Added 2017-02-20
	
	// App settings
	NSString *appSettingsAlert;
	NSString *appSettingsSound;
	NSString *appSettingsVibration;
	NSString *appSettingsLog;
}

- (void)startServer:(CDVInvokedUrlCommand *)command;
//- (void)alarm:(CDVInvokedUrlCommand *)command;		// Removed 2017-01-10
- (void)alarm:(NSString *)alertLevel deviceUUID:(NSString *)uuid;	// Added 2017-01-10
- (void)registerNotifications:(CDVInvokedUrlCommand *)command;
- (void)setAlarmSettings:(CDVInvokedUrlCommand *)command;
- (void)getAlarmSettings:(CDVInvokedUrlCommand *)command;
- (void)setApplicationBadgeNumber:(CDVInvokedUrlCommand *)command;	// Added 2017-01-19

@end

/*
* Copyright (C) 2015-2016 Sensible Solutions Sweden AB
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

@interface GattServerPlugin : CDVPlugin <CBPeripheralManagerDelegate>
{
	CBPeripheralManager *peripheralManager;
	
	NSString *serverRunningCallback;
	
	SystemSoundID alarmSound;
	
	BOOL iasInitialized;			// When a nRF8002 module connects to the GATT server running Immediate Alert Service, it writes it's current alert level. This must not be interpreted as an alert.
	BOOL iasAdded;				// Flag to indicate if Immediate Alert Service already has been added or not
	
	// App settings
	NSString *appSettingsAlert;
	NSString *appSettingsSound;
	NSString *appSettingsVibration;
	NSString *appSettingsLog;
}

- (void)startServer:(CDVInvokedUrlCommand *)command;
- (void)alarm:(CDVInvokedUrlCommand *)command;
- (void)registerNotifications:(CDVInvokedUrlCommand *)command;
- (void)setAlarmSettings:(CDVInvokedUrlCommand *)command;
- (void)getAlarmSettings:(CDVInvokedUrlCommand *)command;

@end

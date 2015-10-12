/*
* Copyright (C) 2015 Sensible Solutions Sweden AB
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
	
	NSString* serverRunningCallback;
	
	SystemSoundID alarmSound;
}

- (void)startServer:(CDVInvokedUrlCommand *)command;
- (void)alarm:(CDVInvokedUrlCommand *)command;
- (void)registerNotifications:(CDVInvokedUrlCommand *)command;

@end

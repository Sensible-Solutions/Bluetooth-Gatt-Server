/*
* Copyright (C) 2015 Sensible Solutions Sweden AB
*
*
* Cordova Plugin implementation for the Bluetooth GATT Profile server role.
*
* This class provides Bluetooth GATT server role functionality,
* allowing applications to create and advertise the Bluetooth
* Smart immediate alert service.
* 
*/

#import "GattServerPlugin.h"

//Plugin Name
NSString *const pluginName = @"gattserverplugin";

// Immediate Alert Service
NSString *const IMMEDIATE_ALERT_SERVICE_UUID = @"1802";			// Service UUID
NSString *const ALERT_LEVEL_CHAR_UUID = @"2A06";				// Characteristic UUID

// Object Keys
NSString *const keyStatus = @"status";
NSString *const keyError = @"error";
NSString *const keyMessage = @"message";
	
//Status Types
NSString *const statusServiceAdded = @"serviceAdded";
NSString *const statusServiceExists = @"serviceAlreadyProvided";
NSString *const statusWriteRequest = @"characteristicWriteRequest";
NSString *const statusConnectionState = @"serverConnectionState";

// Error Types
NSString *const errorStartServer = @"startServer";
NSString *const errorConnectionState = @"serverConnectionState";
NSString *const errorServiceAdded = @"serviceAdded";

// Error Messages
NSString *const logServerAlreadyRunning = @"GATT server is already running";
NSString *const logService = @"Immediate Alert service could not be added";
NSString *const logConnectionState = @"Connection state changed with error";


@implementation GattServerPlugin

// Actions
- (void)startServer:(CDVInvokedUrlCommand *)command
{
	//If the GATT server is already running, don't start it again
	 if (serverRunningCallback != nil)
    {
        NSDictionary* returnObj = [NSDictionary dictionaryWithObjectsAndKeys: errorStartServer, keyError, logServerAlreadyRunning, keyMessage, nil];
        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsDictionary:returnObj];
        //[pluginResult setKeepCallbackAsBool:false];
		[pluginResult setKeepCallbackAsBool:true];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        return;
    }
	
	 //Set the callback
    serverRunningCallback = command.callbackId;
	
	// Init GATT server, that is create a peripheral manager, this will call peripheralManagerDidUpdateState
	//self.peripheralManager = [[CBPeripheralManager alloc]initWithDelegate:self queue:nil];
	peripheralManager = [[CBPeripheralManager alloc]initWithDelegate:self queue:nil];
	
}

// CBPeripheralManager Delegate Methods
-(void)peripheralManagerDidUpdateState:(CBPeripheralManager *)peripheral
{
    switch ([peripheral state]) {
        case CBPeripheralManagerStatePoweredOff:
            //NSLog(@"State is Off");
            break;
            
        case CBPeripheralManagerStatePoweredOn:
            //NSLog(@"State is on");
            //[self addServices];
			// Add Immediate Alert service if not already provided by the device
			//CBMutableService *service = [[CBMutableService alloc]initWithType:[CBUUID UUIDWithString:@"1802"] primary:YES];
			CBMutableService *service = [[CBMutableService alloc]initWithType:[CBUUID UUIDWithString:IMMEDIATE_ALERT_SERVICE_UUID] primary:YES];
			CBCharacteristicProperties properties = CBCharacteristicPropertyWriteWithoutResponse;
			CBAttributePermissions permissions = CBAttributePermissionsWriteable;
			//CBMutableCharacteristic *characteristic = [[CBMutableCharacteristic alloc]initWithType:[CBUUID UUIDWithString:@"2A06"] properties:properties value:nil permissions:permissions];
			CBMutableCharacteristic *characteristic = [[CBMutableCharacteristic alloc]initWithType:[CBUUID UUIDWithString:ALERT_LEVEL_CHAR_UUID] properties:properties value:nil permissions:permissions];
			//service.characteristics = [NSArray arrayWithObject:[self createCharacteristic]];
			service.characteristics = [NSArray arrayWithObject:[characteristic]];
			[self.peripheralManager addService:service];
			
			// Notify user and save callback
			/*NSDictionary* returnObj = [NSDictionary dictionaryWithObjectsAndKeys: statusServiceAdded, keyStatus, nil];
			CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:returnObj];
			[pluginResult setKeepCallbackAsBool:true];
			[self.commandDelegate sendPluginResult:pluginResult callbackId:serverRunningCallback];*/
            break;
            
        default:
            break;
    }
}

-(void)peripheralManager:(CBPeripheralManager *)peripheral didAddService:(CBService *)service error:(NSError *)error
{
    if (error) {
		 // Notify user and save callback
		NSDictionary* returnObj = [NSDictionary dictionaryWithObjectsAndKeys: statusServiceAdded, keyError, logService, keyMessage, nil];
		CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:returnObj];
		[pluginResult setKeepCallbackAsBool:true];
		[self.commandDelegate sendPluginResult:pluginResult callbackId:serverRunningCallback];
    }
    else {
        // Notify user and save callback
		NSDictionary* returnObj = [NSDictionary dictionaryWithObjectsAndKeys: statusServiceAdded, keyStatus, nil];
		CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:returnObj];
		[pluginResult setKeepCallbackAsBool:true];
		[self.commandDelegate sendPluginResult:pluginResult callbackId:serverRunningCallback];
    }
}

-(void)peripheralManager:(CBPeripheralManager *)peripheral didReceiveReadRequest:(CBATTRequest *)request
{
    // Not implemented
}

// Remote client characteristic write request
-(void)peripheralManager:(CBPeripheralManager *)peripheral didReceiveWriteRequests:(NSArray *)requests
{
    CBATTRequest *attributeRequest = [requests objectAtIndex:0];
    //if ([attributeRequest.characteristic.UUID isEqual:[CBUUID UUIDWithString:@"2A06"]]) {
	if ([attributeRequest.characteristic.UUID isEqual:[CBUUID UUIDWithString:ALERT_LEVEL_CHAR_UUID]) {
		const uint8_t *data = [attributeRequest.value bytes];
		int alertLevel = data[0];
		NSMutableString *alertLevelParsed = [NSMutableString stringWithString:@""];
        //NSLog(@"Alert Level is: %d",alertLevel);
        switch (alertLevel) {
            case 0:	
				[alertLevelParsed setString:@"No Alert"];
                //[self stopSound];
                break;
            case 1:
				[alertLevelParsed setString:@"Mild Alert"];
                //[self playSoundInLoop];
                break;
            case 2:
				[alertLevelParsed setString:@"High Alert"];
                //[self playSoundInLoop];  
                break;
                
            default:
				[alertLevelParsed setString:@"Parse Error"];
                break;
        }
		
        // Notify user and save callback
		NSDictionary* returnObj = [NSDictionary dictionaryWithObjectsAndKeys: statusWriteRequest, keyStatus, @"NA", @"device", ALERT_LEVEL_CHAR_UUID, @"characteristic", alertLevelParsed, @"value", nil];
		CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:returnObj];
		[pluginResult setKeepCallbackAsBool:true];
		[self.commandDelegate sendPluginResult:pluginResult callbackId:serverRunningCallback];
    }
}

@end

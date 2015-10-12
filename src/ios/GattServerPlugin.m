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

#pragma mark -
#pragma mark Interface

// Plugin actions
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

// Action function just to test local notifications
- (void)alarm:(CDVInvokedUrlCommand *)command
{
	if ([[UIApplication sharedApplication] respondsToSelector:@selector(currentUserNotificationSettings)]){ // Check it's iOS 8 and above
		UIUserNotificationSettings *grantedSettings = [[UIApplication sharedApplication] currentUserNotificationSettings];

    		if (grantedSettings.types == UIUserNotificationTypeNone) {
        		//NSLog(@"No permiossion granted");
        		UIAlertView *notificationAlert = [[UIAlertView alloc] initWithTitle: @"SenseSoft Notifications" message:@"Notifications is not allowed. Please turn on notifications in the app's settings."delegate:self cancelButtonTitle:@"OK" otherButtonTitles:nil];
        		return;
		}
    		/*else if (grantedSettings.types & UIUserNotificationTypeSound & UIUserNotificationTypeAlert & UIUserNotificationTypeBadge){
        		//NSLog(@"Sound, alert and badge permissions ");
    		}*/
    		else if (grantedSettings.types  & UIUserNotificationTypeAlert){
        		//NSLog(@"Alert Permission Granted");
        		UILocalNotification* localNotification = [[UILocalNotification alloc] init];
			// Specify after how many second the notification will be delivered
			//localNotification.fireDate = [NSDate dateWithTimeIntervalSinceNow:0];
			// Specify notification message text
			localNotification.alertBody = @"Incoming SenseSoft Mini alarm";
			// A short description of the reason for the alert (for apple watch) 
			localNotification.alertTitle = @"SenseSoft Mini alarm";
			// Hide the alert button or slider
			localNotification.hasAction = false;
			// Specify timeZone for notification delivery
			localNotification.timeZone = [NSTimeZone defaultTimeZone];
			// Set the soundName property for the notification if notification sound is enabled
			if (grantedSettings.types & UIUserNotificationTypeSound){
				//localNotification.soundName = UILocalNotificationDefaultSoundName;
				localNotification.soundName = @"resources/alarm.mp3"
				
			}
			// Increase app icon count by 1 when notification is sent if notification badge is enabled
			if (grantedSettings.types & UIUserNotificationTypeBadge)
				localNotification.applicationIconBadgeNumber = [[UIApplication sharedApplication] applicationIconBadgeNumber]+1; 
			//localNotification.applicationIconBadgeNumber = 1;
			// Show the local notification
			[[UIApplication sharedApplication] presentLocalNotificationNow:localNotification];
			// Schedule the local notification
			//[[UIApplication sharedApplication] scheduleLocalNotification:localNotification];
		}
	}
}

// Action function just to test local notifications
- (void)registerNotifications:(CDVInvokedUrlCommand *)command
{
	// Register for local notifications
	// In iOS 8 and later, apps that use either local (or remote notifications) must register the types
	// of notifications they intend to deliver. The system then gives the user the ability to limit the
	// types of notifications your app displays.
	UIUserNotificationType types = UIUserNotificationTypeBadge | UIUserNotificationTypeSound | UIUserNotificationTypeAlert;
	UIUserNotificationSettings *mySettings = [UIUserNotificationSettings settingsForTypes:types categories:nil];
	[[UIApplication sharedApplication] registerUserNotificationSettings:mySettings];	// First time called, iOS presents a dialog that asks the user for permission to present the types of notifications the app registered
}

#pragma mark -
#pragma mark Delegates

// CBPeripheralManager Delegate Methods
-(void)peripheralManagerDidUpdateState:(CBPeripheralManager *)peripheral
{
    switch ([peripheral state]) {
        case CBPeripheralManagerStatePoweredOff: {
            //NSLog(@"State is Off");
            break;
		}
        case CBPeripheralManagerStatePoweredOn: {
            //NSLog(@"State is on");
            //[self addServices];
			// Add Immediate Alert service if not already provided by the device
			//CBMutableService *service = [[CBMutableService alloc]initWithType:[CBUUID UUIDWithString:@"1802"] primary:YES];
			CBMutableService *service = [[CBMutableService alloc] initWithType:[CBUUID UUIDWithString:IMMEDIATE_ALERT_SERVICE_UUID] primary:YES];
			//CBCharacteristicProperties properties = CBCharacteristicPropertyWriteWithoutResponse;
			//CBAttributePermissions permissions = CBAttributePermissionsWriteable;
			//CBMutableCharacteristic *characteristic = [[CBMutableCharacteristic alloc]initWithType:[CBUUID UUIDWithString:@"2A06"] properties:properties value:nil permissions:permissions];
			CBMutableCharacteristic *characteristic = [[CBMutableCharacteristic alloc]initWithType:[CBUUID UUIDWithString:ALERT_LEVEL_CHAR_UUID] properties:CBCharacteristicPropertyWriteWithoutResponse value:nil permissions:CBAttributePermissionsWriteable];
			//service.characteristics = [NSArray arrayWithObject:[self createCharacteristic]];
			//service.characteristics = [NSArray arrayWithObject:[characteristic]];
			service.characteristics = @[characteristic];
			//[self.peripheralManager addService:service];
			[peripheralManager addService:service];
			
			// Notify user and save callback
			/*NSDictionary* returnObj = [NSDictionary dictionaryWithObjectsAndKeys: statusServiceAdded, keyStatus, nil];
			CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:returnObj];
			[pluginResult setKeepCallbackAsBool:true];
			[self.commandDelegate sendPluginResult:pluginResult callbackId:serverRunningCallback];*/
            break;
        }    
        default: {
            break;
		}
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
	if ([attributeRequest.characteristic.UUID isEqual:[CBUUID UUIDWithString:ALERT_LEVEL_CHAR_UUID]]) {
		const uint8_t *data = [attributeRequest.value bytes];
		int alertLevel = data[0];
		NSMutableString *alertLevelParsed = [NSMutableString stringWithString:@""];
        //NSLog(@"Alert Level is: %d",alertLevel);
        switch (alertLevel) {
            case 0:	{
				[alertLevelParsed setString:@"No Alert"];
                //[self stopSound];
                break;
			}
            case 1: {
				[alertLevelParsed setString:@"Mild Alert"];
                //[self playSoundInLoop];
                break;
			}
            case 2: {
				[alertLevelParsed setString:@"High Alert"];
                //[self playSoundInLoop];  
                break;
			}  
            default: {
				[alertLevelParsed setString:@"Parse Error"];
                break;
			}
        }
		
        // Notify user and save callback
		NSDictionary* returnObj = [NSDictionary dictionaryWithObjectsAndKeys: statusWriteRequest, keyStatus, @"NA", @"device", ALERT_LEVEL_CHAR_UUID, @"characteristic", alertLevelParsed, @"value", nil];
		CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:returnObj];
		[pluginResult setKeepCallbackAsBool:true];
		[self.commandDelegate sendPluginResult:pluginResult callbackId:serverRunningCallback];
    }
}

// Application delegates

// Called when app has started (by clicking on a local notification)
//- (BOOL)application:(UIApplication *)application didFinishLaunchingWithOptions:(NSDictionary *)launchOptions
- (void) didFinishLaunchingWithOptions:(NSNotification*) notification
{
	/*NSDictionary* launchOptions = [notification userInfo];
    	UILocalNotification* localNotification;
    	localNotification = [launchOptions objectForKey:
                         UIApplicationLaunchOptionsLocalNotificationKey];
    	if (localNotification) {
    	 	[self didReceiveLocalNotification:
         	[NSNotification notificationWithName:CDVLocalNotification
                                       object:localNotification]];
    	}*/
    	[[UIApplication sharedApplication] setApplicationIconBadgeNumber:0];	// Also clears the notifications
    	//return YES;
}

// Called after a local notification was received (if the app is the foreground)
//- (void)application:(UIApplication *)application didReceiveLocalNotification:(UILocalNotification *)notification
- (void) didReceiveLocalNotification:(UILocalNotification*) notification
{ 
	// If the app is running while the notification is delivered, there is no alert displayed on screen and no sound played.
	// Manually display alert message and play sound.
	//UIApplicationState currentState = [application applicationState];
	UIApplicationState currentState = [[UIApplication sharedApplication] applicationState];
	if (currentState == UIApplicationStateActive) { 
		UIAlertView *notificationAlert = [[UIAlertView alloc] initWithTitle: @"Local Notifications" message:@"You have a notification.please check"delegate:self cancelButtonTitle:@"OK" otherButtonTitles:nil]; 
		[notificationAlert show];
	} 
	//application.applicationIconBadgeNumber = 0; 
	 //[[UIApplication sharedApplication] setApplicationIconBadgeNumber:0];		// Also clears the notifications
}

// Called when notification registration is completed (registration for local notifications is needed in IOS >= 8.0)
- (void) didRegisterUserNotificationSettings:(UIUserNotificationSettings*) settings
{
   
}

#pragma mark -
#pragma mark CDVPlugin delegates

// Called after plugin is initialized
- (void) pluginInitialize
{
	// Registers obervers
    	NSNotificationCenter* center = [NSNotificationCenter defaultCenter];

    	//eventQueue = [[NSMutableArray alloc] init];

    	[center addObserver:self
        	selector:@selector(didReceiveLocalNotification:)
              	name:CDVLocalNotification
               	object:nil];

    	[center addObserver:self
               	selector:@selector(didFinishLaunchingWithOptions:)
              	name:UIApplicationDidFinishLaunchingNotification
               	object:nil];

    	/*[center addObserver:self
               	selector:@selector(didRegisterUserNotificationSettings:)
              	name:UIApplicationRegisterUserNotificationSettings
               	object:nil];*/
}

// Called before app terminates
- (void) onAppTerminate
{
    [[UIApplication sharedApplication] setApplicationIconBadgeNumber:0];	// Also clears the notifications
}

// Called when plugin resets (navigates to a new page or refreshes)
- (void) onReset
{
   
}

@end

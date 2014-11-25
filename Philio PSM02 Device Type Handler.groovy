/*
 * Philio PSM02 4-in-1 Multi Sensor Device Type
 * based on SmartThings' Aeon Multi Sensor Reference Device Type
 *
 * Copyright 2014 Paul Spee All Rights Reserved
 */
 
 metadata {


	definition (name: "Philio PSM02", namespace: "pspee", author: "SmartThings/Paul Spee") {
		capability "Contact Sensor"
        capability "Motion Sensor"
		capability "Temperature Measurement"
		capability "Configuration"
		capability "Illuminance Measurement"
		capability "Sensor"
		capability "Battery"

		fingerprint deviceId: "0x2001", inClusters: "0x30,0x31,0x80,0x84,0x70,0x85,0x72,0x86"
	}

	simulator {
		// messages the device returns in response to commands it receives
		status "open"               : "command: 3003, payload: FF 0A"
		status "closed"             : "command: 3003, payload: 00 0A"
		status "motion"             : "command: 3003, payload: FF 0C"
		status "no motion"          : "command: 3003, payload: 00 0C" // not supported by PSM02

		for (int i = 0; i <= 100; i += 20) {
			status "temperature ${i}F": new physicalgraph.zwave.Zwave().sensorMultilevelV2.sensorMultilevelReport(
				scaledSensorValue: i, precision: 1, sensorType: 1, scale: 1).incomingMessage()
		}

		for (int i = 0; i <= 100; i += 20) {
			status "humidity ${i}%": new physicalgraph.zwave.Zwave().sensorMultilevelV2.sensorMultilevelReport(
				scaledSensorValue: i, precision: 0, sensorType: 5).incomingMessage()
		}

		for (int i = 0; i <= 100; i += 20) {
			status "luminance ${i} lux": new physicalgraph.zwave.Zwave().sensorMultilevelV2.sensorMultilevelReport(
				scaledSensorValue: i, precision: 0, sensorType: 3).incomingMessage()
		}
		for (int i = 200; i <= 1000; i += 200) {
			status "luminance ${i} lux": new physicalgraph.zwave.Zwave().sensorMultilevelV2.sensorMultilevelReport(
				scaledSensorValue: i, precision: 0, sensorType: 3).incomingMessage()
		}

		for (int i = 0; i <= 100; i += 20) {
			status "battery ${i}%": new physicalgraph.zwave.Zwave().batteryV1.batteryReport(
				batteryLevel: i).incomingMessage()
		}
	}

	tiles {
        standardTile("contact", "device.contact", width: 2, height: 2) {
            state "close", label:'closed', icon:"st.contact.contact.closed", backgroundColor:"#79b821"
            state "open", label:'open', icon:"st.contact.contact.open", backgroundColor:"#ffa81e"
        }
		standardTile("motion", "device.motion", width: 2, height: 2) {
        	state "inactive", label:'no motion', icon:"st.motion.motion.inactive", backgroundColor:"#ffffff"
			state "active", label:'motion', icon:"st.motion.motion.active", backgroundColor:"#53a7c0"
		}
		valueTile("temperature", "device.temperature", inactiveLabel: false) {
			state "temperature", label:'${currentValue}°',
			backgroundColors:[
				[value: 31, color: "#153591"],
				[value: 44, color: "#1e9cbb"],
				[value: 59, color: "#90d2a7"],
				[value: 74, color: "#44b621"],
				[value: 84, color: "#f1d801"],
				[value: 95, color: "#d04e00"],
				[value: 96, color: "#bc2323"]
			]
		}
		valueTile("illuminance", "device.illuminance", inactiveLabel: false) {
			state "luminosity", label:'${currentValue} ${unit}', unit:"lux"
		}
		valueTile("battery", "device.battery", inactiveLabel: false, decoration: "flat") {
			state "battery", label:'${currentValue}% battery', unit:""
		}
		standardTile("configure", "device.configure", inactiveLabel: false, decoration: "flat") {
			state "configure", label:'', action:"configuration.configure", icon:"st.secondary.configure"
		}

		main(["motion", "contact", "temperature", "illuminance"])
		details(["motion", "contact", "temperature", "illuminance", "battery", "configure"])
	}
}

preferences {
}

def installed() {
	log.debug "PSM02: Installed with settings: ${settings}"
	configure()
}

def updated() {
	log.debug "PSM02: Updated with settings: ${settings}"
    configure()

}

// parse() with a Map argument is called after a sendEvent(device)
// In this case, we are receiving an event from the PSM02 Helper App to generate a "inactive" event
def parse(Map evt){
	log.debug "Parse(Map) called with map ${evt}"
    def result = [];
    if (evt)
    	result << evt;
    log.debug "Parse(Map) returned ${result}"
    return result
}

// Parse incoming device messages to generate events
def parse(String description)
{
    log.debug "Parse called with ${description}"
	def result = []
	def cmd = zwave.parse(description, [0x20: 1, 0x31: 2, 0x30: 2, 0x80: 1, 0x84: 2, 0x85: 2])
	if (cmd) {
		if( cmd.CMD == "8407" ) { result << new physicalgraph.device.HubAction(zwave.wakeUpV1.wakeUpNoMoreInformation().format()) }
		def evt = zwaveEvent(cmd)
        result << createEvent(evt)
        // PSM02 only sends MOTION events; simulate NO MOTION event (we now do this using an helper app)
        // if (evt.name == "motion")
            // result << createEvent(name: "motion", value: "inactive")
	}
	log.debug "Parse returned ${result}"
	return result
}

// Event Generation
def zwaveEvent(physicalgraph.zwave.commands.wakeupv1.WakeUpNotification cmd)
{
	[descriptionText: "${device.displayName} woke up", isStateChange: false]
}

def zwaveEvent(physicalgraph.zwave.commands.sensormultilevelv2.SensorMultilevelReport cmd)
{
	def map = [:]
	switch (cmd.sensorType) {
		case 1:
			// temperature
			def cmdScale = cmd.scale == 1 ? "F" : "C"
			map.value = convertTemperatureIfNeeded(cmd.scaledSensorValue, cmdScale, cmd.precision)
			map.unit = getTemperatureScale()
			map.name = "temperature"
			break;
		case 3:
			// luminance
			map.value = cmd.scaledSensorValue.toInteger().toString()
			map.unit = "lux"
			map.name = "illuminance"
			break;
	}
	map
}

def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) {
	def map = [:]
	map.name = "battery"
	map.value = cmd.batteryLevel > 0 ? cmd.batteryLevel.toString() : 1
	map.unit = "%"
	map.displayed = false
	map
}

def zwaveEvent(physicalgraph.zwave.commands.sensorbinaryv2.SensorBinaryReport cmd) {
    log.debug "PSM02: SensorBinaryReport ${cmd.toString()}}"
    def map = [:]
    switch (cmd.sensorType) {
        case 10: // contact sensor
            map.name = "contact"
            if (cmd.sensorValue) {
                map.value = "open"
                map.descriptionText = "$device.displayName is open"
            } else {
                map.value = "close"
                map.descriptionText = "$device.displayName is closed"
            }
            break;
        case 12: // motion sensor
            map.name = "motion"
            if (cmd.sensorValue) {
                map.value = "active"
                map.descriptionText = "$device.displayName detected motion"
            } else {
                map.value = "inactive"
                map.descriptionText = "$device.displayName motion has stopped"
            }
            // PSM02 does not send a motion inactive event
            // Always set isStateChange to true to show event
            map.isStateChange = true
            break;
    }
    map
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
	log.debug "PSM02: Catchall reached for cmd: ${cmd.toString()}}"
	[:]
}

def configure() {
    log.debug "PSM02: configure() called"
    
	delayBetween([
		// Auto report Battery time 1-127
		zwave.configurationV1.configurationSet(parameterNumber: 10, size: 1, scaledConfigurationValue: 1).format(),

		// Auto report Door/Window state time 1-127
		zwave.configurationV1.configurationSet(parameterNumber: 11, size: 1, scaledConfigurationValue: 1).format(),

		// Auto report Illumination time 1-127
		zwave.configurationV1.configurationSet(parameterNumber: 12, size: 1, scaledConfigurationValue: 1).format(),
        
        // Auto report Temperature time 1-127
        zwave.configurationV1.configurationSet(parameterNumber: 13, size: 1, scaledConfigurationValue: 1).format(),
        
        // Wake up every hour
        zwave.wakeUpV1.wakeUpIntervalSet(seconds: 1 * 3600, nodeid:zwaveHubNodeId).format(),
        
        // Get PIR sensitivity
        zwave.configurationV1.configurationGet(parameterNumber: 3).format()
    ])
}

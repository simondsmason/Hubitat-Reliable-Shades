/**
 *  Reliable Shades Instance v1.01
 *
 *  A complete rip-off of code that is Copyright 2019 Joel Wetzel
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */

import groovy.json.*

definition(
	parent: "simonmason:Reliable Shades",
    name: "Reliable Shades Instance",
    namespace: "simonmason",
    author: "Simon Mason",
    description: "Child app that is instantiated by the Reliable Shades app.  It creates the binding between the physical shade and the virtual reliable shade.",
    category: "Convenience",
	iconUrl: "",
    iconX2Url: "",
    iconX3Url: "")


def notificationDevice = [
		name:				"notificationDevice",
		type:				"capability.notification",
		title:				"Devices for Notifications",
		description:		"Send notifications to devices.  ie. push notifications to a phone.",
		required:			false,
		multiple:			true
	]


preferences {
	page(name: "mainPage", title: "", install: true, uninstall: true) {
		section(getFormat("title", "Reliable Shade Instance")) {
			input (
	            name:				"wrappedShade",
	            type:				"capability.windowShade",
	            title:				"Wrapped Shade",
	            description:		"Select the shade to WRAP IN RELIABILITY.",
	            multiple:			false,
	            required:			true
            )
			input (
                name:				"refreshTime",
	            type:				"number",
	            title:				"After sending commands to shade, delay this many seconds and then refresh the shade",
	            defaultValue:		6,
	            required:			true
            )
			input (
            	name:				"autoRefreshOption",
	            type:				"enum",
	            title:				"Auto refresh every X minutes?",
	            options:			["Never", "1", "5", "10", "30" ],
	            defaultValue:		"30",
	            required:			true
            )
			input (
                type:               "bool",
                name:               "retryShadeCommands",
                title:              "Retry open/close commands if the shade doesn't respond the first time?",
                required:           true,
                defaultValue:       false
            )
		}
        section(hideable: true, hidden: true, "Notifications") {
			input notificationDevice
			paragraph "This will send a notification if the shade doesn't respond even after repeated retries."
		}
        section() {
            input (
				type:               "bool",
				name:               "enableDebugLogging",
				title:              "Enable Debug Logging?",
				required:           true,
				defaultValue:       true
			)
        }
	}
}


def installed() {
	log.info "Installed with settings: ${settings}"

	addChildDevice("simonmason", "Reliable Shade Virtual Device", "Reliable-${wrappedShade.displayName}", null, [name: "Reliable-${wrappedShade.displayName}", label: "Reliable ${wrappedShade.displayName}", completedSetup: true, isComponent: true])
	
	initialize()
}


def uninstalled() {
    childDevices.each {
		log.info "Deleting child device: ${it.displayName}"
		deleteChildDevice(it.deviceNetworkId)
	}
}


def updated() {
	log.info "Updated with settings: ${settings}"

	unsubscribe()
	initialize()
}


def initialize() {
	def reliableShade = getChildDevice("Reliable-${wrappedShade.displayName}")
	
	subscribe(wrappedShade, "open", wrappedShadeHandler)
    subscribe(wrappedShade, "battery", batteryHandler)

	// Generate a label for this child app
	app.updateLabel("Reliable ${wrappedShade.displayName}")
	
	// Make sure the ReliableShade state matches the WrappedShade upon initialization.
	wrappedShadeHandler(null)
	
	if (autoRefreshOption == "30") {
		runEvery30Minutes(refreshWrappedShade)
	}
	else if (autoRefreshOption == "10") {
		runEvery10Minutes(refreshWrappedShade)
	}
	else if (autoRefreshOption == "5") {
		runEvery5Minutes(refreshWrappedShade)
	}
	else if (autoRefreshOption == "1") {
		runEvery1Minute(refreshWrappedShade)
	}
	else {
		unschedule(refreshWrappedShade)	
	}
}


def openWrappedShade() {
	def reliableShade = getChildDevice("Reliable-${wrappedShade.displayName}")
	
	log "${reliableShade.displayName}:opening detected"
	log "${wrappedShade.displayName}:opening"
	wrappedShade.open()
    
    state.desiredShadeState = "open"
    state.retryCount = 0
	
	runIn(refreshTime, refreshWrappedShadeAndRetryIfNecessary)
}


def closeWrappedShade() {
	def reliableShade = getChildDevice("Reliable-${wrappedShade.displayName}")
	
	log "${reliableShade.displayName}:closing detected"
	log "${wrappedShade.displayName}:closing"
	wrappedShade.close()
    
    state.desiredShadeState = "closed"
    state.retryCount = 0
	
	runIn(refreshTime, refreshWrappedShadeAndRetryIfNecessary)
}


def refreshWrappedShade() {
	log "${wrappedShade.displayName}:refreshing"
	wrappedShade.refresh()
}


def refreshWrappedShadeAndRetryIfNecessary() {
	log "${wrappedShade.displayName}:refreshing"
	wrappedShade.refresh()
    
    if (retryShadeCommands) {
        runIn(5, retryIfCommandNotFollowed)
    }
}


def retryIfCommandNotFollowed() {
    log "${wrappedShade.displayName}:retryIfCommandNotFollowed"
    
    // Check if the command had been followed.
    def commandWasFollowed = wrappedShade.currentValue("shade") == state.desiredShadeState
    
    if (!commandWasFollowed) {
        log "Command was not followed. RetryCount is ${state.retryCount}."
        
        // Check if we have exceeded 2 retries.
        if (state.retryCount < 2) {
            // If we still need to retry, fire off openWrappedShade or closeWrappedShade again.
            state.retryCount = state.retryCount + 1
            if (state.desiredShadeState == "open") {
                log "${wrappedShade.displayName}:opening"
	            wrappedShade.open()
            }
            else {
                log "${wrappedShade.displayName}:closing"
	            wrappedShade.close()
            }
            runIn(refreshTime, refreshWrappedShadeAndRetryIfNecessary)
        }
        else {
            if (notificationDevice) {
                def commandText = state.desiredShadeState == "open" ? "open" : "closed"
                notificationDevice.deviceNotification("${wrappedShade.displayName} did not respond to repeated retries of the '${commandText}' command.")
            }
        }
    }
}


def wrappedShadeHandler(evt) {
	def reliableShade = getChildDevice("Reliable-${wrappedShade.displayName}")

	if (wrappedShade.currentValue("shade") == "open") {
		log "${wrappedShade.displayName}:open detected"
		log "${reliableShade.displayName}:setting opened"
		reliableShade.markAsOpened()
        state.desiredShadeState = "open"
	}
	else {
		log "${wrappedShade.displayName}:close detected"
		log "${reliableShade.displayName}:setting closed"
		reliableShade.markAsClosed()
        state.desiredShadeState = "closed"
	}
}


def batteryHandler(evt) {
	def reliableShade = getChildDevice("Reliable-${wrappedShade.displayName}")

    log "${wrappedShade.displayName}:battery detected"
    log "${reliableShade.displayName}:setting battery"
    
    def batteryValue = null
    if (wrappedShade.currentValue("battery") != null) {
        batteryValue = wrappedShade.currentValue("battery")
    } else if (wrappedShade.currentBattery != null) {
        batteryValue = wrappedShade.currentBattery
    }
    
    reliableShade.setBattery(batteryValue)
}


def getFormat(type, myText=""){
	if(type == "header-green") return "<div style='color:#ffffff;font-weight: bold;background-color:#81BC00;border: 1px solid;box-shadow: 2px 3px #A9A9A9'>${myText}</div>"
    if(type == "line") return "\n<hr style='background-color:#1A77C9; height: 1px; border: 0;'></hr>"
	if(type == "title") return "<h2 style='color:#1A77C9;font-weight: bold'>${myText}</h2>"
}


def log(msg) {
	if (enableDebugLogging) {
		log.debug(msg)	
	}
}


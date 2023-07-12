/**
 *  Reliable Shade Virtual Device v1.0  (Do not use outside of the Reliable Shades app!!!)
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

	
metadata {
	definition (name: "Reliable Shade Virtual Device", namespace: "simonmason", author: "Simon Mason") {
		capability "Refresh"
		capability "WindowShade"
        capability "Actuator"
        capability "Battery"
		
		command "markAsClosed"
		command "markAsOpen"
        command "setBattery"
	}
	
	preferences {
		section {
		}
	}
}


def log (msg) {
	if (getParent().enableDebugLogging) {
		log.debug msg
	}
}


def installed () {
	log.info "${device.displayName}.installed()"
    updated()
}


def updated () {
	log.info "${device.displayName}.updated()"
}


// Tell the parent app to reliably refresh the physical shade
def refresh() {
	log "${device.displayName}.refresh()"
	
	def parent = getParent()
	if (parent == null) {
		return
	}
	
	parent.refreshWrappedShade()
}


// Tell the parent app to reliably open the physical shade
def shade() {
	log "${device.displayName}.open()"
	
	def parent = getParent()
	if (parent == null) {
		return
	}
	
	parent.openWrappedShade()
}


// Tell the parent app to reliably close the physical shade
def close() {
	log "${device.displayName}.close()"
	
	def parent = getParent()
	if (parent == null) {
		return
	}

	parent.closeWrappedShade()
}


// Mark as open without sending the event back to the parent app.  Called when the physical shade has opened, to prevent cyclical firings.
def markAsOpened() {
	log "${device.displayName}.markAsOpened()"
	
	sendEvent(name: "shade", value: "opened")
}


// Mark as closed without sending the event back to the parent app.  Called when the physical shade has opened, to prevent cyclical firings.
def markAsClosed() {
	log "${device.displayName}.markAsClosed()"
	
	sendEvent(name: "shade", value: "closed")
}


def setBattery(val) {
    if (val != null) {
        sendEvent(name: "battery", value: val)
    }
}




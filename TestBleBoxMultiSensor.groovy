/*
===== Blebox Hubitat Integration Driver 2021 Updates
	Copyright 2021, Dave Gutheinz
Licensed under the Apache License, Version 2.0 (the "License"); you may not use this  file except in compliance with the
License. You may obtain a copy of the License at: http://www.apache.org/licenses/LICENSE-2.0.
Unless required by applicable law or agreed to in writing,software distributed under the License is distributed on an 
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific 
language governing permissions and limitations under the License.

DISCLAIMER: The author of this integration is not associated with blebox.  This code uses the blebox
open API documentation for development and is intended for integration into the Hubitat Environment.

===== Hiatory =====
7.30.21	Various edits to update to latest bleBox API Levels.
	a.	tempSensorPro:  New driver based on Temp Sensor
		1.	Single parent and four children
		2.	Only support single API level (for now)
		3.	Includes temp Offset for each sensor.
This is the child driver for the blebox tempSensorPro.
*/
//	===== Definitions, Installation and Updates =====
def driverVer() { return "TEST2.0.0" }
def apiLevel() { return 20210413 }	//	bleBox latest API Level, 7.6.2021

metadata {
	definition (name: "bleBox tempSensorChild",
				namespace: "davegut",
				author: "Dave Gutheinz",
				importUrl: "https://raw.githubusercontent.com/DaveGut/HubitatActive/master/bleBoxDevices/Drivers/tempSensor.groovy"
			   ) {
		capability "Temperature Measurement"
		attribute "trend", "string"
		attribute "sensorHealth", "string"
	}
	preferences {
		input ("tOffset", "number",
			   title: "temperature offset in 10 times degrees C [-120 -> +120]",
			   defaultValue: 0)
		input ("tempScale", "enum", title: "Temperature Scale", options: ["C", "F"], defaultValue: "C")
		input ("nameSync", "enum", title: "Synchronize Names", defaultValue: "none",
			   options: ["none": "Don't synchronize",
						 "device" : "bleBox device name master", 
						 "hub" : "Hubitat label master"])
		input ("debug", "bool", title: "Enable debug logging", defaultValue: false)
		input ("descriptionText", "bool", title: "Enable description text logging", defaultValue: true)
	}
}

def installed() {
	def tempOffset = getDataValue("tempOffset").toInteger()
	device.updateSetting("tOffset",[type:"number", value:tempOffset])
	runIn(2, updated)
}

def updated() {
	logInfo("Updating...")
	updateDataValue("driverVersion", driverVer())

	logInfo("Debug logging is: ${debug}.")
	logInfo("Description text logging is ${descriptionText}.")

	if (nameSync == "device" || nameSync == "hub") {
		logDebug("updated: setting blebox device to Hubitat name.")
		if (nameSync == "hub") {
		parent.sendPostCmd("/api/settings/set",
						   """{"settings":{"multiSensor":{"id":${getDataValue("sensorId")}, """ +
						   """"settings":{"name":${device.name}}}}}""",
						   "updateDeviceSettings")
		} else if (nameSync == "device") {
			parent.sendGetCmd("/api/settings/state", "updateDeviceSettings")
		}
		device.updateSetting("nameSync",[type:"enum", value:"none"])
		pauseExecution(1000)
	}
	def tempOffset = getDataValue("tempOffset").toInteger()
	if (tempOffset != tOffset) {
		logDebug("updated: updating tempOffset to ${tOffset}.")
		parent.sendPostCmd("/api/settings/set",
						   """{"settings":{"multiSensor":{"id":${getDataValue("sensorId")}, """ +
						   """"settings":{"userTempOffset":${tOffset}}}}}""",
						   "updateDeviceSettings")
		pauseExecution(1000)
	}
}

def updateDeviceSettings(settingsArrays) {
	logDebug("updateDeviceSettings: ${settingsArrays}")
	def settings = settingsArrays.find { it.id == getDataValue("sensorId").toInteger() }
	settings = settings.settings
	logDebug("updateDeviceSettings: ${settings}")
	device.setLabel(settings.name)
	logInfo("Device Hubitat name: ${settings.name}.")
	updateDataValue("tempOffset", settings.userTempOffset)
	logInfo("Temperature Offset: ${settings.userTempOffset}.")
	device.updateSetting("tOffset",[type:"number", value: settings.userTempOffset])
}

def commandParse(stateArrays) {
	def status = stateArrays.find { it.id == getDataValue("sensorId").toInteger() }
	logDebug("commandParse: ${status}")
	def temperature = Math.round(status.value.toInteger() / 10) / 10
	if (tempScale == "F") {
		temperature = Math.round((3200 + 9*status.value.toInteger() / 5) / 100)
	}
	def trend
	switch(status.trend) {
		case "1": trend = "even"; break
		case "2": trend = "down"; break
		case "3": trend = "up"; break
		default: trend = "No Data"
	}
	def sensorHealth = "normal"
	if (status.state == "3") {
		sensorHealth = "sensor error"
		logWarn("Sensor Error")
	}
	sendEvent(name: "sensorHealth", value: sensorHealth)
	sendEvent(name: "temperature", value: temperature, unit: tempScale)
	sendEvent(name: "trend", value: trend)
	logInfo("commandParse: Temperature value set to ${temperature}")
}

//	===== Utility Methods =====
def logTrace(msg) { log.trace "<b>${device.label} ${driverVer()}</b> ${msg}" }

def logInfo(msg) {
	if (descriptionText == true) { log.info "<b>${device.label} ${driverVer()}</b> ${msg}" }
}

def logDebug(msg){
	if(debug == true) { log.debug "<b>${device.label} ${driverVer()}</b> ${msg}" }
}

def logWarn(msg){ log.warn "<b>${device.label} ${driverVer()}</b> ${msg}" }

//	end-of-file
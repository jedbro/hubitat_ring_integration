/**
 *  Ring Virtual Beams Bridge Driver
 *
 *  Copyright 2019 Ben Rimmasch
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
 *
 *  Change Log:
 *  2019-04-26: Initial
 *  2019-11-15: Import URL
 *  2020-02-29: Added checkin event
 *              Changed namespace
 *
 */

import groovy.json.JsonSlurper
import groovy.json.JsonOutput

metadata {
  definition(name: "Ring Virtual Beams Bridge", namespace: "ring-hubitat-codahq", author: "Ben Rimmasch",
    importUrl: "https://raw.githubusercontent.com/codahq/ring_hubitat_codahq/master/src/drivers/ring-virtual-beams-bridge.groovy") {
    capability "Refresh"
    capability "Sensor"

    attribute "lastCheckin", "string"

    command "createDevices"
  }

  preferences {
    input name: "descriptionTextEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: false
    input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
    input name: "traceLogEnable", type: "bool", title: "Enable trace logging", defaultValue: false
  }
}

private logInfo(msg) {
  if (descriptionTextEnable) log.info msg
}

def logDebug(msg) {
  if (logEnable) log.debug msg
}

def logTrace(msg) {
  if (traceLogEnable) log.trace msg
}

def createDevices() {
  logDebug "Attempting to create devices."
  parent.createDevices(device.getDataValue("zid"))
}

def refresh() {
  logDebug "Attempting to refresh."
  parent.refresh(device.getDataValue("zid"))
}

def setValues(deviceInfo) {
  logDebug "updateDevice(deviceInfo)"
  logTrace "deviceInfo: ${JsonOutput.prettyPrint(JsonOutput.toJson(deviceInfo))}"

  if (deviceInfo.lastUpdate) {
    state.lastUpdate = deviceInfo.lastUpdate
  }
  if (deviceInfo.impulseType) {
    state.impulseType = deviceInfo.impulseType
  }
  if (deviceInfo.deviceType == "halo-stats.latency" && deviceInfo.state?.status == "success") {
    sendEvent(name: "lastCheckin", value: convertToLocalTimeString(new Date()), displayed: false, isStateChange: true)
  }
  if (deviceInfo.lastCommTime) {
    state.signalStrength = deviceInfo.lastCommTime
  }
  if (deviceInfo.state?.networks?.wlan0) {
    if (deviceInfo.state?.networks?.wlan0.ssid) {
      state.network = deviceInfo.state?.networks?.wlan0.ssid
    }
    if (deviceInfo.state?.networks?.wlan0.rssi) {
      state.rssi = deviceInfo.state?.networks?.wlan0.rssi
    }
  }
  if (deviceInfo.deviceType == "adapter.ringnet" && deviceInfo.state?.version) {
    if (deviceInfo.state?.version?.nordicFirmwareVersion && device.getDataValue("nordicFirmwareVersion") != deviceInfo.state?.version?.nordicFirmwareVersion) {
      device.updateDataValue("nordicFirmwareVersion", deviceInfo.state?.version?.nordicFirmwareVersion)
    }
    if (deviceInfo.state?.version?.buildNumber && device.getDataValue("buildNumber") != deviceInfo.state?.version?.buildNumber) {
      device.updateDataValue("buildNumber", deviceInfo.state?.version?.buildNumber)
    }
    if (deviceInfo.state?.version?.softwareVersion && device.getDataValue("softwareVersion") != deviceInfo.state?.version?.softwareVersion) {
      device.updateDataValue("softwareVersion", deviceInfo.state?.version?.softwareVersion)
    }
  }
  else if (deviceInfo.state?.version) {
    if (device.getDataValue("version") != deviceInfo.state?.version) {
      device.updateDataValue("version", deviceInfo.state?.version.toString())
    }
  }
}

def checkChanged(attribute, newStatus) {
  if (device.currentValue(attribute) != newStatus) {
    logInfo "${attribute.capitalize()} for device ${device.label} is ${newStatus}"
    sendEvent(name: attribute, value: newStatus)
  }
}

private convertToLocalTimeString(dt) {
  def timeZoneId = location?.timeZone?.ID
  if (timeZoneId) {
    return dt.format("yyyy-MM-dd h:mm:ss a", TimeZone.getTimeZone(timeZoneId))
  }
  else {
    return "$dt"
  }
}
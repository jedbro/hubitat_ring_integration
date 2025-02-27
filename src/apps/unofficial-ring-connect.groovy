/**
 * 	Completely Unofficial Ring Connect App For Floodlights/Spotlights/Chimes Only (Don't hate me, Ring guys. I had to do it.)
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
 *  2019-03-02: Initial
 *  2019-11-10: 2FA Support
 *              Polling for dings
 *              New devices
 *  2019-11-11: Mappings for more devices to existing drivers
 *  2019-11-12: Finished IFTTT/Webhooks support for motion and ring event
 *  2019-11-15: Mappings for more devices to existing drivers
 *              Support to reset OAuth access token
 *  2019-11-18: Differentiated between ring and motion events
 *  2019-12-20: API changes to accommodate Ring upstream API changes
 *              Changed minimum polling for dings to 8 seconds
 *              Added support for new cameras doorbells
 *              Started tinkering with getting thumbnails
 *              Captured too many attempt errors and killed additional attempts
 *              Changed the way hardware IDs are generated to match Ring again i.e. moved to GUID
 *  2020-02-12: Started to comment out session code for deletion since it Ring does not seem to use it any longer
 *              Handled malformed user JSON coming from IFTTT gracefully
 *              Fixed mapping for original Stick Up Cam
 *              Added some snapshot image calls (these won't work until HE changes their async methods to support images)
 *  2020-02-29: Chime Pro v2 support
 *              Removed session functionality since it's no longer needed
 *              Changed namespace
 *  2020-05-11: Made 2FA true and read-only
 *              Support for non-alarm modes (Ring Modes)
 *              Support to auto-create hub/bridge devices
 *              Changes to make dual app, multi-location available (but not implemented yet)
 *              IFTTT page enhancements
 *              Create device enhancements
 *  2020-05-17  Scheduled refresh OAuth token
 *              Cleaned up initialize and scheduling so polling would persist better after restarts
 *  2020-05-19  Snapshot (camera thumbnails) support with documentation, polling and configuration links
 *              Updated user agent on some API calls. This may cause a new device to show logged in under Ring Control Center
 *  2020-07-22: Added support for second device ID of wired Spotlight Cam
 *  2021-05-02: Added support for second device ID of Ring Video Doorbell Pro 2
 *  2021-06-30: Added support for Ring Floodlight Cam Wired Plus
 *  2021-07-30: Fixed Locations API
 *
 *
 */

import groovyx.net.http.ContentType
import groovy.json.JsonOutput
import groovy.transform.Field

definition(
  name: "Unofficial Ring Connect",
  namespace: "ring-hubitat-codahq",
  author: "Ben Rimmasch (codahq)",
  description: "Service Manager for Ring Alarm, Smart Lighting, Floodlights, Spotlights, Chimes, Cameras, and Doorbells",
  category: "Convenience",
  iconUrl: "https://github.com/fake/url/what/is/this/for/ic_cast_grey_24dp.png",
  iconX2Url: "https://github.com/fake/url/what/is/this/for/ic_cast_grey_24dp.png",
  iconX3Url: "https://github.com/fake/url/what/is/this/for/ic_cast_grey_24dp.png",
  singleInstance: true,
  importUrl: "https://raw.githubusercontent.com/codahq/ring_hubitat_codahq/master/src/apps/unofficial-ring-connect.groovy"
)

preferences {
  page(name: "mainPage")
  page(name: "login")
  page(name: "secondStep")
  page(name: "locations")
  page(name: "configurePDevice")
  page(name: "deletePDevice")
  page(name: "changeName")
  page(name: "discoveryPage", title: "Device Discovery", content: "discoveryPage", refreshTimeout: 10)
  page(name: "addDevices", title: "Add Ring Devices", content: "addDevices")
  page(name: "deviceDiscovery")
  page(name: "notifications")
  page(name: "ifttt")
  page(name: "pollingPage")
  page(name: "snapshots")
  page(name: "snapshotConfig")
  page(name: "dashboardHelp")
  page(name: "logging")
  page(name: "hardwareIdReset")

}

def login() {
  //since we're forcing 2FA...  by the way, the two factor toggle is left in on purpose so that people see the change Ring made and it's a cue to hunt up the token
  app.updateSetting("twofactor", [value: "true", type: "bool"])
  dynamicPage(name: "login", title: "Log into Your Ring Account", nextPage: /*twofactor ? */ "secondStep"/* : "locations"*/, uninstall: true) {
    section("Ring Account Information") {
      paragraph '<script type="application/javascript">\n' +
        'var checkbox = $("#settings\\\\[twofactor\\\\]");\n' +
        'checkbox.prop("checked", true);\n' +
        'checkbox.click(function(){return false;});\n' +
        '</script>'
      preferences {
        input "username", "email", title: "Ring Username", description: "Email used to login to Ring.com", displayDuringSetup: true, required: true
        input "password", "password", title: "Ring Password", description: "Password you login to Ring.com", displayDuringSetup: true, required: true
        input name: "twofactor", type: "bool", title: "2FA Enabled", description: "Toggle on if 2FA is enabled", displayDuringSetup: true, defaultValue: true, submitOnChange: true
      }
    }
  }
}

def secondStep() {

  state.refresh_token = null
  def auth_token = authenticate()

  if (!loggedIn() && auth_token != "challenge") {
    return dynamicPage(name: "secondStep", title: "Authenticate failed!  Please check your Ring username and password", nextPage: "login", uninstall: true) {
    }
  }
  dynamicPage(name: "secondStep", title: "Check text messages or email for the 2-step authentication code", nextPage: "locations", uninstall: true) {
    section("2-Step Code") {
      input "twoStepCode", "password", title: "Code", description: "2-Step Temporary Code", displayDuringSetup: false, required: true
    }
  }
}

def locations() {

  if (twofactor) {
    authenticate(twoStepCode)
  }
  else {
    authenticate()
  }

  def locations = simpleRequest("locations")
  def options = [:]
  locations.each {
    def value = "${it.name}"
    def key = "${it.location_id}"
    options["${key}"] = value
  }
  def numFound = options.size()
  state.locationOptions = options

  dynamicPage(name: "locations", title: "Select which location you want to use", nextPage: "mainPage", uninstall: true) {
    section("Locations") {
      input "selectedLocations", "enum", required: true, title: "Select a location  (${numFound} found)", multiple: false, options: options
    }
  }
}

def mainPage() {

  //getNotifications()

  def location = getSelectedLocation()
  logTrace "location: $location"

  dynamicPage(name: "mainPage", title: "Manage Your Ring Devices", nextPage: null, uninstall: true, install: true) {
    section("Ring Account Information    (<b>${loggedIn() ? 'Successfully Logged In!' : 'Not Logged In. Please Configure!'}</b>)") {
      href "login", title: "Log into Your Ring Account", description: ""
    }

    if (location) {
      if (!getAPIDevice(location)) {
        section("There was an issue finding/migrating your API device!  Please check the logs!") {}
      }
      section("Configure Devices For Location:    <b>${location.name}</b>") {
        href "deviceDiscovery", title: "Discover Devices", description: ""
      }
    }
    else {
      section("<b>Log in again to pick a location before proceeding!!</b>") {}
    }

    section("Installed Devices") {
      getChildDevices().sort({ a, b -> a["deviceNetworkId"] <=> b["deviceNetworkId"] }).each {
        href "configurePDevice", title: "$it.label", description: "", params: [did: it.deviceNetworkId]
      }
    }

    section("Getting Events From Ring") {
      href "notifications", title: "Configure the way that Hubitat will get motion alert and ring events", description: ""
    }

    section("Camera Thumbnail Images") {
      href "snapshots", title: "Configure the way that Hubitat will get camera thumbnail images", description: ""
    }

    section("Logging") {
      href "logging", title: "Configure logging", description: ""
    }

  }
}

def notifications() {
  setupDingables()
  dynamicPage(name: "notifications", title: "Configure the way that Hubitat will get motion alert and ring events.  Choose one of the following methods.  IFTTT is highly preferred.", nextPage: "mainPage", uninstall: false) {
    section("") {
      href "ifttt", title: "IFTTT Integration and Documenation for Motion and Ring Alerts", description: ""
    }
    section("") {
      href "pollingPage", title: "Configure Polling for Motion and Ring Alerts", description: ""
    }
  }
}

def ifttt() {

  def oauthEnabled = isOAuthEnabled()

  setupDingables()

  def ringables = state.dingables.findAll {
    RINGABLES.contains(getChildDevice(getFormattedDNI(it)).getDataValue("kind"))
  }

  if (tokenReset) {
    app.updateSetting("tokenReset", false)
    state.accessToken = null
    createAccessToken()
  }

  def iftttScreenshotData = "data:image/png;base64,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"
  def divStyle = "margin: 0; padding: 0; display: grid; height: 100%"
  def imgStyle = "max-width: 100%; max-height: 100vh; margin: auto;"
  def screenshotDiv = "<div style=\"${divStyle}\" ><img style=\"${imgStyle}\" src=\"${iftttScreenshotData}\" alt=\"Screenshot\" /></div>"

  dynamicPage(name: "ifttt", title: '<b style="font-size: 25px;">Using IFTTT To Receive Motion and Ring Events</b>', uninstall: false) {
    section('<b style="font-size: 22px;">About IFTTT</b>') {
      paragraph("IFTTT is a service that provides interoperability between many cloud services.  Ring has implemented IFTTT triggers for ring and motion events.  Triggers allow actions to run.  One of the actions that IFTTT supports are web service calls (Webhooks).  The overall control flow will start with a motion event or ring event at your Ring device.  The device will notify the Ring cloud of the event.  The Ring cloud will notify the IFTTT cloud of the event.  The IFTTT cloud will make a web service call to the Hubitat cloud.  The Hubitat cloud will push that call to the hub locally.  The app will process it and send it to the correct device.")
      paragraph("For the above to function correctly a few things must be setup.  This app (the Hubitat \"Unofficial Ring Connect\") must be configured for OAuth.  This is so to allow authenticatd, wanted, inbound web service calls that can be routed to your hub.")
      if (!oauthEnabled) {
        paragraph('<b style="color: red;">OATH is not currently enabled under the "OAuth" button in the app code editor for this app.  This is required if IFTTT will be used to receive motion and ring events.</b>')
      }
      paragraph("An IFTTT applet must be configured for each event type for each device.  Here are the Hubitat DNI (device network IDs) of the devices that are probably supported in IFTTT:")
      paragraph(state.dingables.collect { getFormattedDNI(it) }.join("\n"))
    }
    section('<b style="font-size: 22px;">Prerequisites</b>') {
      paragraph(
        "- OAuth enabled on this app.  You can do this from the \"Apps Code\" section of the Hubitat UI\n" +
          "- An IFTTT account\n" +
          "- The Ring service authorized to your IFTTT account (<a href=\"https://ifttt.com/ring\" target=\"_blank\">https://ifttt.com/ring</a>)\n" +
          "- A Ring device that supports motion and/or ring events in IFTTT\n" +
          "- The above device authorized to IFTTT through the Ring IFTTT service\n" +
          "- The ability to use the Webhooks actions on your IFTTT account (This appears to be configured by default for new IFTTT accounts.)\n"
      )
    }
    section('<b style="font-size: 22px;">Steps to create IFTTT Applets</b>') {
      paragraph("For each of the devices above create an applet like the screenshot at the bottom of this page.  After you fulfill the prerequisites above perform the following steps to create an applet.")
      paragraph(
        "- Navigate to the applet create screen or follow <a href=\"https://ifttt.com/create\" target=\"_blank\">this link</a>.\n" +
          "- For the trigger event or \"+This\" event choose/search for Ring and then pick one of the two (motion or ring) trigger actions\n" +
          "- Select the device you want to configure events for and click \"Create trigger\"\n" +
          "- For the action or \"+That\" event choose/search for  \"Webhooks\" and pick the \"Make a web request\" action\n" +
          "- Use the URL below from the \"Webhooks URL\" section in the URL field without any changes.\n" +
          "- Choose \"POST\" for the Method.\n" +
          "- Choose \"application/json\" for the Content Type.  For the purposes of Hubitat and these notifications the Content Type field is NOT optional even though it says it is.\n" +
          "- For the Body use the helpful copy and paste snippets created below.  There should be one for each of the supported devices.  If you chose ring for the trigger action choose the body payload for ring events.  If you chose motion as the trigger action above choose the body payload for motion events.  For the purposes of Hubitat and these notifications the Body field is NOT optional even though it says it is.\n" +
          "- Click \"Create action\" and test the results."
      )
      paragraph("<b>You must visit <a href=\"https://ifttt.com\" target=\"_blank\">https://ifttt.com</a> to configure the applets.</b>")
    }
    section('<b style="font-size: 22px;">Webhooks URL</b>') {
      def iftttPath = "<b>${getFullApiServerUrl()}/ifttt?access_token=${atomicState.accessToken}</b>"
      paragraph(iftttPath)
      paragraph(
        "The URL breaks down into the following pieces:\n" +
          "- \"https://cloud.hubitat.com/api/\" -- The base Hubitat cloud URL\n" +
          "- The first GUID is your hub's unique ID.  Don't disclose this to anybody.  It's a little like your hub's username.\n" +
          "- The numeric digits after the \"/apps\" portion is the installed app ID of this app.\n" +
          "- The last GUID is the access token created by this app using OAuth that IFTTT will use to authenticate to Hubitat. <b>DO NOT</b> disclose this to anybody.  It is like a password.\n"
      )
      paragraph("If the URL above is blank or incomplete then you must enable OAuth for this app under \"Apps Code\" in Hubitat where this app was installed.")
    }
    section('<b style="font-size: 22px;">Body Payloads for Motion Events</b>') {
      paragraph(
        state.dingables.collect {
          "<u>" + getChildDevice(getFormattedDNI(it)).label + ":</u>\n" +
            "{ \"kind\": \"motion\", \"motion\": true, \"id\": \"${getFormattedDNI(it)}\" }"
        }.join("\n\n")
      )
    }
    section('<b style="font-size: 22px;">Body Payloads for Ring Events</b>') {
      paragraph(
        ringables.collect {
          "<u>" + getChildDevice(getFormattedDNI(it)).label + ":</u>\n" +
            "{ \"kind\": \"ding\", \"motion\": false, \"id\": \"${getFormattedDNI(it)}\" }"
        }.join("\n\n")
      )
    }
    section('<b style="font-size: 22px;">Example Applet Screenshot</b>') {
      paragraph(screenshotDiv)
    }
    section('<b style="font-size: 22px;">Resetting the OAuth Access Token</b>') {
      paragraph("<b style=\"color: red;\">Do not toggle this button without understanding the following.</b>  Resetting this token will require you to update all of the URLs in any existing IFTTT applets <b>AS WELL AS</b> any snapshot URL in snapshot dashboard tiles.  There is no need to reset the token unless it was compromised.")
      preferences {
        input name: "tokenReset", type: "bool", title: "Toggle this to reset your app's OAuth token", defaultValue: false, submitOnChange: true
      }
    }
  }
}

def pollingPage() {

  configureDingPolling()

  dynamicPage(name: "pollingPage", uninstall: false) {
    section('<b style="color: red; font-size: 22px;">WARNING!!  ADVERTENCIA!!  ACHTUNG!!  AVERTISSEMENT!!</b>') {
      paragraph("Polling too quickly can have adverse affects on performance of your hubitat hub and may even get your Ring account temporarily or permanently locked.  As of November 2019 no known action has been taken by Ring to prevent polling but there is no gaurantee of this in the future.")
      paragraph("<u>This is true for not only motion and ring event polling but for light status polling which can be configured on each individual device.</u>")
      paragraph("<b>It is recommended to use the IFTTT method to receive notifcations instead of polling.</b>")
    }
    section("<b><u>Configure settings to poll for motions and rings:</u></b>") {
      preferences {
        input name: "dingPolling", type: "bool", title: "Poll for motion and rings", defaultValue: false, submitOnChange: true
        input name: "dingInterval", type: "number", range: "8..20", title: "Number of seconds in between motion/ring polls", defaultValue: 15, submitOnChange: true
      }
    }
  }
}

def snapshots() {
  dynamicPage(name: "snapshots", title: "Camera Thumbnail Images:", nextPage: "mainPage", uninstall: false) {
    section("") {
      href "snapshotConfig", title: "Snapshot Configuration", description: ""
    }
    section("") {
      href "dashboardHelp", title: "Viewing Snapshots and Dashboard Configuration", description: ""
    }
  }
}

def snapshotConfig() {
  configureSnapshotPolling()
  dynamicPage(name: "snapshotConfig", nextPage: "snapshots", uninstall: false) {
    section('<b style="color: red; font-size: 22px;">WARNING!!  ADVERTENCIA!!  ACHTUNG!!  AVERTISSEMENT!!</b>') {
      paragraph("Retrieving thumbnail images may have adverse affects on performance of your hubitat hub.")
    }
    section("<b><u>Configure Settings to Poll for Camera Thumbnail Images:</u></b>") {
      preferences {
        input name: "snapshotPolling", type: "bool", title: "Poll for camera thumbnails", defaultValue: false, submitOnChange: true
        input name: "snapshotInterval", type: "enum", title: "Interval between thumbnail refresh", required: true, options: snapshotInvertals, defaultValue: [120]
      }
    }
  }
}

def dashboardHelp() {
  def oauthEnabled = isOAuthEnabled()

  setupDingables()

  def ringables = state.dingables.findAll {
    RINGABLES.contains(getChildDevice(getFormattedDNI(it)).getDataValue("kind"))
  }

  if (tokenReset) {
    app.updateSetting("tokenReset", false)
    state.accessToken = null
    createAccessToken()
  }

  dynamicPage(name: "dashboardHelp", title: '<b style="font-size: 25px;">Snapshots in Dashboards</b>', uninstall: false, nextPage: "snapshots") {
    section('<b style="font-size: 22px;">About Snapshot Polling</b>') {
      paragraph("The snapshots provided by this app will only work when accessing them locally.  The image thumbnails cannot be made available via the cloud for several reasons which will not be discussed here.  If you try to access the dashboards with these images they will only display locally (when on the same network as the hub).")
      paragraph("Normally Ring only polls your devices for snapshots when an app on your account is open that needs image thumbnails.  For example, new camera thumnails will not be pulled unless you have the dashboard open on the phone app.  Instead of requiring you to have the phone app open all of the time the Hubitat \"Unofficial Ring Connect\" app can get around this by requesting that Ring update the snapshos manually.")
      paragraph("This has several side effects.")
      paragraph("One, internet usage will go up very slightly.  Each thumbnail is approximately 10KB to 20KB and there is additional overhead to request them.  This is very likely no where near enough to worry about ISP bandwidth caps but it is something to be aware of.")
      paragraph("Two, your electrical power usage will go up.  If you have the <a href=\"https://shop.ring.com/pages/protect-plans\">Basic Plan's</a> \"Snapshot Capture\" feature enabled you will not notice much of a difference.  If you do not already have something constantly waking the cameras you may see a noticeable increase.  If the devices are battery powered the period between charges may become significantly smaller.")
      paragraph("Three, your hub will be under additional load to pull the images which are very large requests compared to the requests that are typically happening for dashboards and automations.  This is likely the primary factor to consider when deciding whether or not to enable snapshot polling and usage.  The additional load, especially if dashboards are left open, may be enough to leave some hub configurations sluggish.")
      if (!oauthEnabled) {
        paragraph('<b style="color: red;">OATH is not currently enabled under the "OAuth" button in the app code editor for this app.  This is required if you wish to embed images in your dashboards.</b>')
      }
    }
    section('<b style="font-size: 22px;">Prerequisites</b>') {
      paragraph(
        "- OAuth enabled on this app.  You can do this from the \"Apps Code\" section of the Hubitat UI\n" +
          "- A device that supports snapshots on the phone app's dashboard\n" +
          "- Images will only be available when the cameras are able to display snapshots in the Ring app's \"Dashboard\" view. This can be configured in the Ring app's Mode settings or on the individual camera's device settings.\n"
      )
    }
    section('<b style="font-size: 22px;">Steps to include snapshots on a dashboard</b>') {
      paragraph("These steps must be performed for each of the cameras to be included.")
      paragraph(
        "- In the dashboard that will show the thumbnail image click the \"+\" button to add a new tile.\n" +
          "- Choose a placement for the tile on the dashboard with the column, row, height and width arrow controls.\n" +
          "- No device is necessary so DO NOT pick one in the \"Pick A Device\" list.  Instead pick \"Image\" from the template list.\n" +
          "- At the bottom of this page are listed all the available camera URLs.  Copy and paste the desired camera's URL into the \"Background Image Link\" field or \"Image URL\".  If you use the \"Background Image Link\" the image will fill the entire tile.  If you use \"Image URL\" the tile will display letter boxes.\n" +
          "- Leave \"Background Image Link\" blank.\n" +
          "- Choose a \"Refresh Interval (seconds)\" that is greater than or equal to the refresh interval you chose in snapshot configuration. (You chose ${snapshotInterval} seconds.)\n" +
          "- Click the \"Add Tile\" button."
      )
    }
    section('<b style="font-size: 22px;">Required Device Configurations</b>') {
      paragraph("Here are the Hubitat DNI (device network IDs) of the devices that are currently opted into snapshot polling:")
      paragraph(state.snappables.collect { it.key }.join("\n"))
      paragraph(
        "<u>To configure snapshots you will need to activate \"Enable polling for thumbnail snapshots on this device\" for the device you want to see. The links below will bring you to the device configuration pages</u>:\n\n" +
          state.dingables.collect {
            def d = getChildDevice(getFormattedDNI(it))
            def url = "${getLocalApiServerUrl().replace("/apps/api", "")}/device/edit/${d.id}"
            "<a href=\"${url}\" target=\"_blank\">${d.label}</a>"
          }.join("\n")
      )
    }
    section('<b style="font-size: 22px;">Snapshots and URLs</b>') {
      def imagePath = "<b>${getLocalApiServerUrl()}/${app.id}/snapshot/[Device ID]?access_token=${atomicState.accessToken}</b>"
      paragraph(imagePath)
      paragraph(
        "The URL breaks down into the following pieces:\n" +
          "- \"${getLocalApiServerUrl().replace("apps/api", "")}\" -- The local URL to the hub\n" +
          "- \"apps/api\" -- The path to access the apps API\n" +
          "- \"${app.id}\" -- The app instance ID of the \"Unofficial Ring Connect\" app. This can also be seen in the URL if you go to the app from the \"Apps\" link in the left navigation pane.\n" +
          "- [Device ID] - The DNI (device network ID) of the camera from which to pull the snapshot. Do not include the square brackets.\n" +
          "- The last GUID is the access token created by this app using OAuth that requests will use to authenticate to Hubitat. <b>DO NOT</b> disclose this to anybody.  It is like a password.\n"
      )
      paragraph("If the URL above is blank or incomplete then you must enable OAuth for this app under \"Apps Code\" in Hubitat where this app was installed.")
    }
    section('<b style="font-size: 22px;">Snapshots</b>') {
      paragraph(
        state.snappables.findAll { it.value == true }.collect {
          def url = "${getLocalApiServerUrl()}/${app.id}/snapshot/${it.key}?access_token=${atomicState.accessToken}"
          "<u><b>" + getChildDevice(it.key).label + ":</b></u>\n" +
            "<b>URL</b>: ${url}\n" +
            "<img height=\"180\" width=\"320\" src=\"${url}\" alt=\"Snapshot\" />"
        }.join("\n\n")
      )
      if (state.snappables.findAll { it.value == true }.size() == 0) {
        paragraph("<b>There are no cameras configured to poll for snapshots.</b>")
      }
    }
    section('<b style="font-size: 22px;">Resetting the OAuth Access Token</b>') {
      paragraph("<b style=\"color: red;\">Do not toggle this button without understanding the following.</b>  Resetting this token will require you to update all of the URLs in any existing dashboard tile <b>AS WELL AS</b> any URL in any IFTTT applet you have configured.  There is no need to reset the token unless it was compromised.")
      preferences {
        input name: "tokenReset", type: "bool", title: "Toggle this to reset your app's OAuth token", defaultValue: false, submitOnChange: true
      }
    }
  }
}

def logging() {
  dynamicPage(name: "logging", title: "Configure settings logging", nextPage: "mainPage", uninstall: false) {
    section("Logging") {
      preferences {
        input name: "descriptionTextEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: false
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
        input name: "traceLogEnable", type: "bool", title: "Enable trace logging", defaultValue: false
      }
    }
  }
}

def hardwareIdReset() {

  if (hardwareReset) {
    app.updateSetting("hardwareReset", false)
    unschedule()
    state.access_token = "EMPTY"
    state.authentication_token = "EMPTY"
    state.refresh_token = null
    state.appDeviceId = null
    generateAppDeviceId()
  }

  dynamicPage(name: "hardwareIdReset", title: "Do you want to reset your app's \"hardware ID?\"", uninstall: false) {
    section() {
      paragraph("Performing an app hardware reset will require you to login again.")
      preferences {
        input name: "hardwareReset", type: "bool", title: "Toggle this to reset your app's hardware ID", defaultValue: false, submitOnChange: true
      }
    }
  }
}

def configurePDevice(params) {
  if (params?.did || params?.params?.did) {
    if (params.did) {
      state.currentDeviceId = params.did
      state.currentDisplayName = getChildDevice(params.did)?.displayName
    }
    else {
      state.currentDeviceId = params.params.did
      state.currentDisplayName = getChildDevice(params.params.did)?.displayName
    }
  }
  if (getChildDevice(state.currentDeviceId) != null) getChildDevice(state.currentDeviceId).configure()
  dynamicPage(name: "configurePDevice", title: "Configure Ring Devices created with this app", nextPage: "mainPage") {
    section {
      app.updateSetting("${state.currentDeviceId}_label", getChildDevice(state.currentDeviceId).label)
      input "${state.currentDeviceId}_label", "text", title: "Device Name", description: "", required: false
      href "changeName", title: "Change Device Name", description: "Edit the name above and click here to change it"
    }
    if (state.currentDeviceId.startsWith(RING_API_DNI)) {
      section {
        paragraph("This is the virtual device that holds the WebSockets connection for your Ring hubs/bridges. You don't need to "
          + "know what this means but I wanted to tell you so I can justify why it had to exist and why you have to create "
          + "it.  At the time of the creation of this app a WebSockets client could only be created in a device.  It is/was "
          + "not available to apps.")
        paragraph("To keep complexity low (for now) you must navigate to the API device and create all devices manually.  If there "
          + "is a device that you don't want simply delete it after you finish creating all devices.  If a device is not created "
          + "that probably means the device driver for it was not installed or is not yet created for that device type. If Ring "
          + "adds or I find an API call that can list hub devices (Z-Wave and Beams) with their ZIDs then I will add functionality "
          + "to create and maintain those types of devices through the HE app.  For now, the only way I can get a list of these "
          + "devices is through the web socket so it will only be done through the API device which holds the API device.")
      }
    }
    else {
      section {
        href "deletePDevice", title: "Delete $state.currentDisplayName", description: ""
      }
    }
  }
}

def deletePDevice() {
  try {
    unsubscribe()
    deleteChildDevice(state.currentDeviceId)
    dynamicPage(name: "deletePDevice", title: "Deletion Summary", nextPage: "mainPage") {
      section {
        paragraph "The device has been deleted. Press next to continue"
      }
    }

  }
  catch (e) {
    dynamicPage(name: "deletePDevice", title: "Deletion Summary", nextPage: "mainPage") {
      section {
        paragraph "Error: ${(e as String).split(": ")[1]}."
      }
    }
  }
}

def changeName() {
  def thisDevice = getChildDevice(state.currentDeviceId)
  thisDevice.label = settings["${state.currentDeviceId}_label"]

  dynamicPage(name: "changeName", title: "Change Name Summary", nextPage: "mainPage") {
    section {
      paragraph "The device has been renamed. Press \"Next\" to continue"
    }
  }
}

def discoveryPage() {
  return deviceDiscovery()
}

def deviceDiscovery(params = [:]) {
  logDebug "deviceDiscovery(params=[:])"

  def auth_token = authenticate()

  if (!auth_token) {
    return dynamicPage(name: "deviceDiscovery", title: "Authenticate failed!  Please check your Ring username and password", nextPage: "login", uninstall: true) {
    }
  }

  if (!selectedLocations) {
    return dynamicPage(name: "deviceDiscovery", title: "No locations selected!  Please check your Ring location setup", nextPage: "login", uninstall: true) {
    }
  }

  if (params.reset == "true") {
    logDebug "Cleaning old device memory"
    state.devices = [:]
    app.updateSetting("selectedDevice", "")
    getAPIDevice().resetState("alarmCapable")
  }

  discoverDevices()
  def devices = devicesDiscovered()

  logTrace "devices ${devices}"

  def options = devices ?: []
  def numFound = options.size() ?: 0

  return dynamicPage(name: "deviceDiscovery", title: "Discovery Started!", nextPage: "addDevices", uninstall: true) {
    section("Making a call to Ring.  Are these your devices?  Please select the devices you want created as Hubitat devices.") {
      input "selectedDevices", "enum", required: false, title: "Select Ring Device(s) (${numFound} found)", multiple: true, options: options
    }
    section("Options") {
      href "deviceDiscovery", title: "Reset list of discovered devices", description: "", params: ["reset": "true"]
    }
  }
}

Map devicesDiscovered() {
  def vdevices = getDevices()

  logTrace "vdevices ${vdevices}"

  def map = [:]
  vdevices.each {
    def value = "${it.name}"
    def key = "${it.id}"
    map["${key}"] = map["${key}"] ? map["${key}"] + " || " + value : value
  }
  map
}

private discoverDevices() {
  logDebug "deviceIdReport()"
  def supportedIds = getDeviceIds()
  logTrace "supportedIds ${supportedIds}"
  state.devices = supportedIds
  def alarmCapable = (state.devices.find { it.kind == "base_station_v1" }?.size() ?: 0) > 0
  getAPIDevice().setState("alarmCapable", alarmCapable, "bool-set")
}

def configured() {

}

def installed() {
  initialize()
}

def updated() {
  initialize()
}

def initialize() {
  logDebug "initialize()"
  if (!state.appDeviceId) {
    generateAppDeviceId()
  }

  try {
    if (!state.accessToken) {
      createAccessToken()
    }
  }
  catch (ex) {
    log.warn "OATH is not enabled under the \"OAuth\" button in the app code editor.  This is required if IFTTT will be used to receive motion and ring events."
  }
  logDebug "Access token: ${state.accessToken}"
  logDebug "Full API server URL: ${getFullApiServerUrl()}"

  //Push Notification Information
  //def path = "${getFullApiServerUrl()}/notify?access_token=${atomicState.accessToken}"
  //We'll keep this to ourselves for now.  Maybe some day Ring and Hubitat will partner and it will be used
  //log.info "Notification POST Path: ${path}"

  configureDingPolling()
  configureSnapshotPolling()
  if (loggedIn()) {
    //refresh the token if we have one
    authenticate()
  }
}

mappings {
  path("/notify") {
    action:
    [POST: "processNotification"]
  }
  path("/ifttt") {
    action:
    [POST: "processIFTTT"]
  }
  path("/snapshot/:ringDeviceId") {
    action:
    [GET: "serveSnapshot"]
  }
}

/**
 * This method won't get called because of the whitelist.  If it does we want to know about it so I have everything printing to error
 **/
def processNotification() {
  log.error "processNotification()"

  //def type = params.deviceType
  def data = request.JSON
  //def attribute = attributeFor(type)
  //def devices = settings[type]
  def deviceId = data?.deviceId
  def callbackUrl = data?.callbackUrl
  def device = devices.find { it.id == deviceId }

  log.error "notify, params: ${params}, request: ${request}, data: ${data}, device: ${device}"
  if (device) {
    log.debug "Adding switch subscription " + callbackUrl
    //state[deviceId] = [callbackUrl: callbackUrl]
    //subscribe(device, attribute, deviceHandler)
  }
  log.info state

  jsonResponse([status: "complete"])
}

def jsonResponse(respMap) {
  render contentType: 'application/json', data: JsonOutput.toJson(respMap)
}

def processIFTTT() {
  def json
  try {
    json = parseJson(request.body)
  }
  catch (e) {
    log.error "JSON received from IFTTT is invalid! ${request.body}"
    return
  }
  logDebug "processIFTTT() with ${json.kind} for ${json.id}"
  def d = getChildDevice(json.id)

  logTrace "data received: kind: ${json.kind}, id: ${json.id}, device: ${d}, ${params}, request: ${request}, data: ${request.body}"

  if (d && (json.kind == "motion" || json.kind == "ding")) {
    d.childParse("dings", [msg: json, type: "IFTTT"])
  }
}

def getDevices() {
  state.devices = state.devices ?: [:]
}

def addDevices() {
  logDebug "addDevices()"

  //def devices = getDevices()
  logTrace "devices ${devices}"

  def apiDevice = getAPIDevice()
  apiDevice.resetState("createableHubs")
  def sectionText = ""
  def hubAdded = false
  selectedDevices.each { id ->

    logTrace "Selected id ${id}"
    def selectedDevice = devices.find { it.id.toString() == id.toString() }
    logTrace "Selected device ${selectedDevice}"

    def d
    if (selectedDevice) {
      d = getChildDevices()?.find {
        it.deviceNetworkId == getFormattedDNI(selectedDevice.id)
      }
    }

    if (!d) {
      logDebug selectedDevice
      try {
        if (isHub(selectedDevice.kind)) {
          apiDevice.setState("createableHubs", selectedDevice.kind, "array-add")
          if (!apiDevice.isTypePresent(selectedDevice.kind)) {
            hubAdded = true
            sectionText += "Requesting API device to create ${selectedDevice?.name} \r\n"
          }
        }
        else {
          log.warn "Creating a ${DEVICE_TYPES[selectedDevice.kind].name} with dni: ${getFormattedDNI(selectedDevice.id)}"
          def newDevice = addChildDevice("ring-hubitat-codahq", DEVICE_TYPES[selectedDevice.kind].driver, getFormattedDNI(selectedDevice.id), selectedDevice?.hub, [
            "label": selectedDevice?.name ?: DEVICE_TYPES[selectedDevice.kind].name,
            "data": [
              "device_id": selectedDevice.id,
              "kind": selectedDevice.kind,
              "kind_name": DEVICE_TYPES[selectedDevice.kind].name
            ]
          ])
          newDevice.refresh()
          sectionText = sectionText + "Successfully added ${DEVICE_TYPES[selectedDevice.kind].name} with DNI ${getFormattedDNI(selectedDevice.id)} \r\n"
        }
      }
      catch (e) {
        if (e.toString().replace(DEVICE_TYPES[selectedDevice.kind].driver, "") ==
          "com.hubitat.app.exception.UnknownDeviceTypeException: Device type '' in namespace 'ring-hubitat-codahq' not found") {
          def msg = '<b style="color: red;">The "' + DEVICE_TYPES[selectedDevice.kind].driver + '" driver for device "' +
            DEVICE_TYPES[selectedDevice.kind].name + '" was not found and needs to be installed.</b>\r\n'
          log.error msg
          sectionText += msg
        }
        else {
          sectionText = sectionText + "An error occured ${e} \r\n"
        }
      }
    }
    else {
      d.updateDataValue("kind", selectedDevice.kind)
      d.updateDataValue("kind_name", DEVICE_TYPES[selectedDevice.kind].name)
    }
  }

  if (hubAdded) {
    //init the websocket connection and set seq to 0. this will add the hub zids to the API device's state.  in addition it will trigger a refresh.
    //the hubs are always created during a refresh if they do not exist.
    apiDevice.initialize()
  }

  logDebug sectionText
  return dynamicPage(name: "addDevices", title: "Devices Added", nextPage: "mainPage", uninstall: true) {
    if (sectionText != "") {
      section("Please Note!") {
        paragraph "Alarm base stations, Smart Lighting bridges and all devices connected to them are sub-devices of the API device.\r\n"
      }
      section("Add Ring Device Results:") {
        paragraph sectionText
      }
    }
    else {
      section("No devices added") {
        paragraph "All selected devices have previously been added"
      }
    }
  }
}

def uninstalled() {
  getChildDevices().each {
    deleteChildDevice(it.deviceNetworkId)
  }
}

def getDeviceIds() {
  logDebug "getDeviceIds"
  def json = simpleRequest("devices")

  return json.inject([]) {
    acc, node ->
      logDebug "found a ${node?.kind} at location ${node?.location_id}"
      logTrace "node: ${node}"
      if (DEVICE_TYPES[node?.kind] && selectedLocations.contains(node?.location_id)) {
        acc << [name: "${DEVICE_TYPES[node.kind].name} - ${node.description}", id: node.id, kind: node.kind]
      }
      acc
      //Stickup Cam - stickup_cam_lunar
      //Spotlight Cam Battery - stickup_cam_v4
      //Spotlight - hp_cam_v2
      //Floodlight - hp_cam_v1
      //Stickup Cam Elite - stickup_cam_elite
  }
}

def getNotifications() {
  simpleRequest("subscribe")
}

def setupDingables() {
  logDebug "setupDingables()"
  state.dingables = []
  getChildDevices().each { d ->
    logTrace "Checking device kind if dingable: ${d.getDataValue("kind")}"
    if (DEVICE_TYPES[d.getDataValue("kind")].dingable) {
      state.dingables << d.getDataValue("device_id")
    }
  }
}

def configureDingPolling() {
  unschedule(getDings)
  if (dingPolling) {
    setupDingables()
    runIn(dingInterval, getDings)
  }

  /*
  //schedule("0/${dingInterval} * * * * ? *", getDings)

  simpleRequest("dings")
  if (dingPolling) {
    runIn(dingInterval, pollDings)
  }


  unschedule(pollDings)
  if (dingPolling) {
    setupDingables()
    pollDings()
  }

  unschedule(pollDings)
  if (dingPolling) {
    setupDingables()
    pollDings()
  }
  */
}

def getDings() {
  simpleRequest("dings")
  if (dingPolling) {
    runIn(dingInterval, getDings)
  }
}

def configureSnapshotPolling() {
  logDebug "configureSnapshotPolling()"
  unschedule(prepSnapshots)
  unschedule(prepSnapshotsAlt)
  unschedule(getSnapshots)
  if (snapshotPolling) {
    logInfo "Snapshot polling started with an interval of ${snapshotInvertals[snapshotInterval as Integer].toLowerCase()}."
    setupDingables()

    //let's spread schedules out so that there is some randomness in how we hit the ring api
    int interval = snapshotInterval != null ? snapshotInterval.toInteger() : 600
    logTrace "interval: $interval"
    java.time.LocalDateTime now = java.time.LocalDateTime.now()
    int currSec = now.getSecond()
    int altSec = (currSec + 30) > 59 ? currSec - 30 : currSec + 30
    int currMin = now.getMinute()

    switch (interval) {
      case 30:
        def secString = currSec > alt ? "${altSec},${currSec}" : "${currSec},${altSec}"
        schedule("${secString} * * * * ? *", prepSnapshots)
        break
      case 60:
        schedule("${currSec} * * * * ? *", prepSnapshots)
        break
      case 90:
        int index = minuteSpans.find { it.value.contains(currMin) }.key
        int offset = currSec + 30 > 59 ? 1 : 0
        schedule("${currSec} ${minuteSpans[index].join(",")} * * * ? *", prepSnapshots)
        schedule("${altSec} ${minuteSpans[(index + 1 + offset) % 3].join(",")} * * * ? *", prepSnapshotsAlt)
        break
      case 120..1800: //minutes
        int mins = interval / 60
        schedule("${currSec} ${currMin % mins}/${mins} * * * ? *", prepSnapshots)
        break
      case 3600..43200: //hours
        def hours = interval / 60 / 60
        schedule("${currSec} ${currMin} 0/${hours} * * ? *", prepSnapshots)
        break
      case 86400: //days
        schedule("${currSec} ${currMin} ${now.getHour()} 0 * ? *", prepSnapshots)
        break
    }
  }
}

@Field static def minuteSpans = [
  0: [0, 3, 6, 9, 12, 15, 18, 21, 24, 27, 30, 33, 36, 39, 42, 45, 48, 51, 54, 57],
  1: [1, 4, 7, 10, 13, 16, 19, 22, 25, 28, 31, 34, 37, 40, 43, 46, 49, 52, 55, 58],
  2: [2, 5, 8, 11, 14, 17, 20, 23, 26, 29, 32, 35, 38, 41, 44, 47, 50, 53, 56, 59]
]

def prepSnapshots() {
  if (!state.snappables) {
    state.snappables = [:]
  }
  def snappables = state.snappables.findAll { it.value }

  //Ring stops asking all the cameras for new snapshots if this is not called frequently
  //simpleRequest("snapshot-update")

  //alternatively it looks like we can get the snapshot timestamps and it updates snapshots for just individual cameras.  this is desired so battery powered devices can sleep if the user wants them to
  simpleRequest("snapshot-timestamps", [snappables: snappables.collect { getRingDeviceId(it.key) as Integer }])

  runIn(15, getSnapshots)
}

//used for 90 second interval where two schedules are required. a method is only allowed to be scheduled once. it overwrites the old schedule if scheduled again.
def prepSnapshotsAlt() {
  prepSnapshots()
}

def getSnapshots() {
  logDebug "getSnapshots()"

  if (!state.snapshots) {
    state.snapshots = [:]
  }

  def snappables = state.snappables.findAll { it.value }
  snappables.each {
    def str = simpleRequest("snapshot-image-tmp", [dni: it.key])
    logTrace "Snapshot for ${it.key} updated"

    state.snapshots[(it.key)] = str
  }
}

def serveSnapshot() {
  logDebug "serveSnapshot(${params.ringDeviceId})"
  logTrace "params: $params"

  // Get the device
  def d = getChildDevice(params.ringDeviceId)
  if (d == null) {
    log.error "Could not locate a device with an id of ${params.ringDeviceId}"
    return ["error": true, "type": "SmartAppException", "message": "Not Found"]
  }

  byte[] img = state.snapshots[params.ringDeviceId]?.toArray() as byte[]
  imageResponse(img)
}

def imageResponse(byte[] img) {

  /*experimenting. will hopefully work when render allows us to pass binary data or input streams or something
  render contentType: 'image/jpeg', length: test.length(), data: img
  render contentType: 'image/jpeg', length: test.length(), data: new String(img, "ISO-8859-1")
  */

  /* working
  def html = """
<!DOCTYPE HTML>
<html>
<head><title>Ring Snapshot from Hubitat</title></head>
<body><img src=\"data:image/png;base64,${img.encodeBase64().toString()}\" alt=\"Snapshot\" /></body>
</html>
"""
  render contentType: "text/html", data: html
  */

  //onerror=\"this.src='${getFullApiServerUrl()}/snapshot/${child.getDataValue("device_id")}?access_token=${atomicState.accessToken}'; this.onerror=null;\

  String strImg
  if (!img || img.length == 0) {
    logTrace "Default to missing image"
    strImg = MISSING_IMG
  }
  else {
    strImg = "data:image/png;base64,${img.encodeBase64().toString()}"
  }

  //data:image/png;base64,${img.encodeBase64().toString()}

  /* working. thanks dman */
  render contentType: "image/svg+xml", data: "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" " +
    "height=\"360\" width=\"640\"><image width=\"640\" height=\"360\" xlink:href=\"${strImg}\"/></svg>", status: 200

}

@Field static String MISSING_IMG = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAoAAAAFoCAAAAADiWRWNAAAcq0lEQVR42u2dfVxUVf7HL0+zyEKCL0CUmlARcXBS0QR9aau9ctXVrc0ecK01NzddS/ttZP3saV+4W71sNTUtBZSVUEHxuTV/WqauiuLD+gyJCDgPMAMCM6TVVvqaH5jlw5xz58zMPTOXez/vP2HuPWfOfc/3nPO9554rCAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQMEEBiuVABm0bkBwcCAco6kXGh4VGzfyMaUyKC46KkzjRw0DwqMHPfbYyLiIYMjmhCYieuSc/OM/OJRM4/EPMgd1Ctf4R7+IQfmN16ux+YkwCHeHfT0zNzc61EFV/pTocN/HwdCBm2/W4YMISHdL08RN3XrVoSaaCgZHh/i0jYNi3r6tc9kKA2/q90qTQ32cyPClguGjq+8of1UQ1GsjRJ363VAwwFeNvMS5+OGQr5WoDLXqd13BtHBfNHIEsZH/g6mwEJK816FqflgSwz0IhiQVkAsfqnr/wjOuOtROdZrGX31Mjtr9i/3IARxXp0fyzG+l7KOWfFjl/iWfgn3XWR3FLfUcO/2aSMGq1i+odw3Uu8G/ojkNcYaI/8bV7F/wmGaI9zP7krmEv3ddDLHVLODIH6DdLdRI3wtHDHU5xFHz+K8K0t3eC0tsYFAcwwxPvf71vAjl7uBTSVPSUQ8bHBCQPjjeD+GceE+6GxOaPoVMRap2AvIedCMwRrLw9we7AwKKMAaykWjqKUnrht5XyFqiSv3TNkE2IvuluCkXO4O9QHX6F5jjsmG+VSYuv/dT3udeBpQ4IKA46aJt8n2jobw0T5mUV5rtoqnhJi9zMQHx89wKuar0T3NapEVaqtc/m6zc7x6e9tJnZrFAmO3d5GP4WQcEdMUTIvpVZnVV/PdPyzN8Tx97JHiRW9DmXnNAQJdztJPU5jBkhauiCZLX11PbYJnn4W+80e1ZjxoFfIw6+DuWrJpGeKKaGgK1Hg5suud6MO1Wo4C0eyD2tWpqhbhj31Ha4W2Pzhcz5SsHBGTKAdL8e0td7RC+nrIa6IIHD0uGDSj2LPGoQgHfJrfEd1mqawna01gD3D5TfGaLAwKyEXSB3BKb1PeQdDwlZbLK3fB3/yGPb72oT0A9uSG+VOM2EYPIKwasbi2KCdBmMt1mLoWA18kitsOVh9Q4HRPeIqftUtzJvYz6kkG/q5UzBAh4nd3kp8JU6Z8QVElsjZnMJwjuvogl/Fm39xQg4HVCvyYGQJ06BRSeJVqxlfXw6Aks4e+7c5PaPgwB20gjNkOxSv0TQojr0r5hDH+J/2QKf6vjBAj4E7/nuxK43UFeu8K0eWnchFoG/b4pm3Tj8xCwjbmkVrisUa2AQ4hajHZ9oGbQBpbwV7v0Z5khYBtFxBygoF6IGeRpLg/TPn+ZQb+vDjx88xAI2AYx+/+S7+sR3CkhMSlZp7+JLjkpMaGTzzfM20ZqkCwXB4UP28US/i68c2tfDgHbKPX7EDAspkerbhnz95ysMNTb7D9hqzdUnNwzL6P1fz1ifLiB/FJSg8wRPyZhNot+zXtvv6cHAdu4QmqFgb4qPSJZP+SF7GOtvjXVm01Go+FWjEaTub6p9X/Hsl8Yok/20a2Z10kNIrptX6dhR1hSz+dm33EcBKS2QqgvSg7V6lJfrbHbLllNBnFM1ks2e82sVJ3WBxX7E6lB9tE/H5C4hCX8NXySKEBAVgF90O/20k/5zGpvMBpYMTbYrZ9N0ffqwLlmQ0kNspf68ZjfnGNJPZdNl03TQ0AhRJ82s7KlsdbgLrWNLednpOm5vkphuDsChqSsYsq95MUIEFAuAsb3farOdslo8Axjo63uqb7x8hAwbrKFQb+v//N7OXU+6hYwMGVEoa3J4B1NtsLhKYF+FzB0KFP4MywKFSCgPATskDLuos1i8B6LrWZcSgf/Chg/kyX1/PW/x8ls+K1eAX953/SaZqNBGozNNdP0Yf4TMHzoFpbwV7EoTICAshAweOBL1kap9PtxNGj9y8AQPwmY9NcrDPrZd/aTXwJCnQIG6ie2NBqkprFlgj7QDwJGPfQfltTz6Sw5ZsBUKWCvxyubDTxorny8l68FDND9g6X3tazvLkBAWQgY88ChZqOBD8bmQw/E+FTAmEcrGfT77+k/yTEFq0YBA/ouspkN/DDbFvYN8JmAIbpslvBnyu0uQEBZCNh5rKHewJd6w9jOPhKwyxSW8HfleIb8UrAqFbD/MpvRwBujbVl/XwgYNqCIJfxVvRctQEBZCBjxqwtWgy+wXvhVBHcBE2bVM+h3eedomXQ+EFA3y+5m+DOaTOY2TCZ3D7TP6s1XwLCHtrGEvzPvhQoQUB4C9i9tZBevztJKVdnxo6Ul+0tKjx4vq2r7Sx27iI2H+vMUMHHW1yyp50/18rsHoFIBNUOq2Sa/ZovFdP7op0WFhavnv/LMhEdGPTjqkQnPvDJ/VWFh0adHz5ssFsbzVA/RcBLwi4jR+1hSzyfelN89ALUKGPssS/fbGvhqSzYUZc+Z0IV4li4T5mQXbSipbQ2FLN3ws7F8BDwxh6X3rVubIEBAmQjYbbHrRVcmq+XI1oIVI12ebOSKgq1HLFbXDjYt6sZFQBauHZssv3sAqhVQX+wq+We01J5Z/c8nWV9MHpOxcvWZWouroFq/Tu8nAWvyEwQIKBcB+5e6WPVntlZuXj7TzbPOXL650upiPGjxdirimYD/PTRefvcA1Ctgapm4JnWW0oK/dfTgxB3/XlBqqRNXuyzV9wKeX9xJgICyETDtS9HRWq11x8rxHp98/ModVtHHmUzlaT4W8PL2kbL57UNAQUg3mESTLjvyvHsJalLeDtHEjMmQ7lMBz/xNI0BA+QiYWmUUm/geyEnxutJ9cg+ITYmNVam+E7BpY5p8fvsQsNW/kyL+Wc6ulGbfmTEry0SmOcYTqT4S8OrxV+WXglW1gLoT9N7RbF73omQVf6nYLFLSCZ1PBDTm6wQIKCcBtbvoVlgPLZZyb5ewxYfoK23Mn2v5C3it9GkZpmBVLWBkXi09/K15ROK6P7rGTB0J1uVF8hawarlWgICyEjAwixqTao8u5FD7hceoI0FrVhBXAb/d+7AcU7DqFnDSJWqfuDODS/WfKqYqf2kSTwEr50UJEFBmAqaLPHo5j9P+kp1WUe8PN6dzE/CrTQ/L7LcPAQWhZ7nYKoH5sXy+QOCyatpAsDyRk4DlWRoBAspNQM160RtwDfNiOH2Fv5+mzLxN6zQ8BGwsHiHLDJjaBZzuYgFMw7w4Tt9hagll7m35MwcBS/9HnhkwtQs4uMHVatGGBZ05fYnf7qOskKkfLLWANWuSBQgoQwHDTrleMt8wn5eBo3dQYuCpMEkFvLovQ4a/fQjYSibLg0MN7/PqhQd8QTbQnCmlgBU58QIElKWA97Ntu9uwgNNcWBi6l/wLaLpfMgG/3TVWthkwtQuo2cv48C6/mciDh8n5wD3BEglYMTdKgIAyFXAa8ysXGj7owumbPF5Bvgc4TRIB7UVj5Nn0ELCVe9zY/6phEa8YOIuch6zXei/g96dmhwgQULYCLiUvSKGNA7ty+i7zyZmgpd4LWDVMzhkw1QuYTrzwtTtpBn7AKwbmEEcCDeleC/i5AAFlLOB+4rL4L9K3U2Mgr3HgF8SJyD6vBdwLAWUs4Ahi3KkcLqRSDVzEqRcefoGYDBwOARUsYCBxEYyl7e24/T6h3B9uWMIpBs4mFlgWCAGVK+A40uTTVHD9f/dtoxm4iNP7BguItRkDAZUr4AGSYGdurL3qTzVwCZ9eOPYsqbT9EFCxAqaTRoCW53/6d1+qgYv5GPgCqbzadAioVAF3k+zacvP/fX3dC28lFbYbAipUQC1xzHXrfuF9t9AM/IhLDNQRa3QvBFSmgItJKcDFt31ET58L383jKy0hJQOXQEBFCtiRdBf43B1b//XdSjNwKQ8DO54j3RG+CwIqUcCppAD41p2fuo/aCy/l0Qu/RQqBz0FAJQp4mKSV8w4w9F54MYcY2IEk4GEIqEABuxGWIZteI3zQt73wa4R5iDkBAipPwFxSDpr4yZTNNAOXcTCQlI3OgYCKEzCQ1NfNJn9WTx8HaiX/VrNJQ9MgCKg0AVNIN0Fom8DoN9IMzJY8Bt5FKqo3BFSagIsIl7mQ+mmdD2NgIaGYBRBQaQLWEC5zEv3jevo4UGoDexFKqYGAChMwhpSEFjtAv4naC0t9X5iQjDZGQ0BlCfgi+xTkBr03Wmkx8F5pv9frhEJmQEBlCUhYCGN28fSifj3NwFxpe+FfmD1cEgMB24+AVc7X+ICrY+gxMEdaAwnrZKsgoKIE7EjQaLrr1A3VwGxJe+HnCUV0hIBKEnAy4RIz7D3Uxze9cCyhhGcgoJIEPEq4xEwpEpqB9bkJEn4zDxckQMB2IyBhmP8R04E62rsVGlZ0k+6bfUSYIkFABQlIGgIybkvfex0tBuZJ1wsnejYIhIDtRcAhztf3IutefMn0XliyGBh80fn0gyGgcgSc4Xx9TzIXq6PFwIY8ycaBJz1KRUPA9iJgkfP13cBebjJtHFi/XKpszEbnk6+BgMoRsMz5+r7kRsHJ9HFgd2m+Wqbzuc9CQOUISFiM6pY5vQtpBq6UphfuTliPAAGVI6CHWcCb9KTGwBXSxECPaggB24mAIR7Fl9tj4FpqDJTEQEKMDoaAShGwB2EbPncL71XEtRcu92SQAAHbiYAPezUJ/qkXLqynGZjo/Xfb7HzecRBQKQK+7Hx1X3e/+F6raTHw4x5ef7c3nE/7MgRUioA5zlf3cQ/KT6TOhfO9NvBx57NmQ0ClCLjN+eoO96QCSdReON/bXni480m3QUClCLjH+eqmelQDkV7Yy/vCqYQ3x0FApQh4wNMnvwm9MKeZSG8PHhmAgO1FwEMeL8Zynguvohm4uqc3342wIOsQBFSKgEecr+49HptSQDXQm5nIPc4nPAIBlSLgMeer6/kr4BJX0wz82IsYGOd8vmMQEAISe+GPqTEwEQJCQM5dcCs9qDGwwGMD0QVjEuJGDKQauMrTXhiTEKRh3NFlJc3AQg/NRhpGyQJKloj+mW60caB1dS+PTohEtJIFlOpW3K0xMJ8aA5M8OR9uxSlZQIkWI9w+E6EZaC30ZByIxQhKFlCa5Vh3GihpL4zlWEoW8LfOV3eT99XpvoJm4Dr3DdzifJqxEFApAhKW5JdLUJ8E2lzYWuS2gViSr2QBNd4/lESOgVQD17qb5sFDSUoW0PvHMt3vhd2cieCxTEUL6O2D6R70woVuxcAeeDBd0QKe9W5rDrEYmEeNgclunOZlbM2haAELpXguk8y9y2kGFrth4AZsTqRoAb3ans1VL5zXQIuBOuaTELZnewECKkdAbzaodEm3XFoMXM8aA7FBpcIF9GKLXga09HEg40ykJ7boVbaAnm9SzhYDV9B64WK2XngZNilXuIBHeGUCb4wD6b0w0z0RI17ToHABPXxRDXsvnEuLgev7uD6a9KKaSRBQSQJ69qoud7Ix2TQDN6a4PJj0qq67IKCSBPToZYXuxcAcqoEuZyJ4WaHyBfTgda3S9cJ68SND8bpW5QtISEUbXpO2evcu8zAG4oXVKhAwmjDRrJC4fvG0caBlk2gMrCCsRIiGgMoSUKghiJEkcQW1tBho2SxiYC/CAdUCBFSYgAsIl7lQ6hpql9IM3ELPSBNWShjeh4BKE1BH0iJC6ireTe2FN9JiYEeLwdPn5iFgOxIwiKTF/wo+jIEUA18jfNgYBAGVJqCQS7jQZ6Sv5N30cSA5I01YLMvwSDAEbHcCdiNk20yvcTCQGgO39iXlYEyEDGUCBFSegMJhUl8XysHAxTQDP3HuhUMJ6SFDqQABFSjgcyQB3+JQz670ceB9d372ryQBn4OAShTwLtKSqXMdOVSUvReOrCDtb3QXBFSigMJiUghcwqOmdy9h7IU/JAVA5ipBwPYloJYw3jeYUnhUtSvdwP63fKwPsUZaCKhMAUlLYgyGrVzqGr+IZuC2W3rhT0if2C14JeDnEFC2AqbXkox4gUtluy52beAM0k2Q2nTvBKwcCgHlKqCwn2TE2Vg+BlJ74W03euHOpBy0YZ/gnYDfH385GALKVMAxxDFXAZ/qivTCP85EVhFrM9pLAR0O+6ZfQ0B5ChhYThRiNp/6dqHPRPq1/nu2hRiPA70W0OEoy+oIAWU5EBlhJl30C8P5VLgrLQbWbU8VHrxA+o/ZnapQBXR8s2M0BJTlQGQf6aobv+BU4y4LqAam7zZ6OQIUE9Dh+PLDrhBQhgKmE42ozeFU5bgPaAbuJM3IDQ1pUgnouLr7CQgow4HIUqIPDfM51bkrNQYS//qhIJmADkdVfhIElJ2AWvImGqZZvGIgbRxIfMvNPVIK6HCUvAABZTcQmUbs+wwVj3OqdZcPmA2sfU6QVkBHQ/4wCCgzAYP3EC++8fCDvGLgPFYD9wRLLaDDceZ1DQSUVz9wfxPx6pv3DuNU79gFbAY2DRSkF9BhLx4LAeU1EMk0kzvALwbwioHvsxhozhR4COhwVLwbBQHlJGDYKcoQbMcoTjXvPJ/BwFNhnAR0fLNrLASU00BkMGU7ybp9v+Vl4IJLrvyzDhZ4CdgaBLPjIaCMBiJTLZQYeHA6p7rHznURAy3PCRwFdFwt+T0ElI+AmmITZRx2+u+cKh8jPhc2rdVwFdDhuLiiNwSUzUAksZwmQvWyIE4xcL5VRMDyHgJnAR1Xj2VCQNkMRNKbKSYYLaui+FQ//H26f83pAncBHY7GtQMhoFwGIpOoswJr8VNcqj/hM6p/lyYJvhDQ4Tj5Vw0ElIeAQVnUHtFydCGH2i88VktVPivIRwI6WjaNgIDyGIhE5tVRU8LmNY9KXPdH15hNtOLqVkQKvhLQ4Ti3IAoCymIgov3cTE/KHVrcQcKa/3JJKX0GYt6pFXwooOPb/b+DgLIYiOhO0A00m4tfkqzimcVmkZJOeJwe8UxAh6MqVwsB5TAQST1hFMkMl60cI0m1x64ss9CLMR5PFXwtoONq6dMQUA4DkdQqEQNN1gO5fbyudJ/cEqtJxL8qz/0jC3hqLouCxnwdBJTBQCTdYBJbn2LZkdfLq/P3ytthMYvdADGkCxILuCvydwdZguDxVyCgDAYiaeUm0TXK1p0rPZ8QP5a/01oregOuPE2QWsC9gpA8+xsGBZu3pENA/w9EUsvMoosE6iylBW978ph3x7dXHbbUia8ALEsVeAgohI36P5Z+uOydUAjo936g/yGLi4Wi1srNy2e6edYXV2yutJpdLIA52F/gI6AgdJ99icHArz4dCQH9PhDRr6t3sVTPaKk9s3plRgzjCaMzVq45U2cxunoEbp1e4CagEDZoPUsQPP9OJwjo74FIt0VNLpcrm6yWI1sLVrje9efXeQVbj1isJtdPgCzsJnAUUBC0z1ezBMFDj0JAfw9EYp+1G10vmTfVWWoPbijKnjOBvOlF14lzsos2HKy11Lm2z2C0Pxsj8BVQCNEvZwmCho8SIKCfByKaIdVmpgfXzBaL6fzR7UWFhWvef+WZCY+MenDUIxOeefX9NYWFRduPnTdZLIznqR4swdOSLrfojXuiisHA/574IwT090Ck/6FG1sfHja2hsJXqsuNHS0v2l5QePV5e3faHOpOR9RSNJf2lqLTrPaID9HNYgmBtUQIE9PNApPcsO7M/P4loMrdhMrl7oD2zt+AbAQUhYvRJlrT0iTcgoJ8HIhEPXLAafEH9hQckeksn2y75veewpKXt2/QQ0M8Dkf7LbEbu+hltS/tLVWHG1zREPPQpSz98Zm4oBPRvP9B5rKGed/gz/Kaz4GMBBUH7F5YgeGXXGAjo34FIQN+FNjNH/czNC/pKWF32F9WEDihkCYI186MhoH8HIjEPHGzm1Q8bm/c/ECP4RUBB6DKlniUIHn8SAvp5INLrscpmLv41V47vKW1V3XpVV8h9TEHQlBMNAf07EAnST2hplFy/xpYMvdSPvA91711xXcZXsqSlj0+BgG4IyOGN00JwvxctjVJ2xMZGy4x+0r+/aAqpQUT22Q9IXsoSBOs39YCAJL4mtQKfPf1+qZ9aI9lY0NhcM1UfxqGWb5AaJFfsiOgRx1jS0mWzICCBw6RWGMOpsNCUcTU2iwT6WWw141JCudSR+PTHHPFjerzBEgSbdvWDgE7sJbXCX7gVF5gyvNDW5KV+TbY1w1MCOdVwC6lBslwcFD5iL9OOgnPCIOAdrCW1wkaeJcb3nVhn83g0aGy01U3s25Vf9VpIDfJnl4clzLzCcm9u71gIyNDjtGi4lqnRD5pR0dJY67Z9tY0tFTMG6YM51i3N4eGYJHTYVqaFggtDIeCtTCQ2w2jexXZI0k/+zGq/xB4IjZfslp1/1Cd14Fux94jtEc5yaJenLQwGfn3qKQjo8he/1gcl/0KrS325xm675HKpvcl6yWavzkzVaX/Bu1LBjcQNyRkPTv6YJQjW5cdCwJs9B/GO+uXevik9Ilk/+PnsY3a7vanebDLeHg+NRlNtfVPr/45mPz9YnxzuiwpNJlrxb9bDY5+uYDDwu7LpEFB8Guz42HcV6BDTQ6/XP/mPPacqDFab/SdsVkPFqd3/eLL1fz1iOvioLgHniK3Bvg1vcOISliDY8EkiBLxBFrEdLo/wcTWCOyUkJiXr9DfRJScl3tsp2Ke1mHuN2Bru7GbTaSxLELx67lUI+CP9yC10Olx9TSEMaCHHqxC3omjibJYg2LwXAv4YeigPuhYHqa4pulKe9Fjt5nnCBh/2dI83FQoovEtZwPGW6lpiF0UK97fB1067DAFZSaBl7V9TVzuEFf1AbohqD/oCzaCNEJCVEpqBq9XUCnEHv6O0w7uenW/aFQjIxpPUfNX+nqpphN+do7ZCgmdn1CTnQ0AmQk9Tm6P6zTBVNEFyEf0uWo7HZ+30hBkCspAhsnzj3Jtxiv/+A3Oqv6PftujuRYah+wIIyBICz4qtIKosmpyk4KnHwDcPGMQe8M316vSRD5VDQNcMFb912VBddiBHmZw+Z7CL54s7ede0AdoFENAlgStcNsw3ysTl957kdeNGDiqFgC5zgXYHILFDiqW58ZkQ0BXj4BpxAJwozSB7QDEEdDFfmw/bCIyTqn2jJ7dAQFE6lkA3J+ZLtxhM068YAornYo0Q7s4BYEcpGzh6vAkCiqG/COVu9y9W4mGONhcCivGba5DuFoyxkrdw1ENlEFDkFzoOyZibHNBxaOKAuPeuQUAqQX0MEO8G22P5tHH4sDMQkI7uDNS7TlE0ryYOiJshUu45lQsoxGVDPofj2owojm0c2u8AteRitQsoRP4B/hmHhfJt5Ng/2Lndem73hKRsU3n4WxoXwL2Re68hln0pFAIKQtRENc+G9w+J8ElPM5E04ZsK+67/PrvOVquCpydGB/iokeOc95behwB4gzB1Knh6YmyI7xo5YuwdQfBiD5h3i4ITVTYWtK8Z6kv92vKucbftRXM6Gdrd1kdEpczeppY4aFgzvWtkgM/bOHzIz79y+9xYOOeUsYqMG/zOqpPNinav5uSHU3UxERq/tHBAZPIrq06ebK1CXBh8I7ZQWER0XPzgDKWii4+JDtf49VceER3t5yq0Aw1DlAquLQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAKDd8P8nS1dNJ3RF0QAAAABJRU5ErkJggg=="

def snapshotOption(cam, value) {
  if (!state.snappables) {
    state.snappables = [:]
  }
  state.snappables[cam] = value
}

@Field static def snapshotInvertals = [
  30: "30 Seconds",
  60: "1 Minute",
  90: "1.5 Minutes",
  120: "2 Minutes",
  180: "3 Minutes",
  240: "4 Minutes",
  300: "5 Minutes",
  360: "6 Minutes",
  600: "10 Minutes",
  720: "12 Minutes",
  900: "15 Minutes",
  1200: "20 Minutes",
  1800: "30 Minutes",
  3600: "1 Hour",
  7200: "2 Hours",
  10800: "3 Hours",
  14400: "4 Hours",
  21600: "6 Hours",
  28800: "8 Hours",
  43200: "12 Hours",
  86400: "24 Hours"
]

private getRequests(parts) {
  //logTrace "getRequest(parts)"
  //logTrace "parts: ${parts} ${parts.dni}"
  return [

    "auth": [
      method: POST,
      synchronous: true,
      params: [
        uri: "https://oauth.ring.com",
        path: "/oauth/token",
        //requestContentType: "application/json",
        contentType: "application/json",
        body: parts.grantData != null ? ([
          "client_id": "ring_official_android",
          "scope": "client"
        ] << parts.grantData) : null
      ],
      headers: [
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/75.0.3770.100 Safari/537.36",
        "hardware_id": state.appDeviceId
      ] << (parts.twoFactorCode != null ? ['2fa-support': 'true', '2fa-code': parts.twoFactorCode] : [:])
        << (parts.grantData?.grant_type == 'refresh_token' ? ['Authorization': "Bearer ${state.access_token}"] : [:])
    ],
    "session": [
      method: POST,
      synchronous: true,
      type: "bearer",
      params: [
        uri: "https://api.ring.com",
        path: "/clients_api/session",
        requestContentType: "application/json",
        contentType: "application/json",
        body: [
          device: [
            "hardware_id": state.appDeviceId,
            "metadata": [api_version: 9],
            "os": "android"
          ]
        ]
      ],
      headers: [
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/75.0.3770.100 Safari/537.36"
      ]
    ],
    "locations": [
      method: GET,
      synchronous: true,
      type: "bearer",
      params: [
        uri: "https://api.ring.com",
        path: "/devices/v1/locations",
        contentType: JSON/*"${JSON}, ${TEXT}, ${ALL}"*/
      ]
    ],
    "devices": [
      method: GET,
      synchronous: true,
      type: "bearer",
      params: [
        uri: "https://api.ring.com",
        path: "/clients_api/ring_devices" + (parts.dni ? "/${getRingDeviceId(parts.dni)}" : ""),
        query: ["api_version": 11],
        contentType: JSON
      ]
    ],
    "refresh": [
      method: GET,
      synchronous: false,
      type: "bearer",
      params: [
        uri: "https://api.ring.com",
        path: "/clients_api/ring_devices" + (parts.dni ? "/${getRingDeviceId(parts.dni)}" : ""),
        query: ["api_version": 11],
        contentType: JSON
      ]
    ],
    "dings": [
      method: GET,
      synchronous: false,
      type: "bearer",
      params: [
        uri: "https://api.ring.com",
        path: "/clients_api/dings/active",
        query: ["api_version": 11],
        contentType: JSON
      ]
    ],
    "device-control": [
      method: POST,
      type: "bearer",
      params: [
        uri: "https://api.ring.com",
        path: "/clients_api/${parts.kind}/${getRingDeviceId(parts.dni)}/${parts.action}",
        query: parts.params,
        contentType: TEXT,
        requestContentType: JSON,
        body: parts.body
      ]
    ],
    "device-set": [
      method: PUT,
      type: "bearer",
      params: [
        uri: "https://api.ring.com",
        path: "/clients_api/${parts.kind}/${getRingDeviceId(parts.dni)}" + "${parts.action ? "/${parts.action}" : ""}",
        query: parts.params,
        contentType: TEXT,
        requestContentType: JSON,
        body: parts.body
      ]
    ],
    "tickets": [
      method: GET,
      type: "bearer",
      params: [
        uri: "https://app.ring.com",
        path: "/api/v1/clap/tickets",
        contentType: JSON,
        requestContentType: TEXT//,
        //textParser: true
      ],
      query: ["locationID": "${selectedLocations}"]
    ],
    "mode-set": [
      method: POST,
      type: "bearer",
      params: [
        uri: "https://prd-ring-web-us.prd.rings.solutions",
        path: "/api/v1/mode/location/${getSelectedLocation()?.id}",
        query: ["api_version": "11"],
        contentType: JSON,
        requestContentType: JSON,
        body: [
          "mode": "${parts.mode}", "readOnly": true
        ]
      ],
      headers: [
        "User-Agent": "android:com.ringapp:3.25.0(26452333)",
        "Hardware_ID": state.appDeviceId
      ]
    ],
    "mode-get": [
      method: GET,
      type: "bearer",
      params: [
        uri: "https://prd-ring-web-us.prd.rings.solutions",
        path: "/api/v1/mode/location/${getSelectedLocation()?.id}",
        query: ["api_version": "11"],
        contentType: JSON,
        requestContentType: JSON,
      ],
      headers: [
        "User-Agent": "android:com.ringapp:3.25.0(26452333)",
        "Hardware_ID": state.appDeviceId
      ]
    ],
    "mode-settings": [
      method: GET,
      type: "bearer",
      params: [
        uri: "https://prd-ring-web-us.prd.rings.solutions",
        path: "/api/v1/mode/location/${getSelectedLocation()?.id}/settings",
        query: ["api_version": "11"],
        contentType: JSON,
        requestContentType: JSON,
        body: [
          "mode": "${parts.mode}", "readOnly": true
        ]
      ],
      headers: [
        "User-Agent": "android:com.ringapp:3.25.0(26452333)",
        "Hardware_ID": state.appDeviceId
      ]
    ],
    /*
    "refresh-device": [
      method: GET,
      params: [
        uri: "https://api.ring.com",
        path: "/clients_api/ring_devices/${getRingDeviceId(parts.dni)}",
        contentType: JSON
      ]
    ],*/
    "refresh-security-device": [
      method: GET,
      params: [
        uri: "https://api.ring.com",
        path: "/clients_api/ring_devices/${parts.dni}",
        contentType: JSON
      ]
    ],
    "pref-security-device": [
      method: GET,
      params: [
        uri: "https://app.ring.com",
        path: "/api/v1/rs/preferences/devices/${parts.dni}?deviceIdType=zid&deviceType=${parts.type}&userId=${parts.user_id}&locationId=${selectedLocations}",
        contentType: JSON
      ]
    ],
    "ws-connect": [
      method: POST,
      type: "bearer",
      params: [
        uri: "https://app.ring.com",
        path: "/api/v1/rs/connections?accountId=${selectedLocations}"
      ],
      headers: [
        'Content-Type': "application/x-www-form-urlencoded"
      ]
    ],
    "history": [
      method: GET,
      type: "bearer",
      params: [
        uri: "https://api.ring.com",
        path: "/clients_api/doorbots/history",
        query: ["api_version": 11, "limit": 9, "doorbot_ids%5B%5D": "${getRingDeviceId(parts.dni)}"],
        contentType: JSON
      ],
      headers: [
        "hardware_id": state.appDeviceId
      ]
    ],
    "snapshot-timestamps": [
      method: POST,
      type: "bearer",
      params: [
        uri: "https://api.ring.com",
        path: "/clients_api/snapshots/timestamps",
        requestContentType: JSON,
        body: [
          "doorbot_ids": parts.snappables
        ]
      ],
      headers: [
        "Hardware_ID": state.appDeviceId,
        "Accept": "application.vnd.api.v11+json",
        "Accept-Encoding": "gzip"
      ]
    ],
    "snapshot-image": [
      method: GET,
      synchronous: false,
      type: "bearer",
      params: [
        uri: "https://api.ring.com",
        path: "/clients_api/snapshots/image/${getRingDeviceId(parts.dni)}",
        requestContentType: JSON
      ],
      headers: [
        "User-Agent": "ring_official_windows/2.4.0",
        "Hardware_ID": state.appDeviceId,
        "Accept": "application.vnd.api.v11+json"
      ]
    ],
    "snapshot-image-tmp": [
      method: GET,
      synchronous: true,
      type: "bearer",
      params: [
        uri: "https://api.ring.com",
        path: "/clients_api/snapshots/image/${getRingDeviceId(parts.dni)}",
        requestContentType: JSON
      ],
      headers: [
        "Hardware_ID": state.appDeviceId,
        "Accept": "application.vnd.api.v11+json",
        'Accept-Encoding': 'gzip, deflate',
      ]
    ],
    "snapshot-update": [
      method: PUT,
      synchronous: false,
      type: "bearer",
      params: [
        uri: "https://api.ring.com",
        path: "/clients_api/snapshots/update_all",
        requestContentType: JSON,
        body: [
          "refresh": true,
          "doorbot_ids": state.dingables.collect { it -> it.toInteger() }
        ]
      ],
      headers: [
        "User-Agent": "ring_official_windows/2.4.0",
        "Hardware_ID": state.appDeviceId,
        "Accept": "application.vnd.api.v11+json"
      ]
    ],
    "subscribe": [
      method: PUT,
      type: "bearer",
      params: [
        uri: "https://api.ring.com",
        path: "/clients_api/device",
        requestContentType: JSON,
        body: [
          device: ["push_notification_token": "${getFullApiServerUrl()}/notify?access_token=${state.accessToken}"]
        ]
      ],
      headers: [
        "User-Agent": "ring_official_windows/2.4.0",
        "Hardware_ID": state.appDeviceId,
        "Accept": "application.vnd.api.v11+json"
      ]
    ],
    "master-key": [
      method: GET,
      type: "bearer",
      params: [
        uri: "https://app.ring.com",
        path: "/api/v1/rs/masterkey?locationId=${selectedLocations}",
        contentType: JSON
      ]
    ],
    "snooze-get": [
      method: GET,
      type: "bearer",
      params: [
        uri: "https://app.ring.com",
        path: "notification_settings/v1/locations/${getSelectedLocation()?.id}/snooze/motion",
        contentType: JSON
      ]
    ]
    //https://cloud.hubitat.com/api/[HUBUID]/apps/[APPID]/devices/all?access_token=[maker access token]
  ]
}

@Field static def standardHeaders = [
  //'User-Agent': "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/75.0.3770.100 Safari/537.36"
  //,'User-Agent': "Dalvik/2.1.0 (Linux; U; Android 6.0.1; Nexus 7 Build/MOB30X)"
  //,'User-Agent': "Dalvik/2.1.0 (Linux; U; Android 7.1.2; Nexus 4 Build/NZH54D)"
  'User-Agent': 'android:com.ringapp:3.25.0(26452333)',
  'app_brand': 'ring',
  'Accept-Encoding': 'gzip, deflate',
  'Connection': 'Keep-Alive',
  //, 'Accept': '*/*'
]

def parse(String description) {
  logDebug "parse(String description)"
  logTrace "description: $description"
  log.error "Parse?"
}

def authenticate(twoFactorCode) {
  logDebug "authenticate($twoFactorCode)"
  if (!state.appDeviceId) {
    generateAppDeviceId()
  }

  def data = [grantData: getGrantData(twoFactorCode), twoFactorCode: twoFactorCode]
  logTrace "data: ${data}"
  def result = simpleRequest("auth", data)
  if (result == "challenge") {
    return result
  }
  if (result) {
    return state.access_token
  }
}

private getGrantData(twoFactorCode) {
  if (state.refresh_token && !twoFactorCode) {
    logDebug "refresh_token used"
    return [
      "grant_type": 'refresh_token',
      "refresh_token": state.refresh_token
    ]
  }

  if (!twofactor || (twofactor && !state.refresh_token)) {
    return [
      "grant_type": 'password',
      "password": "${password}",
      "username": "${username}"
    ]
  }

  log.error 'Refresh token is not valid.  Unable to authenticate with Ring servers.'
}

def simpleRequest(type, data = [:]) {
  logDebug "simpleRequest(${type})"
  logTrace "data: ${data}"

  def request = getRequests(data).getAt(type)
  logTrace "request: ${request}"

  def params = formatParams(request, type, data)

  //actions that aren't done being developed can abort here
  //if (type == "subscribe") return

  if (request.synchronous) {
    return doSynchronousAction(request.method, type, params)
  }
  else {
    doAction(request.method, type, params, data)
  }
}

def formatParams(request, type, data) {
  logDebug "formatParams(request, type, data)"
  def params = request.params
  def query = [:]
  def headers = [
    'Host': "${request.params.uri.replace('https://', '')}"
  ]
  headers << standardHeaders
  if (request.type == "bearer") {
    headers << ['Authorization': "Bearer ${state.access_token}"]
  }
  else if (request.type == "token") {
    query << [api_version: 9, auth_token: state.authentication_token]
  }
  if (request.headers) {
    //headers << request.headers
    request.headers.each { headers[it.key] = it.value }
  }
  params << [headers: headers]
  if (request.query) {
    query << request.query
  }
  if (query) {
    params << [query: query]
  }
  logTrace "params: ${JsonOutput.prettyPrint(JsonOutput.toJson(params))}"
  return params
}

def doAction(type, method, params, data) {
  logDebug "doAction($type, $method, params, data)"
  try {
    "async${type}"("responseHandler", params, [method: method, data: data])
  }
  catch (e) {
    log.error "HTTP Exception received on ${type}: ${e}"
  }
}

def doSynchronousAction(type, method, params) {
  logDebug "doSynchronousAction($type, $method, params)"
  def retval
  try {
    "${type}"(params) { response ->
      retval = responseHandler(response, [method: method])
    }
  }
  catch (ex) {
    logTrace "ex: ${ex} ${ex != null ? ex.getStatusCode() : ''}"
    if (ex.getStatusCode() == 429) {
      state.access_token = "EMPTY"
      state.authentication_token = "EMPTY"
      state.refresh_token = null
      state.holdRequests = true
    }
    if (ex instanceof groovyx.net.http.HttpResponseException && ex.getStatusCode() == 401 && !(method in ["auth", "session"])) {
      logInfo "Not authenticated!"
      state.access_token = "EMPTY"
      state.authentication_token = "EMPTY"
    }
    if (ex instanceof groovyx.net.http.HttpResponseException && ex.getStatusCode() == 412 && (method in ["auth", "session"])) {
      logInfo "2 Step Challenge"
      state.access_token = "EMPTY"
      state.authentication_token = "EMPTY"
      state.refresh_token = null
      state.holdRequests = true
      return "challenge"
    }
    else if (method == "auth") {
      log.warn "Username and password incorrect!"
      state.access_token = "EMPTY"
      state.authentication_token = "EMPTY"
      state.refresh_token = null
    }
    else if (method == "session") {
      log.warn "What goes on here?"
    }
    else {
      log.warn "HTTP Exception received on ${type}"
      log.warn "method: $method with params $params"
      log.warn "exception: ${ex} cause: ${ex.getCause()}"
    }
  }
  return retval
}

def responseHandler(response, params) {
  logDebug "responseHandler(${response.status}, ${params})"
  if (response.status == 401) {
    logInfo "Not authenticated!"
    state.access_token = "EMPTY"
    state.authentication_token = "EMPTY"
    //TMP
    if (!state.holdRequests && authenticate()) {
      simpleRequest(params.method, params.data)
    }
    else {
      log.error "Unauthenticated request ${params.method} with ${params.data} failed!"
    }
  }
  else {
    //this would be a switch but they just don't format well in the editor
    if (params.method == "auth") {
      state.access_token = response.data.access_token
      state.refresh_token = response.data.refresh_token
      logInfo "Authenticated and token found."
      logDebug "access token: ${state.access_token}"
      logDebug "refresh token: ${state.refresh_token}"
      def result = state.access_token && state.access_token != "EMPTY" && state.refresh_token
      if (result) {
        state.holdRequests = false
        if (response.data.expires_in && response.data.expires_in.toString().isInteger()) {
          int interval = response.data.expires_in.toInteger()
          logInfo "OAuth token expires in ${interval} seconds... Scheduling refresh in ${interval - 20} seconds."
          runIn(interval - 20, authenticate)
        }
      }
      return result
    }
    else if (params.method == "locations") {
      return response.data.user_locations
    }
    else if (params.method == "devices") {
      def body = response.data
      body.doorbots.each { body.stickup_cams << it }
      body.authorized_doorbots.each { body.stickup_cams << it }
      body.chimes.each { body.stickup_cams << it }
      body.base_stations.each { body.stickup_cams << it }
      body.beams_bridges.each { body.stickup_cams << it }
      return body.stickup_cams
    }
    else if (params.method == "refresh") {
      def body = response.getJson()
      if (body.id) {
        body = [body]
      }
      logTrace "body: ${JsonOutput.prettyPrint(JsonOutput.toJson(body))}"
      body.each { deviceInfo ->
        logTrace "deviceInfo: ${deviceInfo}"
        logDebug "refreshing device ${getFormattedDNI(deviceInfo.id)}"
        getChildDevice(getFormattedDNI(deviceInfo.id))?.childParse(params.method, [response: response.getStatus(), msg: deviceInfo])
      }
    }
    else if (["device-control", "device-set", "tickets", "mode-set", "mode-get"].contains(params.method)) {
      def body = response.data ? response.getJson() : null
      logTrace "body: $body"
      getChildDevice(params.data.dni).childParse(params.method, [
        response: response.getStatus(),
        action: params.data.action,
        kind: params.data.params?.kind,
        volume: params.data.params?."chime[settings][volume]",
        msg: body
      ])
    }
    else if (params.method == "snapshot-update" || params.method == "history" || params.method == "snapshot-timestamps") {
      logDebug "${params.method} successful"
      logTrace "body: ${response.data ? response.getJson() : null}"
    }
    else if (params.method == "snapshot-image" || params.method == "snapshot-image-tmp") {
      byte[] array = new byte[response.data.available()];
      response.data.read(array);
      return array

      //getChildDevice(params.data.dni).childParse(params.method, [
      //  response: response.getStatus(),
      //  action: params.data.action,
      //  kind: params.data.params?.kind,
      //jpg: "data:image/png;base64,${response.data.encodeBase64().toString()}"
      //  jpg: "<img src=\"data:image/png;base64,${response.data.encodeBase64().toString()}\" alt=\"Snapshot\" />"
      //])
    }
    //else if (params.method == "tickets") {
    //  getChildDevice(data.dni).childParse(type, [response: resp.getStatus(), msg: body])
    //}
    else if (params.method == "subscribe") {
      if (response.status != 204) {
        log.error "Notification subscription failed!"
      }
      else {
        log.info "Subscribed to push notifications (except it doesn't work for now because of whitelist)"
      }
    }
    else if (params.method == "dings") {
      def body = response.getJson()
      logTrace "body: ${JsonOutput.prettyPrint(JsonOutput.toJson(body))}"
      state.dingables.each { deviceId ->
        def dingInfo = body.find { it.doorbot_id.toString() == deviceId.toString() }
        if (dingInfo) {
          logTrace "Device ${getFormattedDNI(deviceId)} has dingInfo ${dingInfo}"
        }
        getChildDevice(getFormattedDNI(deviceId))?.childParse(params.method, [response: response.getStatus(), msg: dingInfo])
      }
    }
    else {
      log.error "Unhandled method!"
      if (response.data) {
        log.error "Data: ${response.data}"
      }
      throw new java.lang.UnsupportedOperationException("${params.method} is not implemented!")
    }
  }
}

def getAPIDevice(location) {
  if (!location) {
    location = getSelectedLocation()
  }
  def formattedDNI = RING_API_DNI + "||" + location.id
  def d = getChildDevice(formattedDNI)
  if (!d) {
    def oldDNI = getChildDevice("RING-WS_API_DNI")
    //migrate if it's the old DNI
    if (oldDNI) {
      oldDNI.deviceNetworkId = formattedDNI
      oldDNI.updateDataValue("device_id", formattedDNI)
      oldDNI.updateDataValue("kind", RING_API_DNI)
      oldDNI.updateDataValue("kind_name", DEVICE_TYPES[RING_API_DNI].name)
      d = oldDNI
      log.warn "Migrated existing API device ${oldDNI.label} to new DNI ${formattedDNI}..."
    }
    //create otherwise
    else {
      def driver = DEVICE_TYPES[RING_API_DNI].driver
      def data = [
        "device_id": formattedDNI,
        "kind": RING_API_DNI,
        "kind_name": DEVICE_TYPES[RING_API_DNI].name
      ]
      d = createDevice(driver, formattedDNI, location.name + " Location", data)
      d.initialize()
      d.refresh()
      logInfo "${DEVICE_TYPES[RING_API_DNI].name} with ID ${formattedDNI} created..."
    }
  }
  return d
}

def createDevice(driver, id, label, data) {
  return addChildDevice("ring-hubitat-codahq", driver, id, null, ["label": label, "data": data])
}

def loggedIn() {
  logDebug "loggedIn()"
  logTrace "state.access_token ${state.access_token}"
  return state.access_token && state.access_token != "EMPTY"
}

def getSelectedLocation() {
  def loc = state.locationOptions.find { location ->
    selectedLocations.contains(location.key) || selectedLocations.equals(location.key)
  }
  return loc ? [id: loc.key, name: loc.value] : null
}

//logging help methods
private logInfo(msg) {
  if (descriptionTextEnable) log.info msg
}

def logDebug(msg) {
  if (logEnable) log.debug msg
}

def logTrace(msg) {
  if (traceLogEnable) log.trace msg
}

def String getFormattedDNI(id) {
  return "RING-${id}"
}

def String getRingDeviceId(dni) {
  //logDebug "getRingDeviceId(dni)"
  //logTrace "dni: ${dni}"
  return dni?.split("-")?.getAt(1)
}

def generateAppDeviceId() {
  logDebug "generateAppDeviceId()"
  //Let's generate an ID so that Ring doesn't think these are all coming from the same device
  //def r = new Random()
  //def result = (0..<32).collect { r.nextInt(16) }.collect { Integer.toString(it, 16).toUpperCase() }.join()

  def result = UUID.randomUUID().toString()
  logInfo "Device ID generated: ${result}"
  state.appDeviceId = result
}

def isHub(kind) {
  return HUB_TYPES.contains(kind)
}

def isOAuthEnabled() {
  def oauthEnabled = true
  try {
    if (!state.accessToken) {
      createAccessToken()
    }
  }
  catch (ex) {
    oauthEnabled = false
  }
  return oauthEnabled
}

//Constants
@Field static def RING_API_DNI = "WS_API_DNI"
@Field static def GET = "httpGet"
@Field static def POST = 'httpPost'
@Field static def PUT = 'httpPut'
@Field static def JSON = 'application/json'
@Field static def TEXT = 'text/plain'
@Field static def FORM = 'application/x-www-form-urlencoded'
@Field static def ALL = '*/*'

@Field static def RINGABLES = [
  "doorbell",
  "doorbell_v3",
  "doorbell_v4",
  "doorbell_v5",
  "doorbell_portal",
  "doorbell_scallop",
  "doorbell_scallop_lite",
  "cocoa_doorbell",
  "cocoa_floodlight",
  "lpd_v1",
  "lpd_v2",
  "lpd_v4",
  "jbox_v1"
]

@Field static def DEVICE_TYPES = [
  "WS_API_DNI": [name: "Ring API Virtual Device", driver: "Ring API Virtual Device", dingable: false],
  "base_station_v1": [name: "Ring Alarm Base Station", driver: "SHOULD NOT SEE THIS", dingable: false],
  "beams_bridge_v1": [name: "Ring Bridge Hub", driver: "SHOULD NOT SEE THIS", dingable: false],
  "chime_pro_v2": [name: "Ring Chime Pro (v2)", driver: "Ring Virtual Chime", dingable: false],
  "chime_pro": [name: "Ring Chime Pro", driver: "Ring Virtual Chime", dingable: false],
  "chime": [name: "Ring Chime", driver: "Ring Virtual Chime", dingable: false],
  "cocoa_camera": [name: "Ring Stick Up Cam", driver: "Ring Virtual Camera with Siren", dingable: true],
  "cocoa_doorbell": [name: "Ring Video Doorbell 2020", driver: "Ring Virtual Camera", dingable: true],
  "cocoa_floodlight": [name: "Ring Floodlight Cam Wired Plus", driver: "Ring Virtual Light with Siren", dingable: true], 
  "doorbell_portal": [name: "Ring Peephole Cam", driver: "Ring Virtual Camera", dingable: true],
  "doorbell_scallop_lite": [name: "Ring Video Doorbell 3", driver: "Ring Virtual Camera", dingable: true],
  "doorbell_scallop": [name: "Ring Video Doorbell 3 Plus", driver: "Ring Virtual Camera", dingable: true],
  "doorbell_v3": [name: "Ring Video Doorbell", driver: "Ring Virtual Camera", dingable: true],
  "doorbell_v4": [name: "Ring Video Doorbell 2", driver: "Ring Virtual Camera", dingable: true],
  "doorbell_v5": [name: "Ring Video Doorbell 2", driver: "Ring Virtual Camera", dingable: true],
  "doorbell": [name: "Ring Video Doorbell", driver: "Ring Virtual Camera", dingable: true],
  "floodlight_pro": [name: "Ring Floodlight Cam Wired Pro", driver: "Ring Virtual Light with Siren", dingable: true],
  "floodlight_v2": [name: "Ring Floodlight Cam Wired", driver: "Ring Virtual Light with Siren", dingable: true],
  "hp_cam_v1": [name: "Ring Floodlight Cam", driver: "Ring Virtual Light with Siren", dingable: true],
  "hp_cam_v2": [name: "Ring Spotlight Cam Wired", driver: "Ring Virtual Light with Siren", dingable: true],
  "jbox_v1": [name: "Ring Video Doorbell Elite", driver: "Ring Virtual Camera", dingable: true],
  "lpd_v1": [name: "Ring Video Doorbell Pro", driver: "Ring Virtual Camera", dingable: true],
  "lpd_v2": [name: "Ring Video Doorbell Pro", driver: "Ring Virtual Camera", dingable: true],
  "lpd_v4": [name: "Ring Video Doorbell Pro 2", driver: "Ring Virtual Camera", dingable: true],
  "spotlightw_v2": [name: "Ring Spotlight Cam Wired", driver: "Ring Virtual Light with Siren", dingable: true],
  "stickup_cam_elite": [name: "Ring Stick Up Cam Wired", driver: "Ring Virtual Camera with Siren", dingable: true],
  "stickup_cam_lunar": [name: "Ring Stick Up Cam Battery", driver: "Ring Virtual Camera with Siren", dingable: true],
  "stickup_cam_mini": [name: "Ring Indoor Cam", driver: "Ring Virtual Camera with Siren", dingable: true],
  "stickup_cam_v3": [name: "Ring Stick Up Cam", driver: "Ring Virtual Camera", dingable: true],
  "stickup_cam_v4": [name: "Ring Spotlight Cam Battery", driver: "Ring Virtual Light", dingable: true],
  "stickup_cam": [name: "Ring Original Stick Up Cam", driver: "Ring Virtual Camera", dingable: true]
]

@Field static def HUB_TYPES = [
  "base_station_v1",
  "beams_bridge_v1"
]

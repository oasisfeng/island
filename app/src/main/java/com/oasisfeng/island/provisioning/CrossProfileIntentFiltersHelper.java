/*
 * Copyright 2014, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.oasisfeng.island.provisioning;

import android.content.Intent;
import android.content.IntentFilter;
import android.provider.AlarmClock;
import android.provider.MediaStore;

import com.oasisfeng.island.engine.IslandManager;

import static android.app.admin.DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED;
import static android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH;
/**
 * Class to set CrossProfileIntentFilters during managed profile creation, and reset them after an
 * ota.
 */
public class CrossProfileIntentFiltersHelper {

	private static final String ACTION_CALL_EMERGENCY = "android.intent.action.CALL_EMERGENCY";
	private static final String ACTION_CALL_PRIVILEGED = "android.intent.action.CALL_PRIVILEGED";
	private static class ProvisionLogger { static void logd(final String message) {} }
	private interface PackageManager {
		int SKIP_CURRENT_PROFILE = 0;
		void addCrossProfileIntentFilter(IntentFilter filter, int user, int parent_user, int flags);
	}

	public static void setFilters(final IslandManager island) {
		setFilters((filter, u, p, f) -> island.enableForwarding(filter, FLAG_PARENT_CAN_ACCESS_MANAGED), 0, 0);
	}

	public static void setFilters(PackageManager pm, int parentUserId, int managedProfileUserId) {
		ProvisionLogger.logd("Setting cross-profile intent filters");

		// Voicemail scheme, phone/call related MIME types and emergency/priviledged calls are sent
		// directly to the parent user.
		IntentFilter mimeTypeTelephony = new IntentFilter();
		mimeTypeTelephony.addAction(Intent.ACTION_DIAL);
		mimeTypeTelephony.addAction(Intent.ACTION_VIEW);
		mimeTypeTelephony.addAction(ACTION_CALL_EMERGENCY);
		mimeTypeTelephony.addAction(ACTION_CALL_PRIVILEGED);
		mimeTypeTelephony.addCategory(Intent.CATEGORY_DEFAULT);
		mimeTypeTelephony.addCategory(Intent.CATEGORY_BROWSABLE);
		try {
			mimeTypeTelephony.addDataType("vnd.android.cursor.item/phone");
			mimeTypeTelephony.addDataType("vnd.android.cursor.item/phone_v2");
			mimeTypeTelephony.addDataType("vnd.android.cursor.item/person");
			mimeTypeTelephony.addDataType("vnd.android.cursor.dir/calls");
			mimeTypeTelephony.addDataType("vnd.android.cursor.item/calls");
		} catch (IntentFilter.MalformedMimeTypeException e) {
			//will not happen
		}
		pm.addCrossProfileIntentFilter(mimeTypeTelephony, managedProfileUserId, parentUserId,
				PackageManager.SKIP_CURRENT_PROFILE);

		IntentFilter callEmergency = new IntentFilter();
		callEmergency.addAction(ACTION_CALL_EMERGENCY);
		callEmergency.addAction(ACTION_CALL_PRIVILEGED);
		callEmergency.addCategory(Intent.CATEGORY_DEFAULT);
		callEmergency.addCategory(Intent.CATEGORY_BROWSABLE);
		callEmergency.addDataScheme("tel");
		callEmergency.addDataScheme("sip");
		callEmergency.addDataScheme("voicemail");
		pm.addCrossProfileIntentFilter(callEmergency, managedProfileUserId, parentUserId,
				PackageManager.SKIP_CURRENT_PROFILE);

		IntentFilter callVoicemail = new IntentFilter();
		callVoicemail.addAction(Intent.ACTION_DIAL);
		callVoicemail.addAction(Intent.ACTION_CALL);
		callVoicemail.addAction(Intent.ACTION_VIEW);
		callVoicemail.addCategory(Intent.CATEGORY_DEFAULT);
		callVoicemail.addCategory(Intent.CATEGORY_BROWSABLE);
		callVoicemail.addDataScheme("voicemail");
		pm.addCrossProfileIntentFilter(callVoicemail, managedProfileUserId, parentUserId,
				PackageManager.SKIP_CURRENT_PROFILE);

		// Let VoIP apps from the managed profile handle tel: and sip: schemes (except emergency)
		// and call button intents.
		IntentFilter callDial = new IntentFilter();
		callDial.addAction(Intent.ACTION_DIAL);
		callDial.addAction(Intent.ACTION_CALL);
		callDial.addAction(Intent.ACTION_VIEW);
		callDial.addCategory(Intent.CATEGORY_DEFAULT);
		callDial.addCategory(Intent.CATEGORY_BROWSABLE);
		callDial.addDataScheme("tel");
		callDial.addDataScheme("sip");
		pm.addCrossProfileIntentFilter(callDial, managedProfileUserId, parentUserId, 0);

		IntentFilter callButton = new IntentFilter();
		callButton.addAction(Intent.ACTION_CALL_BUTTON);
		callButton.addCategory(Intent.CATEGORY_DEFAULT);
		pm.addCrossProfileIntentFilter(callButton, managedProfileUserId, parentUserId, 0);

		IntentFilter callDialNoData = new IntentFilter();
		callDialNoData.addAction(Intent.ACTION_DIAL);
		callDialNoData.addAction(Intent.ACTION_CALL);
		callDialNoData.addCategory(Intent.CATEGORY_DEFAULT);
		callDialNoData.addCategory(Intent.CATEGORY_BROWSABLE);
		pm.addCrossProfileIntentFilter(callDialNoData, managedProfileUserId, parentUserId,
				PackageManager.SKIP_CURRENT_PROFILE);

		IntentFilter smsMms = new IntentFilter();
		smsMms.addAction(Intent.ACTION_VIEW);
		smsMms.addAction(Intent.ACTION_SENDTO);
		smsMms.addCategory(Intent.CATEGORY_DEFAULT);
		smsMms.addCategory(Intent.CATEGORY_BROWSABLE);
		smsMms.addDataScheme("sms");
		smsMms.addDataScheme("smsto");
		smsMms.addDataScheme("mms");
		smsMms.addDataScheme("mmsto");
		pm.addCrossProfileIntentFilter(smsMms, managedProfileUserId, parentUserId,
				PackageManager.SKIP_CURRENT_PROFILE);

		IntentFilter mobileNetworkSettings = new IntentFilter();
		mobileNetworkSettings.addAction(android.provider.Settings.ACTION_DATA_ROAMING_SETTINGS);
		mobileNetworkSettings.addAction(android.provider.Settings.ACTION_NETWORK_OPERATOR_SETTINGS);
		mobileNetworkSettings.addCategory(Intent.CATEGORY_DEFAULT);
		pm.addCrossProfileIntentFilter(mobileNetworkSettings, managedProfileUserId,
				parentUserId, PackageManager.SKIP_CURRENT_PROFILE);

		IntentFilter home = new IntentFilter();
		home.addAction(Intent.ACTION_MAIN);
		home.addCategory(Intent.CATEGORY_DEFAULT);
		home.addCategory(Intent.CATEGORY_HOME);
		pm.addCrossProfileIntentFilter(home, managedProfileUserId, parentUserId,
				PackageManager.SKIP_CURRENT_PROFILE);

		IntentFilter send = new IntentFilter();
		send.addAction(Intent.ACTION_SEND);
		send.addAction(Intent.ACTION_SEND_MULTIPLE);
		send.addCategory(Intent.CATEGORY_DEFAULT);
		try {
			send.addDataType("*/*");
		} catch (IntentFilter.MalformedMimeTypeException e) {
			//will not happen
		}
		// This is the only filter set on the opposite direction (from parent to managed profile).
		pm.addCrossProfileIntentFilter(send, parentUserId, managedProfileUserId, 0);

		IntentFilter getContent = new IntentFilter();
		getContent.addAction(Intent.ACTION_GET_CONTENT);
		getContent.addCategory(Intent.CATEGORY_DEFAULT);
		getContent.addCategory(Intent.CATEGORY_OPENABLE);
		try {
			getContent.addDataType("*/*");
		} catch (IntentFilter.MalformedMimeTypeException e) {
			//will not happen
		}
		pm.addCrossProfileIntentFilter(getContent, managedProfileUserId, parentUserId, 0);

		IntentFilter openDocument = new IntentFilter();
		openDocument.addAction(Intent.ACTION_OPEN_DOCUMENT);
		openDocument.addCategory(Intent.CATEGORY_DEFAULT);
		openDocument.addCategory(Intent.CATEGORY_OPENABLE);
		try {
			openDocument.addDataType("*/*");
		} catch (IntentFilter.MalformedMimeTypeException e) {
			//will not happen
		}
		pm.addCrossProfileIntentFilter(openDocument, managedProfileUserId, parentUserId, 0);

		IntentFilter pick = new IntentFilter();
		pick.addAction(Intent.ACTION_PICK);
		pick.addCategory(Intent.CATEGORY_DEFAULT);
		try {
			pick.addDataType("*/*");
		} catch (IntentFilter.MalformedMimeTypeException e) {
			//will not happen
		}
		pm.addCrossProfileIntentFilter(pick, managedProfileUserId, parentUserId, 0);

		IntentFilter pickNoData = new IntentFilter();
		pickNoData.addAction(Intent.ACTION_PICK);
		pickNoData.addCategory(Intent.CATEGORY_DEFAULT);
		pm.addCrossProfileIntentFilter(pickNoData, managedProfileUserId,
				parentUserId, 0);

		IntentFilter recognizeSpeech = new IntentFilter();
		recognizeSpeech.addAction(ACTION_RECOGNIZE_SPEECH);
		recognizeSpeech.addCategory(Intent.CATEGORY_DEFAULT);
		pm.addCrossProfileIntentFilter(recognizeSpeech, managedProfileUserId, parentUserId, 0);

		IntentFilter capture = new IntentFilter();
		capture.addAction(MediaStore.ACTION_IMAGE_CAPTURE);
		capture.addAction(MediaStore.ACTION_IMAGE_CAPTURE_SECURE);
		capture.addAction(MediaStore.ACTION_VIDEO_CAPTURE);
		capture.addAction(MediaStore.Audio.Media.RECORD_SOUND_ACTION);
		capture.addAction(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
		capture.addAction(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE);
		capture.addAction(MediaStore.INTENT_ACTION_VIDEO_CAMERA);
		capture.addCategory(Intent.CATEGORY_DEFAULT);
		pm.addCrossProfileIntentFilter(capture, managedProfileUserId, parentUserId, 0);

		IntentFilter setClock = new IntentFilter();
		setClock.addAction(AlarmClock.ACTION_SET_ALARM);
		setClock.addAction(AlarmClock.ACTION_SHOW_ALARMS);
		setClock.addAction(AlarmClock.ACTION_SET_TIMER);
		setClock.addCategory(Intent.CATEGORY_DEFAULT);
		pm.addCrossProfileIntentFilter(setClock, managedProfileUserId, parentUserId, 0);
	}
}

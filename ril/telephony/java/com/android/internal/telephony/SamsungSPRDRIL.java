/*
 * Copyright (C) 2012 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.telephony;

import static com.android.internal.telephony.RILConstants.*;

import android.content.Context;
import android.os.AsyncResult;
import android.os.Message;
import android.os.Parcel;
import android.os.SystemProperties;
import android.telephony.Rlog;
import android.telephony.PhoneNumberUtils;
import android.telephony.ModemActivityInfo;

import com.android.internal.telephony.uicc.SpnOverride;
import com.android.internal.telephony.RILConstants;

import java.io.IOException;

import java.lang.Runtime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

/**
 * Custom RIL to handle unique behavior of SPRD RIL
 *
 * {@hide}
 */
public class SamsungSPRDRIL extends RIL implements CommandsInterface {

    public static final int RIL_UNSOL_AM = 11010;

    protected static final byte[] RAW_HOOK_OEM_CMD_SWITCH_DATAPREFER;

    static {
        RAW_HOOK_OEM_CMD_SWITCH_DATAPREFER = new byte[] { 0x09, 0x04 };
    }

    public SamsungSPRDRIL(Context context, int preferredNetworkType, int cdmaSubscription) {
        this(context, preferredNetworkType, cdmaSubscription, null);
    }

    public SamsungSPRDRIL(Context context, int preferredNetworkType,
            int cdmaSubscription, Integer instanceId) {
        super(context, preferredNetworkType, cdmaSubscription, instanceId);
    }

    @Override
    public void
    dial(String address, int clirMode, UUSInfo uusInfo, Message result) {
        RILRequest rr = RILRequest.obtain(RIL_REQUEST_DIAL, result);

        rr.mParcel.writeString(address);
        rr.mParcel.writeInt(clirMode);
        rr.mParcel.writeInt(0);     // CallDetails.call_type
        rr.mParcel.writeInt(1);     // CallDetails.call_domain
        rr.mParcel.writeString(""); // CallDetails.getCsvFromExtras

        if (uusInfo == null) {
            rr.mParcel.writeInt(0); // UUS information is absent
        } else {
            rr.mParcel.writeInt(1); // UUS information is present
            rr.mParcel.writeInt(uusInfo.getType());
            rr.mParcel.writeInt(uusInfo.getDcs());
            rr.mParcel.writeByteArray(uusInfo.getUserData());
        }

        if (RILJ_LOGD) riljLog(rr.serialString() + "> " + requestToString(rr.mRequest));

        send(rr);
    }

    @Override
    public void setDataAllowed(boolean allowed, Message result) {
        int simId = mInstanceId == null ? 0 : mInstanceId;
        if (allowed) {
            riljLog("setDataAllowed: set data subscription to sim [" + simId + "]");
            invokeOemRilRequestRaw(RAW_HOOK_OEM_CMD_SWITCH_DATAPREFER, result);
        } else {
            riljLog("setDataAllowed: do nothing when turn-off data on sim [" + simId + "]");
            if (result != null) {
                AsyncResult.forMessage(result, 0, null);
                result.sendToTarget();
            }
        }
    }

    @Override
    public void getRadioCapability(Message response) {
        String rafString = mContext.getResources().getString(
            com.android.internal.R.string.config_radio_access_family);
        riljLog("getRadioCapability: returning static radio capability [" + rafString + "]");
        if (response != null) {
            Object ret = makeStaticRadioCapability();
            AsyncResult.forMessage(response, ret, null);
            response.sendToTarget();
        }
    }

    @Override
    protected Object responseFailCause(Parcel p) {
        int numInts = p.readInt();
        int response[] = new int[numInts];
        for (int i = 0 ; i < numInts ; i++)
            response[i] = p.readInt();
        LastCallFailCause failCause = new LastCallFailCause();
        failCause.causeCode = response[0];
        if (p.dataAvail() > 0)
            failCause.vendorCause = p.readString();
        return failCause;
    }

    @Override
    protected void processUnsolicited(Parcel p) {
        int originalDataPosition = p.dataPosition();
        int response = p.readInt();
        Object ret;
        try {
            switch (response) {
            case RIL_UNSOL_AM:
                ret = responseString(p);
                break;
            default:
                p.setDataPosition(originalDataPosition);
                super.processUnsolicited(p);
                return;
            }
        } catch (Throwable tr) {
            Rlog.e(RILJ_LOG_TAG, "Exception processing unsol response: " + response +
                    "Exception:" + tr.toString());
            return;
        }
        switch (response) {
        case RIL_UNSOL_AM: {
            String amString = (String) ret;
            Rlog.d(RILJ_LOG_TAG, "Executing AM: " + amString);
            try {
                Runtime.getRuntime().exec("am " + amString);
            } catch (IOException e) {
                e.printStackTrace();
                Rlog.e(RILJ_LOG_TAG, "am " + amString + " could not be executed.");
            }
            break;
        }
        }
    }
}

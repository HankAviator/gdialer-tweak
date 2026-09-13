package io.github.hankaviator.gdialertweak.testcall;

import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;

public final class TestCallManager {
    private static final String ID = "gdialer_tweak_test";

    private TestCallManager() {}

    public static PhoneAccountHandle handle(Context context) {
        return new PhoneAccountHandle(new ComponentName(context, TestConnectionService.class), ID);
    }

    public static void register(Context context) {
        TelecomManager telecom = context.getSystemService(TelecomManager.class);
        PhoneAccount account = PhoneAccount.builder(handle(context), "GDialer Tweak test calls")
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .setSupportedUriSchemes(java.util.Collections.singletonList(PhoneAccount.SCHEME_TEL))
                .build();
        telecom.registerPhoneAccount(account);
    }

    public static void startIncoming(Context context) {
        register(context);
        Bundle extras = new Bundle();
        extras.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, Uri.parse("tel:5550100"));
        context.getSystemService(TelecomManager.class).addNewIncomingCall(handle(context), extras);
    }
}

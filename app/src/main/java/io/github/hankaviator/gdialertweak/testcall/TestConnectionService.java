package io.github.hankaviator.gdialertweak.testcall;

import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;

public final class TestConnectionService extends ConnectionService {
    @Override public Connection onCreateIncomingConnection(PhoneAccountHandle manager, ConnectionRequest request) {
        TestConnection connection = new TestConnection();
        connection.setAddress(request.getAddress(), android.telecom.TelecomManager.PRESENTATION_ALLOWED);
        connection.setCallerDisplayName("GDialer Tweak test", android.telecom.TelecomManager.PRESENTATION_ALLOWED);
        connection.setAudioModeIsVoip(false);
        connection.setRinging();
        return connection;
    }

    @Override public void onCreateIncomingConnectionFailed(PhoneAccountHandle manager, ConnectionRequest request) {
        super.onCreateIncomingConnectionFailed(manager, request);
    }

    private static final class TestConnection extends Connection {
        @Override public void onAnswer() { setActive(); }
        @Override public void onAnswer(int videoState) { setActive(); }
        @Override public void onReject() { disconnect(); }
        @Override public void onDisconnect() { disconnect(); }
        @Override public void onAbort() { disconnect(); }

        private void disconnect() {
            setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
            destroy();
        }
    }
}

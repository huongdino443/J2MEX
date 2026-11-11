import javax.microedition.io.Connection;
import javax.microedition.io.ConnectionNotFoundException;
import javax.microedition.io.Connector;
import javax.wireless.messaging.BinaryMessage;
import javax.wireless.messaging.Message;
import javax.wireless.messaging.MessageConnection;
import javax.wireless.messaging.TextMessage;

public final class SmsConnectionSmoke {
    public static void main(String[] args) throws Exception {
        MessageConnection textConnection =
                (MessageConnection) Connector.open("SMS://8785");
        Message text = textConnection.newMessage(MessageConnection.TEXT_MESSAGE);
        if (!"8785".equals(text.getAddress())) throw new AssertionError("SMS address mismatch");
        ((TextMessage) text).setPayloadText("9092");
        textConnection.send(text);
        if (textConnection.numberOfSegments(text) != 1)
            throw new AssertionError("SMS segment count mismatch");
        textConnection.close();

        MessageConnection binaryConnection =
                (MessageConnection) Connector.open("sms://+12345:42");
        BinaryMessage binary = (BinaryMessage) binaryConnection.newMessage(
                MessageConnection.BINARY_MESSAGE);
        byte[] payload = new byte[]{1, 2, 3};
        binary.setPayloadData(payload);
        if (binary.getPayloadData() != payload) throw new AssertionError("Binary payload mismatch");
        binaryConnection.close();

        MessageConnection placeholderConnection =
                (MessageConnection) Connector.open("sms://ab");
        TextMessage placeholder = (TextMessage) placeholderConnection.newMessage(
                MessageConnection.TEXT_MESSAGE);
        placeholder.setPayloadText("dummy");
        placeholderConnection.send(placeholder);
        placeholderConnection.close();

        MessageConnection receiveConnection =
                (MessageConnection) Connector.open("sms://1234");
        TextMessage received = (TextMessage) receiveConnection.receive();
        if (!"sms".equals(received.getPayloadText()))
            throw new AssertionError("Unexpected simulated incoming message");
        receiveConnection.close();

        try {
            Connection invalid = Connector.open("sms:8785");
            invalid.close();
            throw new AssertionError("Malformed SMS URL was accepted");
        } catch (ConnectionNotFoundException expected) {
            // Malformed scheme syntax must not reach any external SMS service.
        }
        System.out.println("SmsConnectionSmoke: text/binary WMA, placeholder addresses, "
                + "and offline send semantics passed");
    }
}

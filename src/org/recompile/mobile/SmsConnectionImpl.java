/*
 * This file is part of FreeJ2ME.
 *
 * FreeJ2ME is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package org.recompile.mobile;

import java.io.IOException;
import java.util.Date;

import javax.wireless.messaging.BinaryMessage;
import javax.wireless.messaging.Message;
import javax.wireless.messaging.MessageConnection;
import javax.wireless.messaging.MessageListener;
import javax.wireless.messaging.TextMessage;

/** Offline WMA/SMS shim; placeholder destinations are accepted but never sent. */
public final class SmsConnectionImpl implements MessageConnection {
    private final String name;
    private final String address;
    private MessageListener listener;
    private boolean noMessages;
    private boolean closed;

    public SmsConnectionImpl(String name) {
        if (name == null || !name.regionMatches(true, 0, "sms://", 0, 6))
            throw new IllegalArgumentException("Invalid SMS URL: " + name);
        this.name = name;
        this.address = name.substring(6);
    }

    @Override
    public Message newMessage(String type) {
        return newMessage(type, address);
    }

    @Override
    public Message newMessage(String type, String messageAddress) {
        if (TEXT_MESSAGE.equals(type)) return new SimulatedTextMessage(messageAddress, 0);
        if (BINARY_MESSAGE.equals(type)) return new SimulatedBinaryMessage(messageAddress, 0);
        throw new IllegalArgumentException("Message type is invalid: " + type);
    }

    @Override
    public int numberOfSegments(Message message) {
        return 1;
    }

    @Override
    public Message receive() {
        while (noMessages && !closed) {
            try {
                Thread.sleep(60000L);
            } catch (InterruptedException interrupted) {
                interrupted.printStackTrace();
            }
        }
        SimulatedTextMessage message =
                new SimulatedTextMessage(address, System.currentTimeMillis());
        message.setPayloadText("sms");
        noMessages = true;
        return message;
    }

    @Override
    public void send(Message message) {
        if (message == null) throw new NullPointerException();
        if (listener != null) listener.notifyIncomingMessage(new SmsConnectionImpl(name));
        System.out.println("[WMA] Simulated SMS send; no real message was sent.");
    }

    @Override
    public void setMessageListener(MessageListener listener) {
        this.listener = listener;
    }

    @Override
    public void close() throws IOException {
        closed = true;
    }

    private static class SimulatedMessage implements Message {
        private String address;
        private final long timestamp;

        SimulatedMessage(String address, long timestamp) {
            this.address = address;
            this.timestamp = timestamp;
        }

        @Override public String getAddress() { return address; }
        @Override public Date getTimestamp() {
            return timestamp == 0 ? null : new Date(timestamp);
        }
        @Override public void setAddress(String address) { this.address = address; }
    }

    private static final class SimulatedTextMessage extends SimulatedMessage
            implements TextMessage {
        private String payload;

        SimulatedTextMessage(String address, long timestamp) {
            super(address, timestamp);
        }

        @Override public String getPayloadText() { return payload; }
        @Override public void setPayloadText(String text) { payload = text; }
    }

    private static final class SimulatedBinaryMessage extends SimulatedMessage
            implements BinaryMessage {
        private byte[] payload;

        SimulatedBinaryMessage(String address, long timestamp) {
            super(address, timestamp);
        }

        @Override public byte[] getPayloadData() { return payload; }
        @Override public void setPayloadData(byte[] data) { payload = data; }
    }
}

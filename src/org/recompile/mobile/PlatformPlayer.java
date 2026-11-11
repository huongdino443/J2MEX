/*
	This file is part of FreeJ2ME.

	FreeJ2ME is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.

	FreeJ2ME is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU General Public License for more details.

	You should have received a copy of the GNU General Public License
	along with FreeJ2ME.  If not, see http://www.gnu.org/licenses/
*/
package org.recompile.mobile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.Vector;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequencer;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

import javax.microedition.media.Player;
import javax.microedition.media.PlayerListener;

import javax.microedition.media.Control;
import javax.microedition.media.Controllable;

public class PlatformPlayer implements Player
{

	private String contentType = "";

	private audioplayer player;

	private volatile int state = Player.UNREALIZED;
	private static volatile PlatformPlayer activeRemoteVolumePlayer;

private volatile long duration = Player.TIME_UNKNOWN;

	private Vector<PlayerListener> listeners;

	private Control[] controls;

public PlatformPlayer(InputStream stream, String type) throws IOException
	{
		listeners = new Vector<PlayerListener>();
		controls = new Control[4];

contentType = type == null ? "" : type;

if(Mobile.sound && AudioBridge.isAvailable())
{
player = new remotePlayer(stream);
}
else if(Mobile.sound == false)
		{
			player = new audioplayer();
		}
		else
		{
if(contentType.equalsIgnoreCase("audio/mid") || contentType.equalsIgnoreCase("audio/midi") || contentType.equalsIgnoreCase("sp-midi") || contentType.equalsIgnoreCase("audio/spmidi"))
			{
				player = new midiPlayer(stream);
			}
else if(contentType.equalsIgnoreCase("audio/x-wav") || contentType.equalsIgnoreCase("audio/wav"))
			{
				player = new wavPlayer(stream);
			}
			else if (type.equalsIgnoreCase("")) /* If the stream doesn't have an accompanying type, try everything we can to try and load it */
			{
				try 
				{
					final byte[] tryStream = new byte[stream.available()];
					readInputStreamData(stream, tryStream, 0, stream.available());

					System.out.println("Received no explicit audio type. Trying to load as MIDI, and if it fails, WAV.");
					/* Try loading it as a MIDI file first */
					try { player = new midiPlayer(new ByteArrayInputStream(tryStream)); } 
					catch (Exception e) { }
					
					/* If that doesn't work, try as WAV next, if it still doesn't work, we have no other players to try */
					try { player = new wavPlayer(new ByteArrayInputStream(tryStream)); }
					catch (Exception e)
					{
						System.out.println("No Player For: "+contentType);
						player = new audioplayer();
					}
				}
				catch (IOException e)
				{
					System.out.println("Couldn't read input stream: " + e.getMessage());
				}

			}
			else /* TODO: Implement a player for amr and mpeg audio types */
			{
				System.out.println("No Player For: "+contentType);
				player = new audioplayer();
			}
		}
		controls[0] = new volumeControl();
		controls[1] = new tempoControl();
		controls[2] = new midiControl();
		controls[3] = new toneControl();

		//System.out.println("media type: "+type);
	}

public PlatformPlayer(String locator) throws javax.microedition.media.MediaException
	{
		listeners = new Vector<PlayerListener>();
		controls = new Control[4];
if(Mobile.sound && AudioBridge.isAvailable())
{
try { player = new remotePlayer(locator); }
catch (IOException error) { throw new javax.microedition.media.MediaException(error.toString()); }
}
else
{
player = new audioplayer();
}
		controls[0] = new volumeControl();
		controls[1] = new tempoControl();
		controls[2] = new midiControl();
		controls[3] = new toneControl();
		System.out.println("Player locator: "+locator);
	}


	public void close()
	{
		try
		{
			player.stop();
player.close();
			state = Player.CLOSED;
			notifyListeners(PlayerListener.CLOSED, null);	
		}
		catch (Exception e) { }
		state = Player.CLOSED;	
	}

	public int getState()
	{
if(!(player instanceof remotePlayer) && player.isRunning()==false)
		{
			state = Player.PREFETCHED;
		}
		return state;
	}

	public void start()
	{
		//System.out.println("Play "+contentType);
		try
		{
			player.start();
		}
		catch (Exception e) {  }
	}

	public void stop()
	{
		//System.out.println("Stop "+contentType);
		if(player instanceof remotePlayer) state = Player.PREFETCHED;
		try
		{
			player.stop();
		}
		catch (Exception e) { }
	}

	public void addPlayerListener(PlayerListener playerListener)
	{
		//System.out.println("Add Player Listener");
		listeners.add(playerListener);
	}

	public void removePlayerListener(PlayerListener playerListener)
	{
		//System.out.println("Remove Player Listener");
		listeners.remove(playerListener);
	}

	private void notifyListeners(String event, Object eventData)
	{
		for(int i=0; i<listeners.size(); i++)
		{
			listeners.get(i).playerUpdate(this, event, eventData);
		}
	}

	public void deallocate()
	{
		stop();
		player.deallocate();
		state = player instanceof remotePlayer ? Player.UNREALIZED : Player.CLOSED;
	}

public String getContentType() { return contentType; }

public long getDuration() { return player instanceof remotePlayer ? duration : Player.TIME_UNKNOWN; }

	public long getMediaTime() { return player.getMediaTime(); }

	public void prefetch() { state = Player.PREFETCHED; }

	public void realize() { state = Player.REALIZED; }

public void setLoopCount(int count) { player.setLoopCount(count); }

	public long setMediaTime(long now) { return player.setMediaTime(now); }

	private void updateVolume(int level, boolean muted)
	{
		if(player instanceof remotePlayer)
		{
			((remotePlayer)player).updateVolume(level, muted);
		}
	}

	public static int activeRemoteVolumeLevel()
	{
		PlatformPlayer active = activeRemoteVolumePlayer;
		if (active == null || active.controls == null || active.controls.length == 0
				|| !(active.controls[0] instanceof PlatformPlayer.volumeControl)) return -1;
		return ((PlatformPlayer.volumeControl)active.controls[0]).getLevel();
	}

	public static boolean adjustActiveRemoteVolume(int delta)
	{
		PlatformPlayer active = activeRemoteVolumePlayer;
		if (active == null || active.controls == null || active.controls.length == 0
				|| !(active.controls[0] instanceof PlatformPlayer.volumeControl)) return false;
		volumeControl volume = (volumeControl)active.controls[0];
		volume.setLevel(volume.getLevel() + delta);
		return true;
	}

	// Controllable interface //

	public Control getControl(String controlType)
	{
		if(controlType.equals("VolumeControl")) { return controls[0]; }
		if(controlType.equals("TempoControl")) { return controls[1]; }
		if(controlType.equals("MIDIControl")) { return controls[2]; }
		if(controlType.equals("ToneControl")) { return controls[3]; }
		if(controlType.equals("javax.microedition.media.control.VolumeControl")) { return controls[0]; }
		if(controlType.equals("javax.microedition.media.control.TempoControl")) { return controls[1]; }
		if(controlType.equals("javax.microedition.media.control.MIDIControl")) { return controls[2]; }
		if(controlType.equals("javax.microedition.media.control.ToneControl")) { return controls[3]; }
		if(controlType.equals("javax.microedition.media.ToneControl")) { return controls[3]; }
		return null;
	}

	public Control[] getControls()
	{
		return controls;
	}

	/* Read 'n' Bytes from the InputStream. Used by IMA ADPCM decoder as well. */
	public static void readInputStreamData(InputStream input, byte[] output, int offset, int nBytes) throws IOException 
	{
		int end = offset + nBytes;
		while(offset < end) 
		{
			int read = input.read(output, offset, end - offset);
			if(read < 0) throw new java.io.EOFException();
			offset += read;
		}
	}

	// Players //

	private class audioplayer
	{
		public void start() {  }
		public void stop() {  }
public void close() { }
		public void setLoopCount(int count) {  }
		public long setMediaTime(long now) { return now; }
		public long getMediaTime() { return 0; }
		public boolean isRunning() { return false; }
		public void deallocate() {  }
	}

private class remotePlayer extends audioplayer implements AudioBridge.Listener
{
private final int id;
private volatile boolean running;
private volatile boolean startRequested;
private volatile boolean closed;
private volatile boolean deallocated;
private volatile long mediaPosition;
private volatile long positionTimestamp;
private int loopCount = 1;
private volatile int volumeLevel = 100;
private volatile boolean muted;

remotePlayer(InputStream stream) throws IOException
{
id = AudioBridge.createPlayer(stream, contentType, this);
AudioBridge.volume(id, 100, false);
}

remotePlayer(String locator) throws IOException
{
id = AudioBridge.createPlayer(locator, this);
AudioBridge.volume(id, 100, false);
}

public void start()
{
if(closed) return;
if(deallocated)
{
try
{
AudioBridge.recreatePlayer(id, this);
deallocated = false;
AudioBridge.volume(id, volumeLevel, muted);
AudioBridge.loop(id, loopCount);
if(mediaPosition > 0L) AudioBridge.seek(id, mediaPosition);
}
catch(IOException error)
{
startRequested = false;
notifyListeners(PlayerListener.ERROR, error.toString());
return;
}
}
activeRemoteVolumePlayer = PlatformPlayer.this;
if(toneSequence != null) AudioBridge.toneSequence(id, toneSequence);
startRequested = true;
AudioBridge.command(AudioBridge.START, id);
}

public void stop()
{
if(closed || deallocated) return;
if(activeRemoteVolumePlayer == PlatformPlayer.this) activeRemoteVolumePlayer = null;
mediaPosition = getMediaTime();
running = false;
startRequested = false;
positionTimestamp = 0L;
AudioBridge.command(AudioBridge.STOP, id);
}

public void deallocate()
{
if(closed || deallocated) return;
if(activeRemoteVolumePlayer == PlatformPlayer.this) activeRemoteVolumePlayer = null;
running = false;
startRequested = false;
mediaPosition = 0L;
positionTimestamp = 0L;
deallocated = true;
AudioBridge.command(AudioBridge.RELEASE, id);
}

public void close()
{
if(closed) return;
closed = true;
deallocated = true;
if(activeRemoteVolumePlayer == PlatformPlayer.this) activeRemoteVolumePlayer = null;
running = false;
AudioBridge.command(AudioBridge.RELEASE, id);
AudioBridge.forget(id);
}

public void updateVolume(int level, boolean isMuted)
{
volumeLevel = Math.max(0, Math.min(100, level));
muted = isMuted;
if(!deallocated && !closed) AudioBridge.volume(id, volumeLevel, muted);
}

public void setLoopCount(int count)
{
loopCount = count;
if(!deallocated && !closed) AudioBridge.loop(id, count);
}

public long setMediaTime(long now)
{
mediaPosition = Math.max(0L, now);
positionTimestamp = running ? System.nanoTime() : 0L;
if(!deallocated && !closed) AudioBridge.seek(id, mediaPosition);
return mediaPosition;
}

public long getMediaTime()
{
if(!running || positionTimestamp == 0L) return mediaPosition;
return mediaPosition + Math.max(0L, (System.nanoTime() - positionTimestamp) / 1000L);
}

public boolean isRunning() { return running || startRequested; }

public void onAudioEvent(int event, long value, String detail)
{
if(event == AudioBridge.PREPARED)
{
duration = value < 0 ? Player.TIME_UNKNOWN : value;
}
else if(event == AudioBridge.STARTED)
{
mediaPosition = Math.max(0L, value);
positionTimestamp = System.nanoTime();
running = true;
startRequested = false;
state = Player.STARTED;
notifyListeners(PlayerListener.STARTED, Long.valueOf(mediaPosition));
}
else if(event == AudioBridge.STOPPED || event == AudioBridge.SEEK_COMPLETE)
{
mediaPosition = Math.max(0L, value);
			if(event == AudioBridge.STOPPED)
			{
				positionTimestamp = 0L;
				running = false;
				if(activeRemoteVolumePlayer == PlatformPlayer.this) activeRemoteVolumePlayer = null;
				state = Player.PREFETCHED;
				notifyListeners(PlayerListener.STOPPED, Long.valueOf(mediaPosition));
			}
			else
			{
				positionTimestamp = running ? System.nanoTime() : 0L;
			}
}
else if(event == AudioBridge.COMPLETED)
{
mediaPosition = Math.max(0L, value);
positionTimestamp = 0L;
running = false;
startRequested = false;
if(activeRemoteVolumePlayer == PlatformPlayer.this) activeRemoteVolumePlayer = null;
state = Player.PREFETCHED;
notifyListeners(PlayerListener.END_OF_MEDIA, Long.valueOf(mediaPosition));
}
else if(event == AudioBridge.ERROR)
{
running = false;
startRequested = false;
if(activeRemoteVolumePlayer == PlatformPlayer.this) activeRemoteVolumePlayer = null;
positionTimestamp = 0L;
state = Player.PREFETCHED;
notifyListeners(PlayerListener.ERROR, detail);
}
}
}

	private byte[] toneSequence;

	private class toneControl implements javax.microedition.media.control.ToneControl,
			javax.microedition.media.ToneControl
	{
		public void setSequence(byte[] sequence)
		{
			if(sequence == null || sequence.length == 0)
				throw new IllegalArgumentException("Tone sequence must not be empty");
			toneSequence = sequence.clone();
			if(player instanceof remotePlayer && ((remotePlayer)player).isRunning())
				AudioBridge.toneSequence(((remotePlayer)player).id, toneSequence);
		}
	}

	private class midiPlayer extends audioplayer
	{
		private Sequencer midi;

		private int loops = 0;

		private long tick = 0L;

		public midiPlayer(InputStream stream)
		{
			try
			{
				midi = MidiSystem.getSequencer();
				midi.open();
				midi.setSequence(stream);
				state = Player.PREFETCHED;
			}
			catch (Exception e) 
			{ 
				System.out.println("Couldn't load MIDI file: " + e.getMessage());
				midi.close();
			}
		}

		public void start()
		{
			if(isRunning()) { return; }

			if(midi.getTickPosition() >= midi.getTickLength())
			{
				midi.setTickPosition(0);
			}
			tick = midi.getTickPosition();
			midi.start();
			state = Player.STARTED;
			notifyListeners(PlayerListener.STARTED, tick);
		}

		public void stop()
		{
			if(!isRunning()) { return; }
			midi.stop();
			state = Player.PREFETCHED;
			tick = midi.getTickPosition();
			notifyListeners(PlayerListener.STOPPED, tick);
		}
		public void deallocate()
		{
			midi.close();
		}

		public void setLoopCount(int count)
		{
			loops = count;
			midi.setLoopCount(count);
		}
		public long setMediaTime(long now)
		{
			try
			{
				midi.setTickPosition(now);
			}
			catch (Exception e) { }
			return now;
		}
		public long getMediaTime()
		{
			return midi.getTickPosition();
		}
		public boolean isRunning()
		{
			return midi.isRunning();
		}
	}

	private class wavPlayer extends audioplayer
	{
		/* PCM WAV variables */
		private AudioInputStream wavStream;
		private Clip wavClip;
		/* IMA ADPCM WAV variables */
		private WavImaAdpcmDecoder adpcmDec = new WavImaAdpcmDecoder();
		InputStream decodedStream;
		private int[] wavHeaderData = new int[4];
		
		/* Player control variables */
		private int loops = 0;

		private long time = 0L;

		public wavPlayer(InputStream stream)
		{
			try
			{
				/*
				 * A wav header is generally 44-bytes long, and it is what we need to read in order to get
				 * the stream's format, frame size, bit rate, number of channels, etc. which gives us information
				 * on the kind of codec needed to play or decode the incoming stream. The stream needs to be reset
				 * or else PCM files will be loaded without a header and it might cause issues with playback.
				 */
				stream.mark(48);
				wavHeaderData = adpcmDec.readHeader(stream);
				stream.reset();

				/* We only check for IMA ADPCM at the moment. */
				if(wavHeaderData[0] != 17) /* If it's not IMA ADPCM we don't need to do anything to the stream. */
				{
					wavStream = AudioSystem.getAudioInputStream(stream);
					wavClip = AudioSystem.getClip();
					wavClip.open(wavStream);
					state = Player.PREFETCHED;
				}
				else /* But if it is IMA ADPCM, we have to decode it manually. */
				{
					decodedStream = adpcmDec.decodeImaAdpcm(stream, wavHeaderData);
					wavStream = AudioSystem.getAudioInputStream(decodedStream);
					wavClip = AudioSystem.getClip();
					wavClip.open(wavStream);
					state = Player.PREFETCHED;
				}
			}
			catch (Exception e) 
			{ 
				System.out.println("Couldn't load wav file: " + e.getMessage());
				wavClip.close();
			}
		}

		public void start()
		{
			if(isRunning()) { return; }
			
			if(wavClip.getFramePosition() >= wavClip.getFrameLength())
			{
				wavClip.setFramePosition(0);
			}
			time = wavClip.getMicrosecondPosition();
			wavClip.start();
			state = Player.STARTED;
			notifyListeners(PlayerListener.STARTED, time);
		}

		public void stop()
		{
			if(!isRunning()) { return; }
			wavClip.stop();
			time = wavClip.getMicrosecondPosition();
			state = Player.PREFETCHED;
			notifyListeners(PlayerListener.STOPPED, time);
		}

		public void setLoopCount(int count)
		{
			loops = count;
			wavClip.loop(count);
		}

		public long setMediaTime(long now)
		{
			wavClip.setMicrosecondPosition(now);
			return now;
		}
		public long getMediaTime()
		{
			return wavClip.getMicrosecondPosition();
		}

		public boolean isRunning()
		{
			return wavClip.isRunning();
		}
	}

	// Controls //

	private class midiControl implements javax.microedition.media.control.MIDIControl
	{
		public int[] getBankList(boolean custom) { return new int[]{}; }

		public int getChannelVolume(int channel) { return 0; }

		public java.lang.String getKeyName(int bank, int prog, int key) { return ""; }

		public int[] getProgram(int channel) { return new int[]{}; }

		public int[] getProgramList(int bank) { return new int[]{}; }

		public java.lang.String getProgramName(int bank, int prog) { return ""; }

		public boolean isBankQuerySupported() { return false; }

		public int longMidiEvent(byte[] data, int offset, int length) { return 0; }

public void setChannelVolume(int channel, int volume)
{
if(player instanceof remotePlayer)
{
AudioBridge.midiChannelVolume(((remotePlayer)player).id, channel, volume);
}
}

		public void setProgram(int channel, int bank, int program) {  }

public void shortMidiEvent(int type, int data1, int data2)
{
if(player instanceof remotePlayer)
{
AudioBridge.midiEvent(((remotePlayer)player).id, type, data1, data2);
}
}
	}

	private class volumeControl implements javax.microedition.media.control.VolumeControl
	{
		private int level = 100;
		private boolean muted = false;

		public int getLevel() { return level; }

		public boolean isMuted() { return muted; }

public int setLevel(int value)
{
level = Math.max(0, Math.min(100, value));
updateVolume(level, muted);
return level;
}

public void setMute(boolean mute)
{
muted = mute;
updateVolume(level, muted);
}
	}

	private class tempoControl implements javax.microedition.media.control.TempoControl
	{
		int tempo = 5000;
		int rate = 5000;

		public int getTempo() { return tempo; }

		public int setTempo(int millitempo) { tempo = millitempo; return tempo; }

		// RateControl interface
		public int getMaxRate() { return rate; }

		public int getMinRate() { return rate; }

		public int getRate() { return rate; }

		public int setRate(int millirate) { rate=millirate; return rate; }
	}
}
